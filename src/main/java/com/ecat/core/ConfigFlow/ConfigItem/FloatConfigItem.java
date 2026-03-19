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
import com.ecat.core.Utils.DynamicConfig.Validator.FloatRangeValidator;

import java.util.Map;

/**
 * 浮点数配置项
 * <p>
 * 用于定义浮点数类型的配置字段（Float），使用 {@link FloatRangeValidator} 进行范围验证。
 * <p>
 * 示例：
 * <pre>{@code
 * FloatConfigItem temperature = new FloatConfigItem("temperature", true, 25.0f)
 *     .displayName("温度")
 *     .range(-40.0f, 100.0f);
 * }</pre>
 *
 * @author coffee
 */
public class FloatConfigItem extends AbstractConfigItem<Float> {

    /**
     * 构造函数
     *
     * @param key 配置项键
     * @param required 是否必需
     */
    public FloatConfigItem(String key, boolean required) {
        super(key, required);
    }

    /**
     * 构造函数
     *
     * @param key 配置项键
     * @param required 是否必需
     * @param defaultValue 默认值
     */
    public FloatConfigItem(String key, boolean required, Float defaultValue) {
        super(key, required, defaultValue);
    }

    /**
     * 设置显示名称
     *
     * @param displayName 显示名称
     * @return this
     */
    @Override
    public FloatConfigItem displayName(String displayName) {
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
    public FloatConfigItem placeholder(String placeholder) {
        this.placeholder = placeholder;
        return this;
    }

    /**
     * 设置浮点数范围约束
     * <p>
     * 创建并添加 {@link FloatRangeValidator}。
     *
     * @param minValue 最小值
     * @param maxValue 最大值
     * @return this
     */
    public FloatConfigItem range(float minValue, float maxValue) {
        addValidator(new FloatRangeValidator(minValue, maxValue));
        return this;
    }

    @Override
    protected String validateType(Object value) {
        // 支持 Float, Double, Integer, Long, BigDecimal (FastJSON2 解析 JSON 数字)
        // 也支持 String 类型（前端输入）
        if (value instanceof Float || value instanceof Double || value instanceof Integer ||
            value instanceof Long || value instanceof java.math.BigDecimal) {
            return null;
        }
        // 尝试解析 String
        if (value instanceof String) {
            try {
                Float.parseFloat((String) value);
                return null;
            } catch (NumberFormatException e) {
                return displayName != null
                    ? displayName + " 必须是有效的浮点数"
                    : "配置项 " + key + " 必须是有效的浮点数";
            }
        }
        return displayName != null
            ? displayName + " 必须是浮点数类型"
            : "配置项 " + key + " 必须是浮点数类型";
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

        // 转换为 Float 进行验证
        Float typedValue;
        if (value instanceof Float) {
            typedValue = (Float) value;
        } else if (value instanceof Double) {
            typedValue = ((Double) value).floatValue();
        } else if (value instanceof Integer) {
            typedValue = ((Integer) value).floatValue();
        } else if (value instanceof Long) {
            typedValue = ((Long) value).floatValue();
        } else if (value instanceof java.math.BigDecimal) {
            typedValue = ((java.math.BigDecimal) value).floatValue();
        } else if (value instanceof String) {
            try {
                typedValue = Float.parseFloat((String) value);
            } catch (NumberFormatException e) {
                return displayName != null
                    ? displayName + " 必须是有效的浮点数"
                    : "配置项 " + key + " 必须是有效的浮点数";
            }
        } else {
            return displayName != null
                ? displayName + " 必须是浮点数类型"
                : "配置项 " + key + " 必须是浮点数类型";
        }

        for (ConstraintValidator<?> validator : validators) {
            ConstraintValidator<Float> floatValidator = (ConstraintValidator<Float>) validator;
            if (!floatValidator.validate(typedValue)) {
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
    public Float getDefaultValue() {
        return defaultValue;
    }

    @Override
    public String getFieldType() {
        return "number";
    }
}
