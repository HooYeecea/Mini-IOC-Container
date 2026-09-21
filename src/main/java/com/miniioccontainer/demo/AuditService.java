package com.miniioccontainer.demo;

/**
 * 只通过 XML 的 constructor-arg 创建，类上没有 @MyComponent。
 */
public class AuditService {

    private final OrderService orderService;

    public AuditService(OrderService orderService) {
        this.orderService = orderService;
    }

    public String audit() {
        return "audit " + orderService.getName();
    }

    public void start() {
        System.out.println("AuditService init");
    }

    public void stop() {
        System.out.println("AuditService destroy");
    }
}
