package com.miniioccontainer;

import com.miniioccontainer.context.MiniApplicationContext;
import com.miniioccontainer.demo.OrderService;
import com.miniioccontainer.demo.SmsService;
import com.miniioccontainer.demo.UserService;

public class Main {
    public static void main(String[] args) {
        // beans.xml 里既有 component-scan（注解），也有 <bean>（XML）
        MiniApplicationContext context = new MiniApplicationContext("beans.xml");

        UserService userService = context.getBean(UserService.class);
        System.out.println("userService = " + userService);

        OrderService primary = context.getBean(OrderService.class);
        System.out.println("getBean(OrderService.class) = " + primary.getName());

        System.out.println("userService.orderService = "
                + userService.getOrderService().getName());
        System.out.println("userService.orderServiceV2 = "
                + userService.getOrderServiceV2().getName());

        OrderService v2 = context.getBean(OrderService.class, "orderServiceV2");
        System.out.println("getBean(OrderService.class, \"orderServiceV2\") = "
                + v2.getName());

        SmsService smsService = (SmsService) context.getBean("smsService");
        System.out.println("smsService.send() = " + smsService.send());

        // 同类 XML 被跳过，容器里不应出现 orderServiceFromXml
        System.out.println("orderServiceFromXml = " + context.getBean("orderServiceFromXml"));
    }
}
