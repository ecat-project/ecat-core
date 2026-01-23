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

package com.ecat.core.I18n;

import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathFactory;

import com.ecat.core.Utils.ClassUrlTools;
import org.w3c.dom.Document;

import com.ecat.core.Const;

/**
 * I18n helper utility class for creating I18nProxy instances with caching
 *
 * @author coffee
 */
public class I18nHelper {

    private static final Map<String, I18nProxy> proxyCache = new ConcurrentHashMap<>();

    /**
     * Create I18nProxy for a given class
     * Automatically detects artifactId from ClassLoader
     * 
     * If artifactId cannot be determined, returns null
     */
    public static I18nProxy createProxy(Class<?> clazz) {
        ClassLoader classLoader = clazz.getClassLoader();
        String artifactId = getArtifactId(clazz, classLoader);
        if(artifactId == null){
            return null;
        }

        return createProxy(artifactId, clazz);
    }

    /**
     * Create I18nProxy for a given class
     * Automatically detects artifactId from ClassLoader
     * If artifactId cannot be determined, defaults to core artifactId
     * @param clazz Class to create proxy for
     * @return I18nProxy instance
     */
    // public static I18nProxy createProxyDefaultCore(Class<?> clazz) {
    //     ClassLoader classLoader = clazz.getClassLoader();
    //     String artifactId = getArtifactId(clazz, classLoader);
    //     if(artifactId == null){
    //         artifactId = Const.CORE_ARTIFACT_ID;
    //     }

    //     return createProxy(artifactId, clazz);
    // }



    /**
     * Create I18nProxy for core module
     */
    public static I18nProxy createCoreProxy() {
        return createProxy(Const.CORE_ARTIFACT_ID,I18nHelper.class);
    }

    /**
     * Create I18nProxy with explicit artifactId
     */
    public static I18nProxy createProxy(String artifactId, Class<?> clazz) {
        // Use cached proxy if available
        I18nProxy cachedProxy = proxyCache.get(artifactId);
        if (cachedProxy != null) {
            return cachedProxy;
        }
        ClassLoader classLoader = clazz.getClassLoader();

        // Create new proxy and cache it
        I18nProxy proxy = new I18nProxy(artifactId, clazz, classLoader);
        proxyCache.put(artifactId, proxy);

        return proxy;
    }

    /**
     * Get artifactId from ClassLoader by reading MANIFEST.MF
     */
    private static String getArtifactId(Class<?> clazz, ClassLoader classLoader) {
        try {
            // Try to get the jar file containing this class
            String classFile = clazz.getName().replace('.', '/') + ".class";
            URL classUrl = classLoader.getResource(classFile);
            classUrl = ClassUrlTools.decodeUrlPath(classUrl);
            if (classUrl != null && "jar".equals(classUrl.getProtocol())) {
                // for jar run mode
                String path = classUrl.getPath();
                int separatorIndex = path.indexOf("!/");
                if (separatorIndex > 0) {
                    String jarPath = path.substring(5, separatorIndex); // strip "file:" prefix
                    try (java.util.jar.JarFile jarFile = new java.util.jar.JarFile(jarPath)) {
                        java.util.jar.Manifest manifest = jarFile.getManifest();
                        if (manifest != null) {
                            String artifactId = manifest.getMainAttributes().getValue("Ecat-Artifact-Id");
                            if (artifactId != null) {
                                return artifactId;
                            }
                        }
                    }
                }
            }
            else if (classUrl != null && "file".equals(classUrl.getProtocol()) && classUrl.getPath().contains("/target/")) {
                // for IDE run mode, scan pom.xml files in path
                // 从例如"/app/ecat-integrations/sensecap/target/test-classes/com/ecat/integration/SenseCAPIntegration/SenseCAPI18nTest.class"中
                // 获取/app/ecat-integrations/sensecap/pom.xml中的artifactId
                String path = classUrl.getPath();
                int targetIndex = path.indexOf("/target/");
                if (targetIndex > 0) {
                    String projectPath = path.substring(0, targetIndex);
                    java.io.File pomFile = new java.io.File(projectPath, "pom.xml");
                    if (pomFile.exists()) {
                        try (java.io.InputStream is = new java.io.FileInputStream(pomFile)) {
                            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
                            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
                            Document doc = dBuilder.parse(is);
                            doc.getDocumentElement().normalize();
                            XPath xp = XPathFactory.newInstance().newXPath();
                            
                            // 首先获取project下的artifactId节点
                            String artifactId = xp.evaluate("/project/artifactId", doc).trim();
                            
                            // 精确查找maven-assembly-plugin中的Ecat-Artifact-Id节点
                            String ecatArtifactId = xp.evaluate("/project/build/plugins/plugin[artifactId='maven-assembly-plugin']/configuration/archive/manifestEntries/Ecat-Artifact-Id", doc).trim();
                            
                            // 只有当两个节点都存在时才返回artifactId
                            if (!artifactId.isEmpty() && !ecatArtifactId.isEmpty()) {
                                return artifactId;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Ignore and use default
        }

        // Fallback to core if artifactId cannot be determined
        // return Const.CORE_ARTIFACT_ID;
        return null;
    }

    /**
     * Get global translation (for Ecat Core class use only)
     * @apiNote This method is only for Ecat Core classes to avoid circular dependency.
     */
    public static String t(String key) {
        return createCoreProxy().t(key);
    }

    /**
     * Get global translation with parameters (for Ecat Core class use only)
     * @apiNote This method is only for Ecat Core classes to avoid circular dependency.
     */
    public static String t(String key, Object... params) {
        return createCoreProxy().t(key, params);
    }

    /**
     * Get translation by key with named parameters (ICU4J Map support)
     * @apiNote This method is only for Ecat Core classes to avoid circular dependency.
     */
    public String t(String key, Map<String, Object> params) {
        return createCoreProxy().t(key, params);
    }

    /**
     * Set global locale
     */
    public static void setLocale(String languageTag) {
        I18nRegistry.getInstance().setGlobalLocale(java.util.Locale.forLanguageTag(languageTag));
    }

    /**
     * Set global locale
     */
    public static void setLocale(java.util.Locale locale) {
        I18nRegistry.getInstance().setGlobalLocale(locale);
    }

    /**
     * Get current locale
     */
    public static java.util.Locale getCurrentLocale() {
        return I18nRegistry.getInstance().getCurrentLocale();
    }

    /**
     * Clear all cached proxies
     */
    public static void clearCache() {
        proxyCache.clear();
    }

    /**
     * Get cached proxy count
     */
    public static int getCachedProxyCount() {
        return proxyCache.size();
    }

    /**
     * Check if proxy is cached for artifactId
     */
    public static boolean isProxyCached(String artifactId) {
        return proxyCache.containsKey(artifactId);
    }

    /**
     * Remove cached proxy for artifactId
     */
    public static void removeCachedProxy(String artifactId) {
        I18nProxy proxy = proxyCache.remove(artifactId);
        if (proxy != null) {
            I18nRegistry.getInstance().unregisterProxy(artifactId);
        }
    }

    /**
     * Get all cached artifactIds
     */
    public static Map<String, I18nProxy> getCachedProxies() {
        return new HashMap<>(proxyCache);
    }

    /**
     * Get cached proxy for artifactId
     * @param artifactId artifactId
     * @return I18nProxy or null if not cached
     */
    public static I18nProxy getCachedProxy(String artifactId) {
        return proxyCache.getOrDefault(artifactId, null);
    }
}
