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
import java.util.HashMap;
import java.util.Map;

import com.ecat.core.EcatCore;
import com.ecat.core.Device.DeviceBase;
import com.ecat.core.Utils.DynamicConfig.ConfigDefinition;

/**
 * IntegrationDeviceBase
 * 针对含义设备管理的集成基类
 * 
 * @author coffee
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
