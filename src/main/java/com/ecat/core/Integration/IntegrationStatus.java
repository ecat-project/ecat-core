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

import java.util.Date;
import java.util.List;

import lombok.Getter;
import lombok.Setter;

/**
 * 集成状态信息 DTO
 *
 * <p>包含集成的完整状态信息，用于查询和展示集成当前状态。
 *
 * @author coffee
 */
public class IntegrationStatus {

    /**
     * 集成坐标 (groupId:artifactId)
     */
    @Getter
    @Setter
    private String coordinate;

    /**
     * 当前状态
     */
    @Getter
    @Setter
    private IntegrationState state;

    /**
     * 状态消息
     */
    @Getter
    @Setter
    private String message;

    /**
     * 依赖此集成的其他集成列表
     */
    @Getter
    @Setter
    private List<String> dependents;

    /**
     * 此集成的依赖列表
     */
    @Getter
    @Setter
    private List<String> dependencies;

    /**
     * 是否可以停用（无依赖者且非 PENDING 状态）
     */
    @Getter
    @Setter
    private boolean canDisable;

    /**
     * 是否可以卸载（无依赖者且非 PENDING 状态）
     */
    @Getter
    @Setter
    private boolean canRemove;

    /**
     * 是否可以启用（非 PENDING 状态）
     */
    @Getter
    @Setter
    private boolean canEnable;

    /**
     * 是否可以升级（非 PENDING 状态）
     */
    @Getter
    @Setter
    private boolean canUpgrade;

    /**
     * 是否处于 PENDING_* 状态（禁止操作）
     */
    @Getter
    @Setter
    private boolean isLocked;

    /**
     * 当前版本号
     */
    @Getter
    @Setter
    private String version;

    /**
     * 待升级的版本号（仅 PENDING_UPGRADE 状态有效）
     */
    @Getter
    @Setter
    private String pendingVersion;

    /**
     * 最后更新时间
     */
    @Getter
    @Setter
    private Date lastUpdate;

    /**
     * 默认构造函数
     */
    public IntegrationStatus() {
        this.dependents = new java.util.ArrayList<>();
        this.dependencies = new java.util.ArrayList<>();
        this.lastUpdate = new Date();
    }

    /**
     * 完整构造函数
     */
    public IntegrationStatus(String coordinate, IntegrationState state, String message,
                            List<String> dependents, List<String> dependencies,
                            boolean canDisable, boolean canRemove, boolean canEnable, boolean canUpgrade,
                            boolean isLocked, String version, String pendingVersion, Date lastUpdate) {
        this.coordinate = coordinate;
        this.state = state;
        this.message = message;
        this.dependents = dependents != null ? dependents : new java.util.ArrayList<>();
        this.dependencies = dependencies != null ? dependencies : new java.util.ArrayList<>();
        this.canDisable = canDisable;
        this.canRemove = canRemove;
        this.canEnable = canEnable;
        this.canUpgrade = canUpgrade;
        this.isLocked = isLocked;
        this.version = version;
        this.pendingVersion = pendingVersion;
        this.lastUpdate = lastUpdate != null ? lastUpdate : new Date();
    }

    /**
     * 创建构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 构建器类
     */
    public static class Builder {
        private String coordinate;
        private IntegrationState state;
        private String message;
        private List<String> dependents = new java.util.ArrayList<>();
        private List<String> dependencies = new java.util.ArrayList<>();
        private boolean canDisable;
        private boolean canRemove;
        private boolean canEnable;
        private boolean canUpgrade;
        private boolean isLocked;
        private String version;
        private String pendingVersion;
        private Date lastUpdate = new Date();

        public Builder coordinate(String coordinate) {
            this.coordinate = coordinate;
            return this;
        }

        public Builder state(IntegrationState state) {
            this.state = state;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder dependents(List<String> dependents) {
            this.dependents = dependents != null ? dependents : new java.util.ArrayList<>();
            return this;
        }

        public Builder dependencies(List<String> dependencies) {
            this.dependencies = dependencies != null ? dependencies : new java.util.ArrayList<>();
            return this;
        }

        public Builder canDisable(boolean canDisable) {
            this.canDisable = canDisable;
            return this;
        }

        public Builder canRemove(boolean canRemove) {
            this.canRemove = canRemove;
            return this;
        }

        public Builder canEnable(boolean canEnable) {
            this.canEnable = canEnable;
            return this;
        }

        public Builder canUpgrade(boolean canUpgrade) {
            this.canUpgrade = canUpgrade;
            return this;
        }

        public Builder isLocked(boolean isLocked) {
            this.isLocked = isLocked;
            return this;
        }

        public Builder version(String version) {
            this.version = version;
            return this;
        }

        public Builder pendingVersion(String pendingVersion) {
            this.pendingVersion = pendingVersion;
            return this;
        }

        public Builder lastUpdate(Date lastUpdate) {
            this.lastUpdate = lastUpdate != null ? lastUpdate : new Date();
            return this;
        }

        public IntegrationStatus build() {
            return new IntegrationStatus(coordinate, state, message, dependents, dependencies,
                canDisable, canRemove, canEnable, canUpgrade, isLocked, version, pendingVersion, lastUpdate);
        }
    }

    @Override
    public String toString() {
        return "IntegrationStatus{" +
                "coordinate='" + coordinate + '\'' +
                ", state=" + state +
                ", message='" + message + '\'' +
                ", dependents=" + dependents +
                ", dependencies=" + dependencies +
                ", canDisable=" + canDisable +
                ", canRemove=" + canRemove +
                ", canEnable=" + canEnable +
                ", canUpgrade=" + canUpgrade +
                ", isLocked=" + isLocked +
                ", version='" + version + '\'' +
                ", pendingVersion='" + pendingVersion + '\'' +
                ", lastUpdate=" + lastUpdate +
                '}';
    }

    /**
     * 检查是否处于运行状态
     */
    public boolean isRunning() {
        return state != null && state.isRunning();
    }

    /**
     * 检查是否处于停止状态
     */
    public boolean isStopped() {
        return state != null && state.isStopped();
    }

    // 显式 getter 方法，提供更自然的 API

    public boolean canDisable() {
        return canDisable;
    }

    public boolean canRemove() {
        return canRemove;
    }

    public boolean canEnable() {
        return canEnable;
    }

    public boolean canUpgrade() {
        return canUpgrade;
    }
}
