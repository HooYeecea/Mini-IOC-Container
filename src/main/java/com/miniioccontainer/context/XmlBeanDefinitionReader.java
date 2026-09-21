package com.miniioccontainer.context;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 从 classpath 读取迷你版 beans.xml。
 *
 * 支持：
 * - &lt;component-scan base-package="..."/&gt;
 * - &lt;bean id class primary&gt;
 * - &lt;constructor-arg ref/&gt;
 * - &lt;property name ref/&gt;
 */
public class XmlBeanDefinitionReader {

    public static class Result {
        private final List<String> scanPackages = new ArrayList<>();
        private final List<XmlBeanDefinition> beans = new ArrayList<>();

        public List<String> getScanPackages() {
            return scanPackages;
        }

        public List<XmlBeanDefinition> getBeans() {
            return beans;
        }
    }

    public static Result load(String classpathLocation) {
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        InputStream inputStream = classLoader.getResourceAsStream(classpathLocation);
        if (inputStream == null) {
            throw new RuntimeException("找不到 XML 配置: " + classpathLocation);
        }

        try (InputStream in = inputStream) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setIgnoringComments(true);
            Document document = factory.newDocumentBuilder().parse(in);
            document.getDocumentElement().normalize();

            Element root = document.getDocumentElement();
            if (!"beans".equals(root.getTagName())) {
                throw new RuntimeException("XML 根节点必须是 <beans>，实际是 <" + root.getTagName() + ">");
            }

            Result result = new Result();
            NodeList children = root.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                Node node = children.item(i);
                if (node.getNodeType() != Node.ELEMENT_NODE) {
                    continue;
                }
                Element element = (Element) node;
                String tag = element.getTagName();
                if ("component-scan".equals(tag)) {
                    String basePackage = element.getAttribute("base-package").trim();
                    if (basePackage.isEmpty()) {
                        throw new RuntimeException("<component-scan> 缺少 base-package");
                    }
                    result.scanPackages.add(basePackage);
                } else if ("bean".equals(tag)) {
                    result.beans.add(parseBean(element));
                } else {
                    throw new RuntimeException("不支持的 XML 标签: <" + tag + ">");
                }
            }
            return result;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("解析 XML 失败: " + classpathLocation, e);
        }
    }

    private static XmlBeanDefinition parseBean(Element beanElement) {
        String className = beanElement.getAttribute("class").trim();
        if (className.isEmpty()) {
            throw new RuntimeException("<bean> 缺少 class");
        }
        String id = beanElement.getAttribute("id").trim();
        boolean primary = Boolean.parseBoolean(beanElement.getAttribute("primary"));
        String initMethod = beanElement.getAttribute("init-method").trim();
        String destroyMethod = beanElement.getAttribute("destroy-method").trim();
        String scope = beanElement.getAttribute("scope").trim();
        String lazyInit = beanElement.getAttribute("lazy-init").trim();
        XmlBeanDefinition definition = new XmlBeanDefinition(
                id, className, primary, initMethod, destroyMethod, scope, lazyInit);

        NodeList children = beanElement.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node node = children.item(i);
            if (node.getNodeType() != Node.ELEMENT_NODE) {
                continue;
            }
            Element child = (Element) node;
            if ("constructor-arg".equals(child.getTagName())) {
                String ref = child.getAttribute("ref").trim();
                if (ref.isEmpty()) {
                    throw new RuntimeException("<constructor-arg> 缺少 ref");
                }
                definition.getConstructorArgRefs().add(ref);
                continue;
            }
            if (!"property".equals(child.getTagName())) {
                throw new RuntimeException("<bean> 下不支持的标签: <" + child.getTagName() + ">");
            }
            String name = child.getAttribute("name").trim();
            String ref = child.getAttribute("ref").trim();
            if (name.isEmpty() || ref.isEmpty()) {
                throw new RuntimeException("<property> 必须同时有 name 和 ref");
            }
            definition.getProperties().add(new XmlBeanDefinition.Property(name, ref));
        }
        return definition;
    }
}
