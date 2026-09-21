package com.miniioccontainer.demo;

import com.miniioccontainer.annotation.MyComponent;
import com.miniioccontainer.annotation.MyPostConstruct;
import com.miniioccontainer.annotation.MyPreDestroy;

@MyComponent
public class LifecycleDemo {

    @MyPostConstruct
    public void init() {
        System.out.println("LifecycleDemo init");
    }

    @MyPreDestroy
    public void shutdown() {
        System.out.println("LifecycleDemo destroy");
    }
}
