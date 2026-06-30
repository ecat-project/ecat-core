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
import org.mockito.Mockito;
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

    /**
     * 绑定一个返回固定 id 的 mock 设备到属性上，使其后的 updateValue 调用能构建非 null 的 AttrState
     * （AttributeBase 仅在已绑定设备且 getId() 非 null 时构建 lastState）。
     */
    private static void bindDevice(AttributeBase<?> attr) {
        DeviceBase mock = Mockito.mock(DeviceBase.class);
        Mockito.when(mock.getId()).thenReturn("testDevice");
        attr.setDevice(mock);
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

        // 未触发任何更新前，逻辑属性尚无状态（getState() 为 null 即等价于"无值"）
        assertNull(logicAttr.getState());

        phyAttr.setTestValue("Maintenance");
        bindDevice(logicAttr);
        logicAttr.updateBindAttrValue(phyAttr.getState());

        assertEquals("Maintenance", (String) logicAttr.getState().getValue());
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
        bindDevice(logicAttr);

        logicAttr.updateValue("Maintenance");
        assertEquals("Maintenance", (String) logicAttr.getState().getValue());
    }

    @Test
    public void standaloneModeUpdateBindAttrValueIsNoOp() {
        LStringSelectAttribute logicAttr = LStringSelectAttribute.standalone(
            "manual_status", AttributeClass.MODE, Arrays.asList("Normal", "Maintenance"));
        logicAttr.initAttributeID("manual_status");
        logicAttr.initValueChangeable(true);

        logicAttr.updateBindAttrValue(null);
        assertNull(logicAttr.getState());
    }

    @Test
    public void standaloneModeSetDisplayValueSelectsLocally() {
        LStringSelectAttribute logicAttr = LStringSelectAttribute.standalone(
            "manual_status", AttributeClass.MODE, Arrays.asList("Normal", "Maintenance"));
        logicAttr.initAttributeID("manual_status");
        logicAttr.initValueChangeable(true);
        logicAttr.setDevice(mockDevice);

        logicAttr.setDisplayValue("Maintenance", null).join();
        assertEquals("Maintenance", (String) logicAttr.getState().getValue());
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
        bindDevice(logicAttr);
        logicAttr.updateBindAttrValue(phyAttr.getState());
        assertEquals("cooling", (String) logicAttr.getState().getValue());
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

        bindDevice(logicAttr);
        logicAttr.updateBindAttrValue(phyAttr.getState());
        assertEquals("Maintenance", (String) logicAttr.getState().getValue());
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

        logicAttr.updateBindAttrValue(phyAttr.getState());
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

    // ========== fromUnit 透传验证 ==========

    @Test
    public void setDisplayValue_passesFromUnitToBindAttr() {
        // 验证修复：setDisplayValue 的 fromUnit 应透传到 bindAttr，而非用 bindAttr.getNativeUnit()
        UnitInfo testUnit = new UnitInfo() {
            @Override public String getName() { return "test"; }
            @Override public Double getRatio() { return 1.0; }
            @Override public String getDisplayName() { return "test"; }
        };

        Map<String, String> mapping = new HashMap<>();
        mapping.put("cooling", "1");
        mapping.put("heating", "2");

        FromUnitCapturingAttr captureAttr = new FromUnitCapturingAttr(
                "fan_mode", Arrays.asList("1", "2"), "1");
        LStringSelectAttribute logicAttr = new LStringSelectAttribute(captureAttr,
                Arrays.asList("cooling", "heating"), mapping);
        logicAttr.initAttributeID("fan_status");
        logicAttr.initValueChangeable(true);

        logicAttr.setDisplayValue("cooling", testUnit).join();

        // 验证 bindAttr.setDisplayValue 收到的 fromUnit 是传入的 testUnit
        assertNotNull("fromUnit should be passed to bindAttr", captureAttr.capturedFromUnit);
        assertSame("fromUnit should match caller's fromUnit, not bindAttr.getNativeUnit()",
                testUnit, captureAttr.capturedFromUnit);
        assertEquals("1", captureAttr.capturedValue);
    }

    // ========== Local test helpers ==========

    private static TestStringSelectAttr createStringSelectAttr(String attrId,
            List<String> options, String initialValue) {
        return new TestStringSelectAttr(attrId, options, initialValue);
    }

    private static class TestStringSelectAttr extends AttributeBase<String> {
        private String value;
        private String lastSetDisplayValue;

        TestStringSelectAttr(String attributeID, List<String> options, String initialValue) {
            super(attributeID, AttributeClass.MODE, null, null, 0, false, false);
            this.value = initialValue;
            // 绑定 mock 设备并以初值构建 AttrState，使 getState() 在 updateBindAttrValue(AttrState) 场景下非 null
            bindDevice(this);
            updateValue(initialValue);
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

        public void setTestValue(String val) {
            this.value = val;
            // 重建 AttrState，使后续 getState() 反映新值（updateValue 在设备已绑定时构建 lastState）
            updateValue(val);
        }
        public String getLastSetDisplayValue() { return lastSetDisplayValue; }
    }

    /** 捕获 setDisplayValue 收到的 value 和 fromUnit 参数 */
    private static class FromUnitCapturingAttr extends AttributeBase<String> {
        private String value;
        String capturedValue;
        UnitInfo capturedFromUnit;

        FromUnitCapturingAttr(String attributeID, List<String> options, String initialValue) {
            super(attributeID, AttributeClass.MODE, null, null, 0, false, false);
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
            this.capturedValue = newDisplayValue;
            this.capturedFromUnit = fromUnit;
            this.value = newDisplayValue;
            return CompletableFuture.completedFuture(true);
        }
    }
}
