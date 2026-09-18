package com.miniioccontainer.demo;

import com.miniioccontainer.annotation.MyComponent;
import com.miniioccontainer.annotation.MyPrimary;

@MyComponent
@MyPrimary
public class OrderServiceImpl implements OrderService {
    @Override
    public String getName() {
        return "OrderServiceImpl";
    }
}