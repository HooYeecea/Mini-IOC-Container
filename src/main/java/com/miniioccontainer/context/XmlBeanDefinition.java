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
    private final List<String> constructorArgRefs = new ArrayList<>();
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

    public List<String> getConstructorArgRefs() {
        return constructorArgRefs;
    }

    public List<Property> getProperties() {
        return properties;
    }

    public static class Property {
        private final String name;
        private final String ref;

        public Property(String name, String ref) {
            this.name = name;
            this.ref = ref;
        }

        public String getName() {
            return name;
        }

        public String getRef() {
            return ref;
        }
    }
}
