package com.miniioccontainer;

import com.miniioccontainer.context.PackageScanner;

import java.util.List;

public class Main {
    public static void main(String[] args) {
        List<Class<?>> classes = PackageScanner.scan("com.miniioccontainer.demo");
        for (Class<?> clazz : classes) {
            System.out.println(clazz.getName());
        }
    }
}