package com.miniioccontainer.context;


import com.miniioccontainer.annotation.MyComponent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MiniApplicationContext {

    // Bean 容器：key 是 Bean 名字，value 是 Bean 实例
    private final Map<String, Object> beans = new HashMap<>();

    public MiniApplicationContext(String basePackage) {
        // 1. 扫描包
        List<Class<?>> classes = PackageScanner.scan(basePackage);

        // 2. 遍历，创建实例，放进容器
        for (Class<?> clazz : classes) {
            try {
                Object instance = clazz.getDeclaredConstructor().newInstance(); // 创建实例
                String beanName = clazz.getSimpleName();  // 先用简单类名做 key
                beans.put(beanName, instance);// 把beans实例放进容器
                System.out.println("注册 Bean: " + beanName);
            } catch (Exception e) {
                throw new RuntimeException("创建 Bean 失败: " + clazz.getName(), e);
            }
        }
    }

    // 按名字拿 Bean
    public Object getBean(String name) {
        return beans.get(name);
    }

    // 按类型拿 Bean（后面注入会用到）
    public <T> T getBean(Class<T> type) {
        for (Object bean : beans.values()) {
            if (type.isInstance(bean)) {
                return type.cast(bean);
            }
        }
        return null;
    }
}
