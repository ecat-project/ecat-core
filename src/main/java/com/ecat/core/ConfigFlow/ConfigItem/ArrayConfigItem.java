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

import java.util.*;

/**
 * 多选数组配置项
 * <p>
 * 用于定义多选类型的配置字段，用户从预定义选项中选择多个值。
 * 必须提供选项列表，不允许用户自由输入。
 * <p>
 * 示例：
 * <pre>{@code
 * ArrayConfigItem features = new ArrayConfigItem("features", true)
 *     .displayName("启用功能")
 *     .addOption("sync", "自动同步")
 *     .addOption("backup", "数据备份")
 *     .addOption("monitor", "远程监控")
 *     .size(1, 3)
 *     .buildValidator();
 * }</pre>
 *
 * @param <T> 数组元素类型
 * @author coffee
 */
public class ArrayConfigItem<T> extends AbstractConfigItem<List<T>> {

    private final Set<T> validValues = new LinkedHashSet<>();
    private final Map<T, String> optionLabels = new LinkedHashMap<>();
    private final String elementType;

    /**
     * 构造函数
     *
     * @param key 配置项键
     * @param required 是否必需
     */
    public ArrayConfigItem(String key, boolean required) {
        super(key, required);
        this.elementType = "string";
    }

    /**
     * 构造函数
     *
     * @param key 配置项键
     * @param required 是否必需
     * @param elementType 元素类型（用于前端渲染）
     */
    public ArrayConfigItem(String key, boolean required, String elementType) {
        super(key, required);
        this.elementType = elementType != null ? elementType : "string";
    }

    /**
     * 构造函数
     *
     * @param key 配置项键
     * @param required 是否必需
     * @param defaultValue 默认值
     */
    public ArrayConfigItem(String key, boolean required, List<T> defaultValue) {
        super(key, required, defaultValue);
        this.elementType = "string";
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
        this.elementType = elementType != null ? elementType : "string";
    }

    /**
     * 设置显示名称
     *
     * @param displayName 显示名称
     * @return this
     */
    @Override
    public ArrayConfigItem<T> displayName(String displayName) {
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
    public ArrayConfigItem<T> placeholder(String placeholder) {
        this.placeholder = placeholder;
        return this;
    }

    /**
     * 添加选项
     *
     * @param value 选项值
     * @param label 显示标签
     * @return this
     */
    public ArrayConfigItem<T> addOption(T value, String label) {
        validValues.add(value);
        optionLabels.put(value, label);
        return this;
    }

    /**
     * 添加选项（只有值，显示标签与值相同）
     *
     * @param value 选项值
     * @return this
     */
    public ArrayConfigItem<T> addOption(T value) {
        return addOption(value, String.valueOf(value));
    }

    /**
     * 批量添加选项
     *
     * @param options 选项映射 (value -> label)
     * @return this
     */
    public ArrayConfigItem<T> addOptions(Map<T, String> options) {
        if (options != null) {
            for (Map.Entry<T, String> entry : options.entrySet()) {
                addOption(entry.getKey(), entry.getValue());
            }
        }
        return this;
    }

    /**
     * 设置默认值
     *
     * @param defaultValue 默认值列表
     * @return this
     */
    public ArrayConfigItem<T> defaultValue(List<T> defaultValue) {
        this.defaultValue = defaultValue != null ? new ArrayList<>(defaultValue) : null;
        return this;
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
    public ArrayConfigItem<T> size(int minSize, int maxSize) {
        addValidator(new ListSizeValidator<>(minSize, maxSize));
        return this;
    }

    /**
     * 获取有效值集合
     *
     * @return 有效值集合
     */
    public Set<T> getValidValues() {
        return new LinkedHashSet<>(validValues);
    }

    /**
     * 获取选项标签映射
     *
     * @return 选项标签映射
     */
    public Map<T, String> getOptionLabels() {
        return new LinkedHashMap<>(optionLabels);
    }

    /**
     * 获取选项的显示标签
     *
     * @param value 选项值
     * @return 显示标签
     */
    public String getOptionLabel(T value) {
        return optionLabels.getOrDefault(value, String.valueOf(value));
    }

    /**
     * 获取元素类型
     *
     * @return 元素类型
     */
    public String getElementType() {
        return elementType;
    }

    /**
     * 检查是否有选项定义
     *
     * @return 是否有选项
     */
    public boolean hasOptions() {
        return !validValues.isEmpty();
    }

    @Override
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
    public Object validate(Object value) {
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

        // 验证每个选中的值是否在有效选项中
        if (hasOptions()) {
            for (T item : typedValue) {
                if (!validValues.contains(item)) {
                    String displayValues = getDisplayValuesString();
                    return displayName != null
                        ? displayName + ": 请从有效选项中选择 (" + displayValues + ")"
                        : "配置项 " + key + ": 请从有效选项中选择 (" + displayValues + ")";
                }
            }
        }

        // 运行其他验证器（如大小验证）
        for (ConstraintValidator<?> validator : validators) {
            ConstraintValidator<List<T>> listValidator = (ConstraintValidator<List<T>>) validator;
            if (!listValidator.validate(typedValue)) {
                return displayName != null
                    ? displayName + ": " + validator.getErrorMessage()
                    : "配置项 " + key + ": " + validator.getErrorMessage();
            }
        }

        return null;
    }

    /**
     * 获取有效值的显示字符串（用于错误消息）
     */
    private String getDisplayValuesString() {
        if (optionLabels.isEmpty()) {
            return validValues.toString();
        }
        List<String> labels = new ArrayList<>();
        for (T val : validValues) {
            labels.add(optionLabels.getOrDefault(val, String.valueOf(val)));
        }
        return labels.toString();
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
