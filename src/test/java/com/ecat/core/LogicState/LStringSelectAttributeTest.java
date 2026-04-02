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

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;

/**
 * LStringSelectAttribute 单元测试
 *
 * @author coffee
 */
public class LStringSelectAttributeTest {

    // ========== Bound mode tests ==========

    @Test
    public void boundModeDelegatesToPhysicalAttr() {
        AttributeBase<String> phyAttr = createStringSelectAttr("test_mode",
            Arrays.asList("Normal", "Maintenance"), "Normal");
        LStringSelectAttribute logicAttr = new LStringSelectAttribute(phyAttr,
            Arrays.asList("Normal", "Maintenance", "Calibration"));
        logicAttr.initAttributeID("manual_status");
        logicAttr.initValueChangeable(true);

        assertEquals("manual_status", logicAttr.getAttributeID());
        assertEquals(1, logicAttr.getBindedAttrs().size());
        assertEquals(phyAttr, logicAttr.getBindedAttrs().get(0));
    }

    @Test
    public void boundModeUpdateValueReadsFromBindAttr() {
        TestStringSelectAttr phyAttr = createStringSelectAttr("test_mode",
            Arrays.asList("Normal", "Maintenance"), "Normal");

        LStringSelectAttribute logicAttr = new LStringSelectAttribute(phyAttr,
            Arrays.asList("Normal", "Maintenance", "Calibration"));
        logicAttr.initAttributeID("manual_status");
        logicAttr.initValueChangeable(true);

        assertNull(logicAttr.getValue());

        phyAttr.setTestValue("Maintenance");
        logicAttr.updateBindAttrValue(phyAttr);

        assertEquals("Maintenance", logicAttr.getValue());
    }

    // ========== Standalone mode tests ==========

    @Test
    public void standaloneModeHasNoBindedAttrs() {
        LStringSelectAttribute logicAttr = LStringSelectAttribute.standalone(
            "manual_status", AttributeClass.MODE, Arrays.asList("Normal", "Maintenance"));
        logicAttr.initAttributeID("manual_status");
        logicAttr.initValueChangeable(true);

        assertEquals("manual_status", logicAttr.getAttributeID());
        assertTrue(logicAttr.getBindedAttrs().isEmpty());
    }

    @Test
    public void standaloneModeStoresValueLocally() {
        LStringSelectAttribute logicAttr = LStringSelectAttribute.standalone(
            "manual_status", AttributeClass.MODE, Arrays.asList("Normal", "Maintenance"));
        logicAttr.initAttributeID("manual_status");
        logicAttr.initValueChangeable(true);

        logicAttr.updateValue("Maintenance");
        assertEquals("Maintenance", logicAttr.getValue());
    }

    @Test
    public void standaloneModeUpdateBindAttrValueIsNoOp() {
        LStringSelectAttribute logicAttr = LStringSelectAttribute.standalone(
            "manual_status", AttributeClass.MODE, Arrays.asList("Normal", "Maintenance"));
        logicAttr.initAttributeID("manual_status");
        logicAttr.initValueChangeable(true);

        logicAttr.updateBindAttrValue(null);
        assertNull(logicAttr.getValue());
    }

    @Test
    public void standaloneModeSetDisplayValueSelectsLocally() {
        LStringSelectAttribute logicAttr = LStringSelectAttribute.standalone(
            "manual_status", AttributeClass.MODE, Arrays.asList("Normal", "Maintenance"));
        logicAttr.initAttributeID("manual_status");
        logicAttr.initValueChangeable(true);

        logicAttr.setDisplayValue("Maintenance", null).join();
        assertEquals("Maintenance", logicAttr.getValue());
    }

    @Test
    public void standaloneModeIsStandalone() {
        LStringSelectAttribute logicAttr = LStringSelectAttribute.standalone(
            "manual_status", AttributeClass.MODE, Arrays.asList("Normal", "Maintenance"));
        assertTrue(logicAttr.isStandalone());
        assertNull(logicAttr.getBindAttr());
    }

    @Test
    public void boundModeIsNotStandalone() {
        AttributeBase<String> phyAttr = createStringSelectAttr("test_mode",
            Arrays.asList("Normal"), "Normal");
        LStringSelectAttribute logicAttr = new LStringSelectAttribute(phyAttr,
            Arrays.asList("Normal", "Maintenance"));
        assertFalse(logicAttr.isStandalone());
        assertNotNull(logicAttr.getBindAttr());
    }

    // ========== Interface tests ==========

    @Test
    public void implementsILogicAttribute() {
        LStringSelectAttribute logicAttr = LStringSelectAttribute.standalone(
            "status", AttributeClass.STATUS, Arrays.asList("Normal", "Alarm"));
        assertTrue(logicAttr instanceof ILogicAttribute);
    }

    // ========== Local test helpers ==========

    private static TestStringSelectAttr createStringSelectAttr(String attrId,
            List<String> options, String initialValue) {
        return new TestStringSelectAttr(attrId, options, initialValue);
    }

    private static class TestStringSelectAttr extends AttributeBase<String> {
        private final List<String> options;
        private String value;

        TestStringSelectAttr(String attributeID, List<String> options, String initialValue) {
            super(attributeID, AttributeClass.MODE, null, null, 0, false, false);
            this.options = options;
            this.value = initialValue;
        }

        @Override public String getDisplayValue(UnitInfo toUnit) { return value; }
        @Override protected String convertFromUnitImp(String value, UnitInfo fromUnit) { return value; }
        @Override public Double convertValueToUnit(Double value, UnitInfo fromUnit, UnitInfo toUnit) { return value; }
        @Override public com.ecat.core.Utils.DynamicConfig.ConfigDefinition getValueDefinition() { return null; }
        @Override protected com.ecat.core.I18n.I18nKeyPath getI18nPrefixPath() {
            return new com.ecat.core.I18n.I18nKeyPath("test.", "test");
        }
        @Override public AttributeType getAttributeType() { return AttributeType.STRING_SELECT; }
        @Override public String getValue() { return value; }

        public void setTestValue(String val) { this.value = val; }
    }
}
