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
import com.ecat.core.State.AttributeType;
import com.ecat.core.State.UnitInfo;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;

/**
 * LBinaryAttribute 单元测试
 *
 * @author coffee
 */
public class LBinaryAttributeTest {

    // ========== Bound mode tests ==========

    @Test
    public void boundModeHasBindedAttr() {
        AttributeBase<?> phyAttr = createMockAttr("online", AttributeClass.STATUS);
        LBinaryAttribute logicAttr = new LBinaryAttribute(phyAttr);

        assertEquals(1, logicAttr.getBindedAttrs().size());
        assertEquals(phyAttr, logicAttr.getBindedAttrs().get(0));
        assertFalse(logicAttr.isStandalone());
        assertNotNull(logicAttr.getBindAttr());
    }

    @Test
    public void boundModeUpdateBindAttrValueNoOp() {
        AttributeBase<?> phyAttr = createMockAttr("online", AttributeClass.STATUS);
        LBinaryAttribute logicAttr = new LBinaryAttribute(phyAttr);

        // Default implementation does nothing
        logicAttr.updateBindAttrValue(phyAttr);
        assertNull(logicAttr.getValue());
    }

    @Test
    public void boundModeSetDisplayValueDelegatesToBindAttr() {
        TestMockAttr phyAttr = createMockAttr("online", AttributeClass.STATUS);
        LBinaryAttribute logicAttr = new LBinaryAttribute(phyAttr);
        logicAttr.initAttributeID("online_status");
        logicAttr.initValueChangeable(true);

        logicAttr.setDisplayValue("on", null).join();
        assertEquals("on", phyAttr.lastSetStringValue);
    }

    @Test
    public void boundModeImplementsILogicAttribute() {
        AttributeBase<?> phyAttr = createMockAttr("alarm", AttributeClass.ALARM_STATUS);
        LBinaryAttribute logicAttr = new LBinaryAttribute(phyAttr);
        assertTrue(logicAttr instanceof ILogicAttribute);
    }

    @Test
    public void boundModeInitMethods() {
        AttributeBase<?> phyAttr = createMockAttr("alarm", AttributeClass.ALARM_STATUS);
        LBinaryAttribute logicAttr = new LBinaryAttribute(phyAttr);

        logicAttr.initAttributeID("alarm_status");
        assertEquals("alarm_status", logicAttr.getAttributeID());

        logicAttr.initValueChangeable(false);
        assertFalse(logicAttr.canValueChange());

        logicAttr.initNativeUnit(null);
        assertNull(logicAttr.getNativeUnit());

        logicAttr.initDisplayUnit(null);
        assertNull(logicAttr.getDisplayUnit());
    }

    @Test
    public void boundModeAttributeValueStoredInSubclass() {
        // Test that subclasses can call updateValue to set the binary value
        AttributeBase<?> phyAttr = createMockAttr("online", AttributeClass.STATUS);
        TestUpdatableBinary logicAttr = new TestUpdatableBinary(phyAttr);

        logicAttr.doUpdate(true);
        assertTrue(logicAttr.isOn());

        logicAttr.doUpdate(false);
        assertTrue(logicAttr.isOff());
    }

    // ========== Standalone mode tests ==========

    @Test
    public void standaloneModeNoBindedAttrs() {
        TestStandaloneBinary logicAttr = new TestStandaloneBinary("alarm_status", AttributeClass.ALARM_STATUS);
        assertTrue(logicAttr.getBindedAttrs().isEmpty());
        assertTrue(logicAttr.isStandalone());
        assertNull(logicAttr.getBindAttr());
    }

    @Test
    public void standaloneModeSetDisplayValueReturnsFalse() {
        TestStandaloneBinary logicAttr = new TestStandaloneBinary("alarm_status", AttributeClass.ALARM_STATUS);
        logicAttr.initValueChangeable(true);

        Boolean result = logicAttr.setDisplayValue("on", null).join();
        assertFalse(result);
    }

    @Test
    public void standaloneModeTurnOnOff() {
        TestStandaloneBinary logicAttr = new TestStandaloneBinary("alarm_status", AttributeClass.ALARM_STATUS);

        logicAttr.turnOn();
        assertTrue(logicAttr.isOn());
        assertTrue(logicAttr.getValue());

        logicAttr.turnOff();
        assertTrue(logicAttr.isOff());
        assertFalse(logicAttr.getValue());
    }

    // ========== Test helpers ==========

    private static class TestStandaloneBinary extends LBinaryAttribute {
        TestStandaloneBinary(String attributeID, AttributeClass attrClass) {
            super(attributeID, attrClass);
        }
    }

    private static class TestUpdatableBinary extends LBinaryAttribute {
        TestUpdatableBinary(AttributeBase<?> bindAttr) {
            super(bindAttr);
        }

        public void doUpdate(boolean value) {
            updateValue(value);
        }
    }

    private static TestMockAttr createMockAttr(String attrId, AttributeClass attrClass) {
        return new TestMockAttr(attrId, attrClass);
    }

    private static class TestMockAttr extends AttributeBase<String> {
        String lastSetStringValue;

        TestMockAttr(String attributeID, AttributeClass attrClass) {
            super(attributeID, attrClass, null, null, 0, false, true);
        }

        @Override public String getDisplayValue(UnitInfo toUnit) { return null; }
        @Override protected String convertFromUnitImp(String value, UnitInfo fromUnit) { return value; }
        @Override public Double convertValueToUnit(Double value, UnitInfo fromUnit, UnitInfo toUnit) { return value; }
        @Override public com.ecat.core.Utils.DynamicConfig.ConfigDefinition getValueDefinition() { return null; }
        @Override protected com.ecat.core.I18n.I18nKeyPath getI18nPrefixPath() {
            return new com.ecat.core.I18n.I18nKeyPath("test.", "test");
        }
        @Override public AttributeType getAttributeType() { return AttributeType.STRING_SELECT; }

        @Override
        protected CompletableFuture<Boolean> setDisplayValueImp(String value, UnitInfo fromUnit) {
            this.lastSetStringValue = value;
            return CompletableFuture.completedFuture(true);
        }
    }
}
