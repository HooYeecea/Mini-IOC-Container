package com.miniioccontainer.demo;

/**
 * 只通过 XML 注册的懒加载单例。Demo 里不会 getBean，所以不会创建，也不会销毁。
 */
public class LazyNote {

    public void start() {
        System.out.println("LazyNote init");
    }

    public void stop() {
        System.out.println("LazyNote destroy");
    }
}
