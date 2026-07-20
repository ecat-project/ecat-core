package com.ecat.core.Device;

import com.ecat.core.Bus.event.DeviceLifecycleEvent;
import com.ecat.core.ConfigEntry.ConfigEntry;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * DeviceRegistry 基础查询测试（注册经 getOrCreate，查询经 getDeviceByID/getAllDevices）。
 * <p>完整生命周期（replace/disable/remove/purge + matchIndex + 持久化）见 {@link DeviceRegistryGetOrCreateTest}。
 *
 * @author coffee
 */
public class DeviceRegistryTest {

    private DeviceRegistry deviceRegistry;

    @Before
    public void setUp() {
        deviceRegistry = new DeviceRegistry();
    }

    private DeviceBase newDevice(String entryId, String uniqueId) {
        ConfigEntry e = new ConfigEntry();
        e.setEntryId(entryId);
        e.setUniqueId(uniqueId);
        e.setCoordinate("com.ecat:c");
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
    public void testRegisterAndGetDeviceByID() {
        DeviceBase device = newDevice("e1", "u1");
        deviceRegistry.getOrCreate(device, DeviceLifecycleEvent.Action.CREATE);
        DeviceBase retrieved = deviceRegistry.getDeviceByID(device.getId());
        assertNotNull(retrieved);
        assertSame(device, retrieved);
    }

    @Test
    public void testGetDeviceByID_NonExistent() {
        assertNull(deviceRegistry.getDeviceByID("nonExistent"));
    }

    @Test
    public void testGetAllDevices() {
        DeviceBase d1 = newDevice("e1", "u1");
        DeviceBase d2 = newDevice("e2", "u2");
        deviceRegistry.getOrCreate(d1, DeviceLifecycleEvent.Action.CREATE);
        deviceRegistry.getOrCreate(d2, DeviceLifecycleEvent.Action.CREATE);
        List<DeviceBase> all = deviceRegistry.getAllDevices();
        assertEquals(2, all.size());
        assertTrue(all.contains(d1));
        assertTrue(all.contains(d2));
    }

    @Test
    public void testRegisterDuplicateDeviceID() {
        // 同 (coordinate,uniqueId) 再 getOrCreate 复用同一 id（覆盖旧实例）
        DeviceBase d1 = newDevice("e1", "u1");
        deviceRegistry.getOrCreate(d1, DeviceLifecycleEvent.Action.CREATE);
        DeviceBase d2 = newDevice("e1", "u1");
        deviceRegistry.getOrCreate(d2, DeviceLifecycleEvent.Action.CREATE);
        assertEquals("同 uniqueId 复用同一 id", d1.getId(), d2.getId());
        assertSame(d2, deviceRegistry.getDeviceByID(d1.getId()));
    }
}
