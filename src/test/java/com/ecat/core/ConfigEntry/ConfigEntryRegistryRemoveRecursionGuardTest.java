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

package com.ecat.core.ConfigEntry;

import com.ecat.core.EcatCore;
import com.ecat.core.Integration.IntegrationBase;
import com.ecat.core.Integration.IntegrationRegistry;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.*;

/**
 * ConfigEntryRegistry.removeEntry 互递归护栏回归测试（bug-record-20260907-195458）。
 * <p>
 * 风险：Registry.removeEntry → notifyIntegrationRemove → integration.removeEntry →
 * IntegrationBase 默认实现回查 registry.removeEntry → 无界互递归 StackOverflowError（Error
 * 穿透 notify 的 catch(Exception) 与 HTTP handler → DELETE 请求永久挂死）。三个用例分别锁：
 * ① 默认实现不再回查 Registry；② 子类手滑调 super 也不递归（Registry 重入护栏封顶）；
 * ③ 回调抛 Error 可见且删除中止（转 EntryNotificationException，entry 保留）。
 */
public class ConfigEntryRegistryRemoveRecursionGuardTest {

    private static final String COORDINATE = "com.ecat:integration-guard-test";
    private static final AtomicLong UID_SEQ = new AtomicLong();

    private ConfigEntryRegistry registry;
    private RecordingPersistence persistence;
    private EcatCore coreMock;
    private IntegrationRegistry integrationRegistryMock;

    @Before
    public void setUp() {
        persistence = new RecordingPersistence();
        coreMock = Mockito.mock(EcatCore.class);
        integrationRegistryMock = Mockito.mock(IntegrationRegistry.class);
        registry = new ConfigEntryRegistry(coreMock, persistence);

        // 复刻生产链路：IntegrationBase.getEntryRegistry() = core.getEntryRegistry()——
        // 不 stub 这条，默认实现的回查拿不到 registry，递归复现不出来
        Mockito.when(coreMock.getEntryRegistry()).thenReturn(registry);
        Mockito.when(coreMock.getIntegrationRegistry()).thenReturn(integrationRegistryMock);
    }

    /** 建 entry 并把 fake 集成挂到 mock IntegrationRegistry（entry 坐标指向它）。 */
    private String createEntryHeldBy(IntegrationBase integration) {
        Mockito.when(integrationRegistryMock.getIntegration(COORDINATE)).thenReturn(integration);
        ConfigEntry created = registry.createEntry(new ConfigEntry.Builder()
                .coordinate(COORDINATE)
                .uniqueId("uid-" + UID_SEQ.incrementAndGet())
                .title("recursion guard fixture")
                .build());
        return created.getEntryId();
    }

    /**
     * 用例1：直接继承 IntegrationBase、不覆写 removeEntry 的集成（mqtt 同型受害者画像），
     * registry.removeEntry 必须正常完成且 entry 真删（缓存 + 持久化）。
     * 现状：默认实现回查 Registry → 互递归 StackOverflowError。
     */
    @Test
    public void removeEntry_defaultImplWithoutOverride_completesAndDeletesEntry() {
        BareIntegration integration = new BareIntegration();
        integration.wire(coreMock);
        String entryId = createEntryHeldBy(integration);

        registry.removeEntry(entryId);

        assertNull("entry 应从缓存删除", registry.getByEntryId(entryId));
        assertTrue("persistence.delete 应被调用", persistence.deletedIds.contains(entryId));
    }

    /**
     * 用例2：子类覆写了 removeEntry 但体内先调 super.removeEntry(entryId)（手滑写法画像），
     * registry.removeEntry 必须正常完成且 entry 真删。防御来自 Registry 重入护栏：
     * 回调再进本方法时外层删除已在进行，幂等返回，递归深度封顶 1。
     * 现状：super 回查 Registry → 互递归 StackOverflowError。
     */
    @Test
    public void removeEntry_overrideCallingSuper_completesAndDeletesEntry() {
        SuperCallingIntegration integration = new SuperCallingIntegration();
        integration.wire(coreMock);
        String entryId = createEntryHeldBy(integration);

        registry.removeEntry(entryId);

        assertNull("entry 应从缓存删除", registry.getByEntryId(entryId));
        assertTrue("persistence.delete 应被调用", persistence.deletedIds.contains(entryId));
    }

    /**
     * 用例3：集成 removeEntry 回调抛 Error（SOE/OOM 同型）不得穿透 Registry 静默逃逸：
     * 须转 EntryNotificationException 上抛（调用方拿到明确失败）且删除中止（entry 保留），
     * 否则半删状态（通知失败但缓存/持久化已删）与无声假挂都不可接受。
     * 现状：notify 的 catch 链只接 Exception，AssertionError 直接逃逸。
     */
    @Test
    public void removeEntry_callbackThrowsError_abortsRemovalAndWrapsAsEntryNotificationException() {
        ErrorThrowingIntegration integration = new ErrorThrowingIntegration();
        integration.wire(coreMock);
        String entryId = createEntryHeldBy(integration);

        try {
            registry.removeEntry(entryId);
            fail("回调抛 Error 应转 EntryNotificationException 上抛，而非正常返回或 Error 逃逸");
        } catch (ConfigEntryRegistry.EntryNotificationException e) {
            assertTrue("cause 应保留原始 Error 全栈", e.getCause() instanceof AssertionError);
            assertEquals("boom", e.getCause().getMessage());
        }
        assertNotNull("删除中止：entry 应保留在缓存", registry.getByEntryId(entryId));
        assertFalse("删除中止：persistence 不应被删", persistence.deletedIds.contains(entryId));
    }

    // ==================== fixture（沿用邻测 ConfigEntryRegistryTest 风格）====================

    /** 记录 delete 调用的空转 persistence（邻测匿名内联同款，加 delete 记录取证）。 */
    private static class RecordingPersistence implements ConfigEntryPersistence {
        final List<String> deletedIds = new ArrayList<>();

        @Override
        public List<ConfigEntry> loadAll() {
            return new ArrayList<>();
        }

        @Override
        public void save(ConfigEntry entry) {
        }

        @Override
        public void update(ConfigEntry entry) {
        }

        @Override
        public void delete(String entryId) {
            deletedIds.add(entryId);
        }
    }

    /**
     * 持有 coreMock 的假集成底座：生产集成经 onLoad 注入 core（默认 removeEntry 的回查经
     * core.getEntryRegistry()），单测绕开重型 onLoad 用 wire 直塞。createEntry 透传同邻测
     * CapturingIntegration（避免建 entry 时默认实现抛 UnsupportedOperationException 噪音）。
     */
    private static class StubIntegration extends IntegrationBase {
        void wire(EcatCore core) {
            this.core = core;
        }

        @Override
        public void onInit() {
        }

        @Override
        public void onStart() {
        }

        @Override
        public void onPause() {
        }

        @Override
        public ConfigEntry createEntry(ConfigEntry entry) {
            return entry;
        }
    }

    /** 不覆写 removeEntry——吃 IntegrationBase 默认实现（mqtt 画像）。 */
    private static class BareIntegration extends StubIntegration {
    }

    /** 覆写 removeEntry 但先调 super（手滑写法画像）。 */
    private static class SuperCallingIntegration extends StubIntegration {
        @Override
        public void removeEntry(String entryId) {
            super.removeEntry(entryId);
            // 自家清理点（本测试无 entry 级资源，留空）
        }
    }

    /** 回调抛 Error 画像。 */
    private static class ErrorThrowingIntegration extends StubIntegration {
        @Override
        public void removeEntry(String entryId) {
            throw new AssertionError("boom");
        }
    }
}
