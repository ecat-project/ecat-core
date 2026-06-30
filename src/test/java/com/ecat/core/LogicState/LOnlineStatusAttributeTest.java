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

import java.time.Instant;
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
        phyAttr.setTestUpdateTime(Instant.now());
        bindDevice(phyAttr);
        phyAttr.updateValue(null, AttributeStatus.NORMAL);

        LOnlineStatusAttribute logicAttr = new LOnlineStatusAttribute(phyAttr);
        // 逻辑属性自身也需绑定设备：updateBindAttrValue 内部调用 logicAttr.updateValue(...) 构建不可变
        // AttrState（getState() 的返回），未绑定设备时 updateValue 跳过 lastState 构建导致 getState() 恒为 null
        bindDevice(logicAttr);

        logicAttr.updateBindAttrValue(phyAttr.getState());

        assertEquals("online", logicAttr.getState().getValue());
        assertEquals(AttributeStatus.NORMAL, logicAttr.getState().getStatus());
    }

    @Test
    public void offlineWhenUpdatedLongAgo() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        phyAttr.setTestStatus(AttributeStatus.NORMAL);
        phyAttr.setTestUpdateTime(Instant.now().minusSeconds(120));
        bindDevice(phyAttr);
        phyAttr.updateValue(null, AttributeStatus.NORMAL);

        LOnlineStatusAttribute logicAttr = new LOnlineStatusAttribute(phyAttr);
        bindDevice(logicAttr);

        logicAttr.updateBindAttrValue(phyAttr.getState());

        assertEquals("offline", logicAttr.getState().getValue());
        assertEquals(AttributeStatus.NORMAL, logicAttr.getState().getStatus());
    }

    @Test
    public void offlineWhenUpdateTimeIsNull() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        phyAttr.setTestStatus(AttributeStatus.NORMAL);
        phyAttr.setTestUpdateTime(null);
        bindDevice(phyAttr);
        phyAttr.updateValue(null, AttributeStatus.NORMAL);

        LOnlineStatusAttribute logicAttr = new LOnlineStatusAttribute(phyAttr);
        bindDevice(logicAttr);

        logicAttr.updateBindAttrValue(phyAttr.getState());

        assertEquals("offline", logicAttr.getState().getValue());
        assertEquals(AttributeStatus.NORMAL, logicAttr.getState().getStatus());
    }

    @Test
    public void offlineWhenUpdateTimeIsNullAndStatusIsNull() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        phyAttr.setTestUpdateTime(null);
        bindDevice(phyAttr);
        // 意图：物理属性从未上报（status 缺失、updateTime 为 null）→ 离线。
        // buildState() 强制 status 非 null（deviceId/attrId/status/context 不可缺），
        // 故用 AttributeStatus.EMPTY 表达"无状态"这一语义（生产 updateBindAttrValue 同样以
        // EMPTY 兜底 null status），既保留测试意图又避免向不可变状态构造器传 null。
        phyAttr.updateValue(null, AttributeStatus.EMPTY);

        LOnlineStatusAttribute logicAttr = new LOnlineStatusAttribute(phyAttr);
        bindDevice(logicAttr);

        logicAttr.updateBindAttrValue(phyAttr.getState());

        assertEquals("offline", logicAttr.getState().getValue());
        assertEquals(AttributeStatus.EMPTY, logicAttr.getState().getStatus());
    }

    // ========== 边界条件测试 ==========

    @Test
    public void onlineAtExactly59Seconds() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        phyAttr.setTestStatus(AttributeStatus.NORMAL);
        phyAttr.setTestUpdateTime(Instant.now().minusSeconds(59));
        bindDevice(phyAttr);
        phyAttr.updateValue(null, AttributeStatus.NORMAL);

        LOnlineStatusAttribute logicAttr = new LOnlineStatusAttribute(phyAttr);
        bindDevice(logicAttr);

        logicAttr.updateBindAttrValue(phyAttr.getState());

        assertEquals("online", logicAttr.getState().getValue());
    }

    @Test
    public void offlineAtExactly60Seconds() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        phyAttr.setTestStatus(AttributeStatus.NORMAL);
        phyAttr.setTestUpdateTime(Instant.now().minusSeconds(60));
        bindDevice(phyAttr);
        phyAttr.updateValue(null, AttributeStatus.NORMAL);

        LOnlineStatusAttribute logicAttr = new LOnlineStatusAttribute(phyAttr);
        bindDevice(logicAttr);

        logicAttr.updateBindAttrValue(phyAttr.getState());

        assertEquals("offline", logicAttr.getState().getValue());
    }

    @Test
    public void transitionFromOnlineToOffline() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        phyAttr.setTestStatus(AttributeStatus.NORMAL);
        phyAttr.setTestUpdateTime(Instant.now());
        bindDevice(phyAttr);
        phyAttr.updateValue(null, AttributeStatus.NORMAL);

        LOnlineStatusAttribute logicAttr = new LOnlineStatusAttribute(phyAttr);
        bindDevice(logicAttr);

        logicAttr.updateBindAttrValue(phyAttr.getState());
        assertEquals("online", logicAttr.getState().getValue());

        // 模拟设备断开，更新时间设为 2 分钟前
        phyAttr.setTestUpdateTime(Instant.now().minusSeconds(120));
        phyAttr.updateValue(null, AttributeStatus.NORMAL);
        logicAttr.updateBindAttrValue(phyAttr.getState());
        assertEquals("offline", logicAttr.getState().getValue());
    }

    @Test
    public void transitionFromOfflineToOnline() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        phyAttr.setTestStatus(AttributeStatus.NORMAL);
        phyAttr.setTestUpdateTime(Instant.now().minusSeconds(120));
        bindDevice(phyAttr);
        phyAttr.updateValue(null, AttributeStatus.NORMAL);

        LOnlineStatusAttribute logicAttr = new LOnlineStatusAttribute(phyAttr);
        bindDevice(logicAttr);

        logicAttr.updateBindAttrValue(phyAttr.getState());
        assertEquals("offline", logicAttr.getState().getValue());

        // 模拟设备恢复，更新时间设为现在
        phyAttr.setTestUpdateTime(Instant.now());
        phyAttr.updateValue(null, AttributeStatus.NORMAL);
        logicAttr.updateBindAttrValue(phyAttr.getState());
        assertEquals("online", logicAttr.getState().getValue());
    }

    @Test
    public void statusPropagatedCorrectly() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        phyAttr.setTestStatus(AttributeStatus.MAINTENANCE);
        phyAttr.setTestUpdateTime(Instant.now());
        bindDevice(phyAttr);
        phyAttr.updateValue(null, AttributeStatus.MAINTENANCE);

        LOnlineStatusAttribute logicAttr = new LOnlineStatusAttribute(phyAttr);
        bindDevice(logicAttr);

        logicAttr.updateBindAttrValue(phyAttr.getState());

        assertEquals("online", logicAttr.getState().getValue());
        assertEquals(AttributeStatus.MAINTENANCE, logicAttr.getState().getStatus());
    }

    @Test
    public void onlineWithAlarmStatus() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        phyAttr.setTestStatus(AttributeStatus.ALARM);
        phyAttr.setTestUpdateTime(Instant.now());
        bindDevice(phyAttr);
        phyAttr.updateValue(null, AttributeStatus.ALARM);

        LOnlineStatusAttribute logicAttr = new LOnlineStatusAttribute(phyAttr);
        bindDevice(logicAttr);

        logicAttr.updateBindAttrValue(phyAttr.getState());

        // 即使在报警状态，只要最近更新过，仍为 online
        assertEquals("online", logicAttr.getState().getValue());
        assertEquals(AttributeStatus.ALARM, logicAttr.getState().getStatus());
    }

    // ========== 继承与接口测试 ==========

    @Test
    public void extendsLStringSelectAttribute() {
        TestPhyAttr phyAttr = createMockAttr("so2", AttributeClass.SO2);
        LOnlineStatusAttribute logicAttr = new LOnlineStatusAttribute(phyAttr);
        assertTrue(logicAttr instanceof LStringSelectAttribute);
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

    /**
     * 绑定 mock 设备，使 {@link AttributeBase#getState()} 在 updateValue 后返回非 null 的 AttrState。
     * getState() 的返回 lastState 由 updateValue 构建，而 updateValue 仅在 device 非 null 且
     * getId() 非 null 时才构建（见 AttributeBase.updateValue 的 deviceId 守卫）——未绑定设备时
     * lastState 恒为 null，下游 getState() 读取即 NPE。物理属性与逻辑属性均需绑定。
     */
    private static void bindDevice(AttributeBase<?> attr) {
        DeviceBase mockDevice = org.mockito.Mockito.mock(DeviceBase.class);
        org.mockito.Mockito.when(mockDevice.getId()).thenReturn("testDevice");
        attr.setDevice(mockDevice);
    }

    private static class TestPhyAttr extends AttributeBase<String> {
        private AttributeStatus testStatus = AttributeStatus.EMPTY;
        // 测试桩控制的更新时间：override setValueUpdated 时写入 updateTime 字段，
        // buildState() 读取 updateTime 进入 AttrState.lastUpdated——这样既能驱动"最近/久远/为 null"
        // 三类在线判断，又不依赖已收紧为 protected 的 getUpdateTime()/getStatus()。
        private Instant testUpdateTime = null;

        TestPhyAttr(String attributeID, AttributeClass attrClass) {
            super(attributeID, attrClass, null, null, 0, false, false);
        }

        void setTestStatus(AttributeStatus status) {
            this.testStatus = status;
            // 保持 status 字段与桩值同步：updateValue(...,null) 不会改写 status，
            // buildState() 读 status 字段进入 AttrState.status
            super.status = status;
        }

        void setTestUpdateTime(Instant time) { this.testUpdateTime = time; }

        /**
         * 覆盖基类 setValueUpdated：用测试桩的 testUpdateTime 替代 Instant.now()，
         * 使 updateValue → buildState 构建的 AttrState.lastUpdated 由测试控制
         * （包括 null——模拟设备从未上报过更新时间）。
         */
        @Override
        public void setValueUpdated(boolean isValueUpdated) {
            if (isValueUpdated) {
                this.updateTime = this.testUpdateTime;
            }
            this.isValueUpdated = isValueUpdated;
        }

        // getStatus()/getUpdateTime() 已降为 protected 且不再属于接口契约；
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
