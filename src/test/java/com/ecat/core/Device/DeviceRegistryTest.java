package com.ecat.core.Device;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import java.util.List;

/**
 * DeviceRegistryTest is a unit test class for testing the DeviceRegistry functionality.
 * 
 * @author coffee
 */
public class DeviceRegistryTest {

    private DeviceRegistry deviceRegistry;

    @Before
    public void setUp() {
        deviceRegistry = new DeviceRegistry();
    }

    // 测试注册和获取设备
    @Test
    public void testRegisterAndGetDeviceByID() {
        String deviceID = "testDevice";
        // 使用Mockito创建DeviceBase的模拟对象
        DeviceBase mockDevice = mock(DeviceBase.class);
        
        // 注册设备
        deviceRegistry.register(deviceID, mockDevice);
        
        // 获取设备并验证
        DeviceBase retrievedDevice = deviceRegistry.getDeviceByID(deviceID);
        assertNotNull(retrievedDevice);
        assertSame(mockDevice, retrievedDevice);
    }

    // 测试获取不存在的设备
    @Test
    public void testGetDeviceByID_NonExistent() {
        DeviceBase device = deviceRegistry.getDeviceByID("nonExistent");
        assertNull(device);
    }

    // 测试获取所有设备
    @Test
    public void testGetAllDevices() {
        // 创建多个模拟设备
        DeviceBase device1 = mock(DeviceBase.class);
        DeviceBase device2 = mock(DeviceBase.class);
        
        // 注册设备
        deviceRegistry.register("device1", device1);
        deviceRegistry.register("device2", device2);
        
        // 获取所有设备并验证
        List<DeviceBase> allDevices = deviceRegistry.getAllDevices();
        assertEquals(2, allDevices.size());
        assertTrue(allDevices.contains(device1));
        assertTrue(allDevices.contains(device2));
    }

    // 测试注册相同ID的设备
    @Test
    public void testRegisterDuplicateDeviceID() {
        String deviceID = "duplicateID";
        DeviceBase device1 = mock(DeviceBase.class);
        DeviceBase device2 = mock(DeviceBase.class);
        
        // 先注册一个设备
        deviceRegistry.register(deviceID, device1);
        // 使用相同ID注册另一个设备
        deviceRegistry.register(deviceID, device2);
        
        // 验证最后注册的设备被保留
        assertSame(device2, deviceRegistry.getDeviceByID(deviceID));
    }
}