package com.ecat.core.State.Unit;

import com.ecat.core.State.UnitInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * 工厂类，用于根据枚举全名获取Ecat.Core中对应的 UnitInfo 实例
 * 
 * 
 * <pre>
 * // 示例用法
 * UnitInfo unit = UnitInfoFactory.getEnum("CurrentUnit.MILLIAMPERE");
 * System.out.println(unit.getName()); // 输出 "mA"
 * </pre>
 * 
 * @author coffee
 */
public class UnitInfoFactory {
/** 缓存：key = 枚举全名，value = 枚举值 */
    private static final Map<String, UnitInfo> CACHE = new HashMap<>();
    private static final String UNIT_CLASS_PREFIX = "com.ecat.core.State.Unit.";

    private UnitInfoFactory() { }

    /**
     * 根据 "类名.枚举名" 找到对应的 UnitInfo
     * 例子：getEnum("CurrentUnit.MILLIAMPERE")
     */
    public static UnitInfo getEnum(String enumFullName) {
        if (enumFullName == null || enumFullName.trim().isEmpty()) {
            throw new IllegalArgumentException("enumFullName must not be empty");
        }

        // 先查缓存
        UnitInfo cached = CACHE.get(enumFullName);
        if (cached != null) {
            return cached;
        }

        int lastDot = enumFullName.lastIndexOf('.');
        if (lastDot <= 0) {
            throw new IllegalArgumentException(
                    "must be in format 'ClassName.ENUM_NAME'");
        }

        String className = enumFullName.substring(0, lastDot);
        String enumName  = enumFullName.substring(lastDot + 1);

        try {
            // 判断className 是否以有前缀，兼容前缀的类名
            if (!className.contains(UNIT_CLASS_PREFIX)) {
                className = UNIT_CLASS_PREFIX + className;
                enumFullName = className + "." + enumName;
            }
            // 加载枚举类
            Class<?> clazz = Class.forName(className);
            if (!clazz.isEnum()) {
                throw new IllegalArgumentException(className + " is not an enum");
            }

            // 确保它实现了 UnitInfo
            if (!UnitInfo.class.isAssignableFrom(clazz)) {
                throw new IllegalArgumentException(className + " does not implement UnitInfo");
            }

            // 找到枚举常量
            Object[] constants = clazz.getEnumConstants();
            for (Object c : constants) {
                if (((Enum<?>) c).name().equals(enumName)) {
                    UnitInfo result = (UnitInfo) c;
                    CACHE.put(enumFullName, result);
                    return result;
                }
            }

            throw new IllegalArgumentException("No enum constant " + enumFullName);

        } catch (ClassNotFoundException ex) {
            throw new IllegalArgumentException("Enum class not found: " + className, ex);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("No enum constant " + enumFullName, ex);
        }
    }
}
