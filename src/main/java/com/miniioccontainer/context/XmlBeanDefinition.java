package com.miniioccontainer.context;

import java.util.ArrayList;
import java.util.List;

/**
 * 从 XML 解析出来的一条 Bean 定义。
 */
public class XmlBeanDefinition {

    private final String id;
    private final String className;
    private final boolean primary;
    private final String initMethod;
    private final String destroyMethod;
    private final String scope;
    private final String lazyInit;
    private final List<ConstructorArg> constructorArgs = new ArrayList<>();
    private final List<Property> properties = new ArrayList<>();

    public XmlBeanDefinition(String id, String className, boolean primary,
                             String initMethod, String destroyMethod, String scope, String lazyInit) {
        this.id = id;
        this.className = className;
        this.primary = primary;
        this.initMethod = initMethod;
        this.destroyMethod = destroyMethod;
        this.scope = scope;
        this.lazyInit = lazyInit;
    }

    public String getId() {
        return id;
    }

    public String getClassName() {
        return className;
    }

    public boolean isPrimary() {
        return primary;
    }

    public String getInitMethod() {
        return initMethod;
    }

    public String getDestroyMethod() {
        return destroyMethod;
    }

    public String getScope() {
        return scope;
    }

    public String getLazyInit() {
        return lazyInit;
    }

    public List<ConstructorArg> getConstructorArgs() {
        return constructorArgs;
    }

    public List<Property> getProperties() {
        return properties;
    }

    public static class ConstructorArg {
        private final String ref;
        private final String value;
        private final boolean valuePresent;

        public ConstructorArg(String ref, String value, boolean valuePresent) {
            this.ref = ref;
            this.value = value;
            this.valuePresent = valuePresent;
        }

        public String getRef() {
            return ref;
        }

        public String getValue() {
            return value;
        }

        public boolean isValue() {
            return valuePresent;
        }
    }

    public static class Property {
        private final String name;
        private final String ref;
        private final String value;
        private final boolean valuePresent;

        public Property(String name, String ref, String value, boolean valuePresent) {
            this.name = name;
            this.ref = ref;
            this.value = value;
            this.valuePresent = valuePresent;
        }

        public String getName() {
            return name;
        }

        public String getRef() {
            return ref;
        }

        public String getValue() {
            return value;
        }

        public boolean isValue() {
            return valuePresent;
        }
    }
}
