package com.ecat.core.Utils;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MavenDependencyParser {

    /**
     * 根据输入的 jar 文件路径，读取对应的 pom.xml 文件，解析其中的依赖信息，
     * 并将每个依赖项拼接成 Maven 本地仓库的 jar 文件地址，最后返回依赖的 jar 地址列表。
     *
     * @param jarPath 输入的 jar 文件路径
     * @return 依赖的 jar 地址列表
     */
    public static List<String> getDependencyJarPaths(String jarPath) {
        List<String> dependencyJarPaths = new ArrayList<>();
        // 获取 pom.xml 文件路径
        String pomPath = getPomPathFromJarPath(jarPath);
        File pomFile = new File(pomPath);
        if (!pomFile.exists()) {
            return dependencyJarPaths;
        }

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(pomFile);

            Element root = doc.getDocumentElement();
            NodeList dependencies = root.getElementsByTagName("dependencies");
            if (dependencies.getLength() > 0) {
                Element dependenciesElement = (Element) dependencies.item(0);
                NodeList dependencyNodes = dependenciesElement.getElementsByTagName("dependency");
                for (int i = 0; i < dependencyNodes.getLength(); i++) {
                    Element dependency = (Element) dependencyNodes.item(i);
                    String groupId = getElementText(dependency, "groupId");
                    String artifactId = getElementText(dependency, "artifactId");
                    String version = getElementText(dependency, "version");
                    String jarFileName = artifactId + "-" + version + ".jar";
                    String dependencyJarPath = getJarPathFromCoordinates(groupId, artifactId, version);
                    dependencyJarPaths.add(dependencyJarPath);
                }
            }
        } catch (ParserConfigurationException | SAXException | IOException e) {
            e.printStackTrace();
        }

        return dependencyJarPaths;
    }

    /**
     * 根据 jar 文件路径获取对应的 pom.xml 文件路径。
     *
     * @param jarPath jar 文件路径
     * @return pom.xml 文件路径
     */
    private static String getPomPathFromJarPath(String jarPath) {
        return jarPath.replace(".jar", ".pom");
    }

    /**
     * 从 XML 元素中获取指定标签名的文本内容。
     *
     * @param parent  父 XML 元素
     * @param tagName 标签名
     * @return 标签的文本内容，如果未找到则返回 null
     */
    private static String getElementText(Element parent, String tagName) {
        NodeList nodes = parent.getElementsByTagName(tagName);
        if (nodes.getLength() > 0) {
            return nodes.item(0).getTextContent().trim();
        }
        return null;
    }

    /**
     * 根据依赖的 groupId、artifactId 和 version 拼接出 Maven 本地仓库的 jar 文件地址。
     *
     * @param groupId    依赖的 groupId
     * @param artifactId 依赖的 artifactId
     * @param version    依赖的版本号
     * @return Maven 本地仓库的 jar 文件地址
     */
    private static String getJarPathFromCoordinates(String groupId, String artifactId, String version) {
        String groupPath = groupId.replace(".", File.separator);
        return System.getProperty("user.home") + File.separator + ".m2" + File.separator + "repository" +
                File.separator + groupPath + File.separator + artifactId + File.separator + version +
                File.separator + artifactId + "-" + version + ".jar";
    }

    public static void main(String[] args) {
        // String jarPath = "/root/.m2/repository/com/ecat/integration-mock-serial/1.0.0/integration-mock-serial-1.0.0.jar";
        String jarPath = "/root/.m2/repository/com/fazecast/jSerialComm/2.6.2/jSerialComm-2.6.2.jar";
        List<String> dependencyJarPaths = getDependencyJarPaths(jarPath);
        for (String path : dependencyJarPaths) {
            System.out.println(path);
        }
    }
}    