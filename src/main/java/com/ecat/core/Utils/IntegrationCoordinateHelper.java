/*
 * Copyright (c) 2026 ECAT Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ecat.core.Utils;

import java.io.File;
import java.io.FileInputStream;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;
import org.w3c.dom.Document;

/**
 * 集成坐标获取工具
 *
 * <p>从 JAR MANIFEST 或 pom.xml 获取集成坐标（groupId:artifactId）。
 *
 * <p>支持：
 * <ul>
 *   <li>从 JAR 包的 MANIFEST.MF 读取 Ecat-Group-Id 和 Ecat-Artifact-Id</li>
 *   <li>从开发环境的 pom.xml 读取 groupId 和 artifactId</li>
 * </ul>
 * 
 * @author coffee
 */
public final class IntegrationCoordinateHelper {
    private static final String DEFAULT_GROUP_ID = "com.ecat";
    private static final String MANIFEST_ARTIFACT_ID = "Ecat-Artifact-Id";
    private static final String MANIFEST_GROUP_ID = "Ecat-Group-Id";
    private static final Map<String, String> coordinateCache = new ConcurrentHashMap<>();

    private IntegrationCoordinateHelper() {
    }

    /**
     * 获取类的坐标
     *
     * @param clazz 类
     * @return 坐标（groupId:artifactId）
     */
    public static String getCoordinate(Class<?> clazz) {
        if (clazz == null) {
            return null;
        }
        return getCoordinate(clazz, clazz.getClassLoader());
    }

    /**
     * 获取类的坐标
     *
     * @param clazz 类
     * @param classLoader 类加载器
     * @return 坐标（groupId:artifactId）
     */
    public static String getCoordinate(Class<?> clazz, ClassLoader classLoader) {
        if (clazz == null) {
            return null;
        }
        String className = clazz.getName();
        return coordinateCache.computeIfAbsent(className, k -> detectCoordinate(clazz, classLoader));
    }

    /**
     * 获取类的 artifactId
     *
     * @param clazz 类
     * @return artifactId
     */
    public static String getArtifactId(Class<?> clazz) {
        if (clazz == null) {
            return null;
        }
        return getArtifactId(clazz, clazz.getClassLoader());
    }

    /**
     * 获取类的 artifactId
     *
     * @param clazz 类
     * @param classLoader 类加载器
     * @return artifactId
     */
    public static String getArtifactId(Class<?> clazz, ClassLoader classLoader) {
        if (clazz == null) {
            return null;
        }
        String coordinate = getCoordinate(clazz, classLoader);
        if (coordinate != null && coordinate.contains(":")) {
            return coordinate.substring(coordinate.indexOf(":") + 1);
        }
        return null;
    }

    /**
     * 获取类的 groupId
     *
     * @param clazz 类
     * @return groupId
     */
    public static String getGroupId(Class<?> clazz) {
        if (clazz == null) {
            return DEFAULT_GROUP_ID;
        }
        return getGroupId(clazz, clazz.getClassLoader());
    }

    /**
     * 获取类的 groupId
     *
     * @param clazz 类
     * @param classLoader 类加载器
     * @return groupId
     */
    public static String getGroupId(Class<?> clazz, ClassLoader classLoader) {
        if (clazz == null) {
            return DEFAULT_GROUP_ID;
        }
        String coordinate = getCoordinate(clazz, classLoader);
        if (coordinate != null && coordinate.contains(":")) {
            return coordinate.substring(0, coordinate.indexOf(":"));
        }
        return DEFAULT_GROUP_ID;
    }

    /**
     * 清除缓存
     */
    public static void clearCache() {
        coordinateCache.clear();
    }

    /**
     * 检测坐标
     *
     * @param clazz 类
     * @param classLoader 类加载器
     * @return 坐标
     */
    private static String detectCoordinate(Class<?> clazz, ClassLoader classLoader) {
        try {
            String classFile = clazz.getName().replace('.', '/') + ".class";
            URL classUrl = classLoader.getResource(classFile);
            classUrl = ClassUrlTools.decodeUrlPath(classUrl);
            if (classUrl != null && "jar".equals(classUrl.getProtocol())) {
                return getCoordinateFromJar(classUrl);
            }
            if (classUrl != null && "file".equals(classUrl.getProtocol()) && classUrl.getPath().contains("/target/")) {
                return getCoordinateFromPom(classUrl.getPath());
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    /**
     * 从 JAR 包获取坐标
     *
     * @param classUrl 类 URL
     * @return 坐标
     */
    private static String getCoordinateFromJar(URL classUrl) {
        try {
            String path = classUrl.getPath();
            int separatorIndex = path.indexOf("!/");
            if (separatorIndex <= 0) {
                return null;
            }
            String jarPath = path.substring(5, separatorIndex);
            try (JarFile jarFile = new JarFile(jarPath)) {
                Manifest manifest = jarFile.getManifest();
                if (manifest == null) {
                    return null;
                }
                String artifactId = manifest.getMainAttributes().getValue(MANIFEST_ARTIFACT_ID);
                if (artifactId == null) {
                    return null;
                }
                String groupId = manifest.getMainAttributes().getValue(MANIFEST_GROUP_ID);
                if (groupId == null) {
                    groupId = DEFAULT_GROUP_ID;
                }
                return groupId + ":" + artifactId;
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }

    /**
     * 从 pom.xml 获取坐标
     *
     * @param classPath 类路径
     * @return 坐标
     */
    private static String getCoordinateFromPom(String classPath) {
        try {
            int targetIndex = classPath.indexOf("/target/");
            if (targetIndex <= 0) {
                return null;
            }
            String projectPath = classPath.substring(0, targetIndex);
            File pomFile = new File(projectPath, "pom.xml");
            if (!pomFile.exists()) {
                return null;
            }
            try (FileInputStream is = new FileInputStream(pomFile)) {
                DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
                DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
                Document doc = dBuilder.parse(is);
                doc.getDocumentElement().normalize();
                XPath xp = XPathFactory.newInstance().newXPath();

                String artifactId = xp.evaluate("/project/artifactId", doc).trim();
                String ecatArtifactId = xp.evaluate(
                        "/project/build/plugins/plugin[artifactId='maven-assembly-plugin']/configuration/archive/manifestEntries/Ecat-Artifact-Id",
                        doc).trim();
                if (artifactId.isEmpty() || ecatArtifactId.isEmpty()) {
                    return null;
                }

                String groupId = xp.evaluate("/project/groupId", doc).trim();
                if (groupId.isEmpty()) {
                    groupId = xp.evaluate("/project/parent/groupId", doc).trim();
                }
                if (groupId.isEmpty()) {
                    groupId = DEFAULT_GROUP_ID;
                }
                return groupId + ":" + artifactId;
            }
        } catch (Exception e) {
            // ignore
        }
        return null;
    }
}
