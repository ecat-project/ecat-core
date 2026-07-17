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
 * 物理设备注册表，管理所有物理设备对象的注册与访问。
 *
 * <p><b>调用方建议优先使用 {@link UnifiedDeviceStore}</b>——它聚合物理设备表与逻辑设备表
 * ({@link com.ecat.core.LogicDevice.LogicDeviceRegistry})，能跨表统一查询，覆盖更全面；
 * 仅当确需限定在物理设备范围内查询时，才直接使用本表。
 *
 * @author coffee
 */
public class DeviceRegistry implements IDeviceRegistry {

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
     * 注销设备
     *
     * @param deviceID 设备ID
     */
    public void unregister(String deviceID) {
        registry.remove(deviceID);
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

    /**
     * {@inheritDoc}
     *
     * <p>实现：遍历本表设备按 {@link DeviceBase#getUniqueId()} 匹配（uniqueId 在 entry 创建时由
     * {@code ConfigEntryRegistry} 强制全局唯一）。
     *
     * @throws IllegalArgumentException uniqueId 为 null 或空串
     */
    @Override
    public DeviceBase getDeviceByUniqueId(String uniqueId) {
        if (uniqueId == null || uniqueId.isEmpty()) {
            throw new IllegalArgumentException("uniqueId 不能为 null 或空串");
        }
        for (DeviceBase device : registry.values()) {
            if (uniqueId.equals(device.getUniqueId())) {
                return device;
            }
        }
        return null;
    }

    /**
     * {@inheritDoc}
     *
     * @throws IllegalArgumentException coordinate 为 null 或空串
     */
    @Override
    public List<DeviceBase> getDevicesByCoordinate(String coordinate) {
        if (coordinate == null || coordinate.isEmpty()) {
            throw new IllegalArgumentException("coordinate 不能为 null 或空串");
        }
        List<DeviceBase> result = new ArrayList<>();
        for (DeviceBase device : registry.values()) {
            if (coordinate.equals(device.getCoordinate())) {
                result.add(device);
            }
        }
        return result;
    }

    // todo: query devices by class

    // todo: query devices by ability

}
