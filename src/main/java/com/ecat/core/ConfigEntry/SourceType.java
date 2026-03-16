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

/**
 * 流程来源类型枚举
 * <p>
 * 用于标识 ConfigEntry 的创建来源。
 *
 * @author coffee
 */
public enum SourceType {

    /**
     * 用户主动创建
     */
    USER,

    /**
     * 重新配置已有 entry
     */
    RECONFIGURE,

    /**
     * 设备自动发现 (预留)
     */
    DISCOVERY
}
