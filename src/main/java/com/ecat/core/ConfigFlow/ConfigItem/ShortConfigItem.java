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
import com.ecat.core.Utils.DynamicConfig.Validator.ShortRangeValidator;

import java.util.Map;

/**
 * 短整型配置项
 * <p>
 * 用于定义短整型类型的配置字段（Short），使用 {@link ShortRangeValidator} 进行范围验证。
 * <p>
 * 示例：
 * <pre>{@code
 * ShortConfigItem priority = new ShortConfigItem("priority", true, (short) 5)
 *     .displayName("优先级")
 *     .range((short) 1, (short) 10);
 * }</pre>
 *
 * @author coffee
 */
public class ShortConfigItem extends AbstractConfigItem<Short> {

    /**
     * 构造函数
     *
     * @param key 配置项键
     * @param required 是否必需
     */
    public ShortConfigItem(String key, boolean required) {
        super(key, required);
    }

    /**
     * 构造函数
     *
     * @param key 配置项键
     * @param required 是否必需
     * @param defaultValue 默认值
     */
    public ShortConfigItem(String key, boolean required, Short defaultValue) {
        super(key, required, defaultValue);
    }

    /**
     * 构造函数（接受 int 以方便使用）
     *
     * @param key 配置项键
     * @param required 是否必需
     * @param defaultValue 默认值
     */
    public ShortConfigItem(String key, boolean required, int defaultValue) {
        super(key, required, (short) defaultValue);
    }

    /**
     * 设置显示名称
     *
     * @param displayName 显示名称
     * @return this
     */
    @Override
    public ShortConfigItem displayName(String displayName) {
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
    public ShortConfigItem placeholder(String placeholder) {
        this.placeholder = placeholder;
        return this;
    }

    /**
     * 设置短整型范围约束
     * <p>
     * 创建并添加 {@link ShortRangeValidator}。
     *
     * @param minValue 最小值
     * @param maxValue 最大值
     * @return this
     */
    public ShortConfigItem range(short minValue, short maxValue) {
        addValidator(new ShortRangeValidator(minValue, maxValue));
        return this;
    }

    /**
     * 设置短整型范围约束（接受 int 以方便使用）
     *
     * @param minValue 最小值
     * @param maxValue 最大值
     * @return this
     */
    public ShortConfigItem range(int minValue, int maxValue) {
        return range((short) minValue, (short) maxValue);
    }

    @Override
    protected String validateType(Object value) {
        // 支持 Short, Integer, Long, BigDecimal (FastJSON2 解析 JSON 数字为 BigDecimal)
        // 也支持 String 类型（前端输入）
        if (value instanceof Short || value instanceof Integer || value instanceof Long ||
            value instanceof java.math.BigDecimal) {
            return null;
        }
        // 尝试解析 String
        if (value instanceof String) {
            try {
                Short.parseShort((String) value);
                return null;
            } catch (NumberFormatException e) {
                return displayName != null
                    ? displayName + " 必须是有效的整数"
                    : "配置项 " + key + " 必须是有效的整数";
            }
        }
        return displayName != null
            ? displayName + " 必须是整数类型"
            : "配置项 " + key + " 必须是整数类型";
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

        // 转换为 Short 进行验证
        Short typedValue;
        if (value instanceof Short) {
            typedValue = (Short) value;
        } else if (value instanceof Integer) {
            Integer intValue = (Integer) value;
            if (intValue < Short.MIN_VALUE || intValue > Short.MAX_VALUE) {
                return displayName != null
                    ? displayName + " 超出短整型范围"
                    : "配置项 " + key + " 超出短整型范围";
            }
            typedValue = intValue.shortValue();
        } else if (value instanceof Long) {
            Long longValue = (Long) value;
            if (longValue < Short.MIN_VALUE || longValue > Short.MAX_VALUE) {
                return displayName != null
                    ? displayName + " 超出短整型范围"
                    : "配置项 " + key + " 超出短整型范围";
            }
            typedValue = (short) longValue.longValue();
        } else if (value instanceof java.math.BigDecimal) {
            java.math.BigDecimal bdValue = (java.math.BigDecimal) value;
            // 检查是否为整数值
            if (bdValue.scale() > 0 && bdValue.stripTrailingZeros().scale() > 0) {
                return displayName != null
                    ? displayName + " 必须是整数类型"
                    : "配置项 " + key + " 必须是整数类型";
            }
            if (bdValue.compareTo(java.math.BigDecimal.valueOf(Short.MIN_VALUE)) < 0 ||
                bdValue.compareTo(java.math.BigDecimal.valueOf(Short.MAX_VALUE)) > 0) {
                return displayName != null
                    ? displayName + " 超出短整型范围"
                    : "配置项 " + key + " 超出短整型范围";
            }
            typedValue = bdValue.shortValue();
        } else if (value instanceof String) {
            try {
                typedValue = Short.parseShort((String) value);
            } catch (NumberFormatException e) {
                return displayName != null
                    ? displayName + " 必须是有效的整数"
                    : "配置项 " + key + " 必须是有效的整数";
            }
        } else {
            return displayName != null
                ? displayName + " 必须是整数类型"
                : "配置项 " + key + " 必须是整数类型";
        }

        for (ConstraintValidator<?> validator : validators) {
            ConstraintValidator<Short> shortValidator = (ConstraintValidator<Short>) validator;
            if (!shortValidator.validate(typedValue)) {
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
    public Short getDefaultValue() {
        return defaultValue;
    }

    @Override
    public String getFieldType() {
        return "integer";
    }
}
