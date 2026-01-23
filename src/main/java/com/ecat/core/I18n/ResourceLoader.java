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

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import com.alibaba.fastjson2.JSON;
import com.ecat.core.Utils.ClassUrlTools;

/**
 * Resource loader for JSON resource files with ClassLoader isolation
 *
 * @author coffee
 */
public class ResourceLoader {

    private final ClassLoader classLoader;
    private final Class<?> clazz;

    /**
     * Control flag for loading i18n directory resources
     * Default is true to maintain backward compatibility
     */
    private static boolean loadI18nResources = true;

    /**
     * Set whether to load i18n directory resources
     * This is useful for unit tests to verify strings.json keys without translation
     *
     * @param load true to load i18n resources, false to only load base strings.json
     */
    public static void setLoadI18nResources(boolean load) {
        loadI18nResources = load;
    }

    /**
     * Get current setting for loading i18n directory resources
     *
     * @return true if i18n resources will be loaded, false otherwise
     */
    public static boolean isLoadI18nResources() {
        return loadI18nResources;
    }

    /**
     * Create ResourceLoader with specific ClassLoader
     */
    public ResourceLoader(Class<?> clazz, ClassLoader classLoader) {
        this.clazz = clazz;
        this.classLoader = classLoader != null ? classLoader : getClass().getClassLoader();
    }

    /**
     * Load resources for the given locale
     */
    public Map<String, Object> loadResources(Locale locale) {
        Map<String, Object> result = new HashMap<>();

        // Load base strings.json (always loaded)
        Map<String, Object> baseResources = loadJsonFile("strings.json");
        if (baseResources != null) {
            mergeMaps(result, baseResources);
        }

        // Load locale-specific translation only if enabled
        if (loadI18nResources) {
            String localeFile = "i18n/" + locale.toLanguageTag() + ".json";
            Map<String, Object> localeResources = loadJsonFile(localeFile);
            if (localeResources != null) {
                mergeMaps(result, localeResources);
            }
        }

        return result;
    }

    /**
     * Load JSON file from classpath
     */
    protected Map<String, Object> loadJsonFile(String filePath) {
        // InputStream inputStream = getResourceAsStream(filePath);
        // if (inputStream == null) {
        //     return null;
        // }
        String jsonContent = getResourceAsString(filePath);
        if (jsonContent == null || jsonContent.isEmpty()) {
            return null;
        }
        
        try {
            // 使用 fastjson2 直接解析为 Map，避免多余空格和回车
            return JSON.parseObject(jsonContent, Map.class);
        } catch (Exception e) {
            System.err.println("Failed to load JSON file: " + filePath + " - " + e.getMessage());
            return null;
        } finally {
            // try {
            //     inputStream.close();
            // } catch (IOException e) {
            //     // Ignore close exception
            // }
        }
    }

    /**
     * Get resource as InputStream using ClassLoader
     * @param resourcePath relative path like "strings.json" or "i18n/en-US.json", parent path is xxx/resources/
     * @return InputStream of the resource or null if not found
     */
    private InputStream getResourceAsStream(String resourcePath) {
        try {
            // Try to get the jar file containing this class
            String classFile = clazz.getName().replace('.', '/') + ".class";
            URL classUrl = classLoader.getResource(classFile);
            if (classUrl != null && "jar".equals(classUrl.getProtocol())) {
                // for jar run mode
                String path = classUrl.getPath();
                int separatorIndex = path.indexOf("!/");
                if (separatorIndex > 0) {
                    String jarPath = path.substring(5, separatorIndex); // strip "file:" prefix
                    try (java.util.jar.JarFile jarFile = new java.util.jar.JarFile(jarPath)) {
                        // 获取resourcePath 对应的json文件资源
                        // String fullResourcePath = "resources/" + resourcePath;
                        String fullResourcePath = resourcePath;
                        java.util.jar.JarEntry entry = jarFile.getJarEntry(fullResourcePath);
                        if (entry != null) {
                            return jarFile.getInputStream(entry);
                        }
                    }
                }
            }
            else if (classUrl != null && "file".equals(classUrl.getProtocol()) && classUrl.getPath().contains("/target/")) {
                // for IDE run mode, scan pom.xml files in path
                // 从例如"/app/ecat-integrations/sensecap/target/test-classes/com/ecat/integration/SenseCAPIntegration/SenseCAPI18nTest.class"中
                // 获取/app/ecat-integrations/sensecap/中的 resourcePath
                String path = classUrl.getPath();
                int targetIndex = path.indexOf("/target/");
                if (targetIndex > 0) {
                    String projectPath = path.substring(0, targetIndex);
                    // 获取resourcePath 对应的json文件资源
                    java.io.File resourceFile = new java.io.File(projectPath, "src/main/resources/" + resourcePath);
                    if (resourceFile.exists()) {
                        return new java.io.FileInputStream(resourceFile);
                    }
                }
            }
        } catch (Exception e) {
            // Ignore and use default
        }

        // 返回null表示未找到
        return null;
    }

    /**
     * 读取资源文件内容并返回字符串
     * @param resourcePath 相对路径，如"strings.json"或"i18n/en-US.json"
     *                     对应src/main/resources下的文件路径
     * @return 资源文件的字符串内容，若未找到或读取失败则返回null
     */
    private String getResourceAsString(String resourcePath) {
        InputStream inputStream = null;
        JarFile jarFile = null;
        try {
            // 尝试获取当前类所在的位置信息
            String classFile = clazz.getName().replace('.', '/') + ".class";
            URL classUrl = classLoader.getResource(classFile);
            classUrl = ClassUrlTools.decodeUrlPath(classUrl);
            String sourcePath = null; // JAR文件路径或IDE项目路径
            
            if (classUrl != null && "jar".equals(classUrl.getProtocol())) {
                // JAR运行模式处理
                String path = classUrl.getPath();
                int separatorIndex = path.indexOf("!/");
                if (separatorIndex > 0) {
                    sourcePath = path.substring(5, separatorIndex); // 去除"file:"前缀
                    jarFile = new JarFile(sourcePath);
                    
                    // 修正：移除多余的"resources/"前缀，Maven打包后资源在根目录
                    String fullResourcePath = resourcePath;
                    JarEntry entry = jarFile.getJarEntry(fullResourcePath);
                    
                    if (entry != null) {
                        inputStream = jarFile.getInputStream(entry);
                    } else {
                        System.out.println("JAR中未找到资源: " + fullResourcePath);
                        return null;
                    }
                }
            } 
            else if (classUrl != null && "file".equals(classUrl.getProtocol()) && classUrl.getPath().contains("/target/")) {
                // IDE运行模式处理
                String path = classUrl.getPath();
                int targetIndex = path.indexOf("/target/");
                if (targetIndex > 0) {
                    String projectPath = path.substring(0, targetIndex);
                    sourcePath = projectPath + "/src/main/resources/";
                    // 构建资源文件路径
                    File resourceFile = new File(sourcePath + resourcePath);
                    
                    if (resourceFile.exists() && resourceFile.isFile()) {
                        inputStream = new FileInputStream(resourceFile);
                    } else {
                        System.out.println("IDE中未找到资源文件: " + resourceFile.getAbsolutePath());
                        return null;
                    }
                }
            }

            // 读取输入流内容（Java 8兼容方式）
            if (inputStream != null) {
                try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[1024];
                    int length;
                    while ((length = inputStream.read(buffer)) != -1) {
                        baos.write(buffer, 0, length);
                    }
                    // 转换为UTF-8字符串
                    String content = baos.toString("UTF-8");
                    System.out.println("成功读取资源: " + sourcePath + resourcePath + "，长度: " + content.length() + "字节");
                    return content;
                }
            }
        } catch (Exception e) {
            // 不再忽略异常，打印错误信息便于调试
            System.err.println("读取资源时发生错误: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // 确保所有资源都被关闭
            if (inputStream != null) {
                try {
                    inputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (jarFile != null) {
                try {
                    jarFile.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        
        return null;
    }


    /**
     * Merge two maps recursively
     */
    @SuppressWarnings("unchecked")
    private void mergeMaps(Map<String, Object> target, Map<String, Object> source) {
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (target.containsKey(key) && target.get(key) instanceof Map && value instanceof Map) {
                // Recursively merge nested maps
                mergeMaps((Map<String, Object>) target.get(key), (Map<String, Object>) value);
            } else {
                // Override with new value
                target.put(key, value);
            }
        }
    }

}
