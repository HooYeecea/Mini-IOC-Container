package com.miniioccontainer.demo;

import com.miniioccontainer.annotation.MyAutowired;
import com.miniioccontainer.annotation.MyComponent;
import com.miniioccontainer.annotation.MyQualifier;

/**
 * 构造器注入：第一个参数按类型命中 @MyPrimary，第二个用 @MyQualifier 指定。
 */
@MyComponent
public class OrderFacade {

    private final OrderService orderService;
    private final OrderService orderServiceV2;

    @MyAutowired
    public OrderFacade(OrderService orderService,
                       @MyQualifier("orderServiceV2") OrderService orderServiceV2) {
        this.orderService = orderService;
        this.orderServiceV2 = orderServiceV2;
    }

    public String describe() {
        return orderService.getName() + " / " + orderServiceV2.getName();
    }
}
