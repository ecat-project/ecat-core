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
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;

import com.ecat.core.ConfigEntry.ConfigEntry;
import com.ecat.core.ConfigEntry.ConfigEntryRegistry;
import com.ecat.core.ConfigFlow.AbstractConfigFlow;
import com.ecat.core.EcatCore;
import com.ecat.core.Device.DeviceRegistry;
import com.ecat.core.Device.RemovalHost;
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
public abstract class IntegrationBase implements IntegrationLifecycle, RemovalHost {

    protected Log log;

    /** 集成初始化就绪标志。所有已持久化 ConfigEntry 加载完成后由框架设为 true。 */
    @Getter
    private volatile boolean ready = false;

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
    /**
     * 集成层业务计时器（S2 摆脱引擎）：返回 core 级共享业务池（ecat-biz-sched，普通具名
     * 守护线程 + MDC/TraceContext 包装），不再借道调度引擎——业务 tick（纯计算节律）与
     * IO 轮询（引擎车道/硬超时/熔断）故障域隔离，互不拖累。
     *
     * <p><b>IO 禁入（硬边界，名字自带警示）</b>：仅放纯计算业务计时（时钟显示、随机源、
     * 指标采集等毫秒级任务）；会阻塞的 IO 轮询禁走此池——2 线程小池，一个阻塞任务会
     * <b>静默饿死全部业务计时</b>（无超时、无熔断、无报错），设备 IO 轮询走所属传输域
     * SDK（SerialPolling/ModbusPolling 等，19 号 v2「设备零调度」）。生命周期随 core 停机。
     * 方法名 Biz（业务）对 IO 说不——旧名 getScheduledExecutor 无语义提示、阻塞任务编译
     * 照过，E4-6 改名强制触碰点。
     */
    public ScheduledExecutorService getBizScheduler() {
        return this.core.getTaskManager().getBizScheduler();
    }

    /**
     * 本集成的坐标（groupId:artifactId）；loadOption 未设置时返回 null。
     * 供 flow 上下文之外（如 onStart）需 coordinate 的域化查询：
     * {@code core.getEntryRegistry().getByUniqueId(getCoordinate(), uniqueId)}。
     */
    public String getCoordinate() {
        return (loadOption != null && loadOption.getIntegrationInfo() != null)
                ? loadOption.getIntegrationInfo().getCoordinate() : null;
    }

    /**
     * 集成级移除动作 deque（RemovalHost 注册面，镜像 DeviceBase.removalActions）：
     * 集成自有资源（HostedExecutors 池、jmdns 句柄等）经 {@link #onRemove(Runnable)}
     * 注册，{@link #onRelease()} 时 <b>LIFO</b> 执行（后注册的资源先拆卸——依赖反向于
     * 创建顺序）。与设备级各自挂各自，无嵌套传递：设备级执行器挂设备、集成级挂集成，
     * 天然分层。
     */
    private final ConcurrentLinkedDeque<Runnable> removalActions = new ConcurrentLinkedDeque<>();

    /** 集成级移除动作是否已随 onRelease 关闭（关闭后注册=病态调用 reject，严格模式不静默收下）。 */
    private volatile boolean removalSwept;

    /**
     * 集成级移除动作注册（RemovalHost 宿主实现）：注册到移除动作 deque，
     * {@link #onRelease()} 时 LIFO 统一执行。
     *
     * <p><b>非阻塞契约</b>：动作必须提交即返（{@code shutdownNow()} 纯标记天然合规），
     * 详见 {@link RemovalHost}。</p>
     *
     * @throws RejectedExecutionException 集成已 release 后注册属病态调用（严格模式，
     *         防「release 后才注册」的静默泄漏）
     * @throws IllegalArgumentException action 为 null
     */
    @Override
    public void onRemove(Runnable action) {
        if (action == null) {
            throw new IllegalArgumentException("onRemove(null) 不允许——无动作可注册");
        }
        if (removalSwept) {
            throw new RejectedExecutionException(
                "集成已 release，拒绝注册移除动作——集成生命周期缺陷，请检查 onRelease 后误注册的调用方");
        }
        removalActions.addLast(action);
    }

    /**
     * 集成级移除动作统一执行（RemovalHost，LIFO；逐条 catch 记 warn 继续——清理不容
     * 半途而废）。幂等（重复调用空转）。
     *
     * <p><b>扫除面收口</b>：sweep 只在本方法体尾部内部执行，不暴露任何
     * public 收尾方法——注册面开放（onRemove / HostedExecutors.bounded），扫除面永远
     * 框架调用，使用者零接触。与 {@link com.ecat.core.Device.DeviceBase#cancelManagedTasks()}
     * 的设备级 sweep 构成两层：设备级先扫（各自生命周期 chokepoint），集成级在此兜底。</p>
     */
    private void sweepRemovalActions() {
        removalSwept = true;
        Runnable action;
        while ((action = removalActions.pollLast()) != null) {
            try {
                action.run();
            } catch (Throwable e) {
                log.warn("集成级移除动作执行失败（已跳过，继续执行其余动作）", e);
            }
        }
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

        // 集成级移除动作兜底 sweep（尾部：既有清理先行，随后拆卸自有资源——
        // 日志上下文/包名映射等注册面先撤，再拆挂在本集成上的池/句柄）
        sweepRemovalActions();
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
     * <p> [重要]集成自定义的reconfigure flow原则上不要修改设备类型的配置，比如从电源变为空调，因为这会严重改变该设备被其他集成引用的含义作用而导致其他集成错误。
     * <p> [重要]推荐只修改sn、端口信息等不会
     * <p> [重要]对于更改设备类型的需求，让用户删除旧entry重新创建新的entry实现
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
     * 子类可重写此方法以提供自定义的删除逻辑。
     *
     * @param entryId 配置条目 ID
     */
    public void removeEntry(String entryId) {
        ConfigEntryRegistry registry = getEntryRegistry();
        if (registry != null) {
            registry.removeEntry(entryId);
        }
    }

    /**
     * 禁用配置条目
     * <p>
     * 默认实现：调用 removeEntry() 停止并释放设备资源，但配置文件由 Registry 保留。
     * 子类可重写此方法以提供自定义的禁用逻辑。
     *
     * @param entryId 配置条目 ID
     */
    public void disableEntry(String entryId) {
        removeEntry(entryId);
    }

    /**
     * 启用配置条目
     * <p>
     * 默认实现：调用 createEntry() 从已保存的配置重新创建并启动设备。
     * 子类可重写此方法以提供自定义的启用逻辑。
     *
     * @param entry 已保存的配置条目
     */
    public void enableEntry(ConfigEntry entry) {
        createEntry(entry);
    }

    /**
     * 所有已持久化的 ConfigEntry 加载完成后的回调。
     * <p>
     * 在集成启动时，所有已持久化 ConfigEntry 的 createEntry() 调用完毕后触发。
     * 默认实现将 ready 设为 true。
     * <p>
     * 子集成可覆盖此方法以执行自定义的一致性检查。
     * 如果需要保留默认的 ready 设置行为，应调用 super.onAllExistEntriesLoaded(entries)。
     * <p>
     * 设计理念：幂等配置拉平。集成配置和设备配置在同一平面加载，
     * 加载顺序不可控。此钩子让集成在所有配置就绪后完成最终状态一致性。
     *
     * @param entries 该集成的所有已加载 ConfigEntry（包括 disabled 的）
     */
    public void onAllExistEntriesLoaded(List<ConfigEntry> entries) {
        this.ready = true;
    }
}
