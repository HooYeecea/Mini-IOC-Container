package com.miniioccontainer;

import com.miniioccontainer.context.MiniApplicationContext;
import com.miniioccontainer.demo.AppSettings;
import com.miniioccontainer.demo.AuditService;
import com.miniioccontainer.demo.CircularA;
import com.miniioccontainer.demo.CircularB;
import com.miniioccontainer.demo.LazyReport;
import com.miniioccontainer.demo.MailSettings;
import com.miniioccontainer.demo.OrderFacade;
import com.miniioccontainer.demo.OrderService;
import com.miniioccontainer.demo.SmsService;
import com.miniioccontainer.demo.UserService;

import java.lang.reflect.Proxy;

public class Main {
    public static void main(String[] args) {
        MiniApplicationContext context = new MiniApplicationContext("beans.xml");

        UserService userService = context.getBean(UserService.class);
        System.out.println("userService = " + userService);

        OrderService primary = context.getBean(OrderService.class);
        System.out.println("primary is JDK proxy = "
                + Proxy.isProxyClass(primary.getClass()));
        System.out.println("getBean(OrderService.class) = " + primary.getName());

        System.out.println("userService.orderService is JDK proxy = "
                + Proxy.isProxyClass(userService.getOrderService().getClass()));
        System.out.println("userService.orderService = "
                + userService.getOrderService().getName());

        System.out.println("userService.orderServiceV2 is JDK proxy = "
                + Proxy.isProxyClass(userService.getOrderServiceV2().getClass()));
        System.out.println("userService.orderServiceV2 = "
                + userService.getOrderServiceV2().getName());
        System.out.print("userService.orderServices = ");
        for (int i = 0; i < userService.getOrderServices().size(); i++) {
            if (i > 0) {
                System.out.print(", ");
            }
            System.out.print(userService.getOrderServices().get(i).getName());
        }
        System.out.println();
        System.out.println("userService.orderServiceMap = "
                + userService.getOrderServiceMap().keySet());

        OrderService v2 = context.getBean(OrderService.class, "orderServiceV2");
        System.out.println("getBean(OrderService.class, \"orderServiceV2\") = "
                + v2.getName());

        SmsService smsService = (SmsService) context.getBean("smsService");
        System.out.println("smsService.send() = " + smsService.send());

        System.out.println("orderServiceFromXml = " + context.getBean("orderServiceFromXml"));

        CircularA circularA = context.getBean(CircularA.class);
        CircularB circularB = context.getBean(CircularB.class);
        System.out.println(circularA.describe());
        System.out.println(circularB.describe());

        OrderFacade orderFacade = context.getBean(OrderFacade.class);
        System.out.println("orderFacade = " + orderFacade.describe());

        AuditService auditService = (AuditService) context.getBean("auditService");
        System.out.println("auditService.audit() = " + auditService.audit());

        UserService again = context.getBean(UserService.class);
        System.out.println("singleton same instance = " + (userService == again));

        Object token1 = context.getBean("taskToken");
        Object token2 = context.getBean("taskToken");
        System.out.println("prototype same instance = " + (token1 == token2));

        Object job1 = context.getBean("reportJob");
        Object job2 = context.getBean("reportJob");
        System.out.println("xml prototype same instance = " + (job1 == job2));

        System.out.println("before getBean LazyReport");
        LazyReport lazy1 = context.getBean(LazyReport.class);
        LazyReport lazy2 = context.getBean(LazyReport.class);
        System.out.println("lazy same instance = " + (lazy1 == lazy2));

        AppSettings appSettings = context.getBean(AppSettings.class);
        System.out.println("appSettings = " + appSettings.describe());
        MailSettings mailSettings = (MailSettings) context.getBean("mailSettings");
        System.out.println("mailSettings = " + mailSettings.describe());

        context.close();
    }
}
