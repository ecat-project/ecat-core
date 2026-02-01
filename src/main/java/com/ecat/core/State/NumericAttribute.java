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

import com.ecat.core.Utils.NumberFormatter;
import com.ecat.core.I18n.I18nHelper;
import com.ecat.core.I18n.I18nKeyPath;
import com.ecat.core.Utils.DynamicConfig.ConfigDefinition;


/**
 * NumericAttribute class represents a numeric attribute with a specific unit and display format.
 * 
 * This class extends the AttributeBase class and provides methods to handle numeric values,
 * unit conversions, and display formatting.
 * 
 * It is suitable for attributes that represent numeric states such as
 * current, voltage, wind speed, wind direction, etc.
 * 
 * @apiNote displayName i18n supported, path: state.numeric_attr.{attributeID}
 * 
 * @author coffee
 */
public class NumericAttribute extends AttributeBase<Double> {

    /**
     * 支持I18n的构造函数
     */
	public NumericAttribute(String attributeID, AttributeClass attrClass, UnitInfo nativeUnit,
            UnitInfo displayUnit, int displayPrecision, boolean unitChangeable,
            boolean valueChangeable) {
        this(attributeID, attrClass, nativeUnit, displayUnit, displayPrecision, unitChangeable, valueChangeable, null);
    }

    /**
     * 同时支持用户设置显示名称displayName和I18n的构造函数，displayName优先级高
     */
    public NumericAttribute(String attributeID, String displayName, AttributeClass attrClass, UnitInfo nativeUnit,
            UnitInfo displayUnit, int displayPrecision, boolean unitChangeable,
            boolean valueChangeable) {
        this(attributeID, displayName, attrClass, nativeUnit, displayUnit, displayPrecision, unitChangeable, valueChangeable, null);
    }

    /**
     * 支持I18n的构造函数
     */
    public NumericAttribute(String attributeID, AttributeClass attrClass, UnitInfo nativeUnit,
            UnitInfo displayUnit, int displayPrecision, boolean unitChangeable,
            boolean valueChangeable, Function<AttrChangedCallbackParams<Double>, CompletableFuture<Boolean>> onChangedCallback) {
        super(attributeID, attrClass, nativeUnit, displayUnit, displayPrecision, unitChangeable,
                valueChangeable, onChangedCallback);
    }

    /**
     * 同时支持用户设置显示名称displayName和I18n的构造函数，displayName优先级高
     */
    public NumericAttribute(String attributeID, String displayName, AttributeClass attrClass, UnitInfo nativeUnit,
            UnitInfo displayUnit, int displayPrecision, boolean unitChangeable,
            boolean valueChangeable, Function<AttrChangedCallbackParams<Double>, CompletableFuture<Boolean>> onChangedCallback) {
        super(attributeID, displayName, attrClass, nativeUnit, displayUnit, displayPrecision, unitChangeable,
                valueChangeable, onChangedCallback);
    }

    @Override
    public boolean updateValue(Double value) {
        return super.updateValue(value);
    }

    @Override
    public boolean updateValue(Double value, AttributeStatus newStatus) {
        return super.updateValue(value, newStatus);
    }

    @Override
    public String getDisplayValue(UnitInfo toUnit){
        if (value ==null) return null;
        if (toUnit == null || nativeUnit == null) return NumberFormatter.formatValue(value, displayPrecision); //单位缺失，按照原始值返回

        Double displayValue;
        // 如果是同class转换，直接转换
        if(nativeUnit.getClass().equals(toUnit.getClass())){
            // 根据 newUnit 的转换系数转换
            displayValue = value * nativeUnit.convertUnit(toUnit);
        }
        // 如果是跨class转换，需要根据 molecularWeight 转换
        else{
            throw new RuntimeException(I18nHelper.t("error.invalid_unit_conversion"));
        }
        return NumberFormatter.formatValue(displayValue, displayPrecision);
    }

    @Override
    protected Double convertFromUnitImp(Double value, UnitInfo fromUnit) {
        if (fromUnit == null || nativeUnit == null) {
            return value; // 如果没有指定单位，直接返回原始值
        }
        // 如果是同class转换，直接转换
        if(nativeUnit.getClass().equals(fromUnit.getClass())){
            // 根据 newUnit 的转换系数转换
            return value * fromUnit.convertUnit(nativeUnit);
        }
        // 如果是跨class转换，目前不支持，因为单位转换太广泛，如果有更明确的转换范围则定义新的属性类
        else{
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

        // 复用现有的 SameUnitClassConverter 进行同单位类转换
        return new UnitConverter().convertSameUnitClass(value, fromUnit, toUnit);
    }

    @Override
    public ConfigDefinition getValueDefinition() {
        // Numeric attributes typically don't need validation by default
        // Subclasses can override this to add specific validation rules
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
