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
import com.ecat.core.State.StringCommandAttribute;
import com.ecat.core.State.UnitInfo;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Logic Command Attribute - thin wrapper extending StringCommandAttribute implementing ILogicAttribute.
 *
 * <p>LCommandAttribute wraps a physical {@link StringCommandAttribute} (or any AttributeBase)
 * and provides logic-level attribute management for command-type attributes
 * (e.g., dispatch_command: ZERO_START/SPAN_START).
 *
 * <p>Two modes:
 * <ul>
 *   <li><b>Bound mode</b>: wraps a physical attribute with command mapping to translate
 *       standard commands (e.g., ZERO_START) to physical device command strings
 *       (e.g., zero_calibration_start)</li>
 *   <li><b>Standalone mode</b>: self-maintained, no physical binding; commands complete silently</li>
 * </ul>
 *
 * <p>Usage example:
 * <pre>
 *   // Bound mode with command mapping
 *   AttributeBase&lt;String&gt; phyAttr = ...;
 *   Map&lt;String, String&gt; mapping = new HashMap&lt;&gt;();
 *   mapping.put("ZERO_START", "zero_calibration_start");
 *   mapping.put("ZERO_END", "zero_calibration_cancel");
 *   LCommandAttribute logicAttr = new LCommandAttribute(phyAttr,
 *       Arrays.asList("ZERO_START", "ZERO_END"), mapping);
 *   logicAttr.initFromDefinition(attrDef);
 *
 *   // Standalone mode
 *   LCommandAttribute standalone = LCommandAttribute.standalone(
 *       "dispatch_command", AttributeClass.DISPATCH_COMMAND,
 *       Arrays.asList("ZERO_START", "ZERO_END"));
 *   standalone.initFromDefinition(attrDef);
 * </pre>
 *
 * @see ILogicAttribute
 * @see StringCommandAttribute
 * @see LNumericAttribute
 * @author coffee
 */
public class LCommandAttribute extends StringCommandAttribute implements ILogicAttribute<String> {

    /** Bound physical attribute that this logic attribute delegates to; null for standalone mode */
    private final AttributeBase<?> bindAttr;

    /** Standard commands (e.g., "ZERO_START", "SPAN_START") */
    private final List<String> standardCommands;

    /**
     * Maps standard commands to physical device command strings.
     * Key: standard command (ZERO_START), Value: physical command (zero_calibration_start).
     * Empty map for standalone mode or when no translation is needed.
     */
    private final Map<String, String> commandMapping;

    /**
     * Bound constructor - creates a logic command attribute bound to a physical attribute.
     *
     * <p>Uses bindAttr's metadata as initial values. All logic-level metadata
     * (attributeID, nativeUnit, displayUnit) will be overridden by
     * {@link #initFromDefinition(LogicAttributeDefine)} after construction.
     *
     * @param bindAttr the physical attribute to bind to
     * @param standardCommands the list of standard commands (e.g., "ZERO_START", "ZERO_END")
     * @param commandMapping maps standard commands to physical device command strings;
     *                       can be empty if no translation is needed
     */
    public LCommandAttribute(AttributeBase<?> bindAttr, List<String> standardCommands,
                             Map<String, String> commandMapping) {
        super(bindAttr.getAttributeID(), bindAttr.getAttrClass(),
              standardCommands, null);
        this.bindAttr = bindAttr;
        this.standardCommands = standardCommands;
        this.commandMapping = commandMapping != null
                ? new HashMap<>(commandMapping)
                : new HashMap<String, String>();
    }

    /**
     * Private constructor for standalone mode.
     *
     * @param attributeID the logic attribute ID
     * @param attrClass the attribute class
     * @param standardCommands the list of standard commands
     */
    private LCommandAttribute(String attributeID, AttributeClass attrClass, List<String> standardCommands) {
        super(attributeID, attrClass, standardCommands, null);
        this.bindAttr = null;
        this.standardCommands = standardCommands;
        this.commandMapping = new HashMap<>();
    }

    /**
     * Factory method to create a standalone LCommandAttribute with no physical binding.
     *
     * <p>Standalone mode is used for command attributes that have no physical device binding.
     * Commands in standalone mode complete silently without error.
     *
     * @param attrId the logic attribute ID
     * @param attrClass the attribute class
     * @param standardCommands the list of standard commands
     * @return a new standalone LCommandAttribute
     */
    public static LCommandAttribute standalone(String attrId, AttributeClass attrClass,
                                                List<String> standardCommands) {
        return new LCommandAttribute(attrId, attrClass, standardCommands);
    }

    /**
     * When the bound physical attribute value is updated, update this logic attribute's value.
     *
     * <p>Bound mode: reads the bindAttr's display value and updates the command value.
     * Standalone mode: no-op.
     *
     * @param updatedAttr the physical attribute whose value has been updated
     */
    @Override
    public void updateBindAttrValue(AttributeBase<?> updatedAttr) {
        if (bindAttr == null) return;

        String displayVal = bindAttr.getDisplayValue(bindAttr.getNativeUnit());
        if (displayVal != null) {
            // Try to match the display value against standard commands or reverse-lookup mapping
            String matchedCommand = parseCommandValue(displayVal);
            if (matchedCommand != null) {
                setValue(matchedCommand);
            }
        }
    }

    /**
     * Sets the display value on this logic attribute, translating via command mapping if needed.
     *
     * <p>Bound mode: translates the standard command via commandMapping, then delegates
     * to bindAttr.setDisplayValue() with the translated physical command string.
     * Standalone mode: returns a completed future silently (no exception).
     *
     * @param newDisplayValue the standard command string to send
     * @param fromUnit the unit of the display value (ignored for commands)
     * @return CompletableFuture indicating success/failure
     */
    @Override
    public CompletableFuture<Boolean> setDisplayValue(String newDisplayValue, UnitInfo fromUnit) {
        if (bindAttr != null) {
            // Translate standard command to physical command via mapping
            String physicalCommand = translateCommand(newDisplayValue);
            return bindAttr.setDisplayValue(physicalCommand, bindAttr.getNativeUnit());
        }
        // Standalone mode: silently complete without error
        return CompletableFuture.completedFuture(true);
    }

    /**
     * Sends a command, translating via command mapping if needed.
     *
     * <p>Bound mode: translates the standard command via commandMapping, then delegates
     * to bindAttr.setDisplayValue() with the translated physical command string.
     * Standalone mode: no-op (silently ignores).
     *
     * @param standardCommand the standard command string to send (e.g., "ZERO_START")
     * @return CompletableFuture indicating success/failure
     */
    public CompletableFuture<Boolean> sendStandardCommand(String standardCommand) {
        if (bindAttr != null) {
            String physicalCommand = translateCommand(standardCommand);
            return bindAttr.setDisplayValue(physicalCommand, bindAttr.getNativeUnit());
        }
        // Standalone mode: silently complete without error
        return CompletableFuture.completedFuture(true);
    }

    /**
     * Translates a standard command to a physical device command string using the command mapping.
     *
     * <p>If no mapping exists for the standard command, returns the standard command as-is.
     *
     * @param standardCommand the standard command string (e.g., "ZERO_START")
     * @return the translated physical command string (e.g., "zero_calibration_start"),
     *         or the original standard command if no mapping is found
     */
    private String translateCommand(String standardCommand) {
        if (commandMapping != null && commandMapping.containsKey(standardCommand)) {
            return commandMapping.get(standardCommand);
        }
        return standardCommand;
    }

    /**
     * Returns the list containing the single bound physical attribute.
     *
     * @return singleton list containing bindAttr, or empty list if standalone mode
     */
    @Override
    public List<AttributeBase<?>> getBindedAttrs() {
        if (bindAttr == null) {
            return Collections.emptyList();
        }
        return Arrays.asList(bindAttr);
    }

    /**
     * Gets the command mapping (standard command -> physical command string).
     *
     * @return unmodifiable view of the command mapping; empty if standalone or no mapping
     */
    public Map<String, String> getCommandMapping() {
        return Collections.unmodifiableMap(commandMapping);
    }

    /**
     * Gets the list of standard commands.
     *
     * @return the standard commands list; may be empty
     */
    public List<String> getStandardCommands() {
        return standardCommands != null
                ? Collections.unmodifiableList(standardCommands)
                : Collections.<String>emptyList();
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

    /**
     * Implements the abstract sendCommandImpl from StringCommandAttribute.
     *
     * <p>In bound mode, translates the command via mapping and delegates to bindAttr.
     * In standalone mode, completes silently.
     *
     * @param cmd the command to send
     * @return CompletableFuture indicating success
     */
    @Override
    protected CompletableFuture<Boolean> sendCommandImpl(String cmd) {
        if (bindAttr != null) {
            String physicalCommand = translateCommand(cmd);
            return bindAttr.setDisplayValue(physicalCommand, bindAttr.getNativeUnit());
        }
        // Standalone mode: silently complete
        return CompletableFuture.completedFuture(true);
    }
}
