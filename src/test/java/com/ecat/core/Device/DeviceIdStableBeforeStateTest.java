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
import com.ecat.core.Bus.event.DeviceLifecycleEvent;
import com.ecat.core.EcatCore;
import com.ecat.core.State.AttributeStatus;
import com.ecat.core.State.StateManager;

import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.nio.file.Files;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 稳定 deviceId 前置于 state 物化（resolve-in-load）回归。
 *
 * <p>复现 type=null 根因因果链的关键一环（bug-record-20260801-155000）：设备构造铸造临时 UUID，
 * 若 id 解析（matchIndex 命中→setId 稳定值）发生在 init/buildState 物化 midState 之后，
 * midState 烘焙的是临时 id；markReady flush 发布的 newState.getDeviceId()=临时 id ≠ registry key（稳定 id），
 * 消费侧 registry.getDeviceByID(临时 id)=null → realdata.type=null 撞 NOT NULL。
 *
 * <p>修复（见 docs/2026-08-01-device-id-stable-before-state-design.md）：DeviceBase.load 注入 core 后立即
 * resolveStableId（matchIndex 命中→setId 稳定值），使 id 在 init/buildState 之前就已稳定，
 * midState 从诞生起带稳定 id，flush 发布的 newState.getDeviceId() == registry key。
 *
 * <p>当前 RED：DeviceBase.load 不解析 id，getOrCreate 才 setId → newState 携带临时 id。
 * 实现后 GREEN：newState.getDeviceId() == 稳定 id。
 */
public class DeviceIdStableBeforeStateTest {

    /**
     * 重启恢复场景（matchIndex 命中分支）：
     * 同 (coordinate,uniqueId) 此前注册过 → 新设备构造铸造临时 id，load 后 id 应已解析为稳定值，
     * init 物化的 midState 带稳定 id，markReady flush 发布的事件 newState.getDeviceId()==稳定 id。
     */
    @Test
    public void load_resolvesStableId_beforeInitSoFlushedStateCarriesStableId() throws Exception {
        File tmpDir = Files.createTempDirectory("ecat-idstable").toFile();
        DeviceRegistry reg = new DeviceRegistry();
        reg.setPersistence(new YmlDevicePersistence(tmpDir.getAbsolutePath()));

        // 预先注册同 (coordinate,uniqueId) 设备，让 matchIndex 记下持久化稳定 id S
        DeviceBase first = TestPhyDeviceHelper.createDevice("entry-first", "uid-same", "com.ecat:integration-test");
        reg.getOrCreate(first, DeviceLifecycleEvent.Action.CREATE);
        String stableId = first.getId();

        // 模拟重启：新设备构造铸造临时 tmp（同 coordinate+uniqueId）
        DeviceBase device = TestPhyDeviceHelper.createDevice("entry-restart", "uid-same", "com.ecat:integration-test");
        String tempId = device.getId();
        assertNotEquals("前提：构造铸造的临时 id ≠ 已持久化的稳定 id", stableId, tempId);

        // 注入 mock core：getDeviceRegistry() 返真 registry，bus/stateManager 走 mock
        EcatCore core = mock(EcatCore.class);
        BusRegistry bus = mock(BusRegistry.class);
        StateManager sm = mock(StateManager.class);
        when(core.getDeviceRegistry()).thenReturn(reg);
        when(core.getBusRegistry()).thenReturn(bus);
        when(core.getStateManager()).thenReturn(sm);

        TestPhyDeviceHelper.TestPhyAttr attr = new TestPhyDeviceHelper.TestPhyAttr("attr1");
        device.setAttribute(attr);

        // load：【修后】此处 resolveStableId→setId(stableId)；【当前】仅塞 core，id 仍为 tempId
        device.load(core);

        // init 物化 midState（用 load 此刻的 id 烘焙）
        assertTrue(attr.updateValue(333.0, AttributeStatus.NORMAL));

        // getOrCreate：matchIndex 命中→setId(stableId)。修后幂等（load 已设），当前在此才设 → midState 已物化 tempId
        reg.getOrCreate(device, DeviceLifecycleEvent.Action.CREATE);
        assertEquals("getOrCreate 后 device.getId() 必为稳定 id", stableId, device.getId());

        // markReady → flushPendingPublishes → 单次发布
        device.markReady();

        ArgumentCaptor<BusEvent> captor = ArgumentCaptor.forClass(BusEvent.class);
        verify(bus, times(1)).publish(captor.capture());
        DeviceDataChangedEvent payload = (DeviceDataChangedEvent) captor.getValue().getPayload();

        // RED（当前）：newState.getDeviceId()=tempId（陈旧 midState）≠ stableId → 失败
        // GREEN（修后）：load 已解析，midState 烘焙 stableId → 通过
        assertEquals(
            "flush 发布的 newState 必须带稳定 deviceId（与 registry key 一致，消费侧反查命中，非临时 id）",
            stableId, payload.getNewState().getDeviceId());
    }
}
