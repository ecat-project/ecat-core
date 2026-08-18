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

package com.ecat.core.Task;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import com.ecat.core.Integration.IntegrationBase;
import com.ecat.core.Task.engine.SchedulerEngine;
import com.ecat.core.Utils.Log;
import com.ecat.core.Utils.LogFactory;
import com.ecat.core.Utils.Mdc.MdcExecutorService;

/**
 * TaskManager class is responsible for managing scheduled tasks and executor services.
 * It provides methods to create MDC-wrapped executors that preserve context across async tasks.
 *
 * <p>调度 v2：共享调度器已从全局 2 线程 ScheduledThreadPoolExecutor 换成
 * {@link SchedulerEngine}（表轮计时 + 共享 worker 池 + 每设备逻辑车道 + 硬超时/看门狗/熔断）。
 * 对 141 个 getScheduledExecutor() 调用点，ScheduledExecutorService 契约语义不变——阻塞任务
 * 只烧自己车道，不再拖停全平台轮询（旧引擎两个阻塞任务即全局停摆的结构缺陷）。
 *
 * <p>Usage examples:
 * <pre>
 * // For scheduled tasks (timers, periodic execution)
 * ScheduledExecutorService scheduler = taskManager.getMdcScheduledExecutorService();
 * scheduler.scheduleWithFixedDelay(() -> {...}, 0, 5, TimeUnit.SECONDS);
 *
 * // For CompletableFuture async operations
 * ExecutorService executor = taskManager.createMdcExecutorService(2);
 * CompletableFuture.supplyAsync(() -> {...}, executor)
 *     .thenApplyAsync(result -> {...}, executor);
 * </pre>
 *
 * @author coffee
 */
public class TaskManager {
    // 调度 v2 引擎：MDC 传播在引擎内完成（提交时捕获、周期任务每执行换 traceId），对外仍是一个
    // ScheduledExecutorService，旧 getExecutorService()/getMdcScheduledExecutorService() 两个入口同源
    private final SchedulerEngine engine = new SchedulerEngine();

    // Managed executor services created by this manager (for unified shutdown)
    private final List<ExecutorService> managedExecutors = new CopyOnWriteArrayList<>();

    private static final Log log = LogFactory.getLogger(TaskManager.class);

    public TaskManager() {
    }

    /**
     * @deprecated Use {@link #getMdcScheduledExecutorService()} instead.
     * @return the MDC-wrapped ScheduledExecutorService
     */
    @Deprecated
    public ScheduledExecutorService getExecutorService() {
        return this.engine;
    }

    /**
     * Get the shared MDC-wrapped ScheduledExecutorService.
     * Use this for scheduled tasks (scheduleWithFixedDelay, scheduleAtFixedRate, schedule).
     *
     * @return MDC-wrapped ScheduledExecutorService
     */
    public ScheduledExecutorService getMdcScheduledExecutorService() {
        return this.engine;
    }

    /**
     * 调度 v2 引擎本体（只读观测入口：getMetrics/getBreakers/队列深度/worker 忙闲）。
     * system_health（B2 自观测）从这里读引擎快照；提交任务仍经 ScheduledExecutorService 契约方法。
     */
    public SchedulerEngine getSchedulerEngine() {
        return this.engine;
    }

    /**
     * 返回绑定指定车道键的 {@link ExecutorService} 视图（IO 收敛 P0）：任务全部路由到该车道
     * ——同 key 严格串行、异 key 并行——供分道粒度无法用提交方 MDC 集成坐标表达的调用方使用
     * （典型：modbus source 事务须按「资源坐标」分道，同连接串行防帧碰撞、异连接并行）。
     *
     * <p>laneKey 命名约定「资源域前缀 + 连接标识」：{@code modbus-source:{conn}}，如
     * {@code modbus-source:127.0.0.1:502}；null/blank 抛 {@link IllegalArgumentException}
     * （严格模式，不静默落默认道）。视图轻量（每 key 一个薄对象，不建池不建线程）、幂等缓存，
     * 生命周期跟随引擎（引擎停机后提交拒绝）。完整契约见
     * {@link SchedulerEngine#executorFor(String)}。
     *
     * @param laneKey 显式车道键
     * @return 绑定该键的 ExecutorService 视图
     */
    public ExecutorService executorFor(String laneKey) {
        return this.engine.executorFor(laneKey);
    }

    // =====================================================================
    // D2 设备通讯失败熔断：集成的轮询代码把「通讯失败/成功」上报进来，
    // 连败达阈值后 OPEN，冷却期内轮询方经 isCommOpen 自查跳过整轮轮询
    // =====================================================================

    /**
     * 设备通讯失败上报（一次命令事务失败 = 一次上报；判定权在调用方）。
     *
     * <p><b>键空间</b>：调用方稳定键，约定 {@code coordinate:deviceId}（如
     * {@code com.ecat:integration-sailhero:so2-1}）。与调度车道（集成坐标粒度）刻意解耦：
     * 同集成多台设备共享一条车道，设备级通讯失败计入车道熔断会在单台故障时误伤整集成调度。
     *
     * <p><b>语义（严格模式）</b>：什么算「通讯失败」由调用方判定（串口/总线超时、无有效响应、
     * IO 异常），引擎只数连败——与任务异常同权，成功清零。默认 5 连败 → 300s 冷却（与车道熔断
     * 同参数）。间歇性失败（失败-成功交错）凑不满连败，不触发熔断（保护数据新鲜度）。
     * OPEN 转移行带 COMM marker 进 comm-health.log，reason 前缀「通讯失败: 」区分任务异常。
     *
     * @param key     设备通讯键（coordinate:deviceId）
     * @param summary 失败摘要（进 OPEN 行供运维定位，如 "so2chr$ Serial response timeout"）
     */
    public void reportCommFailure(String key, String summary) {
        this.engine.reportCommFailure(key, summary);
    }

    /**
     * 设备通讯成功上报（一次命令事务成功 = 一次上报）：清零连败；冷却/半开期成功直接关闭熔断
     * （真实成功是最强健康信号——手动命令等非轮询路径的成功同样有效）。
     */
    public void reportCommSuccess(String key) {
        this.engine.reportCommSuccess(key);
    }

    /**
     * 轮询守卫：true = 本轮轮询应整体跳过（不打日志——跳过事实由 comm-health 的 OPEN/PROBING
     * 转移行表达）；false = 正常轮询。
     *
     * <p><b>探测语义</b>：OPEN 冷却期满后的第一次调用消耗探测名额（返回 false，本轮即探测），
     * 探测在途期间后续调用返回 true。因此守卫放行后调用方<b>必须保证随后对本 key 恰好一次
     * {@link #reportCommSuccess}/{@link #reportCommFailure}</b>——探测名额悬挂等于该设备轮询
     * 被永久跳过（轮询链有硬超时兜底必然 complete，正常 wiring 天然满足）。
     *
     * <p><b>边界</b>：跳过仅是「不发通讯、不碰属性」，不得以此绕过 DevicePhase 就绪门禁或
     * 属性状态契约（publicState/AttrState 不受本守卫影响）。
     */
    public boolean isCommOpen(String key) {
        return this.engine.isCommOpen(key);
    }

    /**
     * Create an MDC-wrapped fixed-size thread pool ExecutorService.
     * The thread name prefix is automatically inferred from the caller's coordinate.
     *
     * @param poolSize the number of threads in the pool
     * @return MDC-wrapped ExecutorService
     */
    public ExecutorService createMdcExecutorService(int poolSize) {
        String prefix = inferCallerCoordinate();
        return createMdcExecutorService(poolSize, prefix);
    }

    /**
     * Create an MDC-wrapped fixed-size thread pool ExecutorService with custom prefix.
     *
     * @param poolSize the number of threads in the pool
     * @param threadNamePrefix prefix for thread names
     * @return MDC-wrapped ExecutorService
     */
    public ExecutorService createMdcExecutorService(int poolSize, String threadNamePrefix) {
        ThreadFactory factory = new NamedThreadFactory(threadNamePrefix);
        ExecutorService raw = Executors.newFixedThreadPool(poolSize, factory);
        ExecutorService wrapped = MdcExecutorService.wrap(raw);
        managedExecutors.add(wrapped);
        return wrapped;
    }

    /**
     * Create an MDC-wrapped single-thread ExecutorService.
     * The thread name prefix is automatically inferred from the caller's coordinate.
     *
     * @return MDC-wrapped single-thread ExecutorService
     */
    public ExecutorService createMdcSingleThreadExecutor() {
        String prefix = inferCallerCoordinate();
        return createMdcSingleThreadExecutor(prefix);
    }

    /**
     * Create an MDC-wrapped single-thread ExecutorService with custom prefix.
     *
     * @param threadNamePrefix prefix for thread name
     * @return MDC-wrapped single-thread ExecutorService
     */
    public ExecutorService createMdcSingleThreadExecutor(String threadNamePrefix) {
        ThreadFactory factory = new NamedThreadFactory(threadNamePrefix);
        ExecutorService raw = Executors.newSingleThreadExecutor(factory);
        ExecutorService wrapped = MdcExecutorService.wrap(raw);
        managedExecutors.add(wrapped);
        return wrapped;
    }

    /**
     * Create an MDC-wrapped cached thread pool ExecutorService.
     * The thread name prefix is automatically inferred from the caller's coordinate.
     *
     * @return MDC-wrapped cached thread pool ExecutorService
     */
    public ExecutorService createMdcCachedExecutorService() {
        String prefix = inferCallerCoordinate();
        return createMdcCachedExecutorService(prefix);
    }

    /**
     * Create an MDC-wrapped cached thread pool ExecutorService with custom prefix.
     *
     * @param threadNamePrefix prefix for thread names
     * @return MDC-wrapped cached thread pool ExecutorService
     */
    public ExecutorService createMdcCachedExecutorService(String threadNamePrefix) {
        ThreadFactory factory = new NamedThreadFactory(threadNamePrefix);
        ExecutorService raw = Executors.newCachedThreadPool(factory);
        ExecutorService wrapped = MdcExecutorService.wrap(raw);
        managedExecutors.add(wrapped);
        return wrapped;
    }

    /**
     * Infer the caller's coordinate by inspecting the call stack.
     * Looks for IntegrationBase subclasses and extracts their coordinate.
     * Returns format: integration-{name} (e.g., integration-serial, integration-sailhero)
     *
     * @return inferred coordinate prefix, or "ecat" as default
     */
    String inferCallerCoordinate() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (StackTraceElement element : stackTrace) {
            String className = element.getClassName();
            // Look for IntegrationBase subclasses
            if (className.contains("integration.") || className.contains("Integration")) {
                try {
                    Class<?> clazz = Class.forName(className);
                    if (IntegrationBase.class.isAssignableFrom(clazz) && clazz != IntegrationBase.class) {
                        // Return simplified coordinate using class name
                        String simpleName = clazz.getSimpleName();
                        String name = simpleName.replace("Integration", "").toLowerCase();
                        // Return in integration-{name} format for clarity
                        return "integration-" + name;
                    }
                } catch (ClassNotFoundException ignored) {
                    // Class not found, continue searching
                }
            }
        }
        return "ecat";  // Default prefix
    }

    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long period, TimeUnit unit) {
        return this.engine.scheduleWithFixedDelay(command, initialDelay, period, unit);
    }

    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        return this.engine.scheduleAtFixedRate(command, initialDelay, period, unit);
    }

    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        return this.engine.schedule(command, delay, unit);
    }

    public void pauseAllTasks() {
        // 空实现，可扩展
    }

    /**
     * Shutdown all managed executor services.
     * This includes the shared scheduled executor and all executors created via createMdc* methods.
     */
    public void shutdownAll() {
        // Shutdown scheduled executor
        this.engine.shutdownNow();

        // Shutdown all managed executors
        for (ExecutorService executor : managedExecutors) {
            executor.shutdownNow();
        }
        managedExecutors.clear();
    }

    /**
     * @deprecated Use {@link #shutdownAll()} instead.
     */
    @Deprecated
    public void stopAllTasks() {
        shutdownAll();
    }
}
