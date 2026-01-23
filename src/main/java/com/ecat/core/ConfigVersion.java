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

package com.ecat.core;

/**
 * 配置文件版本常量
 *
 * <p>此版本号用于：</p>
 * <ul>
 *   <li>标记 integrations.yml 配置文件的格式版本</li>
 *   <li>支持配置文件的升级和兼容性检查</li>
 *   <li>与 CLI 版本保持同步</li>
 * </ul>
 *
 * <p>配置文件格式示例：</p>
 * <pre>
 * version: "1.0.0"  # 配置文件版本，与 CLI 版本同步
 * core:
 *   groupId: com.ecat
 *   artifactId: ecat-core
 *   version: 1.0.0
 * integrations:
 *   ...
 * </pre>
 *
 * @author coffee
 * @version 1.0.0
 */
public final class ConfigVersion {

    /**
     * 配置文件版本号
     *
     * <p>此版本号应与 CLI 版本保持一致，用于：
     * <ul>
     *   <li>配置文件格式版本控制</li>
     *   <li>配置迁移和兼容性检查</li>
     *   <li>追踪配置文件创建来源</li>
     * </ul>
     */
    public static final String CURRENT_VERSION = "1.0.0";

    private ConfigVersion() {
        // 工具类，禁止实例化
    }

    /**
     * 获取当前配置文件版本号
     *
     * @return 版本号，如 "1.0.0"
     */
    public static String getVersion() {
        return CURRENT_VERSION;
    }

    /**
     * 检查配置文件版本是否兼容
     *
     * @param configVersion 配置文件中的版本号
     * @return 如果版本兼容返回 true，否则返回 false
     */
    public static boolean isCompatible(String configVersion) {
        if (configVersion == null || configVersion.isEmpty()) {
            // 旧版本配置文件没有 version 字段，视为兼容
            return true;
        }
        // 当前只有一个版本，所有 1.x 版本都兼容
        return configVersion.startsWith("1.");
    }
}
