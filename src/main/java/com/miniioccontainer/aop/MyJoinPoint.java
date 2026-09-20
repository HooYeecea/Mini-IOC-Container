package com.miniioccontainer.aop;

import java.lang.reflect.Method;

/**
 * 当前这次方法调用。调用 proceed() 才会进入下一个通知或真正的目标方法。
 */
public interface MyJoinPoint {

    Object proceed() throws Throwable;

    Method getMethod();

    Object[] getArgs();

    Object getTarget();
}
