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
        // 注意：这里使用当前线程的上下文 ClassLoader，确保加载的是当前应用的类
        // Thread.currentThread().getContextClassLoader() 是当前线程的上下文 ClassLoader，确保加载的是当前应用的类
        // 如果当前线程没有设置上下文 ClassLoader，会使用父线程的上下文 ClassLoader
        // 如果父线程也没有设置上下文 ClassLoader，会使用系统 ClassLoader().getContextClassLoader();
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        // 调用 ClassLoader 的 getResource 方法，获取指定路径对应的资源 URLf
        URL url = classLoader.getResource(path);

        if (url == null) {
            throw new RuntimeException("包不存在: " + basePackage);
        }

        // 3. URL 转 File对象
        File dir = new File(url.getFile());

        // 4. 递归扫描目录
        scanDirectory(dir, basePackage, classLoader, result);

        return result;
    }

    /**
     * 递归遍历目录，收集 .class 文件对应的 Class 对象
     * @param dir 当前目录
     * @param packageName 当前包名
     * @param classLoader 用于加载类的 ClassLoader
     * @param result 存储结果的列表
     * 递归遍历目录，收集所有带 @MyComponent 的类
     */
    private static void scanDirectory(File dir,
                                      String packageName,
                                      ClassLoader classLoader,
                                      List<Class<?>> result) {
        // listFiles() 方法返回当前目录下的所有文件和子目录，包括隐藏文件
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }

        // 遍历目录下的所有文件和子目录
        for (File file : files) {
            if (file.isDirectory()) {
                // 子目录，包名加上子目录名
                scanDirectory(file, packageName + "." + file.getName(), classLoader, result);
            } else {
                String fileName = file.getName();
                // 过滤：只处理 .class 文件
                if (!fileName.endsWith(".class")) {
                    continue;
                }

                // 去掉 .class 后缀
                String className = fileName.substring(0, fileName.length() - 6);

                // 拼成全限定类名：cn.hooyeecea.demo.UserService
                String fullClassName = packageName + "." + className;

                try {
                    // 加载类，第二个参数 false 表示不执行静态初始化块
                    /*
                    * Class.forName() 方法会加载类，返回 Class 对象,接收的第一个参数是类的全限定名，
                    * 第二个参数是是否执行静态初始化块，第三个参数是 Class加载器
                    */
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