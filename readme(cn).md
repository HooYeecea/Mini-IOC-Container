# Mini IOC Container

一个从零手写的迷你 IoC 容器，用来理解 Spring 风格的依赖注入。

Bean 可以用注解注册，也可以用 XML 注册，或两者一起用。启动后它们进入同一个容器，可以互相依赖。

## 功能

- 包扫描 + `@MyComponent` 注册 Bean
- `@MyAutowired` 字段注入
- 同类型多个 Bean 的消歧：
  - `@MyPrimary` / XML `primary="true"`
  - `@MyQualifier` 或 `@MyAutowired(name = "...")`
- XML 配置：`<component-scan>`、`<bean>`、`<property ref>`
- 同一个类既打了注解又写了 XML 时，**以注解为准**

## 环境

- JDK 21
- Maven 3.6+

## 快速开始

```bash
mvn compile exec:java -Dexec.mainClass=com.miniioccontainer.Main
```

`Main` 会加载 `beans.xml`：既扫描注解 Bean，也注册纯 XML Bean。

也可以只传包名，走纯注解：

```java
MiniApplicationContext context =
        new MiniApplicationContext("com.miniioccontainer.demo");

UserService userService = context.getBean(UserService.class);
```

或者从 classpath 上的 XML 启动：

```java
MiniApplicationContext context = new MiniApplicationContext("beans.xml");
SmsService smsService = (SmsService) context.getBean("smsService");
```

构造参数以 `.xml` 结尾时按 XML 处理，否则当作扫描包名。

## 注解

| 注解 | 作用位置 | 作用 |
| --- | --- | --- |
| `@MyComponent` | 类 | 把该类注册为 Bean。默认名是类名首字母小写（`OrderServiceImpl` → `orderServiceImpl`）。 |
| `@MyAutowired` | 字段 | 按类型注入。可用 `name` 按 Bean 名注入。 |
| `@MyPrimary` | 类 | 同类型有多个候选时的首选。 |
| `@MyQualifier("beanName")` | 字段或类 | 按名字精确指定，优先级高于 `@MyPrimary`。 |

示例：

```java
@MyComponent
public class UserService {

    @MyAutowired
    private OrderService orderService;          // 命中 @MyPrimary

    @MyAutowired
    @MyQualifier("orderServiceV2")
    private OrderService orderServiceV2;        // 精确指定
}
```

## XML

配置文件：`src/main/resources/beans.xml`

```xml
<beans>
    <component-scan base-package="com.miniioccontainer.demo"/>

    <bean id="smsService" class="com.miniioccontainer.demo.SmsService">
        <property name="orderService" ref="orderServiceImpl"/>
    </bean>

    <!-- 跳过：OrderServiceImpl 已经由 @MyComponent 注册 -->
    <bean id="orderServiceFromXml" class="com.miniioccontainer.demo.OrderServiceImpl"/>
</beans>
```

支持的标签：

- `<component-scan base-package="..."/>`
- `<bean id="..." class="..." primary="true"/>`
- `<property name="..." ref="..."/>`（先按字段注入，找不到再走 setter）

## 消歧规则

按类型注入时：

1. `@MyAutowired(name = "...")` → 按名字
2. `@MyQualifier` → 匹配 Bean 名（或类上的 Qualifier）
3. 该类型只有一个候选 → 直接用
4. 多个候选 → 用唯一的 Primary
5. 仍然无法确定 → 报错

注解和 XML 混用时：

- 只有注解 → 走注解 Bean
- 只有 XML → 走 XML Bean
- 同一个类两边都配了 → 保留注解 Bean，跳过 XML
- 不同类抢同一个 Bean 名 → 报错

## 项目结构

```
src/main/java/com/miniioccontainer/
  annotation/          # @MyComponent、@MyAutowired、@MyPrimary、@MyQualifier
  context/             # 包扫描、XML 解析、MiniApplicationContext
  demo/                # 示例 Bean
  Main.java
src/main/resources/
  beans.xml
```

## 明确不做的事

这是学习用容器，不是 Spring 替代品。没有构造器注入、Bean 作用域、生命周期回调、AOP，也不支持完整的 Spring XML Schema。
