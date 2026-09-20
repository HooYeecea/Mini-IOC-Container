package com.miniioccontainer.aop;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * 一条 @MyAround 通知：切面实例 + 通知方法。
 */
public class AopAdvice {

    private final Object aspect;
    private final Method method;

    public AopAdvice(Object aspect, Method method) {
        this.aspect = aspect;
        this.method = method;
        this.method.setAccessible(true);
    }

    public Object invoke(MyJoinPoint joinPoint) throws Throwable {
        try {
            return method.invoke(aspect, joinPoint);
        } catch (InvocationTargetException e) {
            if (e.getCause() != null) {
                throw e.getCause();
            }
            throw e;
        }
    }
}
