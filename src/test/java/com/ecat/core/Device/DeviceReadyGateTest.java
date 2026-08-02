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
package com.ecat.core.Device;

import com.ecat.core.Bus.BusRegistry;
import com.ecat.core.Bus.event.BusEvent;
import com.ecat.core.Bus.event.DeviceDataChangedEvent;
import com.ecat.core.EcatCore;
import com.ecat.core.State.AttributeBase;
import com.ecat.core.State.AttributeStatus;
import com.ecat.core.State.StateManager;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;

/**
 * 设备就绪门禁（ready gate）回归：init 期（设备未 READY）publicState 必须挂起发布、保留 midState，
 * 待 {@link DeviceBase#markReady()} 统一 flush，避免 init 期用未解析的临时 deviceId 发孤儿事件
 * （{@code null value in column "type"} 撞 NOT NULL，bug-record-20260801-071800）。
 *
 * <p>三层断言：
 * <ol>
 *   <li>门禁抑制：device 未 READY 时 publicState 不发布、不清 isValueUpdated（midState 保留）；</li>
 *   <li>就绪发布：device READY 后 publicState 正常发布单次；</li>
 *   <li>markReady flush 端到端：真设备 + 真属性，未 READY 期 updateValue+publicState 挂起，
 *       markReady 后单次 flush 发布（用稳定 deviceId）。</li>
 * </ol>
 *
 * <p>当前 RED：DeviceBase 无 isReady/markReady（编译断）；publicState 无 READY 门禁（mock 设备默认 isReady=false
 * 仍照发——但 mock 方法不存在编译先断）。实现后 GREEN。
 */
public class DeviceReadyGateTest {

    /** 门禁抑制：未 READY 时 publicState 不发布、保留 midState（isValueUpdated 不清）。 */
    @Test
    public void publicState_suppressedBeforeReady_retainsMidState() {
        DeviceBase device = mock(DeviceBase.class, RETURNS_DEEP_STUBS);
        when(device.isReady()).thenReturn(false);
        AttributeBase<Double> attr = new TestPhyDeviceHelper.TestPhyAttr("attr1");
        attr.setDevice(device);
        BusRegistry bus = mock(BusRegistry.class);
        when(device.getCore().getBusRegistry()).thenReturn(bus);

        assertTrue(attr.updateValue(111.0, AttributeStatus.NORMAL));   // 设 midState + isValueUpdated=true（updateValue 不自动发布）
        assertTrue(attr.publicState());         // 门禁应挂起

        assertTrue("未 READY 时 isValueUpdated 必须保留（midState 未 flush）", attr.isValueUpdated());
        verify(bus, never()).publish(any(BusEvent.class));
    }

    /** 就绪发布：READY 后 publicState 正常发布单次、清 isValueUpdated。 */
    @Test
    public void publicState_publishesWhenReady() {
        DeviceBase device = mock(DeviceBase.class, RETURNS_DEEP_STUBS);
        when(device.getId()).thenReturn("stable-id");
        when(device.isReady()).thenReturn(true);
        AttributeBase<Double> attr = new TestPhyDeviceHelper.TestPhyAttr("attr1");
        attr.setDevice(device);
        BusRegistry bus = mock(BusRegistry.class);
        when(device.getCore().getBusRegistry()).thenReturn(bus);

        assertTrue(attr.updateValue(222.0, AttributeStatus.NORMAL));
        assertTrue(attr.publicState());

        assertFalse(attr.isValueUpdated());
        verify(bus, times(1)).publish(any(BusEvent.class));
    }

    /**
     * markReady flush 端到端：真设备（TestPhyDevice，core 注入 mock bus）+ 真属性。
     * 未 READY 期 updateValue+publicState 挂起（bus 0 次、isValueUpdated 保留）；
     * markReady 后单次 flush 发布，deviceId=稳定 id、isValueUpdated 清零。
     */
    @Test
    public void markReady_flushesPendingPublishes_singleStableEvent() {
        // 真设备（构造铸造临时 id；此处不进 registry/不 setId，仅验 flush 行为，id 即构造默认值）
        DeviceBase device = TestPhyDeviceHelper.createDevice("entry1", "uid1", "com.ecat:integration-test");
        String stableId = device.getId();
        TestPhyDeviceHelper.TestPhyAttr attr = new TestPhyDeviceHelper.TestPhyAttr("attr1");
        device.setAttribute(attr);

        // 注入 mock core + bus + stateManager（restore/flush 走 device.getCore()）
        EcatCore core = mock(EcatCore.class);
        BusRegistry bus = mock(BusRegistry.class);
        StateManager sm = mock(StateManager.class);
        when(core.getBusRegistry()).thenReturn(bus);
        when(core.getStateManager()).thenReturn(sm);
        device.load(core);

        // 未 READY（构造默认）→ updateValue + 显式 publicState 应被门禁挂起
        assertTrue(attr.updateValue(333.0, AttributeStatus.NORMAL));
        assertTrue(attr.publicState());
        assertTrue(attr.isValueUpdated());
        verify(bus, never()).publish(any(BusEvent.class));

        // markReady → 内部 flushPendingPublishes → 单次稳定 id 发布
        device.markReady();

        assertFalse("flush 后 isValueUpdated 必须清零", attr.isValueUpdated());
        verify(bus, times(1)).publish(any(BusEvent.class));

        // 捕获发布的事件，断言 deviceId=稳定 id（非临时/非 null）
        org.mockito.ArgumentCaptor<BusEvent> captor = org.mockito.ArgumentCaptor.forClass(BusEvent.class);
        verify(bus, times(1)).publish(captor.capture());
        DeviceDataChangedEvent payload = (DeviceDataChangedEvent) captor.getValue().getPayload();
        assertEquals("flush 发布的事件必须用稳定 deviceId", stableId, payload.getDeviceId());
    }
}
