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

import com.alibaba.fastjson2.annotation.JSONField;

/**
 * 日期时间字段精度枚举（{@link DateTimeConfigItem} 用）。
 * <p>
 * 每种精度同时锚定三端契约，保证前后端永远对得上：
 * <ul>
 *   <li><b>值格式</b>（Java pattern）：后端校验与存储格式；</li>
 *   <li><b>原生控件</b>：前端 config-flow lib 据此选 {@code <input>} 类型
 *       （date / datetime-local / time，秒级加 step="1"）；</li>
 *   <li><b>前端值形态</b>：原生控件产出值经 lib 归一为对应格式。</li>
 * </ul>
 * 序列化为 {@link #getCode()}（随 ConfigItem JSON 的 precision 字段下发，前端同名匹配）。
 * 不提供任意 format 字符串：原生选择器只能产出这些形态，自由格式会断裂前后端契约
 * （纯文本自由格式需求用 TextConfigItem + 自定义校验）。
 *
 * @author dreamfalls
 */
public enum DateTimePrecision {

    /** 仅日期：{@code yyyy-MM-dd}，前端 {@code <input type="date">} */
    DATE("date", "yyyy-MM-dd"),

    /** 日期时间到分：{@code yyyy-MM-dd HH:mm}，前端 {@code <input type="datetime-local">} */
    DATETIME_MINUTE("datetime_minute", "yyyy-MM-dd HH:mm"),

    /** 日期时间到秒（默认）：{@code yyyy-MM-dd HH:mm:ss}，前端 datetime-local + step="1" */
    DATETIME_SECOND("datetime_second", "yyyy-MM-dd HH:mm:ss"),

    /** 仅时间到秒：{@code HH:mm:ss}，前端 {@code <input type="time">}（step="1"） */
    TIME("time", "HH:mm:ss");

    /** 序列化码（前后端契约，勿改） */
    private final String code;

    /** 值格式（Java pattern） */
    private final String pattern;

    DateTimePrecision(String code, String pattern) {
        this.code = code;
        this.pattern = pattern;
    }

    /**
     * 序列化码（前后端契约，勿改）。
     * {@code @JSONField(value = true)}：fastjson2 序列化/反序列化本枚举时用此值而非 name()，
     * 保证 schema JSON 里 precision 恒为小写码（前端同名匹配）。
     */
    @JSONField(value = true)
    public String getCode() {
        return code;
    }

    public String getPattern() {
        return pattern;
    }

    /** 是否含日期部分（决定校验前是否做 'T' 分隔归一） */
    public boolean hasDatePart() {
        return this != TIME;
    }

    /**
     * 按序列化码解析精度；大小写不敏感（容忍误传枚举 name 大写），
     * 未知/空码回退 {@link #DATETIME_SECOND}（默认精度，兼容旧 schema 与旧副本）。
     *
     * @param code 序列化码（可空）
     * @return 精度枚举，永不返回 null
     */
    public static DateTimePrecision fromCode(String code) {
        if (code != null) {
            for (DateTimePrecision p : values()) {
                if (p.code.equalsIgnoreCase(code)) {
                    return p;
                }
            }
        }
        return DATETIME_SECOND;
    }
}
