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
}
