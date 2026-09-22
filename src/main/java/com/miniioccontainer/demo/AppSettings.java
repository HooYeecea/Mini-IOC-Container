package com.miniioccontainer.demo;

import com.miniioccontainer.annotation.MyComponent;
import com.miniioccontainer.annotation.MyValue;

@MyComponent
public class AppSettings {

    @MyValue("${app.name}")
    private String appName;

    @MyValue("${app.port}")
    private int port;

    @MyValue("${app.debug:false}")
    private boolean debug;

    public String describe() {
        return appName + " port=" + port + " debug=" + debug;
    }
}
