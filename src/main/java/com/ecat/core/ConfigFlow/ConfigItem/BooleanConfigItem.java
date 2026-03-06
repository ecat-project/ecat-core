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

import com.ecat.core.Utils.DynamicConfig.Validator.BooleanValidator;

import java.util.Map;

/**
 * 布尔配置项
 * <p>
 * 用于定义布尔类型的配置字段，使用 {@link BooleanValidator} 进行验证。
 * <p>
 * 示例：
 * <pre>{@code
 * BooleanConfigItem enabled = new BooleanConfigItem("enabled", true, true)
 *     .displayName("启用状态");
 * }</pre>
 *
 * @author coffee
 */
public class BooleanConfigItem extends AbstractConfigItem<Boolean> {

    /**
     * 构造函数
     *
     * @param key 配置项键
     * @param required 是否必需
     */
    public BooleanConfigItem(String key, boolean required) {
        super(key, required);
        // 添加布尔验证器
        addValidator(new BooleanValidator());
    }

    /**
     * 构造函数
     *
     * @param key 配置项键
     * @param required 是否必需
     * @param defaultValue 默认值
     */
    public BooleanConfigItem(String key, boolean required, Boolean defaultValue) {
        super(key, required, defaultValue);
        // 添加布尔验证器
        addValidator(new BooleanValidator());
    }

    /**
     * 设置显示名称
     *
     * @param displayName 显示名称
     * @return this
     */
    @Override
    public BooleanConfigItem displayName(String displayName) {
        this.displayName = displayName;
        return this;
    }

    /**
     * 设置占位符
     *
     * @param placeholder 占位符
     * @return this
     */
    @Override
    public BooleanConfigItem placeholder(String placeholder) {
        this.placeholder = placeholder;
        return this;
    }

    @Override
    protected String validateType(Object value) {
        if (!(value instanceof Boolean)) {
            return displayName != null
                ? displayName + " 必须是布尔类型"
                : "配置项 " + key + " 必须是布尔类型";
        }
        return null;
    }

    @Override
    public void addDefaultValue(Map<String, Object> config) {
        if (!config.containsKey(key) && defaultValue != null) {
            config.put(key, defaultValue);
        }
    }

    @Override
    public Boolean getDefaultValue() {
        return defaultValue;
    }

    @Override
    public String getFieldType() {
        return "boolean";
    }
}
