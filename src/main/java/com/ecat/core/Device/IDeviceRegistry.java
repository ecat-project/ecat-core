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

/**
 * 可写设备注册表接口：在只读查询契约 {@link IDeviceQuery} 基础上增加注册/注销写操作。
 *
 * <p>所有可写设备注册表（物理设备、逻辑设备等）都应实现此接口，
 * 以便 {@link UnifiedDeviceStore} 可以统一聚合查询所有设备。
 * 只读消费方应面向 {@link IDeviceQuery} 编程，避免误用写操作。
 *
 * @author coffee
 */
public interface IDeviceRegistry extends IDeviceQuery {

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
}
