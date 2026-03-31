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
import com.ecat.core.State.UnitInfo;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

/**
 * Three-parameter function interface for logic-to-source value conversion.
 * Java 8 does not provide a built-in TriFunction, so we define one here.
 *
 * @param <T> first input type
 * @param <U> second input type
 * @param <V> third input type
 * @param <R> return type
 */
@FunctionalInterface
interface TriFunction<T, U, V, R> {
    R apply(T t, U u, V v);
}

/**
 * Logic AQ attribute - single physical attribute to single logic attribute delegation.
 *
 * <p>LAQAttribute wraps a physical {@link AQAttribute} and provides logic-level
 * attribute management with cross-class unit conversion support (e.g., PPB ↔ µg/m³).
 * It supports optional conversion functions between the physical (source) value and the logic value:
 * <ul>
 *   <li>{@code stlfunc} (source-to-logic): custom conversion function.
 *       When not set, passthrough uses {@link #convertValueToUnit} with the
 *       physical attribute's native unit and the logic's native unit.</li>
 *   <li>{@code ltsfunc} (logic-to-source): custom reverse conversion function.
 *       When not set, passthrough uses {@link #convertValueToUnit} in reverse direction.</li>
 * </ul>
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

    /** Source-to-logic conversion function. Converts physical attribute value to logic value.
     *  Receives the physical attribute and the logic's native unit, returns the converted logic value.
     *  When null, passthrough is used (converts using {@link #convertValueToUnit}). */
    private BiFunction<AttributeBase<?>, UnitInfo, Double> stlfunc;

    /** Logic-to-source conversion function. Converts logic display value string to source (physical) value.
     *  Receives the display value string, the physical attribute, and the logic's native unit.
     *  Returns the converted value to be set on the physical attribute.
     *  When null, passthrough is used (converts using {@link #convertValueToUnit}). */
    private TriFunction<String, AttributeBase<?>, UnitInfo, Double> ltsfunc;

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
     * <p>If {@code stlfunc} is set, applies the conversion function to transform the physical value.
     * Otherwise (passthrough), converts the physical attribute's raw value from the physical
     * attribute's native unit ({@code bindNativeUnit}) to this logic attribute's native unit
     * using {@link #convertValueToUnit}. This supports both same-class conversion
     * (e.g., mg/m³ → µg/m³) and cross-class conversion (e.g., PPB → µg/m³, requiring molecularWeight).
     *
     * @param updatedAttr the physical attribute whose value has been updated
     * @throws IllegalStateException if cross-class conversion is needed but molecularWeight is not set
     * @throws RuntimeException if unit conversion is not supported
     */
    @Override
    public void updateBindAttrValue(AttributeBase<?> updatedAttr) {
        Double newValue;

        if (stlfunc != null) {
            // Apply source-to-logic conversion function
            newValue = stlfunc.apply(updatedAttr, nativeUnit);
        } else {
            // Passthrough: convert raw value from physical native unit to logic native unit
            Object rawValue = bindAttr.getValue();
            if (rawValue == null) {
                newValue = null;
            } else {
                Double rawDouble = rawValue instanceof Number ? ((Number) rawValue).doubleValue() : null;
                if (rawDouble == null) {
                    newValue = null;
                } else {
                    newValue = this.convertValueToUnit(rawDouble, bindNativeUnit, nativeUnit);
                }
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
     * <p>If {@code ltsfunc} is set, the conversion function transforms the display value string
     * to a source value, which is then passed to bindAttr.
     * Otherwise (passthrough), converts the display value from {@code fromUnit} to the physical
     * attribute's native unit using {@link #convertValueToUnit}.
     *
     * @param newDisplayValue the display value to set
     * @param fromUnit the unit of the display value
     * @return CompletableFuture indicating success/failure
     * @throws IllegalStateException if cross-class conversion is needed but molecularWeight is not set
     * @throws RuntimeException if unit conversion is not supported
     */
    @Override
    public CompletableFuture<Boolean> setDisplayValue(String newDisplayValue, UnitInfo fromUnit) {
        if (ltsfunc != null) {
            // Apply logic-to-source conversion function
            Double sourceValue = ltsfunc.apply(newDisplayValue, bindAttr, fromUnit);
            if (sourceValue == null) {
                return CompletableFuture.completedFuture(false);
            }
            return bindAttr.setDisplayValue(String.valueOf(sourceValue), bindNativeUnit);
        } else {
            // Passthrough: convert from display unit to physical native unit
            Double logicValue = Double.parseDouble(newDisplayValue);
            Double sourceValue = this.convertValueToUnit(logicValue, fromUnit, bindNativeUnit);
            return bindAttr.setDisplayValue(String.valueOf(sourceValue), bindNativeUnit);
        }
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
     * Sets the source-to-logic conversion function.
     * This function is called in {@link #updateBindAttrValue(AttributeBase)} to convert
     * the physical attribute value to the logic attribute value.
     *
     * @param stlfunc the conversion function, or null for passthrough
     */
    public void setSource2LogicConvertFunc(BiFunction<AttributeBase<?>, UnitInfo, Double> stlfunc) {
        this.stlfunc = stlfunc;
    }

    /**
     * Sets the logic-to-source conversion function.
     * This function is called in {@link #setDisplayValue(String, UnitInfo)} to convert
     * the logic display value to the physical attribute value.
     *
     * @param ltsfunc the conversion function, or null for passthrough
     */
    public void setLogic2SourceConvertFunc(TriFunction<String, AttributeBase<?>, UnitInfo, Double> ltsfunc) {
        this.ltsfunc = ltsfunc;
    }
}
