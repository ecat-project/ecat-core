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

package com.ecat.core.ConfigFlow;

import com.ecat.core.Bus.BusRegistry;
import com.ecat.core.Bus.BusTopic;
import com.ecat.core.Bus.NotificationAction;
import com.ecat.core.Bus.NotificationEvent;
import com.ecat.core.ConfigEntry.ConfigEntry;
import com.ecat.core.ConfigEntry.ConfigEntryRegistry;
import com.ecat.core.ConfigEntry.SourceType;
import com.ecat.core.EcatCore;
import com.ecat.core.Integration.IntegrationBase;
import com.ecat.core.Integration.IntegrationManager;
import com.ecat.core.Integration.IntegrationRegistry;
import com.ecat.core.Task.TaskManager;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * ConfigFlowService 单元测试（flow 能力下沉 core 后的规范测试位置）
 *
 * <p>测试 Provider 发现、流程管理和过期清理功能。
 * Flow 实例管理完全委托给 ConfigFlowRegistry。
 * <p>本测试随 ConfigFlowService 从 ecat-core-api 迁入 ecat-core（2026-06-22）。
 *
 * @author coffee
 */
public class ConfigFlowServiceTest {

    @Mock
    private EcatCore mockCore;

    @Mock
    private IntegrationRegistry mockRegistry;

    @Mock
    private IntegrationManager mockIntegrationManager;

    @Mock
    private ConfigFlowRegistry mockFlowRegistry;

    @Mock
    private TaskManager mockTaskManager;

    @Mock
    private ScheduledExecutorService mockScheduler;

    private ConfigFlowService service;

    private TestConfigFlowProvider testProvider;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);

        // 设置 mockCore 的默认行为
        when(mockCore.getIntegrationRegistry()).thenReturn(mockRegistry);
        when(mockCore.getIntegrationManager()).thenReturn(mockIntegrationManager);
        when(mockCore.getFlowRegistry()).thenReturn(mockFlowRegistry);

        // TaskManager 调度链（P3 周期清理：构造器 scheduleAtFixedRate；mock scheduler 不启真实线程、不泄漏）
        when(mockCore.getTaskManager()).thenReturn(mockTaskManager);
        when(mockTaskManager.getMdcScheduledExecutorService()).thenReturn(mockScheduler);

        // ConfigFlowRegistry — 默认 doNothing for mutation methods
        doNothing().when(mockFlowRegistry).registerActiveFlow(anyString(), any(AbstractConfigFlow.class));
        doNothing().when(mockFlowRegistry).registerIfAbsent(anyString(), any(AbstractConfigFlow.class));
        doNothing().when(mockFlowRegistry).finishActiveFlow(anyString());
        doNothing().when(mockFlowRegistry).abortActiveFlow(anyString());

        // cleanupExpiredFlows / abortAllActiveFlows 默认返回 0 / doNothing
        when(mockFlowRegistry.cleanupExpiredFlows(anyLong())).thenReturn(0);
        doNothing().when(mockFlowRegistry).abortAllActiveFlows();
        // pending 容量检查用：默认无待处理 flow（各测试可覆盖）
        when(mockFlowRegistry.getActiveFlowSnapshots()).thenReturn(new ArrayList<>());

        // 创建实际的测试 Provider
        testProvider = new TestConfigFlowProvider();

        // 创建 service 实例
        service = new ConfigFlowService(mockCore);
    }

    // ========== startDiscoveryFlow() 测试（IMPORT_FLOW/ZEROCONF/MQTT；initImportFlow 已并入）==========

    @Test
    public void testStartDiscoveryFlow_ImportFlow_Success() {
        String coordinate = "com.ecat.integration:import-provider";
        // 注册了 IMPORT_FLOW 的真实 flow
        final AbstractConfigFlow flow = new AbstractConfigFlow() {
            {
                registerStepDiscovery(SourceType.IMPORT_FLOW,
                        (payload, ctx) -> {
                            ctx.setEntryData("imported", "yes");
                            return ConfigFlowResult.showForm("comm_config", new ConfigSchema(), new HashMap<>(), ctx);
                        });
            }
        };

        ConfigEntryRegistry mockEntryRegistry = mock(ConfigEntryRegistry.class);
        when(mockCore.getEntryRegistry()).thenReturn(mockEntryRegistry);
        when(mockFlowRegistry.hasActiveFlowWithDiscoveryPayload(eq(coordinate), eq(SourceType.IMPORT_FLOW), any()))
                .thenReturn(false);
        when(mockFlowRegistry.createFlow(coordinate)).thenReturn(flow);

        ConfigFlowService.ConfigFlowInstance instance =
                service.startDiscoveryFlow(coordinate, SourceType.IMPORT_FLOW,
                        new ImportFlowPayload(coordinate, 1, "so2|SN001|name"));

        assertNotNull("instance 不应为 null", instance);
        assertEquals("应 SHOW_FORM", ConfigFlowResult.ResultType.SHOW_FORM, instance.getResult().getType());
        assertEquals("应落地 comm_config", "comm_config", instance.getStepId());
        assertEquals("sourceType 应为 IMPORT_FLOW", SourceType.IMPORT_FLOW, flow.getSourceType());
        assertEquals("handler 应已预填 entryData", "yes", flow.getContext().getEntryData("imported"));
        verify(mockFlowRegistry).registerIfAbsent(eq(flow.getFlowId()), eq(flow));
    }

    @Test
    public void testStartDiscoveryFlow_NotReady_FlowRegistryNull() {
        when(mockCore.getFlowRegistry()).thenReturn(null);  // 覆盖 setUp 默认：core 未就绪
        try {
            service.startDiscoveryFlow("com.ecat:x", SourceType.IMPORT_FLOW,
                        new ImportFlowPayload("com.ecat:x", 1, "data"));
            fail("core 未就绪应抛 ConfigFlowException");
        } catch (ConfigFlowException e) {
            assertTrue("异常消息应提示 core 未就绪", e.getMessage().contains("core 未就绪"));
        }
    }

    @Test
    public void testStartDiscoveryFlow_NoHandler() {
        String coordinate = "com.ecat.integration:no-import";
        final AbstractConfigFlow flow = new AbstractConfigFlow() {
        };  // 不注册 IMPORT_FLOW
        ConfigEntryRegistry mockEntryRegistry = mock(ConfigEntryRegistry.class);
        when(mockCore.getEntryRegistry()).thenReturn(mockEntryRegistry);
        when(mockFlowRegistry.hasActiveFlowWithDiscoveryPayload(any(), any(), any())).thenReturn(false);
        when(mockFlowRegistry.createFlow(coordinate)).thenReturn(flow);

        try {
            service.startDiscoveryFlow(coordinate, SourceType.IMPORT_FLOW,
                        new ImportFlowPayload(coordinate, 1, "data"));
            fail("集成未注册 IMPORT_FLOW 应抛 ConfigFlowException");
        } catch (ConfigFlowException e) {
            assertTrue("异常消息应提示未注册 IMPORT_FLOW", e.getMessage().contains("未注册 IMPORT_FLOW"));
        }
    }

    @Test
    public void testStartDiscoveryFlow_Zeroconf_Success() {
        String coordinate = "com.ecat.integration:zeroconf-device";
        // 注册了 ZEROCONF 的真实 flow（payload 当 Object，core 不透明）
        final AbstractConfigFlow flow = new AbstractConfigFlow() {
            {
                registerStepDiscovery(SourceType.ZEROCONF,
                        (payload, ctx) -> {
                            ctx.setEntryData("discovered", payload.toString());
                            return ConfigFlowResult.showForm("comm_config", new ConfigSchema(), new HashMap<>(), ctx);
                        });
            }
        };

        ConfigEntryRegistry mockEntryRegistry = mock(ConfigEntryRegistry.class);
        when(mockCore.getEntryRegistry()).thenReturn(mockEntryRegistry);
        when(mockFlowRegistry.hasActiveFlowWithDiscoveryPayload(eq(coordinate), eq(SourceType.ZEROCONF), any()))
                .thenReturn(false);
        when(mockFlowRegistry.createFlow(coordinate)).thenReturn(flow);

        Object payload = "zc-payload-sn001";  // 任意 opaque payload（有 equals 供 Layer2）
        ConfigFlowService.ConfigFlowInstance instance =
                service.startDiscoveryFlow(coordinate, SourceType.ZEROCONF, payload);

        assertNotNull("instance 不应为 null", instance);
        assertEquals("应 SHOW_FORM", ConfigFlowResult.ResultType.SHOW_FORM, instance.getResult().getType());
        assertEquals("sourceType 应为 ZEROCONF", SourceType.ZEROCONF, flow.getSourceType());
        assertEquals("handler 应已预填 entryData", "zc-payload-sn001", flow.getContext().getEntryData("discovered"));
        verify(mockFlowRegistry).registerIfAbsent(eq(flow.getFlowId()), eq(flow));
    }

    @Test
    public void testStartDiscoveryFlow_NotReady_Zeroconf_FlowRegistryNull() {
        when(mockCore.getFlowRegistry()).thenReturn(null);  // core 未就绪
        try {
            service.startDiscoveryFlow("com.ecat:x", SourceType.ZEROCONF, "payload");
            fail("core 未就绪应抛 ConfigFlowException");
        } catch (ConfigFlowException e) {
            assertTrue("异常消息应提示 core 未就绪", e.getMessage().contains("core 未就绪"));
        }
    }

    @Test
    public void testStartDiscoveryFlow_R12RenewsExistingFlow() {
        // R12 命中(同 coordinate+source+payload 已有活跃 flow)→ 续期现有 flow 并返回,不重建、不重发提示。
        // 语义:重复广播=设备仍在线→续期保留;设备消失→30min 无续期自动清除。
        String coordinate = "com.ecat.integration:zeroconf-device";
        ConfigEntryRegistry mockEntryRegistry = mock(ConfigEntryRegistry.class);
        BusRegistry mockBus = mock(BusRegistry.class);
        when(mockCore.getEntryRegistry()).thenReturn(mockEntryRegistry);
        when(mockCore.getBusRegistry()).thenReturn(mockBus);

        // 已存在的 discovery flow(findDiscoveryFlowId 命中它)
        final AbstractConfigFlow existingFlow = new AbstractConfigFlow() {{
            registerStepDiscovery(SourceType.ZEROCONF,
                    (payload, ctx) -> ConfigFlowResult.showForm("probe", new ConfigSchema(), new HashMap<>(), ctx));
        }};
        existingFlow.getContext().setCoordinate(coordinate);
        existingFlow.setSourceType(SourceType.ZEROCONF);
        String existingFlowId = existingFlow.getFlowId();
        when(mockFlowRegistry.findDiscoveryFlowId(eq(coordinate), eq(SourceType.ZEROCONF), any()))
                .thenReturn(existingFlowId);
        // getStatus 续期链路:getActiveFlow(touch 续期 30min)+ getStatus(重跑当前步拿 SHOW_FORM)
        when(mockFlowRegistry.getActiveFlow(existingFlowId)).thenReturn(existingFlow);
        when(mockFlowRegistry.getStatus(existingFlowId)).thenReturn(
                ConfigFlowResult.showForm("probe", new ConfigSchema(), new HashMap<>(), existingFlow.getContext()));

        ConfigFlowService.ConfigFlowInstance instance = service.startDiscoveryFlow(coordinate, SourceType.ZEROCONF, "dup-payload");

        assertEquals("R12 命中应返回现有 flowId(续期,非新建)", existingFlowId, instance.getFlowId());
        verify(mockFlowRegistry, never()).createFlow(any());               // 不建新 flow
        verify(mockFlowRegistry, times(1)).getActiveFlow(existingFlowId);   // getStatus 经 getActiveFlow touch 续期
        verify(mockBus, never()).publish(anyString(), any());               // R12 命中不重发 discovery 提示
    }

    @Test
    public void testStartDiscoveryFlow_PendingLimitRejects() {
        // pending 容量上限(100):满则拒绝新发现入列,不建 flow(防泛洪)。
        String coordinate = "com.ecat.integration:flood";
        ConfigEntryRegistry mockEntryRegistry = mock(ConfigEntryRegistry.class);
        when(mockCore.getEntryRegistry()).thenReturn(mockEntryRegistry);
        // 灌满 100 个 discovery pending 快照(同一 IMPORT_FLOW flow × 100,filter count=100)
        AbstractConfigFlow discFlow = new AbstractConfigFlow() {{
            registerStepDiscovery(SourceType.IMPORT_FLOW,
                    (payload, ctx) -> ConfigFlowResult.showForm("c", new ConfigSchema(), new HashMap<>(), ctx));
        }};
        discFlow.setSourceType(SourceType.IMPORT_FLOW);
        List<ConfigFlowRegistry.ActiveFlowSnapshot> full = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            full.add(new ConfigFlowRegistry.ActiveFlowSnapshot("flow-flood-" + i, discFlow, 1000L));
        }
        when(mockFlowRegistry.getActiveFlowSnapshots()).thenReturn(full);

        try {
            service.startDiscoveryFlow(coordinate, SourceType.IMPORT_FLOW,
                    new ImportFlowPayload(coordinate, 1, "m|s|n"));
            fail("pending 满(100)应抛 ConfigFlowException");
        } catch (ConfigFlowException e) {
            assertTrue("异常消息应提示容量上限", e.getMessage().contains("上限"));
        }
        verify(mockFlowRegistry, never()).createFlow(any());   // 满则拒绝,不建 flow
    }

    @Test
    public void testStartDiscoveryFlow_IllegalSource_User() {
        // USER 不是 payload-discovery 源 → 严格模式抛异常
        try {
            service.startDiscoveryFlow("com.ecat:x", SourceType.USER, "payload");
            fail("USER 非 payload-discovery 源应抛 ConfigFlowException");
        } catch (ConfigFlowException e) {
            assertTrue("异常消息应提示 payload-discovery 源", e.getMessage().contains("payload-discovery 源"));
        }
    }

    // ========== discoverProviders() 测试 ==========

    @Test
    public void testDiscoverProviders_Success() {
        List<String> coordinates = Arrays.asList("com.ecat.integration:test-provider");

        when(mockFlowRegistry.getCoordinatesWithUserStep()).thenReturn(coordinates);
        when(mockFlowRegistry.hasUserStep("com.ecat.integration:test-provider")).thenReturn(true);
        when(mockFlowRegistry.createFlow("com.ecat.integration:test-provider")).thenReturn(testProvider.createFlow());
        when(mockRegistry.getIntegration("com.ecat.integration:test-provider")).thenReturn(testProvider);

        Map<String, ConfigFlowProvider> providers = service.discoverProviders();

        assertNotNull("providers 不应为 null", providers);
        assertEquals("应发现 1 个 provider", 1, providers.size());
        assertTrue("应包含 test-provider", providers.containsKey("com.ecat.integration:test-provider"));

        verify(mockFlowRegistry).getCoordinatesWithUserStep();
    }

    @Test
    public void testDiscoverProviders_MultipleProviders() {
        List<String> coordinates = Arrays.asList(
            "com.ecat.integration:test-provider",
            "com.ecat.integration:provider2"
        );

        when(mockFlowRegistry.getCoordinatesWithUserStep()).thenReturn(coordinates);
        when(mockFlowRegistry.hasUserStep("com.ecat.integration:test-provider")).thenReturn(true);
        when(mockFlowRegistry.hasUserStep("com.ecat.integration:provider2")).thenReturn(true);
        when(mockFlowRegistry.createFlow("com.ecat.integration:test-provider")).thenReturn(testProvider.createFlow());
        when(mockFlowRegistry.createFlow("com.ecat.integration:provider2")).thenReturn(testProvider.createFlow());
        when(mockRegistry.getIntegration("com.ecat.integration:test-provider")).thenReturn(testProvider);

        Map<String, ConfigFlowProvider> providers = service.discoverProviders();

        assertEquals("应发现 2 个 provider", 2, providers.size());
    }

    @Test
    public void testDiscoverProviders_NoProviders() {
        when(mockFlowRegistry.getCoordinatesWithUserStep()).thenReturn(new ArrayList<>());

        Map<String, ConfigFlowProvider> providers = service.discoverProviders();

        assertNotNull("providers 不应为 null", providers);
        assertTrue("providers 应为空", providers.isEmpty());
    }

    @Test
    public void testDiscoverProviders_EmptyStatusList() {
        when(mockFlowRegistry.getCoordinatesWithUserStep()).thenReturn(new ArrayList<>());

        Map<String, ConfigFlowProvider> providers = service.discoverProviders();

        assertNotNull("providers 不应为 null", providers);
        assertTrue("providers 应为空", providers.isEmpty());
    }

    @Test
    public void testDiscoverProviders_ErrorGettingIntegration() {
        List<String> coordinates = Arrays.asList(
            "com.ecat.integration:test-provider",
            "com.ecat.integration:error-integration"
        );

        when(mockFlowRegistry.getCoordinatesWithUserStep()).thenReturn(coordinates);
        when(mockFlowRegistry.hasUserStep("com.ecat.integration:test-provider")).thenReturn(true);
        when(mockFlowRegistry.hasUserStep("com.ecat.integration:error-integration")).thenReturn(true);
        when(mockRegistry.getIntegration("com.ecat.integration:test-provider")).thenReturn(testProvider);
        when(mockRegistry.getIntegration("com.ecat.integration:error-integration")).thenThrow(new RuntimeException("Integration not found"));

        Map<String, ConfigFlowProvider> providers = service.discoverProviders();

        assertEquals("应发现 1 个 provider（error-integration 出错被跳过）", 1, providers.size());
        assertTrue("应包含 test-provider", providers.containsKey("com.ecat.integration:test-provider"));
    }

    // ========== listDiscoveryFlows() 测试（待处理发现 DTO 暴露）==========

    @Test
    public void testListDiscoveryFlows_FiltersPayloadDiscoverySourcesOnly() {
        // 待处理 IMPORT_FLOW flow（SHOW_FORM，sourceType=IMPORT_FLOW）
        final AbstractConfigFlow discFlow = new AbstractConfigFlow() {
            {
                registerStepDiscovery(SourceType.IMPORT_FLOW,
                        (payload, ctx) -> ConfigFlowResult.showForm("confirm", new ConfigSchema(), new HashMap<>(), ctx));
            }
        };
        discFlow.getContext().setCoordinate("com.ecat.integration:disc");
        discFlow.setSourceType(SourceType.IMPORT_FLOW);
        discFlow.getContext().setEntryUniqueId("uid-disc-001");
        discFlow.getContext().setEntryData("name", "发现设备A");

        // USER flow（非 discovery 源，应被过滤）
        final AbstractConfigFlow userFlow = testProvider.createFlow();
        userFlow.getContext().setCoordinate("com.ecat.integration:user");

        when(mockFlowRegistry.getActiveFlowSnapshots()).thenReturn(Arrays.asList(
                new ConfigFlowRegistry.ActiveFlowSnapshot("flow-disc", discFlow, 1000L),
                new ConfigFlowRegistry.ActiveFlowSnapshot("flow-user", userFlow, 2000L)));

        List<DiscoveryFlowInfo> list = service.listDiscoveryFlows();

        assertEquals("仅 1 个 discovery flow（USER 被过滤）", 1, list.size());
        DiscoveryFlowInfo info = list.get(0);
        assertEquals("flowId", "flow-disc", info.getFlowId());
        assertEquals("source", "IMPORT_FLOW", info.getSource());
        assertEquals("coordinate", "com.ecat.integration:disc", info.getCoordinate());
        assertEquals("uniqueId", "uid-disc-001", info.getUniqueId());
        assertEquals("title 取 entryData.name", "发现设备A", info.getTitle());
    }

    @Test
    public void testListDiscoveryFlows_EmptyWhenNoActiveFlow() {
        when(mockFlowRegistry.getActiveFlowSnapshots()).thenReturn(new ArrayList<>());
        assertTrue("无活跃 flow 返回空列表", service.listDiscoveryFlows().isEmpty());
    }

    @Test
    public void testListDiscoveryFlows_EmptyWhenRegistryNull() {
        when(mockCore.getFlowRegistry()).thenReturn(null);
        // 重新构造 service 让 null 生效
        ConfigFlowService svc = new ConfigFlowService(mockCore);
        assertTrue("flowRegistry 为 null 返回空列表（不抛异常）", svc.listDiscoveryFlows().isEmpty());
    }

    // ========== notifyDiscoveryIfPending() 测试（落 SHOW_FORM 发强类型 action 通知）==========

    @Test
    public void testStartDiscoveryFlow_PublishesActionableNotificationOnShowForm() {
        String coordinate = "com.ecat.integration:notify-provider";
        final AbstractConfigFlow flow = new AbstractConfigFlow() {
            {
                registerStepDiscovery(SourceType.IMPORT_FLOW,
                        (payload, ctx) -> {
                            ctx.setEntryData("name", "新发现设备");
                            ctx.setEntryUniqueId("uid-notify-001");
                            return ConfigFlowResult.showForm("confirm", new ConfigSchema(), new HashMap<>(), ctx);
                        });
            }
        };

        ConfigEntryRegistry mockEntryRegistry = mock(ConfigEntryRegistry.class);
        BusRegistry mockBus = mock(BusRegistry.class);
        when(mockCore.getEntryRegistry()).thenReturn(mockEntryRegistry);
        when(mockCore.getBusRegistry()).thenReturn(mockBus);
        when(mockFlowRegistry.hasActiveFlowWithDiscoveryPayload(eq(coordinate), eq(SourceType.IMPORT_FLOW), any()))
                .thenReturn(false);
        when(mockFlowRegistry.createFlow(coordinate)).thenReturn(flow);

        service.startDiscoveryFlow(coordinate, SourceType.IMPORT_FLOW,
                new ImportFlowPayload(coordinate, 1, "model|SN|新发现设备"));

        // 捕获 publish 的 NotificationEvent，断言其携带强类型 DiscoveryNotificationAction（非 Map）
        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(mockBus).publish(eq(BusTopic.NOTIFICATION.getTopicName()), captor.capture());
        Object published = captor.getValue();
        assertTrue("应发布 NotificationEvent", published instanceof NotificationEvent);
        NotificationEvent evt = (NotificationEvent) published;
        NotificationAction action = evt.getAction();
        assertNotNull("应携带 action", action);
        assertTrue("action 应为强类型 DiscoveryNotificationAction", action instanceof DiscoveryNotificationAction);
        DiscoveryNotificationAction da = (DiscoveryNotificationAction) action;
        assertEquals("action.category", "discovery", da.getCategory());
        assertEquals("action.flowId", flow.getFlowId(), da.getFlowId());
        assertEquals("action.source", "IMPORT_FLOW", da.getSource());
        assertEquals("action.coordinate", coordinate, da.getCoordinate());
        assertEquals("action.uniqueId", "uid-notify-001", da.getUniqueId());
        assertEquals("action.title", "新发现设备", da.getTitle());
    }

    @Test
    public void testStartDiscoveryFlow_NoNotificationWhenBusNotReady() {
        String coordinate = "com.ecat.integration:no-bus-provider";
        final AbstractConfigFlow flow = new AbstractConfigFlow() {
            {
                registerStepDiscovery(SourceType.IMPORT_FLOW,
                        (payload, ctx) -> ConfigFlowResult.showForm("confirm", new ConfigSchema(), new HashMap<>(), ctx));
            }
        };
        ConfigEntryRegistry mockEntryRegistry = mock(ConfigEntryRegistry.class);
        when(mockCore.getEntryRegistry()).thenReturn(mockEntryRegistry);
        when(mockCore.getBusRegistry()).thenReturn(null);   // bus 未就绪
        when(mockFlowRegistry.hasActiveFlowWithDiscoveryPayload(eq(coordinate), eq(SourceType.IMPORT_FLOW), any()))
                .thenReturn(false);
        when(mockFlowRegistry.createFlow(coordinate)).thenReturn(flow);

        service.startDiscoveryFlow(coordinate, SourceType.IMPORT_FLOW,
                new ImportFlowPayload(coordinate, 1, "m|s|n"));
        // bus 为 null 时不发通知、不抛异常（flow 本身正常 SHOW_FORM）
        // —— 这里无法 verify(null) ，仅断言流程未因 bus null 而抛异常（走到此行即通过）
    }

    // ========== startFlow() 测试 ==========

    @Test
    public void testStartFlow_Success() {
        when(mockFlowRegistry.hasUserStep("com.ecat.integration:test-provider")).thenReturn(true);
        when(mockFlowRegistry.createFlow("com.ecat.integration:test-provider")).thenReturn(testProvider.createFlow());

        ConfigFlowService.ConfigFlowInstance instance = service.startFlow("com.ecat.integration:test-provider");

        assertNotNull("instance 不应为 null", instance);
        assertNotNull("flowId 不应为 null", instance.getFlowId());
        assertEquals("stepId 应为 user", "user", instance.getStepId());
        assertNotNull("result 不应为 null", instance.getResult());
        assertNotNull("flow 不应为 null", instance.getFlow());
    }

    @Test
    public void testStartFlow_IntegrationNotFound() {
        when(mockFlowRegistry.hasUserStep("com.ecat.integration:non-existent")).thenReturn(false);

        try {
            service.startFlow("com.ecat.integration:non-existent");
            fail("应抛出 ConfigFlowException");
        } catch (ConfigFlowException e) {
            assertTrue("异常消息应包含 does not provide config flow",
                e.getMessage().contains("does not provide config flow"));
        }
    }

    @Test
    public void testStartFlow_NotConfigFlowProvider() {
        when(mockFlowRegistry.hasUserStep("com.ecat.integration:non-provider")).thenReturn(false);

        try {
            service.startFlow("com.ecat.integration:non-provider");
            fail("应抛出 ConfigFlowException");
        } catch (ConfigFlowException e) {
            assertTrue("异常消息应包含 does not provide config flow",
                e.getMessage().contains("does not provide config flow"));
        }
    }

    // ========== submitStep() 测试 ==========

    @SuppressWarnings("unchecked")
    @Test
    public void testSubmitStep_Success() {
        // 先启动流程
        when(mockFlowRegistry.hasUserStep(anyString())).thenReturn(true);
        when(mockFlowRegistry.createFlow(anyString())).thenReturn(testProvider.createFlow());
        ConfigFlowService.ConfigFlowInstance startInstance = service.startFlow("com.ecat.integration:test-provider");
        String flowId = startInstance.getFlowId();
        AbstractConfigFlow flow = startInstance.getFlow();

        // Mock 便捷方法 submitStep：返回 SHOW_FORM
        ConfigFlowResult mockResult = ConfigFlowResult.showForm("step2", null, new HashMap<>(), flow.getContext());
        when(mockFlowRegistry.submitStep(eq(flowId), eq("user"), any(Map.class))).thenReturn(mockResult);
        // flow 仍然在 registry 中
        when(mockFlowRegistry.getActiveFlow(flowId)).thenReturn(flow);

        Map<String, Object> userInput = new HashMap<>();
        userInput.put("username", "testuser");

        ConfigFlowService.ConfigFlowInstance instance = service.submitStep(flowId, "user", userInput);

        assertNotNull("instance 不应为 null", instance);
        assertEquals("flowId 应一致", flowId, instance.getFlowId());
    }

    @SuppressWarnings("unchecked")
    @Test
    public void testSubmitStep_FlowNotFound() {
        when(mockFlowRegistry.submitStep(eq("non-existent-flow"), anyString(), any(Map.class)))
            .thenThrow(new IllegalArgumentException("Flow not found: non-existent-flow"));

        try {
            service.submitStep("non-existent-flow", "step1", new HashMap<>());
            fail("应抛出 FlowNotFoundException");
        } catch (FlowNotFoundException e) {
            assertTrue("异常消息应包含 Flow not found",
                e.getMessage().contains("Flow not found"));
        }
    }

    @Test
    public void testSubmitStep_FlowCompleted() {
        when(mockFlowRegistry.hasUserStep(anyString())).thenReturn(true);
        when(mockFlowRegistry.createFlow(anyString())).thenReturn(testProvider.createFlow());
        ConfigFlowService.ConfigFlowInstance startInstance = service.startFlow("com.ecat.integration:test-provider");
        String flowId = startInstance.getFlowId();
        AbstractConfigFlow flow = startInstance.getFlow();

        // submitStep 收口到 drive（跑真实 handler）；预填 context 供 createEntry 建条目
        FlowContext ctx = flow.getContext();
        ctx.setEntryUniqueId("test-id");
        ctx.setEntryTitle("Test");
        // getActiveFlow 在 submitStep 中获取 flow 引用（在 finish 之前），返回 flow
        when(mockFlowRegistry.getActiveFlow(flowId)).thenReturn(flow);

        Map<String, Object> userInput = new HashMap<>();
        userInput.put("action", "complete");

        ConfigFlowService.ConfigFlowInstance instance = service.submitStep(flowId, "user", userInput);

        assertNotNull("instance 不应为 null", instance);
        assertEquals("result type 应为 CREATE_ENTRY", ConfigFlowResult.ResultType.CREATE_ENTRY, instance.getResult().getType());

        verify(mockFlowRegistry).finishActiveFlow(flowId);
    }

    // ========== goPrevious() 测试 ==========

    @Test
    public void testGoPrevious_Success() {
        when(mockFlowRegistry.hasUserStep(anyString())).thenReturn(true);
        when(mockFlowRegistry.createFlow(anyString())).thenReturn(testProvider.createFlow());
        ConfigFlowService.ConfigFlowInstance startInstance = service.startFlow("com.ecat.integration:test-provider");
        String flowId = startInstance.getFlowId();
        // Mock goPrevious 便捷方法：第一步返回异常
        when(mockFlowRegistry.goPrevious(flowId)).thenThrow(new IllegalStateException("已经是第一步"));

        try {
            service.goPrevious(flowId);
            fail("第一步不能返回上一步");
        } catch (IllegalStateException e) {
            assertTrue("异常消息应包含 '第一步'", e.getMessage().contains("第一步"));
        }
    }

    @Test
    public void testGoPrevious_FlowNotFound() {
        when(mockFlowRegistry.goPrevious("non-existent-flow"))
            .thenThrow(new IllegalArgumentException("Flow not found: non-existent-flow"));

        try {
            service.goPrevious("non-existent-flow");
            fail("应抛出 FlowNotFoundException");
        } catch (FlowNotFoundException e) {
            assertTrue("异常消息应包含 Flow not found",
                e.getMessage().contains("Flow not found"));
        }
    }

    // ========== getStatus() 测试 ==========

    @Test
    public void testGetStatus_Success() {
        when(mockFlowRegistry.hasUserStep(anyString())).thenReturn(true);
        when(mockFlowRegistry.createFlow(anyString())).thenReturn(testProvider.createFlow());
        ConfigFlowService.ConfigFlowInstance startInstance = service.startFlow("com.ecat.integration:test-provider");
        String flowId = startInstance.getFlowId();
        AbstractConfigFlow flow = startInstance.getFlow();

        // Mock getStatus 便捷方法
        ConfigFlowResult mockResult = ConfigFlowResult.showForm("user", null, new HashMap<>(), flow.getContext());
        when(mockFlowRegistry.getStatus(flowId)).thenReturn(mockResult);
        when(mockFlowRegistry.getActiveFlow(flowId)).thenReturn(flow);

        ConfigFlowService.ConfigFlowInstance instance = service.getStatus(flowId);

        assertNotNull("instance 不应为 null", instance);
        assertEquals("flowId 应一致", flowId, instance.getFlowId());
        assertNotNull("flow 不应为 null", instance.getFlow());
    }

    @Test
    public void testGetStatus_FlowNotFound() {
        when(mockFlowRegistry.getStatus("non-existent-flow"))
            .thenThrow(new IllegalArgumentException("Flow not found: non-existent-flow"));

        try {
            service.getStatus("non-existent-flow");
            fail("应抛出 FlowNotFoundException");
        } catch (FlowNotFoundException e) {
            assertTrue("异常消息应包含 Flow not found",
                e.getMessage().contains("Flow not found"));
        }
    }

    // ========== cancelFlow() 测试 ==========

    @Test
    public void testCancelFlow_Success() {
        when(mockFlowRegistry.hasUserStep(anyString())).thenReturn(true);
        when(mockFlowRegistry.createFlow(anyString())).thenReturn(testProvider.createFlow());
        ConfigFlowService.ConfigFlowInstance startInstance = service.startFlow("com.ecat.integration:test-provider");
        String flowId = startInstance.getFlowId();

        // 确保 getActiveFlow 返回非 null（表示 flow 存在）
        when(mockFlowRegistry.getActiveFlow(flowId)).thenReturn(startInstance.getFlow());

        service.cancelFlow(flowId);

        verify(mockFlowRegistry).abortActiveFlow(flowId);
    }

    @Test
    public void testCancelFlow_FlowNotFound() {
        // getActiveFlow 返回 null — cancelFlow 不应抛异常
        when(mockFlowRegistry.getActiveFlow("non-existent-flow")).thenReturn(null);

        service.cancelFlow("non-existent-flow");
        // 验证通过（无异常）
    }

    // ========== 管理方法测试 ==========

    @Test
    public void testClearAllFlows() {
        service.clearAllFlows();

        verify(mockFlowRegistry).abortAllActiveFlows();
    }

    @Test
    public void testGetActiveFlowCount() {
        when(mockFlowRegistry.getActiveFlowCount()).thenReturn(0);
        assertEquals("初始活动流程数应为 0", 0, service.getActiveFlowCount());

        when(mockFlowRegistry.getActiveFlowCount()).thenReturn(3);
        assertEquals("活动流程数应为 3", 3, service.getActiveFlowCount());
    }

    // ========== 过期清理测试 ==========

    @Test
    public void testStartFlow_CleanupExpiredFlows() {
        when(mockFlowRegistry.hasUserStep(anyString())).thenReturn(true);
        when(mockFlowRegistry.createFlow(anyString())).thenReturn(testProvider.createFlow());

        // Mock cleanupExpiredFlows 返回 1（清理了 1 个过期流程）
        when(mockFlowRegistry.cleanupExpiredFlows(anyLong())).thenReturn(1);

        ConfigFlowService.ConfigFlowInstance instance = service.startFlow("com.ecat.integration:test-provider");

        assertNotNull("instance 不应为 null", instance);
        verify(mockFlowRegistry).cleanupExpiredFlows(anyLong());
    }

    // ========== 辅助测试类 ==========

    /**
     * 测试用 ConfigFlowProvider 实现
     */
    private static class TestConfigFlowProvider extends IntegrationBase implements ConfigFlowProvider {

        @Override
        public String getDisplayName() {
            return "Test Provider";
        }

        @Override
        public String getFlowType() {
            return "test-flow";
        }

        @Override
        public AbstractConfigFlow createFlow() {
            return new TestConfigFlow();
        }

        @Override
        public void onInit() {}

        @Override
        public void onStart() {}

        @Override
        public void onPause() {}

        @Override
        public void onRelease() {}

        @Override
        public ConfigEntry createEntry(ConfigEntry entry) {
            return entry;
        }

        @Override
        public ConfigEntry reconfigureEntry(String entryId, ConfigEntry entry) {
            return entry;
        }
    }

    /**
     * 测试用 AbstractConfigFlow 实现
     */
    private static class TestConfigFlow extends AbstractConfigFlow {
        private boolean completeOnNextStep = false;
        private String currentStep = "user";
        private List<String> stepHistory = new ArrayList<>();
        public TestConfigFlow() {
            super();
            registerStepUser("user", "用户配置", this::stepUserHandler);
        }

        private ConfigFlowResult stepUserHandler(Map<String, Object> userInput, FlowContext context) {
            if (completeOnNextStep) {
                context.setEntryData("result", "completed");
                return ConfigFlowResult.createEntry(context);
            }

            // submitStep 已收口到 drive（跑真实 handler）：action=complete → 建条目完成流程
            if (userInput != null && "complete".equals(userInput.get("action"))) {
                context.setEntryData("result", "completed");
                return ConfigFlowResult.createEntry(context);
            }

            if (userInput == null || userInput.isEmpty()) {
                return ConfigFlowResult.showForm("user", null, new HashMap<>(), context);
            }

            return ConfigFlowResult.showForm("step2", null, new HashMap<>(), context);
        }

        @Override
        public String getCurrentStep() {
            return currentStep;
        }

        @Override
        public List<String> getStepHistory() {
            return new ArrayList<>(stepHistory);
        }
    }
}
