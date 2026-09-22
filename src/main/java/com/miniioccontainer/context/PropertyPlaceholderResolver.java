package com.miniioccontainer.context;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Properties;
import java.util.Set;

/**
 * 从 classpath 加载 .properties，并把 ${key} / ${key:default} 解析成字面量。
 */
public class PropertyPlaceholderResolver {

    private final Properties properties = new Properties();

    public void load(String classpathLocation) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream(classpathLocation);
        if (inputStream == null) {
            throw new RuntimeException("找不到配置文件: " + classpathLocation);
        }
        try (InputStream in = inputStream;
             InputStreamReader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            properties.load(reader);
            System.out.println("加载配置: " + classpathLocation);
        } catch (IOException e) {
            throw new RuntimeException("读取配置失败: " + classpathLocation, e);
        }
    }

    public void loadIfPresent(String classpathLocation) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        if (classLoader.getResource(classpathLocation) == null) {
            return;
        }
        load(classpathLocation);
    }

    public String resolve(String text, String where) {
        if (text == null || !text.contains("${")) {
            return text;
        }
        return resolveInternal(text, where, new HashSet<>());
    }

    private String resolveInternal(String text, String where, Set<String> resolving) {
        StringBuilder result = new StringBuilder();
        int index = 0;
        while (index < text.length()) {
            int start = text.indexOf("${", index);
            if (start < 0) {
                result.append(text, index, text.length());
                break;
            }
            result.append(text, index, start);
            int end = text.indexOf('}', start + 2);
            if (end < 0) {
                throw new RuntimeException("占位符缺少右括号 }（" + where + "）: " + text);
            }
            String expression = text.substring(start + 2, end).trim();
            if (expression.isEmpty()) {
                throw new RuntimeException("占位符不能为空（" + where + "）");
            }
            String key;
            String defaultValue = null;
            int colon = expression.indexOf(':');
            if (colon >= 0) {
                key = expression.substring(0, colon).trim();
                defaultValue = expression.substring(colon + 1);
            } else {
                key = expression;
            }
            if (!resolving.add(key)) {
                throw new RuntimeException("检测到循环占位符: " + key + "（" + where + "）");
            }
            String value = properties.getProperty(key);
            if (value == null) {
                if (defaultValue == null) {
                    throw new RuntimeException(
                            "找不到配置项 " + key + "（" + where + "）");
                }
                value = defaultValue;
            }
            result.append(resolveInternal(value, where, resolving));
            resolving.remove(key);
            index = end + 1;
        }
        return result.toString();
    }
}
