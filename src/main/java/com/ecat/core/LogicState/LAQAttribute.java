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

package com.ecat.core.LogicState;

import com.ecat.core.State.AQAttribute;
import com.ecat.core.State.AttributeBase;
import com.ecat.core.State.AttributeClass;
import com.ecat.core.State.UnitInfo;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Logic AQ attribute - single physical attribute to single logic attribute delegation.
 *
 * <p>LAQAttribute wraps a physical {@link AQAttribute} and provides logic-level
 * attribute management with cross-class unit conversion support (e.g., PPB ↔ µg/m³).
 * Value conversion uses {@link #convertValueToUnit} with the physical attribute's
 * native unit and the logic's native unit.
 *
 * <p>Cross-class unit conversion (e.g., PPB → µg/m³) requires molecularWeight to be set
 * in the constructor. Without molecularWeight, only same-class conversion is supported
 * (e.g., µg/m³ → mg/m³). If conversion is not possible, an exception will be thrown.
 *
 * <p>Usage example:
 * <pre>
 *   AQAttribute physicalAttr = ...;
 *   LAQAttribute logicAttr = new LAQAttribute(physicalAttr, MolecularWeights.SO2);
 *   logicAttr.initFromDefinition(attrDef);  // sets attributeID, nativeUnit, precision, etc.
 * </pre>
 *
 * @see ILogicAttribute
 * @see AQAttribute
 * @author coffee
 */
public class LAQAttribute extends AQAttribute implements ILogicAttribute<Double> {

    /** Bound physical attribute that this logic attribute delegates to */
    private final AttributeBase<?> bindAttr;

    /** Cached native unit of the bound physical attribute, used for unit conversion */
    private final UnitInfo bindNativeUnit;

    /**
     * Constructor - creates a logic AQ attribute bound to a physical attribute.
     *
     * <p>Uses bindAttr's metadata as initial values. All logic-level metadata
     * (attributeID, nativeUnit, displayUnit, precision) will be overridden by
     * {@link #initFromDefinition(LogicAttributeDefine)} after construction.
     *
     * @param bindAttr the physical attribute to bind to
     * @param molecularWeight molecular weight in g/mol for cross-class unit conversion
     *                        (e.g., SO2=64.066). Required for PPB↔UGM3 conversion.
     *                        Use {@code null} for same-class conversion only.
     */
    public LAQAttribute(AttributeBase<?> bindAttr, Double molecularWeight) {
        super(bindAttr.getAttributeID(), bindAttr.getAttrClass(),
              bindAttr.getNativeUnit(), bindAttr.getNativeUnit(),
              2, false, false, molecularWeight);
        this.bindAttr = bindAttr;
        this.bindNativeUnit = bindAttr.getNativeUnit();
    }

    /**
     * When the bound physical attribute value is updated, update this logic attribute's value.
     *
     * <p>Converts the physical attribute's raw value from the physical attribute's native unit
     * ({@code bindNativeUnit}) to this logic attribute's native unit using {@link #convertValueToUnit}.
     * This supports both same-class conversion (e.g., mg/m³ → µg/m³) and cross-class conversion
     * (e.g., PPB → µg/m³, requiring molecularWeight).
     *
     * @param updatedAttr the physical attribute whose value has been updated
     * @throws IllegalStateException if cross-class conversion is needed but molecularWeight is not set
     * @throws RuntimeException if unit conversion is not supported
     */
    @Override
    public void updateBindAttrValue(AttributeBase<?> updatedAttr) {
        Object rawValue = bindAttr.getValue();
        Double newValue = null;
        if (rawValue != null) {
            Double rawDouble = rawValue instanceof Number ? ((Number) rawValue).doubleValue() : null;
            if (rawDouble != null) {
                newValue = this.convertValueToUnit(rawDouble, bindNativeUnit, nativeUnit);
            }
        }
        updateValue(newValue, updatedAttr.getStatus());
    }

    /**
     * Sets the display value on this logic attribute, delegating to the bound physical attribute.
     *
     * <p>This method does NOT call {@code super.setDisplayValue()}. Instead, it converts the
     * value and delegates to {@code bindAttr.setDisplayValue()}.
     *
     * <p>Converts the display value from {@code fromUnit} to the physical attribute's native unit
     * using {@link #convertValueToUnit}.
     *
     * @param newDisplayValue the display value to set
     * @param fromUnit the unit of the display value
     * @return CompletableFuture indicating success/failure
     * @throws IllegalStateException if cross-class conversion is needed but molecularWeight is not set
     * @throws RuntimeException if unit conversion is not supported
     */
    @Override
    public CompletableFuture<Boolean> setDisplayValue(String newDisplayValue, UnitInfo fromUnit) {
        Double logicValue = Double.parseDouble(newDisplayValue);
        Double sourceValue = this.convertValueToUnit(logicValue, fromUnit, bindNativeUnit);
        return bindAttr.setDisplayValue(String.valueOf(sourceValue), bindNativeUnit);
    }

    /**
     * Returns the list containing the single bound physical attribute.
     *
     * @return singleton list containing bindAttr
     */
    @Override
    public List<AttributeBase<?>> getBindedAttrs() {
        return Arrays.asList(bindAttr);
    }

    /**
     * Sets the logic attribute's attributeID.
     * Directly sets the protected field inherited from AttributeBase.
     *
     * @param attrID the logic attribute ID
     */
    @Override
    public void initAttributeID(String attrID) {
        this.attributeID = attrID;
    }

    /**
     * Sets the logic attribute's native unit.
     * Directly sets the protected field inherited from AttributeBase.
     *
     * @param nativeUnit the native unit for this logic attribute
     */
    @Override
    public void initNativeUnit(UnitInfo nativeUnit) {
        this.nativeUnit = nativeUnit;
    }

    /**
     * 初始化显示单位（直接赋值，不受 unitChangeable 限制）。
     */
    @Override
    public void initDisplayUnit(UnitInfo displayUnit) {
        this.displayUnit = displayUnit;
    }

    /**
     * Sets whether the logic attribute's value can be changed externally.
     * Directly sets the protected field inherited from AttributeBase.
     *
     * @param valueChangeable true if the value can be changed by users/API
     */
    @Override
    public void initValueChangeable(boolean valueChangeable) {
        this.valueChangeable = valueChangeable;
    }

    /**
     * 初始化逻辑属性的属性类型。
     *
     * @param attrClass 属性类型，允许为null
     */
    @Override
    public void initAttrClass(AttributeClass attrClass) {
        this.attrClass = attrClass;
    }

}
