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

package com.ecat.core.ConfigFlow;

/**
 * Schema 提供者接口
 * <p>
 * 类名作为 Schema 标识，通过 {@link #createSchema()} 方法创建 Schema 实例。
 * 用于实现 Schema 的复用和引用机制。
 *
 * @author coffee
 */
public interface ConfigSchemaProvider {

    /**
     * 创建 Schema
     *
     * @return Schema 实例
     */
    ConfigSchema createSchema();

    /**
     * 返回 strings.json 中此 schema 翻译的 key 前缀。
     * <p>
     * SchemaConversionService 使用此前缀在定义方 namespace 中查找翻译。
     * 例如 SerialCommConfigSchema 返回 "config_schemas.serial_comm"，
     * 对应 serial 集成 strings.json 中 config_schemas.serial_comm.* 下的翻译条目。
     *
     * @return i18n key 前缀，默认 null（表示不提供 schema 级翻译）
     */
    default String getI18nKeyPrefix() { return null; }

    /**
     * 返回此 Provider 所属集成的 coordinate（如 "com.ecat:integration-serial"）。
     * <p>
     * 默认 null → 由 {@link com.ecat.core.Utils.IntegrationCoordinateHelper} 从 JAR MANIFEST 自动检测。
     * 非 null → 使用显式值（适用于单元测试等无法自动检测的场景）。
     *
     * @return coordinate 字符串，默认 null（自动检测）
     */
    default String getCoordinate() { return null; }
}
