package com.miniioccontainer.demo;

import com.miniioccontainer.annotation.MyAutowired;
import com.miniioccontainer.annotation.MyComponent;

@MyComponent
public class UserService{

    @MyAutowired
    private OrderService orderService;

    public OrderService getOrderService() {
        return orderService;
    }
}
