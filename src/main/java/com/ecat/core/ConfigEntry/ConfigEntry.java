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

package com.ecat.core.ConfigEntry;

import com.ecat.core.Utils.DateTimeUtils;
import lombok.Data;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 配置条目模型
 * <p>
 * 封装集成的配置条目信息，支持创建、更新和版本管理。
 *
 * @author coffee
 */
@Data
public class ConfigEntry {

    /**
     * 系统生成 UUID
     */
    private String entryId;

    /**
     * 集成标识 (groupId:artifactId)
     */
    private String coordinate;

    /**
     * 业务唯一标识 (由集成生成)
     * 示例: 厂家_sn
     */
    private String uniqueId;

    /**
     * 配置名称 (用户可编辑)
     */
    private String title;

    /**
     * 核心配置数据
     */
    private Map<String, Object> data;

    /**
     * 启用状态
     */
    private boolean enabled;

    /**
     * 创建时间 (时区感知)
     */
    private ZonedDateTime createTime;

    /**
     * 更新时间 (时区感知)
     */
    private ZonedDateTime updateTime;

    /**
     * 版本号 (修改时自动+1)
     */
    private int version;

    // ==================== Builder ====================

    /**
     * Builder 构建器
     */
    public static class Builder {
        private String entryId;
        private String coordinate;
        private String uniqueId;
        private String title;
        private Map<String, Object> data = new HashMap<>();
        private boolean enabled = true;
        private ZonedDateTime createTime;
        private ZonedDateTime updateTime;
        private int version = 1;

        public Builder entryId(String entryId) {
            this.entryId = entryId;
            return this;
        }

        public Builder coordinate(String coordinate) {
            this.coordinate = coordinate;
            return this;
        }

        public Builder uniqueId(String uniqueId) {
            this.uniqueId = uniqueId;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder data(Map<String, Object> data) {
            this.data = data;
            return this;
        }

        public Builder enabled(boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public Builder createTime(ZonedDateTime createTime) {
            this.createTime = createTime;
            return this;
        }

        public Builder updateTime(ZonedDateTime updateTime) {
            this.updateTime = updateTime;
            return this;
        }

        public Builder version(int version) {
            this.version = version;
            return this;
        }

        public ConfigEntry build() {
            ConfigEntry entry = new ConfigEntry();
            entry.entryId = entryId;
            entry.coordinate = coordinate;
            entry.uniqueId = uniqueId;
            entry.title = title;
            entry.data = new HashMap<>(data);
            entry.enabled = enabled;
            entry.createTime = createTime;
            entry.updateTime = updateTime;
            entry.version = version;
            return entry;
        }
    }

    // ==================== 便捷方法 ====================

    /**
     * 更新数据 (保留 entryId, coordinate, uniqueId)
     *
     * @param newData 新的配置数据
     * @return 更新后的 ConfigEntry
     */
    public ConfigEntry withUpdate(ConfigEntry newData) {
        return new Builder()
                .entryId(this.entryId)
                .coordinate(this.coordinate)
                .uniqueId(this.uniqueId)
                .title(newData.title != null ? newData.title : this.title)
                .data(newData.data != null ? newData.data : this.data)
                .enabled(newData.enabled)
                .createTime(this.createTime)
                .updateTime(DateTimeUtils.now())
                .version(this.version + 1)
                .build();
    }

    /**
     * 重新配置 (不增加版本号)
     * <p>
     * 用于 reconfigure flow，只更新数据和配置，不改变版本号。
     *
     * @param newData 新的配置数据
     * @return 更新后的 ConfigEntry
     */
    public ConfigEntry withReconfigure(ConfigEntry newData) {
        return new Builder()
                .entryId(this.entryId)
                .coordinate(this.coordinate)
                .uniqueId(newData.uniqueId != null ? newData.uniqueId : this.uniqueId)
                .title(newData.title != null ? newData.title : this.title)
                .data(newData.data != null ? newData.data : this.data)
                .enabled(newData.enabled)
                .createTime(this.createTime)
                .updateTime(DateTimeUtils.now())
                .version(this.version)  // 保持版本号不变
                .build();
    }
}
