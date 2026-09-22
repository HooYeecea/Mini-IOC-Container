package com.miniioccontainer.context;

import com.miniioccontainer.annotation.MyAround;
import com.miniioccontainer.annotation.MyAspect;
import com.miniioccontainer.annotation.MyAutowired;
import com.miniioccontainer.annotation.MyLazy;
import com.miniioccontainer.annotation.MyLog;
import com.miniioccontainer.annotation.MyPostConstruct;
import com.miniioccontainer.annotation.MyPreDestroy;
import com.miniioccontainer.annotation.MyPrimary;
import com.miniioccontainer.annotation.MyQualifier;
import com.miniioccontainer.annotation.MyScope;
import com.miniioccontainer.annotation.MyValue;
import com.miniioccontainer.aop.AopAdvice;
import com.miniioccontainer.aop.AopProxyFactory;
import com.miniioccontainer.aop.MiniAopInterceptor;
import com.miniioccontainer.aop.MyJoinPoint;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MiniApplicationContext {

    // 对外暴露的实例（可能是 AOP 代理）。创建过程中会先放进半成品，注入完成后再算正式完成
    private final Map<String, Object> beans = new HashMap<>();
    // 原始目标对象，字段注入打在这上面
    private final Map<String, Object> targets = new HashMap<>();
    // 已注册、尚未（或正在）创建的 Bean 定义。按类型查找走这里，这样依赖的 Bean 可以推迟到真正用到时再创建
    private final Map<String, BeanDefinition> beanDefinitions = new LinkedHashMap<>();
    // 正在创建、但构造已经完成的半成品。循环依赖时把这个提前交出去
    private final Map<String, Object> earlySingletonObjects = new HashMap<>();
    private final Set<String> finishedBeanNames = new HashSet<>();
    private final Set<String> beansInCreation = new HashSet<>();
    private final Deque<String> creationStack = new ArrayDeque<>();
    // 初始化完成的顺序，关闭时倒序销毁
    private final List<String> creationOrder = new ArrayList<>();
    private boolean closed;
    // XML primary="true" 或类上 @MyPrimary 的 Bean 名
    private final Set<String> primaryBeanNames = new HashSet<>();
    private List<AopAdvice> advices = List.of();
    private final PropertyPlaceholderResolver placeholders = new PropertyPlaceholderResolver();

    /**
     * location 以 .xml 结尾：从 classpath 读 XML（可含 component-scan / property-placeholder）。
     * 否则：当作包名，只扫 @MyComponent，并尝试加载 classpath 上的 application.properties。
     */
    public MiniApplicationContext(String location) {
        if (location != null && location.endsWith(".xml")) {
            loadFromXml(location);
        } else {
            placeholders.loadIfPresent("application.properties");
            registerAnnotationBeans(location);
        }
        refresh();
    }

    private void loadFromXml(String xmlClasspath) {
        XmlBeanDefinitionReader.Result config = XmlBeanDefinitionReader.load(xmlClasspath);

        for (String propertyLocation : config.getPropertyLocations()) {
            placeholders.load(propertyLocation);
        }
        // 没写 property-placeholder 时，仍尝试加载默认 application.properties
        if (config.getPropertyLocations().isEmpty()) {
            placeholders.loadIfPresent("application.properties");
        }

        // 先注册注解 Bean，再注册 XML Bean：同类冲突时注解优先
        for (String basePackage : config.getScanPackages()) {
            registerAnnotationBeans(basePackage);
        }
        for (XmlBeanDefinition definition : config.getBeans()) {
            registerXmlBean(definition);
        }
    }

    /**
     * 先准备切面，再按依赖创建 Bean。
     * 单例在注入前提前暴露；对方再要它时直接拿半成品，从而解开字段 / setter 循环依赖。
     * 构造过程中就再入（实例还没暴露）则无法解开，直接报错。
     */
    private void refresh() {
        preInstantiateAspects();
        advices = collectAroundAdvices();
        for (String beanName : new ArrayList<>(beanDefinitions.keySet())) {
            if (beanDefinitions.get(beanName).prototype || beanDefinitions.get(beanName).lazy) {
                continue;
            }
            getBean(beanName);
        }
    }

    private void registerAnnotationBeans(String basePackage) {
        List<Class<?>> classes = PackageScanner.scan(basePackage);
        for (Class<?> clazz : classes) {
            String beanName = getBeanName(clazz);
            registerDefinition(beanName, clazz, clazz.isAnnotationPresent(MyPrimary.class),
                    List.of(), List.of(), "", "", scopeOf(clazz, ""),
                    lazyOf(clazz, "", scopeOf(clazz, "")), "注解");
        }
    }

    private void registerXmlBean(XmlBeanDefinition definition) {
        Class<?> clazz;
        try {
            clazz = Class.forName(definition.getClassName(), false,
                    Thread.currentThread().getContextClassLoader());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("XML 中找不到类: " + definition.getClassName(), e);
        }

        if (hasDefinitionOfClass(clazz)) {
            String xmlName = definition.getId().isEmpty() ? getBeanName(clazz) : definition.getId();
            System.out.println("跳过 XML Bean: " + xmlName
                    + "，类 " + clazz.getName() + " 已由注解注册");
            return;
        }

        String beanName = definition.getId().isEmpty() ? getBeanName(clazz) : definition.getId();
        String scope = scopeOf(clazz, definition.getScope());
        registerDefinition(beanName, clazz, definition.isPrimary(),
                definition.getConstructorArgs(), definition.getProperties(),
                definition.getInitMethod(), definition.getDestroyMethod(),
                scope, lazyOf(clazz, definition.getLazyInit(), scope), "XML");
    }

    private void registerDefinition(String beanName,
                                    Class<?> clazz,
                                    boolean primary,
                                    List<XmlBeanDefinition.ConstructorArg> constructorArgs,
                                    List<XmlBeanDefinition.Property> properties,
                                    String initMethod,
                                    String destroyMethod,
                                    String scope,
                                    boolean lazy,
                                    String source) {
        BeanDefinition existing = beanDefinitions.get(beanName);
        if (existing != null) {
            if (existing.clazz.equals(clazz)) {
                return;
            }
            throw new RuntimeException(
                    "Bean 名重复: " + beanName
                            + "，已有 " + existing.clazz.getName()
                            + "，又注册 " + clazz.getName());
        }
        if (clazz.isAnnotationPresent(MyAspect.class) && "prototype".equals(scope)) {
            throw new RuntimeException("切面必须是单例: " + clazz.getName());
        }
        if (clazz.isAnnotationPresent(MyAspect.class) && lazy) {
            throw new RuntimeException("切面不能懒加载: " + clazz.getName());
        }
        beanDefinitions.put(beanName, new BeanDefinition(
                beanName, clazz, constructorArgs, properties, initMethod, destroyMethod,
                "prototype".equals(scope), lazy));
        if (primary) {
            primaryBeanNames.add(beanName);
        }
        StringBuilder label = new StringBuilder("注册 Bean: " + beanName + " (" + source);
        if ("prototype".equals(scope)) {
            label.append(", prototype");
        }
        if (lazy) {
            label.append(", lazy");
        }
        label.append(")");
        System.out.println(label);
    }

    /**
     * 原型不使用懒加载标记。单例看 XML lazy-init，没写再看 @MyLazy。
     */
    private boolean lazyOf(Class<?> clazz, String xmlLazy, String scope) {
        if ("prototype".equals(scope)) {
            return false;
        }
        String lazy = xmlLazy == null ? "" : xmlLazy.trim();
        if (!lazy.isEmpty()) {
            if (!"true".equals(lazy) && !"false".equals(lazy)) {
                throw new RuntimeException("lazy-init 只能是 true 或 false（" + clazz.getName() + "）");
            }
            return Boolean.parseBoolean(lazy);
        }
        return clazz.isAnnotationPresent(MyLazy.class);
    }

    /**
     * XML 写了 scope 就用 XML 的；否则看类上的 @MyScope；都没有则是 singleton。
     */
    private String scopeOf(Class<?> clazz, String xmlScope) {
        String scope = xmlScope == null ? "" : xmlScope.trim();
        if (scope.isEmpty()) {
            MyScope annotation = clazz.getAnnotation(MyScope.class);
            scope = annotation == null ? "singleton" : annotation.value().trim();
        }
        if (!"singleton".equals(scope) && !"prototype".equals(scope)) {
            throw new RuntimeException("不支持的 scope: " + scope + "（" + clazz.getName() + "）");
        }
        return scope;
    }

    private boolean hasDefinitionOfClass(Class<?> clazz) {
        for (BeanDefinition definition : beanDefinitions.values()) {
            if (definition.clazz.equals(clazz)) {
                return true;
            }
        }
        return false;
    }

    private void preInstantiateAspects() {
        for (BeanDefinition definition : beanDefinitions.values()) {
            if (!definition.clazz.isAnnotationPresent(MyAspect.class)) {
                continue;
            }
            targets.put(definition.name, instantiate(definition));
        }
    }

    /**
     * 构造器参数在这里解析，此时 Bean 还没暴露。
     * 构造器循环依赖会走到 getBean 的「无法解决」分支。
     * 字段循环依赖要等构造完成、提前暴露之后才能解开。
     */
    private Object instantiate(BeanDefinition definition) {
        try {
            if (!definition.constructorArgs.isEmpty()) {
                return instantiateWithXmlArgs(definition);
            }
            Constructor<?> constructor = chooseConstructor(definition.clazz);
            Object[] args = resolveConstructorArguments(definition, constructor);
            constructor.setAccessible(true);
            return constructor.newInstance(args);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("创建 Bean 失败: " + definition.clazz.getName(), e);
        }
    }

    private Constructor<?> chooseConstructor(Class<?> clazz) {
        Constructor<?>[] constructors = clazz.getDeclaredConstructors();
        if (constructors.length == 1) {
            return constructors[0];
        }

        List<Constructor<?>> autowired = new ArrayList<>();
        Constructor<?> noArg = null;
        for (Constructor<?> constructor : constructors) {
            if (constructor.getParameterCount() == 0) {
                noArg = constructor;
            }
            if (constructor.isAnnotationPresent(MyAutowired.class)) {
                autowired.add(constructor);
            }
        }
        if (autowired.size() == 1) {
            return autowired.get(0);
        }
        if (autowired.size() > 1) {
            throw new RuntimeException(
                    clazz.getName() + " 有多个 @MyAutowired 构造器，只能指定一个");
        }
        if (noArg != null) {
            return noArg;
        }
        throw new RuntimeException(
                clazz.getName() + " 有多个构造器，请在要使用的那个上加 @MyAutowired");
    }

    private Object[] resolveConstructorArguments(BeanDefinition definition, Constructor<?> constructor) {
        Parameter[] parameters = constructor.getParameters();
        Class<?>[] types = constructor.getParameterTypes();
        Type[] genericTypes = constructor.getGenericParameterTypes();
        Object[] args = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            MyValue valueAnnotation = parameters[i].getAnnotation(MyValue.class);
            if (valueAnnotation != null) {
                args[i] = convertValue(valueAnnotation.value(), types[i],
                        "构造器 " + definition.clazz.getName());
                continue;
            }
            MyQualifier qualifier = parameters[i].getAnnotation(MyQualifier.class);
            String qualifierValue = qualifier == null ? "" : qualifier.value();
            Object dependency = resolveDependency(
                    types[i], genericTypes[i], "", qualifierValue,
                    "构造器 " + definition.clazz.getName());
            if (dependency == null) {
                throw new RuntimeException(
                        "找不到类型为 " + types[i].getName()
                                + " 的 Bean（构造器注入到 " + definition.clazz.getName() + "）");
            }
            args[i] = dependency;
        }
        if (args.length > 0) {
            System.out.println("构造注入: " + definition.clazz.getSimpleName()
                    + " <- " + joinDependencyNames(args));
        }
        return args;
    }

    private Object instantiateWithXmlArgs(BeanDefinition definition) {
        List<XmlBeanDefinition.ConstructorArg> xmlArgs = definition.constructorArgs;
        Constructor<?> matched = null;
        Object[] resolved = null;
        for (Constructor<?> constructor : definition.clazz.getDeclaredConstructors()) {
            if (constructor.getParameterCount() != xmlArgs.size()) {
                continue;
            }
            Class<?>[] types = constructor.getParameterTypes();
            Object[] candidate = new Object[xmlArgs.size()];
            boolean compatible = true;
            for (int i = 0; i < xmlArgs.size(); i++) {
                XmlBeanDefinition.ConstructorArg arg = xmlArgs.get(i);
                Object value;
                if (arg.isValue()) {
                    value = convertValue(arg.getValue(), types[i],
                            "XML 构造器 " + definition.name);
                } else {
                    value = getBean(arg.getRef());
                    if (value == null) {
                        throw new RuntimeException(
                                "XML 找不到 constructor-arg ref=\"" + arg.getRef()
                                        + "\" 的 Bean（" + definition.name + "）");
                    }
                }
                if (!isAssignable(types[i], value)) {
                    compatible = false;
                    break;
                }
                candidate[i] = value;
            }
            if (compatible) {
                matched = constructor;
                resolved = candidate;
                break;
            }
        }
        if (matched == null) {
            throw new RuntimeException(
                    "XML Bean " + definition.name + " 找不到匹配 "
                            + xmlArgs.size() + " 个 constructor-arg 的构造器");
        }
        try {
            matched.setAccessible(true);
            System.out.println("XML 构造注入: " + definition.clazz.getSimpleName()
                    + " <- " + joinDependencyNames(resolved));
            return matched.newInstance(resolved);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("创建 XML Bean 失败: " + definition.clazz.getName(), e);
        }
    }

    private String joinDependencyNames(Object[] dependencies) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < dependencies.length; i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(describeDependency(dependencies[i]));
        }
        return sb.toString();
    }

    private Object createBean(String beanName) {
        BeanDefinition definition = beanDefinitions.get(beanName);
        beansInCreation.add(beanName);
        creationStack.addLast(beanName);
        try {
            Object target = targets.get(beanName);
            if (target == null) {
                target = instantiate(definition);
                targets.put(beanName, target);
            }
            Object exposed = expose(beanName, target);
            // 构造已完成，先暴露半成品，再注入。循环依赖会在这里被接住
            earlySingletonObjects.put(beanName, exposed);
            beans.put(beanName, exposed);

            injectDependencies(target);
            applyXmlProperties(definition, target);
            invokeLifecycle(target, MyPostConstruct.class, definition.initMethod, "初始化");

            earlySingletonObjects.remove(beanName);
            finishedBeanNames.add(beanName);
            creationOrder.add(beanName);
            return exposed;
        } finally {
            beansInCreation.remove(beanName);
            creationStack.removeLast();
        }
    }

    /**
     * 原型每次都新建：会初始化，但不进单例缓存，关闭容器时也不销毁。
     * 创建过程中再次要到自己，直接失败，不提前暴露。
     */
    private Object createPrototype(BeanDefinition definition) {
        beansInCreation.add(definition.name);
        creationStack.addLast(definition.name);
        try {
            Object target = instantiate(definition);
            Object exposed = expose(definition.name, target);
            injectDependencies(target);
            applyXmlProperties(definition, target);
            invokeLifecycle(target, MyPostConstruct.class, definition.initMethod, "初始化");
            return exposed;
        } finally {
            beansInCreation.remove(definition.name);
            creationStack.removeLast();
        }
    }

    private Object expose(String beanName, Object target) {
        Class<?> targetClass = target.getClass();
        if (targetClass.isAnnotationPresent(MyAspect.class) || advices.isEmpty()) {
            return target;
        }
        if (!hasMyLogMethod(targetClass)) {
            return target;
        }
        Class<?>[] interfaces = businessInterfaces(targetClass);
        if (interfaces.length == 0) {
            System.out.println("跳过 AOP: " + beanName
                    + " 有 @MyLog，但没有业务接口，JDK Proxy 无法代理");
            return target;
        }
        System.out.println("AOP 代理: " + beanName);
        return AopProxyFactory.create(target, interfaces, advices);
    }

    /**
     * 收集 @MyAround。切面自身不代理。
     * 切面要在其他 Bean 创建前先实例化，否则代理时还收集不到通知。
     */
    private List<AopAdvice> collectAroundAdvices() {
        List<AopAdvice> result = new ArrayList<>();
        for (Object bean : targets.values()) {
            Class<?> clazz = bean.getClass();
            if (!clazz.isAnnotationPresent(MyAspect.class)) {
                continue;
            }
            for (Method method : clazz.getDeclaredMethods()) {
                if (!method.isAnnotationPresent(MyAround.class)) {
                    continue;
                }
                if (method.getParameterCount() != 1
                        || !MyJoinPoint.class.isAssignableFrom(method.getParameterTypes()[0])) {
                    throw new RuntimeException(
                            "@MyAround 方法必须是 Object xxx(MyJoinPoint): "
                                    + clazz.getName() + "." + method.getName());
                }
                result.add(new AopAdvice(bean, method));
                System.out.println("注册切面通知: "
                        + clazz.getSimpleName() + "." + method.getName());
            }
        }
        return result;
    }

    private boolean hasMyLogMethod(Class<?> clazz) {
        for (Method method : clazz.getDeclaredMethods()) {
            if (method.isAnnotationPresent(MyLog.class)) {
                return true;
            }
        }
        return false;
    }

    private Class<?>[] businessInterfaces(Class<?> clazz) {
        List<Class<?>> result = new ArrayList<>();
        for (Class<?> iface : clazz.getInterfaces()) {
            String name = iface.getName();
            if (!name.startsWith("java.") && !name.startsWith("javax.")) {
                result.add(iface);
            }
        }
        return result.toArray(new Class<?>[0]);
    }

    /**
     * 把类名转成 Bean 名：OrderServiceImpl -> orderServiceImpl
     * 和 Spring 的默认命名习惯保持一致
     */
    private String getBeanName(Class<?> clazz) {
        String simpleName = clazz.getSimpleName();
        return Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
    }

    /**
     * 给一个 Bean 的所有 @MyAutowired 字段注入依赖
     *
     * 消歧顺序：
     * 1. @MyAutowired(name = "...") 按名字直接拿
     * 2. @MyQualifier 在同类型候选里按限定名精确匹配
     * 3. 按类型查找；多个候选时选唯一的 @MyPrimary / XML primary
     * 4. List / Map 注入该类型的全部候选，不再要求唯一
     */
    private void injectDependencies(Object bean) {
        Class<?> clazz = bean.getClass();
        Field[] fields = clazz.getDeclaredFields();

        for (Field field : fields) {
            boolean hasValue = field.isAnnotationPresent(MyValue.class);
            boolean hasAutowired = field.isAnnotationPresent(MyAutowired.class);
            if (!hasValue && !hasAutowired) {
                continue;
            }
            if (hasValue && hasAutowired) {
                throw new RuntimeException(
                        field.getName() + " 不能同时使用 @MyValue 和 @MyAutowired");
            }

            String where = clazz.getName() + "." + field.getName();
            Object dependency;
            if (hasValue) {
                dependency = convertValue(field.getAnnotation(MyValue.class).value(),
                        field.getType(), where);
            } else {
                MyAutowired annotation = field.getAnnotation(MyAutowired.class);
                String qualifier = "";
                if (field.isAnnotationPresent(MyQualifier.class)) {
                    qualifier = field.getAnnotation(MyQualifier.class).value();
                }
                dependency = resolveDependency(
                        field.getType(), field.getGenericType(), annotation.name(), qualifier, where);
                if (dependency == null) {
                    throw new RuntimeException(
                            "找不到类型为 " + field.getType().getName()
                                    + " 的 Bean（注入到 " + where + "）");
                }
            }

            try {
                field.setAccessible(true);
                field.set(bean, dependency);
                System.out.println("注入: " + clazz.getSimpleName()
                        + "." + field.getName() + " <- "
                        + describeDependency(dependency));
            } catch (IllegalAccessException e) {
                throw new RuntimeException("注入失败: " + field.getName(), e);
            }
        }
    }

    /**
     * 单个 Bean 仍走名字 / Qualifier / Primary。
     * List 与 Map 收集全部同类型候选，Map 的 key 是 Bean 名。
     */
    private Object resolveDependency(Class<?> type,
                                     Type genericType,
                                     String beanName,
                                     String qualifier,
                                     String where) {
        if (List.class.equals(type) || Map.class.equals(type)) {
            if (beanName != null && !beanName.isEmpty()) {
                throw new RuntimeException(
                        "@MyAutowired(name) 不能用在 List/Map 上（" + where + "）");
            }
            Class<?> elementType = collectionElementType(type, genericType, where);
            if (List.class.equals(type)) {
                return beansOfType(elementType, qualifier);
            }
            requireMapKey(genericType, where);
            return mapOfType(elementType, qualifier);
        }
        if (beanName != null && !beanName.isEmpty()) {
            Object dependency = getBean(beanName);
            if (dependency == null) {
                throw new RuntimeException(
                        "找不到名为 " + beanName + " 的 Bean（注入到 " + where + "）");
            }
            return dependency;
        }
        return resolveByType(type, qualifier);
    }

    private Class<?> collectionElementType(Class<?> rawType, Type genericType, String where) {
        if (!(genericType instanceof ParameterizedType parameterizedType)) {
            throw new RuntimeException(where + " 的 " + rawType.getSimpleName() + " 必须声明元素类型");
        }
        Type[] arguments = parameterizedType.getActualTypeArguments();
        int index = Map.class.equals(rawType) ? 1 : 0;
        return toClass(arguments[index], where);
    }

    private void requireMapKey(Type genericType, String where) {
        Type key = ((ParameterizedType) genericType).getActualTypeArguments()[0];
        if (key != String.class) {
            throw new RuntimeException(where + " 的 Map key 必须是 String，实际是 " + key.getTypeName());
        }
    }

    private Class<?> toClass(Type type, String where) {
        if (type instanceof Class<?> clazz) {
            return clazz;
        }
        if (type instanceof ParameterizedType parameterizedType) {
            return toClass(parameterizedType.getRawType(), where);
        }
        if (type instanceof WildcardType wildcardType) {
            return toClass(wildcardType.getUpperBounds()[0], where);
        }
        throw new RuntimeException(where + " 无法解析类型: " + type.getTypeName());
    }

    private <T> List<T> beansOfType(Class<T> type, String qualifier) {
        List<T> result = new ArrayList<>();
        for (BeanDefinition definition : matchingDefinitions(type, qualifier)) {
            result.add(adapt(type, definition.name));
        }
        return result;
    }

    private <T> Map<String, T> mapOfType(Class<T> type, String qualifier) {
        Map<String, T> result = new LinkedHashMap<>();
        for (BeanDefinition definition : matchingDefinitions(type, qualifier)) {
            result.put(definition.name, adapt(type, definition.name));
        }
        return result;
    }

    private List<BeanDefinition> matchingDefinitions(Class<?> type, String qualifier) {
        List<BeanDefinition> candidates = findDefinitionsByType(type);
        if (qualifier == null || qualifier.isEmpty()) {
            return candidates;
        }
        List<BeanDefinition> matched = new ArrayList<>();
        for (BeanDefinition definition : candidates) {
            if (qualifierMatches(definition, qualifier)) {
                matched.add(definition);
            }
        }
        if (matched.isEmpty()) {
            throw new RuntimeException(
                    "找不到类型为 " + type.getName()
                            + " 且 @MyQualifier(\"" + qualifier + "\") 的 Bean");
        }
        return matched;
    }

    private String describeDependency(Object dependency) {
        if (dependency instanceof String || dependency instanceof Number || dependency instanceof Boolean) {
            return String.valueOf(dependency);
        }
        if (dependency instanceof List<?> list) {
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < list.size(); i++) {
                if (i > 0) {
                    sb.append(", ");
                }
                sb.append(MiniAopInterceptor.unwrap(list.get(i)).getClass().getSimpleName());
            }
            sb.append("]");
            return sb.toString();
        }
        if (dependency instanceof Map<?, ?> map) {
            return map.keySet().toString();
        }
        Object unwrapped = MiniAopInterceptor.unwrap(dependency);
        return unwrapped.getClass().getSimpleName()
                + (unwrapped != dependency ? " (AOP 代理)" : "");
    }

    private void applyXmlProperties(BeanDefinition definition, Object bean) {
        for (XmlBeanDefinition.Property property : definition.properties) {
            Object dependency;
            if (property.isValue()) {
                Class<?> type = propertyType(bean, property.getName(), definition.name);
                dependency = convertValue(property.getValue(), type,
                        definition.name + "." + property.getName());
            } else {
                dependency = getBean(property.getRef());
                if (dependency == null) {
                    throw new RuntimeException(
                            "XML 找不到 ref=\"" + property.getRef()
                                    + "\" 的 Bean（注入到 " + definition.name
                                    + "." + property.getName() + "）");
                }
            }
            if (!injectFieldByName(bean, property.getName(), dependency)
                    && !injectSetter(bean, property.getName(), dependency)) {
                throw new RuntimeException(
                        "XML Bean " + definition.name + " 找不到属性: " + property.getName());
            }
            System.out.println("XML 注入: " + bean.getClass().getSimpleName()
                    + "." + property.getName() + " <- " + describeDependency(dependency));
        }
    }

    private Class<?> propertyType(Object bean, String propertyName, String beanName) {
        Class<?> current = bean.getClass();
        while (current != null) {
            try {
                return current.getDeclaredField(propertyName).getType();
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            }
        }
        String setterName = "set" + Character.toUpperCase(propertyName.charAt(0))
                + propertyName.substring(1);
        for (Method method : bean.getClass().getMethods()) {
            if (method.getName().equals(setterName) && method.getParameterCount() == 1) {
                return method.getParameterTypes()[0];
            }
        }
        throw new RuntimeException("XML Bean " + beanName + " 找不到属性: " + propertyName);
    }

    private Object convertValue(String raw, Class<?> type, String where) {
        String text = placeholders.resolve(raw, where);
        try {
            if (type == String.class) {
                return text;
            }
            if (type == int.class || type == Integer.class) {
                return Integer.valueOf(text);
            }
            if (type == long.class || type == Long.class) {
                return Long.valueOf(text);
            }
            if (type == double.class || type == Double.class) {
                return Double.valueOf(text);
            }
            if (type == boolean.class || type == Boolean.class) {
                if (!"true".equalsIgnoreCase(text) && !"false".equalsIgnoreCase(text)) {
                    throw new IllegalArgumentException("布尔值只能是 true 或 false");
                }
                return Boolean.valueOf(text);
            }
        } catch (RuntimeException e) {
            throw new RuntimeException("配置值转换失败: \"" + text + "\" -> "
                    + type.getSimpleName() + "（" + where + "）", e);
        }
        throw new RuntimeException("不支持的配置类型 " + type.getSimpleName() + "（" + where + "）");
    }

    private boolean isAssignable(Class<?> type, Object value) {
        if (value == null) {
            return !type.isPrimitive();
        }
        if (type.isInstance(value)) {
            return true;
        }
        if (!type.isPrimitive()) {
            return false;
        }
        Class<?> wrapper = switch (type.getName()) {
            case "int" -> Integer.class;
            case "long" -> Long.class;
            case "double" -> Double.class;
            case "boolean" -> Boolean.class;
            default -> null;
        };
        return wrapper != null && wrapper.isInstance(value);
    }

    private boolean injectFieldByName(Object bean, String fieldName, Object dependency) {
        Class<?> current = bean.getClass();
        while (current != null) {
            try {
                Field field = current.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(bean, dependency);
                return true;
            } catch (NoSuchFieldException e) {
                current = current.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new RuntimeException("XML 注入字段失败: " + fieldName, e);
            }
        }
        return false;
    }

    private boolean injectSetter(Object bean, String propertyName, Object dependency) {
        String setterName = "set" + Character.toUpperCase(propertyName.charAt(0))
                + propertyName.substring(1);
        for (Method method : bean.getClass().getMethods()) {
            if (method.getName().equals(setterName) && method.getParameterCount() == 1) {
                try {
                    method.invoke(bean, dependency);
                    return true;
                } catch (Exception e) {
                    throw new RuntimeException("XML 注入 setter 失败: " + setterName, e);
                }
            }
        }
        return false;
    }

    /**
     * 按名字拿 Bean。还没创建的会在这里创建。
     * 若它正在创建且半成品已暴露，直接返回半成品以解开循环依赖。
     */
    public Object getBean(String name) {
        if (closed) {
            throw new RuntimeException("容器已关闭");
        }
        BeanDefinition definition = beanDefinitions.get(name);
        if (definition != null && definition.prototype) {
            if (beansInCreation.contains(name)) {
                throw new RuntimeException("原型 Bean 不支持循环依赖: " + cyclePath(name));
            }
            return createPrototype(definition);
        }
        if (finishedBeanNames.contains(name)) {
            return beans.get(name);
        }
        if (beansInCreation.contains(name)) {
            Object early = earlySingletonObjects.get(name);
            if (early != null) {
                System.out.println("循环依赖，提前暴露: " + cyclePath(name));
                return early;
            }
            throw new RuntimeException("检测到无法解决的循环依赖: " + cyclePath(name));
        }
        if (!beanDefinitions.containsKey(name)) {
            return null;
        }
        return createBean(name);
    }

    /**
     * 按类型拿 Bean。
     * 多个同类型候选时，选唯一的 Primary；没有或超过一个 Primary 则报错。
     */
    public <T> T getBean(Class<T> type) {
        return resolveByType(type, "");
    }

    /**
     * 按类型拿 Bean，并用限定名消歧。
     */
    public <T> T getBean(Class<T> type, String qualifier) {
        return resolveByType(type, qualifier);
    }

    /**
     * 该类型的全部 Bean，按注册顺序。没有候选时返回空列表。
     */
    public <T> List<T> getBeans(Class<T> type) {
        if (closed) {
            throw new RuntimeException("容器已关闭");
        }
        return beansOfType(type, "");
    }

    /**
     * 该类型的全部 Bean，key 是 Bean 名。没有候选时返回空 Map。
     */
    public <T> Map<String, T> getBeansOfType(Class<T> type) {
        if (closed) {
            throw new RuntimeException("容器已关闭");
        }
        return mapOfType(type, "");
    }

    /**
     * 同类型多 Bean 的完整消歧：
     * - 指定了 qualifier：只保留限定名匹配的候选
     * - 0 个候选：返回 null
     * - 1 个候选：直接返回
     * - 多个候选：选唯一的 Primary，否则报错
     */
    private <T> T resolveByType(Class<T> type, String qualifier) {
        List<BeanDefinition> candidates = findDefinitionsByType(type);

        if (qualifier != null && !qualifier.isEmpty()) {
            List<BeanDefinition> matched = new ArrayList<>();
            for (BeanDefinition definition : candidates) {
                if (qualifierMatches(definition, qualifier)) {
                    matched.add(definition);
                }
            }
            if (matched.isEmpty()) {
                throw new RuntimeException(
                        "找不到类型为 " + type.getName()
                                + " 且 @MyQualifier(\"" + qualifier + "\") 的 Bean");
            }
            if (matched.size() > 1) {
                throw new RuntimeException(
                        "找到多个类型为 " + type.getName()
                                + " 且 @MyQualifier(\"" + qualifier + "\") 的 Bean: "
                                + joinBeanNames(matched));
            }
            return adapt(type, matched.get(0).name);
        }

        if (candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() == 1) {
            return adapt(type, candidates.get(0).name);
        }

        List<BeanDefinition> primaries = new ArrayList<>();
        for (BeanDefinition definition : candidates) {
            if (primaryBeanNames.contains(definition.name)) {
                primaries.add(definition);
            }
        }
        if (primaries.size() == 1) {
            return adapt(type, primaries.get(0).name);
        }
        if (primaries.size() > 1) {
            throw new RuntimeException(
                    "类型 " + type.getName() + " 有多个 Primary Bean: "
                            + joinBeanNames(primaries)
                            + "，同一类型只能有一个 Primary");
        }
        throw new RuntimeException(
                "找到多个类型为 " + type.getName() + " 的 Bean: "
                        + joinBeanNames(candidates)
                        + "，请用 @MyQualifier 指定，或给其中一个加 @MyPrimary / XML primary");
    }

    /**
     * 接口注入拿到代理；如果要的是实现类本身，而容器里放的是 JDK 代理，则退回原始对象。
     */
    private <T> T adapt(Class<T> type, String beanName) {
        Object exposed = getBean(beanName);
        if (exposed == null || type.isInstance(exposed)) {
            return type.cast(exposed);
        }
        Object target = targets.get(beanName);
        if (target != null && type.isInstance(target)) {
            return type.cast(target);
        }
        return type.cast(exposed);
    }

    private List<BeanDefinition> findDefinitionsByType(Class<?> type) {
        List<BeanDefinition> result = new ArrayList<>();
        for (BeanDefinition definition : beanDefinitions.values()) {
            if (type.isAssignableFrom(definition.clazz)) {
                result.add(definition);
            }
        }
        return result;
    }

    /**
     * 限定名匹配：对上 Bean 名，或 Bean 类上的 @MyQualifier。
     */
    private boolean qualifierMatches(BeanDefinition definition, String qualifier) {
        if (qualifier.equals(definition.name)) {
            return true;
        }
        MyQualifier annotation = definition.clazz.getAnnotation(MyQualifier.class);
        return annotation != null && qualifier.equals(annotation.value());
    }

    private String joinBeanNames(List<BeanDefinition> definitions) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < definitions.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(definitions.get(i).name);
        }
        return sb.toString();
    }

    private String cyclePath(String requestedName) {
        StringBuilder sb = new StringBuilder();
        for (String name : creationStack) {
            if (sb.length() > 0) {
                sb.append(" -> ");
            }
            sb.append(name);
        }
        if (sb.length() > 0) {
            sb.append(" -> ");
        }
        sb.append(requestedName);
        return sb.toString();
    }

    /**
     * 按创建完成的相反顺序销毁单例。重复调用不会再次销毁。
     */
    public void close() {
        if (closed) {
            return;
        }
        closed = true;
        for (int i = creationOrder.size() - 1; i >= 0; i--) {
            String beanName = creationOrder.get(i);
            Object target = targets.get(beanName);
            BeanDefinition definition = beanDefinitions.get(beanName);
            if (target == null || definition == null) {
                continue;
            }
            invokeLifecycle(target, MyPreDestroy.class, definition.destroyMethod, "销毁");
        }
    }

    /**
     * 先调用注解方法（父类在前），再调用 XML 指定的方法。同名方法只调用一次。
     */
    private void invokeLifecycle(Object target,
                                 Class<? extends Annotation> annotation,
                                 String xmlMethodName,
                                 String label) {
        List<Method> methods = new ArrayList<>();
        collectLifecycleMethods(target.getClass(), annotation, methods);
        Set<String> called = new HashSet<>();
        for (Method method : methods) {
            invokeNoArg(target, method, label);
            called.add(method.getName());
        }
        if (xmlMethodName == null || xmlMethodName.isEmpty() || called.contains(xmlMethodName)) {
            return;
        }
        Method xmlMethod = findNoArgMethod(target.getClass(), xmlMethodName);
        if (xmlMethod == null) {
            throw new RuntimeException(
                    target.getClass().getName() + " 找不到无参方法: " + xmlMethodName);
        }
        invokeNoArg(target, xmlMethod, label);
    }

    private void collectLifecycleMethods(Class<?> clazz,
                                         Class<? extends Annotation> annotation,
                                         List<Method> result) {
        if (clazz == null || clazz == Object.class) {
            return;
        }
        collectLifecycleMethods(clazz.getSuperclass(), annotation, result);
        for (Method method : clazz.getDeclaredMethods()) {
            if (!method.isAnnotationPresent(annotation)) {
                continue;
            }
            if (method.getParameterCount() != 0) {
                throw new RuntimeException(
                        annotation.getSimpleName() + " 方法必须无参: "
                                + clazz.getName() + "." + method.getName());
            }
            result.add(method);
        }
    }

    private Method findNoArgMethod(Class<?> clazz, String methodName) {
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            try {
                return current.getDeclaredMethod(methodName);
            } catch (NoSuchMethodException e) {
                current = current.getSuperclass();
            }
        }
        return null;
    }

    private void invokeNoArg(Object target, Method method, String label) {
        try {
            method.setAccessible(true);
            method.invoke(target);
            System.out.println(label + ": " + target.getClass().getSimpleName()
                    + "." + method.getName());
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            throw new RuntimeException(
                    label + "失败: " + target.getClass().getName() + "." + method.getName(), cause);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(
                    label + "失败: " + target.getClass().getName() + "." + method.getName(), e);
        }
    }

    private static final class BeanDefinition {
        private final String name;
        private final Class<?> clazz;
        private final List<XmlBeanDefinition.ConstructorArg> constructorArgs;
        private final List<XmlBeanDefinition.Property> properties;
        private final String initMethod;
        private final String destroyMethod;
        private final boolean prototype;
        private final boolean lazy;

        private BeanDefinition(String name,
                               Class<?> clazz,
                               List<XmlBeanDefinition.ConstructorArg> constructorArgs,
                               List<XmlBeanDefinition.Property> properties,
                               String initMethod,
                               String destroyMethod,
                               boolean prototype,
                               boolean lazy) {
            this.name = name;
            this.clazz = clazz;
            this.constructorArgs = constructorArgs;
            this.properties = properties;
            this.initMethod = initMethod;
            this.destroyMethod = destroyMethod;
            this.prototype = prototype;
            this.lazy = lazy;
        }
    }
}
