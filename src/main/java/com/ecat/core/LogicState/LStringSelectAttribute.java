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
import com.ecat.core.State.StringSelectAttribute;
import com.ecat.core.State.UnitInfo;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Logic String Select Attribute - thin wrapper extending StringSelectAttribute implementing ILogicAttribute.
 *
 * <p>LStringSelectAttribute wraps a physical {@link StringSelectAttribute} (or any AttributeBase)
 * and provides logic-level attribute management for select-type attributes
 * (e.g., manual_status: Normal/Maintenance/Calibration).
 *
 * <p>Two modes:
 * <ul>
 *   <li><b>Bound mode</b>: wraps a physical attribute, delegates value read/write to it</li>
 *   <li><b>Standalone mode</b>: self-maintained, no physical binding (e.g., manual_status)</li>
 * </ul>
 *
 * <p>Usage example:
 * <pre>
 *   // Bound mode
 *   AttributeBase&lt;String&gt; phyAttr = ...;
 *   LStringSelectAttribute logicAttr = new LStringSelectAttribute(phyAttr,
 *       Arrays.asList("Normal", "Maintenance", "Calibration"));
 *   logicAttr.initFromDefinition(attrDef);
 *
 *   // Standalone mode
 *   LStringSelectAttribute standalone = LStringSelectAttribute.standalone(
 *       "manual_status", AttributeClass.MODE,
 *       Arrays.asList("Normal", "Maintenance", "Calibration"));
 *   standalone.initFromDefinition(attrDef);
 * </pre>
 *
 * @see ILogicAttribute
 * @see StringSelectAttribute
 * @see LNumericAttribute
 * @author coffee
 */
public class LStringSelectAttribute extends StringSelectAttribute implements ILogicAttribute<String> {

    /** Bound physical attribute that this logic attribute delegates to; null for standalone mode */
    private final AttributeBase<?> bindAttr;

    /**
     * Bound constructor - creates a logic string select attribute bound to a physical attribute.
     *
     * <p>Uses bindAttr's metadata as initial values. All logic-level metadata
     * (attributeID, nativeUnit, displayUnit) will be overridden by
     * {@link #initFromDefinition(LogicAttributeDefine)} after construction.
     *
     * @param bindAttr the physical attribute to bind to
     * @param options the list of valid options for this select attribute
     */
    public LStringSelectAttribute(AttributeBase<?> bindAttr, List<String> options) {
        super(bindAttr.getAttributeID(), bindAttr.getAttrClass(),
              bindAttr.getNativeUnit(), bindAttr.getNativeUnit(),
              false, options, null);
        this.bindAttr = bindAttr;
    }

    /**
     * Protected constructor for subclasses that manage their own binding independently.
     *
     * @param attributeID the logic attribute ID
     * @param attrClass the attribute class
     * @param options the list of valid options for this select attribute
     */
    protected LStringSelectAttribute(String attributeID, AttributeClass attrClass, List<String> options) {
        super(attributeID, attrClass, null, null, false, options, null);
        this.bindAttr = null;
    }

    /**
     * Factory method to create a standalone LStringSelectAttribute with no physical binding.
     *
     * <p>Standalone mode is used for attributes that maintain their own value
     * (e.g., manual_status which is not tied to any physical device).
     *
     * @param attrId the logic attribute ID
     * @param attrClass the attribute class
     * @param options the list of valid options
     * @return a new standalone LStringSelectAttribute
     */
    public static LStringSelectAttribute standalone(String attrId, AttributeClass attrClass, List<String> options) {
        return new LStringSelectAttribute(attrId, attrClass, options);
    }

    /**
     * When the bound physical attribute value is updated, update this logic attribute's value.
     *
     * <p>Bound mode: reads the bindAttr's display value and calls updateValue().
     * Standalone mode: no-op.
     *
     * @param updatedAttr the physical attribute whose value has been updated
     */
    @Override
    public void updateBindAttrValue(AttributeBase<?> updatedAttr) {
        if (bindAttr == null) return;

        String displayVal = bindAttr.getDisplayValue(bindAttr.getNativeUnit());
        if (displayVal == null) {
            // If physical attr returns null, do not update (keep current value)
            return;
        }
        // Try to find matching option (case-sensitive match)
        String matchedOption = findOption(displayVal);
        if (matchedOption != null) {
            updateValue(matchedOption, updatedAttr.getStatus());
        }
    }

    /**
     * Sets the display value on this logic attribute.
     *
     * <p>Bound mode: delegates to bindAttr.setDisplayValue().
     * Standalone mode: calls selectOption() locally.
     *
     * @param newDisplayValue the display value to set
     * @param fromUnit the unit of the display value
     * @return CompletableFuture indicating success/failure
     */
    @Override
    public CompletableFuture<Boolean> setDisplayValue(String newDisplayValue, UnitInfo fromUnit) {
        if (bindAttr != null) {
            return bindAttr.setDisplayValue(newDisplayValue, bindAttr.getNativeUnit());
        }
        // Standalone mode: update value locally (avoid selectOption which calls publicState/getDevice)
        String matchedOption = findOption(newDisplayValue);
        if (matchedOption != null) {
            updateValue(matchedOption);
            return CompletableFuture.completedFuture(true);
        }
        return CompletableFuture.completedFuture(false);
    }

    /**
     * Returns the list containing the single bound physical attribute.
     *
     * @return singleton list containing bindAttr, or empty list if standalone mode
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
     * @return the bound physical attribute, or null if in standalone mode
     */
    public AttributeBase<?> getBindAttr() {
        return bindAttr;
    }

    /**
     * Returns whether this attribute is in standalone mode (no physical binding).
     *
     * @return true if standalone mode, false if bound mode
     */
    public boolean isStandalone() {
        return bindAttr == null;
    }
}
