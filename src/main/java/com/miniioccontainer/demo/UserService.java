package com.miniioccontainer.demo;

import com.miniioccontainer.annotation.MyAutowired;
import com.miniioccontainer.annotation.MyComponent;
import com.miniioccontainer.demo.OrderService;

@MyComponent
public class UserService {

    @MyAutowired(name = "orderServiceImpl")
    private OrderService orderService;

    public OrderService getOrderService() {
        return orderService;
    }
}