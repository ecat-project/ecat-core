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

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import com.ecat.core.I18n.I18nHelper;
import com.ecat.core.I18n.I18nKeyPath;
import com.ecat.core.State.UnitConversions.SameUnitClassConverter;
import com.ecat.core.Utils.DynamicConfig.ConfigDefinition;
import com.ecat.core.Utils.NumberFormatter;

/**
 * 泛型数值属性基类
 *
 * 提供所有数值类型的公共实现。集成推荐使用具体的子类（FloatAttribute、ShortAttribute 等）。
 * 如需特殊数值类型，可继承此类。
 *
 * @param <T> 数值类型 (Double, Float, Short, Integer 等)
 * @implNote displayName i18n supported, path: state.numeric_attr.{attributeID}
 * @author coffee
 */
public abstract class NumberAttribute<T extends Number> extends AttributeBase<T> {

    /**
     * 支持I18n的构造函数
     */
    protected NumberAttribute(String attributeID, AttributeClass attrClass, UnitInfo nativeUnit,
            UnitInfo displayUnit, int displayPrecision, boolean unitChangeable,
            boolean valueChangeable) {
        this(attributeID, attrClass, nativeUnit, displayUnit, displayPrecision, unitChangeable, valueChangeable, null);
    }

    /**
     * 同时支持用户设置显示名称displayName和I18n的构造函数，displayName优先级高
     */
    protected NumberAttribute(String attributeID, String displayName, AttributeClass attrClass, UnitInfo nativeUnit,
            UnitInfo displayUnit, int displayPrecision, boolean unitChangeable,
            boolean valueChangeable) {
        this(attributeID, displayName, attrClass, nativeUnit, displayUnit, displayPrecision, unitChangeable, valueChangeable, null);
    }

    /**
     * 支持I18n的构造函数
     */
    protected NumberAttribute(String attributeID, AttributeClass attrClass, UnitInfo nativeUnit,
            UnitInfo displayUnit, int displayPrecision, boolean unitChangeable,
            boolean valueChangeable, Function<AttrChangedCallbackParams<T>, CompletableFuture<Boolean>> onChangedCallback) {
        super(attributeID, attrClass, nativeUnit, displayUnit, displayPrecision, unitChangeable,
                valueChangeable, onChangedCallback);
    }

    /**
     * 同时支持用户设置显示名称displayName和I18n的构造函数，displayName优先级高
     */
    protected NumberAttribute(String attributeID, String displayName, AttributeClass attrClass, UnitInfo nativeUnit,
            UnitInfo displayUnit, int displayPrecision, boolean unitChangeable,
            boolean valueChangeable, Function<AttrChangedCallbackParams<T>, CompletableFuture<Boolean>> onChangedCallback) {
        super(attributeID, displayName, attrClass, nativeUnit, displayUnit, displayPrecision, unitChangeable,
                valueChangeable, onChangedCallback);
    }

    @Override
    public String getDisplayValue(UnitInfo toUnit) {
        if (value == null) return null;
        if (toUnit == null || nativeUnit == null) {
            return formatNumberValue(value, displayPrecision);
        }

        Number displayValue;
        if (nativeUnit.getClass().equals(toUnit.getClass())) {
            double ratio = nativeUnit.convertUnit(toUnit);
            displayValue = multiplyNumber(value, ratio);
        } else {
            throw new RuntimeException(I18nHelper.t("error.invalid_unit_conversion"));
        }
        return formatNumberValue(displayValue, displayPrecision);
    }

    @Override
    protected T convertFromUnitImp(T value, UnitInfo fromUnit) {
        if (fromUnit == null || nativeUnit == null) {
            return value;
        }

        if (nativeUnit.getClass().equals(fromUnit.getClass())) {
            double ratio = fromUnit.convertUnit(nativeUnit);
            return convertToType(value.doubleValue() * ratio);
        } else {
            throw new RuntimeException(I18nHelper.t("error.invalid_unit_conversion"));
        }
    }

    @Override
    public Double convertValueToUnit(Double value, UnitInfo fromUnit, UnitInfo toUnit) {
        if (value == null) {
            return null;
        }
        if (fromUnit == null || toUnit == null) {
            throw new NullPointerException("fromUnit and toUnit cannot be null");
        }

        return SameUnitClassConverter.convert(value, fromUnit, toUnit);
    }

    /**
     * 将数值乘以比率
     * @param value 原始数值
     * @param ratio 乘数比率
     * @return 乘积结果
     */
    protected Number multiplyNumber(T value, double ratio) {
        return value.doubleValue() * ratio;
    }

    /**
     * 格式化数值为指定精度的字符串
     * @param value 数值
     * @param precision 精度（小数位数）
     * @return 格式化后的字符串
     */
    protected String formatNumberValue(Number value, int precision) {
        return NumberFormatter.formatValue(value.doubleValue(), precision);
    }

    /**
     * 将double值转换为具体类型T
     * 子类必须实现此方法以提供类型转换逻辑
     * @param value double值
     * @return 转换后的类型T值
     */
    protected abstract T convertToType(double value);

    @Override
    public ConfigDefinition getValueDefinition() {
        return null;
    }

    @Override
    public I18nKeyPath getI18nPrefixPath() {
        return new I18nKeyPath("state.numeric_attr.", "");
    }

    @Override
    public AttributeType getAttributeType() {
        return AttributeType.NUMERIC;
    }
}
