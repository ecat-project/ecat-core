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

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.ecat.core.ConfigEntry.ConfigEntry;
import com.ecat.core.ConfigEntry.ConfigEntryRegistry;
import com.ecat.core.EcatCore;
import com.ecat.core.State.StateManager;
import com.ecat.core.Utils.Mdc.MdcContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Mockito.when;

/**
 * 装载失败点 entry 身份 scope 回归锁（223801 E3 修复，Option A）：
 * {@code loadExistingConfigEntriesForCoordinate} 的逐 entry 循环包 {@code LogScope.entry}——
 * 同错误点（同类异常、同消息模板）死掉的多个 entry，各自的「Failed to load entry」日志事件
 * MDC 必须携带自己的 entry.id（错误限频签名据此分窗，跨受害者互吞明细的机制在此断根；
 * 该点位是 core logger，ClassLoaderCoordinateFilter 不写 coordinate，身份只能来自 scope）。
 *
 * <p>范式复用 IntegrationManagerMergeEntriesTest：mock core/registry + 假集成反射直驱，
 * 全程同步无等待。日志事件经 ListAppender 挂单例 context 的 IntegrationManager logger 捕获。
 */
public class IntegrationManagerLoadEntryScopeTest {

    private static final String COORD = "com.ecat:scope-gate";

    private AutoCloseable mocks;
    private EcatCore core;
    private IntegrationRegistry integrationRegistry;
    private StateManager stateManager;
    private ConfigEntryRegistry entryRegistry;
    private ListAppender<ILoggingEvent> appender;
    private ch.qos.logback.classic.Logger imLogger;

    @Before
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        core = Mockito.mock(EcatCore.class);
        integrationRegistry = Mockito.mock(IntegrationRegistry.class);
        stateManager = Mockito.mock(StateManager.class);
        entryRegistry = Mockito.mock(ConfigEntryRegistry.class);
        when(core.getEntryRegistry()).thenReturn(entryRegistry);

        appender = new ListAppender<>();
        appender.start();
        imLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(IntegrationManager.class);
        imLogger.addAppender(appender);
    }

    @After
    public void tearDown() throws Exception {
        imLogger.detachAppender(appender);
        MDC.clear();
        mocks.close();
    }

    /** 同错误点双 entry 死亡（E3 形态）：createEntry 恒抛同类异常，记录调用时 MDC 的 entry.id。 */
    private static class FailingCreateIntegration extends IntegrationBase {
        final List<String> mdcEntryIdsAtCreate = new ArrayList<>();

        @Override public void onInit() { }
        @Override public void onStart() { }
        @Override public void onPause() { }
        @Override public ConfigEntry createEntry(ConfigEntry entry) {
            mdcEntryIdsAtCreate.add(MDC.get(MdcContext.ENTRY_ID_KEY));
            throw new IllegalStateException("boom: " + entry.getEntryId());
        }
    }

    @Test
    public void perEntryLoadFailuresEachCarryOwnEntryIdentity() throws Exception {
        IntegrationManager manager = new IntegrationManager(core, integrationRegistry, stateManager);
        FailingCreateIntegration integration = new FailingCreateIntegration();
        when(integrationRegistry.getIntegration(COORD)).thenReturn(integration);
        when(entryRegistry.listByCoordinate(COORD))
                .thenReturn(Arrays.asList(entry("entry-a", 1), entry("entry-b", 1)));

        restoreEntries(manager, COORD);

        assertEquals("createEntry 执行时 MDC 必须是当前 entry 的 id（scope 包住回调）",
                Arrays.asList("entry-a", "entry-b"), integration.mdcEntryIdsAtCreate);

        List<ILoggingEvent> failures = new ArrayList<>();
        for (ILoggingEvent event : appender.list) {
            if (event.getFormattedMessage().contains("Failed to load entry")) {
                failures.add(event);
            }
        }
        assertEquals("两个 entry 的失败行都要落（per-entry 容错语义不变）", 2, failures.size());
        assertEquals("失败行(a)的 MDC 须带自己的 entry.id（限频签名的身份来源）",
                "entry-a", failures.get(0).getMDCPropertyMap().get(MdcContext.ENTRY_ID_KEY));
        assertEquals("失败行(b)的 MDC 须带自己的 entry.id",
                "entry-b", failures.get(1).getMDCPropertyMap().get(MdcContext.ENTRY_ID_KEY));

        assertNull("装载结束 scope 必须关闭——复用线程不得泄漏 entry 身份",
                MDC.get(MdcContext.ENTRY_ID_KEY));
    }

    // ==================== 辅助 ====================

    private static void restoreEntries(IntegrationManager manager, String coordinate)
            throws Exception {
        Method method = IntegrationManager.class.getDeclaredMethod(
                "loadExistingConfigEntriesForCoordinate", String.class, StartupLoadTracker.class);
        method.setAccessible(true);
        method.invoke(manager, coordinate, (StartupLoadTracker) null);
    }

    private static ConfigEntry entry(String id, int version) {
        return new ConfigEntry.Builder()
                .entryId(id)
                .coordinate(COORD)
                .uniqueId("unique-" + id)
                .title("Entry " + id)
                .enabled(true)
                .version(version)
                .build();
    }
}
