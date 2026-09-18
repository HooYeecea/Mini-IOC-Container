package com.miniioccontainer.context;

import com.miniioccontainer.annotation.MyAutowired;
import com.miniioccontainer.annotation.MyPrimary;
import com.miniioccontainer.annotation.MyQualifier;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MiniApplicationContext {

    // Bean 容器：key 是 Bean 名字，value 是 Bean 实例
    private final Map<String, Object> beans = new HashMap<>();

    public MiniApplicationContext(String basePackage) {
        // 1. 扫描包，拿到所有带 @MyComponent 的类
        List<Class<?>> classes = PackageScanner.scan(basePackage);

        // 2. 阶段 1：实例化所有 Bean，放进容器
        for (Class<?> clazz : classes) {
            try {
                Object instance = clazz.getDeclaredConstructor().newInstance();
                String beanName = getBeanName(clazz);
                beans.put(beanName, instance);
                System.out.println("注册 Bean: " + beanName);
            } catch (Exception e) {
                throw new RuntimeException("创建 Bean 失败: " + clazz.getName(), e);
            }
        }

        // 3. 阶段 2：给每个 Bean 注入依赖
        for (Object bean : beans.values()) {
            injectDependencies(bean);
        }
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
     * 3. 按类型查找；多个候选时选唯一的 @MyPrimary
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

    /**
     * 按名字拿 Bean
     */
    public Object getBean(String name) {
        return beans.get(name);
    }

    /**
     * 按类型拿 Bean。
     * 多个同类型候选时，选唯一的 @MyPrimary；没有或超过一个 Primary 则报错。
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
     * - 多个候选：选唯一的 @MyPrimary，否则报错
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
            if (entry.getValue().getClass().isAnnotationPresent(MyPrimary.class)) {
                primaries.add(entry);
            }
        }
        if (primaries.size() == 1) {
            return type.cast(primaries.get(0).getValue());
        }
        if (primaries.size() > 1) {
            throw new RuntimeException(
                    "类型 " + type.getName() + " 有多个 @MyPrimary Bean: "
                            + joinBeanNames(primaries)
                            + "，同一类型只能有一个 @MyPrimary");
        }
        throw new RuntimeException(
                "找到多个类型为 " + type.getName() + " 的 Bean: "
                        + joinBeanNames(candidates)
                        + "，请用 @MyQualifier 指定，或给其中一个加 @MyPrimary");
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
