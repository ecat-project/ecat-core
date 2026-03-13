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

import java.util.List;
import java.util.concurrent.ScheduledExecutorService;

import com.ecat.core.ConfigEntry.ConfigEntry;
import com.ecat.core.ConfigEntry.ConfigEntryRegistry;
import com.ecat.core.ConfigFlow.AbstractConfigFlow;
import com.ecat.core.EcatCore;
import com.ecat.core.Device.DeviceRegistry;
import com.ecat.core.I18n.I18nHelper;
import com.ecat.core.I18n.I18nProxy;
import com.ecat.core.Log.ClassLoaderCoordinateFilter;
import com.ecat.core.Log.LogManager;
import com.ecat.core.State.StateManager;

import com.ecat.core.Utils.LogFactory;

import lombok.Getter;

import com.ecat.core.Utils.Log;

/**
 * Base class for all integrations without device management.
 *
 * @author coffee
 */
public abstract class IntegrationBase implements IntegrationLifecycle {

    protected Log log;

    protected EcatCore core;
    @Getter
    protected IntegrationLoadOption loadOption;
    protected StateManager stateManager;
    protected IntegrationManager integrationManager;
    protected IntegrationRegistry integrationRegistry;
    protected DeviceRegistry deviceRegistry;

    protected final I18nProxy i18n = I18nHelper.createProxy(this.getClass());

    public IntegrationBase() {
        // 初始化日志对象为子类的类名
        log = LogFactory.getLogger(getClass());
    }

    @Override
    public void onLoad(EcatCore core, IntegrationLoadOption loadOption) {
        this.core = core;
        this.loadOption = loadOption;
        this.stateManager = core.getStateManager();
        this.integrationManager = core.getIntegrationManager();
        this.deviceRegistry = core.getDeviceRegistry();
        this.integrationRegistry = core.getIntegrationRegistry();

        // 设置日志上下文，将日志路由到对应集成的日志文件
        String coordinate = loadOption.getIntegrationInfo().getCoordinate();
        Log.setIntegrationContext(coordinate);

        // 注册包名前缀与坐标的映射
        // 业务类通过 LogFactory.getLogger(this.getClass()) 获取 logger
        // logger.getName() 就是业务类的全限定名，通过包名前缀匹配实现日志路由
        String packagePrefix = this.getClass().getPackage().getName();
        ClassLoaderCoordinateFilter.registerPackagePrefix(packagePrefix, coordinate);

        LogManager.getInstance().registerIntegration(coordinate, loadOption.getIntegrationInfo());
    }

    public String getName() {
        return this.getClass().getSimpleName();
    }

    // integration schedule task executor
    public ScheduledExecutorService getScheduledExecutor() {
        return this.core.getTaskManager().getExecutorService();
    }

    @Override
    public void onRelease() {
        // 清除日志上下文
        Log.clearIntegrationContext();

        // 注销包名前缀映射
        String packagePrefix = this.getClass().getPackage().getName();
        ClassLoaderCoordinateFilter.unregisterPackagePrefix(packagePrefix);

        // 从日志管理器中注销集成
        if (this.loadOption != null && this.loadOption.getIntegrationInfo() != null) {
            String coordinate = this.loadOption.getIntegrationInfo().getCoordinate();
            LogManager.getInstance().unregisterIntegration(coordinate);
        }
    }

    // ==================== ConfigEntry 相关方法 ====================

    /**
     * 获取配置条目注册器
     *
     * @return ConfigEntryRegistry，如果 core 不可用则返回 null
     */
    protected ConfigEntryRegistry getEntryRegistry() {
        return core != null ? core.getEntryRegistry() : null;
    }

    /**
     * 获取集成的配置流程实例
     * <p>
     * 设计说明：
     * <ul>
     *   <li>返回的实例用于判断 Flow 能力（hasUserStep 等）</li>
     *   <li>Registry 会提取实例的类定义存储</li>
     *   <li>后续通过类定义创建新实例，保证状态隔离</li>
     * </ul>
     *
     * @return AbstractConfigFlow 实例，默认 null 表示无 flow
     */
    public AbstractConfigFlow getConfigFlow() {
        return null;  // 默认无 flow
    }

    /**
     * 合并/升级配置条目到当前版本格式
     * <p>
     * 在加载持久化条目后、createEntry() 之前调用。
     * 如果条目落后多个版本，应顺序升级每个版本。
     * <p>
     * 示例实现：
     * <pre>{@code
     * @Override
     * public List<ConfigEntry> mergeEntries(List<ConfigEntry> entries) {
     *     List<ConfigEntry> merged = new ArrayList<>();
     *     boolean hasChanges = false;
     *
     *     for (ConfigEntry entry : entries) {
     *         ConfigEntry current = entry;
     *         int originalVersion = entry.getVersion();
     *
     *         // 顺序版本升级
     *         if (entry.getVersion() < 2) {
     *             current = upgradeV1toV2(current);
     *         }
     *         if (current.getVersion() < 3) {
     *             current = upgradeV2toV3(current);
     *         }
     *
     *         if (current.getVersion() != originalVersion) {
     *             hasChanges = true;
     *         }
     *         merged.add(current);
     *     }
     *
     *     return hasChanges ? merged : null;
     * }
     * }</pre>
     *
     * @param entries 待合并/升级的配置条目列表
     * @return 合并后的条目列表，如果无需更改返回 null
     */
    public List<ConfigEntry> mergeEntries(List<ConfigEntry> entries) {
        return null;  // 默认：无需合并
    }

    /**
     * 创建配置条目
     * <p>
     * 默认实现：抛出异常，表示此集成不支持 ConfigEntry 机制。
     * 需要 ConfigEntry 功能的子类应重写此方法。
     *
     * @param entry 配置条目
     * @return 创建后的配置条目
     * @throws UnsupportedOperationException 如果集成不支持 ConfigEntry
     */
    public ConfigEntry createEntry(ConfigEntry entry) {
        throw new UnsupportedOperationException(
            getClass().getSimpleName() + " does not support ConfigEntry");
    }

    /**
     * 重新配置条目
     * <p>
     * 默认实现：抛出异常，表示此集成不支持 ConfigEntry 机制。
     * 需要 ConfigEntry 功能的子类应重写此方法。
     *
     * @param entryId 配置条目 ID
     * @param entry   新的配置数据
     * @return 更新后的配置条目
     * @throws UnsupportedOperationException 如果集成不支持 ConfigEntry
     */
    public ConfigEntry reconfigureEntry(String entryId, ConfigEntry entry) {
        throw new UnsupportedOperationException(
            getClass().getSimpleName() + " does not support ConfigEntry");
    }

    /**
     * 删除配置条目
     * <p>
     * 默认实现：调用 onPreRemove() 然后从注册器中删除。
     * 子类可重写此方法以提供自定义的删除逻辑。
     *
     * @param entryId 配置条目 ID
     */
    public void removeEntry(String entryId) {
        onPreRemove(entryId);
        ConfigEntryRegistry registry = getEntryRegistry();
        if (registry != null) {
            registry.removeEntry(entryId);
        }
    }

    /**
     * 删除前回调
     * <p>
     * 可选实现：子类可重写此方法以进行资源清理。
     *
     * @param entryId 配置条目 ID
     */
    protected void onPreRemove(String entryId) {
        // 子类可重写以进行资源清理
    }
}
