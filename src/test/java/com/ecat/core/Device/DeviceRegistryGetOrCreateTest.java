package com.ecat.core.Device;

import com.ecat.core.Bus.BusRegistry;
import com.ecat.core.Bus.BusTopic;
import com.ecat.core.Bus.event.DeviceLifecycleEvent;
import com.ecat.core.ConfigEntry.ConfigEntry;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

/** 00-core Task 4/5：DeviceRegistry load/matchIndex/getOrCreate/register/unregister/findDevicesByEntryId。 */
public class DeviceRegistryGetOrCreateTest {

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
    public void getOrCreate_firstMints_thenReusesSameId() throws Exception {
        File tmp = Files.createTempDirectory("ecat-reg-goc").toFile();
        DeviceRegistry reg = new DeviceRegistry();
        reg.setPersistence(new YmlDevicePersistence(tmp.getAbsolutePath()));
        DeviceBase d1 = newDevice("e1", "u1", "com.ecat:c");
        reg.getOrCreate(d1, DeviceLifecycleEvent.Action.CREATE);
        String id1 = d1.getId();
        DeviceBase d2 = newDevice("e1", "u1", "com.ecat:c");
        reg.getOrCreate(d2, DeviceLifecycleEvent.Action.CREATE);
        assertEquals("同 (coordinate,uniqueId) 应复用同一持久化 id", id1, d2.getId());
    }

    @Test
    public void load_repopulatesMatchIndex_acrossRestart() throws Exception {
        File tmp = Files.createTempDirectory("ecat-reg-load").toFile();
        DeviceRegistry reg1 = new DeviceRegistry();
        reg1.setPersistence(new YmlDevicePersistence(tmp.getAbsolutePath()));
        DeviceBase d = newDevice("e1", "u1", "com.ecat:c");
        reg1.getOrCreate(d, DeviceLifecycleEvent.Action.CREATE);
        String persistedId = d.getId();

        // 模拟重启：新 registry 从同一 yml 目录 load
        DeviceRegistry reg2 = new DeviceRegistry();
        reg2.setPersistence(new YmlDevicePersistence(tmp.getAbsolutePath()));
        reg2.load();
        DeviceBase d2 = newDevice("e1", "u1", "com.ecat:c");
        reg2.getOrCreate(d2, DeviceLifecycleEvent.Action.CREATE);
        assertEquals("重启后 getOrCreate 应复原同一持久化 id", persistedId, d2.getId());
    }

    @Test
    public void register_publishesCreate_unregisterPublishesRemove() {
        DeviceRegistry reg = new DeviceRegistry();
        BusRegistry bus = new BusRegistry();
        reg.setBusRegistry(bus);
        final AtomicReference<DeviceLifecycleEvent> seen = new AtomicReference<>();
        bus.subscribe(BusTopic.DEVICE_LIFECYCLE.getTopicName(), event -> {
            if (event.getPayload() instanceof DeviceLifecycleEvent) {
                seen.set((DeviceLifecycleEvent) event.getPayload());
            }
        });
        DeviceBase d = newDevice("e1", "u1", "com.ecat:c");
        reg.register(d, DeviceLifecycleEvent.Action.CREATE);
        assertNotNull(seen.get());
        assertEquals(DeviceLifecycleEvent.Action.CREATE, seen.get().getAction());
        assertEquals(d.getId(), seen.get().getDeviceId());

        seen.set(null);
        reg.unregister(d, false);
        assertNotNull("软 unregister 也应发 REMOVE", seen.get());
        assertEquals(DeviceLifecycleEvent.Action.REMOVE, seen.get().getAction());
    }

    @Test
    public void unregister_softKeepsRecord_hardDeletes() throws Exception {
        File tmp = Files.createTempDirectory("ecat-reg-unreg").toFile();
        YmlDevicePersistence p = new YmlDevicePersistence(tmp.getAbsolutePath());
        DeviceRegistry reg = new DeviceRegistry();
        reg.setPersistence(p);
        DeviceBase d = newDevice("e1", "u1", "com.ecat:c");
        reg.getOrCreate(d, DeviceLifecycleEvent.Action.CREATE);
        File yml = new File(tmp, "com.ecat/c/" + d.getId() + ".yml");
        assertTrue(yml.exists());

        reg.unregister(d, false);
        assertTrue("软移除应保留 yml", yml.exists());

        reg.register(d, DeviceLifecycleEvent.Action.CREATE);
        reg.unregister(d, true);
        assertFalse("硬删除应移除 yml", yml.exists());
    }

    @Test
    public void findDevicesByEntryId_returnsAllChildren() {
        DeviceRegistry reg = new DeviceRegistry();
        DeviceBase c1 = newDevice("gw-1", "child-1", "com.ecat:c");
        DeviceBase c2 = newDevice("gw-1", "child-2", "com.ecat:c");
        DeviceBase other = newDevice("ent-other", "u-other", "com.ecat:c");
        reg.register(c1, DeviceLifecycleEvent.Action.CREATE);
        reg.register(c2, DeviceLifecycleEvent.Action.CREATE);
        reg.register(other, DeviceLifecycleEvent.Action.CREATE);
        assertEquals(2, reg.findDevicesByEntryId("gw-1").size());
        assertEquals(1, reg.findDevicesByEntryId("ent-other").size());
    }

    @Test
    public void softRemove_dropsFromRegistry_silently() {
        DeviceRegistry reg = new DeviceRegistry();
        DeviceBase d = newDevice("e1", "u1", "com.ecat:c");
        reg.register(d, DeviceLifecycleEvent.Action.CREATE);
        assertSame(d, reg.getDeviceByID(d.getId()));
        reg.softRemove(d);
        assertNull("softRemove 后应从 registry 移除", reg.getDeviceByID(d.getId()));
    }
}
