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
 * 设备注册表只读查询接口，定义设备查询的标准方法（不含注册/注销等写操作）。
 *
 * <p>Phase 3 统一后由 {@link DeviceRegistry} 实现（物理 + 逻辑设备同表；原 IDeviceRegistry/
 * UnifiedDeviceStore 已删）。调用方面向本接口编程，按 (coordinate, uniqueId) 域化查询。
 *
 * @author coffee
 */
public interface IDeviceQuery {

    /**
     * 根据设备ID获取设备对象。
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

    /**
     * 按集成坐标（coordinate）域化查找设备。
     *
     * <p>uniqueId 是设备的业务标识（由集成生成，如 {@code 厂家_sn}），仅在 coordinate 内唯一（00-core
     * 域化约束），故查询必须带 coordinate。uniqueId 与 {@link #getDeviceByID(String)} 使用的 deviceId
     * 不同——deviceId 是 core 铸造的设备主键（全局稳定），用 uniqueId 调 {@code getDeviceByID} 会返回 null。
     *
     * <p>找不到返回 null（正常查询 miss，非异常）；coordinate/uniqueId 为 null 或 uniqueId 为空串时返回 null。
     *
     * @param coordinate 集成坐标（域）
     * @param uniqueId   设备业务唯一标识（coordinate 内唯一）
     * @return 设备对象，不存在则返回 null
     */
    DeviceBase getDeviceByUniqueId(String coordinate, String uniqueId);

    /**
     * 根据集成坐标（coordinate）获取该集成下的全部设备。
     *
     * <p>coordinate 形如 {@code groupId:artifactId}（如 {@code com.ecat:integration-hikvision}），
     * 即设备所属集成的标识，见 {@link DeviceBase#getCoordinate()}。
     *
     * <p>无匹配返回空列表（非 null）；入参为 null/空串抛 {@link IllegalArgumentException}。
     *
     * @param coordinate 集成坐标
     * @return 该坐标下所有设备的新列表（副本）
     */
    List<DeviceBase> getDevicesByCoordinate(String coordinate);
}
