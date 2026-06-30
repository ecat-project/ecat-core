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
import com.ecat.core.State.AttrState;
import com.ecat.core.State.UnitInfo;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;

/**
 * LAlarmStatusAttribute 单元测试
 *
 * <p>状态封装修订后：{@code AttributeBase.getValue()/getStatus()} 降为 protected，
 * 跨包测试改为读不可变 {@link AttrState}（经 {@code getState()}）。
 * 因 {@code getState()} 仅在属性绑定设备且 {@code updateValue} 调用后非空，
 * 故物理属性与被断言的逻辑属性均需绑定 mock 设备（{@code getId()} 非 null）。
 *
 * @author coffee
 */
public class LAlarmStatusAttributeTest {

    // ========== 核心逻辑测试 ==========

    @Test
    public void alarmStatusSetToTrueWhenPhyStatusIsAlarm() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        phyAttr.setTestStatus(AttributeStatus.ALARM);

        LAlarmStatusAttribute logicAttr = new LAlarmStatusAttribute(phyAttr);
        bindDevice(logicAttr);
        logicAttr.updateBindAttrValue(phyAttr.getState());

        assertEquals("alarm", logicAttr.getState().getValue());
        assertEquals(AttributeStatus.ALARM, logicAttr.getState().getStatus());
    }

    @Test
    public void alarmStatusSetToFalseWhenPhyStatusIsNormal() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        phyAttr.setTestStatus(AttributeStatus.NORMAL);

        LAlarmStatusAttribute logicAttr = new LAlarmStatusAttribute(phyAttr);
        bindDevice(logicAttr);
        logicAttr.updateBindAttrValue(phyAttr.getState());

        assertEquals("normal", logicAttr.getState().getValue());
        assertEquals(AttributeStatus.NORMAL, logicAttr.getState().getStatus());
    }

    @Test
    public void alarmStatusSetToFalseWhenPhyStatusIsMaintenance() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        phyAttr.setTestStatus(AttributeStatus.MAINTENANCE);

        LAlarmStatusAttribute logicAttr = new LAlarmStatusAttribute(phyAttr);
        bindDevice(logicAttr);
        logicAttr.updateBindAttrValue(phyAttr.getState());

        assertEquals("normal", logicAttr.getState().getValue());
        assertEquals(AttributeStatus.MAINTENANCE, logicAttr.getState().getStatus());
    }

    @Test
    public void alarmStatusTransitionsFromNormalToAlarm() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        phyAttr.setTestStatus(AttributeStatus.NORMAL);

        LAlarmStatusAttribute logicAttr = new LAlarmStatusAttribute(phyAttr);
        bindDevice(logicAttr);
        logicAttr.updateBindAttrValue(phyAttr.getState());
        assertEquals("normal", logicAttr.getState().getValue());

        phyAttr.setTestStatus(AttributeStatus.ALARM);
        logicAttr.updateBindAttrValue(phyAttr.getState());
        assertEquals("alarm", logicAttr.getState().getValue());
    }

    @Test
    public void alarmStatusTransitionsFromAlarmToNormal() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        phyAttr.setTestStatus(AttributeStatus.ALARM);

        LAlarmStatusAttribute logicAttr = new LAlarmStatusAttribute(phyAttr);
        bindDevice(logicAttr);
        logicAttr.updateBindAttrValue(phyAttr.getState());
        assertEquals("alarm", logicAttr.getState().getValue());

        phyAttr.setTestStatus(AttributeStatus.NORMAL);
        logicAttr.updateBindAttrValue(phyAttr.getState());
        assertEquals("normal", logicAttr.getState().getValue());
    }

    @Test
    public void alarmStatusWithMalfunctionStatus() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        phyAttr.setTestStatus(AttributeStatus.MALFUNCTION);

        LAlarmStatusAttribute logicAttr = new LAlarmStatusAttribute(phyAttr);
        bindDevice(logicAttr);
        logicAttr.updateBindAttrValue(phyAttr.getState());

        assertEquals("normal", logicAttr.getState().getValue());
        assertEquals(AttributeStatus.MALFUNCTION, logicAttr.getState().getStatus());
    }

    // ========== 边界条件测试 ==========

    @Test
    public void statusPropagatedWhenAlarm() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        phyAttr.setTestStatus(AttributeStatus.ALARM);

        LAlarmStatusAttribute logicAttr = new LAlarmStatusAttribute(phyAttr);
        bindDevice(logicAttr);
        logicAttr.updateBindAttrValue(phyAttr.getState());

        assertEquals(AttributeStatus.ALARM, logicAttr.getState().getStatus());
    }

    @Test
    public void statusPropagatedWhenInsufficient() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        phyAttr.setTestStatus(AttributeStatus.INSUFFICIENT);

        LAlarmStatusAttribute logicAttr = new LAlarmStatusAttribute(phyAttr);
        bindDevice(logicAttr);
        logicAttr.updateBindAttrValue(phyAttr.getState());

        assertEquals("normal", logicAttr.getState().getValue());
        assertEquals(AttributeStatus.INSUFFICIENT, logicAttr.getState().getStatus());
    }

    // ========== 继承与接口测试 ==========

    @Test
    public void extendsLStringSelectAttribute() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        LAlarmStatusAttribute logicAttr = new LAlarmStatusAttribute(phyAttr);
        assertTrue(logicAttr instanceof LStringSelectAttribute);
    }

    @Test
    public void implementsILogicAttribute() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        LAlarmStatusAttribute logicAttr = new LAlarmStatusAttribute(phyAttr);
        assertTrue(logicAttr instanceof ILogicAttribute);
    }

    @Test
    public void boundModeHasCorrectBindAttr() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        LAlarmStatusAttribute logicAttr = new LAlarmStatusAttribute(phyAttr);
        assertEquals(1, logicAttr.getBindedAttrs().size());
        assertEquals(phyAttr, logicAttr.getBindAttr());
        assertFalse(logicAttr.isStandalone());
    }

    // ========== Mock 辅助类 ==========

    private static TestPhyAttr createMockAttr(String attrId, AttributeClass attrClass) {
        TestPhyAttr attr = new TestPhyAttr(attrId, attrClass);
        // 绑定 mock 设备：getState() 依赖 device.getId() 非 null 才会在 updateValue 时构建 lastState。
        bindDevice(attr);
        return attr;
    }

    /**
     * 为属性绑定 mock 设备（getId 返回非 null），使 {@link AttributeBase#getState()} 在
     * updateValue 调用后非空。LAlarmStatusAttribute 运行时经 bindAttr.getState().getStatus() 取值，
     * 故物理属性必须绑定设备；逻辑属性若需断言其 getState() 也需绑定。
     */
    private static void bindDevice(AttributeBase<?> attr) {
        DeviceBase mockDevice = Mockito.mock(DeviceBase.class);
        Mockito.when(mockDevice.getId()).thenReturn("testDevice");
        attr.setDevice(mockDevice);
    }

    private static class TestPhyAttr extends AttributeBase<String> {

        TestPhyAttr(String attributeID, AttributeClass attrClass) {
            super(attributeID, attrClass, null, null, 0, false, false);
        }

        /**
         * 设置物理属性状态并通过 updateValue 落入不可变 AttrState（lastState），
         * 模拟设备上报新数据并刷新状态——与运行时一致。
         */
        void setTestStatus(AttributeStatus status) {
            updateValue("x", status);
        }

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
