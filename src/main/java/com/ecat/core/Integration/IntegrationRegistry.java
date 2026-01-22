package com.ecat.core.Integration;

import java.util.HashMap;
import java.util.Map;

/**
 * 集成注册表，用于注册和查找集成实例
 *
 * 注意：此注册表使用 Maven coordinate (groupId:artifactId) 作为唯一键
 * 而不是单独的 artifactId
 */
public class IntegrationRegistry {

    private final Map<String, IntegrationBase> registry = new HashMap<>();

    /**
     * 注册集成实例
     *
     * @param coordinate 集成的唯一标识 (groupId:artifactId)
     * @param integration 集成实例
     */
    public void register(String coordinate, IntegrationBase integration) {
        registry.put(coordinate, integration);
    }

    /**
     * 根据坐标查找集成实例
     *
     * 支持两种格式：
     * 1. 完整坐标：groupId:artifactId（推荐）
     * 2. 仅 artifactId：向后兼容，自动添加 com.ecat: 前缀
     *
     * @param coordinate 集成的标识 (groupId:artifactId 或 artifactId)
     * @return 集成实例，若不存在则返回 null
     */
    public IntegrationBase getIntegration(String coordinate) {
        // 1. 精确匹配
        IntegrationBase result = registry.get(coordinate);
        if (result != null) {
            return result;
        }

        // 2. 向后兼容：旧格式 artifactId
        // 如果传入的是纯 artifactId（不含冒号），尝试添加默认 groupId
        if (!coordinate.contains(":")) {
            return registry.get("com.ecat:" + coordinate);
        }

        return null;
    }
}