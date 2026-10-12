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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.lang.reflect.Field;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import com.ecat.core.ConfigEntry.ConfigEntry;
import com.ecat.core.ConfigEntry.ConfigEntryRegistry;
import com.ecat.core.EcatCore;
import com.ecat.core.State.StateManager;

/**
 * 启动全接入看门狗（arch-review 27 号 W1）IntegrationManager 侧测试：
 * onStart 挂死→超时抛出+坐标标记、entry 恢复挂死→整坐标放弃但他人继续、
 * onStart 超时坐标的 entry 恢复被跳过。启动契约：任何集成挂死都不拖死启动。
 *
 * <p>同步纪律：latch 证任务已进入挂死段；超时是等看门狗执法事件（预算收紧到 300ms 内）。
 *
 * @author coffee
 */
public class IntegrationManagerStartupTimeoutTest {

    private static final long TIGHT_BUDGET_MS = 300L;

    private AutoCloseable mocks;
    private EcatCore core;
    private IntegrationRegistry integrationRegistry;
    private StateManager stateManager;
    private ConfigEntryRegistry entryRegistry;

    @Before
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        core = Mockito.mock(EcatCore.class);
        integrationRegistry = Mockito.mock(IntegrationRegistry.class);
        stateManager = Mockito.mock(StateManager.class);
        entryRegistry = Mockito.mock(ConfigEntryRegistry.class);
        Mockito.when(core.getEntryRegistry()).thenReturn(entryRegistry);
    }

    @After
    public void tearDown() throws Exception {
        mocks.close();
    }

    // ==================== 假集成 ====================

    private abstract static class EntryIntegration extends IntegrationBase {
        @Override public void onInit() { }
        @Override public void onStart() { }
        @Override public void onPause() { }
    }

    /** onStart 不可中断挂死（忽略 interrupt，直到测试放行） */
    private static class HungOnStartIntegration extends EntryIntegration {
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);

        @Override public void onStart() {
            started.countDown();
            // 不可中断挂死的模拟：吃掉所有 interrupt，直到测试显式放行
            while (release.getCount() > 0) {
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException tolerated) {
                }
            }
        }
    }

    /** createEntry 第一个 entry 即挂死（后续 entry 应被整坐标放弃） */
    private static class HungCreateEntryIntegration extends EntryIntegration {
        final CountDownLatch started = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);

        @Override public ConfigEntry createEntry(ConfigEntry entry) {
            started.countDown();
            // 同 onStart：吃掉所有 interrupt 直到放行（真实不可中断 IO 的行为同构）
            while (release.getCount() > 0) {
                try {
                    release.await(10, TimeUnit.SECONDS);
                } catch (InterruptedException tolerated) {
                }
            }
            return entry;
        }
    }

    private static class FastIntegration extends EntryIntegration {
        @Override public ConfigEntry createEntry(ConfigEntry entry) { return entry; }
    }

    // ==================== 用例 ====================

    /** onStart 挂死：guardedOnStart 在预算内抛出、坐标标记超时、tracker 记 timeouts 账。 */
    @Test(timeout = 10000)
    public void hungOnStart_throwsWithinBudgetAndMarksCoordinate() throws Exception {
        IntegrationManager manager = newManager();
        HungOnStartIntegration hung = new HungOnStartIntegration();
        StartupLoadTracker tracker = new StartupLoadTracker();

        long start = System.nanoTime();
        try {
            manager.guardedOnStart(hung, "com.ecat:hung", tracker);
            fail("挂死 onStart 应在预算内抛出");
        } catch (RuntimeException e) {
            assertNotNull(e.getCause());
        }
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        assertTrue("应在预算附近抛出, 实际 " + elapsedMs + "ms", elapsedMs < 3000);

        String line = tracker.render(TimeUnit.MILLISECONDS.toNanos(100));
        assertTrue(line, line.contains("timeouts=1"));
        assertTrue(line, line.contains("com.ecat:hung:onStart"));

        // 坐标已标记 → entry 恢复阶段会跳过
        assertTrue(isMarkedTimedOut(manager, "com.ecat:hung"));
        hung.release.countDown();
    }

    /** entry 恢复挂死：本坐标被放弃（记 timeout+failure），其他坐标照常恢复。 */
    @Test(timeout = 10000)
    public void hungEntryRestore_coordinateAbandonedOthersContinue() throws Exception {
        IntegrationManager manager = newManager();
        HungCreateEntryIntegration hung = new HungCreateEntryIntegration();
        FastIntegration healthy = new FastIntegration();

        Mockito.when(integrationRegistry.getIntegration("com.ecat:hung")).thenReturn((IntegrationBase) hung);
        Mockito.when(entryRegistry.listByCoordinate("com.ecat:hung"))
                .thenReturn(Arrays.asList(entry("e1", "com.ecat:hung"), entry("e2", "com.ecat:hung")));
        Mockito.when(integrationRegistry.getIntegration("com.ecat:ok")).thenReturn(healthy);
        Mockito.when(entryRegistry.listByCoordinate("com.ecat:ok"))
                .thenReturn(Arrays.asList(entry("ok-1", "com.ecat:ok")));

        StartupLoadTracker tracker = new StartupLoadTracker();
        manager.guardedRestoreEntries("com.ecat:hung", tracker);

        String afterHung = tracker.render(TimeUnit.MILLISECONDS.toNanos(100));
        assertTrue(afterHung, afterHung.contains("com.ecat:hung:entry-restore:timeout"));

        // 其他人继续：健康坐标不受挂死坐标影响
        manager.guardedRestoreEntries("com.ecat:ok", tracker);
        String line = tracker.render(TimeUnit.MILLISECONDS.toNanos(100));
        assertTrue(line, line.contains("entries=1"));
        assertTrue(line, line.contains("com.ecat:hung:entry-restore"));

        hung.release.countDown();
    }

    /** onStart 超时的坐标：entry 恢复整体跳过（不再喂 entry 给挂死集成）。 */
    @Test(timeout = 10000)
    public void timedOutOnStartCoordinate_entryRestoreSkipped() throws Exception {
        IntegrationManager manager = newManager();
        AtomicInteger createEntryCalls = new AtomicInteger();
        EntryIntegration shouldNotRun = new FastIntegration() {
            @Override public ConfigEntry createEntry(ConfigEntry entry) {
                createEntryCalls.incrementAndGet();
                return entry;
            }
        };
        Mockito.when(integrationRegistry.getIntegration("com.ecat:hung")).thenReturn(shouldNotRun);
        Mockito.when(entryRegistry.listByCoordinate("com.ecat:hung"))
                .thenReturn(Arrays.asList(entry("e1", "com.ecat:hung")));

        // 先把坐标标记为超时（模拟 guardedOnStart 的执法结果），再走全量恢复入口
        Field field = IntegrationManager.class.getDeclaredField("startupTimedOutCoordinates");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Set<String> marked = (java.util.Set<String>) field.get(manager);
        marked.add("com.ecat:hung");

        StartupLoadTracker tracker = new StartupLoadTracker();
        Mockito.when(integrationRegistry.getAllCoordinates())
                .thenReturn(new java.util.HashSet<>(Arrays.asList("com.ecat:hung")));
        manager.loadExistingConfigEntries(tracker);

        assertEquals("超时坐标不应再执行 createEntry", 0, createEntryCalls.get());
        String line = tracker.render(TimeUnit.MILLISECONDS.toNanos(100));
        assertTrue(line, line.contains("com.ecat:hung:entry-restore:skipped"));
        assertTrue(line, line.contains("timeouts=1"));
    }

    // ==================== 辅助 ====================

    private IntegrationManager newManager() {
        IntegrationManager manager = new IntegrationManager(core, integrationRegistry, stateManager);
        manager.guardedTimeoutMs = TIGHT_BUDGET_MS;
        return manager;
    }

    private static boolean isMarkedTimedOut(IntegrationManager manager, String coordinate) throws Exception {
        Field field = IntegrationManager.class.getDeclaredField("startupTimedOutCoordinates");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Set<String> marked = (java.util.Set<String>) field.get(manager);
        return marked.contains(coordinate);
    }

    private static ConfigEntry entry(String id, String coordinate) {
        return new ConfigEntry.Builder()
                .entryId(id)
                .coordinate(coordinate)
                .uniqueId("unique-" + id)
                .title("Test Entry " + id)
                .enabled(true)
                .createTime(ZonedDateTime.now())
                .updateTime(ZonedDateTime.now())
                .version(1)
                .build();
    }
}
