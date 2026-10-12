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
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * 配置条目恢复链 3.3 语义回归锁：mergeEntries 是可选钩子，默认返回 null 即“无变更”。
 * core 不再做 entryFormatVersion / FormatVersion 门控；修改计数 version 是 int。
 *
 * <p>范式复用 IntegrationManagerStartupReportTest：mock core/registry + 假集成直驱
 * {@code loadExistingConfigEntriesForCoordinate}；fixture entry 一律显式 version。
 * 全部同步直驱，无 sleep/真实等待。
 *
 * @author coffee
 */
public class IntegrationManagerMergeEntriesTest {

    private static final String COORD = "com.ecat:gate";
    private static final String COORD_OK = "com.ecat:gate-ok";

    private AutoCloseable mocks;
    private EcatCore core;
    private IntegrationRegistry integrationRegistry;
    private StateManager stateManager;
    private ConfigEntryRegistry entryRegistry;

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
    }

    // ==================== 假集成（探测 3.3 可选钩子语义） ====================

    /** 不覆写 mergeEntries：默认 null 必须是合法“无变更”结果。 */
    private static class DefaultHookIntegration extends IntegrationBase {
        int createEntryCalls;

        @Override public void onInit() { }
        @Override public void onStart() { }
        @Override public void onPause() { }
        @Override public ConfigEntry createEntry(ConfigEntry entry) {
            createEntryCalls++;
            return entry;
        }
    }

    /** 覆写 mergeEntries：记录调用并原样返回（3.3 语义中这是可选钩子，不承担格式门控）。 */
    private static class RecordingHookIntegration extends DefaultHookIntegration {
        final List<ConfigEntry> mergeInput = new ArrayList<>();
        final List<ConfigEntry> mergeOutput = new ArrayList<>();
        ConfigEntry createInput;

        @Override public List<ConfigEntry> mergeEntries(List<ConfigEntry> entries) {
            mergeInput.addAll(entries);
            ConfigEntry source = entries.get(0);
            mergeOutput.add(entry(source.getCoordinate(), "hooked", source.getVersion()));
            return mergeOutput;
        }

        @Override public ConfigEntry createEntry(ConfigEntry entry) {
            createInput = entry;
            return entry;
        }
    }

    @Test
    public void defaultMergeEntriesNull_isAcceptedWithoutVersionGate() throws Exception {
        IntegrationManager manager = newManager();
        DefaultHookIntegration integration = new DefaultHookIntegration();
        Mockito.when(integrationRegistry.getIntegration(COORD)).thenReturn(integration);
        Mockito.when(entryRegistry.listByCoordinate(COORD))
                .thenReturn(Arrays.asList(entry("e1", 17)));

        restoreEntries(manager, COORD);

        assertEquals("默认 mergeEntries 返回 null 后应继续恢复 enabled entry", 1,
                integration.createEntryCalls);
    }

    @Test
    public void overrideMergeEntries_isOptionalHook_calledForAnyIntVersion() throws Exception {
        IntegrationManager manager = newManager();
        RecordingHookIntegration integration = new RecordingHookIntegration();
        Mockito.when(integrationRegistry.getIntegration(COORD)).thenReturn(integration);
        Mockito.when(entryRegistry.listByCoordinate(COORD))
                .thenReturn(Arrays.asList(entry("original", 1)));

        restoreEntries(manager, COORD);

        assertEquals("可选钩子应被调用", 1, integration.mergeInput.size());
        assertEquals("钩子入参保留旧修改计数", 1, integration.mergeInput.get(0).getVersion());
        assertNotNull("钩子返回结果应传给 createEntry", integration.createInput);
        assertEquals("hooked", integration.createInput.getEntryId());
    }

    @Test
    public void runtimeEnablePath_usesSameOptionalHook() throws Exception {
        IntegrationManager manager = newManager();
        RecordingHookIntegration integration = new RecordingHookIntegration();
        Mockito.when(integrationRegistry.getIntegration(COORD_OK)).thenReturn(integration);
        Mockito.when(entryRegistry.listByCoordinate(COORD_OK))
                .thenReturn(Arrays.asList(entry(COORD_OK, "original", 17)));

        restoreEntries(manager, COORD_OK);

        assertEquals("运行时 enable 路同调可选钩子", 1, integration.mergeInput.size());
        assertEquals("hooked", integration.createInput.getEntryId());
    }

    // ==================== 辅助 ====================

    private IntegrationManager newManager() {
        return new IntegrationManager(core, integrationRegistry, stateManager);
    }

    /** 两入口均为包内可见方法；运行时入口 tracker=null。 */
    private static void restoreEntries(IntegrationManager manager, String coordinate)
            throws Exception {
        Method method = IntegrationManager.class.getDeclaredMethod(
                "loadExistingConfigEntriesForCoordinate", String.class, StartupLoadTracker.class);
        method.setAccessible(true);
        method.invoke(manager, coordinate, (StartupLoadTracker) null);
    }

    private static ConfigEntry entry(String id, int version) {
        return entry(COORD, id, version);
    }

    private static ConfigEntry entry(String coordinate, String id, int version) {
        return new ConfigEntry.Builder()
                .entryId(id)
                .coordinate(coordinate)
                .uniqueId("unique-" + id)
                .title("Entry " + id)
                .enabled(true)
                .version(version)
                .build();
    }
}
