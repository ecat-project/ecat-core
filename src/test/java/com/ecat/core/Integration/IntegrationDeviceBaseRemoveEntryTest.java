package com.ecat.core.Integration;

import com.ecat.core.ConfigEntry.ConfigEntry;
import com.ecat.core.Device.DeviceBase;
import com.ecat.core.Device.DeviceRegistry;
import com.ecat.core.Device.YmlDevicePersistence;
import com.ecat.core.EcatCore;
import com.ecat.core.State.StateManager;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * 00-core：IntegrationDeviceBase.removeEntry 的逻辑删语义守护。
 * <p>设计意图：一个 deviceId 绑定一个物理设备，entry 删除后用户重新添加同 uniqueId 时，
 * 必须复原同一 deviceId（不能铸新 UUID）。故 removeEntry 走 DeviceRegistry.remove（保 matchIndex），
 * 不走 purge（删 matchIndex→铸新 id）；且不物理删 state DB（保留 state，与逻辑删语义一致）。
 */
public class IntegrationDeviceBaseRemoveEntryTest {

    /** 匿名 IntegrationDeviceBase 子类：createDeviceFromEntry 返回空壳设备，同包直注 protected deviceRegistry。 */
    private IntegrationDeviceBase newIntegration(DeviceRegistry reg) {
        IntegrationDeviceBase integration = new IntegrationDeviceBase() {
            @Override
            protected DeviceBase createDeviceFromEntry(ConfigEntry entry) {
                return new DeviceBase(entry) {
                    @Override public void init() {}
                    @Override public void start() {}
                    @Override public void stop() {}
                    @Override public void release() {}
                };
            }
        };
        integration.deviceRegistry = reg;
        return integration;
    }

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
    public void removeEntry_thenRecreateSameUniqueId_revivesSameDeviceId() throws Exception {
        File tmp = Files.createTempDirectory("ecat-idb-remove").toFile();
        YmlDevicePersistence p = new YmlDevicePersistence(tmp.getAbsolutePath());
        DeviceRegistry reg = new DeviceRegistry();
        reg.setPersistence(p);

        IntegrationDeviceBase integration = newIntegration(reg);

        ConfigEntry e1 = newEntry("u1", "com.ecat:c", "entry-1");
        integration.createEntry(e1);
        String originalId = integration.getAllDevices().iterator().next().getId();

        // 删除 entry：应逻辑删（保留 matchIndex），活跃设备从系统消失
        integration.removeEntry(e1.getEntryId());
        assertTrue("removeEntry 后活跃设备应清空", integration.getAllDevices().isEmpty());

        // 同 uniqueId 重新创建 → 必须复原同 deviceId（一 deviceId 绑定一物理设备）
        ConfigEntry e2 = newEntry("u1", "com.ecat:c", "entry-2");
        integration.createEntry(e2);
        String revivedId = integration.getAllDevices().iterator().next().getId();

        assertEquals("removeEntry 后同 uniqueId 重建应复原同 deviceId", originalId, revivedId);
    }


    @Test
    public void removeEntry_doesNotDeleteStateDb_logicDeleteKeepsState() {
        // 守护：逻辑删保留 state DB——removeEntry 不得调用 stateManager.removeDevice（物理删 state 文件）。
        // deviceId 复原靠 matchIndex，与 state 无关；state 是设备历史，逻辑删应保留（与 disable 一致）。
        DeviceRegistry reg = new DeviceRegistry();
        StateManager stateManager = mock(StateManager.class);
        EcatCore core = mock(EcatCore.class);
        when(core.getStateManager()).thenReturn(stateManager);

        IntegrationDeviceBase integration = newIntegration(reg);
        integration.core = core;

        ConfigEntry e1 = newEntry("u1", "com.ecat:c", "entry-1");
        integration.createEntry(e1);

        integration.removeEntry(e1.getEntryId());

        verify(stateManager, never()).removeDevice(any(DeviceBase.class));
    }
}
