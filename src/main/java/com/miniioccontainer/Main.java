package com.miniioccontainer;

import com.miniioccontainer.context.MiniApplicationContext;
import com.miniioccontainer.context.PackageScanner;
import com.miniioccontainer.demo.OrderService;
import com.miniioccontainer.demo.UserService;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        MiniApplicationContext context = new MiniApplicationContext("com.miniioccontainer.demo");

        UserService userService = context.getBean(UserService.class);
        System.out.println("拿到 Bean: " + userService);

        OrderService orderService = context.getBean(OrderService.class);
        System.out.println("拿到 Bean: " + orderService);
    }
}