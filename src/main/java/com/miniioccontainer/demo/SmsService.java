package com.miniioccontainer.demo;

/**
 * 只通过 XML 注册的 Bean，类上没有 @MyComponent。
 */
public class SmsService {

    private OrderService orderService;

    public OrderService getOrderService() {
        return orderService;
    }

    public String send() {
        return "SMS via " + orderService.getName();
    }
}
