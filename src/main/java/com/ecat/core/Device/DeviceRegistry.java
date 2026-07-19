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

import com.ecat.core.Bus.BusRegistry;
import com.ecat.core.Bus.BusTopic;
import com.ecat.core.Bus.event.BusEvent;
import com.ecat.core.Bus.event.DeviceLifecycleEvent;
import com.ecat.core.Bus.event.EventContext;

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

    /** (coordinate, uniqueId) → id 匹配索引：启动 {@link #load()} 从 device yml 建立，供 getOrCreate 跨重启稳定 id。 */
    private final Map<String, String> matchIndex = new HashMap<>();

    /** 设备持久化（device yml），由 EcatCore.init 注入（可空→仅内存模式，不发事件不落盘）。 */
    private DevicePersistence persistence;

    /** 总线，用于发布 DEVICE_LIFECYCLE（由 EcatCore.init 注入，可空→不发事件）。 */
    private BusRegistry busRegistry;

    /** 注入持久化层（EcatCore.init 调用）。 */
    public void setPersistence(DevicePersistence persistence) { this.persistence = persistence; }

    /** 注入总线（EcatCore.init 调用）。 */
    public void setBusRegistry(BusRegistry busRegistry) { this.busRegistry = busRegistry; }

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

    /** 00-core：按 coordinate 域化查找设备（uniqueId 仅 coordinate 内唯一）。 */
    @Override
    public DeviceBase getDeviceByUniqueId(String coordinate, String uniqueId) {
        if (coordinate == null || uniqueId == null || uniqueId.isEmpty()) {
            return null;
        }
        for (DeviceBase device : registry.values()) {
            if (coordinate.equals(device.getCoordinate()) && uniqueId.equals(device.getUniqueId())) {
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

    // ==================== 00-core：deviceId 稳定 + DEVICE_LIFECYCLE + 1:N 级联 ====================

    /** 匹配键：coordinate 与 uniqueId 用 SOH 分隔（二者均不会含 SOH）。 */
    static String matchKey(String coordinate, String uniqueId) {
        return (coordinate == null ? "" : coordinate) + "" + (uniqueId == null ? "" : uniqueId);
    }

    /**
     * 启动加载：读全部 device yml 建 (coordinate, uniqueId) → id 匹配索引。
     * <p>必须在 IntegrationManager.loadExistingConfigEntries（createEntry→restore）之前完成
     * （EcatCore.init 内调用）。这样 getOrCreate 在 createEntry 时能命中持久化 id 覆盖构造默认 UUID。
     */
    public void load() {
        if (persistence == null) {
            return;
        }
        matchIndex.clear();
        for (DeviceRecord r : persistence.loadAll()) {
            matchIndex.put(matchKey(r.getCoordinate(), r.getUniqueId()), r.getId());
        }
    }

    /**
     * 解析稳定 deviceId 并注册。命中 matchIndex（同 coordinate+uniqueId）则用 {@link DeviceBase#setId}
     * 覆盖构造铸造的默认 UUID，否则保留新 UUID；随后 {@link #register(DeviceBase, Action)} 落库+建索引+发事件。
     * <p>跨重启稳定的核心：重启后构造铸造新 UUID，getOrCreate 用持久化 id 覆盖 → deviceId 不变。
     *
     * @param device 待注册设备（getId 已被构造铸造为默认 UUID）
     * @param action 事件意图（create/enable→CREATE，reconfigure→RECONFIGURE）
     * @return 注册后的设备（id 已解析为稳定值）
     */
    public DeviceBase getOrCreate(DeviceBase device, DeviceLifecycleEvent.Action action) {
        String existing = matchIndex.get(matchKey(device.getCoordinate(), device.getUniqueId()));
        if (existing != null) {
            device.setId(existing); // 包级私有 setId：同包 DeviceRegistry 可调，覆盖构造默认 UUID
        }
        register(device, action);
        return device;
    }

    /**
     * 注册：map.put + matchIndex + 持久化 + 在 put 完成后发 DEVICE_LIFECYCLE(action)。
     * <p>与旧 {@link #register(String, DeviceBase)} 共存——旧方法供未迁移调用方使用（Task 7 接线后逐步切到本方法）。
     */
    public void register(DeviceBase device, DeviceLifecycleEvent.Action action) {
        registry.put(device.getId(), device);
        matchIndex.put(matchKey(device.getCoordinate(), device.getUniqueId()), device.getId());
        if (persistence != null) {
            persistence.save(toRecord(device));
        }
        publish(device, action);
    }

    /**
     * 注销：从 map 移除；deleteRecord=true 真实删除（删 yml + matchIndex），false 软移除（保记录，供 enable 恢复）。
     * 两者都发 REMOVE（设备从活跃系统消失，订阅方按离线处理）。
     */
    public void unregister(DeviceBase device, boolean deleteRecord) {
        registry.remove(device.getId());
        if (deleteRecord) {
            if (persistence != null) {
                persistence.delete(device.getId());
            }
            matchIndex.remove(matchKey(device.getCoordinate(), device.getUniqueId()));
        }
        publish(device, DeviceLifecycleEvent.Action.REMOVE);
    }

    /**
     * config entry 的 reconfigure 专用软移除：仅从内存 map 移除旧对象，不发事件、不删记录、不动 matchIndex。
     * <p>随后新设备 register 时命中保留的 matchIndex 复原同 id，并按编排意图发 RECONFIGURE（见 IntegrationDeviceBase）。
     */
    public void softRemove(DeviceBase device) {
        registry.remove(device.getId());
    }

    /**
     * 1:N 级联用：枚举某 ConfigEntry 关联的全部设备（按 device.getEntry().getEntryId() 反查）。
     * <p>网关 entry 的 N 个子设备 entry 均回指网关 entryId（1:N back-ref），故可一次枚举。
     */
    public List<DeviceBase> findDevicesByEntryId(String entryId) {
        List<DeviceBase> result = new ArrayList<>();
        for (DeviceBase d : registry.values()) {
            if (d.getEntry() != null && entryId.equals(d.getEntry().getEntryId())) {
                result.add(d);
            }
        }
        return result;
    }

    private DeviceRecord toRecord(DeviceBase d) {
        String entryId = d.getEntry() != null ? d.getEntry().getEntryId() : null;
        return DeviceRecord.builder()
                .id(d.getId())
                .coordinate(d.getCoordinate())
                .uniqueId(d.getUniqueId())
                .entryId(entryId)
                .name(d.getName())
                .vendor(d.getVendor())
                .model(d.getModel())
                .createTime(com.ecat.core.Utils.DateTimeUtils.now())
                .updateTime(com.ecat.core.Utils.DateTimeUtils.now())
                .build();
    }

    private void publish(DeviceBase d, DeviceLifecycleEvent.Action action) {
        if (busRegistry == null) {
            return;
        }
        String entryId = d.getEntry() != null ? d.getEntry().getEntryId() : null;
        DeviceLifecycleEvent event = new DeviceLifecycleEvent(d.getId(), d.getCoordinate(), entryId, action);
        busRegistry.publish(BusEvent.of(
                BusTopic.DEVICE_LIFECYCLE.getTopicName(), event,
                EventContext.root(EventContext.Source.SYSTEM, null)));
    }

}
