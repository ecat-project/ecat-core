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

import com.ecat.core.State.AttributeBase;
import com.ecat.core.State.AttributeClass;
import com.ecat.core.State.NumericAttribute;
import com.ecat.core.State.UnitInfo;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Logic Numeric Attribute - thin wrapper extending NumericAttribute implementing ILogicAttribute.
 *
 * <p>LNumericAttribute wraps a physical {@link NumericAttribute} and provides logic-level
 * attribute management for non-AQ numeric attributes (temperature, humidity, voltage, etc.).
 *
 * <p>This is the single-physical-to-single-logic variant. For multi-physical-to-single-logic
 * aggregation, see {@link LMixNumericAttribute}.
 *
 * <p>Usage example:
 * <pre>
 *   NumericAttribute physicalAttr = ...;
 *   LNumericAttribute logicAttr = new LNumericAttribute(physicalAttr);
 *   logicAttr.initFromDefinition(attrDef);  // sets attributeID, nativeUnit, precision, etc.
 * </pre>
 *
 * @see ILogicAttribute
 * @see NumericAttribute
 * @see LMixNumericAttribute
 * @author coffee
 */
public class LNumericAttribute extends NumericAttribute implements ILogicAttribute<Double> {

    /** Bound physical attribute that this logic attribute delegates to */
    private final AttributeBase<?> bindAttr;

    /**
     * Constructor - creates a logic numeric attribute bound to a physical attribute.
     *
     * <p>Uses bindAttr's metadata as initial values. All logic-level metadata
     * (attributeID, nativeUnit, displayUnit, precision) will be overridden by
     * {@link #initFromDefinition(LogicAttributeDefine)} after construction.
     *
     * @param bindAttr the physical attribute to bind to
     */
    public LNumericAttribute(AttributeBase<?> bindAttr) {
        super(bindAttr.getAttributeID(), bindAttr.getAttrClass(),
              bindAttr.getNativeUnit(), bindAttr.getNativeUnit(),
              bindAttr.getDisplayPrecision(), false, false);
        this.bindAttr = bindAttr;
    }

    /**
     * Protected constructor for subclasses (e.g., {@link LMixNumericAttribute})
     * that manage their own binding independently.
     *
     * @param attributeID the logic attribute ID
     * @param attrClass the attribute class
     * @param nativeUnit the native unit
     * @param displayUnit the display unit
     * @param displayPrecision the number of decimal places to display
     */
    protected LNumericAttribute(String attributeID, AttributeClass attrClass,
            UnitInfo nativeUnit, UnitInfo displayUnit, int displayPrecision) {
        super(attributeID, attrClass, nativeUnit, displayUnit, displayPrecision, false, false);
        this.bindAttr = null;
    }

    /**
     * When the bound physical attribute value is updated, update this logic attribute's value.
     *
     * <p>Passthrough: reads the bindAttr's display value in this logic attribute's
     * native unit and parses it as a Double.
     *
     * @param updatedAttr the physical attribute whose value has been updated
     */
    @Override
    public void updateBindAttrValue(AttributeBase<?> updatedAttr) {
        if (bindAttr == null) return;

        String displayVal = bindAttr.getDisplayValue(nativeUnit);
        if (displayVal == null) {
            updateValue(null, updatedAttr.getStatus());
        } else {
            try {
                Double newValue = Double.parseDouble(displayVal);
                updateValue(newValue, updatedAttr.getStatus());
            } catch (NumberFormatException e) {
                // If parsing fails, set null with the source status
                updateValue(null, updatedAttr.getStatus());
            }
        }
    }

    /**
     * Sets the display value on this logic attribute, delegating to the bound physical attribute.
     *
     * <p>This method does NOT call {@code super.setDisplayValue()}. Instead, it delegates
     * the display value string directly to bindAttr.
     *
     * @param newDisplayValue the display value to set
     * @param fromUnit the unit of the display value
     * @return CompletableFuture indicating success/failure
     */
    @Override
    public CompletableFuture<Boolean> setDisplayValue(String newDisplayValue, UnitInfo fromUnit) {
        if (bindAttr == null) {
            return CompletableFuture.completedFuture(false);
        }
        return bindAttr.setDisplayValue(newDisplayValue, bindAttr.getNativeUnit());
    }

    /**
     * Returns the list containing the single bound physical attribute.
     *
     * @return singleton list containing bindAttr, or empty list if no bindAttr (subclass manages its own)
     */
    @Override
    public List<AttributeBase<?>> getBindedAttrs() {
        if (bindAttr == null) {
            return Arrays.asList();
        }
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
     * Gets the bound physical attribute.
     *
     * @return the bound physical attribute, or null if using protected constructor
     */
    public AttributeBase<?> getBindAttr() {
        return bindAttr;
    }
}
