package com.miniioccontainer.demo;

import com.miniioccontainer.annotation.MyComponent;

@MyComponent
public class OrderServiceImpl implements OrderService {
    @Override
    public String getName() {
        return "OrderServiceImpl";
    }
}