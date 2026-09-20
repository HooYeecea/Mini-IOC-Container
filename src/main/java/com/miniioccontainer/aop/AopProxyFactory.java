package com.miniioccontainer.aop;

import java.lang.reflect.Proxy;
import java.util.List;

/**
 * 用 JDK Proxy 把目标对象包成代理。
 */
public class AopProxyFactory {

    public static Object create(Object target, Class<?>[] interfaces, List<AopAdvice> advices) {
        ClassLoader classLoader = target.getClass().getClassLoader();
        if (classLoader == null) {
            classLoader = Thread.currentThread().getContextClassLoader();
        }
        return Proxy.newProxyInstance(
                classLoader,
                interfaces,
                new MiniAopInterceptor(target, advices));
    }
}
