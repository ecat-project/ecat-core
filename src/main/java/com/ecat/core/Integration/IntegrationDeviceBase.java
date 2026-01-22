package com.ecat.core.Integration;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import com.ecat.core.EcatCore;
import com.ecat.core.Device.DeviceBase;
import com.ecat.core.Utils.DynamicConfig.ConfigDefinition;

/**
 * IntegrationDeviceBase
 * 针对含义设备管理的集成基类
 * 
 */
public abstract class IntegrationDeviceBase extends IntegrationBase implements IIntegrationDeviceManagement {

    protected Map<String, DeviceBase> devices = new HashMap<>();
    protected ConfigDefinition deviceConfigDefinition;

    @Override
    public void onLoad(EcatCore core, IntegrationLoadOption loadOption) {
        super.onLoad(core, loadOption);
        deviceConfigDefinition = getDeviceConfigDefinition();
    }

    @Override
    public boolean addDevice(DeviceBase device) {
        if (devices.containsKey(device.getId())) {
            return true;
        }
        devices.put(device.getId(), device);
        device.setIntegration(this);
        deviceRegistry.register(device.getId(), device);
        return true;
    }

    @Override
    public Collection<DeviceBase> getAllDevices() {
        return devices.values();
    }

    @Override
    public boolean createDevice(Map<String, Object> config) {
        throw new UnsupportedOperationException("createDevice not implemented");
    }
    
}
