package com.ecat.core.Config;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * YAML 配置文件工具类
 *
 * <p>提供统一的 YAML 配置文件读写功能，确保输出格式一致</p>
 *
 * @author coffee
 * @version 1.0.0
 */
public class YamlConfigUtils {

    /**
     * 保存集成配置文件（使用标准 YAML 格式）
     *
     * @param configPath 配置文件路径
     * @param version 版本号
     * @param integrations 集成配置（key 为 groupId:artifactId）
     * @param coreConfig Core 配置（可为 null）
     * @throws IOException 保存失败
     */
    public static void saveIntegrationConfig(Path configPath, String version,
                                              Map<String, Map<String, Object>> integrations,
                                              Map<String, Object> coreConfig) throws IOException {
        // 确保父目录存在
        Files.createDirectories(configPath.getParent());

        // 使用 TreeMap 确保输出顺序一致（按 key 排序）
        Map<String, Map<String, Object>> sortedIntegrations = new TreeMap<>(integrations);

        // 手动构建 YAML 字符串以确保标准格式
        StringBuilder yaml = new StringBuilder();

        // version 字段
        yaml.append("version: ").append(version).append("\n\n");

        // integrations 字段
        if (!sortedIntegrations.isEmpty()) {
            yaml.append("integrations:\n");
            for (Map.Entry<String, Map<String, Object>> entry : sortedIntegrations.entrySet()) {
                String key = entry.getKey();
                Map<String, Object> integrationConfig = entry.getValue();
                yaml.append("  ").append(key).append(":\n");
                yaml.append("    enabled: ").append(integrationConfig.get("enabled")).append("\n");
                yaml.append("    groupId: ").append(integrationConfig.get("groupId")).append("\n");
                yaml.append("    artifactId: ").append(integrationConfig.get("artifactId")).append("\n");
                yaml.append("    version: ").append(integrationConfig.get("version")).append("\n");
            }
            yaml.append("\n");
        }

        // core 字段（如果存在）
        if (coreConfig != null) {
            yaml.append("core:\n");
            yaml.append("  enabled: true\n");
            yaml.append("  groupId: ").append(coreConfig.get("groupId")).append("\n");
            yaml.append("  artifactId: ").append(coreConfig.get("artifactId")).append("\n");
            yaml.append("  version: ").append(coreConfig.get("version")).append("\n");
        }

        try (Writer writer = Files.newBufferedWriter(configPath)) {
            writer.write(yaml.toString());
        }
    }

    /**
     * 创建集成配置的 Map 结构
     *
     * @param groupId groupId
     * @param artifactId artifactId
     * @param version 版本号
     * @param enabled 是否启用
     * @return 集成配置 Map
     */
    public static Map<String, Object> createIntegrationConfig(String groupId, String artifactId,
                                                                  String version, boolean enabled) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("enabled", enabled);
        config.put("groupId", groupId);
        config.put("artifactId", artifactId);
        config.put("version", version);
        return config;
    }

    /**
     * 创建 core 配置的 Map 结构
     *
     * @param groupId groupId
     * @param artifactId artifactId
     * @param version 版本号
     * @return core 配置 Map
     */
    public static Map<String, Object> createCoreConfig(String groupId, String artifactId, String version) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("groupId", groupId);
        config.put("artifactId", artifactId);
        config.put("version", version);
        return config;
    }
}
