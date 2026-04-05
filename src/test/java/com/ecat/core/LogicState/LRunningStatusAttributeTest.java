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

        LRunningStatusAttribute logicAttr = new LRunningStatusAttribute(phyAttr);
        logicAttr.updateBindAttrValue(phyAttr);

        assertEquals("Normal", logicAttr.getValue());
        assertEquals(AttributeStatus.NORMAL, logicAttr.getStatus());
    }

    @Test
    public void valueSetToAlarmWhenPhyStatusIsAlarm() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        phyAttr.setTestStatus(AttributeStatus.ALARM);

        LRunningStatusAttribute logicAttr = new LRunningStatusAttribute(phyAttr);
        logicAttr.updateBindAttrValue(phyAttr);

        assertEquals("Alarm", logicAttr.getValue());
        assertEquals(AttributeStatus.ALARM, logicAttr.getStatus());
    }

    @Test
    public void valueSetToMaintenanceWhenPhyStatusIsMaintenance() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        phyAttr.setTestStatus(AttributeStatus.MAINTENANCE);

        LRunningStatusAttribute logicAttr = new LRunningStatusAttribute(phyAttr);
        logicAttr.updateBindAttrValue(phyAttr);

        assertEquals("Maintenance", logicAttr.getValue());
        assertEquals(AttributeStatus.MAINTENANCE, logicAttr.getStatus());
    }

    @Test
    public void valueSetToCalibrationWhenPhyStatusIsCalibration() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        phyAttr.setTestStatus(AttributeStatus.CALIBRATION);

        LRunningStatusAttribute logicAttr = new LRunningStatusAttribute(phyAttr);
        logicAttr.updateBindAttrValue(phyAttr);

        assertEquals("Calibration", logicAttr.getValue());
        assertEquals(AttributeStatus.CALIBRATION, logicAttr.getStatus());
    }

    @Test
    public void valueSetToMalfunctionWhenPhyStatusIsMalfunction() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        phyAttr.setTestStatus(AttributeStatus.MALFUNCTION);

        LRunningStatusAttribute logicAttr = new LRunningStatusAttribute(phyAttr);
        logicAttr.updateBindAttrValue(phyAttr);

        assertEquals("Malfunction", logicAttr.getValue());
        assertEquals(AttributeStatus.MALFUNCTION, logicAttr.getStatus());
    }

    // ========== 边界条件测试 ==========

    @Test
    public void noUpdateWhenPhyStatusIsNull() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        phyAttr.setTestStatus(null);

        LRunningStatusAttribute logicAttr = new LRunningStatusAttribute(phyAttr);
        logicAttr.updateBindAttrValue(phyAttr);

        assertNull(logicAttr.getValue());
    }

    @Test
    public void noUpdateWhenPhyStatusIsEmpty() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        phyAttr.setTestStatus(AttributeStatus.EMPTY);

        LRunningStatusAttribute logicAttr = new LRunningStatusAttribute(phyAttr);
        logicAttr.updateBindAttrValue(phyAttr);

        assertNull(logicAttr.getValue());
    }

    @Test
    public void statusTransitionNormalToAlarm() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        phyAttr.setTestStatus(AttributeStatus.NORMAL);

        LRunningStatusAttribute logicAttr = new LRunningStatusAttribute(phyAttr);
        logicAttr.updateBindAttrValue(phyAttr);
        assertEquals("Normal", logicAttr.getValue());

        phyAttr.setTestStatus(AttributeStatus.ALARM);
        logicAttr.updateBindAttrValue(phyAttr);
        assertEquals("Alarm", logicAttr.getValue());
    }

    @Test
    public void statusTransitionAlarmToNormal() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        phyAttr.setTestStatus(AttributeStatus.ALARM);

        LRunningStatusAttribute logicAttr = new LRunningStatusAttribute(phyAttr);
        logicAttr.updateBindAttrValue(phyAttr);
        assertEquals("Alarm", logicAttr.getValue());

        phyAttr.setTestStatus(AttributeStatus.NORMAL);
        logicAttr.updateBindAttrValue(phyAttr);
        assertEquals("Normal", logicAttr.getValue());
    }

    // ========== 选项列表测试 ==========

    @Test
    public void runningStatusOptionsExcludesEmptyAndOther() {
        List<String> options = LRunningStatusAttribute.getRunningStatusOptions();
        assertFalse(options.contains("Empty"));
        assertFalse(options.contains("Other"));
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

    private static class TestPhyAttr extends AttributeBase<String> {
        private AttributeStatus testStatus = AttributeStatus.EMPTY;

        TestPhyAttr(String attributeID, AttributeClass attrClass) {
            super(attributeID, attrClass, null, null, 0, false, false);
        }

        void setTestStatus(AttributeStatus status) {
            this.testStatus = status;
        }

        @Override public AttributeStatus getStatus() { return testStatus; }
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
