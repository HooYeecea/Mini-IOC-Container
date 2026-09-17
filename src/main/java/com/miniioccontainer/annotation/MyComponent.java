package com.miniioccontainer.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE) // 仅用于类
@Retention(RetentionPolicy.RUNTIME) // 运行时注解
public @interface MyComponent {
}
