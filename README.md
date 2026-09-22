# Mini IOC Container

A small, from-scratch IoC container for learning how Spring-style dependency injection works.

It can register beans from annotations, from XML, or from both. After startup, they live in the same container and can depend on each other.

Chinese version: [README(CN).md](README(CN).md)

Part of the parent [MiniSpring](../README.md) reactor. MiniMVC uses this container to discover controllers and advice beans.

## Features

- Package scan and `@MyComponent` bean registration
- Field injection with `@MyAutowired`; constructor injection (`@MyAutowired` on ctor / XML `<constructor-arg>`)
- Collection injection: `List` / `Map` of a type
- Disambiguation: `@MyPrimary` / XML `primary`, `@MyQualifier` / `@MyAutowired(name=...)`
- Scopes: `@MyScope` / XML `scope` (`singleton` / `prototype`)
- Lazy singletons: `@MyLazy` / XML `lazy-init`
- Lifecycle: `@MyPostConstruct` / `@MyPreDestroy`, XML `init-method` / `destroy-method`, `context.close()`
- Properties: `@MyValue("${...}")`, XML `<property-placeholder>` / `value="${...}"`
- XML: `<component-scan>`, `<bean>`, `<property ref|value>`, `<constructor-arg>`
- Same class in annotation + XML → **annotation wins**
- Simple AOP: `@MyLog` pointcut + `@MyAround`, JDK dynamic proxy (interface-only)
- Early singleton exposure for simple circular references (singleton field injection)

## Requirements

- JDK 21
- Maven 3.6+

## Quick start

From the parent reactor:

```bash
mvn -pl MiniIOCContainer -am compile exec:java -Dexec.mainClass=com.miniioccontainer.Main
```

Or inside this module:

```bash
mvn compile exec:java -Dexec.mainClass=com.miniioccontainer.Main
```

`Main` loads `beans.xml`, which scans annotation beans and also registers XML-only beans.

Annotation-only:

```java
MiniApplicationContext context =
        new MiniApplicationContext("com.miniioccontainer.demo");
UserService userService = context.getBean(UserService.class);
```

Classpath XML (argument ending with `.xml`):

```java
MiniApplicationContext context = new MiniApplicationContext("beans.xml");
SmsService smsService = (SmsService) context.getBean("smsService");
```

## Annotations

| Annotation | Target | Role |
| --- | --- | --- |
| `@MyComponent` | class | Register as a bean (default name: decapitalized simple name) |
| `@MyAutowired` | field / constructor | Inject by type (optional `name`) |
| `@MyPrimary` | class | Preferred candidate when several beans share a type |
| `@MyQualifier("beanName")` | field or class | Pick one candidate by name |
| `@MyScope("prototype")` | class | Bean scope (`singleton` default) |
| `@MyLazy` | class | Lazy singleton |
| `@MyPostConstruct` / `@MyPreDestroy` | method | Lifecycle callbacks |
| `@MyValue("${key:default}")` | field / ctor param | Property placeholder |
| `@MyAspect` / `@MyAround` / `@MyLog` | aspect AOP | JDK proxy around `@MyLog` methods |

## XML

Classpath file: `src/main/resources/beans.xml`

Supported tags:

- `<property-placeholder location="..."/>`
- `<component-scan base-package="..."/>`
- `<bean id scope primary lazy-init init-method destroy-method>`
- `<property name ref|value>`
- `<constructor-arg ref|value>`

## Resolution rules

When injecting by type:

1. `@MyAutowired(name = "...")` → by name
2. `@MyQualifier` → match bean name (or qualifier on the bean class)
3. Single candidate → use it
4. Multiple → unique Primary
5. Still ambiguous → fail fast

Annotation vs XML for the same class → keep annotation bean, skip XML entry.

## AOP

1. Mark target methods with `@MyLog` (class must implement an interface — JDK proxy).
2. Aspect: `@MyComponent` + `@MyAspect`, `@MyAround` calls `joinPoint.proceed()`.
3. Proxies are created **before** injection so fields receive the proxy.

Limitations: no CGLIB, no AspectJ `execution(...)`, no `@Before` / `@After`, no XML `<aop:config>`.

## Project layout

```text
src/main/java/com/miniioccontainer/
  annotation/          # IoC + AOP annotations
  aop/                 # JoinPoint, JDK proxy
  context/             # scanner, XML reader, MiniApplicationContext
  demo/                # sample beans and LogAspect
  Main.java
src/main/resources/
  beans.xml
  application.properties
```

## Out of scope

Learning container, not a Spring replacement: no CGLIB, no full Spring XML / AspectJ, no Boot-style auto-configuration.

## License

Personal practice project.
