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
package com.ecat.core.Integration;

import com.ecat.core.Bus.BusRegistry;
import com.ecat.core.Bus.event.BusEvent;
import com.ecat.core.Bus.event.DeviceDataChangedEvent;
import com.ecat.core.Bus.event.DeviceLifecycleEvent;
import com.ecat.core.ConfigEntry.ConfigEntry;
import com.ecat.core.Device.DeviceBase;
import com.ecat.core.Device.DeviceRegistry;
import com.ecat.core.Device.YmlDevicePersistence;
import com.ecat.core.EcatCore;
import com.ecat.core.State.AttributeStatus;

import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ready gate 生命周期收口（Phase 1.3）：IntegrationDeviceBase.createEntry 必须在 register+restore 之后
 * 调 markReady，使 init 期默认值发布被门禁挂起、最终用 getOrCreate 解析的稳定 id 单次 flush。
 *
 * <p>端到端守护：预铸持久化稳定 id（≠ 构造临时 UUID），init() 内对一个属性 updateValue 建 midState（不显式
 * publicState——硬门禁下预 ready publish 会抛）；createEntry（finalizeNewDevice）markReady 时 flushPendingPublishes
 * 用 getOrCreate 解析的稳定 id 单次发布。createEntry 完成后断言——① 设备 isReady；② 恰 1 个
 * DeviceDataChangedEvent；③ 其 deviceId == 稳定 id（非构造临时 UUID）。任一缺失即回归：漏 markReady →
 * isReady=false + 0 事件（midState 永不 flush）。
 */
public class IntegrationDeviceBaseReadyGateTest {

    private ConfigEntry newEntry(String uniqueId, String coordinate, String entryId) {
        ConfigEntry e = new ConfigEntry();
        e.setEntryId(entryId);
        e.setUniqueId(uniqueId);
        e.setCoordinate(coordinate);
        Map<String, Object> data = new HashMap<>();
        data.put("name", "n-" + uniqueId);
        e.setData(data);
        return e;
    }

    @Test
    public void createEntry_initDefaultSuppressed_thenFlushedWithStableId() throws Exception {
        File tmp = Files.createTempDirectory("ecat-readygate-idb").toFile();
        YmlDevicePersistence p = new YmlDevicePersistence(tmp.getAbsolutePath());

        // ① 预铸稳定 id：reg1 先 getOrCreate (u1,c)，持久化记录（其 id 即稳定 id，≠ 后续构造临时 UUID）
        DeviceRegistry reg1 = new DeviceRegistry();
        reg1.setPersistence(p);
        DeviceBase seed = new DeviceBase(newEntry("u1", "com.ecat:c", "seed")) {
            @Override public void init() {}
            @Override public void start() {}
            @Override public void stop() {}
            @Override public void release() {}
        };
        reg1.getOrCreate(seed, DeviceLifecycleEvent.Action.CREATE);
        String stableId = seed.getId();

        // ② reg2 共享持久化（load 重建 matchIndex→getOrCreate 命中复原稳定 id）；busRegistry 留 null→不发 lifecycle 事件
        DeviceRegistry reg2 = new DeviceRegistry();
        reg2.setPersistence(p);
        reg2.load();

        EcatCore mockCore = mock(EcatCore.class);
        BusRegistry bus = mock(BusRegistry.class);
        when(mockCore.getBusRegistry()).thenReturn(bus);
        when(mockCore.getStateManager()).thenReturn(null);   // restorePersistedState 跳过（本测聚焦 flush）

        IntegrationDeviceBase integration = new IntegrationDeviceBase() {
            @Override
            protected DeviceBase createDeviceFromEntry(ConfigEntry entry) {
                DeviceBase d = new DeviceBase(entry) {
                    @Override
                    public void init() {
                        // init 期 updateValue 建 midState（不显式 publicState——硬门禁下预 ready publish 抛）；
                        // midState 保留，待 markReady 的 flushPendingPublishes 用稳定 id 单次发布。
                        com.ecat.core.Device.TestPhyDeviceHelper.TestPhyAttr a =
                            new com.ecat.core.Device.TestPhyDeviceHelper.TestPhyAttr("def");
                        setAttribute(a);
                        a.updateValue(1.0, AttributeStatus.NORMAL);
                    }
                    @Override public void start() {}
                    @Override public void stop() {}
                    @Override public void release() {}
                };
                d.load(mockCore);
                d.init();   // 物理集成约定：createDeviceFromEntry 内 load+init（init 期建 midState，未发布）
                return d;
            }
        };
        integration.deviceRegistry = reg2;
        integration.core = mockCore;

        integration.createEntry(newEntry("u1", "com.ecat:c", "entry-x"));

        DeviceBase created = integration.getAllDevices().iterator().next();
        assertTrue("createEntry 完成后设备必须 isReady（已 markReady）", created.isReady());
        assertEquals("getOrCreate 应复原预铸稳定 id（≠ 构造临时 UUID）", stableId, created.getId());

        // 仅统计 DeviceDataChangedEvent（排除 registry 的 lifecycle 事件——此处 busRegistry=null 本就无）
        ArgumentCaptor<BusEvent> captor = ArgumentCaptor.forClass(BusEvent.class);
        verify(bus, atLeast(0)).publish(captor.capture());
        List<DeviceDataChangedEvent> dataEvents = captor.getAllValues().stream()
            .filter(e -> e.getPayload() instanceof DeviceDataChangedEvent)
            .map(e -> (DeviceDataChangedEvent) e.getPayload())
            .collect(Collectors.toList());
        assertEquals("init 期建的 midState 应由 markReady flush 单次发布", 1, dataEvents.size());
        assertEquals("flush 事件必须用稳定 id（非 init 期临时 UUID）", stableId, dataEvents.get(0).getDeviceId());
    }
}
