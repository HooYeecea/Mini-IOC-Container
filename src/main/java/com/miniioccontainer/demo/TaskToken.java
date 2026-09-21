package com.miniioccontainer.demo;

import com.miniioccontainer.annotation.MyComponent;
import com.miniioccontainer.annotation.MyPostConstruct;
import com.miniioccontainer.annotation.MyPreDestroy;
import com.miniioccontainer.annotation.MyScope;

@MyComponent
@MyScope("prototype")
public class TaskToken {

    @MyPostConstruct
    public void init() {
        System.out.println("TaskToken init");
    }

    @MyPreDestroy
    public void shutdown() {
        System.out.println("TaskToken destroy");
    }
}
