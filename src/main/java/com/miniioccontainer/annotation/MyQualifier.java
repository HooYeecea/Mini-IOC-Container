package com.miniioccontainer.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 按名字精确指定要注入哪一个 Bean。
 * 可标在注入字段上，也可标在 Bean 类上给它起一个限定名。
 * 优先级高于 @MyPrimary。
 */
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface MyQualifier {
    String value();
}
