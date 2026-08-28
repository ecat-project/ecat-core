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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.ecat.core.Bus.event.DeviceLifecycleEvent;
import com.ecat.core.ConfigEntry.ConfigEntry;
import com.ecat.core.EcatCore;
import com.ecat.core.Device.DeviceBase;
import com.ecat.core.Utils.DynamicConfig.ConfigDefinition;

/**
 * IntegrationDeviceBase
 * 针对含有设备管理的集成基类
 *
 * <p>提供设备注册/注销、ConfigEntry 生命周期（创建/重配置/删除）的默认实现。
 * 子类只需实现 {@link #createDeviceFromEntry(ConfigEntry)} 来定义设备创建逻辑，
 * 以及 {@link #getConfigFlow()} 返回对应的 ConfigFlow 实例。
 *
 * <p>设备本地映射表 {@code devices} 以 deviceId（即 {@code device.getId()}，见 {@link DeviceBase#getId()}）为 key，
 * 全局 {@link com.ecat.core.Device.DeviceRegistry} 同样以 deviceId 为 key。
 * deviceId 由 core 铸造（UUID），跨重启/重配稳定（getOrCreate 命中持久化记录复原）；与 entryId（配置记录 UUID）不同，
 * 一个 entry 在网关场景可派生 1:N 个 device。
 *
 * @author coffee
 */
public abstract class IntegrationDeviceBase extends IntegrationBase implements IIntegrationDeviceManagement {

    /** 设备映射表 (deviceId -> Device)，key 即 device.getId()（core 铸造的 deviceId，非 entryId） */
    protected final Map<String, DeviceBase> devices = new ConcurrentHashMap<>();
    protected ConfigDefinition deviceConfigDefinition;

    @Override
    public void onLoad(EcatCore core, IntegrationLoadOption loadOption) {
        super.onLoad(core, loadOption);
        deviceConfigDefinition = getDeviceConfigDefinition();
    }

    // TODO: 旧式 addDevice 直接注册模式，后续统一通过 createEntry 后删除
    @Override
    public boolean addDevice(DeviceBase device) {
        return addDevice(device, DeviceLifecycleEvent.Action.CREATE);
    }

    /**
     * 00-core：经 DeviceRegistry.getOrCreate 解析稳定 deviceId（跨重启）后注册。
     *
     * @param action 事件意图（create/enable→CREATE，reconfigure→RECONFIGURE）
     */
    public boolean addDevice(DeviceBase device, DeviceLifecycleEvent.Action action) {
        // 先解析稳定 id（可能用持久化值覆盖构造铸造的默认 UUID），再按解析后的 id 幂等建本地映射
        deviceRegistry.getOrCreate(device, action);
        if (devices.containsKey(device.getId())) {
            return true;
        }
        devices.put(device.getId(), device);
        device.setIntegration(this);
        return true;
    }

    @Override
    public boolean removeDevice(DeviceBase device) {
        DeviceBase removed = devices.remove(device.getId());
        if (removed != null) {
            // 逻辑删：保 yml(deleted=true)+matchIndex，使同 uniqueId 重建/reconfigure 复原同 deviceId
            // （与 removeEntry 一致；reconfigure 先 removeDevice 旧设备再 addDevice 新设备，
            // 命中保留的 matchIndex 复原同 id，而非铸新 UUID）。
            deviceRegistry.remove(device);
            return true;
        }
        return false;
    }

    @Override
    public Collection<DeviceBase> getAllDevices() {
        return devices.values();
    }

    // TODO: 旧式模式兼容，后续升级到 ConfigEntry 模式后删除
    @Override
    public boolean createDevice(Map<String, Object> config) {
        throw new UnsupportedOperationException("createDevice not implemented");
    }

    @Override
    public ConfigDefinition getDeviceConfigDefinition() {
        return null;
    }

    // ==================== ConfigEntry 默认生命周期 ====================

    @Override
    public void onInit() {
        // 默认空实现，子类可覆盖
    }

    /**
     * 默认实现：通过 createDeviceFromEntry 创建设备并启动
     * 对于某些集成既有集成配置，又有设备配置的，应当重写此方法以自定义实现区分不同类型的 ConfigEntry
     */
    @Override
    public ConfigEntry createEntry(ConfigEntry entry) {
        log.info("Creating entry: {}", entry.getUniqueId());
        DeviceBase device = createDeviceFromEntry(entry);
        if (device != null) {
            // device.load(core) 已在各集成的 createDeviceFromEntry() 中调用
            addDevice(device);                                   // getOrCreate 解析稳定 id + register
            finalizeNewDevice(device);                           // restore 持久态 + markReady(flush init 挂起态) + start
        }
        log.info("Entry created: {}", entry.getUniqueId());
        return entry;
    }

    /**
     * 物理设备就绪收口（addDevice 之后）：批量恢复持久化 state → markReady（翻就绪 + flush init 期被门禁挂起的发布，
     * 用稳定 id 首发）→ start。
     *
     * <p>createEntry 与 reconfigureEntry 共用此序列——markReady 单点落在 restorePersistedState 之后，
     * 使 flush 发出"最终态"（持久化值优先，否则 config 派生值），单次稳定 id 首发，避免默认值→持久值双发。
     * init 期（addDevice 之前）的 publicState 被 ready gate 挂起、保留 midState 到此处 flush。
     */
    protected void finalizeNewDevice(DeviceBase device) {
        device.restorePersistedState();
        device.markReady();
        device.start();
    }

    /**
     * 设备停止单一收口（RemovalHost 兜底收尾，18 号设计 §3.3）：{@code device.stop()}（集成
     * 自管收尾，sailhero 型自 cancel / tianhong 型 no-op）之后必执行
     * {@link DeviceBase#cancelManagedTasks()}（框架 LIFO 执行移除动作——SDK 轮询句柄等
     * 注册资源的兜底拆卸）。try-finally——集成 stop() 抛异常也必执行移除动作（不留泄漏窗口），
     * 异常本身照原语义上抛不吞（「崩溃可见」与「必收尾」兼得）。
     *
     * <p>protected final——本类生命周期 chokepoint 之外，集成仓 override 路径里自管
     * stop 设备的地方也必须走同一收口（防旁路裸 {@code device.stop()} 把移除动作滞留到
     * onRelease 兜底）；final 禁止子类改写「stop 后必 sweep」语义。</p>
     */
    protected final void stopWithManagedSweep(DeviceBase device) {
        try {
            device.stop();
        } finally {
            device.cancelManagedTasks();
        }
    }

    @Override
    public ConfigEntry reconfigureEntry(String entryId, ConfigEntry newEntry) {
        log.info("Reconfiguring entry: {}", entryId);

        // ① 先查旧设备（design §5.1：用 registry 复数查询 findDevicesByEntryId，支持 1:N 网关；取首个为 reconfigure 1:1 目标）
        java.util.List<DeviceBase> olds = deviceRegistry.findDevicesByEntryId(entryId);
        DeviceBase oldDevice = olds.isEmpty() ? null : olds.get(0);

        // ② 先备新设备（失败直接抛，old 原封未动 → 消除回归窗口：设备不会因建新失败而离线无替代）
        DeviceBase newDevice = createDeviceFromEntry(newEntry);
        if (newDevice == null) {
            throw new RuntimeException("Failed to create device with new config for entry: " + entryId);
        }

        // ③ 先 stop 旧（资源排他安全；统一"先 stop 后 start"）——在 new 已备好之后，失败也不丢 old
        if (oldDevice != null) {
            stopWithManagedSweep(oldDevice);   // stop + 移除动作收尾（try-finally 必执行）
            oldDevice.release();
            devices.remove(oldDevice.getId());
        }

        // ④ 原子换装：replace 解析 new 到 old 的稳定 id（setId）+ 单 RECONFIGURE（旧 softRemove+getOrCreate 两步合一）。
        //    无旧设备（首次/异常）则走 getOrCreate(CREATE)。
        if (oldDevice != null) {
            deviceRegistry.replace(oldDevice, newDevice);
        } else {
            deviceRegistry.getOrCreate(newDevice, DeviceLifecycleEvent.Action.CREATE);
        }
        if (!devices.containsKey(newDevice.getId())) {
            devices.put(newDevice.getId(), newDevice);
            newDevice.setIntegration(this);
        }
        finalizeNewDevice(newDevice);        // restore 保留 state DB（id 已复原）+ markReady + start（⑤ 后起新）

        log.info("Entry reconfigured: {}", entryId);
        return newEntry;
    }

    @Override
    public void removeEntry(String entryId) {
        log.info("Removing entry: {}", entryId);
        // 逻辑删：从活跃系统移除设备，保留 matchIndex + yml 记录(deleted=true) + state DB，
        // 使同 (coordinate,uniqueId) 重新创建时 getOrCreate 命中 matchIndex 复原同 deviceId
        // （一个 deviceId 绑定一个物理设备，删除后再添加复用原 id，而非铸新 UUID）。
        // 与 disable 的区别：disable 保留 entryId（entry 仍在、仅禁用）；remove 置 entryId=null（entry 已删、仅留身份记忆）。
        for (DeviceBase device : deviceRegistry.findDevicesByEntryId(entryId)) {
            stopWithManagedSweep(device);    // stop + 移除动作收尾（try-finally 必执行）
            device.release();
            devices.remove(device.getId());
            deviceRegistry.remove(device);   // 逻辑删：保 yml(deleted=true)+matchIndex，发 REMOVE
            // 不删 state DB：state 是设备历史，逻辑删保留（与 disable 一致）；deviceId 复原靠 matchIndex，与 state 无关。
        }
        // 注意：不调用 super.removeEntry(entryId)——ConfigEntryRegistry.removeEntry → notifyIntegrationRemove → 本方法，
        // 再调 super 会递归 StackOverflowError。Registry 已负责从缓存移除 entry。
        log.info("Entry removed: {}", entryId);
    }

    /**
     * 00-core：entry disable 的 1:N 级联——软移除该 entry 关联的全部设备（保记录+state），逐个发 REMOVE。
     * 软移除使 enable 时 getOrCreate 命中保留 yml 复原 id、restorePersistedState 读保留 state → 跨 disable/enable 保活。
     * 覆盖 {@link IntegrationBase#disableEntry(String)}（默认实现调 removeEntry 是破坏性删 state，不适配新模型）。
     */
    @Override
    public void disableEntry(String entryId) {
        for (DeviceBase device : deviceRegistry.findDevicesByEntryId(entryId)) {
            stopWithManagedSweep(device);    // stop + 移除动作收尾（try-finally 必执行）
            device.release();
            devices.remove(device.getId());
            deviceRegistry.disable(device);   // 软：保 yml+matchIndex，发 REMOVE
        }
    }


    // ==================== 默认生命周期回调 ====================

    @Override
    public void onStart() {
        log.info("{} started", getName());
        for (DeviceBase device : getAllDevices()) {
            device.start();
        }
    }

    @Override
    public void onPause() {
        log.info("{} paused", getName());
        for (DeviceBase device : getAllDevices()) {
            stopWithManagedSweep(device);    // stop + 移除动作收尾（pause 后句柄不得残留；enable 后 onStart 重 start 重注册）
        }
        // 关闭所有设备的持久化 DB（commit + close，保留文件）
        if (core != null && core.getStateManager() != null) {
            core.getStateManager().closeIntegrationDevices(devices.keySet());
        }
    }

    @Override
    public void onRelease() {
        log.info("{} released", getName());
        for (DeviceBase device : getAllDevices()) {
            device.cancelManagedTasks();     // 幂等补一次移除动作收尾（防 onPause 未走过的直停路径）
            device.release();
        }
        devices.clear();
        super.onRelease();
    }

    // ==================== 抽象方法 ====================

    /**
     * 从 ConfigEntry 创建设备实例
     * <p>
     * 子类实现此方法以创建特定类型的设备。设备应使用
     * {@link com.ecat.core.Device.DeviceBase#DeviceBase(ConfigEntry)} 构造。
     *
     * @param entry 配置条目（包含 uniqueId、data 等信息）
     * @return 设备实例，不应返回 null
     */
    protected DeviceBase createDeviceFromEntry(ConfigEntry entry) {
        log.warn("{} 未实现 createDeviceFromEntry，跳过 entry: {}", getName(), entry.getUniqueId());
        return null;
    }

}
