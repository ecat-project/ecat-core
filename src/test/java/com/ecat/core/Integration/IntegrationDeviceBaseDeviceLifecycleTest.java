package com.ecat.core.Integration;

import com.ecat.core.ConfigEntry.ConfigEntry;
import com.ecat.core.Device.DeviceBase;
import com.ecat.core.Device.DeviceRecord;
import com.ecat.core.Device.DeviceRegistry;
import com.ecat.core.Device.YmlDevicePersistence;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * 00-core：IntegrationDeviceBase 设备级生命周期（removeDevice / disableEntry）的逻辑删语义守护。
 * <p>regression 锁：removeDevice 与 disableEntry 都必须保留 matchIndex，使同 uniqueId 重建复原同 deviceId；
 * 且 disableEntry 是软移除（保 yml 记录、不标 deleted=true，区别于 removeEntry 的逻辑删）。
 * 防止后续误把 remove/disable 改回 purge（硬删 matchIndex→铸新 id）而无人察觉。
 */
public class IntegrationDeviceBaseDeviceLifecycleTest {

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
    public void removeDevice_keepsMatchIndex_recreateRevivesSameId() {
        // 守护：removeDevice 走逻辑删（DeviceRegistry.remove），保留 matchIndex，
        // 同 uniqueId 重建复原同 deviceId——防止误用 purge（删 matchIndex→铸新 id）。
        DeviceRegistry reg = new DeviceRegistry();
        IntegrationDeviceBase integration = newIntegration(reg);

        ConfigEntry e1 = newEntry("u1", "com.ecat:c", "entry-1");
        integration.createEntry(e1);
        DeviceBase d = integration.getAllDevices().iterator().next();
        String originalId = d.getId();

        assertTrue("removeDevice 应返回 true（设备存在）", integration.removeDevice(d));
        assertTrue("removeDevice 后活跃设备应清空", integration.getAllDevices().isEmpty());

        ConfigEntry e2 = newEntry("u1", "com.ecat:c", "entry-2");
        integration.createEntry(e2);
        assertEquals("removeDevice 后同 uniqueId 重建应复原同 deviceId", originalId,
            integration.getAllDevices().iterator().next().getId());
    }


    @Test
    public void disableEntry_softRemoves_keepsRecordNoDeletedFlag_recreateRevivesSameId() throws Exception {
        // 守护：disableEntry 是软移除——保 yml 记录 + matchIndex，且不标 deleted=true（区别于 remove 的逻辑删）。
        // 同 uniqueId 重建复原同 deviceId。防止误把 disable 改成 purge（删记录）或 remove（标 deleted）。
        File tmp = Files.createTempDirectory("ecat-idb-disable").toFile();
        YmlDevicePersistence p = new YmlDevicePersistence(tmp.getAbsolutePath());
        DeviceRegistry reg = new DeviceRegistry();
        reg.setPersistence(p);
        IntegrationDeviceBase integration = newIntegration(reg);

        ConfigEntry e1 = newEntry("u1", "com.ecat:c", "entry-1");
        integration.createEntry(e1);
        String originalId = integration.getAllDevices().iterator().next().getId();

        integration.disableEntry(e1.getEntryId());
        assertTrue("disableEntry 后活跃设备应清空", integration.getAllDevices().isEmpty());

        // 软移除：yml 记录仍在，且 deleted=false（entry 仅禁用、未删除）
        DeviceRecord rec = p.loadAll().stream()
            .filter(r -> originalId.equals(r.getId())).findFirst()
            .orElseThrow(() -> new AssertionError("disableEntry 不应删 yml 记录"));
        assertFalse("disableEntry 软移除，不应标 deleted=true（那是 removeEntry 的语义）", rec.isDeleted());

        // matchIndex 保留：同 uniqueId 重建复原同 id
        ConfigEntry e2 = newEntry("u1", "com.ecat:c", "entry-2");
        integration.createEntry(e2);
        assertEquals("disableEntry 后同 uniqueId 重建应复原同 deviceId", originalId,
            integration.getAllDevices().iterator().next().getId());
    }
}
