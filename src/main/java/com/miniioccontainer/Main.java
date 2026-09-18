package com.miniioccontainer;

import com.miniioccontainer.context.MiniApplicationContext;
import com.miniioccontainer.demo.UserService;

public class Main {
    public static void main(String[] args) {
        MiniApplicationContext context =
                new MiniApplicationContext("com.miniioccontainer.demo");

        UserService userService = context.getBean(UserService.class);
        System.out.println("userService = " + userService);
        System.out.println("userService.orderService = " + userService.getOrderService());
        System.out.println("orderService.getName() = " + userService.getOrderService().getName());
    }
}