package com.miniioccontainer.demo;

import com.miniioccontainer.annotation.MyAutowired;
import com.miniioccontainer.annotation.MyComponent;

@MyComponent
public class CircularA {

    @MyAutowired
    private CircularB circularB;

    public String label() {
        return "CircularA";
    }

    public String describe() {
        return "CircularA -> " + circularB.label();
    }
}
