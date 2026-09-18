package com.miniioccontainer.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标在 Bean 类上：同类型有多个候选时，优先注入这个。
 * 同一类型只能有一个 @MyPrimary，否则报错。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface MyPrimary {
}
