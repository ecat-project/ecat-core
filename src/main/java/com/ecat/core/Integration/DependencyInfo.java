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

import java.util.Objects;

/**
 * 集成依赖信息
 *
 * <p>表示一个集成对另一个集成的依赖关系，包含：
 * <ul>
 *   <li>groupId: 依赖的 groupId（默认 "com.ecat"）</li>
 *   <li>artifactId: 依赖的 artifactId（必需）</li>
 *   <li>version: 版本约束（可选，* 表示任意版本）</li>
 * </ul>
 *
 * <p>从 ecat-config.yml 的 dependencies 配置解析而来：</p>
 * <pre>
 * dependencies:
 *   - artifactId: integration-modbus        # 官方集成
 *     version: "^1.0.0"
 *   - artifactId: modbus4j                   # 第三方集成
 *     groupId: com.github.tusky2015
 *     version: "^3.0.0"
 *   - artifactId: integration-serial         # 旧格式兼容
 * </pre>
 *
 * @author coffee
 * @version 1.0.0
 */
public class DependencyInfo {

    /**
     * 默认 groupId（官方集成）
     */
    public static final String DEFAULT_GROUP_ID = "com.ecat";

    private String groupId;
    private String artifactId;
    private String version;

    /**
     * 构造函数
     *
     * @param artifactId artifactId（必需）
     */
    public DependencyInfo(String artifactId) {
        this(DEFAULT_GROUP_ID, artifactId, "*");
    }

    /**
     * 构造函数
     *
     * @param artifactId artifactId（必需）
     * @param version 版本约束（可选，* 表示任意版本）
     */
    public DependencyInfo(String artifactId, String version) {
        this(DEFAULT_GROUP_ID, artifactId, version);
    }

    /**
     * 完整构造函数
     *
     * @param groupId groupId（可选，null 使用默认值 "com.ecat"）
     * @param artifactId artifactId（必需）
     * @param version 版本约束（可选，* 表示任意版本）
     */
    public DependencyInfo(String groupId, String artifactId, String version) {
        this.groupId = groupId != null ? groupId : DEFAULT_GROUP_ID;
        this.artifactId = artifactId;
        this.version = version;
    }

    // ========== Getters and Setters ==========

    /**
     * 获取 groupId
     *
     * @return groupId，永远不会为 null
     */
    public String getGroupId() {
        return groupId;
    }

    /**
     * 设置 groupId
     *
     * @param groupId groupId，null 会使用默认值 "com.ecat"
     */
    public void setGroupId(String groupId) {
        this.groupId = groupId != null ? groupId : DEFAULT_GROUP_ID;
    }

    /**
     * 获取 artifactId
     *
     * @return artifactId
     */
    public String getArtifactId() {
        return artifactId;
    }

    /**
     * 设置 artifactId
     *
     * @param artifactId artifactId
     */
    public void setArtifactId(String artifactId) {
        this.artifactId = artifactId;
    }

    /**
     * 获取版本约束
     *
     * @return 版本约束字符串（如 "^1.0.0"），* 表示任意版本
     */
    public String getVersion() {
        return version;
    }

    /**
     * 设置版本约束
     *
     * @param version 版本约束字符串（如 "^1.0.0"），* 表示任意版本
     */
    public void setVersion(String version) {
        this.version = version;
    }

    // ========== 工具方法 ==========

    /**
     * 获取完整的依赖标识（groupId:artifactId）
     *
     * @return groupId:artifactId 格式的字符串
     */
    public String getCoordinate() {
        return groupId + ":" + artifactId;
    }

    /**
     * 检查是否有版本约束
     *
     * @return 如果有版本约束返回 true（* 表示任意版本，不算作约束）
     */
    public boolean hasVersionConstraint() {
        // * 表示任意版本，不算作真正的版本约束
        return version != null && !version.isEmpty() && !"*".equals(version);
    }

    /**
     * 检查是否使用默认 groupId
     *
     * @return 如果使用默认 groupId 返回 true
     */
    public boolean isDefaultGroupId() {
        return DEFAULT_GROUP_ID.equals(groupId);
    }

    // ========== equals and hashCode ==========

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DependencyInfo that = (DependencyInfo) o;
        return Objects.equals(groupId, that.groupId) &&
               Objects.equals(artifactId, that.artifactId) &&
               Objects.equals(version, that.version);
    }

    @Override
    public int hashCode() {
        return Objects.hash(groupId, artifactId, version);
    }

    // ========== toString ==========

    @Override
    public String toString() {
        return "DependencyInfo{" +
                "groupId='" + groupId + '\'' +
                ", artifactId='" + artifactId + '\'' +
                ", version='" + version + '\'' +
                '}';
    }

    /**
     * 获取简短字符串表示（用于日志）
     *
     * @return 简短字符串，如 "com.ecat:integration-modbus:^1.0.0" 或 "com.ecat:integration-modbus:*"
     */
    public String toShortString() {
        StringBuilder sb = new StringBuilder();
        sb.append(groupId).append(":").append(artifactId);
        if (version != null && !version.isEmpty()) {
            sb.append(":").append(version);
        }
        return sb.toString();
    }
}
