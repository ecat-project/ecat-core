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

package com.ecat.core.State;

/**
 * 属性类型枚举
 * 按照继承体系组织类型
 */
public enum AttributeType {
    // 基础属性类型
    BINARY("binary", "二值属性"),
    NUMERIC("numeric", "数值属性"),
    TEXT("text", "文本属性"),

    // 命令类型 - 具体类型的命令属性
    STRING_COMMAND("string_command", "字符串命令类型"),

    // 选择类型 - 具体类型的选择属性
    STRING_SELECT("string_select", "字符串选择类型"),

    // 未知类型
    UNKNOWN("unknown", "未知类型");

    private final String code;
    private final String description;

    AttributeType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() { return code; }
    public String getDescription() { return description; }
}
