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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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

    /** Mapping from standard option key to physical value; empty map means no mapping (direct match) */
    private final Map<String, String> valueMapping;

    /**
     * Bound constructor with valueMapping - creates a logic string select attribute bound to a physical attribute.
     *
     * <p>Uses bindAttr's metadata as initial values. All logic-level metadata
     * (attributeID, nativeUnit, displayUnit) will be overridden by
     * {@link #initFromDefinition(LogicAttributeDefine)} after construction.
     *
     * <p>The valueMapping maps standard option keys to physical attribute values.
     * For example: {"cooling" -> "1", "heating" -> "2"} means when the physical
     * attribute value is "1", the logic attribute will show "cooling", and vice versa.
     *
     * @param bindAttr the physical attribute to bind to
     * @param standardOptions the list of standard options for this select attribute
     * @param valueMapping mapping from standard option key to physical value, may be null or empty
     */
    public LStringSelectAttribute(AttributeBase<?> bindAttr, List<String> standardOptions,
                                   Map<String, String> valueMapping) {
        super(bindAttr.getAttributeID(), bindAttr.getAttrClass(),
              bindAttr.getNativeUnit(), bindAttr.getNativeUnit(),
              false, standardOptions, null);
        this.bindAttr = bindAttr;
        this.valueMapping = valueMapping != null
                ? new HashMap<>(valueMapping)
                : new HashMap<>();
    }

    /**
     * Master constructor for standalone mode with persistable support.
     *
     * @param attributeID the logic attribute ID
     * @param attrClass the attribute class
     * @param options the list of valid options for this select attribute
     * @param persistable whether this attribute's value should be persisted
     * @param defaultValue the default value to use when no persisted value exists
     */
    protected LStringSelectAttribute(String attributeID, AttributeClass attrClass, List<String> options,
                                      boolean persistable, String defaultValue) {
        super(attributeID, attrClass, null, null, false, options, null);
        this.persistable = persistable;
        this.defaultValue = defaultValue;
        this.bindAttr = null;
        this.valueMapping = new HashMap<>();
    }

    /**
     * Protected constructor for subclasses that manage their own binding independently.
     *
     * @param attributeID the logic attribute ID
     * @param attrClass the attribute class
     * @param options the list of valid options for this select attribute
     */
    protected LStringSelectAttribute(String attributeID, AttributeClass attrClass, List<String> options) {
        this(attributeID, attrClass, options, false, null);
    }

    /**
     * Protected constructor for standalone string-select attributes.
     * Creates a template with attributeID set (for i18n initialization);
     * remaining fields are set by {@link #initFromDefinition(LogicAttributeDefine)}.
     *
     * @param attributeID the logic attribute ID (must not be null, required for i18n)
     */
    protected LStringSelectAttribute(String attributeID) {
        super(attributeID);
        this.bindAttr = null;
        this.valueMapping = new HashMap<>();
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
     * <p>Bound mode: reads the bindAttr's display value, reverse-looks up via valueMapping
     * (or falls back to direct option match), and calls updateValue().
     * <b>Strict mode</b>: throws IllegalStateException if the physical value cannot be mapped
     * to any standard option.
     * Standalone mode: no-op.
     *
     * @param updatedAttr the physical attribute whose value has been updated
     * @throws IllegalStateException if physical value cannot be mapped to any standard option
     */
    @Override
    public void updateBindAttrValue(AttributeBase<?> updatedAttr) {
        if (bindAttr == null) return;

        // 使用 getI18nValue() 获取稳定的语言无关值：
        // - NumericAttribute: 返回 getDisplayValue()（精度格式化后的值）
        // - BinaryAttribute: 返回 "on"/"off"
        // - 其他类型: 返回 getDisplayValue()
        String displayVal = bindAttr.getI18nValue(bindAttr.getNativeUnit());
        if (displayVal == null) {
            throw new IllegalStateException("bindAttr displayValue is null, attrId=" + attributeID);
        }

        // 反向查找：物理值 → 标准 key
        String matchedOption = reverseLookupMapping(displayVal);
        if (matchedOption == null) {
            // 兜底：物理值直接匹配选项字符串（无需映射的场景）
            matchedOption = findOption(displayVal);
        }
        if (matchedOption == null) {
            throw new IllegalStateException(
                "Physical value '" + displayVal + "' cannot be mapped to any standard option, attrId=" + attributeID
                + ", options=" + getOptions());
        }

        updateValue(matchedOption, updatedAttr.getStatus());
    }

    /**
     * Sets the display value on this logic attribute.
     *
     * <p>Bound mode: translates standard option key to physical value via valueMapping,
     * then delegates to bindAttr.setDisplayValue().
     * <b>Strict mode</b>: throws IllegalArgumentException if the standard key has no mapping
     * and valueMapping is non-empty.
     * Standalone mode: calls selectOption() locally.
     *
     * @param newDisplayValue the standard option key to set
     * @param fromUnit the unit of the display value
     * @return CompletableFuture indicating success/failure
     * @throws IllegalArgumentException if standard key has no mapping to physical value
     */
    @Override
    public CompletableFuture<Boolean> setDisplayValue(String newDisplayValue, UnitInfo fromUnit) {
        if (bindAttr != null) {
            String physicalValue = valueMapping.get(newDisplayValue);
            if (physicalValue == null) {
                throw new IllegalArgumentException(
                    "Standard key '" + newDisplayValue + "' has no mapping to physical value, attrId=" + attributeID);
            }
            return bindAttr.setDisplayValue(physicalValue, bindAttr.getNativeUnit());
        }
        // Standalone mode: delegate to parent (valueChangeable check + selectOption)
        // Caller must pass an exact option string as newDisplayValue
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
     * Gets the valueMapping (standard option key → physical value).
     *
     * @return unmodifiable copy of the valueMapping
     */
    public Map<String, String> getValueMapping() {
        return new HashMap<>(valueMapping);
    }

    /**
     * Reverse lookup: find the standard option key for a given physical value.
     *
     * @param physicalValue the physical attribute value to look up
     * @return the matching standard option key, or null if not found
     */
    private String reverseLookupMapping(String physicalValue) {
        if (physicalValue == null || valueMapping.isEmpty()) return null;
        for (Map.Entry<String, String> entry : valueMapping.entrySet()) {
            if (entry.getValue().equals(physicalValue)) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Returns whether this attribute is in standalone mode (no physical binding).
     *
     * @return true if standalone mode, false if bound mode
     */
    public boolean isStandalone() {
        return bindAttr == null;
    }

    /**
     * Sets the options list for factory-created instances.
     * Called by {@link com.ecat.core.LogicDevice.LogicDevice#genAttrMap}
     * after construction when the definition is a {@link StringSelectAttrDef}.
     *
     * @param options the standard options list to set
     */
    public void setOptionsFromDef(List<String> options) {
        this.options = options;
    }
}
