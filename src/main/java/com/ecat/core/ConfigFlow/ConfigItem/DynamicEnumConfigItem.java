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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;

/**
 * 动态枚举配置项 - 选项运行时获取
 * <p>
 * 与 EnumConfigItem 的区别：
 * <ul>
 *   <li>EnumConfigItem: 选项编译时固定</li>
 *   <li>DynamicEnumConfigItem: 选项运行时通过 Supplier 获取</li>
 * </ul>
 *
 * @author coffee
 */
public class DynamicEnumConfigItem extends AbstractConfigItem<String> {

    private final Supplier<Map<String, String>> optionsSupplier;
    private boolean caseSensitive = true;

    /**
     * 创建动态枚举配置项
     *
     * @param key             字段名
     * @param required        是否必填
     * @param optionsSupplier 选项提供者（运行时调用获取选项）
     */
    public DynamicEnumConfigItem(String key, boolean required,
                                  Supplier<Map<String, String>> optionsSupplier) {
        super(key, required);
        this.optionsSupplier = optionsSupplier;
    }

    /**
     * 创建动态枚举配置项（带默认值）
     *
     * @param key             字段名
     * @param required        是否必填
     * @param defaultValue    默认值
     * @param optionsSupplier 选项提供者（运行时调用获取选项）
     */
    public DynamicEnumConfigItem(String key, boolean required, String defaultValue,
                                  Supplier<Map<String, String>> optionsSupplier) {
        super(key, required, defaultValue);
        this.optionsSupplier = optionsSupplier;
    }

    /**
     * 设置是否区分大小写（默认区分）
     *
     * @param caseSensitive 是否区分大小写
     * @return this
     */
    public DynamicEnumConfigItem caseSensitive(boolean caseSensitive) {
        this.caseSensitive = caseSensitive;
        return this;
    }

    @Override
    public String getFieldType() {
        return "dynamic_enum";
    }

    /**
     * 获取选项（运行时调用）
     *
     * @return 选项映射
     */
    public Map<String, String> getOptions() {
        Map<String, String> options = optionsSupplier.get();
        return options != null ? options : new LinkedHashMap<>();
    }

    @Override
    protected String validateType(Object value) {
        if (value == null) {
            return null;
        }

        String strValue = value.toString();

        // 空字符串表示未选择，对于必填字段应该报错
        if (strValue.isEmpty()) {
            if (required) {
                return displayName != null
                        ? displayName + " 是必需的"
                        : "配置项 " + key + " 是必需的";
            }
            return null;
        }

        Map<String, String> options = getOptions();

        if (caseSensitive) {
            if (!options.containsKey(strValue)) {
                return "无效选项: " + strValue;
            }
        } else {
            boolean found = options.keySet().stream()
                    .anyMatch(k -> k.equalsIgnoreCase(strValue));
            if (!found) {
                return "无效选项: " + strValue;
            }
        }
        return null;
    }

    @Override
    public Object validate(Object value) {
        // 必填验证：空字符串也视为未填写
        if (value == null || (value instanceof String && ((String) value).isEmpty())) {
            if (required) {
                return displayName != null
                    ? displayName + " 是必需的"
                    : "配置项 " + key + " 是必需的";
            }
            return null;
        }

        // 类型验证
        String typeError = validateType(value);
        if (typeError != null) {
            return typeError;
        }

        // 执行额外验证器（继承自父类）
        for (ConstraintValidator<?> validator : getValidators()) {
            @SuppressWarnings("unchecked")
            ConstraintValidator<Object> objectValidator = (ConstraintValidator<Object>) validator;
            if (!objectValidator.validate(value)) {
                return displayName != null
                    ? displayName + ": " + validator.getErrorMessage()
                    : "配置项 " + key + ": " + validator.getErrorMessage();
            }
        }

        return null;
    }

    @Override
    public void addDefaultValue(Map<String, Object> config) {
        if (defaultValue != null && !config.containsKey(key)) {
            config.put(key, defaultValue);
        }
    }

    @Override
    public String getDefaultValue() {
        return defaultValue;
    }
}
