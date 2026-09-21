package com.miniioccontainer.demo;

/**
 * 只通过 XML 的 value 注入配置，类上没有 @MyComponent。
 */
public class MailSettings {

    private String host;
    private int port;
    private boolean ssl;

    public String describe() {
        return host + ":" + port + " ssl=" + ssl;
    }
}
