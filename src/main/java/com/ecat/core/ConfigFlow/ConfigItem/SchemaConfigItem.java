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

import com.ecat.core.ConfigFlow.ConfigSchema;
import com.ecat.core.ConfigFlow.ConfigSchemaProvider;

import java.util.Map;

/**
 * Schema 字段 - 统一处理嵌套和引用
 * <p>
 * 支持两种模式：
 * <ul>
 *   <li>嵌套模式：直接定义嵌套的 Schema</li>
 *   <li>引用模式：通过 Provider 类引用外部 Schema</li>
 * </ul>
 * <p>
 * 扩展模式：将嵌套字段提升到父级，不产生嵌套对象。
 *
 * <h3>嵌套字段校验错误契约（errors Map 层级结构）</h3>
 * <p>errors 的 key 永远是 {@code ConfigItem.getKey()}（字段名本身，<b>不带父前缀</b>）；值分两种：
 * <ul>
 *   <li>普通字段（叶子）：{@code String} 错误消息</li>
 *   <li>嵌套字段（本类 SchemaConfigItem）：{@code Map<String,Object>}，递归同结构（其子字段错误）</li>
 * </ul>
 *
 * <p><b>一层嵌套示例</b>（serial_settings 嵌套子 schema，serial_port 重复 + 同级 station_id 必填）：
 * <pre>{@code
 * errors = {
 *   "serial_settings": {                              // SchemaConfigItem → 嵌套 Map
 *     "serial_port": "该地址对应的设备已存在，请修改地址"  // 叶子子字段 → String
 *   },
 *   "station_id": "站点号必填"                          // 同级顶层普通字段 → String
 * }
 * }</pre>
 *
 * <p><b>两层嵌套示例</b>（schema 套 schema，address → location → city）：
 * <pre>{@code
 * errors = {
 *   "address": {                // 第一层 SchemaConfigItem → 嵌套 Map
 *     "location": {             // 第二层 SchemaConfigItem（子 schema 里又套 schema）→ 再嵌套 Map
 *       "city": "城市不合法"     // 叶子 → String
 *     }
 *   }
 * }
 * }</pre>
 *
 * <p><b>禁止·扁平</b>（典型错误，前端按父字段名取不到）：
 * <pre>{@code
 * errors = { "serial_port": "..." }   // ✗ serial_port 在 serial_settings 嵌套内，应 errors["serial_settings"]["serial_port"]
 * }</pre>
 * 前端 flow-form {@code error = errors[field.key]}：对 serial_settings 取 {@code errors["serial_settings"]}
 * 得到 undefined（扁平 key 挂在 serial_port，父字段名取不到）→ SchemaFieldRenderer 拿不到错误 → 不显示。
 *
 * <p><b>前端契约</b>（lib flow-form renderField + schema-field SchemaFieldRenderer，SPA/ADM 共享）：
 * schema 类型字段的 error 必须是嵌套 Map，SchemaFieldRenderer 按 {@code nestedField.key} 从嵌套 Map
 * 取子字段错误，<b>递归渲染（天然支持多层）</b>。故后端任何手动 errors.put 嵌套子字段错误，必须按本契约
 * 挂嵌套 Map，禁止扁平。{@link #validate} 的自动校验已遵循（递归 schema.validate 返回嵌套 Map）。
 *
 * @author coffee
 */
public class SchemaConfigItem extends AbstractConfigItem<Map<String, Object>> {

    /** 直接定义的嵌套 Schema */
    private ConfigSchema nestedSchema;

    /** Schema 提供者类（用于引用外部 Schema） */
    private Class<? extends ConfigSchemaProvider> schemaProvider;

    /** 是否扩展模式（字段提升到父级） */
    private boolean extend = false;

    /**
     * 创建嵌套 Schema（直接定义 / Builder 定制）
     * <p>
     * 直接传入 Schema 实例。Schema 携带 sourceProvider 信息，翻译自动从 Provider 所属集成查找。
     * <p>
     * 推荐场景：需要自定义默认值——通过 Provider 的 Builder 构建定制实例。
     * <pre>
     * // 自定义默认值 + 自动 i18n
     * new SchemaConfigItem("comm_settings", true,
     *     SerialCommConfigSchema.builder()
     *         .baudrate(BaudRate.BAUD_115200)
     *         .parity(Parity.ODD)
     *         .timeout(1000)
     *         .build()
     *         .createSchema())
     *     .displayName("通讯配置")
     * </pre>
     *
     * @param key      字段名
     * @param required 是否必填
     *                 - true: 该嵌套对象字段本身必须存在（不能为 null 或缺失）
     *                 - false: 该嵌套对象字段可选
     *                 注意：嵌套对象内部的子字段有独立的 required 定义，递归验证
     * @param schema   嵌套的 Schema 定义
     */
    public SchemaConfigItem(String key, boolean required, ConfigSchema schema) {
        super(key, required);
        this.nestedSchema = schema;
    }

    /**
     * 创建引用 Schema（通过 Provider 类）
     * <p>
     * 运行时通过 Provider 类反射创建实例，使用标准默认值。
     * Schema 翻译自动从 Provider 所属集成的 strings.json 中查找。
     * <p>
     * 推荐场景：不需要自定义默认值的标准复用场景。
     * <pre>
     * // 标准默认值 + 自动 i18n
     * new SchemaConfigItem("comm_settings", true, SerialCommConfigSchema.class)
     *     .displayName("通讯配置")
     * </pre>
     *
     * @param key           字段名
     * @param required      是否必填（同上）
     * @param providerClass Schema 提供者类，通过 createSchema() 获取定义
     */
    public SchemaConfigItem(String key, boolean required,
                            Class<? extends ConfigSchemaProvider> providerClass) {
        super(key, required);
        this.schemaProvider = providerClass;
    }

    /**
     * 扩展模式：将嵌套字段提升到父级
     * <p>
     * 扩展模式下，嵌套字段直接出现在父级 JSON 中，不产生嵌套对象。
     *
     * @return this，支持链式调用
     */
    public SchemaConfigItem extend() {
        this.extend = true;
        return this;
    }

    /**
     * 是否扩展模式
     *
     * @return true 表示扩展模式
     */
    public boolean isExtend() {
        return extend;
    }

    /**
     * 解析 Schema（延迟解析）
     *
     * @return Schema 实例
     */
    public ConfigSchema resolveSchema() {
        if (nestedSchema != null) {
            return nestedSchema;
        }
        if (schemaProvider != null) {
            try {
                return schemaProvider.getDeclaredConstructor().newInstance().createSchema();
            } catch (Exception e) {
                throw new RuntimeException("Failed to create schema: " + schemaProvider, e);
            }
        }
        return null;
    }

    @Override
    public String getFieldType() {
        return "schema";
    }

    @Override
    protected String validateType(Object value) {
        // Map 类型检查
        if (value != null && !(value instanceof Map)) {
            return displayName != null
                ? displayName + " 必须是对象类型"
                : "配置项 " + key + " 必须是对象类型";
        }
        return null;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Object validate(Object value) {
        // 空值检查和 required 验证委托给父类
        Object baseError = super.validate(value);
        if (baseError != null) {
            return baseError;  // String 类型：自身 required 或类型错误
        }

        ConfigSchema schema = resolveSchema();
        if (schema == null) {
            return null;
        }

        // value 为 null 时，required 已在父类检查中处理
        if (value == null) {
            return null;
        }

        Map<String, Object> mapValue = (Map<String, Object>) value;

        // 递归验证嵌套字段，返回嵌套 Map 结构（支持无限级嵌套）
        Map<String, Object> errors = schema.validate(mapValue);
        if (!errors.isEmpty()) {
            return errors;  // Map 类型：子字段错误，保持嵌套结构
        }
        return null;
    }

    @Override
    public Map<String, Object> getDefaultValue() {
        return defaultValue;
    }
}
