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
import com.ecat.core.Utils.DynamicConfig.Validator.NumericRangeValidator;

import java.util.Map;

/**
 * 数值配置项
 * <p>
 * 用于定义数值类型的配置字段（Double），使用 {@link NumericRangeValidator} 进行范围验证。
 * <p>
 * 示例：
 * <pre>{@code
 * NumericConfigItem port = new NumericConfigItem("port", true, 502.0)
 *     .displayName("端口号")
 *     .range(1, 65535);
 * }</pre>
 *
 * @author coffee
 */
public class NumericConfigItem extends AbstractConfigItem<Double> {

    /**
     * 构造函数
     *
     * @param key 配置项键
     * @param required 是否必需
     */
    public NumericConfigItem(String key, boolean required) {
        super(key, required);
    }

    /**
     * 构造函数
     *
     * @param key 配置项键
     * @param required 是否必需
     * @param defaultValue 默认值
     */
    public NumericConfigItem(String key, boolean required, Double defaultValue) {
        super(key, required, defaultValue);
    }

    /**
     * 构造函数（接受 float 以方便使用）
     *
     * @param key 配置项键
     * @param required 是否必需
     * @param defaultValue 默认值
     */
    public NumericConfigItem(String key, boolean required, float defaultValue) {
        super(key, required, (double) defaultValue);
    }

    /**
     * 设置显示名称
     *
     * @param displayName 显示名称
     * @return this
     */
    @Override
    public NumericConfigItem displayName(String displayName) {
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
    public NumericConfigItem placeholder(String placeholder) {
        this.placeholder = placeholder;
        return this;
    }

    /**
     * 设置数值范围约束
     * <p>
     * 创建并添加 {@link NumericRangeValidator}。
     *
     * @param minValue 最小值
     * @param maxValue 最大值
     * @return this
     */
    public NumericConfigItem range(double minValue, double maxValue) {
        addValidator(new NumericRangeValidator(minValue, maxValue));
        return this;
    }

    @Override
    protected String validateType(Object value) {
        // 支持 Double, Integer, Long, Float, BigDecimal (FastJSON2 解析 JSON 数字为 BigDecimal)
        // 也支持 String 类型（前端输入）
        if (value instanceof Double || value instanceof Integer || value instanceof Long ||
            value instanceof Float || value instanceof java.math.BigDecimal) {
            return null;
        }
        // 尝试解析 String
        if (value instanceof String) {
            try {
                Double.parseDouble((String) value);
                return null;
            } catch (NumberFormatException e) {
                return displayName != null
                    ? displayName + " 必须是有效的数值"
                    : "配置项 " + key + " 必须是有效的数值";
            }
        }
        return displayName != null
            ? displayName + " 必须是数值类型"
            : "配置项 " + key + " 必须是数值类型";
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

        // 转换为 Double 进行验证（与 validate() 保持一致的类型转换逻辑）
        Double typedValue;
        if (value instanceof Double) {
            typedValue = (Double) value;
        } else if (value instanceof Integer) {
            typedValue = ((Integer) value).doubleValue();
        } else if (value instanceof Long) {
            typedValue = ((Long) value).doubleValue();
        } else if (value instanceof Float) {
            typedValue = ((Float) value).doubleValue();
        } else if (value instanceof java.math.BigDecimal) {
            typedValue = ((java.math.BigDecimal) value).doubleValue();
        } else if (value instanceof String) {
            try {
                typedValue = Double.parseDouble((String) value);
            } catch (NumberFormatException e) {
                return displayName != null
                    ? displayName + " 必须是有效的数值"
                    : "配置项 " + key + " 必须是有效的数值";
            }
        } else {
            return displayName != null
                ? displayName + " 必须是数值类型"
                : "配置项 " + key + " 必须是数值类型";
        }

        for (ConstraintValidator<?> validator : validators) {
            ConstraintValidator<Double> doubleValidator = (ConstraintValidator<Double>) validator;
            if (!doubleValidator.validate(typedValue)) {
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
            config.put(key, defaultValue);
        }
    }

    @Override
    public Double getDefaultValue() {
        return defaultValue;
    }

    @Override
    public String getFieldType() {
        return "number";
    }
}
