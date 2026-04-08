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
import com.ecat.core.State.TextAttribute;
import com.ecat.core.State.UnitInfo;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Logic Text Attribute - thin wrapper extending TextAttribute implementing ILogicAttribute.
 *
 * <p>LTextAttribute wraps a physical {@link TextAttribute} (or any AttributeBase)
 * and provides logic-level attribute management for free-text attributes
 * (e.g., replace_timed_cron: cron expression, cylinder_id: cylinder identification).
 *
 * <p>Two modes:
 * <ul>
 *   <li><b>Bound mode</b>: wraps a physical attribute, delegates value read/write to it</li>
 *   <li><b>Standalone mode</b>: self-maintained, no physical binding</li>
 * </ul>
 *
 * @see ILogicAttribute
 * @see TextAttribute
 * @see LNumericAttribute
 * @author coffee
 */
public class LTextAttribute extends TextAttribute implements ILogicAttribute<String> {

    /** Bound physical attribute that this logic attribute delegates to; null for standalone mode */
    private final AttributeBase<?> bindAttr;

    /**
     * Bound constructor - creates a logic text attribute bound to a physical attribute.
     *
     * @param bindAttr the physical attribute to bind to
     */
    public LTextAttribute(AttributeBase<?> bindAttr) {
        super(bindAttr.getAttributeID(), bindAttr.getAttrClass(), null, null, false);
        this.bindAttr = bindAttr;
    }

    /**
     * Protected constructor for standalone mode.
     *
     * @param attributeID the logic attribute ID
     * @param attrClass the attribute class
     */
    protected LTextAttribute(String attributeID, AttributeClass attrClass) {
        super(attributeID, attrClass, null, null, false);
        this.bindAttr = null;
    }

    /**
     * Protected constructor for {@link LogicAttributeFactory}.
     * Creates a template with attributeID set (for i18n initialization);
     * remaining fields are set by {@link #initFromDefinition(LogicAttributeDefine)}.
     *
     * @param attributeID the logic attribute ID (must not be null, required for i18n)
     */
    protected LTextAttribute(String attributeID) {
        super(attributeID);
        this.bindAttr = null;
    }

    /**
     * Factory method to create a standalone LTextAttribute with no physical binding.
     *
     * @param attrId the logic attribute ID
     * @param attrClass the attribute class
     * @return a new standalone LTextAttribute
     */
    public static LTextAttribute standalone(String attrId, AttributeClass attrClass) {
        return new LTextAttribute(attrId, attrClass);
    }

    /**
     * When the bound physical attribute value is updated, update this logic attribute's value.
     *
     * <p>Bound mode: reads the bindAttr's display value as string and calls updateValue().
     * Standalone mode: no-op.
     *
     * @param updatedAttr the physical attribute whose value has been updated
     */
    @Override
    public void updateBindAttrValue(AttributeBase<?> updatedAttr) {
        if (bindAttr == null) return;

        String displayVal = bindAttr.getDisplayValue(bindAttr.getNativeUnit());
        updateValue(displayVal, updatedAttr.getStatus());
    }

    /**
     * Sets the display value on this logic attribute.
     *
     * <p>Bound mode: delegates to bindAttr.setDisplayValue().
     * Standalone mode: calls super.setDisplayValue() locally.
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
        // Standalone mode: delegate to parent
        return super.setDisplayValue(newDisplayValue, fromUnit);
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
     * 初始化逻辑属性的属性类型。
     *
     * @param attrClass 属性类型，允许为null
     */
    @Override
    public void initAttrClass(AttributeClass attrClass) {
        this.attrClass = attrClass;
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
