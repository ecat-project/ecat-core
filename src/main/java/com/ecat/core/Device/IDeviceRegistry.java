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

import java.util.List;

/**
 * 设备注册表公共接口，定义设备注册、注销和查询的标准方法。
 *
 * <p>所有设备注册表（物理设备、逻辑设备等）都应实现此接口，
 * 以便 UnifiedDeviceStore 可以统一查询所有设备。
 *
 * @author coffee
 */
public interface IDeviceRegistry {

    /**
     * 注册设备到注册表
     *
     * @param deviceID 设备ID，在注册表内唯一
     * @param device   设备对象
     */
    void register(String deviceID, DeviceBase device);

    /**
     * 注销设备
     *
     * @param deviceID 设备ID
     */
    void unregister(String deviceID);

    /**
     * 根据设备ID获取设备对象
     *
     * @param deviceID 设备ID
     * @return 设备对象，如果不存在则返回 null
     */
    DeviceBase getDeviceByID(String deviceID);

    /**
     * 获取注册表中所有设备的列表
     *
     * @return 所有设备的新列表（副本）
     */
    List<DeviceBase> getAllDevices();
}
