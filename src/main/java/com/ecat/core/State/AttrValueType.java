package com.ecat.core.State;

/**
 * 属性值类型枚举
 * 定义java 常用的基础值类型与Ecat数据存储类型的映射关系
 * 
 * @author coffee
 */
public enum AttrValueType {

    INT("int"), //int类型存储
    DOUBLE("double"),  // double类型存储
    BOOL("bool"), // bool类型存储
    STRING("string"), // string类型存储
    FLOAT("float"), // float类型存储
    SHORT("short"), // short类型存储
    LONG("long"), // long类型存储
    BYTE("byte"); // byte类型存储


    private final String valueTypeName;

    AttrValueType(String valueTypeName) {
        this.valueTypeName = valueTypeName;
    }

    public String getValueTypeName() {
        return valueTypeName;
    }
    /**
     * 根据Java类类型获取对应的AttrValueType枚举
     * @param clazz Java类类型
     * @return 对应的AttrValueType枚举
     * @throws IllegalArgumentException 如果传入的类类型不支持转换为AttrValueType
     */
    public static AttrValueType fromJDKClass(Class<?> clazz) {
        if (clazz == Integer.class || clazz == int.class) {
            return INT;
        } else if (clazz == Double.class || clazz == double.class) {
            return DOUBLE;
        } else if (clazz == Boolean.class || clazz == boolean.class) {
            return BOOL;
        } else if (clazz == String.class) {
            return STRING;
        } else if (clazz == Float.class || clazz == float.class) {
            return FLOAT;
        } else if (clazz == Short.class || clazz == short.class) {
            return SHORT;
        } else if (clazz == Long.class || clazz == long.class) {
            return LONG;
        } else if (clazz == Byte.class || clazz == byte.class) {
            return BYTE;
        } else {
            throw new IllegalArgumentException("Unsupported class type: " + clazz.getName());
        }
    }
}
