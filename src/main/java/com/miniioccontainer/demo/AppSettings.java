package com.miniioccontainer.demo;

import com.miniioccontainer.annotation.MyComponent;
import com.miniioccontainer.annotation.MyValue;

@MyComponent
public class AppSettings {

    @MyValue("mini-ioc")
    private String appName;

    @MyValue("8080")
    private int port;

    @MyValue("true")
    private boolean debug;

    public String describe() {
        return appName + " port=" + port + " debug=" + debug;
    }
}
