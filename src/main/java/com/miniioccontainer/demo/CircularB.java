package com.miniioccontainer.demo;

import com.miniioccontainer.annotation.MyAutowired;
import com.miniioccontainer.annotation.MyComponent;

@MyComponent
public class CircularB {

    @MyAutowired
    private CircularA circularA;

    public String label() {
        return "CircularB";
    }

    public String describe() {
        return "CircularB -> " + circularA.label();
    }
}
