package com.ecat.core.Device;

import com.ecat.core.LogicDevice.LogicDeviceRegistry;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * IDeviceRegistry 接口契约测试
 * 验证 DeviceRegistry 和 LogicDeviceRegistry 都实现了 IDeviceRegistry 接口
 */
public class IDeviceRegistryTest {

    @Test
    public void testDeviceRegistryImplementsInterface() {
        IDeviceRegistry registry = new DeviceRegistry();
        assertNotNull(registry);
    }

    @Test
    public void testLogicDeviceRegistryImplementsInterface() {
        IDeviceRegistry registry = new LogicDeviceRegistry();
        assertNotNull(registry);
    }

    @Test
    public void testRegistriesAreReadableAsIDeviceQuery() {
        // 可写注册表同时满足只读契约 IDeviceQuery（IDeviceRegistry extends IDeviceQuery）
        assertTrue(new DeviceRegistry() instanceof IDeviceQuery);
        assertTrue(new LogicDeviceRegistry() instanceof IDeviceQuery);
    }

    @Test
    public void testInterfaceMethods() {
        IDeviceRegistry registry = new DeviceRegistry();
        DeviceBase device = TestPhyDeviceHelper.createDevice("test-id");

        registry.register("test-id", device);
        assertSame(device, registry.getDeviceByID("test-id"));

        java.util.List<DeviceBase> all = registry.getAllDevices();
        assertEquals(1, all.size());

        registry.unregister("test-id");
        assertNull(registry.getDeviceByID("test-id"));
    }

    // ========== getDeviceByUniqueId 契约（两实现通用） ==========

    @Test
    public void testGetDeviceByUniqueId_DeviceRegistry_Found() {
        IDeviceRegistry registry = new DeviceRegistry();
        // entryId 与 uniqueId 故意不同：证明本方法按 uniqueId 查而非 entryId
        DeviceBase device = TestPhyDeviceHelper.createDevice("entry-1", "sn-001", "com.ecat:x");
        registry.register("entry-1", device);

        assertSame(device, registry.getDeviceByUniqueId("sn-001"));
        // 用 uniqueId 调 getDeviceByID 应返回 null（key 是 entryId），印证两套查询的区别
        assertNull(registry.getDeviceByID("sn-001"));
    }

    @Test
    public void testGetDeviceByUniqueId_LogicDeviceRegistry_Found() {
        IDeviceRegistry registry = new LogicDeviceRegistry();
        DeviceBase device = TestPhyDeviceHelper.createDevice("entry-2", "sn-002", "com.ecat:y");
        registry.register("entry-2", device);

        assertSame(device, registry.getDeviceByUniqueId("sn-002"));
    }

    @Test
    public void testGetDeviceByUniqueId_NotFound_ReturnsNull() {
        IDeviceRegistry registry = new DeviceRegistry();
        registry.register("entry-1", TestPhyDeviceHelper.createDevice("entry-1", "sn-001", "com.ecat:x"));

        assertNull(registry.getDeviceByUniqueId("nonexistent-sn"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetDeviceByUniqueId_NullInput_Throws() {
        new DeviceRegistry().getDeviceByUniqueId(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetDeviceByUniqueId_EmptyInput_Throws() {
        new DeviceRegistry().getDeviceByUniqueId("");
    }

    // ========== getDevicesByCoordinate 契约 ==========

    @Test
    public void testGetDevicesByCoordinate_ReturnsAllMatching() {
        IDeviceRegistry registry = new DeviceRegistry();
        DeviceBase d1 = TestPhyDeviceHelper.createDevice("e1", "u1", "com.ecat:x");
        DeviceBase d2 = TestPhyDeviceHelper.createDevice("e2", "u2", "com.ecat:x");
        DeviceBase d3 = TestPhyDeviceHelper.createDevice("e3", "u3", "com.ecat:y");
        registry.register("e1", d1);
        registry.register("e2", d2);
        registry.register("e3", d3);

        java.util.List<DeviceBase> xs = registry.getDevicesByCoordinate("com.ecat:x");
        assertEquals(2, xs.size());
        assertTrue(xs.contains(d1));
        assertTrue(xs.contains(d2));
    }

    @Test
    public void testGetDevicesByCoordinate_LogicDeviceRegistry() {
        IDeviceRegistry registry = new LogicDeviceRegistry();
        registry.register("e1", TestPhyDeviceHelper.createDevice("e1", "u1", "com.ecat:logic"));

        assertEquals(1, registry.getDevicesByCoordinate("com.ecat:logic").size());
    }

    @Test
    public void testGetDevicesByCoordinate_NoMatch_ReturnsEmptyList() {
        IDeviceRegistry registry = new DeviceRegistry();
        registry.register("e1", TestPhyDeviceHelper.createDevice("e1", "u1", "com.ecat:x"));

        java.util.List<DeviceBase> result = registry.getDevicesByCoordinate("com.ecat:z");
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    public void testGetDevicesByCoordinate_ReturnsMutableCopy() {
        IDeviceRegistry registry = new DeviceRegistry();
        registry.register("e1", TestPhyDeviceHelper.createDevice("e1", "u1", "com.ecat:x"));

        java.util.List<DeviceBase> first = registry.getDevicesByCoordinate("com.ecat:x");
        first.clear();
        // 清掉返回副本不应影响注册表内部
        assertEquals(1, registry.getDevicesByCoordinate("com.ecat:x").size());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetDevicesByCoordinate_NullInput_Throws() {
        new DeviceRegistry().getDevicesByCoordinate(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetDevicesByCoordinate_EmptyInput_Throws() {
        new DeviceRegistry().getDevicesByCoordinate("");
    }
}
