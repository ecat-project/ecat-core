package com.ecat.core.Device;

import com.ecat.core.ConfigEntry.ConfigEntry;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

/**
 * 00-core Task 1：DeviceBase 身份字段——id 铸造 UUID、uniqueId 字段化、网关构造器。
 */
public class DeviceBaseIdentityTest {

    /** 最小可实例化桩：实现 DeviceControl 的抽象方法（load 由 DeviceBase 实现） */
    public static class StubDevice extends DeviceBase {
        public StubDevice(ConfigEntry e) { super(e); }
        public StubDevice(ConfigEntry e, String uniqueId, Map<String, Object> config) { super(e, uniqueId, config); }
        @Override public void init() {}
        @Override public void start() {}
        @Override public void stop() {}
        @Override public void release() {}
    }

    @Test
    public void entryBacked_ctorMintsUuidId_andUniqueIdFromEntry() {
        ConfigEntry e = new ConfigEntry.Builder()
                .entryId("ent-1").coordinate("com.ecat:t").uniqueId("sn-999").build();
        StubDevice d = new StubDevice(e);
        assertNotNull("id 必须被铸造", d.getId());
        assertNotEquals("id 必须不同于 entryId（解耦）", "ent-1", d.getId());
        assertEquals("entry-backed uniqueId 取自 entry", "sn-999", d.getUniqueId());
    }

    @Test
    public void gateway_ctorMintsId_uniqueIdFromParam_entryIsGateway() {
        ConfigEntry gw = new ConfigEntry.Builder()
                .entryId("gw-1").coordinate("com.ecat:t").uniqueId("gw-sn").build();
        StubDevice child = new StubDevice(gw, "child-sn-1", new java.util.HashMap<>());
        assertNotNull("网关子设备 id 必须被铸造", child.getId());
        assertEquals("子设备 uniqueId 取自入参，非网关 entry 的", "child-sn-1", child.getUniqueId());
        assertEquals("entry 是网关的（1:N back-ref）", "gw-1", child.getEntry().getEntryId());
    }

    @Test
    public void twoDevices_haveDistinctIds() {
        ConfigEntry e = new ConfigEntry.Builder()
                .entryId("ent-1").coordinate("com.ecat:t").uniqueId("sn-1").build();
        assertNotEquals("每台设备铸造各自 UUID", new StubDevice(e).getId(), new StubDevice(e).getId());
    }
}
