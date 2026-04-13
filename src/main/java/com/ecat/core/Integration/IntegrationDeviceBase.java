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
 * <p>设备本地映射表 {@code devices} 以 uniqueId（device.getId()）为 key，
 * 全局 {@link com.ecat.core.Device.DeviceRegistry} 同样以 uniqueId 为 key。
 *
 * @author coffee
 */
public abstract class IntegrationDeviceBase extends IntegrationBase implements IIntegrationDeviceManagement {

    /** 设备映射表 (uniqueId -> Device)，uniqueId 即 device.getId() */
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
        if (devices.containsKey(device.getId())) {
            return true;
        }
        devices.put(device.getId(), device);
        device.setIntegration(this);
        deviceRegistry.register(device.getId(), device);
        return true;
    }

    @Override
    public boolean removeDevice(DeviceBase device) {
        DeviceBase removed = devices.remove(device.getId());
        if (removed != null) {
            deviceRegistry.unregister(device.getId());
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

    @Override
    public ConfigEntry createEntry(ConfigEntry entry) {
        log.info("Creating entry: {}", entry.getUniqueId());
        DeviceBase device = createDeviceFromEntry(entry);
        if (device != null) {
            // device.load(core) 已在各集成的 createDeviceFromEntry() 中调用
            addDevice(device);
            device.start();
        }
        log.info("Entry created: {}", entry.getUniqueId());
        return entry;
    }

    @Override
    public ConfigEntry reconfigureEntry(String entryId, ConfigEntry newEntry) {
        log.info("Reconfiguring entry: {}", entryId);

        // 1. 停止并释放旧设备
        DeviceBase oldDevice = findDeviceByEntryId(entryId);
        if (oldDevice != null) {
            oldDevice.stop();
            oldDevice.release();
            removeDevice(oldDevice);
            // 删除旧设备的持久化状态文件
            if (core != null && core.getStateManager() != null) {
                core.getStateManager().removeDevice(oldDevice);
            }
        }

        // 2. 创建并启动新设备
        DeviceBase newDevice = createDeviceFromEntry(newEntry);
        if (newDevice == null) {
            throw new RuntimeException("Failed to create device with new config for entry: " + entryId);
        }
        // device.load(core) 已在各集成的 createDeviceFromEntry() 中调用
        addDevice(newDevice);
        newDevice.start();

        log.info("Entry reconfigured: {}", entryId);
        return newEntry;
    }

    @Override
    public void removeEntry(String entryId) {
        log.info("Removing entry: {}", entryId);
        DeviceBase device = findDeviceByEntryId(entryId);
        if (device != null) {
            device.stop();
            device.release();
            removeDevice(device);
            // 删除持久化状态文件
            if (core != null && core.getStateManager() != null) {
                core.getStateManager().removeDevice(device);
            }
        }
        // 注意：不调用 super.removeEntry(entryId)
        // 调用链：ConfigEntryRegistry.removeEntry() -> notifyIntegrationRemove() -> integration.removeEntry()
        // 如果这里再调用 super.removeEntry() 会导致递归 StackOverflowError
        // Registry 已负责从缓存中移除 entry
        log.info("Entry removed: {}", entryId);
    }

    /**
     * 通过 entryId 查找设备
     * devices 以 uniqueId 为 key，需要遍历匹配 entry.getEntryId()
     */
    private DeviceBase findDeviceByEntryId(String entryId) {
        for (DeviceBase device : devices.values()) {
            if (device.getEntry() != null && entryId.equals(device.getEntry().getEntryId())) {
                return device;
            }
        }
        return null;
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
            device.stop();
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
