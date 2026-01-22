package com.ecat.core.Utils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

import org.yaml.snakeyaml.Yaml;

import com.ecat.core.Integration.IntegrationBase;
import com.ecat.core.Integration.IntegrationInfo;
import com.ecat.core.Integration.IntegrationSubInfo.WebPlatformSupport;

/**
 * JarDependencyLoader is a utility class for loading and parsing IntegrationInfo and dependencies
 * 
 * @author coffee
 *  
*/ 
public class JarDependencyLoader {

    static private Log log = LogFactory.getLogger(JarDependencyLoader.class);

    // 读取ecat-config.yml并返回部分填充的IntegrationInfo（仅包含配置文件能提供的字段）
    @SuppressWarnings("unchecked")
    public static IntegrationInfo readPartialIntegrationInfoFromJar(File jarFile) {
        IntegrationInfo partialInfo = new IntegrationInfo(
            null,  // artifactId（由主配置填充）
            false, // isDepended（由主配置填充）
            null,  // dependencyInfoList（由配置文件填充）
            false, // enabled（由主配置填充）
            null,  // className（由主配置填充）
            null,  // groupId（由主配置填充）
            null,  // version（由主配置填充）
            new WebPlatformSupport(), // webPlatform（由配置文件填充默认值）
            null  // requiresCore（由配置文件填充，默认 "^1.0.0"）
        );

        try {
            URL url = jarFile.toURI().toURL();
            url = new URL("jar:file:" + jarFile.getAbsolutePath() + "!/");
            URL[] urls = new URL[] { url };

            try (URLClassLoader classLoader = new URLClassLoader(urls)) {
                InputStream inputStream = classLoader.getResourceAsStream("ecat-config.yml");
                if (inputStream == null) {
                    return partialInfo; // 无配置文件，返回仅含默认值的对象（requiresCore 默认 "^1.0.0"）
                }

                Yaml yaml = new Yaml();
                Map<String, Object> yamlMap = yaml.load(inputStream);

                // 填充 requires_core（配置文件存在时覆盖默认值）
                String requiresCore = (String) yamlMap.get("requires_core");
                if (requiresCore != null) {
                    partialInfo.setRequiresCore(requiresCore);
                }
                // 注意：如果配置文件中没有 requires_core，构造函数中已设置默认值 "^1.0.0"

                // 填充依赖信息（配置文件存在时覆盖默认值）
                // 阶段1新增：支持读取版本约束
                // 阶段2新增：支持 groupId（支持第三方集成）
                List<Map<String, Object>> dependencyList = (List<Map<String, Object>>) yamlMap.get("dependencies");
                if (dependencyList != null) {
                    List<com.ecat.core.Integration.DependencyInfo> dependencyInfoList = new ArrayList<>();

                    for (Map<String, Object> dependency : dependencyList) {
                        if (dependency.containsKey("artifactId")) {
                            String artifactId = (String) dependency.get("artifactId");

                            // 读取 groupId（可选，默认为 com.ecat）
                            String groupId = (String) dependency.get("groupId");

                            // 读取版本约束（可选字段，默认为 * 表示任意版本）
                            String version = "*";  // 默认为 *（任意版本）
                            if (dependency.containsKey("version")) {
                                version = (String) dependency.get("version");
                            }

                            // 创建 DependencyInfo 对象
                            com.ecat.core.Integration.DependencyInfo depInfo =
                                new com.ecat.core.Integration.DependencyInfo(groupId, artifactId, version);
                            dependencyInfoList.add(depInfo);
                        }
                    }

                    // 设置新格式
                    partialInfo.setDependencyInfoList(dependencyInfoList);
                }

                // 填充Web平台支持信息（配置文件存在时覆盖默认值）
                Map<String, Object> webPlatformMap = (Map<String, Object>) yamlMap.get("web-platform");
                if (webPlatformMap != null) {
                    boolean ui = (Boolean) webPlatformMap.getOrDefault("ui", false);
                    boolean api = (Boolean) webPlatformMap.getOrDefault("api", false);
                    partialInfo.setWebPlatform(new WebPlatformSupport(ui, api));
                }

                // 注意：className 不从 ecat-config.yml 读取，由 IntegrationManager 通过扫描 JAR 获取

                return partialInfo;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return partialInfo; // 异常时返回初始默认对象
        }
    }

    // TODO: 目前没有处理被依赖组件未加载或缺失情况下，依赖其的组件的异常处理（不再加载还是停止运行报错？）
    public static List<IntegrationInfo> getLoadOrder(List<IntegrationInfo> integrationInfoList, Map<String, List<String>> dependencyMap) {
        // Log log = LogFactory.getLogger(JarDependencyLoader.class);

        // 构建图的邻接表和入度表（使用 coordinate 作为唯一键）
        Map<String, List<String>> graph = new HashMap<>();
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, IntegrationInfo> coordinateToInfo = new HashMap<>();

        for (IntegrationInfo info : integrationInfoList) {
            String coordinate = info.getCoordinate();
            graph.put(coordinate, new ArrayList<>());
            inDegree.put(coordinate, 0);
            coordinateToInfo.put(coordinate, info);
        }

        // DEBUG: 记录所有 key
        if (log.isDebugEnabled()) {
            log.debug("getLoadOrder - coordinateToInfo keys: " + coordinateToInfo.keySet());
            log.debug("getLoadOrder - dependencyMap keys: " + dependencyMap.keySet());
        }

        for (Map.Entry<String, List<String>> entry : dependencyMap.entrySet()) {
            String from = entry.getKey();
            for (String to : entry.getValue()) {
                graph.get(from).add(to);
                inDegree.put(to, inDegree.getOrDefault(to, 0) + 1);
            }
        }

        // DEBUG: 记录入度
        if (log.isDebugEnabled()) {
            log.debug("getLoadOrder - inDegree after building graph: " + inDegree);
        }

        // 拓扑排序
        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.offer(entry.getKey());
            }
        }

        // DEBUG: 记录初始队列
        if (log.isDebugEnabled()) {
            log.debug("getLoadOrder - initial queue: " + queue);
        }

        List<IntegrationInfo> loadOrder = new ArrayList<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            if (log.isDebugEnabled()) {
                log.debug("getLoadOrder - processing current: " + current + ", coordinateToInfo has key: " + coordinateToInfo.containsKey(current));
            }
            IntegrationInfo info = coordinateToInfo.get(current);
            boolean isDepended = false;
            for (List<String> deps : dependencyMap.values()) {
                if (deps.contains(current)) {
                    isDepended = true;
                    break;
                }
            }
            if (info == null) {
                log.error("getLoadOrder - 未找到 coordinate 为 " + current + " 的 IntegrationInfo");
                log.error("getLoadOrder - coordinateToInfo keys: " + coordinateToInfo.keySet());
                throw new IllegalStateException("未找到 coordinate 为 " + current + " 的 IntegrationInfo");
            }
            info.setDepended(isDepended);
            loadOrder.add(info);

            List<String> neighbors = graph.get(current);
            for (String neighbor : neighbors) {
                inDegree.put(neighbor, inDegree.get(neighbor) - 1);
                if (inDegree.get(neighbor) == 0) {
                    queue.offer(neighbor);
                }
            }
        }

        // 检测是否存在环路
        if (loadOrder.size() != integrationInfoList.size()) {
            List<String> loopNodes = new ArrayList<>();
            for (Map.Entry<String, Integer> entry : inDegree.entrySet()) {
                if (entry.getValue() > 0) {
                    loopNodes.add(entry.getKey());
                }
            }
            throw new IllegalStateException("存在环路，无法确定依赖加载顺序，可能存在环路的 coordinates 为: " + loopNodes);
        }

        // 反转列表以实现从底层依赖开始输出
        Collections.reverse(loadOrder);

        return loadOrder;
    }

    // ==================== JAR 扫描功能 ====================

    /**
     * 扫描 JAR 文件，查找继承自 IntegrationBase 的入口类
     *
     * <p>此方法会：</p>
     * <ul>
     *   <li>扫描 JAR 中的所有 .class 文件</li>
     *   <li>找到继承自 IntegrationBase 的非抽象类</li>
     *   <li>验证有且仅有一个这样的类</li>
     *   <li>如果验证失败，抛出 JarScanException 并提供详细错误信息</li>
     * </ul>
     *
     * @param jarFile JAR 文件
     * @return 入口类的完整类名（如：com.ecat.integration.Modbus4jIntegration.Modbus4jIntegration）
     * @throws JarScanException 扫描失败时抛出
     */
    // @SuppressWarnings("unchecked")
    public static String scanIntegrationEntryClass(File jarFile) throws JarScanException {
        // Log log = LogFactory.getLogger(JarDependencyLoader.class);
        Set<String> entryClasses = new HashSet<>();
        JarFile jarFileObj = null;

        try {
            // URL jarUrl = jarFile.toURI().toURL();
            URL url = new URL("jar:file:" + jarFile.getAbsolutePath() + "!/");
            URL[] urls = new URL[] { url };

            try (URLClassLoader classLoader = new URLClassLoader(urls)) {
                // 获取 JAR 中所有条目
                jarFileObj = new JarFile(jarFile);
                Enumeration<JarEntry> entries = jarFileObj.entries();

                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String entryName = entry.getName();

                    // 只处理 .class 文件，跳过内部类和非类文件
                    if (!entryName.endsWith(".class") || entryName.contains("$")) {
                        continue;
                    }

                    // 将路径转换为类名：com/ecat/integration/xxx.class -> com.ecat.integration.xxx
                    String className = entryName
                        .replace('/', '.')
                        .replace(".class", "");

                    try {
                        // 加载类
                        Class<?> clazz = classLoader.loadClass(className);

                        // 检查是否继承自 IntegrationBase
                        if (IntegrationBase.class.isAssignableFrom(clazz) &&
                            !clazz.isInterface() &&
                            !Modifier.isAbstract(clazz.getModifiers())) {

                            log.debug("找到集成入口类: " + className);
                            entryClasses.add(className);
                        }
                    } catch (ClassNotFoundException | NoClassDefFoundError e) {
                        // 跳过无法加载的类（可能是依赖类）
                        log.trace("跳过类（缺少依赖）: " + className);
                    }
                }

                // 验证扫描结果
                if (entryClasses.isEmpty()) {
                    String errorMsg = buildNoEntryClassMessage(jarFile);
                    log.error(errorMsg);
                    throw new JarScanException(
                        jarFile.getAbsolutePath(),
                        JarScanException.ScanFailureType.NO_ENTRY_CLASS,
                        errorMsg
                    );
                }

                if (entryClasses.size() > 1) {
                    String errorMsg = buildMultipleEntryClassesMessage(jarFile, entryClasses);
                    log.error(errorMsg);
                    throw new JarScanException(
                        jarFile.getAbsolutePath(),
                        JarScanException.ScanFailureType.MULTIPLE_ENTRY_CLASSES,
                        errorMsg
                    );
                }

                // 返回唯一的入口类
                String entryClass = entryClasses.iterator().next();
                log.info("扫描 JAR [" + jarFile.getName() + "] 找到入口类: " + entryClass);
                return entryClass;
            }

        } catch (IOException e) {
            String errorMsg = "扫描 JAR 文件时发生 I/O 错误: " + e.getMessage();
            log.error(errorMsg, e);
            throw new JarScanException(
                jarFile.getAbsolutePath(),
                JarScanException.ScanFailureType.SCAN_ERROR,
                errorMsg,
                e
            );
        } finally {
            closeJarFile(jarFileObj);
        }
    }

    /**
     * 构建未找到入口类的错误消息
     */
    private static String buildNoEntryClassMessage(File jarFile) {
        return String.format(
            "\n" +
            "================================================================================\n" +
            "错误：JAR 文件中未找到集成入口类\n" +
            "================================================================================\n" +
            "JAR 文件: %s\n" +
            "\n" +
            "要求：每个集成 JAR 必须包含且仅包含一个继承自 IntegrationBase 的类\n" +
            "\n" +
            "可能的原因：\n" +
            "  1. JAR 中没有继承自 IntegrationBase 的类\n" +
            "  2. 入口类被声明为 abstract（抽象类）\n" +
            "  3. 入口类是接口而不是具体类\n" +
            "\n" +
            "解决方案：\n" +
            "  - 检查集成是否正确继承 IntegrationBase 或 IntegrationDeviceBase\n" +
            "  - 确保入口类是具体类（非 abstract）\n" +
            "  - 参考：com.ecat.core.Integration.IntegrationBase\n" +
            "================================================================================\n",
            jarFile.getAbsolutePath()
        );
    }

    /**
     * 构建找到多个入口类的错误消息
     */
    private static String buildMultipleEntryClassesMessage(File jarFile, Set<String> entryClasses) {
        StringBuilder sb = new StringBuilder();
        sb.append("\n");
        sb.append("================================================================================\n");
        sb.append("错误：JAR 文件中找到多个集成入口类\n");
        sb.append("================================================================================\n");
        sb.append("JAR 文件: ").append(jarFile.getAbsolutePath()).append("\n");
        sb.append("\n");
        sb.append("找到 ").append(entryClasses.size()).append(" 个继承自 IntegrationBase 的类：\n");
        sb.append("\n");

        int index = 1;
        for (String className : entryClasses) {
            sb.append("  ").append(index++).append(". ").append(className).append("\n");
        }

        sb.append("\n");
        sb.append("要求：每个集成 JAR 必须包含且仅包含一个入口类\n");
        sb.append("\n");
        sb.append("解决方案：\n");
        sb.append("  - 将多个入口类合并为一个\n");
        sb.append("  - 或将辅助类移到单独的辅助模块/依赖中\n");
        sb.append("================================================================================\n");

        return sb.toString();
    }

    /**
     * 安全关闭 JarFile
     */
    private static void closeJarFile(JarFile jarFile) {
        if (jarFile != null) {
            try {
                jarFile.close();
            } catch (IOException e) {
                // 忽略关闭错误
            }
        }
    }

}
