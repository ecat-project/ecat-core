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
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 统一设备注册表（物理 + 逻辑设备同表），管理所有设备对象的注册与访问。
 *
 * <p>Phase 3 起逻辑设备合并进本表（撤 LogicDevice.getId 豁免 + 经 getOrCreate 注册铸稳定 deviceId
 * + 持久化 + 发 DEVICE_LIFECYCLE）。本表是唯一个查询入口（{@link IDeviceQuery} 契约），
 * 按 (coordinate, uniqueId) 域化查询（uniqueId 仅 coordinate 内唯一）。
 *
 * @author coffee
 */
public class DeviceRegistry implements IDeviceQuery {

    /**
     * 使用Map结构存储设备ID和设备对象的映射关系
     * Key为设备ID(String类型)，Value为设备对象(DeviceBase类型)
     *
     * <p>并发安全（修 CME/丢注册竞态）：REST 线程（getDeviceByUniqueId/getAllDevices 迭代）、
     * ConfigFlow 提交线程与 bus consumer 线程（commit/register put）对本表并发读写且无外部互斥，
     * 裸 HashMap 在此组合下可抛 CME、并发 put 丢条目（JDK8 resize 并发结构损坏）。
     * ConcurrentHashMap 迭代弱一致（不抛 CME）+ 并发 put 不丢，注册表读多写少场景零争用损耗。
     */
    private final Map<String, DeviceBase> registry = new ConcurrentHashMap<>();

    /**
     * (coordinate, uniqueId) → id 匹配索引：启动 {@link #load()} 从 device yml 建立，供 getOrCreate 跨重启稳定 id。
     * 与 {@link #registry} 同理用 ConcurrentHashMap：load()（启动线程）clear+put 与运行期
     * commit/remove/purge（REST/consumer 线程）put/remove 并发。
     */
    private final Map<String, String> matchIndex = new ConcurrentHashMap<>();

    /** 设备持久化（device yml），由 EcatCore.init 注入（可空→仅内存模式，不发事件不落盘）。 */
    private DevicePersistence persistence;

    /** 总线，用于发布 DEVICE_LIFECYCLE（由 EcatCore.init 注入，可空→不发事件）。 */
    private BusRegistry busRegistry;

    /** entry 注册表，disable 时级联 setEnabled(false)（由 EcatCore.init 注入，可空→跳过级联）。 */
    private com.ecat.core.ConfigEntry.ConfigEntryRegistry entryRegistry;

    /** 注入持久化层（EcatCore.init 调用）。 */
    public void setPersistence(DevicePersistence persistence) { this.persistence = persistence; }

    /** 注入总线（EcatCore.init 调用）。 */
    public void setBusRegistry(BusRegistry busRegistry) { this.busRegistry = busRegistry; }

    /** 注入 entry 注册表（EcatCore.init 调用，disable 级联用）。 */
    public void setEntryRegistry(com.ecat.core.ConfigEntry.ConfigEntryRegistry entryRegistry) { this.entryRegistry = entryRegistry; }

    /**
     * 低级原语：按显式 deviceID 直接 put（不经 matchIndex/持久化/事件）。
     * <p>仅用于设备 getId() 与期望寻址键不同的场景（如 media 测试桩：DeviceBase(Map) 构造的设备 getId() 是随机 UUID，
     * 但 MediaService 按逻辑键 "camera-001" 查找，需显式键注册）。生产路径一律走 {@link #getOrCreate}
     * （解析稳定 id + 持久化 + 事件）或 {@link #replace}/{@link #disable}/{@link #purge}。
     *
     * @param deviceID    显式注册键（Core 内唯一）
     * @param integration 设备对象
     */
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
     * 解析稳定 deviceId（仅 matchIndex→setId，不 commit/不持久化/不发事件）。两个分支都正确：
     * <ul>
     *   <li><b>matchIndex 命中</b>（同 coordinate+uniqueId 曾注册过——重启恢复/reconfigure 重建）：
     *       setId(持久化稳定 id)，把构造铸造的临时 UUID 替换为跨重启稳定值。这是 type=null 根源场景：
     *       原 setId 发生在 getOrCreate（init 之后），midState 已烘焙临时 id；现前移到 DeviceBase.load
     *       （init 之前），midState 从诞生起带稳定 id。</li>
     *   <li><b>matchIndex 未命中</b>（首次创建，全新设备）：不做任何事，id 保留构造铸造的 UUID。
     *       该 UUID 虽随机生成，但此后永不再变（无后续 setId）→ 它就是这台新设备的稳定 id。
     *       buildState 烘焙它、commit 用它做 registry key，二者天然一致，消费侧反查命中。
     *       bug 本质不是"临时 UUID 不好"，而是"id 在 buildState 与 publish 之间被改写"；未命中分支无改写，天然正确。</li>
     * </ul>
     *
     * <p>幂等：load() 已解析过的设备，getOrCreate 再调结果相同（两者之间无 commit 改变 matchIndex）。
     * matchIndex 是 final 字段非 null；调用方 {@link DeviceBase#load} 已对 registry==null（mock core）做了守卫。
     * 设计文档 docs/2026-08-01-device-id-stable-before-state-design.md。
     */
    void resolveStableId(DeviceBase device) {
        String stableId = matchIndex.get(matchKey(device.getCoordinate(), device.getUniqueId()));
        if (stableId != null) {
            device.setId(stableId); // 包级私有 setId：命中持久化 id 时覆盖构造铸造的临时 UUID（前移到此，先于 init/buildState）
        }
        // 未命中：首次创建，构造 UUID 即稳定 id（永不再变），保留不动
    }

    /**
     * 解析稳定 deviceId 并注册。{@link #resolveStableId} 解析 id（DeviceBase.load 通常已解析过，此处幂等），
     * 随后 {@link #commit} 落库+建索引+发事件。
     * <p>跨重启稳定的核心：重启后构造铸造新 UUID，resolveStableId 用持久化 id 覆盖 → deviceId 不变。
     *
     * @param device 待注册设备（getId 已被构造铸造为默认 UUID；若 load 已调 resolveStableId 则已是稳定值）
     * @param action 事件意图（create/enable→CREATE，reconfigure→RECONFIGURE）
     * @return 注册后的设备（id 已解析为稳定值）
     */
    public DeviceBase getOrCreate(DeviceBase device, DeviceLifecycleEvent.Action action) {
        resolveStableId(device);
        commit(device, action);
        return device;
    }

    /**
     * 提交步（getOrCreate 与 replace 共用）：map.put + matchIndex + 持久化 + 发 DEVICE_LIFECYCLE(action)。
     * <p>包级私有——外部统一走 {@link #getOrCreate}（create/enable）或 {@link #replace}（reconfigure）。
     */
    private void commit(DeviceBase device, DeviceLifecycleEvent.Action action) {
        registry.put(device.getId(), device);
        matchIndex.put(matchKey(device.getCoordinate(), device.getUniqueId()), device.getId());
        if (persistence != null) {
            persistence.save(toRecord(device));
        }
        publish(device, action);
    }

    // ==================== Phase 4 正交化 API：replace / disable / remove / purge ====================

    /**
     * reconfigure 原子换装：newDevice 解析到与 oldDevice 相同的稳定 deviceId（同 coordinate+uniqueId 命中 matchIndex），
     * setId 覆盖构造铸造的默认 UUID → put 同 key 覆盖 old → 单 RECONFIGURE 事件（不双发 REMOVE→CREATE）。
     *
     * <p>断言护栏（决策点 1）：old 与 new 必须同 (coordinate,uniqueId) 且 matchIndex 解析 id == old.getId()，
     * 不满足抛 {@link IllegalStateException}——防止调用方误把不相关设备当换装（old 的 stop/release 由调用方在调本方法前完成）。
     * 替代旧 softRemove + getOrCreate 两步。
     *
     * @param oldDevice 即将被覆盖的旧设备（调用方应已 stop/release；仍注册在 map 中）
     * @param newDevice 新设备（getId 为构造铸造默认 UUID，本方法解析为 old 的稳定 id）
     * @return 注册后的新设备（id 已解析为 old 的稳定值）
     */
    public DeviceBase replace(DeviceBase oldDevice, DeviceBase newDevice) {
        String resolved = matchIndex.get(matchKey(newDevice.getCoordinate(), newDevice.getUniqueId()));
        if (resolved == null
                || !resolved.equals(oldDevice.getId())
                || !matchKey(oldDevice.getCoordinate(), oldDevice.getUniqueId())
                   .equals(matchKey(newDevice.getCoordinate(), newDevice.getUniqueId()))) {
            throw new IllegalStateException("replace 断言失败：old/new 须同 (coordinate,uniqueId) 且解析 id==old.id；"
                + "old=" + oldDevice.getId() + ", resolved=" + resolved);
        }
        newDevice.setId(resolved); // 包级 setId：同包 DeviceRegistry 可调
        commit(newDevice, DeviceLifecycleEvent.Action.RECONFIGURE);
        return newDevice;
    }

    /**
     * entry 禁用的软移除：从 active map 移除设备，**保留** record(yml) + matchIndex（供 enable 时 getOrCreate 复原同 id、
     * restorePersistedState 读保留 state），发 REMOVE（设备从活跃系统消失，订阅方按离线处理）。
     * <p>替代旧 {@code unregister(device, false)}。reconfigure 不走此方法（走 {@link #replace}）。
     */
    public void disable(DeviceBase device) {
        registry.remove(device.getId());
        // 持久化 disabled=true（保 entryId，区别 remove 的 entryId=null/deleted=true）：record 保留供"未绑定"态查询 + re-enable 复原
        if (persistence != null) {
            DeviceRecord record = toRecord(device);
            record.setDisabled(true);
            persistence.save(record);
        }
        // 级联 entry setEnabled(false)：集成 load 跳过 disabled entry（IntegrationManager/AirdeviceIntegration 实证），
        // 使 disable 跨重启持久（否则 entry 仍 enabled → 重启 re-register → disable 失效）。保 matchIndex 供 re-enable getOrCreate 复原同 id。
        if (entryRegistry != null && device.getEntry() != null && device.getEntry().getEntryId() != null) {
            entryRegistry.setEnabled(device.getEntry().getEntryId(), false);
        }
        publish(device, DeviceLifecycleEvent.Action.REMOVE);
    }

    /**
     * 逻辑删：从 active map 移除 + record 标 {@code deleted=true}+{@code entryId=null}（记录保留供审计），
     * **保留 matchIndex**——同 (coordinate,uniqueId) 重新发现时 getOrCreate 命中复原同 deviceId，commit 重写记录翻回 deleted=false。
     * 发 REMOVE。介于 {@link #disable}（仅离线，entryId 保留）与 {@link #purge}（硬删不可复原）之间。
     */
    public void remove(DeviceBase device) {
        registry.remove(device.getId());
        if (persistence != null) {
            DeviceRecord record = toRecord(device);
            record.setDeleted(true);
            record.setEntryId(null);
            persistence.save(record);
        }
        // 保留 matchIndex：供重新发现 getOrCreate 复原同 id
        publish(device, DeviceLifecycleEvent.Action.REMOVE);
    }

    /**
     * 硬删：从 active map 移除 + 删 record(yml) + 删 matchIndex + 发 REMOVE。
     * 删除后同 (coordinate,uniqueId) 再注册会铸新 deviceId（不复原）。替代旧 {@code unregister(device, true)}。
     */
    public void purge(DeviceBase device) {
        registry.remove(device.getId());
        if (persistence != null) {
            persistence.delete(device.getId());
        }
        matchIndex.remove(matchKey(device.getCoordinate(), device.getUniqueId()));
        publish(device, DeviceLifecycleEvent.Action.REMOVE);
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
        DeviceLifecycleEvent event = new DeviceLifecycleEvent(d.getId(), d.getCoordinate(), d.getUniqueId(), entryId, action);
        busRegistry.publish(BusEvent.of(
                BusTopic.DEVICE_LIFECYCLE.getTopicName(), event,
                EventContext.root(EventContext.Source.SYSTEM, null)));
    }

}
