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

package com.ecat.core.ConfigFlow.ConfigItem;

import com.ecat.core.ConfigFlow.ConfigSchema;
import com.ecat.core.ConfigFlow.ConfigSchemaProvider;

import java.util.Map;

/**
 * Schema 字段 - 统一处理嵌套和引用
 * <p>
 * 支持两种模式：
 * <ul>
 *   <li>嵌套模式：直接定义嵌套的 Schema</li>
 *   <li>引用模式：通过 Provider 类引用外部 Schema</li>
 * </ul>
 * <p>
 * 扩展模式：将嵌套字段提升到父级，不产生嵌套对象。
 *
 * @author coffee
 */
public class SchemaConfigItem extends AbstractConfigItem<Map<String, Object>> {

    /** 直接定义的嵌套 Schema */
    private ConfigSchema nestedSchema;

    /** Schema 提供者类（用于引用外部 Schema） */
    private Class<? extends ConfigSchemaProvider> schemaProvider;

    /** 是否扩展模式（字段提升到父级） */
    private boolean extend = false;

    /**
     * 创建嵌套 Schema（直接定义）
     *
     * @param key      字段名
     * @param required 是否必填
     *                 - true: 该嵌套对象字段本身必须存在（不能为 null 或缺失）
     *                 - false: 该嵌套对象字段可选
     *                 注意：嵌套对象内部的子字段有独立的 required 定义，递归验证
     * @param schema   嵌套的 Schema 定义
     */
    public SchemaConfigItem(String key, boolean required, ConfigSchema schema) {
        super(key, required);
        this.nestedSchema = schema;
    }

    /**
     * 创建引用 Schema（通过 Provider 类）
     *
     * @param key           字段名
     * @param required      是否必填（同上）
     * @param providerClass Schema 提供者类，通过 createSchema() 获取定义
     */
    public SchemaConfigItem(String key, boolean required,
                            Class<? extends ConfigSchemaProvider> providerClass) {
        super(key, required);
        this.schemaProvider = providerClass;
    }

    /**
     * 扩展模式：将嵌套字段提升到父级
     * <p>
     * 扩展模式下，嵌套字段直接出现在父级 JSON 中，不产生嵌套对象。
     *
     * @return this，支持链式调用
     */
    public SchemaConfigItem extend() {
        this.extend = true;
        return this;
    }

    /**
     * 是否扩展模式
     *
     * @return true 表示扩展模式
     */
    public boolean isExtend() {
        return extend;
    }

    /**
     * 解析 Schema（延迟解析）
     *
     * @return Schema 实例
     */
    public ConfigSchema resolveSchema() {
        if (nestedSchema != null) {
            return nestedSchema;
        }
        if (schemaProvider != null) {
            try {
                return schemaProvider.getDeclaredConstructor().newInstance().createSchema();
            } catch (Exception e) {
                throw new RuntimeException("Failed to create schema: " + schemaProvider, e);
            }
        }
        return null;
    }

    @Override
    public String getFieldType() {
        return "schema";
    }

    @Override
    protected String validateType(Object value) {
        // Map 类型检查
        if (value != null && !(value instanceof Map)) {
            return displayName != null
                ? displayName + " 必须是对象类型"
                : "配置项 " + key + " 必须是对象类型";
        }
        return null;
    }

    @Override
    public String validate(Object value) {
        // 空值检查和 required 验证委托给父类
        String baseError = super.validate(value);
        if (baseError != null) {
            return baseError;
        }

        ConfigSchema schema = resolveSchema();
        if (schema == null) {
            return null;
        }

        // value 为 null 时，required 已在父类检查中处理
        if (value == null) {
            return null;
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> mapValue = (Map<String, Object>) value;

        // 递归验证嵌套字段（每个子字段有自己的 required 定义）
        Map<String, String> errors = schema.validate(mapValue);
        if (!errors.isEmpty()) {
            return errors.toString();
        }
        return null;
    }

    @Override
    public void addDefaultValue(Map<String, Object> config) {
        if (!config.containsKey(key) && defaultValue != null) {
            config.put(key, defaultValue);
        }
        // 如果值为空 Map，填充默认值
        Object existingValue = config.get(key);
        if (existingValue instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> nestedConfig = (Map<String, Object>) existingValue;
            ConfigSchema schema = resolveSchema();
            if (schema != null) {
                schema.addDefaults(nestedConfig);
            }
        }
    }

    @Override
    public Map<String, Object> getDefaultValue() {
        return defaultValue;
    }
}
