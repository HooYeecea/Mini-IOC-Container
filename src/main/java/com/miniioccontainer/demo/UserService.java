package com.miniioccontainer.demo;

import com.miniioccontainer.annotation.MyAutowired;
import com.miniioccontainer.annotation.MyComponent;
import com.miniioccontainer.annotation.MyQualifier;

@MyComponent
public class UserService {

    // 同类型有多个实现时，走 @MyPrimary：OrderServiceImpl
    @MyAutowired
    private OrderService orderService;

    // 需要另一个实现时，用 @MyQualifier 精确指定
    @MyAutowired
    @MyQualifier("orderServiceV2")
    private OrderService orderServiceV2;

    public OrderService getOrderService() {
        return orderService;
    }

    public OrderService getOrderServiceV2() {
        return orderServiceV2;
    }
}
