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

import com.ecat.core.Utils.DynamicConfig.StringEnumValidator;

import java.util.*;

/**
 * 枚举/下拉选择配置项
 * <p>
 * 用于定义枚举类型的配置字段，使用 {@link StringEnumValidator} 进行枚举值验证。
 * <p>
 * 示例：
 * <pre>{@code
 * EnumConfigItem protocol = new EnumConfigItem("protocol", true)
 *     .displayName("协议类型")
 *     .addOption("TCP", "TCP - 网络协议")
 *     .addOption("UDP", "UDP - 网络协议")
 *     .defaultValue("TCP")
 *     .buildValidator();
 * }</pre>
 *
 * @author ECAT Core
 */
public class EnumConfigItem extends AbstractConfigItem<String> {

    private final Set<String> validValues = new LinkedHashSet<>();
    private final Map<String, String> optionLabels = new LinkedHashMap<>();

    /**
     * 构造函数
     *
     * @param key 配置项键
     * @param required 是否必需
     */
    public EnumConfigItem(String key, boolean required) {
        super(key, required);
    }

    /**
     * 构造函数
     *
     * @param key 配置项键
     * @param required 是否必需
     * @param defaultValue 默认值
     */
    public EnumConfigItem(String key, boolean required, String defaultValue) {
        super(key, required, defaultValue);
    }

    /**
     * 设置显示名称
     *
     * @param displayName 显示名称
     * @return this
     */
    @Override
    public EnumConfigItem displayName(String displayName) {
        this.displayName = displayName;
        return this;
    }

    /**
     * 添加选项
     *
     * @param value 选项值
     * @param label 显示标签
     * @return this
     */
    public EnumConfigItem addOption(String value, String label) {
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
    public EnumConfigItem addOption(String value) {
        return addOption(value, value);
    }

    /**
     * 设置默认值
     *
     * @param defaultValue 默认值
     * @return this
     */
    public EnumConfigItem defaultValue(String defaultValue) {
        this.defaultValue = defaultValue;
        return this;
    }

    /**
     * 构建并添加枚举验证器
     * <p>
     * 调用此方法后，配置项将使用 {@link StringEnumValidator} 进行验证。
     *
     * @return this
     */
    public EnumConfigItem buildValidator() {
        if (!validValues.isEmpty()) {
            addValidator(new StringEnumValidator(validValues));
        }
        return this;
    }

    /**
     * 获取有效值集合
     *
     * @return 有效值集合
     */
    public Set<String> getValidValues() {
        return new LinkedHashSet<>(validValues);
    }

    /**
     * 获取选项标签映射
     *
     * @return 选项标签映射
     */
    public Map<String, String> getOptionLabels() {
        return new LinkedHashMap<>(optionLabels);
    }

    /**
     * 获取选项的显示标签
     *
     * @param value 选项值
     * @return 显示标签
     */
    public String getOptionLabel(String value) {
        return optionLabels.getOrDefault(value, value);
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
        return "select";
    }
}
