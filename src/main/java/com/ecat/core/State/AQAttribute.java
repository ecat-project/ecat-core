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

import com.ecat.core.State.Unit.AirMassUnit;
import com.ecat.core.State.Unit.AirVolumeUnit;
import com.ecat.core.State.UnitConversions.AirVolumeToAirMass;
import com.ecat.core.State.UnitConversions.AirMassToAirVolume;
import com.ecat.core.Utils.NumberFormatter;
import com.ecat.core.I18n.I18nHelper;
import com.ecat.core.I18n.I18nKeyPath;
import com.ecat.core.Utils.DynamicConfig.ConfigDefinition;

/** 
 * 适用于PM、SO2、O3等单参数支持ppm/mg/m3等单位转换的属性
 * 
 * @apiNote displayName i18n supported, path: state.aq_attr.{attributeID}
 * 
 * @author coffee
 */ 
public class AQAttribute extends AttributeBase<Double> {

    public Double molecularWeight; //分子质量，不需要ppm/mg/m3转换的不需要设置

    /**
     * 支持I18n的构造函数
     */
	public AQAttribute(String attributeID, AttributeClass attrClass, UnitInfo nativeUnit,
            UnitInfo displayUnit, int displayPrecision, boolean unitChangeable,
            boolean valueChangeable, Double molecularWeight) {
        super(attributeID, attrClass, nativeUnit, displayUnit, displayPrecision, unitChangeable,
                valueChangeable);

        this.molecularWeight = molecularWeight;
    }

    /**
     * 同时支持用户设置显示名称displayName和I18n的构造函数，displayName优先级高
     */
    public AQAttribute(String attributeID, String displayName, AttributeClass attrClass, UnitInfo nativeUnit,
            UnitInfo displayUnit, int displayPrecision, boolean unitChangeable,
            boolean valueChangeable, Double molecularWeight) {
        super(attributeID, displayName, attrClass, nativeUnit, displayUnit, displayPrecision, unitChangeable,
                valueChangeable);
        this.molecularWeight = molecularWeight;
    }

    // @Override
	// public boolean setValue(String value) {
	// 	// convert the value to double
    //     if(!valueChangeable){
    //         return false;
    //     }
    //     try {
    //         this.value = Double.parseDouble(value);
    //         return true;
    //     } catch (NumberFormatException e) {
    //         return false;
    //     }
	// }

    @Override
    public boolean updateValue(Double value) {
        return super.updateValue(value);
    }

    @Override
    public boolean updateValue(Double value, AttributeStatus newStatus) {
        return super.updateValue(value, newStatus);
    }

	// @Override
	// public String getValue() {
	// 	// Provide implementation
    //     return value.toString();
	// }

    @Override
    public String getDisplayValue(UnitInfo toUnit){
        if (value == null) return null;
        
        if (toUnit == null || nativeUnit == null) return NumberFormatter.formatValue(value, displayPrecision); //单位缺失，按照原始值返回

        Double displayValue;
        // 如果是同class转换，直接转换
        if(nativeUnit.getClass().equals(toUnit.getClass())){
            // 根据 newUnit 的转换系数转换
            displayValue = value * nativeUnit.convertUnit(toUnit);
        }
        // 如果是跨class转换，需要根据 molecularWeight 转换
        else{
            UnitConverter converter = new UnitConverter();
            // 根据 newUnit 的转换系数转换
            if(nativeUnit.getClass().equals(AirVolumeUnit.class) && toUnit.getClass().equals(AirMassUnit.class) ){
                AirVolumeToAirMass massToVolume = new AirVolumeToAirMass((AirVolumeUnit)nativeUnit, (AirMassUnit)toUnit, molecularWeight);
                displayValue = converter.convertValue(value, massToVolume);
            }
            else if(nativeUnit.getClass().equals(AirMassUnit.class) && toUnit.getClass().equals(AirVolumeUnit.class) ){
                AirMassToAirVolume massToVolume = new AirMassToAirVolume((AirMassUnit)nativeUnit, (AirVolumeUnit)toUnit, molecularWeight);
                displayValue = converter.convertValue(value, massToVolume);
            }
            else{
                // throw转换异常
                throw new RuntimeException(I18nHelper.t("error.invalid_unit_conversion"));
            }
        }
        // displayValue = new java.math.BigDecimal(displayValue)
        //         .setScale(displayPrecision, java.math.RoundingMode.HALF_UP)
        //         .doubleValue();
        return NumberFormatter.formatValue(displayValue, displayPrecision);
        // return displayValue.toString();
    }

    @Override
    protected Double convertFromUnitImp(Double fromValue, UnitInfo fromUnit)
    {
        if (fromValue == null) return null;
        if (fromUnit == null || nativeUnit == null) return fromValue; //单位缺失，按照原始值返回

        // 根据输入的 fromUnit 和 nativeUnit 将fromValue转换为 nativeUnit 的值
        UnitInfo toUnit = nativeUnit; // 目标单位是 nativeUnit
        Double displayValue; // 适合nativeUnit的值
        // 如果是同class转换，直接转换
        if(fromUnit.getClass().equals(toUnit.getClass())){
            // 根据 fromUnit 的转换系数转换
            displayValue = fromValue * fromUnit.convertUnit(toUnit);
        }
        // 如果是跨class转换，需要根据 molecularWeight 转换
        else{
            UnitConverter converter = new UnitConverter();
            // 根据 fromUnit 的转换系数转换
            if(fromUnit.getClass().equals(AirVolumeUnit.class) && toUnit.getClass().equals(AirMassUnit.class) ){
                AirVolumeToAirMass massToVolume = new AirVolumeToAirMass((AirVolumeUnit)fromUnit, (AirMassUnit)toUnit, molecularWeight);
                displayValue = converter.convertValue(fromValue, massToVolume);
            }
            else if(fromUnit.getClass().equals(AirMassUnit.class) && toUnit.getClass().equals(AirVolumeUnit.class) ){
                AirMassToAirVolume massToVolume = new AirMassToAirVolume((AirMassUnit)fromUnit, (AirVolumeUnit)toUnit, molecularWeight);
                displayValue = converter.convertValue(fromValue, massToVolume);
            }
            else{
                // throw转换异常
                throw new RuntimeException(I18nHelper.t("error.invalid_unit_conversion"));
            }
        }
        
        return displayValue;
    }

    // public void setMolecularWeight(Double molecularWeight) {
    //     this.molecularWeight = molecularWeight;
    // }

    @Override
    public ConfigDefinition getValueDefinition() {
        // AQ attributes typically don't need validation by default
        // Subclasses can override this to add specific validation rules
        return null;
    }

    @Override
    public I18nKeyPath getI18nPrefixPath() {
        return new I18nKeyPath("state.aq_attr.", "");
    }

    @Override
    public AttributeType getAttributeType() {
        return AttributeType.NUMERIC;
    }

}
