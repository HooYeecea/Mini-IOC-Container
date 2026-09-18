package com.miniioccontainer.context;

import com.miniioccontainer.annotation.MyAutowired;
import com.miniioccontainer.annotation.MyPrimary;
import com.miniioccontainer.annotation.MyQualifier;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MiniApplicationContext {

    // Bean 容器：key 是 Bean 名字，value 是 Bean 实例
    private final Map<String, Object> beans = new HashMap<>();
    // XML primary="true" 或类上 @MyPrimary 的 Bean 名
    private final Set<String> primaryBeanNames = new HashSet<>();
    // 仅 XML 注册的 Bean 需要按 <property ref> 注入
    private final Map<String, List<XmlBeanDefinition.Property>> xmlProperties = new HashMap<>();

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

        for (Object bean : beans.values()) {
            injectDependencies(bean);
        }
        applyXmlPropertyInjections();
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

    private void registerAnnotationBeans(String basePackage) {
        List<Class<?>> classes = PackageScanner.scan(basePackage);
        for (Class<?> clazz : classes) {
            try {
                String beanName = getBeanName(clazz);
                Object existing = beans.get(beanName);
                if (existing != null) {
                    if (existing.getClass().equals(clazz)) {
                        continue;
                    }
                    throw new RuntimeException(
                            "Bean 名重复: " + beanName
                                    + "，已有 " + existing.getClass().getName()
                                    + "，注解又注册 " + clazz.getName());
                }
                Object instance = clazz.getDeclaredConstructor().newInstance();
                beans.put(beanName, instance);
                if (clazz.isAnnotationPresent(MyPrimary.class)) {
                    primaryBeanNames.add(beanName);
                }
                System.out.println("注册 Bean: " + beanName + " (注解)");
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException("创建 Bean 失败: " + clazz.getName(), e);
            }
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

        if (hasBeanOfExactClass(clazz)) {
            String xmlName = definition.getId().isEmpty() ? getBeanName(clazz) : definition.getId();
            System.out.println("跳过 XML Bean: " + xmlName
                    + "，类 " + clazz.getName() + " 已由注解注册");
            return;
        }

        String beanName = definition.getId().isEmpty() ? getBeanName(clazz) : definition.getId();
        Object existing = beans.get(beanName);
        if (existing != null) {
            throw new RuntimeException(
                    "Bean 名重复: " + beanName
                            + "，已有 " + existing.getClass().getName()
                            + "，XML 又注册 " + clazz.getName());
        }

        try {
            Object instance = clazz.getDeclaredConstructor().newInstance();
            beans.put(beanName, instance);
            if (definition.isPrimary()) {
                primaryBeanNames.add(beanName);
            }
            if (!definition.getProperties().isEmpty()) {
                xmlProperties.put(beanName, definition.getProperties());
            }
            System.out.println("注册 Bean: " + beanName + " (XML)");
        } catch (Exception e) {
            throw new RuntimeException("创建 XML Bean 失败: " + clazz.getName(), e);
        }
    }

    private boolean hasBeanOfExactClass(Class<?> clazz) {
        for (Object bean : beans.values()) {
            if (bean.getClass().equals(clazz)) {
                return true;
            }
        }
        return false;
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
                System.out.println("注入: " + clazz.getSimpleName()
                        + "." + field.getName() + " <- "
                        + dependency.getClass().getSimpleName());
            } catch (IllegalAccessException e) {
                throw new RuntimeException("注入失败: " + field.getName(), e);
            }
        }
    }

    private void applyXmlPropertyInjections() {
        for (Map.Entry<String, List<XmlBeanDefinition.Property>> entry : xmlProperties.entrySet()) {
            String beanName = entry.getKey();
            Object bean = beans.get(beanName);
            for (XmlBeanDefinition.Property property : entry.getValue()) {
                Object dependency = getBean(property.getRef());
                if (dependency == null) {
                    throw new RuntimeException(
                            "XML 找不到 ref=\"" + property.getRef()
                                    + "\" 的 Bean（注入到 " + beanName
                                    + "." + property.getName() + "）");
                }
                if (!injectFieldByName(bean, property.getName(), dependency)
                        && !injectSetter(bean, property.getName(), dependency)) {
                    throw new RuntimeException(
                            "XML Bean " + beanName + " 找不到属性: " + property.getName());
                }
                System.out.println("XML 注入: " + bean.getClass().getSimpleName()
                        + "." + property.getName() + " <- " + property.getRef());
            }
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
     * 按名字拿 Bean
     */
    public Object getBean(String name) {
        return beans.get(name);
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
        List<Map.Entry<String, Object>> candidates = findBeansByType(type);

        if (qualifier != null && !qualifier.isEmpty()) {
            List<Map.Entry<String, Object>> matched = new ArrayList<>();
            for (Map.Entry<String, Object> entry : candidates) {
                if (qualifierMatches(entry.getKey(), entry.getValue(), qualifier)) {
                    matched.add(entry);
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
            return type.cast(matched.get(0).getValue());
        }

        if (candidates.isEmpty()) {
            return null;
        }
        if (candidates.size() == 1) {
            return type.cast(candidates.get(0).getValue());
        }

        List<Map.Entry<String, Object>> primaries = new ArrayList<>();
        for (Map.Entry<String, Object> entry : candidates) {
            if (primaryBeanNames.contains(entry.getKey())) {
                primaries.add(entry);
            }
        }
        if (primaries.size() == 1) {
            return type.cast(primaries.get(0).getValue());
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

    private List<Map.Entry<String, Object>> findBeansByType(Class<?> type) {
        List<Map.Entry<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, Object> entry : beans.entrySet()) {
            if (type.isInstance(entry.getValue())) {
                result.add(entry);
            }
        }
        return result;
    }

    /**
     * 限定名匹配：字段上的 @MyQualifier 值，对上 Bean 名，或 Bean 类上的 @MyQualifier。
     */
    private boolean qualifierMatches(String beanName, Object bean, String qualifier) {
        if (qualifier.equals(beanName)) {
            return true;
        }
        MyQualifier annotation = bean.getClass().getAnnotation(MyQualifier.class);
        return annotation != null && qualifier.equals(annotation.value());
    }

    private String joinBeanNames(List<Map.Entry<String, Object>> entries) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(entries.get(i).getKey());
        }
        return sb.toString();
    }
}
