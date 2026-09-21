package com.miniioccontainer.aop;

import com.miniioccontainer.annotation.MyLog;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;

/**
 * JDK 动态代理：只拦截目标类上带 @MyLog 的方法。
 */
public class MiniAopInterceptor implements InvocationHandler {

    private final Object target;
    private final List<AopAdvice> advices;

    public MiniAopInterceptor(Object target, List<AopAdvice> advices) {
        this.target = target;
        this.advices = advices;
    }

    public Object getTarget() {
        return target;
    }

    public static boolean isMiniAopProxy(Object bean) {
        return bean != null
                && Proxy.isProxyClass(bean.getClass())
                && Proxy.getInvocationHandler(bean) instanceof MiniAopInterceptor;
    }

    public static Object unwrap(Object bean) {
        if (isMiniAopProxy(bean)) {
            return ((MiniAopInterceptor) Proxy.getInvocationHandler(bean)).getTarget();
        }
        return bean;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        // 处理 null 参数
        if (args == null) {
            args = new Object[0];
        }
        // 处理 Object 类的方法，直接调用目标方法
        if (method.getDeclaringClass() == Object.class) {
            return method.invoke(target, args);
        }
        // 处理目标方法，直接调用目标方法
        Method targetMethod = resolveTargetMethod(method);
        if (targetMethod == null || !targetMethod.isAnnotationPresent(MyLog.class)) {
            return method.invoke(target, args);
        }
        return new JoinPointImpl(target, targetMethod, args, advices).proceed();
    }
    // 处理目标方法，直接调用目标方法
    private Method resolveTargetMethod(Method interfaceMethod) {
        try {
            return target.getClass().getMethod(
                    interfaceMethod.getName(), interfaceMethod.getParameterTypes());
        } catch (NoSuchMethodException e) {
            try {
                Method declared = target.getClass().getDeclaredMethod(
                        interfaceMethod.getName(), interfaceMethod.getParameterTypes());
                declared.setAccessible(true);
                return declared;
            } catch (NoSuchMethodException ignored) {
                return null;
            }
        }
    }

    private static class JoinPointImpl implements MyJoinPoint {
        private final Object target;
        private final Method targetMethod;
        private final Object[] args;
        private final List<AopAdvice> advices;
        private int adviceIndex;

        private JoinPointImpl(Object target,
                              Method targetMethod,
                              Object[] args,
                              List<AopAdvice> advices) {
            this.target = target;
            this.targetMethod = targetMethod;
            this.args = args;
            this.advices = advices;
        }

        @Override
        public Object proceed() throws Throwable {
            if (adviceIndex < advices.size()) {
                AopAdvice advice = advices.get(adviceIndex++);
                return advice.invoke(this);
            }
            try {
                targetMethod.setAccessible(true);
                return targetMethod.invoke(target, args);
            } catch (InvocationTargetException e) {
                if (e.getCause() != null) {
                    throw e.getCause();
                }
                throw e;
            }
        }

        @Override
        public Method getMethod() {
            return targetMethod;
        }

        @Override
        public Object[] getArgs() {
            return args;
        }

        @Override
        public Object getTarget() {
            return target;
        }
    }
}
