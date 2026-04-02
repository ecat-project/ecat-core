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
import com.ecat.core.State.BinaryAttribute;
import com.ecat.core.State.UnitInfo;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Logic Binary Attribute - thin wrapper extending BinaryAttribute implementing ILogicAttribute.
 *
 * <p>LBinaryAttribute wraps a physical {@link BinaryAttribute} (or any AttributeBase)
 * and provides logic-level attribute management for binary-type attributes
 * (e.g., online_status, alarm_status).
 *
 * <p>Two modes:
 * <ul>
 *   <li><b>Bound mode</b>: wraps a physical attribute, delegates value read/write to it</li>
 *   <li><b>Standalone mode</b>: self-maintained, no physical binding</li>
 * </ul>
 *
 * <p>Subclasses override {@link #updateBindAttrValue(AttributeBase)} to provide
 * domain-specific value derivation logic (e.g., online detection from update timestamp).
 *
 * @see ILogicAttribute
 * @see BinaryAttribute
 * @see LNumericAttribute
 * @author coffee
 */
public class LBinaryAttribute extends BinaryAttribute implements ILogicAttribute<Boolean> {

    /** Bound physical attribute that this logic attribute delegates to; null for standalone mode */
    private final AttributeBase<?> bindAttr;

    /**
     * Bound constructor - creates a logic binary attribute bound to a physical attribute.
     *
     * <p>Uses bindAttr's metadata as initial values. All logic-level metadata
     * (attributeID, nativeUnit, displayUnit) will be overridden by
     * {@link #initFromDefinition(LogicAttributeDefine)} after construction.
     *
     * @param bindAttr the physical attribute to bind to
     */
    public LBinaryAttribute(AttributeBase<?> bindAttr) {
        super(bindAttr.getAttributeID(), bindAttr.getAttrClass(), false);
        this.bindAttr = bindAttr;
    }

    /**
     * Protected constructor for subclasses that manage their own binding independently.
     *
     * @param attributeID the logic attribute ID
     * @param attrClass the attribute class
     */
    protected LBinaryAttribute(String attributeID, AttributeClass attrClass) {
        super(attributeID, attrClass, false);
        this.bindAttr = null;
    }

    /**
     * When the bound physical attribute value is updated, update this logic attribute's value.
     *
     * <p>Default implementation does nothing. Subclasses should override to provide
     * domain-specific logic (e.g., derive online status from update timestamp).
     *
     * @param updatedAttr the physical attribute whose value has been updated
     */
    @Override
    public void updateBindAttrValue(AttributeBase<?> updatedAttr) {
        // Default: no-op. Subclasses override with domain-specific logic.
    }

    /**
     * Sets the display value on this logic attribute.
     *
     * <p>Bound mode: delegates to bindAttr.setDisplayValue().
     * Standalone mode: calls turnOn()/turnOff() locally.
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
        // Standalone mode: not supported
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
     *
     * @param attrID the logic attribute ID
     */
    @Override
    public void initAttributeID(String attrID) {
        this.attributeID = attrID;
    }

    /**
     * Sets the logic attribute's native unit.
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
