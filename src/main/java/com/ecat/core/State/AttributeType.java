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
