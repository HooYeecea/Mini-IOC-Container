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
}
