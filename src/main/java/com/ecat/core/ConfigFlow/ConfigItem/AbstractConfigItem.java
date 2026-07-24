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

import java.util.ArrayList;
import java.util.List;

/**
 * 配置项抽象基类
 * <p>
 * 为所有配置项类型提供通用功能，复用 DynamicConfig 的 ConstraintValidator 进行验证。
 * <p>
 * 子类需要实现：
 * <ul>
 *   <li>{@link #validateType(Object)} - 类型特定的验证逻辑</li>
 *   <li>{@link #getDefaultValue()} - 获取默认值</li>
 *   <li>{@link #getFieldType()} - 获取字段类型（用于前端渲染）</li>
 * </ul>
 *
 * @param <T> 配置项值的类型
 * @author coffee
 */
public abstract class AbstractConfigItem<T> {

    protected final String key;
    protected String displayName;
    protected String description;
    protected String placeholder;
    protected boolean required;
    protected boolean readOnly;
    /** 该列选项依赖同行另一列（两级级联，如单位列随单位类别列过滤）；值=被依赖列的 key。null=不级联。 */
    protected String dependsOn;
    /** 该列渲染位置:"main"(主行,始终显)或 "detail"(详情区,行展开显);null=main(向后兼容扁平表,列全显无展开)。 */
    protected String displayGroup;
    /** 详情列渐进披露条件:"field=v1|v2"(同行 field 值在值集时显本列);null=无条件显。仅 detail 列生效。 */
    protected String showWhen;
    /** 详情列分组小标题(如 "转换参数");前端按 group 在详情区分组渲染。null=不分组。 */
    protected String group;
    protected final List<ConstraintValidator<?>> validators = new ArrayList<>();
    protected T defaultValue;

    /**
     * 构造函数
     *
     * @param key 配置项键
     * @param required 是否必需
     */
    protected AbstractConfigItem(String key, boolean required) {
        this.key = key;
        this.required = required;
    }

    /**
     * 构造函数
     *
     * @param key 配置项键
     * @param required 是否必需
     * @param defaultValue 默认值
     */
    protected AbstractConfigItem(String key, boolean required, T defaultValue) {
        this.key = key;
        this.required = required;
        this.defaultValue = defaultValue;
    }

    // ========== 链式设置方法 ==========

    /**
     * 设置显示名称
     *
     * @param displayName 显示名称
     * @return this
     */
    public AbstractConfigItem<T> displayName(String displayName) {
        this.displayName = displayName;
        return this;
    }

    /**
     * 设置描述
     *
     * @param description 描述
     * @return this
     */
    public AbstractConfigItem<T> description(String description) {
        this.description = description;
        return this;
    }

    /**
     * 设置占位符
     *
     * @param placeholder 占位符
     * @return this
     */
    public AbstractConfigItem<T> placeholder(String placeholder) {
        this.placeholder = placeholder;
        return this;
    }

    /**
     * 设置是否必需
     *
     * @param required 是否必需
     * @return this
     */
    public AbstractConfigItem<T> required(boolean required) {
        this.required = required;
        return this;
    }

    /**
     * 添加验证器
     *
     * @param validator 验证器
     * @return this
     */
    public AbstractConfigItem<T> addValidator(ConstraintValidator<? super T> validator) {
        this.validators.add(validator);
        return this;
    }

    /**
     * 设置默认值
     *
     * @param defaultValue 默认值
     * @return this
     */
    public AbstractConfigItem<T> setDefaultValue(T defaultValue) {
        this.defaultValue = defaultValue;
        return this;
    }

    /**
     * 设置是否只读
     *
     * @param readOnly 是否只读
     * @return this
     */
    public AbstractConfigItem<T> readOnly(boolean readOnly) {
        this.readOnly = readOnly;
        return this;
    }

    /**
     * 声明该列选项依赖同行另一列（两级级联）：前端 table-field 渲染器按同行被依赖列的值前缀过滤本列 options。
     * 典型用途：单位列 dependsOn 单位类别列（选 TemperatureUnit → 单位列只显示 TemperatureUnit.*）。
     *
     * @param dependsOn 被依赖列的 key；null 清除级联
     * @return this
     */
    public AbstractConfigItem<T> dependsOn(String dependsOn) {
        this.dependsOn = dependsOn;
        return this;
    }

    public String getDependsOn() {
        return dependsOn;
    }

    /** 声明该列渲染位置:"main"(主行,始终显)/ "detail"(详情区,行展开显)。 */
    public AbstractConfigItem<T> displayGroup(String displayGroup) { this.displayGroup = displayGroup; return this; }
    public String getDisplayGroup() { return displayGroup; }

    /** 声明详情列渐进披露条件 "field=v1|v2"(同行 field 命中值集时显本列)。 */
    public AbstractConfigItem<T> showWhen(String showWhen) { this.showWhen = showWhen; return this; }
    public String getShowWhen() { return showWhen; }

    /** 声明详情列分组小标题(如 "转换参数"),前端按 group 在详情区分组渲染。 */
    public AbstractConfigItem<T> group(String group) { this.group = group; return this; }
    public String getGroup() { return group; }

    // ========== 验证方法 ==========

    /**
     * 验证配置项的值
     * <p>
     * 默认实现返回 String（叶子字段错误），子类可覆写返回 Map（嵌套 Schema 错误）。
     * 支持无限级嵌套。
     *
     * @param value 待验证的值
     * @return 验证错误信息（String）或嵌套错误（Map），验证通过返回 null
     */
    public Object validate(Object value) {
        // 空值检查：null 或（required 时）空白字符串都视为缺失。
        // 空串也判：堵住 API/脚本 POST 空 sn 等绕过前端 HTML5 required 的口子（bug-record-20260724-084832）。
        if (value == null || (required && isBlankString(value))) {
            if (required) {
                return requiredError();
            }
            return null;
        }

        // 类型检查
        String typeError = validateType(value);
        if (typeError != null) {
            return typeError;
        }

        // 执行验证器
        for (ConstraintValidator<?> validator : validators) {
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

    /** required 缺失（null 或空白串）的错误信息。 */
    private Object requiredError() {
        return displayName != null
            ? displayName + " 是必需的"
            : "配置项 " + key + " 是必需的";
    }

    /** 仅对 String 判空白（trim 后为空）；非 String（数值/布尔/集合）返回 false——其“缺失”只有 null。 */
    private static boolean isBlankString(Object value) {
        return value instanceof String && ((String) value).trim().isEmpty();
    }

    /**
     * 类型特定的验证逻辑
     * <p>
     * 子类实现此方法以检查值的类型是否正确。
     *
     * @param value 待验证的值
     * @return 类型错误信息，类型正确返回 null
     */
    protected abstract String validateType(Object value);

    /**
     * 获取默认值
     *
     * @return 默认值
     */
    public abstract T getDefaultValue();

    /**
     * 获取字段类型
     * <p>
     * 返回前端渲染所需的类型标识。
     *
     * @return 字段类型字符串
     */
    public abstract String getFieldType();

    // ========== Getter 方法 ==========

    public String getKey() {
        return key;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public String getPlaceholder() {
        return placeholder;
    }

    public boolean isRequired() {
        return required;
    }

    public List<ConstraintValidator<?>> getValidators() {
        return new ArrayList<>(validators);
    }

    public boolean isReadOnly() {
        return readOnly;
    }
}
