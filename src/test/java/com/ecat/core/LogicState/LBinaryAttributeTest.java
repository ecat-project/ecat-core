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

import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * LBinaryAttribute 单元测试
 *
 * @author coffee
 */
public class LBinaryAttributeTest {

    @Mock
    private DeviceBase mockDevice;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockDevice.getId()).thenReturn("testDeviceId");
    }

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
        // 物理属性虽绑定设备但从未 updateValue → getState() 为 null。
        // 生产侧 LBinaryAttribute.updateBindAttrValue 读 bindAttr.getState() 为 null → rawValue=null → 提前返回不更新。
        AttributeBase<?> phyAttr = createMockAttr("online", AttributeClass.STATUS);
        bindDevice(phyAttr);
        LBinaryAttribute logicAttr = new LBinaryAttribute(phyAttr);
        bindDevice(logicAttr);

        // Default implementation: sourceState 透传新签名，物理属性无值时无操作
        logicAttr.updateBindAttrValue(phyAttr.getState());
        // 无操作 → 逻辑属性从未 updateValue → getState() 本身为 null
        assertNull(logicAttr.getState());
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
    public void standaloneModeSetDisplayValueSetsLocally() {
        TestStandaloneBinary logicAttr = new TestStandaloneBinary("alarm_status", AttributeClass.ALARM_STATUS);
        logicAttr.initValueChangeable(true);
        logicAttr.setDevice(mockDevice);

        Boolean result = logicAttr.setDisplayValue("on", null).join();
        assertTrue(result);
        assertTrue(logicAttr.isOn());
    }

    @Test
    public void standaloneModeTurnOnOff() {
        TestStandaloneBinary logicAttr = new TestStandaloneBinary("alarm_status", AttributeClass.ALARM_STATUS);
        bindDevice(logicAttr);

        logicAttr.turnOn();
        assertTrue(logicAttr.isOn());
        assertTrue((Boolean) logicAttr.getState().getValue());

        logicAttr.turnOff();
        assertTrue(logicAttr.isOff());
        assertFalse((Boolean) logicAttr.getState().getValue());
    }

    // ========== fromUnit 透传验证 ==========

    @Test
    public void boundModeSetDisplayValue_passesFromUnitToBindAttr() {
        // 验证修复：setDisplayValue("on", fromUnit) 应将 fromUnit 透传到 bindAttr
        UnitCapturingAttr captureAttr = new UnitCapturingAttr("test", AttributeClass.STATUS);
        LBinaryAttribute logicAttr = new LBinaryAttribute(captureAttr);
        logicAttr.initValueChangeable(true);

        UnitInfo testUnit = new UnitInfo() {
            @Override public String getName() { return "test"; }
            @Override public Double getRatio() { return 1.0; }
            @Override public String getDisplayName() { return "test"; }
        };

        logicAttr.setDisplayValue("on", testUnit).join();

        // 验证 bindAttr.setDisplayValue 收到的 fromUnit 是传入的 testUnit，而非 bindAttr.getNativeUnit()
        assertNotNull("fromUnit should be passed to bindAttr", captureAttr.capturedFromUnit);
        assertSame("fromUnit should match the caller's fromUnit, not bindAttr.getNativeUnit()",
                testUnit, captureAttr.capturedFromUnit);
    }

    // ========== Test helpers ==========

    /**
     * Bind a mock device (non-null id) so that {@link AttributeBase#getState()} returns a non-null
     * AttrState snapshot after {@code updateValue} is called. Required by the state-sealing refactor
     * where {@code getState()} returns null until a device is bound.
     */
    private static void bindDevice(AttributeBase<?> attr) {
        DeviceBase mockDevice = mock(DeviceBase.class);
        when(mockDevice.getId()).thenReturn("testDevice");
        attr.setDevice(mockDevice);
    }

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

    /** 捕获 setDisplayValue 收到的 fromUnit 参数 */
    private static class UnitCapturingAttr extends AttributeBase<String> {
        UnitInfo capturedFromUnit;

        UnitCapturingAttr(String attributeID, AttributeClass attrClass) {
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
            this.capturedFromUnit = fromUnit;
            return CompletableFuture.completedFuture(true);
        }
    }
}
