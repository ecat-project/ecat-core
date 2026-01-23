/*
 * Copyright (c) 2026 ECAT Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ecat.core.Integration;

import java.util.Collection;
import java.util.Map;

import com.ecat.core.Device.DeviceBase;
import com.ecat.core.Utils.DynamicConfig.ConfigDefinition;

/**
 * Interface for device type integration management
 * @author coffee
 */
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
