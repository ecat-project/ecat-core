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

package com.ecat.core.Integration;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

import com.ecat.core.Integration.IntegrationSubInfo.*;
import com.ecat.core.Version.Version;
/**
 * Represents the integration information for a specific artifact.
 * This class contains details about the artifact's dependencies,
 * its enabled status, class name, group ID, version, and web platform support.
 *
 * <p>It provides constructors for initializing the integration information
 * with or without web platform support, and includes a custom {@code toString}
 * method for easy representation of the object.</p>
 *
 * @author coffee
 */
public class IntegrationInfo {
    @Getter
    @Setter
    String artifactId;
    @Getter
    @Setter
    boolean isDepended;

    /**
     * 依赖信息列表（包含 groupId、artifactId、version）
     */
    @Getter
    @Setter
    private List<DependencyInfo> dependencyInfoList;

    @Getter
    @Setter
    boolean enabled;
    @Getter
    @Setter
    String className;
    @Getter
    @Setter
    String groupId;
    @Getter
    @Setter
    String version;
    @Getter
    @Setter
    private WebPlatformSupport webPlatform; // Web平台支持配置

    // ========== 版本管理相关字段（阶段1新增）==========

    /**
     * 对 ECAT Core 主程序的版本要求
     * 从 ecat-config.yml 的 requires_core 字段读取
     * 默认值为 "^1.0.0"
     */
    @Getter
    @Setter
    private String requiresCore;

    /**
     * 解析后的版本对象（缓存）
     * 使用transient避免序列化
     */
    private transient Version parsedVersion;

    /**
     * 获取解析后的Version对象
     * 首次调用时会解析version字符串，后续调用返回缓存结果
     *
     * @return Version对象，如果version为null或无效则返回null
     */
    public Version getVersionObject() {
        if (parsedVersion == null && version != null && !version.isEmpty()) {
            try {
                parsedVersion = Version.parse(version);
            } catch (Exception e) {
                // 版本解析失败，返回null
                // 实际使用中可能需要记录日志
                return null;
            }
        }
        return parsedVersion;
    }

    /**
     * 获取唯一标识（Maven坐标：groupId:artifactId）
     * <p>用于依赖解析、兼容性检查等场景的唯一键
     *
     * @return groupId:artifactId 格式的字符串，groupId 默认为 "com.ecat"
     */
    public String getCoordinate() {
        return (groupId != null ? groupId : "com.ecat") + ":" + artifactId;
    }

    /**
     * 根据 artifactId 获取依赖信息
     *
     * @param artifactId 依赖的 artifactId
     * @return DependencyInfo 对象，如果未找到返回 null
     */
    public DependencyInfo getDependencyInfo(String artifactId) {
        if (dependencyInfoList == null) {
            return null;
        }
        for (DependencyInfo dep : dependencyInfoList) {
            if (dep.getArtifactId().equals(artifactId)) {
                return dep;
            }
        }
        return null;
    }

    /**
     * 根据 groupId:artifactId 获取依赖信息
     *
     * @param groupId groupId
     * @param artifactId artifactId
     * @return DependencyInfo 对象，如果未找到返回 null
     */
    public DependencyInfo getDependencyInfo(String groupId, String artifactId) {
        if (dependencyInfoList == null) {
            return null;
        }
        for (DependencyInfo dep : dependencyInfoList) {
            if (dep.getGroupId().equals(groupId) && dep.getArtifactId().equals(artifactId)) {
                return dep;
            }
        }
        return null;
    }

    // ========== 新构造函数 ==========

    /**
     * 完整构造函数
     *
     * @param artifactId artifactId
     * @param isDepended 是否被依赖
     * @param dependencyInfoList 依赖信息列表
     * @param enabled 是否启用
     * @param className 类名
     * @param groupId groupId
     * @param version 版本
     * @param webPlatform Web平台支持
     * @param requiresCore 对 ECAT Core 的版本要求
     */
    public IntegrationInfo(String artifactId, boolean isDepended, List<DependencyInfo> dependencyInfoList,
            boolean enabled, String className, String groupId, String version, WebPlatformSupport webPlatform, String requiresCore) {
        this.artifactId = artifactId;
        this.isDepended = isDepended;
        this.dependencyInfoList = dependencyInfoList;
        this.enabled = enabled;
        this.className = className;
        this.groupId = groupId;
        this.version = version;
        this.webPlatform = webPlatform != null ? webPlatform : new WebPlatformSupport();
        this.requiresCore = requiresCore != null ? requiresCore : "^1.0.0";  // 默认值
    }

    @Override
    public String toString() {
        return "{" +
                "artifactId='" + artifactId + '\'' +
                ", isDepended=" + isDepended +
                ", dependencies=" + dependencyInfoList +
                ", enabled=" + enabled +
                ", className='" + className + '\'' +
                ", groupId='" + groupId + '\'' +
                ", version='" + version + '\'' +
                ", webPlatform=" + webPlatform +
                '}';
    }

}
