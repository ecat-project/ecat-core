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

package com.ecat.core.Device;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备注册表类，用于管理系统中所有设备对象的注册和访问
 * 
 * @author coffee
 */
public class DeviceRegistry {

    /**
     * 使用Map结构存储设备ID和设备对象的映射关系
     * Key为设备ID(String类型)，Value为设备对象(DeviceBase类型)
     */
    private final Map<String, DeviceBase> registry = new HashMap<>();

    // 注册设备
    // @param deviceID 设备ID，Core内唯一
    // @param integration 设备对象
    // @return void
    public void register(String deviceID, DeviceBase integration) {
        
        registry.put(deviceID, integration);
    }

    /**
     * 根据设备ID获取设备对象
     * @param deviceID 设备ID
     * @return 对应的设备对象，如果不存在则返回null
     */
    public DeviceBase getDeviceByID(String deviceID) {
        return registry.get(deviceID);
    }

    /**
     * 获取系统中所有设备的列表
     * @return 包含所有设备对象的列表
     */
    public List<DeviceBase> getAllDevices() {
        return new ArrayList<>(registry.values());
    }

    // todo: query devices by class

    // todo: query devices by ability

}
