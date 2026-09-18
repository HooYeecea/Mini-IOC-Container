package com.miniioccontainer.context;

import com.miniioccontainer.annotation.MyAutowired;
import com.miniioccontainer.annotation.MyComponent;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MiniApplicationContext {

    private final Map<String, Object> beans = new HashMap<>();

    public MiniApplicationContext(String basePackage) {
        List<Class<?>> classes = PackageScanner.scan(basePackage);

        // 阶段 1：实例化所有 Bean，放进容器
        for (Class<?> clazz : classes) {
            try {
                Object instance = clazz.getDeclaredConstructor().newInstance();
                String beanName = clazz.getSimpleName();
                beans.put(beanName, instance);
                System.out.println("注册 Bean: " + beanName);
            } catch (Exception e) {
                throw new RuntimeException("创建 Bean 失败: " + clazz.getName(), e);
            }
        }

        // 阶段 2：给每个 Bean 注入依赖
        for (Object bean : beans.values()) {
            injectDependencies(bean);
        }
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

            // 按类型从容器里找 Bean
            Object dependency = getBean(field.getType());
            if (dependency == null) {
                throw new RuntimeException(
                        "找不到依赖: " + field.getType().getName()
                                + "（注入到 " + clazz.getName() + "）");
            }

            try {
                // 允许访问 private 字段
                field.setAccessible(true);
                field.set(bean, dependency);
                System.out.println("注入: " + clazz.getSimpleName()
                        + "." + field.getName() + " <- " + dependency.getClass().getSimpleName());
            } catch (IllegalAccessException e) {
                throw new RuntimeException("注入失败: " + field.getName(), e);
            }
        }
    }

    public Object getBean(String name) {
        return beans.get(name);
    }

    public <T> T getBean(Class<T> type) {
        for (Object bean : beans.values()) {
            if (type.isInstance(bean)) {
                return type.cast(bean);
            }
        }
        return null;
    }
}