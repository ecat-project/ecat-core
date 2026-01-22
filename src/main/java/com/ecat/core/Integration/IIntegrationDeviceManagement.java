package com.ecat.core.Integration;

import java.util.Collection;
import java.util.Map;

import com.ecat.core.Device.DeviceBase;
import com.ecat.core.Utils.DynamicConfig.ConfigDefinition;

public interface IIntegrationDeviceManagement {
    
    /**
     * addDevice to Integration
     * @param device
     * @return
     */
    boolean addDevice(DeviceBase device);

    /**
     * createDevice to Integration from config
     * @param config
     * @return
     */
    boolean createDevice(Map<String, Object> config);

    
    /**
     * get all devices in Integration
     * @return
     */
    Collection<DeviceBase> getAllDevices();

    /**
     * get ConfigDefinition of Device in Integration
     * @return
     */
    ConfigDefinition getDeviceConfigDefinition();
}
