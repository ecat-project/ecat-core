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

import com.ecat.core.Utils.DynamicConfig.Validator.TextLengthValidator;

import java.util.Map;

/**
 * 文本配置项
 * <p>
 * 用于定义字符串类型的配置字段，使用 {@link TextLengthValidator} 进行长度验证。
 * <p>
 * 示例：
 * <pre>{@code
 * TextConfigItem username = new TextConfigItem("username", true)
 *     .displayName("用户名")
 *     .length(1, 50);
 * }</pre>
 *
 * @author coffee
 */
public class TextConfigItem extends AbstractConfigItem<String> {

    /**
     * 构造函数
     *
     * @param key 配置项键
     * @param required 是否必需
     */
    public TextConfigItem(String key, boolean required) {
        super(key, required);
    }

    /**
     * 构造函数
     *
     * @param key 配置项键
     * @param required 是否必需
     * @param defaultValue 默认值
     */
    public TextConfigItem(String key, boolean required, String defaultValue) {
        super(key, required, defaultValue);
    }

    /**
     * 设置显示名称
     *
     * @param displayName 显示名称
     * @return this
     */
    @Override
    public TextConfigItem displayName(String displayName) {
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
    public TextConfigItem placeholder(String placeholder) {
        this.placeholder = placeholder;
        return this;
    }

    /**
     * 设置是否只读
     *
     * @param readOnly 是否只读
     * @return this
     */
    @Override
    public TextConfigItem readOnly(boolean readOnly) {
        this.readOnly = readOnly;
        return this;
    }

    /**
     * 设置文本长度约束
     * <p>
     * 创建并添加 {@link TextLengthValidator}。
     *
     * @param minLength 最小长度
     * @param maxLength 最大长度
     * @return this
     */
    public TextConfigItem length(int minLength, int maxLength) {
        addValidator(new TextLengthValidator(minLength, maxLength));
        return this;
    }

    @Override
    protected String validateType(Object value) {
        if (!(value instanceof String)) {
            return displayName != null
                ? displayName + " 必须是文本类型"
                : "配置项 " + key + " 必须是文本类型";
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
    public String getDefaultValue() {
        return defaultValue;
    }

    @Override
    public String getFieldType() {
        return "string";
    }
}
