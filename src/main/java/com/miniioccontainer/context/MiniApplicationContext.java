package com.miniioccontainer.context;

import com.miniioccontainer.annotation.MyAutowired;
import com.miniioccontainer.annotation.MyComponent;

import java.lang.reflect.Field;
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
                // 有 name，按名字注入
                dependency = getBean(name);
                if (dependency == null) {
                    throw new RuntimeException(
                            "找不到名为 " + name + " 的 Bean（注入到 "
                                    + clazz.getName() + "." + field.getName() + "）");
                }
            } else {
                // 没 name，按类型注入
                dependency = getBean(field.getType());
                if (dependency == null) {
                    throw new RuntimeException(
                            "找不到类型为 " + field.getType().getName()
                                    + " 的 Bean（注入到 "
                                    + clazz.getName() + "." + field.getName() + "）");
                }
            }

            try {
                // 允许访问 private 字段
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
     * 按类型拿 Bean
     * 注意：如果容器里有多个同类型 Bean，这里只返回第一个，行为不确定。
     * 后面会改进成"多于一个就报错"。
     */
    public <T> T getBean(Class<T> type) {
        Object matched = null;
        for (Object bean : beans.values()) {
            if (type.isInstance(bean)) {
                if (matched != null) {
                    throw new RuntimeException(
                            "找到多个类型为 " + type.getName() + " 的 Bean: "
                                    + matched.getClass().getName() + " 和 "
                                    + bean.getClass().getName()
                                    + "，请用 @MyAutowired(name = ...) 明确指定");
                }
                matched = bean;
            }
        }
        return type.cast(matched);
    }
}