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

import com.ecat.core.Device.DeviceBase;
import com.ecat.core.State.AttributeBase;
import com.ecat.core.State.AttributeClass;
import com.ecat.core.State.AttributeStatus;
import com.ecat.core.State.AttributeType;
import com.ecat.core.State.UnitInfo;
import org.junit.Test;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;

/**
 * LRunningStatusAttribute 单元测试
 *
 * @author coffee
 */
public class LRunningStatusAttributeTest {

    // ========== 核心逻辑测试 ==========

    @Test
    public void valueSetToNormalWhenPhyStatusIsNormal() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        phyAttr.setTestStatus(AttributeStatus.NORMAL);
        bindDevice(phyAttr);
        phyAttr.updateValue(null, AttributeStatus.NORMAL);

        LRunningStatusAttribute logicAttr = new LRunningStatusAttribute(phyAttr);
        bindDevice(logicAttr);

        logicAttr.updateBindAttrValue(phyAttr.getState());

        assertEquals("Normal", logicAttr.getState().getValue());
        assertEquals(AttributeStatus.NORMAL, logicAttr.getState().getStatus());
    }

    @Test
    public void valueSetToAlarmWhenPhyStatusIsAlarm() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        phyAttr.setTestStatus(AttributeStatus.ALARM);
        bindDevice(phyAttr);
        phyAttr.updateValue(null, AttributeStatus.ALARM);

        LRunningStatusAttribute logicAttr = new LRunningStatusAttribute(phyAttr);
        bindDevice(logicAttr);

        logicAttr.updateBindAttrValue(phyAttr.getState());

        assertEquals("Alarm", logicAttr.getState().getValue());
        assertEquals(AttributeStatus.ALARM, logicAttr.getState().getStatus());
    }

    @Test
    public void valueSetToMaintenanceWhenPhyStatusIsMaintenance() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        phyAttr.setTestStatus(AttributeStatus.MAINTENANCE);
        bindDevice(phyAttr);
        phyAttr.updateValue(null, AttributeStatus.MAINTENANCE);

        LRunningStatusAttribute logicAttr = new LRunningStatusAttribute(phyAttr);
        bindDevice(logicAttr);

        logicAttr.updateBindAttrValue(phyAttr.getState());

        assertEquals("Maintenance", logicAttr.getState().getValue());
        assertEquals(AttributeStatus.MAINTENANCE, logicAttr.getState().getStatus());
    }

    @Test
    public void valueSetToCalibrationWhenPhyStatusIsCalibration() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        phyAttr.setTestStatus(AttributeStatus.CALIBRATION);
        bindDevice(phyAttr);
        phyAttr.updateValue(null, AttributeStatus.CALIBRATION);

        LRunningStatusAttribute logicAttr = new LRunningStatusAttribute(phyAttr);
        bindDevice(logicAttr);

        logicAttr.updateBindAttrValue(phyAttr.getState());

        assertEquals("Calibration", logicAttr.getState().getValue());
        assertEquals(AttributeStatus.CALIBRATION, logicAttr.getState().getStatus());
    }

    @Test
    public void valueSetToMalfunctionWhenPhyStatusIsMalfunction() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        phyAttr.setTestStatus(AttributeStatus.MALFUNCTION);
        bindDevice(phyAttr);
        phyAttr.updateValue(null, AttributeStatus.MALFUNCTION);

        LRunningStatusAttribute logicAttr = new LRunningStatusAttribute(phyAttr);
        bindDevice(logicAttr);

        logicAttr.updateBindAttrValue(phyAttr.getState());

        assertEquals("Malfunction", logicAttr.getState().getValue());
        assertEquals(AttributeStatus.MALFUNCTION, logicAttr.getState().getStatus());
    }

    // ========== 边界条件测试 ==========

    @Test
    public void noUpdateWhenPhyStatusIsNull() {
        // 物理属性从未 updateValue → getState() 为 null（等价于原 getStatus()==null 的「无状态」语义）。
        // 不能用 updateValue(null, null) 驱动：buildState() 要求 status 非 null，会抛 IllegalArgument。
        // 生产侧 LRunningStatusAttribute.updateBindAttrValue 已对 getState()==null 做空守卫 → 提前返回不更新。
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        phyAttr.setTestStatus(null);
        bindDevice(phyAttr);

        LRunningStatusAttribute logicAttr = new LRunningStatusAttribute(phyAttr);
        bindDevice(logicAttr);

        logicAttr.updateBindAttrValue(phyAttr.getState());

        // 物理属性无状态 → 逻辑属性从未更新 → getState() 本身为 null
        assertNull(logicAttr.getState());
    }

    @Test
    public void noUpdateWhenPhyStatusIsEmpty() {
        // 物理属性带 EMPTY 状态：EMPTY 非 null，buildState() 可正常构建；
        // 生产侧 LRunningStatusAttribute.updateBindAttrValue 见 status==EMPTY → 提前返回不更新。
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        phyAttr.setTestStatus(AttributeStatus.EMPTY);
        bindDevice(phyAttr);
        phyAttr.updateValue(null, AttributeStatus.EMPTY);

        LRunningStatusAttribute logicAttr = new LRunningStatusAttribute(phyAttr);
        bindDevice(logicAttr);

        logicAttr.updateBindAttrValue(phyAttr.getState());

        // 生产侧见 EMPTY 跳过更新 → 逻辑属性从未 updateValue → getState() 本身为 null
        assertNull(logicAttr.getState());
    }

    @Test
    public void statusTransitionNormalToAlarm() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        phyAttr.setTestStatus(AttributeStatus.NORMAL);
        bindDevice(phyAttr);
        phyAttr.updateValue(null, AttributeStatus.NORMAL);

        LRunningStatusAttribute logicAttr = new LRunningStatusAttribute(phyAttr);
        bindDevice(logicAttr);

        logicAttr.updateBindAttrValue(phyAttr.getState());
        assertEquals("Normal", logicAttr.getState().getValue());

        phyAttr.setTestStatus(AttributeStatus.ALARM);
        phyAttr.updateValue(null, AttributeStatus.ALARM);
        logicAttr.updateBindAttrValue(phyAttr.getState());
        assertEquals("Alarm", logicAttr.getState().getValue());
    }

    @Test
    public void statusTransitionAlarmToNormal() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        phyAttr.setTestStatus(AttributeStatus.ALARM);
        bindDevice(phyAttr);
        phyAttr.updateValue(null, AttributeStatus.ALARM);

        LRunningStatusAttribute logicAttr = new LRunningStatusAttribute(phyAttr);
        bindDevice(logicAttr);

        logicAttr.updateBindAttrValue(phyAttr.getState());
        assertEquals("Alarm", logicAttr.getState().getValue());

        phyAttr.setTestStatus(AttributeStatus.NORMAL);
        phyAttr.updateValue(null, AttributeStatus.NORMAL);
        logicAttr.updateBindAttrValue(phyAttr.getState());
        assertEquals("Normal", logicAttr.getState().getValue());
    }

    // ========== 选项列表测试 ==========

    @Test
    public void runningStatusOptionsExcludesEmpty() {
        List<String> options = LRunningStatusAttribute.getRunningStatusOptions();
        assertFalse(options.contains("Empty"));
    }

    @Test
    public void runningStatusOptionsContainsCommonStatuses() {
        List<String> options = LRunningStatusAttribute.getRunningStatusOptions();
        assertTrue(options.contains("Normal"));
        assertTrue(options.contains("Alarm"));
        assertTrue(options.contains("Maintenance"));
        assertTrue(options.contains("Calibration"));
        assertTrue(options.contains("Malfunction"));
        assertTrue(options.contains("Waiting"));
    }

    @Test
    public void runningStatusOptionsNonEmpty() {
        List<String> options = LRunningStatusAttribute.getRunningStatusOptions();
        assertFalse(options.isEmpty());
    }

    // ========== 继承与接口测试 ==========

    @Test
    public void extendsLStringSelectAttribute() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        LRunningStatusAttribute logicAttr = new LRunningStatusAttribute(phyAttr);
        assertTrue(logicAttr instanceof LStringSelectAttribute);
    }

    @Test
    public void implementsILogicAttribute() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        LRunningStatusAttribute logicAttr = new LRunningStatusAttribute(phyAttr);
        assertTrue(logicAttr instanceof ILogicAttribute);
    }

    @Test
    public void boundModeHasCorrectBindAttr() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        LRunningStatusAttribute logicAttr = new LRunningStatusAttribute(phyAttr);
        assertEquals(1, logicAttr.getBindedAttrs().size());
        assertEquals(phyAttr, logicAttr.getBindAttr());
        assertFalse(logicAttr.isStandalone());
    }

    // ========== Mock 辅助类 ==========

    private static TestPhyAttr createMockAttr(String attrId, AttributeClass attrClass) {
        return new TestPhyAttr(attrId, attrClass);
    }

    /**
     * 绑定 mock 设备，使 {@link AttributeBase#getState()} 在 updateValue 后返回非 null 的 AttrState。
     * getState() 要求设备已绑定且 getId() 非 null，否则恒为 null。
     */
    private static void bindDevice(AttributeBase<?> attr) {
        DeviceBase mockDevice = org.mockito.Mockito.mock(DeviceBase.class);
        org.mockito.Mockito.when(mockDevice.getId()).thenReturn("testDevice");
        attr.setDevice(mockDevice);
    }

    private static class TestPhyAttr extends AttributeBase<String> {
        private AttributeStatus testStatus = AttributeStatus.EMPTY;

        TestPhyAttr(String attributeID, AttributeClass attrClass) {
            super(attributeID, attrClass, null, null, 0, false, false);
        }

        void setTestStatus(AttributeStatus status) {
            this.testStatus = status;
        }

        // getStatus()/getValue() 已降为 protected 且不再属于接口契约；
        // 此处仅作为测试桩的内部状态读取，不再标注 @Override。
        public AttributeStatus getStatus() { return testStatus; }
        @Override public String getDisplayValue(UnitInfo toUnit) { return null; }
        @Override protected String convertFromUnitImp(String value, UnitInfo fromUnit) { return value; }
        @Override public Double convertValueToUnit(Double value, UnitInfo fromUnit, UnitInfo toUnit) { return value; }
        @Override public com.ecat.core.Utils.DynamicConfig.ConfigDefinition getValueDefinition() { return null; }
        @Override protected com.ecat.core.I18n.I18nKeyPath getI18nPrefixPath() {
            return new com.ecat.core.I18n.I18nKeyPath("test.", "test");
        }
        @Override public AttributeType getAttributeType() { return AttributeType.NUMERIC; }
        @Override
        protected CompletableFuture<Boolean> setDisplayValueImp(String value, UnitInfo fromUnit) {
            return CompletableFuture.completedFuture(true);
        }
    }
}
