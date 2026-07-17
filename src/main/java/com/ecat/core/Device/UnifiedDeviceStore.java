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
import java.util.List;

/**
 * 统一设备存储，提供跨多个设备注册表的只读查询能力。
 *
 * <p>通过聚合多个 {@link IDeviceRegistry} 实例（如 DeviceRegistry、LogicDeviceRegistry），
 * 提供统一的设备查询入口，使消费者无需关心设备存储在哪个注册表中。
 *
 * <p>使用方式：
 * <pre>
 *   UnifiedDeviceStore store = new UnifiedDeviceStore();
 *   store.addRegistry(deviceRegistry);
 *   store.addRegistry(logicDeviceRegistry);
 *
 *   // 查询所有设备（物理 + 逻辑）
 *   List&lt;DeviceBase&gt; all = store.getAllDevices();
 *
 *   // 按 ID 查询（自动在所有注册表中查找）
 *   DeviceBase device = store.getDeviceByID("some-id");
 * </pre>
 *
 * <p>注意：此类只提供查询功能（{@link IDeviceQuery}），不提供 register/unregister 操作。
 * 写入操作由各注册表自行管理。
 *
 * @author coffee
 */
public class UnifiedDeviceStore implements IDeviceQuery {

    private final List<IDeviceRegistry> registries = new ArrayList<>();

    /**
     * 添加一个设备注册表到统一存储中。
     *
     * <p>支持运行时动态添加，用于未来扩展新的设备类型注册表。
     *
     * @param registry 设备注册表实例
     */
    public void addRegistry(IDeviceRegistry registry) {
        this.registries.add(registry);
    }

    /**
     * 根据设备ID在所有注册表中查找设备。
     *
     * <p>按注册表添加顺序依次查找，返回第一个匹配的设备。
     *
     * @param deviceID 设备ID
     * @return 设备对象，如果在所有注册表中都不存在则返回 null
     */
    @Override
    public DeviceBase getDeviceByID(String deviceID) {
        for (IDeviceRegistry r : registries) {
            DeviceBase d = r.getDeviceByID(deviceID);
            if (d != null) return d;
        }
        return null;
    }

    /**
     * 获取所有注册表中的所有设备。
     *
     * <p>合并所有注册表中的设备列表，返回一个新的列表（修改不影响任何注册表内部状态）。
     *
     * @return 所有设备的新列表
     */
    @Override
    public List<DeviceBase> getAllDevices() {
        List<DeviceBase> all = new ArrayList<>();
        for (IDeviceRegistry r : registries) {
            all.addAll(r.getAllDevices());
        }
        return all;
    }

    /**
     * 根据设备业务唯一标识（uniqueId）在所有注册表中查找设备。
     *
     * <p>按注册表添加顺序依次查找，返回第一个匹配的设备（与 {@link #getDeviceByID(String)} 同策略）。
     * 入参 null/空串的校验由各注册表实现强制（抛 {@link IllegalArgumentException}）。
     *
     * @param uniqueId 设备业务唯一标识
     * @return 设备对象，所有注册表均无匹配则返回 null
     */
    @Override
    public DeviceBase getDeviceByUniqueId(String uniqueId) {
        for (IDeviceRegistry r : registries) {
            DeviceBase d = r.getDeviceByUniqueId(uniqueId);
            if (d != null) {
                return d;
            }
        }
        return null;
    }

    /**
     * 获取所有注册表中属于指定集成坐标（coordinate）的设备。
     *
     * <p>合并各注册表 {@link IDeviceRegistry#getDevicesByCoordinate(String)} 的结果，跨物理与逻辑设备表。
     * 入参 null/空串的校验由各注册表实现强制（抛 {@link IllegalArgumentException}）。
     *
     * @param coordinate 集成坐标
     * @return 该坐标下所有设备的新列表
     */
    @Override
    public List<DeviceBase> getDevicesByCoordinate(String coordinate) {
        List<DeviceBase> all = new ArrayList<>();
        for (IDeviceRegistry r : registries) {
            all.addAll(r.getDevicesByCoordinate(coordinate));
        }
        return all;
    }
}
