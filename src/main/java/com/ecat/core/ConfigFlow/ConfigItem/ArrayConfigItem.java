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

import com.ecat.core.Utils.DynamicConfig.ConstraintValidator;
import com.ecat.core.Utils.DynamicConfig.ListSizeValidator;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 数组/列表配置项
 * <p>
 * 用于定义数组类型的配置字段，使用 {@link ListSizeValidator} 进行大小验证。
 * <p>
 * 示例：
 * <pre>{@code
 * ArrayConfigItem<String> tags = new ArrayConfigItem<>("tags", true)
 *     .displayName("标签列表")
 *     .size(1, 10);
 * }</pre>
 *
 * @param <T> 数组元素类型
 * @author ECAT Core
 */
public class ArrayConfigItem<T> extends AbstractConfigItem<List<T>> {

    /**
     * 数组元素类型（用于前端渲染）
     */
    private final String elementType;

    /**
     * 构造函数
     *
     * @param key 配置项键
     * @param required 是否必需
     * @param elementType 元素类型（用于前端渲染）
     */
    public ArrayConfigItem(String key, boolean required, String elementType) {
        super(key, required);
        this.elementType = elementType;
    }

    /**
     * 构造函数
     *
     * @param key 配置项键
     * @param required 是否必需
     */
    public ArrayConfigItem(String key, boolean required) {
        this(key, required, "string");
    }

    /**
     * 构造函数
     *
     * @param key 配置项键
     * @param required 是否必需
     * @param defaultValue 默认值
     * @param elementType 元素类型
     */
    public ArrayConfigItem(String key, boolean required, List<T> defaultValue, String elementType) {
        super(key, required, defaultValue);
        this.elementType = elementType;
    }

    /**
     * 构造函数
     *
     * @param key 配置项键
     * @param required 是否必需
     * @param defaultValue 默认值
     */
    public ArrayConfigItem(String key, boolean required, List<T> defaultValue) {
        this(key, required, defaultValue, "string");
    }

    /**
     * 设置数组大小约束
     * <p>
     * 创建并添加 {@link ListSizeValidator}。
     *
     * @param minSize 最小元素数量
     * @param maxSize 最大元素数量
     * @return this
     */
    @SuppressWarnings("unchecked")
    public ArrayConfigItem<T> size(int minSize, int maxSize) {
        addValidator(new ListSizeValidator<>(minSize, maxSize));
        return this;
    }

    /**
     * 获取元素类型
     *
     * @return 元素类型
     */
    public String getElementType() {
        return elementType;
    }

    @Override
    @SuppressWarnings("unchecked")
    protected String validateType(Object value) {
        if (!(value instanceof List)) {
            return displayName != null
                ? displayName + " 必须是数组类型"
                : "配置项 " + key + " 必须是数组类型";
        }
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public String validate(Object value) {
        if (value == null) {
            if (required) {
                return displayName != null
                    ? displayName + " 是必需的"
                    : "配置项 " + key + " 是必需的";
            }
            return null;
        }

        String typeError = validateType(value);
        if (typeError != null) {
            return typeError;
        }

        List<T> typedValue = (List<T>) value;

        for (ConstraintValidator<?> validator : validators) {
            @SuppressWarnings("unchecked")
            ConstraintValidator<List<T>> listValidator = (ConstraintValidator<List<T>>) validator;
            if (!listValidator.validate(typedValue)) {
                return displayName != null
                    ? displayName + ": " + validator.getErrorMessage()
                    : "配置项 " + key + ": " + validator.getErrorMessage();
            }
        }

        return null;
    }

    @Override
    public void addDefaultValue(Map<String, Object> config) {
        if (!config.containsKey(key) && defaultValue != null) {
            config.put(key, new ArrayList<>(defaultValue));
        }
    }

    @Override
    public List<T> getDefaultValue() {
        return defaultValue != null ? new ArrayList<>(defaultValue) : null;
    }

    @Override
    public String getFieldType() {
        return "array";
    }
}
