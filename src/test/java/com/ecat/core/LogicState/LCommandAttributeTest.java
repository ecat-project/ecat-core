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

import com.ecat.core.ConfigEntry.ConfigEntry;
import com.ecat.core.Device.DeviceBase;
import com.ecat.core.State.AttributeBase;
import com.ecat.core.State.AttributeClass;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;

/**
 * LCommandAttribute 单元测试
 *
 * @author coffee
 */
public class LCommandAttributeTest {

    // ========== Bound mode tests ==========

    @Test
    public void boundModeDelegatesToPhysicalCommand() {
        AttributeBase<String> phyAttr = createStringCommandAttr("dispatch_cmd");
        LCommandAttribute logicAttr = new LCommandAttribute(phyAttr,
            Arrays.asList("ZERO_START", "SPAN_START"), new HashMap<String, String>());
        logicAttr.initAttributeID("dispatch_command");

        assertEquals(1, logicAttr.getBindedAttrs().size());
        assertEquals(phyAttr, logicAttr.getBindedAttrs().get(0));
    }

    @Test
    public void commandMappingTranslatesCommands() {
        AttributeBase<String> phyAttr = createStringCommandAttr("dispatch_cmd");

        Map<String, String> mapping = new HashMap<>();
        mapping.put("ZERO_START", "zero_calibration_start");
        mapping.put("ZERO_END", "zero_calibration_cancel");
        LCommandAttribute logicAttr = new LCommandAttribute(phyAttr,
            Arrays.asList("ZERO_START", "ZERO_END"), mapping);
        logicAttr.initAttributeID("dispatch_command");

        assertNotNull(logicAttr.getCommandMapping());
        assertEquals("zero_calibration_start", logicAttr.getCommandMapping().get("ZERO_START"));
        assertEquals("zero_calibration_cancel", logicAttr.getCommandMapping().get("ZERO_END"));
        assertEquals(2, logicAttr.getCommandMapping().size());
    }

    @Test
    public void boundModeSetDisplayValueTranslatesViaMapping() {
        TestStringCommandAttr phyAttr = createStringCommandAttr("dispatch_cmd");

        Map<String, String> mapping = new HashMap<>();
        mapping.put("ZERO_START", "zero_calibration_start");
        LCommandAttribute logicAttr = new LCommandAttribute(phyAttr,
            Arrays.asList("ZERO_START", "ZERO_END"), mapping);
        logicAttr.initAttributeID("dispatch_command");
        logicAttr.initValueChangeable(true);
        logicAttr.setDevice(createMockDevice());

        logicAttr.setDisplayValue("ZERO_START", null).join();

        assertEquals("zero_calibration_start", phyAttr.getLastCommand());
        assertEquals("ZERO_START", logicAttr.getValue());
    }

    @Test
    public void boundModeGetStandardCommands() {
        AttributeBase<String> phyAttr = createStringCommandAttr("dispatch_cmd");
        LCommandAttribute logicAttr = new LCommandAttribute(phyAttr,
            Arrays.asList("ZERO_START", "ZERO_END", "SPAN_START"), new HashMap<String, String>());

        assertEquals(3, logicAttr.getStandardCommands().size());
        assertTrue(logicAttr.getStandardCommands().contains("ZERO_START"));
        assertTrue(logicAttr.getStandardCommands().contains("ZERO_END"));
        assertTrue(logicAttr.getStandardCommands().contains("SPAN_START"));
    }

    @Test
    public void boundModeWithoutMappingPassesThrough() {
        TestStringCommandAttr phyAttr = createStringCommandAttr("dispatch_cmd");

        LCommandAttribute logicAttr = new LCommandAttribute(phyAttr,
            Arrays.asList("ZERO_START", "ZERO_END"), new HashMap<String, String>());
        logicAttr.initAttributeID("dispatch_command");
        logicAttr.initValueChangeable(true);
        logicAttr.setDevice(createMockDevice());

        logicAttr.setDisplayValue("ZERO_START", null).join();

        assertEquals("ZERO_START", phyAttr.getLastCommand());
        assertEquals("ZERO_START", logicAttr.getValue());
    }

    // ========== Standalone mode tests ==========

    @Test
    public void standaloneModeHasNoBindedAttrs() {
        LCommandAttribute logicAttr = LCommandAttribute.standalone(
            "dispatch_command", AttributeClass.DISPATCH_COMMAND,
            Arrays.asList("ZERO_START", "ZERO_END"));
        logicAttr.initAttributeID("dispatch_command");

        assertTrue(logicAttr.getBindedAttrs().isEmpty());
        assertTrue(logicAttr.isStandalone());
    }

    @Test
    public void standaloneModeSendCommandCompletesWithoutError() {
        LCommandAttribute logicAttr = LCommandAttribute.standalone(
            "dispatch_command", AttributeClass.DISPATCH_COMMAND,
            Arrays.asList("ZERO_START", "ZERO_END"));
        logicAttr.initAttributeID("dispatch_command");
        logicAttr.initValueChangeable(true);
        logicAttr.setDevice(createMockDevice());

        try {
            logicAttr.setDisplayValue("ZERO_START", null).join();
        } catch (Exception e) {
            fail("Standalone mode setDisplayValue should not throw: " + e.getMessage());
        }
    }

    @Test
    public void standaloneModeSetDisplayValueReturnsTrue() {
        LCommandAttribute logicAttr = LCommandAttribute.standalone(
            "dispatch_command", AttributeClass.DISPATCH_COMMAND,
            Arrays.asList("ZERO_START", "ZERO_END"));
        logicAttr.initAttributeID("dispatch_command");
        logicAttr.initValueChangeable(true);
        logicAttr.setDevice(createMockDevice());

        Boolean result = logicAttr.setDisplayValue("ZERO_START", null).join();
        assertTrue(result);
        assertEquals("ZERO_START", logicAttr.getValue());
    }

    @Test
    public void standaloneModeCommandMappingIsEmpty() {
        LCommandAttribute logicAttr = LCommandAttribute.standalone(
            "dispatch_command", AttributeClass.DISPATCH_COMMAND,
            Arrays.asList("ZERO_START", "ZERO_END"));

        assertNotNull(logicAttr.getCommandMapping());
        assertTrue(logicAttr.getCommandMapping().isEmpty());
    }

    @Test
    public void standaloneModeGetStandardCommands() {
        LCommandAttribute logicAttr = LCommandAttribute.standalone(
            "dispatch_command", AttributeClass.DISPATCH_COMMAND,
            Arrays.asList("ZERO_START", "ZERO_END"));

        assertEquals(2, logicAttr.getStandardCommands().size());
        assertTrue(logicAttr.getStandardCommands().contains("ZERO_START"));
        assertTrue(logicAttr.getStandardCommands().contains("ZERO_END"));
    }

    @Test
    public void standaloneModeUpdateBindAttrValueIsNoOp() {
        LCommandAttribute logicAttr = LCommandAttribute.standalone(
            "dispatch_command", AttributeClass.DISPATCH_COMMAND,
            Arrays.asList("ZERO_START", "ZERO_END"));

        try {
            logicAttr.updateBindAttrValue(null);
        } catch (Exception e) {
            fail("Standalone mode updateBindAttrValue should not throw: " + e.getMessage());
        }
    }

    // ========== Interface tests ==========

    @Test
    public void implementsILogicAttribute() {
        LCommandAttribute logicAttr = LCommandAttribute.standalone(
            "dispatch_command", AttributeClass.DISPATCH_COMMAND,
            Arrays.asList("ZERO_START", "ZERO_END"));
        assertTrue(logicAttr instanceof ILogicAttribute);
    }

    // ========== Init method tests ==========

    @Test
    public void initMethodsWorkCorrectly() {
        LCommandAttribute logicAttr = LCommandAttribute.standalone(
            "cmd", AttributeClass.DISPATCH_COMMAND,
            Arrays.asList("ZERO_START"));

        logicAttr.initAttributeID("my_command");
        assertEquals("my_command", logicAttr.getAttributeID());

        logicAttr.initValueChangeable(true);
        assertTrue(logicAttr.canValueChange());

        logicAttr.initNativeUnit(null);
        assertNull(logicAttr.getNativeUnit());

        logicAttr.initDisplayUnit(null);
        assertNull(logicAttr.getDisplayUnit());
    }

    @Test
    public void commandMappingIsUnmodifiable() {
        AttributeBase<String> phyAttr = createStringCommandAttr("dispatch_cmd");

        Map<String, String> mapping = new HashMap<>();
        mapping.put("ZERO_START", "zero_calibration_start");
        LCommandAttribute logicAttr = new LCommandAttribute(phyAttr,
            Arrays.asList("ZERO_START"), mapping);

        Map<String, String> returnedMapping = logicAttr.getCommandMapping();
        try {
            returnedMapping.put("NEW_CMD", "new_command");
            fail("getCommandMapping() should return unmodifiable map");
        } catch (UnsupportedOperationException e) {
            // Expected
        }
    }

    // ========== Local test helpers ==========

    private static TestStringCommandAttr createStringCommandAttr(String attrId) {
        return new TestStringCommandAttr(attrId);
    }

    /** Minimal mock DeviceBase for standalone tests (sendCommand needs getDevice().getId()) */
    private static DeviceBase createMockDevice() {
        ConfigEntry entry = new ConfigEntry();
        entry.setEntryId("test-device");
        entry.setUniqueId("test-device");
        java.util.Map<String, Object> data = new java.util.HashMap<>();
        data.put("name", "test-device");
        entry.setData(data);
        return new DeviceBase(entry) {
            @Override public String getId() { return "test-device"; }
            @Override public void init() {}
            @Override public void start() {}
            @Override public void stop() {}
            @Override public void release() {}
        };
    }

    /** Minimal test StringCommand attribute */
    private static class TestStringCommandAttr extends AttributeBase<String> {
        private String lastCommand;

        TestStringCommandAttr(String attributeID) {
            super(attributeID, AttributeClass.DISPATCH_COMMAND, null, null, 0, false, true);
        }

        @Override public String getDisplayValue(com.ecat.core.State.UnitInfo toUnit) { return lastCommand; }
        @Override protected String convertFromUnitImp(String value, com.ecat.core.State.UnitInfo fromUnit) { return value; }
        @Override public Double convertValueToUnit(Double value, com.ecat.core.State.UnitInfo fromUnit, com.ecat.core.State.UnitInfo toUnit) { return value; }
        @Override public com.ecat.core.Utils.DynamicConfig.ConfigDefinition getValueDefinition() { return null; }
        @Override protected com.ecat.core.I18n.I18nKeyPath getI18nPrefixPath() {
            return new com.ecat.core.I18n.I18nKeyPath("test.", "test");
        }
        @Override public com.ecat.core.State.AttributeType getAttributeType() { return com.ecat.core.State.AttributeType.STRING_COMMAND; }

        @Override
        protected CompletableFuture<Boolean> setDisplayValueImp(String value, com.ecat.core.State.UnitInfo fromUnit) {
            this.lastCommand = value;
            return CompletableFuture.completedFuture(true);
        }

        public String getLastCommand() { return lastCommand; }
    }
}
