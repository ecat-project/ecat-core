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

package com.ecat.core.State.UnitConversions;

import com.ecat.core.State.UnitInfo;

/**
 * 相同单位类转换器
 * 专门处理来自同一枚举类的单位之间的转换
 *
 * 支持的使用方式：
 * 1. 静态方法调用：SameUnitClassConverter.convert(value, from, to)
 * 2. 实例方法调用：converter.convert(value)
 *
 * @author coffee
 * @version 1.0.0
 */
public class SameUnitClassConverter implements UnitConversion {

    private final UnitInfo sourceUnit;
    private final UnitInfo targetUnit;

    /**
     * 构造函数
     *
     * @param sourceUnit 源单位
     * @param targetUnit 目标单位
     * @throws IllegalArgumentException 如果单位为null或不属于同一单位类
     */
    public SameUnitClassConverter(UnitInfo sourceUnit, UnitInfo targetUnit) {
        validateInputs(sourceUnit, targetUnit);
        this.sourceUnit = sourceUnit;
        this.targetUnit = targetUnit;
    }

    /**
     * 执行单位转换
     *
     * @param value 要转换的数值
     * @return 转换后的数值
     * @throws IllegalArgumentException 如果数值无效
     */
    @Override
    public double convert(double value) {
        validateValue(value);

        // 检查是否为相同单位类
        if (!isSameUnitClass(sourceUnit, targetUnit)) {
            throw new IllegalArgumentException(
                String.format("Cannot convert between different unit classes: %s and %s",
                             sourceUnit.getClass().getSimpleName(),
                             targetUnit.getClass().getSimpleName()));
        }

        // 相同单位类转换：使用比例换算
        return value * sourceUnit.getRatio() / targetUnit.getRatio();
    }

    /**
     * 静态工厂方法：创建转换器实例
     *
     * @param from 源单位
     * @param to 目标单位
     * @return 转换器实例
     * @throws IllegalArgumentException 如果单位为null或不属于同一单位类
     */
    public static SameUnitClassConverter create(UnitInfo from, UnitInfo to) {
        return new SameUnitClassConverter(from, to);
    }

    /**
     * 静态便捷方法：直接执行转换
     *
     * @param value 要转换的数值
     * @param from 源单位
     * @param to 目标单位
     * @return 转换后的数值
     * @throws IllegalArgumentException 如果参数无效
     */
    public static double convert(double value, UnitInfo from, UnitInfo to) {
        SameUnitClassConverter converter = new SameUnitClassConverter(from, to);
        return converter.convert(value);
    }

    /**
     * 获取源单位
     *
     * @return 源单位
     */
    public UnitInfo getSourceUnit() {
        return sourceUnit;
    }

    /**
     * 获取目标单位
     *
     * @return 目标单位
     */
    public UnitInfo getTargetUnit() {
        return targetUnit;
    }

    /**
     * 检查两个单位是否属于同一单位类
     *
     * @param unit1 第一个单位
     * @param unit2 第二个单位
     * @return 如果属于同一单位类返回true，否则返回false
     */
    public static boolean isSameUnitClass(UnitInfo unit1, UnitInfo unit2) {
        if (unit1 == null || unit2 == null) {
            return false;
        }
        return unit1.getClass().equals(unit2.getClass());
    }

    /**
     * 验证输入参数
     *
     * @param sourceUnit 源单位
     * @param targetUnit 目标单位
     * @throws IllegalArgumentException 如果参数无效
     */
    private void validateInputs(UnitInfo sourceUnit, UnitInfo targetUnit) {
        if (sourceUnit == null) {
            throw new IllegalArgumentException("Source unit cannot be null");
        }
        if (targetUnit == null) {
            throw new IllegalArgumentException("Target unit cannot be null");
        }

        if (!isSameUnitClass(sourceUnit, targetUnit)) {
            throw new IllegalArgumentException(
                String.format("Source and target units must be from the same unit class, but got: %s and %s",
                             sourceUnit.getClass().getSimpleName(),
                             targetUnit.getClass().getSimpleName()));
        }
    }

    /**
     * 验证数值有效性
     *
     * @param value 要验证的数值
     * @throws IllegalArgumentException 如果数值无效
     */
    private void validateValue(double value) {
        if (Double.isNaN(value)) {
            throw new IllegalArgumentException("Conversion value cannot be NaN");
        }
        if (Double.isInfinite(value)) {
            throw new IllegalArgumentException("Conversion value cannot be infinite");
        }
    }

    /**
     * 获取转换器的字符串表示
     *
     * @return 转换器的描述信息
     */
    @Override
    public String toString() {
        return String.format("SameUnitClassConverter{from=%s, to=%s}",
                           sourceUnit != null ? sourceUnit.getName() : "null",
                           targetUnit != null ? targetUnit.getName() : "null");
    }
}
