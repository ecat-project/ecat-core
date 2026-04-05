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

import java.time.LocalDateTime;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;

/**
 * LOnlineStatusAttribute 单元测试
 *
 * @author coffee
 */
public class LOnlineStatusAttributeTest {

    // ========== 核心逻辑测试 ==========

    @Test
    public void onlineWhenUpdatedRecently() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        phyAttr.setTestStatus(AttributeStatus.NORMAL);
        phyAttr.setTestUpdateTime(LocalDateTime.now());

        LOnlineStatusAttribute logicAttr = new LOnlineStatusAttribute(phyAttr);
        logicAttr.updateBindAttrValue(phyAttr);

        assertTrue(logicAttr.getValue());
        assertEquals(AttributeStatus.NORMAL, logicAttr.getStatus());
    }

    @Test
    public void offlineWhenUpdatedLongAgo() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        phyAttr.setTestStatus(AttributeStatus.NORMAL);
        phyAttr.setTestUpdateTime(LocalDateTime.now().minusSeconds(120));

        LOnlineStatusAttribute logicAttr = new LOnlineStatusAttribute(phyAttr);
        logicAttr.updateBindAttrValue(phyAttr);

        assertFalse(logicAttr.getValue());
        assertEquals(AttributeStatus.NORMAL, logicAttr.getStatus());
    }

    @Test
    public void offlineWhenUpdateTimeIsNull() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        phyAttr.setTestStatus(AttributeStatus.NORMAL);
        phyAttr.setTestUpdateTime(null);

        LOnlineStatusAttribute logicAttr = new LOnlineStatusAttribute(phyAttr);
        logicAttr.updateBindAttrValue(phyAttr);

        assertFalse(logicAttr.getValue());
        assertEquals(AttributeStatus.NORMAL, logicAttr.getStatus());
    }

    @Test
    public void offlineWhenUpdateTimeIsNullAndStatusIsNull() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        phyAttr.setTestStatus(null);
        phyAttr.setTestUpdateTime(null);

        LOnlineStatusAttribute logicAttr = new LOnlineStatusAttribute(phyAttr);
        logicAttr.updateBindAttrValue(phyAttr);

        assertFalse(logicAttr.getValue());
        assertEquals(AttributeStatus.EMPTY, logicAttr.getStatus());
    }

    // ========== 边界条件测试 ==========

    @Test
    public void onlineAtExactly59Seconds() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        phyAttr.setTestStatus(AttributeStatus.NORMAL);
        phyAttr.setTestUpdateTime(LocalDateTime.now().minusSeconds(59));

        LOnlineStatusAttribute logicAttr = new LOnlineStatusAttribute(phyAttr);
        logicAttr.updateBindAttrValue(phyAttr);

        assertTrue(logicAttr.getValue());
    }

    @Test
    public void offlineAtExactly60Seconds() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        phyAttr.setTestStatus(AttributeStatus.NORMAL);
        phyAttr.setTestUpdateTime(LocalDateTime.now().minusSeconds(60));

        LOnlineStatusAttribute logicAttr = new LOnlineStatusAttribute(phyAttr);
        logicAttr.updateBindAttrValue(phyAttr);

        assertFalse(logicAttr.getValue());
    }

    @Test
    public void transitionFromOnlineToOffline() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        phyAttr.setTestStatus(AttributeStatus.NORMAL);
        phyAttr.setTestUpdateTime(LocalDateTime.now());

        LOnlineStatusAttribute logicAttr = new LOnlineStatusAttribute(phyAttr);
        logicAttr.updateBindAttrValue(phyAttr);
        assertTrue(logicAttr.getValue());

        // 模拟设备断开，更新时间设为 2 分钟前
        phyAttr.setTestUpdateTime(LocalDateTime.now().minusSeconds(120));
        logicAttr.updateBindAttrValue(phyAttr);
        assertFalse(logicAttr.getValue());
    }

    @Test
    public void transitionFromOfflineToOnline() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        phyAttr.setTestStatus(AttributeStatus.NORMAL);
        phyAttr.setTestUpdateTime(LocalDateTime.now().minusSeconds(120));

        LOnlineStatusAttribute logicAttr = new LOnlineStatusAttribute(phyAttr);
        logicAttr.updateBindAttrValue(phyAttr);
        assertFalse(logicAttr.getValue());

        // 模拟设备恢复，更新时间设为现在
        phyAttr.setTestUpdateTime(LocalDateTime.now());
        logicAttr.updateBindAttrValue(phyAttr);
        assertTrue(logicAttr.getValue());
    }

    @Test
    public void statusPropagatedCorrectly() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        phyAttr.setTestStatus(AttributeStatus.MAINTENANCE);
        phyAttr.setTestUpdateTime(LocalDateTime.now());

        LOnlineStatusAttribute logicAttr = new LOnlineStatusAttribute(phyAttr);
        logicAttr.updateBindAttrValue(phyAttr);

        assertTrue(logicAttr.getValue());
        assertEquals(AttributeStatus.MAINTENANCE, logicAttr.getStatus());
    }

    @Test
    public void onlineWithAlarmStatus() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        phyAttr.setTestStatus(AttributeStatus.ALARM);
        phyAttr.setTestUpdateTime(LocalDateTime.now());

        LOnlineStatusAttribute logicAttr = new LOnlineStatusAttribute(phyAttr);
        logicAttr.updateBindAttrValue(phyAttr);

        // 即使在报警状态，只要最近更新过，仍为 online
        assertTrue(logicAttr.getValue());
        assertEquals(AttributeStatus.ALARM, logicAttr.getStatus());
    }

    // ========== 继承与接口测试 ==========

    @Test
    public void extendsLBinaryAttribute() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        LOnlineStatusAttribute logicAttr = new LOnlineStatusAttribute(phyAttr);
        assertTrue(logicAttr instanceof LBinaryAttribute);
    }

    @Test
    public void implementsILogicAttribute() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        LOnlineStatusAttribute logicAttr = new LOnlineStatusAttribute(phyAttr);
        assertTrue(logicAttr instanceof ILogicAttribute);
    }

    @Test
    public void boundModeHasCorrectBindAttr() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        LOnlineStatusAttribute logicAttr = new LOnlineStatusAttribute(phyAttr);
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
        private LocalDateTime testUpdateTime = null;

        TestPhyAttr(String attributeID, AttributeClass attrClass) {
            super(attributeID, attrClass, null, null, 0, false, false);
        }

        void setTestStatus(AttributeStatus status) { this.testStatus = status; }
        void setTestUpdateTime(LocalDateTime time) { this.testUpdateTime = time; }

        @Override public AttributeStatus getStatus() { return testStatus; }
        @Override public LocalDateTime getUpdateTime() { return testUpdateTime; }
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
