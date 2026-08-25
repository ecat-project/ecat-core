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

import java.text.ParsePosition;
import java.text.SimpleDateFormat;

/**
 * 日期时间配置项：值为文本，格式由 {@link DateTimePrecision} 精度决定，
 * 前端渲染为对应的原生时间选择器（fieldType=datetime，config-flow lib 的
 * datetime 渲染器按 schema 的 precision 字段选控件与值形态）。
 * <p>
 * 精度四选一（详见 {@link DateTimePrecision}）：
 * <ul>
 *   <li>{@code DATE} —— yyyy-MM-dd，日期选择器；</li>
 *   <li>{@code DATETIME_MINUTE} —— yyyy-MM-dd HH:mm，日期时间选择器；</li>
 *   <li>{@code DATETIME_SECOND} —— yyyy-MM-dd HH:mm:ss，日期时间选择器（到秒），<b>默认</b>；</li>
 *   <li>{@code TIME} —— HH:mm:ss，时间选择器。</li>
 * </ul>
 * 校验严格（非宽松解析）：格式错误/非法日期（如 2 月 30 日）均报错。
 * <p>
 * 示例：
 * <pre>{@code
 * // 仅日期
 * new DateTimeConfigItem("start_date", true).precision(DateTimePrecision.DATE);
 * // 到秒（默认，可省略 precision 调用）
 * new DateTimeConfigItem("start_time", true).displayName("回补开始时间");
 * }</pre>
 *
 * @author dreamfalls
 */
public class DateTimeConfigItem extends AbstractConfigItem<String> {

    /** 默认精度对应值格式（向后兼容常量） */
    public static final String FORMAT = DateTimePrecision.DATETIME_SECOND.getPattern();

    /** 字段精度（决定值格式/校验/前端控件），默认到秒 */
    private DateTimePrecision precision = DateTimePrecision.DATETIME_SECOND;

    /**
     * 构造函数
     *
     * @param key      配置项键
     * @param required 是否必需
     */
    public DateTimeConfigItem(String key, boolean required) {
        super(key, required);
    }

    /**
     * 构造函数
     *
     * @param key          配置项键
     * @param required     是否必需
     * @param defaultValue 默认值（须符合所选精度格式）
     */
    public DateTimeConfigItem(String key, boolean required, String defaultValue) {
        super(key, required, defaultValue);
    }

    /**
     * 设置显示名称
     *
     * @param displayName 显示名称
     * @return this
     */
    @Override
    public DateTimeConfigItem displayName(String displayName) {
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
    public DateTimeConfigItem placeholder(String placeholder) {
        this.placeholder = placeholder;
        return this;
    }

    /**
     * 设置字段精度（值格式 + 前端控件形态），默认 {@link DateTimePrecision#DATETIME_SECOND}。
     *
     * @param precision 精度；null 视为保持默认
     * @return this
     */
    public DateTimeConfigItem precision(DateTimePrecision precision) {
        if (precision != null) {
            this.precision = precision;
        }
        return this;
    }

    /**
     * 精度对象（fastjson2 getter 驱动序列化，前端经 field.precision 读取）。
     */
    public DateTimePrecision getPrecision() {
        return precision;
    }

    @Override
    protected String validateType(Object value) {
        if (!(value instanceof String)) {
            return displayName != null
                    ? displayName + " 必须是日期时间文本（" + precision.getPattern() + "）"
                    : "配置项 " + key + " 必须是日期时间文本（" + precision.getPattern() + "）";
        }
        return null;
    }

    /**
     * 校验：必须为所选精度格式的合法日期时间。
     * 含日期部分的精度容忍 datetime-local 原样提交的 'T' 分隔符（解析前归一为空格）。
     * 用 {@link ParsePosition} 校验全串消费：SimpleDateFormat.parse 默认容忍尾随多余字符
     * （如秒值混过分钟级校验），全量匹配堵死该口子。
     */
    @Override
    public Object validate(Object value) {
        Object base = super.validate(value);
        if (base != null || value == null) {
            return base;
        }
        String text = value.toString().trim();
        if (precision.hasDatePart()) {
            text = text.replace('T', ' ');
        }
        SimpleDateFormat sdf = new SimpleDateFormat(precision.getPattern());
        sdf.setLenient(false);
        ParsePosition pos = new ParsePosition(0);
        sdf.parse(text, pos);
        if (pos.getIndex() != text.length()) {
            return (displayName != null ? displayName : "配置项 " + key)
                    + " 格式错误，要求 " + precision.getPattern();
        }
        return null;
    }

    @Override
    public String getDefaultValue() {
        return defaultValue;
    }

    @Override
    public String getFieldType() {
        return "datetime";
    }
}
