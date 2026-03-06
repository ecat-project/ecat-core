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

import java.util.*;

/**
 * 嵌套对象配置项
 * <p>
 * 用于定义嵌套对象类型的配置字段，支持递归验证嵌套的配置项。
 * <p>
 * 示例：
 * <pre>{@code
 * ObjectConfigItem address = new ObjectConfigItem("address", false)
 *     .displayName("地址信息")
 *     .addNestedItem(new TextConfigItem("city", true).displayName("城市"))
 *     .addNestedItem(new TextConfigItem("street", true).displayName("街道"));
 * }</pre>
 *
 * @author ECAT Core
 */
public class ObjectConfigItem extends AbstractConfigItem<Map<String, Object>> {

    private final Map<String, AbstractConfigItem<?>> nestedItems = new LinkedHashMap<>();

    /**
     * 构造函数
     *
     * @param key 配置项键
     * @param required 是否必需
     */
    public ObjectConfigItem(String key, boolean required) {
        super(key, required);
    }

    /**
     * 构造函数
     *
     * @param key 配置项键
     * @param required 是否必需
     * @param defaultValue 默认值
     */
    public ObjectConfigItem(String key, boolean required, Map<String, Object> defaultValue) {
        super(key, required, defaultValue);
    }

    /**
     * 添加嵌套配置项
     *
     * @param item 嵌套配置项
     * @return this
     */
    public ObjectConfigItem addNestedItem(AbstractConfigItem<?> item) {
        nestedItems.put(item.getKey(), item);
        return this;
    }

    /**
     * 获取嵌套配置项
     *
     * @return 嵌套配置项映射
     */
    public Map<String, AbstractConfigItem<?>> getNestedItems() {
        return new LinkedHashMap<>(nestedItems);
    }

    /**
     * 检查是否有嵌套配置项
     *
     * @return 是否有嵌套配置项
     */
    public boolean hasNestedItems() {
        return !nestedItems.isEmpty();
    }

    @Override
    protected String validateType(Object value) {
        if (!(value instanceof Map)) {
            return displayName != null
                ? displayName + " 必须是对象类型"
                : "配置项 " + key + " 必须是对象类型";
        }
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public String validate(Object value) {
        // 先执行基类的验证（null 检查等）
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

        // 递归验证嵌套配置项
        Map<String, Object> typedValue = (Map<String, Object>) value;
        for (AbstractConfigItem<?> nestedItem : nestedItems.values()) {
            Object nestedValue = typedValue.get(nestedItem.getKey());
            String nestedError = nestedItem.validate(nestedValue);
            if (nestedError != null) {
                return displayName != null
                    ? displayName + "." + nestedItem.getKey() + ": " + nestedError
                    : "配置项 " + key + "." + nestedItem.getKey() + ": " + nestedError;
            }
        }

        // 执行自身的验证器
        for (ConstraintValidator<?> validator : validators) {
            @SuppressWarnings("unchecked")
            ConstraintValidator<Map<String, Object>> mapValidator =
                (ConstraintValidator<Map<String, Object>>) validator;
            if (!mapValidator.validate(typedValue)) {
                return displayName != null
                    ? displayName + ": " + validator.getErrorMessage()
                    : "配置项 " + key + ": " + validator.getErrorMessage();
            }
        }

        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void addDefaultValue(Map<String, Object> config) {
        // 如果配置中没有该键且有默认值，则添加默认值
        if (!config.containsKey(key) && defaultValue != null) {
            Map<String, Object> defaultCopy = new LinkedHashMap<>(defaultValue);
            // 为嵌套项添加默认值
            for (AbstractConfigItem<?> nestedItem : nestedItems.values()) {
                nestedItem.addDefaultValue(defaultCopy);
            }
            config.put(key, defaultCopy);
        } else if (config.containsKey(key)) {
            // 如果配置中已有该键，确保嵌套项有默认值
            Object existingValue = config.get(key);
            if (existingValue instanceof Map) {
                Map<String, Object> existingMap = (Map<String, Object>) existingValue;
                for (AbstractConfigItem<?> nestedItem : nestedItems.values()) {
                    nestedItem.addDefaultValue(existingMap);
                }
            }
        }
    }

    @Override
    public Map<String, Object> getDefaultValue() {
        if (defaultValue == null) {
            return null;
        }
        Map<String, Object> copy = new LinkedHashMap<>(defaultValue);
        // 为嵌套项添加默认值
        for (AbstractConfigItem<?> nestedItem : nestedItems.values()) {
            nestedItem.addDefaultValue(copy);
        }
        return copy;
    }

    @Override
    public String getFieldType() {
        return "object";
    }

    /**
     * 填充嵌套对象的默认值
     * <p>
     * 为嵌套对象中的每个字段填充默认值。
     *
     * @param obj 嵌套对象映射
     */
    public void fillNestedDefaults(Map<String, Object> obj) {
        for (AbstractConfigItem<?> nestedItem : nestedItems.values()) {
            nestedItem.addDefaultValue(obj);
        }
    }
}
