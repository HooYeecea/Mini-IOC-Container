package com.miniioccontainer.demo;

import com.miniioccontainer.annotation.MyAround;
import com.miniioccontainer.annotation.MyAspect;
import com.miniioccontainer.annotation.MyComponent;
import com.miniioccontainer.aop.MyJoinPoint;

@MyComponent
@MyAspect
public class LogAspect {

    @MyAround
    public Object log(MyJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getMethod().getName();
        System.out.println("AOP before: " + methodName
                + " on " + joinPoint.getTarget().getClass().getSimpleName());
        Object result = joinPoint.proceed();
        System.out.println("AOP after: " + methodName + " -> " + result);
        return result;
    }
}
