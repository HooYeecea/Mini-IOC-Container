package com.miniioccontainer.demo;

import com.miniioccontainer.annotation.MyComponent;

@MyComponent
public class OrderServiceV2 implements OrderService {
    @Override
    public String getName() {
        return "OrderServiceV2";
    }
}