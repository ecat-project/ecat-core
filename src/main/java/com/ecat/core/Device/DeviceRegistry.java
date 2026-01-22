package com.ecat.core.Device;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class DeviceRegistry {

    private final Map<String, DeviceBase> registry = new HashMap<>();

    // 注册设备
    // @param deviceID 设备ID，Core内唯一
    // @param integration 设备对象
    // @return void
    public void register(String deviceID, DeviceBase integration) {
        
        registry.put(deviceID, integration);
    }

    public DeviceBase getDeviceByID(String deviceID) {
        return registry.get(deviceID);
    }

    public List<DeviceBase> getAllDevices() {
        return new ArrayList<>(registry.values());
    }

    // todo: query devices by class

    // todo: query devices by ability

}