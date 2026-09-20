package com.miniioccontainer.demo;

import com.miniioccontainer.annotation.MyComponent;
import com.miniioccontainer.annotation.MyLog;
import com.miniioccontainer.annotation.MyPrimary;

@MyComponent
@MyPrimary
public class OrderServiceImpl implements OrderService {
    @Override
    @MyLog
    public String getName() {
        return "OrderServiceImpl";
    }
}