package annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD) // 仅用于字段
@Retention(RetentionPolicy.RUNTIME) // 运行时注解
public @interface MyAutoWire {
}
