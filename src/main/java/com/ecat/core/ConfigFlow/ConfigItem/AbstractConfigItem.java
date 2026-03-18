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
import java.util.Map;

/**
 * 配置项抽象基类
 * <p>
 * 为所有配置项类型提供通用功能，复用 DynamicConfig 的 ConstraintValidator 进行验证。
 * <p>
 * 子类需要实现：
 * <ul>
 *   <li>{@link #validateType(Object)} - 类型特定的验证逻辑</li>
 *   <li>{@link #addDefaultValue(Map)} - 添加默认值</li>
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
    @SuppressWarnings("unchecked")
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

    // ========== 验证方法 ==========

    /**
     * 验证配置项的值
     * <p>
     * 首先检查是否为空，然后调用子类的类型验证，最后执行所有验证器。
     *
     * @param value 待验证的值
     * @return 验证错误信息，验证通过返回 null
     */
    public String validate(Object value) {
        // 空值检查
        if (value == null) {
            if (required) {
                return displayName != null
                    ? displayName + " 是必需的"
                    : "配置项 " + key + " 是必需的";
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
     * 为配置添加默认值
     * <p>
     * 如果配置中不存在该键且有默认值，则添加默认值。
     *
     * @param config 配置映射
     */
    public abstract void addDefaultValue(Map<String, Object> config);

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
