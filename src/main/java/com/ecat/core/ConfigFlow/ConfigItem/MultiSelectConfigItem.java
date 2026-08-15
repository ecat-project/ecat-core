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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 受限多选配置项：值是 {@code List<String>}，每个元素必须落在预定义 options 值域内。
 * <p>
 * 与 {@link ArrayConfigItem} 的区别：本类专用于前端 checkbox 组渲染（fieldType=multi_select），
 * 典型应用场景是表格行内受限多选（如 HJ212 推送因子表里每个因子选择推送粒度：
 * 实时/分钟/小时可多选），选项集合小且固定，勾选框比 ArrayConfigItem 的泛型数组更贴合 cell 内交互。
 * <p>
 * required 语义 = 至少选一项：空列表在 required 时报错，非 required 时空集与 null 均合法。
 * <p>
 * 示例：
 * <pre>{@code
 * MultiSelectConfigItem granularity = new MultiSelectConfigItem("granularities", true)
 *     .displayName("推送粒度")
 *     .addOption("REALTIME", "实时")
 *     .addOption("MINUTE", "分钟")
 *     .addOption("HOUR", "小时");
 * }</pre>
 * 
 * @author coffee
 */
public class MultiSelectConfigItem extends AbstractConfigItem<List<String>> {

    private final Set<String> validValues = new LinkedHashSet<>();
    private final Map<String, String> optionLabels = new LinkedHashMap<>();

    /**
     * 构造函数
     *
     * @param key      配置项键
     * @param required 是否必需（语义=至少选一项）
     */
    public MultiSelectConfigItem(String key, boolean required) {
        super(key, required);
    }

    /**
     * 构造函数
     *
     * @param key           配置项键
     * @param required      是否必需（语义=至少选一项）
     * @param defaultValue  默认值（预勾选的选项值列表）
     */
    public MultiSelectConfigItem(String key, boolean required, List<String> defaultValue) {
        super(key, required, defaultValue);
    }

    /**
     * 设置显示名称
     *
     * @param displayName 显示名称
     * @return this
     */
    @Override
    public MultiSelectConfigItem displayName(String displayName) {
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
    public MultiSelectConfigItem placeholder(String placeholder) {
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
    public MultiSelectConfigItem addOption(String value, String label) {
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
    public MultiSelectConfigItem addOption(String value) {
        return addOption(value, value);
    }

    /**
     * 批量添加选项
     *
     * @param options 选项映射 (value -> label)
     * @return this
     */
    public MultiSelectConfigItem addOptions(Map<String, String> options) {
        if (options != null) {
            for (Map.Entry<String, String> entry : options.entrySet()) {
                addOption(entry.getKey(), entry.getValue());
            }
        }
        return this;
    }

    /**
     * 设置默认值（预勾选的选项值列表）
     *
     * @param defaultValue 默认值列表
     * @return this
     */
    public MultiSelectConfigItem defaultValue(List<String> defaultValue) {
        this.defaultValue = defaultValue != null ? new ArrayList<>(defaultValue) : null;
        return this;
    }

    /**
     * 获取有效值集合
     *
     * @return 有效值集合（不可变视图的拷贝）
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
     * @return 显示标签，无映射时返回值本身
     */
    public String getOptionLabel(String value) {
        return optionLabels.getOrDefault(value, value);
    }

    @Override
    protected String validateType(Object value) {
        if (!(value instanceof List)) {
            return displayName != null
                    ? displayName + " 必须是列表(multi_select)"
                    : "配置项 " + key + " 必须是列表(multi_select)";
        }
        return null;
    }

    /**
     * 校验：必须 List、每项必须是文本且落在 options 值域内；required 时空集报错（至少选一项）。
     */
    @Override
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

        List<?> list = (List<?>) value;

        // required 语义=至少选一项：空列表等价于"没填"，与 null 同罪；非 required 空集合法（用户可全不选）。
        if (required && list.isEmpty()) {
            return displayName != null
                    ? displayName + " 至少选择一项"
                    : "配置项 " + key + " 至少选择一项";
        }

        for (Object item : list) {
            if (!(item instanceof String)) {
                return displayName != null
                        ? displayName + " 的每个选项必须是文本"
                        : "配置项 " + key + " 的每个选项必须是文本";
            }
            if (!validValues.contains(item)) {
                return displayName != null
                        ? displayName + ": 请从有效选项中选择 (" + getDisplayValuesString() + ")"
                        : "配置项 " + key + ": 请从有效选项中选择 (" + getDisplayValuesString() + ")";
            }
        }
        return null;
    }

    /**
     * 获取有效值的显示字符串（用于错误消息，显示标签而非内部值）
     */
    private String getDisplayValuesString() {
        if (optionLabels.isEmpty()) {
            return validValues.toString();
        }
        List<String> labels = new ArrayList<>();
        for (String value : validValues) {
            labels.add(optionLabels.getOrDefault(value, value));
        }
        return labels.toString();
    }

    @Override
    public List<String> getDefaultValue() {
        return defaultValue != null ? new ArrayList<>(defaultValue) : null;
    }

    @Override
    public String getFieldType() {
        return "multi_select";
    }
}
