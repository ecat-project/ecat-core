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

import com.ecat.core.ConfigEntry.ConfigEntry;
import com.ecat.core.ConfigEntry.ConfigEntryRegistry;
import com.ecat.core.EcatCore;
import com.ecat.core.State.StateManager;
import com.ecat.core.Utils.Log;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Field;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.*;

/**
 * 启动健康报告（C1）IntegrationManager 侧单测：注入假集成（快/慢/失败）驱动
 * {@code loadExistingConfigEntriesForCoordinate}，断言报告行内容与启动契约 WARN 执法。
 *
 * <p>不驱动完整 loadIntegrations（涉及 jar 扫描/ClassLoader，单测不可承受），
 * 报告汇出点（ALL_LOADED 发布前一行 INFO）由 tracker.render 契约测试 +
 * 本类的埋点数据测试共同锁定。
 *
 * @author coffee
 */
public class IntegrationManagerStartupReportTest {

    private AutoCloseable mocks;
    private EcatCore core;
    private IntegrationRegistry integrationRegistry;
    private StateManager stateManager;
    private ConfigEntryRegistry entryRegistry;
    private Log log;

    @Before
    public void setUp() throws Exception {
        mocks = MockitoAnnotations.openMocks(this);
        core = Mockito.mock(EcatCore.class);
        integrationRegistry = Mockito.mock(IntegrationRegistry.class);
        stateManager = Mockito.mock(StateManager.class);
        entryRegistry = Mockito.mock(ConfigEntryRegistry.class);
        log = Mockito.mock(Log.class);
        Mockito.when(core.getEntryRegistry()).thenReturn(entryRegistry);
    }

    @After
    public void tearDown() throws Exception {
        mocks.close();
    }

    // ==================== 假集成（快/慢/失败） ====================

    private abstract static class EntryIntegration extends IntegrationBase {
        @Override public void onInit() { }
        @Override public void onStart() { }
        @Override public void onPause() { }
    }

    private static class FastIntegration extends EntryIntegration {
        @Override public ConfigEntry createEntry(ConfigEntry entry) { return entry; }
    }

    /** createEntry 自旋 ~50ms（真实耗时，非 sleep）：配合收紧预算 1ms 稳定触发契约 WARN */
    private static class SlowIntegration extends EntryIntegration {
        @Override public ConfigEntry createEntry(ConfigEntry entry) {
            busySpinMillis(50);
            return entry;
        }
    }

    private static class FailingIntegration extends EntryIntegration {
        @Override public ConfigEntry createEntry(ConfigEntry entry) {
            throw new RuntimeException("boom");
        }
    }

    private static class BrokenCallbackIntegration extends FastIntegration {
        @Override public void onAllExistEntriesLoaded(List<ConfigEntry> entries) {
            throw new RuntimeException("late boom");
        }
    }

    private static void busySpinMillis(long millis) {
        long end = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
        while (System.nanoTime() < end) {
            // 自旋占满预算：确定性制造真实耗时
        }
    }

    // ==================== 测试用例 ====================

    @Test
    public void entryRestore_recordedPerCoordinateAndEntriesCounted() throws Exception {
        IntegrationManager manager = newManager();
        IntegrationBase fast = new FastIntegration();
        Mockito.when(integrationRegistry.getIntegration("com.ecat:fast")).thenReturn(fast);
        Mockito.when(entryRegistry.listByCoordinate("com.ecat:fast"))
                .thenReturn(Arrays.asList(entry("e1", "com.ecat:fast"), entry("e2", "com.ecat:fast")));

        StartupLoadTracker tracker = new StartupLoadTracker();
        manager.loadExistingConfigEntriesForCoordinate("com.ecat:fast", tracker);

        String line = tracker.render(TimeUnit.MILLISECONDS.toNanos(1000));
        assertTrue(line, line.contains("integrations=1"));
        assertTrue(line, line.contains("entries=2"));
        assertTrue(line, line.contains("failed=0"));
        assertTrue("top5 应含该坐标", line.contains("com.ecat:fast="));
    }

    @Test
    public void failedEntry_recordedAsFailureAndNotCounted() throws Exception {
        IntegrationManager manager = newManager();
        Mockito.when(integrationRegistry.getIntegration("com.ecat:bad"))
                .thenReturn((IntegrationBase) new FailingIntegration());
        Mockito.when(entryRegistry.listByCoordinate("com.ecat:bad"))
                .thenReturn(Arrays.asList(entry("e1", "com.ecat:bad")));

        StartupLoadTracker tracker = new StartupLoadTracker();
        manager.loadExistingConfigEntriesForCoordinate("com.ecat:bad", tracker);

        String line = tracker.render(TimeUnit.MILLISECONDS.toNanos(100));
        assertTrue(line, line.contains("failed=1"));
        assertTrue(line, line.contains("failures=[com.ecat:bad:entry:e1]"));
        assertFalse("失败的 entry 不计入 entries", line.contains("entries=1"));
        assertTrue(line, line.contains("entries=0"));
    }

    @Test
    public void onAllExistEntriesLoadedFailure_recordedAsFailure() throws Exception {
        IntegrationManager manager = newManager();
        Mockito.when(integrationRegistry.getIntegration("com.ecat:latebad"))
                .thenReturn((IntegrationBase) new BrokenCallbackIntegration());
        Mockito.when(entryRegistry.listByCoordinate("com.ecat:latebad"))
                .thenReturn(Arrays.asList(entry("e1", "com.ecat:latebad")));

        StartupLoadTracker tracker = new StartupLoadTracker();
        manager.loadExistingConfigEntriesForCoordinate("com.ecat:latebad", tracker);

        String line = tracker.render(TimeUnit.MILLISECONDS.toNanos(100));
        assertTrue(line, line.contains("failed=1"));
        assertTrue(line, line.contains("com.ecat:latebad:onAllExistEntriesLoaded"));
    }

    /**
     * 启动契约执法：createEntry 超预算（预算收紧到 1ms，慢集成自旋 50ms）→ WARN 点名；
     * 快集成（远低于预算）不触发。固定睡眠/等待一概不用——自旋是真实耗时。
     */
    @Test
    public void blockingCreateEntry_warnsByContract() throws Exception {
        IntegrationManager manager = newManager();
        manager.createEntryBudgetMs = 1L;

        Mockito.when(integrationRegistry.getIntegration("com.ecat:fast")).thenReturn(new FastIntegration());
        Mockito.when(entryRegistry.listByCoordinate("com.ecat:fast"))
                .thenReturn(Arrays.asList(entry("e-fast", "com.ecat:fast")));
        Mockito.when(integrationRegistry.getIntegration("com.ecat:slow")).thenReturn(new SlowIntegration());
        Mockito.when(entryRegistry.listByCoordinate("com.ecat:slow"))
                .thenReturn(Arrays.asList(entry("e-slow", "com.ecat:slow")));

        manager.loadExistingConfigEntriesForCoordinate("com.ecat:fast", new StartupLoadTracker());
        verify(log, never()).warn(contains("createEntry 阻塞"), any(), any(), any(), any());

        manager.loadExistingConfigEntriesForCoordinate("com.ecat:slow", new StartupLoadTracker());
        verify(log).warn(contains("createEntry 阻塞"), any(), any(), any(), any());
    }

    // ==================== 辅助 ====================

    /** 构造真实 manager 并把 log 换成 mock（构造器内 LogFactory 赋值需事后覆盖才能断言 WARN） */
    private IntegrationManager newManager() throws Exception {
        IntegrationManager manager = new IntegrationManager(core, integrationRegistry, stateManager);
        Field logField = IntegrationManager.class.getDeclaredField("log");
        logField.setAccessible(true);
        logField.set(manager, log);
        return manager;
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
