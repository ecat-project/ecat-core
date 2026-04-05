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

import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;

/**
 * LAlarmStatusAttribute 单元测试
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
        logicAttr.updateBindAttrValue(phyAttr);

        assertTrue(logicAttr.getValue());
        assertEquals(AttributeStatus.ALARM, logicAttr.getStatus());
    }

    @Test
    public void alarmStatusSetToFalseWhenPhyStatusIsNormal() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        phyAttr.setTestStatus(AttributeStatus.NORMAL);

        LAlarmStatusAttribute logicAttr = new LAlarmStatusAttribute(phyAttr);
        logicAttr.updateBindAttrValue(phyAttr);

        assertFalse(logicAttr.getValue());
        assertEquals(AttributeStatus.NORMAL, logicAttr.getStatus());
    }

    @Test
    public void alarmStatusSetToFalseWhenPhyStatusIsMaintenance() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        phyAttr.setTestStatus(AttributeStatus.MAINTENANCE);

        LAlarmStatusAttribute logicAttr = new LAlarmStatusAttribute(phyAttr);
        logicAttr.updateBindAttrValue(phyAttr);

        assertFalse(logicAttr.getValue());
        assertEquals(AttributeStatus.MAINTENANCE, logicAttr.getStatus());
    }

    @Test
    public void alarmStatusTransitionsFromNormalToAlarm() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        phyAttr.setTestStatus(AttributeStatus.NORMAL);

        LAlarmStatusAttribute logicAttr = new LAlarmStatusAttribute(phyAttr);
        logicAttr.updateBindAttrValue(phyAttr);
        assertFalse(logicAttr.getValue());

        phyAttr.setTestStatus(AttributeStatus.ALARM);
        logicAttr.updateBindAttrValue(phyAttr);
        assertTrue(logicAttr.getValue());
    }

    @Test
    public void alarmStatusTransitionsFromAlarmToNormal() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        phyAttr.setTestStatus(AttributeStatus.ALARM);

        LAlarmStatusAttribute logicAttr = new LAlarmStatusAttribute(phyAttr);
        logicAttr.updateBindAttrValue(phyAttr);
        assertTrue(logicAttr.getValue());

        phyAttr.setTestStatus(AttributeStatus.NORMAL);
        logicAttr.updateBindAttrValue(phyAttr);
        assertFalse(logicAttr.getValue());
    }

    @Test
    public void alarmStatusWithMalfunctionStatus() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        phyAttr.setTestStatus(AttributeStatus.MALFUNCTION);

        LAlarmStatusAttribute logicAttr = new LAlarmStatusAttribute(phyAttr);
        logicAttr.updateBindAttrValue(phyAttr);

        assertFalse(logicAttr.getValue());
        assertEquals(AttributeStatus.MALFUNCTION, logicAttr.getStatus());
    }

    // ========== 边界条件测试 ==========

    @Test
    public void statusPropagatedWhenAlarm() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        phyAttr.setTestStatus(AttributeStatus.ALARM);

        LAlarmStatusAttribute logicAttr = new LAlarmStatusAttribute(phyAttr);
        logicAttr.updateBindAttrValue(phyAttr);

        assertEquals(AttributeStatus.ALARM, logicAttr.getStatus());
    }

    @Test
    public void statusPropagatedWhenInsufficient() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        phyAttr.setTestStatus(AttributeStatus.INSUFFICIENT);

        LAlarmStatusAttribute logicAttr = new LAlarmStatusAttribute(phyAttr);
        logicAttr.updateBindAttrValue(phyAttr);

        assertFalse(logicAttr.getValue());
        assertEquals(AttributeStatus.INSUFFICIENT, logicAttr.getStatus());
    }

    // ========== 继承与接口测试 ==========

    @Test
    public void extendsLBinaryAttribute() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        LAlarmStatusAttribute logicAttr = new LAlarmStatusAttribute(phyAttr);
        assertTrue(logicAttr instanceof LBinaryAttribute);
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
