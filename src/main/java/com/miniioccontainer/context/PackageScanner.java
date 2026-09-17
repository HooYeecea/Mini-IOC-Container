package com.miniioccontainer.context;

import com.miniioccontainer.annotation.MyComponent;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

public class PackageScanner {

    /**
     * 扫描指定包下所有带 @MyComponent 的类
     */
    public static List<Class<?>> scan(String basePackage) {
        List<Class<?>> result = new ArrayList<>();

        // 1. 包名转路径：cn.hooyeecea.demo -> cn/hooyeecea/demo
        String path = basePackage.replace(".", "/");

        // 2. 通过 ClassLoader 找到这个路径对应的资源
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        URL url = classLoader.getResource(path);

        if (url == null) {
            throw new RuntimeException("包不存在: " + basePackage);
        }

        // 3. URL 转 File
        File dir = new File(url.getFile());

        // 4. 递归扫描目录
        scanDirectory(dir, basePackage, classLoader, result);

        return result;
    }

    /**
     * 递归遍历目录，收集 .class 文件对应的 Class 对象
     */
    private static void scanDirectory(File dir, String packageName,
                                      ClassLoader classLoader, List<Class<?>> result) {
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                // 子目录，包名加上子目录名
                scanDirectory(file, packageName + "." + file.getName(), classLoader, result);
            } else {
                String fileName = file.getName();
                if (!fileName.endsWith(".class")) {
                    continue;
                }

                // 去掉 .class 后缀
                String className = fileName.substring(0, fileName.length() - 6);

                // 拼成全限定类名：cn.hooyeecea.demo.UserService
                String fullClassName = packageName + "." + className;

                try {
                    // 加载类，第二个参数 false 表示不执行静态初始化块
                    Class<?> clazz = Class.forName(fullClassName, false, classLoader);

                    // 过滤：只保留带 @MyComponent 的类
                    if (clazz.isAnnotationPresent(MyComponent.class)) {
                        result.add(clazz);
                    }
                } catch (ClassNotFoundException e) {
                    // 某些 .class 可能无法加载（比如内部类、模块信息），跳过
                    System.err.println("无法加载类: " + fullClassName);
                }
            }
        }
    }
}