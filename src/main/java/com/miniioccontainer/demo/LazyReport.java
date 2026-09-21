package com.miniioccontainer.demo;

import com.miniioccontainer.annotation.MyComponent;
import com.miniioccontainer.annotation.MyLazy;
import com.miniioccontainer.annotation.MyPostConstruct;
import com.miniioccontainer.annotation.MyPreDestroy;

@MyComponent
@MyLazy
public class LazyReport {

    @MyPostConstruct
    public void init() {
        System.out.println("LazyReport init");
    }

    @MyPreDestroy
    public void shutdown() {
        System.out.println("LazyReport destroy");
    }
}
