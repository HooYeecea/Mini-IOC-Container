# Mini IOC Container

A small, from-scratch IoC container for learning how Spring-style dependency injection works.

It can register beans from annotations, from XML, or from both. After startup, they live in the same container and can depend on each other.

## Features

- Package scan and `@MyComponent` bean registration
- Field injection with `@MyAutowired`
- Multiple beans of the same type:
  - `@MyPrimary` / XML `primary="true"`
  - `@MyQualifier` or `@MyAutowired(name = "...")`
- XML config: `<component-scan>`, `<bean>`, `<property ref>`
- If the same class is configured by both annotation and XML, the **annotation wins**

## Requirements

- JDK 21
- Maven 3.6+

## Quick start

```bash
mvn compile exec:java -Dexec.mainClass=com.miniioccontainer.Main
```

`Main` loads `beans.xml`, which scans annotation beans and also registers XML-only beans.

You can also start from a package name, annotation-only:

```java
MiniApplicationContext context =
        new MiniApplicationContext("com.miniioccontainer.demo");

UserService userService = context.getBean(UserService.class);
```

Or from a classpath XML file:

```java
MiniApplicationContext context = new MiniApplicationContext("beans.xml");
SmsService smsService = (SmsService) context.getBean("smsService");
```

If the constructor argument ends with `.xml`, it is treated as XML. Otherwise it is treated as a base package.

## Annotations

| Annotation | Target | Role |
| --- | --- | --- |
| `@MyComponent` | class | Register the class as a bean. Default name is the decapitalized simple class name (`OrderServiceImpl` → `orderServiceImpl`). |
| `@MyAutowired` | field | Inject a dependency by type. Use `name` to inject by bean name. |
| `@MyPrimary` | class | Preferred candidate when several beans share the same type. |
| `@MyQualifier("beanName")` | field or class | Pick one candidate by name. Takes precedence over `@MyPrimary`. |

Example:

```java
@MyComponent
public class UserService {

    @MyAutowired
    private OrderService orderService;          // hits @MyPrimary

    @MyAutowired
    @MyQualifier("orderServiceV2")
    private OrderService orderServiceV2;        // exact bean
}
```

## XML

Classpath file: `src/main/resources/beans.xml`

```xml
<beans>
    <component-scan base-package="com.miniioccontainer.demo"/>

    <bean id="smsService" class="com.miniioccontainer.demo.SmsService">
        <property name="orderService" ref="orderServiceImpl"/>
    </bean>

    <!-- skipped: OrderServiceImpl is already registered by @MyComponent -->
    <bean id="orderServiceFromXml" class="com.miniioccontainer.demo.OrderServiceImpl"/>
</beans>
```

Supported tags:

- `<component-scan base-package="..."/>`
- `<bean id="..." class="..." primary="true"/>`
- `<property name="..." ref="..."/>` (field first, then setter)

## Resolution rules

When injecting by type:

1. `@MyAutowired(name = "...")` → by name
2. `@MyQualifier` → match bean name (or a qualifier on the bean class)
3. Single candidate of that type → use it
4. Multiple candidates → use the unique Primary
5. Still ambiguous → fail fast

When mixing annotation and XML:

- Annotation-only class → annotation bean
- XML-only class → XML bean
- Same class in both → keep the annotation bean, skip the XML entry
- Different classes, same bean name → error

## Project layout

```
src/main/java/com/miniioccontainer/
  annotation/          # @MyComponent, @MyAutowired, @MyPrimary, @MyQualifier
  context/             # scanner, XML reader, MiniApplicationContext
  demo/                # sample beans
  Main.java
src/main/resources/
  beans.xml
```

## Out of scope

This is a learning container, not a Spring replacement. It does not implement constructor injection, bean scopes, lifecycle callbacks, AOP, or the full Spring XML schema.
