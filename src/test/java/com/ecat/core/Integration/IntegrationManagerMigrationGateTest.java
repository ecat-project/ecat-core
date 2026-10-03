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
import com.ecat.core.ConfigEntry.YmlConfigEntryPersistence;
import com.ecat.core.ConfigFormatException;
import com.ecat.core.EcatCore;
import com.ecat.core.FormatVersion;
import com.ecat.core.State.StateManager;
import com.ecat.core.Utils.Log;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 配置条目版本迁移的门控/执法/写回行为回归锁：门控三路（快路跳过/旧版迁移/越界 fail-closed）、
 * 迁移结果两阶段执法（先全量校验后写回，整批拒写回语义）、半迁移断点续推、
 * 坐标级失败隔离、运行时 enable 路同款门控。
 *
 * <p>范式复用 IntegrationManagerStartupReportTest：mock core/registry + 假集成直驱
 * {@code loadExistingConfigEntriesForCoordinate}；fixture entry 一律显式 version。
 * 期望值单源：一律引用 {@link IntegrationBase#DEFAULT_ENTRY_FORMAT_VERSION}，
 * 禁散落 "4.0" 字面量，期望值一律以声明常量为单源。
 * 全部同步直驱，无 sleep/真实等待。
 *
 * @author coffee
 */
public class IntegrationManagerMigrationGateTest {

    /** 声明版本单源：生产常量即测试期望的唯一来源 */
    private static final String DECLARED = IntegrationBase.DEFAULT_ENTRY_FORMAT_VERSION;
    private static final String COORD = "com.ecat:gate";
    private static final String COORD_OK = "com.ecat:gate-ok";

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
        Mockito.when(core.getEntryRegistry()).thenReturn(entryRegistry);
    }

    @After
    public void tearDown() throws Exception {
        mocks.close();
        deleteRecursively(new File(".ecat-data/core/config_entries/com.ecatgate"));
    }

    // ==================== 假集成（可探测门控是否调度） ====================

    /** 带调度探针的假集成基座：门控应「需要才调」，探针即快路执法的牙 */
    private static class ProbeIntegration extends IntegrationBase {
        boolean mergeEntriesCalled;
        int createEntryCalls;

        @Override public void onInit() { }

        @Override public void onStart() { }

        @Override public void onPause() { }

        @Override public ConfigEntry createEntry(ConfigEntry entry) {
            createEntryCalls++;
            return entry;
        }
    }

    /** 迁移原样返回（不推进版本）——执法第 1 查应整批拒 */
    private static class NoAdvanceIntegration extends ProbeIntegration {
        @Override public List<ConfigEntry> mergeEntries(List<ConfigEntry> entries) {
            mergeEntriesCalled = true;
            return new ArrayList<>(entries);
        }
    }

    /** 迁移抛异常——坐标级隔离路径 */
    private static class ThrowingMigrationIntegration extends ProbeIntegration {
        @Override public List<ConfigEntry> mergeEntries(List<ConfigEntry> entries) {
            mergeEntriesCalled = true;
            throw new RuntimeException("boom-migrate");
        }
    }

    /** 丢一条的迁移——执法第 2 查（id 集合一致）应拒 */
    private static class DroppingMigrationIntegration extends ProbeIntegration {
        @Override public List<ConfigEntry> mergeEntries(List<ConfigEntry> entries) {
            mergeEntriesCalled = true;
            List<ConfigEntry> merged = new ArrayList<>();
            for (ConfigEntry entry : entries) {
                if (FormatVersion.compare(entry.getVersion(), DECLARED) < 0) {
                    merged.add(withVersion(entry, DECLARED));
                }
                // 版本已==声明的条目被吞：id 集合缺员
            }
            return merged;
        }
    }

    // ==================== 门控三路 ====================

    @Test
    public void gate_skipsMergeWhenAllCurrent() {
        IntegrationManager manager = newManager();
        NoAdvanceIntegration integration = new NoAdvanceIntegration();
        Mockito.when(integrationRegistry.getIntegration(COORD)).thenReturn(integration);
        Mockito.when(entryRegistry.listByCoordinate(COORD))
                .thenReturn(Arrays.asList(
                        entry("e1", DECLARED), entry("e2", DECLARED)));

        manager.loadExistingConfigEntriesForCoordinate(COORD, new StartupLoadTracker());

        assertFalse("全条目==声明时不得调 mergeEntries(快路零开销)", integration.mergeEntriesCalled);
        assertEquals("两条 entry 均应正常 createEntry", 2, integration.createEntryCalls);
    }

    @Test
    public void gate_rejectsNewerThanDeclared() {
        IntegrationManager manager = newManager();
        NoAdvanceIntegration integration = new NoAdvanceIntegration();
        Mockito.when(integrationRegistry.getIntegration(COORD)).thenReturn(integration);
        Mockito.when(entryRegistry.listByCoordinate(COORD))
                .thenReturn(Arrays.asList(
                        entry("e1", DECLARED), entry("e2", "5.0")));

        try {
            manager.loadExistingConfigEntriesForCoordinate(COORD, new StartupLoadTracker());
            fail("数据比声明新必须 fail-closed");
        } catch (ConfigFormatException expected) {
            assertTrue("异常消息应点名越界条目",
                    expected.getMessage().contains("e2") && expected.getMessage().contains("5.0"));
        }
        assertFalse("越界属终态故障,迁移不得被调度", integration.mergeEntriesCalled);
        assertEquals("fail-closed 不得进入 createEntry", 0, integration.createEntryCalls);
    }

    // ==================== 迁移执法（两阶段,整批拒写回） ====================

    @Test
    public void enforcement_rejectsUnadvancedVersion() {
        IntegrationManager manager = newManager();
        NoAdvanceIntegration integration = new NoAdvanceIntegration();
        Mockito.when(integrationRegistry.getIntegration(COORD)).thenReturn(integration);
        Mockito.when(entryRegistry.listByCoordinate(COORD))
                .thenReturn(Arrays.asList(
                        entry("e1", "3.9"), entry("e2", DECLARED)));

        try {
            manager.loadExistingConfigEntriesForCoordinate(COORD, new StartupLoadTracker());
            fail("迁移后版本未推进=迁移函数缺陷,必须拒");
        } catch (ConfigFormatException expected) {
            assertTrue("异常消息应点名未推进条目", expected.getMessage().contains("e1"));
        }
        verify(entryRegistry, never()).updateMigratedEntry(any());
        assertEquals("执法失败=整批拒,不得有 entry 进入 createEntry",
                0, integration.createEntryCalls);
    }

    @Test
    public void enforcement_rejectsIdSetMismatch() {
        IntegrationManager manager = newManager();
        DroppingMigrationIntegration integration = new DroppingMigrationIntegration();
        Mockito.when(integrationRegistry.getIntegration(COORD)).thenReturn(integration);
        Mockito.when(entryRegistry.listByCoordinate(COORD))
                .thenReturn(Arrays.asList(
                        entry("e1", "3.9"), entry("e2", DECLARED)));

        try {
            manager.loadExistingConfigEntriesForCoordinate(COORD, new StartupLoadTracker());
            fail("迁移结果 id 集合与输入不一致必须拒");
        } catch (ConfigFormatException expected) {
            assertTrue("异常消息应点名缺失条目", expected.getMessage().contains("e2"));
        }
        verify(entryRegistry, never()).updateMigratedEntry(any());
        assertEquals(0, integration.createEntryCalls);
    }

    // ==================== 半迁移断点续推 ====================

    @Test
    public void resume_migratesOnlyStaleOnMixedDisk() {
        IntegrationManager manager = newManager();
        ProbeIntegration integration = new ProbeIntegration() {
            @Override public List<ConfigEntry> mergeEntries(List<ConfigEntry> entries) {
                mergeEntriesCalled = true;
                List<ConfigEntry> merged = new ArrayList<>();
                for (ConfigEntry entry : entries) {
                    if (FormatVersion.compare(entry.getVersion(), DECLARED) < 0) {
                        merged.add(withVersion(entry, DECLARED));
                    } else {
                        merged.add(entry);
                    }
                }
                return merged;
            }
        };
        Mockito.when(integrationRegistry.getIntegration(COORD)).thenReturn(integration);
        Mockito.when(entryRegistry.listByCoordinate(COORD))
                .thenReturn(Arrays.asList(
                        entry("e1", DECLARED), entry("e2", "3.9")));
        // 执法第 3 查(id 存在于缓存)：mock 环境显式声明缓存在位(生产由 loadAllEntries 保证)
        Mockito.when(entryRegistry.getByEntryId("e1"))
                .thenReturn(Mockito.mock(ConfigEntry.class));
        Mockito.when(entryRegistry.getByEntryId("e2"))
                .thenReturn(Mockito.mock(ConfigEntry.class));

        StartupLoadTracker tracker = new StartupLoadTracker();
        manager.loadExistingConfigEntriesForCoordinate(COORD, tracker);

        ArgumentCaptor<ConfigEntry> written = ArgumentCaptor.forClass(ConfigEntry.class);
        verify(entryRegistry, times(1)).updateMigratedEntry(written.capture());
        assertEquals("只写回版本变化的条目", "e2", written.getValue().getEntryId());
        assertEquals(DECLARED, written.getValue().getVersion());
        assertEquals("两条 entry(含已推进的)均应进入 createEntry", 2, integration.createEntryCalls);
        assertTrue("半迁移续推不得记失败",
                tracker.render(1).contains("failed=0"));
    }

    // ==================== 坐标级失败隔离 ====================

    @Test
    public void failure_isolatesCoordinate() {
        IntegrationManager manager = newManager();
        ThrowingMigrationIntegration failing = new ThrowingMigrationIntegration();
        ProbeIntegration healthy = new ProbeIntegration();
        Mockito.when(integrationRegistry.getIntegration(COORD)).thenReturn(failing);
        Mockito.when(integrationRegistry.getIntegration(COORD_OK)).thenReturn(healthy);
        Mockito.when(integrationRegistry.getAllCoordinates())
                .thenReturn(new LinkedHashSet<>(Arrays.asList(COORD, COORD_OK)));
        Mockito.when(entryRegistry.listByCoordinate(COORD))
                .thenReturn(Arrays.asList(entry("e1", "3.9")));
        Mockito.when(entryRegistry.listByCoordinate(COORD_OK))
                .thenReturn(Arrays.asList(entry("ok-1", DECLARED)));

        StartupLoadTracker tracker = new StartupLoadTracker();
        manager.loadExistingConfigEntries(tracker);

        String line = tracker.render(1);
        assertTrue("迁移失败应经既有隔离通道记 entry-restore 失败",
                line.contains("failed=1") && line.contains(COORD) && line.contains("entry-restore:"));
        assertEquals("健康坐标不受牵连", 1, healthy.createEntryCalls);
        assertEquals("失败坐标不得进入 createEntry", 0, failing.createEntryCalls);
    }

    // ==================== 默认实现零行为变更(回归) ====================

    @Test
    public void defaultImplementation_zeroBehaviorChange() {
        IntegrationManager manager = newManager();
        ProbeIntegration integration = new ProbeIntegration(); // 不覆写 mergeEntries(默认 null)
        Mockito.when(integrationRegistry.getIntegration(COORD)).thenReturn(integration);
        Mockito.when(entryRegistry.listByCoordinate(COORD))
                .thenReturn(Arrays.asList(entry("e1", DECLARED)));

        manager.loadExistingConfigEntriesForCoordinate(COORD, new StartupLoadTracker());

        assertEquals("现状全仓默认路径回归:无异常正常建设备", 1, integration.createEntryCalls);
    }

    @Test
    public void defaultImplementation_staleWithNullReturn_isDefect() {
        IntegrationManager manager = newManager();
        ProbeIntegration integration = new ProbeIntegration(); // 默认 null+盘面有旧版
        Mockito.when(integrationRegistry.getIntegration(COORD)).thenReturn(integration);
        Mockito.when(entryRegistry.listByCoordinate(COORD))
                .thenReturn(Arrays.asList(entry("e1", "3.9")));

        try {
            manager.loadExistingConfigEntriesForCoordinate(COORD, new StartupLoadTracker());
            fail("声明格式存在旧版条目但迁移返回 null=迁移函数缺陷,必须拒");
        } catch (ConfigFormatException expected) {
            // 门控显式失败即预期
        }
        assertEquals(0, integration.createEntryCalls);
    }

    // ==================== 运行时 enable 路同款门控 ====================

    @Test
    public void runtimeEnablePathSameGate() throws Exception {
        IntegrationManager manager = newManager();
        NoAdvanceIntegration integration = new NoAdvanceIntegration();
        Mockito.when(integrationRegistry.getIntegration(COORD)).thenReturn(integration);
        Mockito.when(entryRegistry.listByCoordinate(COORD))
                .thenReturn(Arrays.asList(entry("e1", DECLARED), entry("e2", "5.0")));

        Method runtime = IntegrationManager.class
                .getDeclaredMethod("loadExistingConfigEntriesForCoordinate", String.class);
        runtime.setAccessible(true);
        try {
            runtime.invoke(manager, COORD);
            fail("运行时 enable 路应与 boot 路同款门控");
        } catch (InvocationTargetException e) {
            assertTrue("越界应以 ConfigFormatException 显形",
                    e.getCause() instanceof ConfigFormatException);
        }
        assertFalse("tracker=null 不影响门控判定", integration.mergeEntriesCalled);
    }

    // ==================== updateMigratedEntry 落盘契约 ====================

    @Test
    public void updateMigratedEntry_persistsAdvancedVersion() {
        YmlConfigEntryPersistence persistence = new YmlConfigEntryPersistence();
        ConfigEntry stale = new ConfigEntry.Builder()
                .entryId("gate9-1")
                .coordinate("com.ecatgate:t9")
                .uniqueId("unique-gate9-1")
                .title("Gate 9 Entry")
                .enabled(true)
                .version("3.9")
                .createTime(ZonedDateTime.now())
                .updateTime(ZonedDateTime.now())
                .build();
        persistence.save(stale);

        // 真 registry(构造即 loadAll 入缓存)——迁移前缓存里的版本是旧值,
        // 若误走 withUpdate 版本回抹路径,落盘会被抹回 "3.9",本断言即有牙
        ConfigEntryRegistry realRegistry = new ConfigEntryRegistry(core, persistence);
        ConfigEntry migrated = withVersion(stale, DECLARED);
        realRegistry.updateMigratedEntry(migrated);

        List<ConfigEntry> reloaded = persistence.loadAll();
        ConfigEntry onDisk = reloaded.stream()
                .filter(e -> "gate9-1".equals(e.getEntryId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("写回后 entry 应仍在盘上"));
        assertEquals("迁移推进的版本必须原样落盘(防 withUpdate 版本回抹)",
                DECLARED, onDisk.getVersion());
        assertEquals("写回必须同步换缓存", DECLARED,
                realRegistry.getByEntryId("gate9-1").getVersion());
    }

    // ==================== 辅助 ====================

    private IntegrationManager newManager() {
        return new IntegrationManager(core, integrationRegistry, stateManager);
    }

    private static ConfigEntry entry(String id, String version) {
        return new ConfigEntry.Builder()
                .entryId(id)
                .coordinate(COORD)
                .uniqueId("unique-" + id)
                .title("Gate Entry " + id)
                .enabled(true)
                .version(version)
                .createTime(ZonedDateTime.now())
                .updateTime(ZonedDateTime.now())
                .build();
    }

    /** 构造同 id 迁移副本(迁移步负责把 version 推进到目标声明值) */
    private static ConfigEntry withVersion(ConfigEntry source, String version) {
        Map<String, Object> data = source.getData();
        return new ConfigEntry.Builder()
                .entryId(source.getEntryId())
                .coordinate(source.getCoordinate())
                .uniqueId(source.getUniqueId())
                .title(source.getTitle())
                .data(data != null ? new HashMap<>(data) : null)
                .enabled(source.isEnabled())
                .version(version)
                .createTime(source.getCreateTime())
                .updateTime(source.getUpdateTime())
                .build();
    }

    private static void deleteRecursively(File directory) throws IOException {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteRecursively(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }
}
