package com.miniioccontainer.demo;

import com.miniioccontainer.annotation.MyAutowired;
import com.miniioccontainer.annotation.MyComponent;
import com.miniioccontainer.annotation.MyQualifier;

import java.util.List;
import java.util.Map;

@MyComponent
public class UserService {

    // 同类型有多个实现时，走 @MyPrimary：OrderServiceImpl
    @MyAutowired
    private OrderService orderService;

    // 需要另一个实现时，用 @MyQualifier 精确指定
    @MyAutowired
    @MyQualifier("orderServiceV2")
    private OrderService orderServiceV2;

    // 同类型全部注入：List 按注册顺序，Map 的 key 是 Bean 名
    @MyAutowired
    private List<OrderService> orderServices;

    @MyAutowired
    private Map<String, OrderService> orderServiceMap;

    public OrderService getOrderService() {
        return orderService;
    }

    public OrderService getOrderServiceV2() {
        return orderServiceV2;
    }

    public List<OrderService> getOrderServices() {
        return orderServices;
    }

    public Map<String, OrderService> getOrderServiceMap() {
        return orderServiceMap;
    }
}
