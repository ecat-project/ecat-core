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

package com.ecat.core.ConfigFlow;

import com.ecat.core.ConfigFlow.ConfigItem.AbstractConfigItem;
import com.ecat.core.I18n.I18nHelper;
import com.ecat.core.I18n.I18nProxy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 配置 Schema - 字段定义集合
 * <p>
 * 所有字段（包括嵌套和引用）统一通过 {@link #addField(AbstractConfigItem)} 添加。
 * 嵌套和引用 Schema 通过 {@link com.ecat.core.ConfigFlow.ConfigItem.SchemaConfigItem} 实现（也是一种字段类型）。
 * <p>
 * i18n 支持：通过 {@link #initI18n(ConfigSchemaProvider)} 初始化后，
 * 可调用 {@link #resolveDisplayName(String)}、{@link #resolveOptionLabel(String, String)}
 * 和 {@link #resolveDescription(String)} 从 schema 定义方的 namespace 查找翻译。
 *
 * @author coffee
 */
public class ConfigSchema {

    /** 字段列表（唯一数据结构） */
    private final List<AbstractConfigItem<?>> fields = new ArrayList<>();

    /** schema 定义方的 I18nProxy（内部状态，不对外暴露） */
    private I18nProxy i18nProxy;

    /** schema 的 i18n key 前缀，如 "config_schemas.serial_comm" */
    private String i18nKeyPrefix;

    // ========== 添加字段 ==========

    /**
     * 添加字段
     *
     * @param field 字段定义
     * @return this，支持链式调用
     */
    public ConfigSchema addField(AbstractConfigItem<?> field) {
        this.fields.add(field);
        return this;
    }

    // ========== i18n 初始化与解析 ==========

    /**
     * 初始化 i18n — 由 ConfigSchemaProvider.createSchema() 末尾调用。
     * <p>
     * 根据 provider 的 {@link ConfigSchemaProvider#getI18nKeyPrefix()} 和 coordinate 信息，
     * 创建 I18nProxy，后续 resolveXxx() 方法即可使用。
     * <p>
     * coordinate 来源优先级：
     * <ol>
     *   <li>{@link ConfigSchemaProvider#getCoordinate()} 非 null → 使用显式值</li>
     *   <li>自动检测 → {@link I18nHelper#createProxy(Class)} 从 JAR MANIFEST 解析</li>
     * </ol>
     * <p>
     * 如果 provider.getI18nKeyPrefix() 返回 null，则跳过初始化（schema 不提供 i18n）。
     *
     * @param provider 创建此 schema 的 Provider 实例
     */
    public void initI18n(ConfigSchemaProvider provider) {
        String prefix = provider.getI18nKeyPrefix();
        if (prefix == null) return;
        this.i18nKeyPrefix = prefix;

        String coordinate = provider.getCoordinate();
        if (coordinate != null) {
            // 显式 coordinate（测试场景或特殊部署场景）
            this.i18nProxy = I18nHelper.createProxy(coordinate, provider.getClass());
        } else {
            // 自动检测（生产场景，从 JAR MANIFEST 解析）
            this.i18nProxy = I18nHelper.createProxy(provider.getClass());
        }
    }

    /**
     * 从 schema 定义方 namespace 解析 displayName
     *
     * @param fieldKey 字段名
     * @return 翻译文本，未找到返回 null
     */
    public String resolveDisplayName(String fieldKey) {
        return resolveKey(fieldKey + ".display_name");
    }

    /**
     * 从 schema 定义方 namespace 解析 option label
     *
     * @param fieldKey    字段名
     * @param optionValue 选项值
     * @return 翻译文本，未找到返回 null
     */
    public String resolveOptionLabel(String fieldKey, String optionValue) {
        return resolveKey(fieldKey + ".options." + optionValue);
    }

    /**
     * 从 schema 定义方 namespace 解析 description
     *
     * @param fieldKey 字段名
     * @return 翻译文本，未找到返回 null
     */
    public String resolveDescription(String fieldKey) {
        return resolveKey(fieldKey + ".description");
    }

    /**
     * 内部 key 解析
     *
     * @param suffix 如 "baudrate.display_name" 或 "parity.options.Odd"
     * @return 翻译文本，未找到返回 null
     */
    private String resolveKey(String suffix) {
        if (i18nProxy == null || i18nKeyPrefix == null) return null;
        String key = i18nKeyPrefix + "." + suffix;
        String result = i18nProxy.t(key);
        return result.equals(key) ? null : result;
    }

    // ========== 获取字段 ==========

    /**
     * 获取所有字段（不可修改）
     *
     * @return 字段列表
     */
    public List<AbstractConfigItem<?>> getFields() {
        return Collections.unmodifiableList(fields);
    }

    // ========== 验证 ==========

    /**
     * 验证输入数据
     * <p>
     * 返回的 Map 中，叶子字段的值为 String，嵌套 Schema 字段的值为 Map（递归结构）。
     * 支持无限级嵌套。
     *
     * @param input 输入数据
     * @return 错误映射，key 为字段名，value 为 String（叶子错误）或 Map（嵌套错误）；无错误返回空 Map
     */
    public Map<String, Object> validate(Map<String, Object> input) {
        Map<String, Object> errors = new HashMap<>();

        for (AbstractConfigItem<?> field : fields) {
            Object validationResult = field.validate(input.get(field.getKey()));
            if (validationResult != null) {
                errors.put(field.getKey(), validationResult);
            }
        }

        return errors;
    }

}
