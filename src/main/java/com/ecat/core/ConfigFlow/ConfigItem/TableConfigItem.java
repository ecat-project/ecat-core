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

import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 变长结构化行表配置项：用户可增删 N 行，每行按 {@code rowSchema}（一个 {@link ConfigSchema}）校验。
 * <p>
 * 用途：寄存器映射表等"用户维护的变长结构化行列表"。
 * <p>
 * {@code validate} 返回 {@code Map<String,Map<String,Object>>}（行号字符串 → 该行字段错误），{@code null} 表示全通过；
 * 与 {@link ConfigSchema#validate(Map)} 的 String|Map 树形错误结构一致，便于前端按行/字段定位错误。
 * <p>
 * 行内字段若需"按某列值变化"（两级级联，如单位列随单位类别列过滤），由前端 table-field 渲染器按同行
 * 被依赖列的值过滤该列 options 实现；本类只负责结构化行级校验。
 */
@Getter
public class TableConfigItem extends AbstractConfigItem<List<Map<String, Object>>> {

    /** 行子 schema：每行按此校验。 */
    private final ConfigSchema rowSchema;

    /** 可选：最少行数约束。 */
    private Integer minRows;

    /** 可选：最多行数约束。 */
    private Integer maxRows;

    public TableConfigItem(String key, boolean required, ConfigSchema rowSchema) {
        super(key, required);
        this.rowSchema = Objects.requireNonNull(rowSchema, "rowSchema 不能为空");
    }

    public TableConfigItem(String key, boolean required, ConfigSchema rowSchema,
                           List<Map<String, Object>> defaultValue) {
        super(key, required, defaultValue);
        this.rowSchema = Objects.requireNonNull(rowSchema, "rowSchema 不能为空");
    }

    public TableConfigItem minRows(int n) { this.minRows = n; return this; }
    public TableConfigItem maxRows(int n) { this.maxRows = n; return this; }

    @Override
    public String getFieldType() { return "table"; }

    @Override
    protected String validateType(Object value) {
        if (value == null) {
            return null;                    // required 由父类 validate 兜底
        }
        if (!(value instanceof List)) {
            return "必须是列表(table)";
        }
        int i = 0;
        for (Object row : (List<?>) value) {
            if (!(row instanceof Map)) {
                return "第 " + i + " 行必须是对象";
            }
            i++;
        }
        return null;
    }

    /**
     * 镜像 {@code SchemaConfigItem.validate}：先走父类（空/required/类型），再逐行 {@code rowSchema.validate}。
     * 返回 null（全通过）/ String（表级错误：required、类型、行数）/ Map&lt;Integer,Map&gt;（行级字段错误）。
     */
    @SuppressWarnings("unchecked")
    @Override
    public Object validate(Object value) {
        Object baseError = super.validate(value);
        if (baseError != null) {
            return baseError;               // 父类报错（空/required/类型）直接返回
        }
        if (value == null) {
            return null;
        }
        List<?> rows = (List<?>) value;
        if (minRows != null && rows.size() < minRows) {
            return "至少需要 " + minRows + " 行";
        }
        if (maxRows != null && rows.size() > maxRows) {
            return "最多 " + maxRows + " 行";
        }

        // 行号键用 String（非 Integer）：fastjson 序列化 Integer 键不加引号 → 畸形 JSON {0:...}，
        // 前端无法解析行级错误。String 键 {"0":...} 合法；前端 table-field 读 error[idx]（JS 键本即字符串）兼容。
        Map<String, Map<String, Object>> rowErrors = new LinkedHashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> perRow = rowSchema.validate((Map<String, Object>) rows.get(i));
            if (perRow != null && !perRow.isEmpty()) {
                rowErrors.put(String.valueOf(i), perRow);
            }
        }
        return rowErrors.isEmpty() ? null : rowErrors;
    }

    @Override
    public List<Map<String, Object>> getDefaultValue() {
        return defaultValue;
    }
}
