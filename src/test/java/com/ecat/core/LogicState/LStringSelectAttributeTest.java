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
import com.ecat.core.Device.DeviceBase;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;
import static org.mockito.Mockito.when;

/**
 * LStringSelectAttribute 单元测试
 *
 * @author coffee
 */
public class LStringSelectAttributeTest {

    @Mock
    private DeviceBase mockDevice;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockDevice.getId()).thenReturn("testDeviceId");
    }

    // ========== Bound mode tests ==========

    @Test
    public void boundModeDelegatesToPhysicalAttr() {
        AttributeBase<String> phyAttr = createStringSelectAttr("test_mode",
            Arrays.asList("Normal", "Maintenance"), "Normal");
        LStringSelectAttribute logicAttr = new LStringSelectAttribute(phyAttr,
            Arrays.asList("Normal", "Maintenance", "Calibration"), Collections.emptyMap());
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
            Arrays.asList("Normal", "Maintenance", "Calibration"), Collections.emptyMap());
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
        logicAttr.setDevice(mockDevice);

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
            Arrays.asList("Normal", "Maintenance"), Collections.emptyMap());
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

    // ========== valueMapping tests ==========

    @Test
    public void getValueMappingReturnsDefensiveCopy() {
        Map<String, String> mapping = new HashMap<>();
        mapping.put("cooling", "1");
        mapping.put("heating", "2");
        TestStringSelectAttr phyAttr = createStringSelectAttr("test_mode",
            Arrays.asList("cooling", "heating"), "1");
        LStringSelectAttribute logicAttr = new LStringSelectAttribute(phyAttr,
            Arrays.asList("cooling", "heating"), mapping);

        Map<String, String> retrieved = logicAttr.getValueMapping();
        assertEquals(2, retrieved.size());
        assertEquals("1", retrieved.get("cooling"));

        // 修改返回的 map 不应影响内部状态
        retrieved.put("cooling", "99");
        assertEquals("1", logicAttr.getValueMapping().get("cooling"));
    }

    @Test
    public void valueMappingTranslatesPhysicalToStandardOnUpdate() {
        // 物理值 "1" 应映射到标准 key "cooling"
        Map<String, String> mapping = new HashMap<>();
        mapping.put("cooling", "1");
        mapping.put("heating", "2");
        TestStringSelectAttr phyAttr = createStringSelectAttr("fan_mode",
            Arrays.asList("1", "2"), "1");

        LStringSelectAttribute logicAttr = new LStringSelectAttribute(phyAttr,
            Arrays.asList("cooling", "heating"), mapping);
        logicAttr.initAttributeID("fan_status");
        logicAttr.initValueChangeable(true);

        // 物理值为 "1" → 逻辑值应为 "cooling"
        logicAttr.updateBindAttrValue(phyAttr);
        assertEquals("cooling", logicAttr.getValue());
    }

    @Test
    public void valueMappingDirectMatchFallsBackToFindOption() {
        // 空映射 + 物理值直接等于选项字符串 → 应通过 findOption 兜底匹配
        TestStringSelectAttr phyAttr = createStringSelectAttr("test_mode",
            Arrays.asList("Normal", "Maintenance"), "Maintenance");

        LStringSelectAttribute logicAttr = new LStringSelectAttribute(phyAttr,
            Arrays.asList("Normal", "Maintenance"), Collections.emptyMap());
        logicAttr.initAttributeID("manual_status");
        logicAttr.initValueChangeable(true);

        logicAttr.updateBindAttrValue(phyAttr);
        assertEquals("Maintenance", logicAttr.getValue());
    }

    @Test(expected = IllegalStateException.class)
    public void updateBindAttrValueThrowsWhenPhysicalValueNotMapped() {
        // 物理值 "99" 不在映射中，也不是选项字符串 → 应抛异常
        Map<String, String> mapping = new HashMap<>();
        mapping.put("cooling", "1");
        mapping.put("heating", "2");
        TestStringSelectAttr phyAttr = createStringSelectAttr("fan_mode",
            Arrays.asList("1", "2"), "99");

        LStringSelectAttribute logicAttr = new LStringSelectAttribute(phyAttr,
            Arrays.asList("cooling", "heating"), mapping);
        logicAttr.initAttributeID("fan_status");

        logicAttr.updateBindAttrValue(phyAttr);
    }

    @Test
    public void setDisplayValueTranslatesStandardToPhysical() {
        // 标准 key "cooling" 应转换为物理值 "1" 写入 bindAttr
        Map<String, String> mapping = new HashMap<>();
        mapping.put("cooling", "1");
        mapping.put("heating", "2");
        TestStringSelectAttr phyAttr = createStringSelectAttr("fan_mode",
            Arrays.asList("1", "2"), "2");

        LStringSelectAttribute logicAttr = new LStringSelectAttribute(phyAttr,
            Arrays.asList("cooling", "heating"), mapping);
        logicAttr.initAttributeID("fan_status");
        logicAttr.initValueChangeable(true);

        logicAttr.setDisplayValue("cooling", null).join();
        assertEquals("1", phyAttr.getLastSetDisplayValue());
    }

    @Test(expected = IllegalArgumentException.class)
    public void setDisplayValueThrowsWhenStandardKeyNotMapped() {
        // "unknown_key" 不在映射中 → 应抛异常
        Map<String, String> mapping = new HashMap<>();
        mapping.put("cooling", "1");
        TestStringSelectAttr phyAttr = createStringSelectAttr("fan_mode",
            Arrays.asList("1"), "1");

        LStringSelectAttribute logicAttr = new LStringSelectAttribute(phyAttr,
            Arrays.asList("cooling"), mapping);
        logicAttr.initAttributeID("fan_status");

        logicAttr.setDisplayValue("unknown_key", null).join();
    }

    // ========== Local test helpers ==========

    private static TestStringSelectAttr createStringSelectAttr(String attrId,
            List<String> options, String initialValue) {
        return new TestStringSelectAttr(attrId, options, initialValue);
    }

    private static class TestStringSelectAttr extends AttributeBase<String> {
        private final List<String> options;
        private String value;
        private String lastSetDisplayValue;

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

        @Override
        public CompletableFuture<Boolean> setDisplayValue(String newDisplayValue, UnitInfo fromUnit) {
            this.lastSetDisplayValue = newDisplayValue;
            this.value = newDisplayValue;
            return CompletableFuture.completedFuture(true);
        }

        public void setTestValue(String val) { this.value = val; }
        public String getLastSetDisplayValue() { return lastSetDisplayValue; }
    }
}
