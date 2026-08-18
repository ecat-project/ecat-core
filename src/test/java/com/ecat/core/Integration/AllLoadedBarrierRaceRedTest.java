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

import com.ecat.core.Bus.BusRegistry;
import com.ecat.core.Bus.BusTopic;
import com.ecat.core.Bus.EventSubscriber;
import com.ecat.core.Bus.event.BusEvent;
import com.ecat.core.EcatCore;
import com.ecat.core.State.StateManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 红测试：INTEGRATIONS_ALL_LOADED 60s 栅栏竞态（架构审查 01-F4 静态推演的运行时复现）。
 *
 * <p>被测缺陷（IntegrationManager.loadIntegrations，:626-675）：集成经单线程池逐个 execute，
 * {@code awaitTermination(60, TimeUnit.SECONDS)}（:646，60 为硬编码字面量，不可配置、
 * 无法反射缩短）超时返回 false 后仅 log.warn，随即照常
 * updateLoadedIntegrationsState → saveInitialDependencySnapshot → loadExistingConfigEntries
 * → publish(INTEGRATIONS_ALL_LOADED)——<b>集成加载尚未完成即对外宣布「全部加载完成」</b>，
 * 超时分支没有任何栅栏/失败传播。
 *
 * <p>复现策略（超时字面量不可缩短，按授权直接驱动超时分支）：把 {@code executorService}
 * 字段（:85，private 非 final）反射替换为 {@link TimedOutHoldingExecutor}——
 * {@code execute} 接受任务但持有不执行（等价生产场景：loadSingleIntegration 卡住未完成），
 * {@code awaitTermination} 记录调用后立即返回 false（这正是 JDK 真实池在 60s 到期时
 * 对同一状态返回的值，语义逐位一致，只是省去真实 60s 等待）。随后调用 loadIntegrations()，
 * 通过真实 BusRegistry 订阅记录 ALL_LOADED 发布时刻池中仍未完成的任务数。
 *
 * <p>正确不变量：ALL_LOADED 若发布，发布时刻集成加载任务必须已全部完成（pending==0）。
 * 预测红因：事件已发布且发布时刻 pending==1（任务从未运行）。
 *
 * <p>确定性说明：无时序竞争——awaitTermination 恒返 false、发布经 BusRegistry 同步扇出，
 * 订阅者在 publish 调用线程内同步执行，pending 快照读取无竞态。
 *
 * @author coffee
 */
public class AllLoadedBarrierRaceRedTest {

    private IntegrationManager manager;
    private BusRegistry bus;
    private File testDir;
    private static String originalConfigPath;
    private static String originalItemPath;

    @Before
    public void setUp() throws Exception {
        testDir = new File("target", ".ecat-allloaded-race-red");
        deleteRecursively(testDir);
        assertTrue("测试目录创建失败: " + testDir, testDir.mkdirs() || testDir.exists());

        // 真实 BusRegistry（无构造依赖，publish 同步扇出）；EcatCore mock：
        // getBusRegistry → 真总线；getEntryRegistry/getFlowRegistry 走 Mockito 默认 null
        // ——正是 IntegrationManager 的 null 容错分支（loadExistingConfigEntries 直接返回）。
        bus = new BusRegistry();
        EcatCore core = mock(EcatCore.class);
        when(core.getBusRegistry()).thenReturn(bus);

        manager = new IntegrationManager(core, new IntegrationRegistry(), mock(StateManager.class));

        originalConfigPath = (String) getStaticField("INTEGRATIONS_CONFIG_PATH");
        originalItemPath = (String) getStaticField("INTEGRATION_ITEM_PATH");
        setStaticField("INTEGRATIONS_CONFIG_PATH", testDir.getAbsolutePath() + "/core/integrations.yml");
        setStaticField("INTEGRATION_ITEM_PATH", testDir.getAbsolutePath() + "/integrations/%s.yml");
    }

    @After
    public void tearDown() throws Exception {
        if (originalConfigPath != null) {
            setStaticField("INTEGRATIONS_CONFIG_PATH", originalConfigPath);
        }
        if (originalItemPath != null) {
            setStaticField("INTEGRATION_ITEM_PATH", originalItemPath);
        }
        deleteRecursively(testDir);
    }

    @Test
    public void allLoadedMustNotBePublishedWhileIntegrationLoadStillPending() throws Exception {
        // 1) 反射替换 executorService（:85，private 非 final）为「持有任务 + 超时」池。
        TimedOutHoldingExecutor timedOutPool = new TimedOutHoldingExecutor();
        Field executorField = IntegrationManager.class.getDeclaredField("executorService");
        executorField.setAccessible(true);
        executorField.set(manager, timedOutPool);

        // 2) 池中预置一个已提交、从未运行的集成加载任务（生产等价物：loadSingleIntegration 卡住）。
        timedOutPool.execute(() -> { });

        // 3) 订阅 ALL_LOADED：发布是同步扇出，订阅者在 publish 调用线程内执行，
        //    此刻读取池中未完成任务数 = 「宣布完成时实际未完成」的精确快照。
        final AtomicInteger publishedEvents = new AtomicInteger(0);
        final AtomicInteger pendingAtFirstPublish = new AtomicInteger(-1);
        bus.subscribe(BusTopic.INTEGRATIONS_ALL_LOADED.getTopicName(), new EventSubscriber() {
            @Override
            public void handleEvent(BusEvent<?> event) {
                publishedEvents.incrementAndGet();
                pendingAtFirstPublish.compareAndSet(-1, timedOutPool.pendingTaskCount());
            }
        });

        // 4) 驱动 loadIntegrations（空配置：不触碰 ~/.m2 真实 JAR，环境无关）。
        //    内部路径：shutdown → awaitTermination 返回 false（超时分支）→ warn → 照常 publish。
        manager.loadIntegrations();

        // 证据链：确已驱动的是超时分支（awaitTermination 被调用且返回 false）。
        assertTrue("前置失败：awaitTermination 未被调用，未驱动到超时分支", timedOutPool.awaitTerminationCalls() >= 1);

        // 正确不变量：ALL_LOADED 发布时刻，集成加载任务必须已全部完成。
        // 预测红因：事件已发布且 pending==1 —— 加载未完成即宣布完成（超时分支无栅栏）。
        assertTrue(
            "INTEGRATIONS_ALL_LOADED 在集成加载未完成时已发布：events=" + publishedEvents.get()
                + "，发布时刻池中未完成任务数=" + pendingAtFirstPublish.get()
                + "。缺陷：loadIntegrations 的 awaitTermination(60s) 超时分支仅 warn，"
                + "无栅栏照常宣布全部加载完成",
            publishedEvents.get() == 0 || pendingAtFirstPublish.get() == 0);
    }

    /**
     * 超时分支复刻池：execute 接受任务但持有不运行；shutdown 记录状态；
     * awaitTermination 恒返 false —— 与 JDK ThreadPoolExecutor 在 60s 到期、
     * 仍有任务未完成时的返回值语义一致。仅实现 loadIntegrations 走到的三个方法，
     * 其余入口与本缺陷无关，调用即抛 UnsupportedOperationException 暴露误用。
     */
    private static final class TimedOutHoldingExecutor implements ExecutorService {
        private final List<Runnable> heldTasks = new ArrayList<Runnable>();
        private boolean shutdown = false;
        private int awaitTerminationCalls = 0;

        @Override
        public void execute(Runnable command) {
            heldTasks.add(command);
        }

        @Override
        public void shutdown() {
            shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            List<Runnable> remaining = new ArrayList<Runnable>(heldTasks);
            heldTasks.clear();
            return remaining;
        }

        @Override
        public boolean isShutdown() {
            return shutdown;
        }

        @Override
        public boolean isTerminated() {
            return shutdown && heldTasks.isEmpty();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            awaitTerminationCalls++;
            // 超时到期语义：池中仍有未完成任务 → false（loadIntegrations :646 走 warn 分支）。
            return heldTasks.isEmpty();
        }

        int pendingTaskCount() {
            return heldTasks.size();
        }

        int awaitTerminationCalls() {
            return awaitTerminationCalls;
        }

        @Override
        public <T> Future<T> submit(Callable<T> task) {
            throw new UnsupportedOperationException("本复刻池仅复刻 loadIntegrations 的 execute/shutdown/awaitTermination 路径");
        }

        @Override
        public <T> Future<T> submit(Runnable task, T result) {
            throw new UnsupportedOperationException("本复刻池仅复刻 loadIntegrations 的 execute/shutdown/awaitTermination 路径");
        }

        @Override
        public Future<?> submit(Runnable task) {
            throw new UnsupportedOperationException("本复刻池仅复刻 loadIntegrations 的 execute/shutdown/awaitTermination 路径");
        }

        @Override
        public <T> List<Future<T>> invokeAll(java.util.Collection<? extends Callable<T>> tasks) {
            throw new UnsupportedOperationException("本复刻池仅复刻 loadIntegrations 的 execute/shutdown/awaitTermination 路径");
        }

        @Override
        public <T> List<Future<T>> invokeAll(java.util.Collection<? extends Callable<T>> tasks,
                                             long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException("本复刻池仅复刻 loadIntegrations 的 execute/shutdown/awaitTermination 路径");
        }

        @Override
        public <T> T invokeAny(java.util.Collection<? extends Callable<T>> tasks) {
            throw new UnsupportedOperationException("本复刻池仅复刻 loadIntegrations 的 execute/shutdown/awaitTermination 路径");
        }

        @Override
        public <T> T invokeAny(java.util.Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException("本复刻池仅复刻 loadIntegrations 的 execute/shutdown/awaitTermination 路径");
        }
    }

    private static Object getStaticField(String name) throws Exception {
        Field field = IntegrationManager.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    private static void setStaticField(String name, Object value) throws Exception {
        Field field = IntegrationManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        if (!file.delete()) {
            file.deleteOnExit();
        }
    }
}
