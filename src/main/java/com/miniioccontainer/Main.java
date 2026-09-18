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
        System.out.println("userService = " + userService);
        System.out.println("userService.orderService = " + userService.getOrderService());
    }
}