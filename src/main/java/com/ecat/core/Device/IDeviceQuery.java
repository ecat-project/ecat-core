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
 * <p>既被可写注册表 {@link IDeviceRegistry}（物理设备表、逻辑设备表）继承，
 * 也被只读聚合门面 {@link UnifiedDeviceStore} 实现——两者共享同一套查询契约，
 * 调用方可面向本接口编程，按需传入具体注册表或统一门面。
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
     * 根据设备业务唯一标识（uniqueId）获取设备对象。
     *
     * <p>uniqueId 是设备的业务标识（由集成生成，如 {@code 厂家_sn}），与 {@link #getDeviceByID(String)}
     * 使用的 entryId 不同——entryId 是系统生成的 UUID（见 {@link DeviceBase#getId()} 与
     * {@link DeviceBase#getUniqueId()} 的区别）。因此用 uniqueId 调 {@link #getDeviceByID} 会返回 null，
     * 必须改用本方法。
     *
     * <p>找不到返回 null（正常查询 miss，非异常）；入参为 null/空串属于调用方契约违规，抛
     * {@link IllegalArgumentException}。
     *
     * @param uniqueId 设备业务唯一标识
     * @return 设备对象，不存在则返回 null
     */
    DeviceBase getDeviceByUniqueId(String uniqueId);

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
