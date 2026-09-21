package com.miniioccontainer.context;

import com.miniioccontainer.annotation.MyAround;
import com.miniioccontainer.annotation.MyAspect;
import com.miniioccontainer.annotation.MyAutowired;
import com.miniioccontainer.annotation.MyLog;
import com.miniioccontainer.annotation.MyPrimary;
import com.miniioccontainer.annotation.MyQualifier;
import com.miniioccontainer.aop.AopAdvice;
import com.miniioccontainer.aop.AopProxyFactory;
import com.miniioccontainer.aop.MiniAopInterceptor;
import com.miniioccontainer.aop.MyJoinPoint;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
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
    // XML primary="true" 或类上 @MyPrimary 的 Bean 名
    private final Set<String> primaryBeanNames = new HashSet<>();
    private List<AopAdvice> advices = List.of();

    /**
     * location 以 .xml 结尾：从 classpath 读 XML（可含 component-scan）。
     * 否则：当作包名，只扫 @MyComponent。
     */
    public MiniApplicationContext(String location) {
        if (location != null && location.endsWith(".xml")) {
            loadFromXml(location);
        } else {
            registerAnnotationBeans(location);
        }
        refresh();
    }

    private void loadFromXml(String xmlClasspath) {
        XmlBeanDefinitionReader.Result config = XmlBeanDefinitionReader.load(xmlClasspath);

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
            getBean(beanName);
        }
    }

    private void registerAnnotationBeans(String basePackage) {
        List<Class<?>> classes = PackageScanner.scan(basePackage);
        for (Class<?> clazz : classes) {
            String beanName = getBeanName(clazz);
            registerDefinition(beanName, clazz, clazz.isAnnotationPresent(MyPrimary.class),
                    List.of(), List.of(), "注解");
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
        registerDefinition(beanName, clazz, definition.isPrimary(),
                definition.getConstructorArgRefs(), definition.getProperties(), "XML");
    }

    private void registerDefinition(String beanName,
                                    Class<?> clazz,
                                    boolean primary,
                                    List<String> constructorArgRefs,
                                    List<XmlBeanDefinition.Property> properties,
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
        beanDefinitions.put(beanName,
                new BeanDefinition(beanName, clazz, constructorArgRefs, properties));
        if (primary) {
            primaryBeanNames.add(beanName);
        }
        System.out.println("注册 Bean: " + beanName + " (" + source + ")");
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
            if (!definition.constructorArgRefs.isEmpty()) {
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
        Object[] args = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            MyQualifier qualifier = parameters[i].getAnnotation(MyQualifier.class);
            String qualifierValue = qualifier == null ? "" : qualifier.value();
            Object dependency = resolveByType(types[i], qualifierValue);
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
        List<String> refs = definition.constructorArgRefs;
        Object[] args = new Object[refs.size()];
        for (int i = 0; i < refs.size(); i++) {
            Object dependency = getBean(refs.get(i));
            if (dependency == null) {
                throw new RuntimeException(
                        "XML 找不到 constructor-arg ref=\"" + refs.get(i)
                                + "\" 的 Bean（" + definition.name + "）");
            }
            args[i] = dependency;
        }

        Constructor<?> matched = null;
        for (Constructor<?> constructor : definition.clazz.getDeclaredConstructors()) {
            if (constructor.getParameterCount() != args.length) {
                continue;
            }
            Class<?>[] types = constructor.getParameterTypes();
            boolean compatible = true;
            for (int i = 0; i < types.length; i++) {
                if (!types[i].isInstance(args[i])) {
                    compatible = false;
                    break;
                }
            }
            if (compatible) {
                matched = constructor;
                break;
            }
        }
        if (matched == null) {
            throw new RuntimeException(
                    "XML Bean " + definition.name + " 找不到匹配 "
                            + args.length + " 个 constructor-arg 的构造器");
        }
        try {
            matched.setAccessible(true);
            System.out.println("XML 构造注入: " + definition.clazz.getSimpleName()
                    + " <- " + String.join(", ", refs));
            return matched.newInstance(args);
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
            sb.append(MiniAopInterceptor.unwrap(dependencies[i]).getClass().getSimpleName());
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

            earlySingletonObjects.remove(beanName);
            finishedBeanNames.add(beanName);
            return exposed;
        } finally {
            beansInCreation.remove(beanName);
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
     */
    private void injectDependencies(Object bean) {
        Class<?> clazz = bean.getClass();
        Field[] fields = clazz.getDeclaredFields();

        for (Field field : fields) {
            if (!field.isAnnotationPresent(MyAutowired.class)) {
                continue;
            }

            MyAutowired annotation = field.getAnnotation(MyAutowired.class);
            String name = annotation.name();

            Object dependency;
            if (!name.isEmpty()) {
                dependency = getBean(name);
                if (dependency == null) {
                    throw new RuntimeException(
                            "找不到名为 " + name + " 的 Bean（注入到 "
                                    + clazz.getName() + "." + field.getName() + "）");
                }
            } else {
                String qualifier = "";
                if (field.isAnnotationPresent(MyQualifier.class)) {
                    qualifier = field.getAnnotation(MyQualifier.class).value();
                }
                dependency = resolveByType(field.getType(), qualifier);
                if (dependency == null) {
                    throw new RuntimeException(
                            "找不到类型为 " + field.getType().getName()
                                    + " 的 Bean（注入到 "
                                    + clazz.getName() + "." + field.getName() + "）");
                }
            }

            try {
                field.setAccessible(true);
                field.set(bean, dependency);
                Object injected = MiniAopInterceptor.unwrap(dependency);
                System.out.println("注入: " + clazz.getSimpleName()
                        + "." + field.getName() + " <- "
                        + injected.getClass().getSimpleName()
                        + (injected != dependency ? " (AOP 代理)" : ""));
            } catch (IllegalAccessException e) {
                throw new RuntimeException("注入失败: " + field.getName(), e);
            }
        }
    }

    private void applyXmlProperties(BeanDefinition definition, Object bean) {
        for (XmlBeanDefinition.Property property : definition.properties) {
            Object dependency = getBean(property.getRef());
            if (dependency == null) {
                throw new RuntimeException(
                        "XML 找不到 ref=\"" + property.getRef()
                                + "\" 的 Bean（注入到 " + definition.name
                                + "." + property.getName() + "）");
            }
            if (!injectFieldByName(bean, property.getName(), dependency)
                    && !injectSetter(bean, property.getName(), dependency)) {
                throw new RuntimeException(
                        "XML Bean " + definition.name + " 找不到属性: " + property.getName());
            }
            System.out.println("XML 注入: " + bean.getClass().getSimpleName()
                    + "." + property.getName() + " <- " + property.getRef());
        }
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

    private static final class BeanDefinition {
        private final String name;
        private final Class<?> clazz;
        private final List<String> constructorArgRefs;
        private final List<XmlBeanDefinition.Property> properties;

        private BeanDefinition(String name,
                               Class<?> clazz,
                               List<String> constructorArgRefs,
                               List<XmlBeanDefinition.Property> properties) {
            this.name = name;
            this.clazz = clazz;
            this.constructorArgRefs = constructorArgRefs;
            this.properties = properties;
        }
    }
}
