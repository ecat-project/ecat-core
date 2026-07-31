package com.ecat.core.Device;

import com.ecat.core.Bus.event.DeviceLifecycleEvent;
import com.ecat.core.ConfigEntry.ConfigEntry;
import com.ecat.core.ConfigEntry.ConfigEntryRegistry;
import org.junit.Test;
import org.mockito.Mockito;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * DeviceRegistry.disable 持久化 + 级联 entry setEnabled（P1.2，三态承重墙）。
 * <p>disable 须：① 持久化 device record disabled=true（保 entryId，区别 remove 的 entryId=null/deleted=true）；
 * ② 级联 {@code ConfigEntryRegistry.setEnabled(entryId,false)}——集成 load 跳过 disabled entry（防重启复活）。
 * 当前 disable 只 registry.remove（不持久化、不级联）→ 此测试红。
 */
public class DeviceRegistryDisableTest {

    private DeviceBase newDevice(String entryId, String uniqueId, String coordinate) {
        ConfigEntry e = new ConfigEntry();
        e.setEntryId(entryId);
        e.setUniqueId(uniqueId);
        e.setCoordinate(coordinate);
        Map<String, Object> data = new HashMap<>();
        data.put("name", "n-" + entryId);
        e.setData(data);
        return new DeviceBase(e) {
            @Override public void init() {}
            @Override public void start() {}
            @Override public void stop() {}
            @Override public void release() {}
        };
    }

    @Test
    public void disable_persists_disabled_record_keeps_entryId() throws Exception {
        File tmp = Files.createTempDirectory("ecat-reg-disable").toFile();
        YmlDevicePersistence p = new YmlDevicePersistence(tmp.getAbsolutePath());
        DeviceRegistry reg = new DeviceRegistry();
        reg.setPersistence(p);
        DeviceBase d = newDevice("e1", "u1", "com.ecat:c");
        reg.getOrCreate(d, DeviceLifecycleEvent.Action.CREATE);
        String id = d.getId();

        reg.disable(d);

        assertNull("active map 应移除", reg.getDeviceByID(id));
        DeviceRecord rec = p.loadAll().stream().filter(r -> id.equals(r.getId())).findFirst().orElse(null);
        assertNotNull("record 应持久化", rec);
        assertTrue("disable 应持久化 disabled=true", rec.isDisabled());
        assertEquals("disable 保留 entryId（供 re-enable 复原，区别 remove）", "e1", rec.getEntryId());
        assertFalse("disable 不置 deleted（区别 remove）", rec.isDeleted());
    }

    @Test
    public void disable_cascades_entry_setEnabled_false() {
        DeviceRegistry reg = new DeviceRegistry();
        ConfigEntryRegistry entryReg = Mockito.mock(ConfigEntryRegistry.class);
        reg.setEntryRegistry(entryReg);
        DeviceBase d = newDevice("e1", "u1", "com.ecat:c");
        reg.getOrCreate(d, DeviceLifecycleEvent.Action.CREATE);

        reg.disable(d);

        // 级联：禁用 entry（集成 load 跳过 disabled entry，防重启复活）
        Mockito.verify(entryReg).setEnabled("e1", false);
    }

    @Test
    public void disable_withoutEntryRegistry_skipsCascade_noException() {
        // 未注入 entryRegistry（或设备无 entry）→ 跳过级联，不抛（防御性，禁用持久化仍生效）
        DeviceRegistry reg = new DeviceRegistry();
        DeviceBase d = newDevice("e1", "u1", "com.ecat:c");
        reg.getOrCreate(d, DeviceLifecycleEvent.Action.CREATE);
        reg.disable(d);  // 不应抛
        assertNull(reg.getDeviceByID(d.getId()));
    }
}
