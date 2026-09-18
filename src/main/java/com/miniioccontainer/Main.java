package com.miniioccontainer;

import com.miniioccontainer.context.MiniApplicationContext;
import com.miniioccontainer.demo.OrderService;
import com.miniioccontainer.demo.UserService;

public class Main {
    public static void main(String[] args) {
        MiniApplicationContext context =
                new MiniApplicationContext("com.miniioccontainer.demo");

        UserService userService = context.getBean(UserService.class);
        System.out.println("userService = " + userService);

        // 按类型取：多个实现时命中 @MyPrimary
        OrderService primary = context.getBean(OrderService.class);
        System.out.println("getBean(OrderService.class) = " + primary.getName());

        // 字段注入：无 Qualifier 走 Primary，有 Qualifier 走指定实现
        System.out.println("userService.orderService = "
                + userService.getOrderService().getName());
        System.out.println("userService.orderServiceV2 = "
                + userService.getOrderServiceV2().getName());

        // 也可以在 getBean 时直接带限定名
        OrderService v2 = context.getBean(OrderService.class, "orderServiceV2");
        System.out.println("getBean(OrderService.class, \"orderServiceV2\") = "
                + v2.getName());
    }
}
