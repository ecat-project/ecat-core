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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.File;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.ecat.core.ConfigEntry.ConfigEntry;
import com.ecat.core.Device.DeviceBase;
import com.ecat.core.Device.DeviceRegistry;
import com.ecat.core.Device.YmlDevicePersistence;
import com.ecat.core.EcatCore;
import com.ecat.core.Task.NamedThreadFactory;
import com.ecat.core.Task.TaskManager;

/**
 * 框架 chokepoint 移除动作收尾测试（18 号设计 §3.3，19 号 v2「设备零调度」形态）：
 * 不调 device.cancelManagedTasks，走 IntegrationDeviceBase 的 removeEntry / disableEntry /
 * reconfigureEntry / onPause / onRelease 路径，断言设备经 onRemove 注册的移除动作
 * （SDK 轮询句柄 cancel 等）均被框架执行——句柄丢弃型设备（tianhong stop()=no-op）
 * 零改码获得治理。
 *
 * <p>设备形态按 19 号 v2 真实形态模拟：周期任务由「传输域 SDK」提交（fixture 用测试自备
 * 真池代持——域 SDK 定时器形态，设备类自身不调任何 core 调度入口），句柄经
 * {@code onRemove(future::cancel)} 注册到 RemovalHost，由框架生命周期 chokepoint
 * 兜底执行。</p>
 *
 * <p>同步纪律：正向事件 CountDownLatch；负向断言有界超时 await 返回 false，无 Thread.sleep。</p>
 */
public class IntegrationDeviceBaseRemovalActionsTest {

    /** 测试自备真池（域 SDK 定时器形态，W7 引擎退役后 SDK 自持池的 fixture 抽象）。 */
    private ScheduledThreadPoolExecutor engine;
    private EcatCore core;
    private DeviceRegistry registry;
    private File tmpDir;

    @Before
    public void setUp() throws Exception {
        engine = new ScheduledThreadPoolExecutor(1, new NamedThreadFactory("idb-sweep-test", true));
        TaskManager taskManager = mock(TaskManager.class);
        core = mock(EcatCore.class);
        when(core.getTaskManager()).thenReturn(taskManager);
        registry = new DeviceRegistry();
        tmpDir = Files.createTempDirectory("ecat-idb-sweep").toFile();
        registry.setPersistence(new YmlDevicePersistence(tmpDir.getAbsolutePath()));
    }

    @After
    public void tearDown() {
        engine.shutdownNow();
    }

    /**
     * tianhong 型设备：start() 只注册移除动作（句柄 cancel），stop() no-op——句柄与周期的
     * 提交归「SDK 域」（fixture 由测试自备池代持，回调经 onRunRef 可切换，供 reconfigure 区分 old/new）。
     */
    private DeviceBase newTianhongStyleDevice(ConfigEntry entry, AtomicReference<Runnable> onRunRef,
                                              AtomicBoolean stopThrows) {
        DeviceBase device = new DeviceBase(entry) {
            @Override public void init() {}
            @Override public void start() {
                // SDK 域提交周期（测试自备池代持），句柄经 onRemove 注册——框架 stop 路径兜底 cancel
                Runnable r = onRunRef.get();
                ScheduledFuture<?> future = engine.scheduleWithFixedDelay(
                    r::run, 0, 50, TimeUnit.MILLISECONDS);
                onRemove(() -> future.cancel(false));
            }
            @Override public void stop() {
                if (stopThrows != null && stopThrows.get()) {
                    throw new IllegalStateException("集成 stop 崩溃（Q3 场景）");
                }
            }
            @Override public void release() {}
        };
        device.load(core);
        return device;
    }

    private IntegrationDeviceBase newIntegration(AtomicReference<Runnable> onRunRef,
                                                 AtomicBoolean stopThrows) {
        IntegrationDeviceBase integration = new IntegrationDeviceBase() {
            @Override
            protected DeviceBase createDeviceFromEntry(ConfigEntry entry) {
                return newTianhongStyleDevice(entry, onRunRef, stopThrows);
            }
        };
        integration.deviceRegistry = registry;
        integration.core = core;
        return integration;
    }

    private ConfigEntry newEntry(String uniqueId, String entryId) {
        ConfigEntry e = new ConfigEntry();
        e.setEntryId(entryId);
        e.setUniqueId(uniqueId);
        e.setCoordinate("com.ecat:test-m2");
        Map<String, Object> data = new HashMap<>();
        data.put("name", "n-" + uniqueId);
        e.setData(data);
        return e;
    }

    private static boolean await(CountDownLatch latch, long ms) throws InterruptedException {
        return latch.await(ms, TimeUnit.MILLISECONDS);
    }

    /** 计数闭包：count++ 并触发两级 latch（firstRun=已运行；extraRun=「不应再出现」的负向探针）。 */
    private static Runnable countingRun(AtomicInteger count, CountDownLatch... latches) {
        return () -> {
            count.incrementAndGet();
            for (CountDownLatch l : latches) {
                l.countDown();
            }
        };
    }

    private void assertPeriodicStops(AtomicInteger count, int atStop, CountDownLatch probe) throws Exception {
        assertThat("移除动作执行后周期任务不得再执行（负向有界等待）", await(probe, 500), is(false));
        assertThat("计数不得持续增长（容忍收尾前在途的一轮）", count.get() <= atStop + 1, is(true));
    }

    /** T2a：removeEntry 路径——框架执行移除动作，停掉 no-op-stop 设备的 SDK 周期任务。 */
    @Test
    public void removeEntry_stopsPeriodicTaskOfNoOpStopDevice() throws Exception {
        AtomicInteger count = new AtomicInteger();
        CountDownLatch firstRun = new CountDownLatch(1);
        CountDownLatch probeRun = new CountDownLatch(3);
        AtomicReference<Runnable> onRun = new AtomicReference<>(countingRun(count, firstRun, probeRun));
        IntegrationDeviceBase integration = newIntegration(onRun, null);

        ConfigEntry e = newEntry("u1", "entry-1");
        integration.createEntry(e);
        DeviceBase device = integration.getAllDevices().iterator().next();
        assertTrue("周期任务应已执行", await(firstRun, 5_000));
        int atRemove = count.get();

        integration.removeEntry(e.getEntryId());

        assertPeriodicStops(count, atRemove, probeRun);
        try {
            device.onRemove(() -> { });
            fail("removeEntry 后设备移除面应已 swept，注册必须拒绝");
        } catch (RejectedExecutionException expected) {
            // 严格模式
        }
    }

    /** T2b：disableEntry 路径。 */
    @Test
    public void disableEntry_stopsPeriodicTaskOfNoOpStopDevice() throws Exception {
        AtomicInteger count = new AtomicInteger();
        CountDownLatch firstRun = new CountDownLatch(1);
        CountDownLatch probeRun = new CountDownLatch(3);
        AtomicReference<Runnable> onRun = new AtomicReference<>(countingRun(count, firstRun, probeRun));
        IntegrationDeviceBase integration = newIntegration(onRun, null);

        ConfigEntry e = newEntry("u2", "entry-2");
        integration.createEntry(e);
        assertTrue(await(firstRun, 5_000));
        int atDisable = count.get();

        integration.disableEntry(e.getEntryId());

        assertPeriodicStops(count, atDisable, probeRun);
    }

    /** T2c：onPause 路径（集成暂停，只 stop 不 release）。 */
    @Test
    public void onPause_stopsPeriodicTaskOfNoOpStopDevice() throws Exception {
        AtomicInteger count = new AtomicInteger();
        CountDownLatch firstRun = new CountDownLatch(1);
        CountDownLatch probeRun = new CountDownLatch(3);
        AtomicReference<Runnable> onRun = new AtomicReference<>(countingRun(count, firstRun, probeRun));
        IntegrationDeviceBase integration = newIntegration(onRun, null);

        integration.createEntry(newEntry("u3", "entry-3"));
        assertTrue(await(firstRun, 5_000));
        int atPause = count.get();

        integration.onPause();

        assertPeriodicStops(count, atPause, probeRun);
    }

    /** T2d + Q3：stop() 抛异常——异常上抛不吞，但移除动作必执行（try-finally）。 */
    @Test
    public void removeEntry_stopThrows_removalStillExecutedAndExceptionPropagates() throws Exception {
        AtomicInteger count = new AtomicInteger();
        CountDownLatch firstRun = new CountDownLatch(1);
        CountDownLatch probeRun = new CountDownLatch(3);
        AtomicBoolean stopThrows = new AtomicBoolean(true);
        AtomicReference<Runnable> onRun = new AtomicReference<>(countingRun(count, firstRun, probeRun));
        IntegrationDeviceBase integration = newIntegration(onRun, stopThrows);

        ConfigEntry e = newEntry("u4", "entry-4");
        integration.createEntry(e);
        assertTrue(await(firstRun, 5_000));
        int atRemove = count.get();

        try {
            integration.removeEntry(e.getEntryId());
            fail("集成 stop() 异常必须照原语义上抛（不吞）");
        } catch (IllegalStateException expected) {
            assertThat(expected.getMessage(), is("集成 stop 崩溃（Q3 场景）"));
        }

        // finally 已执行移除动作：周期任务已停
        assertPeriodicStops(count, atRemove, probeRun);
    }

    /** T2e/T6：reconfigureEntry——old 设备任务停，new 设备周期任务正常运行（entry 复用、id 复原）。 */
    @Test
    public void reconfigureEntry_oldTaskStops_newDeviceRuns() throws Exception {
        AtomicInteger oldCount = new AtomicInteger();
        AtomicInteger newCount = new AtomicInteger();
        CountDownLatch oldFirst = new CountDownLatch(1);
        CountDownLatch oldProbe3 = new CountDownLatch(3);   // 负向探针：容忍收尾前在途一轮，不得累计第 3 次
        CountDownLatch newSecond = new CountDownLatch(2);   // 正向：new 设备应执行 ≥2 次
        AtomicReference<Runnable> onRun = new AtomicReference<>(countingRun(oldCount, oldFirst, oldProbe3));
        IntegrationDeviceBase integration = newIntegration(onRun, null);

        integration.createEntry(newEntry("u5", "entry-5"));
        assertTrue(await(oldFirst, 5_000));
        int atReconfigure = oldCount.get();

        // 切到 new 设备闭包后再 reconfigure：createDeviceFromEntry 产出的 new 设备用 newCount 计数
        onRun.set(countingRun(newCount, newSecond));
        integration.reconfigureEntry("entry-5", newEntry("u5", "entry-5"));

        assertThat("reconfigure 后 old 设备任务不得再执行", await(oldProbe3, 500), is(false));
        assertThat("old 计数不得持续增长（容忍在途一轮）", oldCount.get() <= atReconfigure + 1, is(true));
        assertTrue("new 设备周期任务应正常运行", await(newSecond, 5_000));
    }

    /** T2f：onRelease 路径——只 release 不 stop，但补幂等移除动作收尾。 */
    @Test
    public void onRelease_stopsPeriodicTaskViaIdempotentSweep() throws Exception {
        AtomicInteger count = new AtomicInteger();
        CountDownLatch firstRun = new CountDownLatch(1);
        CountDownLatch probeRun = new CountDownLatch(3);
        AtomicReference<Runnable> onRun = new AtomicReference<>(countingRun(count, firstRun, probeRun));
        IntegrationDeviceBase integration = newIntegration(onRun, null);

        integration.createEntry(newEntry("u6", "entry-6"));
        assertTrue(await(firstRun, 5_000));
        int atRelease = count.get();

        integration.onRelease();

        assertPeriodicStops(count, atRelease, probeRun);
    }
}
