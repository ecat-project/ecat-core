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

import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * ConfigFlowRegistry 单元测试
 * <p>
 * 测试 Flow 注册、实例创建、Active Flow 管理（TrackedFlow）和便捷方法。
 */
public class ConfigFlowRegistryTest {

    private ConfigFlowRegistry registry;

    @Before
    public void setUp() {
        registry = new ConfigFlowRegistry();
    }

    // ==================== registerFlow() 测试 ====================

    @Test
    public void testRegisterFlow_Success() {
        String coordinate = "com.ecat.integration:demo";
        TestConfigFlow flow = new TestConfigFlow();

        registry.registerFlow(coordinate, flow);

        FlowRegistration registration = registry.getRegistration(coordinate);

        assertNotNull("注册信息不应为 null", registration);
        assertEquals("coordinate 应该匹配", coordinate, registration.getCoordinate());
        assertEquals("Flow 类应该匹配", TestConfigFlow.class, registration.getFlowClass());
        assertFalse("默认不应支持用户入口", registration.hasUserStep());
        assertFalse("默认不应支持重配置入口", registration.hasReconfigureStep());
        assertFalse("默认不应支持发现入口", registration.hasDiscoveryStep());
    }

    @Test
    public void testRegisterFlow_WithCapabilities() {
        String coordinate = "com.ecat.integration:demo";
        TestConfigFlowWithSteps flow = new TestConfigFlowWithSteps();

        registry.registerFlow(coordinate, flow);

        FlowRegistration registration = registry.getRegistration(coordinate);

        assertNotNull("注册信息不应为 null", registration);
        assertTrue("应该支持用户入口", registration.hasUserStep());
        assertTrue("应该支持重配置入口", registration.hasReconfigureStep());
        assertTrue("应该支持发现入口", registration.hasDiscoveryStep());
    }

    @Test
    public void testRegisterFlow_Override() {
        String coordinate = "com.ecat.integration:demo";

        TestConfigFlow flow1 = new TestConfigFlow();
        registry.registerFlow(coordinate, flow1);

        TestConfigFlowWithSteps flow2 = new TestConfigFlowWithSteps();
        registry.registerFlow(coordinate, flow2);

        FlowRegistration registration = registry.getRegistration(coordinate);

        // 后注册的应该覆盖先注册的
        assertTrue("应该使用后注册的 Flow", registration.hasUserStep());
    }

    // ==================== unregisterFlow() 测试 ====================

    @Test
    public void testUnregisterFlow() {
        String coordinate = "com.ecat.integration:demo";
        TestConfigFlow flow = new TestConfigFlow();

        registry.registerFlow(coordinate, flow);
        assertNotNull("注册后应该存在", registry.getRegistration(coordinate));

        registry.unregisterFlow(coordinate);
        assertNull("注销后应该不存在", registry.getRegistration(coordinate));
    }

    @Test
    public void testUnregisterFlow_NotRegistered() {
        // 注销不存在的 flow 不应抛出异常
        registry.unregisterFlow("non-existent");
    }

    // ==================== createFlow() 测试 ====================

    @Test
    public void testCreateFlow_Success() {
        String coordinate = "com.ecat.integration:demo";
        TestConfigFlow flow = new TestConfigFlow();

        registry.registerFlow(coordinate, flow);

        AbstractConfigFlow newFlow = registry.createFlow(coordinate);

        assertNotNull("创建的 Flow 不应为 null", newFlow);
        assertTrue("应该是 TestConfigFlow 类型", newFlow instanceof TestConfigFlow);
        assertNotSame("应该返回新实例", flow, newFlow);
    }

    @Test
    public void testCreateFlow_NotRegistered() {
        AbstractConfigFlow flow = registry.createFlow("non-existent");

        assertNull("未注册的 Flow 应该返回 null", flow);
    }

    @Test
    public void testCreateFlow_StateIsolation() {
        String coordinate = "com.ecat.integration:demo";
        TestConfigFlowWithSteps flow = new TestConfigFlowWithSteps();

        registry.registerFlow(coordinate, flow);

        // 创建两个实例
        AbstractConfigFlow flow1 = registry.createFlow(coordinate);
        AbstractConfigFlow flow2 = registry.createFlow(coordinate);

        assertNotNull("flow1 不应为 null", flow1);
        assertNotNull("flow2 不应为 null", flow2);
        assertNotSame("应该是不同的实例", flow1, flow2);
    }

    // ==================== 能力查询测试 ====================

    @Test
    public void testHasUserStep() {
        String coordinate1 = "com.ecat.integration:no-steps";
        String coordinate2 = "com.ecat.integration:with-steps";

        TestConfigFlow flow1 = new TestConfigFlow();
        TestConfigFlowWithSteps flow2 = new TestConfigFlowWithSteps();

        registry.registerFlow(coordinate1, flow1);
        registry.registerFlow(coordinate2, flow2);

        assertFalse("不应支持用户入口", registry.hasUserStep(coordinate1));
        assertTrue("应该支持用户入口", registry.hasUserStep(coordinate2));
    }

    @Test
    public void testHasUserStep_NotRegistered() {
        assertFalse("未注册的 Flow 应该返回 false", registry.hasUserStep("non-existent"));
    }

    @Test
    public void testHasReconfigureStep() {
        String coordinate1 = "com.ecat.integration:no-steps";
        String coordinate2 = "com.ecat.integration:with-steps";

        TestConfigFlow flow1 = new TestConfigFlow();
        TestConfigFlowWithSteps flow2 = new TestConfigFlowWithSteps();

        registry.registerFlow(coordinate1, flow1);
        registry.registerFlow(coordinate2, flow2);

        assertFalse("不应支持重配置入口", registry.hasReconfigureStep(coordinate1));
        assertTrue("应该支持重配置入口", registry.hasReconfigureStep(coordinate2));
    }

    @Test
    public void testGetCoordinatesWithUserStep() {
        registry.registerFlow("com.ecat.integration:with-user", new TestConfigFlowWithSteps());
        registry.registerFlow("com.ecat.integration:without-user", new TestConfigFlow());

        List<String> coordinates = registry.getCoordinatesWithUserStep();

        assertEquals("应该找到 1 个 Flow", 1, coordinates.size());
        assertEquals("应该是 with-user", "com.ecat.integration:with-user", coordinates.get(0));
    }

    @Test
    public void testGetCoordinatesWithReconfigureStep() {
        registry.registerFlow("com.ecat.integration:with-reconfigure", new TestConfigFlowWithSteps());
        registry.registerFlow("com.ecat.integration:without-reconfigure", new TestConfigFlow());

        List<String> coordinates = registry.getCoordinatesWithReconfigureStep();

        assertEquals("应该找到 1 个 Flow", 1, coordinates.size());
        assertEquals("应该是 with-reconfigure", "com.ecat.integration:with-reconfigure", coordinates.get(0));
    }

    @Test
    public void testListAllCoordinates() {
        registry.registerFlow("com.ecat.integration:demo1", new TestConfigFlow());
        registry.registerFlow("com.ecat.integration:demo2", new TestConfigFlow());
        registry.registerFlow("com.ecat.integration:demo3", new TestConfigFlow());

        List<String> coordinates = registry.listAllCoordinates();

        assertEquals("应该找到 3 个 Flow", 3, coordinates.size());
        assertTrue("应该包含 demo1", coordinates.contains("com.ecat.integration:demo1"));
        assertTrue("应该包含 demo2", coordinates.contains("com.ecat.integration:demo2"));
        assertTrue("应该包含 demo3", coordinates.contains("com.ecat.integration:demo3"));
    }

    @Test
    public void testGetFlowsWithUserStep() {
        registry.registerFlow("com.ecat.integration:with-user", new TestConfigFlowWithSteps());
        registry.registerFlow("com.ecat.integration:without-user", new TestConfigFlow());

        List<AbstractConfigFlow> flows = registry.getFlowsWithUserStep();

        assertEquals("应该找到 1 个 Flow", 1, flows.size());
        assertTrue("应该是 TestConfigFlowWithSteps 类型", flows.get(0) instanceof TestConfigFlowWithSteps);
    }

    // ==================== FlowRegistration 测试 ====================

    @Test
    public void testFlowRegistration_HasUserStep() {
        FlowRegistration registration = new FlowRegistration(
            "com.ecat.integration:demo",
            TestConfigFlowWithSteps.class,
            true,
            false,
            false
        );

        assertTrue("应该支持用户入口", registration.hasUserStep());
        assertFalse("不应支持重配置入口", registration.hasReconfigureStep());
        assertFalse("不应支持发现入口", registration.hasDiscoveryStep());
    }

    // ========== Active Flow 管理测试（TrackedFlow） ==========

    @Test
    public void testRegisterAndGetActiveFlow() {
        AbstractConfigFlow flow = new TestConfigFlowWithId("flow-1");
        registry.registerActiveFlow("flow-1", flow);

        AbstractConfigFlow retrieved = registry.getActiveFlow("flow-1");
        assertNotNull("应获取到注册的 flow", retrieved);
        assertSame("应返回同一实例", flow, retrieved);
    }

    @Test
    public void testGetActiveFlow_NotFound() {
        assertNull("不存在的 flowId 应返回 null", registry.getActiveFlow("non-existent"));
    }

    @Test
    public void testGetActiveFlow_AutoTouch() throws Exception {
        AbstractConfigFlow flow = new TestConfigFlowWithId("flow-1");
        registry.registerActiveFlow("flow-1", flow);

        // 获取 TrackedFlow 的初始 lastUpdateTime
        java.lang.reflect.Field trackedFlowsField = ConfigFlowRegistry.class.getDeclaredField("trackedFlows");
        trackedFlowsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, ConfigFlowRegistry.TrackedFlow> trackedFlows =
            (Map<String, ConfigFlowRegistry.TrackedFlow>) trackedFlowsField.get(registry);
        ConfigFlowRegistry.TrackedFlow tracked = trackedFlows.get("flow-1");
        long initialTime = tracked.lastUpdateTime;

        // 等待一小段时间
        Thread.sleep(10);

        // getActiveFlow 应自动 touch
        registry.getActiveFlow("flow-1");

        long updatedTime = tracked.lastUpdateTime;
        assertTrue("lastUpdateTime 应被自动刷新", updatedTime > initialTime);
    }

    @Test
    public void testHasActiveFlowWithUniqueId() {
        AbstractConfigFlow flow1 = new TestConfigFlowWithId("flow-1");
        flow1.getContext().setEntryUniqueId("device_001");
        registry.registerActiveFlow("flow-1", flow1);

        // 相同 flowId 排除自身 — 不应冲突
        assertFalse("不应与自身冲突", registry.hasActiveFlowWithUniqueId("device_001", "flow-1"));

        // 不同 flowId — 应冲突
        assertTrue("应检测到冲突", registry.hasActiveFlowWithUniqueId("device_001", "flow-2"));

        // 不同 uniqueId — 不应冲突
        assertFalse("不同 uniqueId 不应冲突", registry.hasActiveFlowWithUniqueId("device_002", "flow-2"));
    }

    @Test
    public void testHasActiveFlowWithUniqueId_NullUniqueId() {
        AbstractConfigFlow flow = new TestConfigFlowWithId("flow-1");
        flow.getContext().setEntryUniqueId(null);
        registry.registerActiveFlow("flow-1", flow);

        assertFalse("null uniqueId 不应冲突", registry.hasActiveFlowWithUniqueId("device_001", "flow-2"));
    }

    @Test
    public void testFinishActiveFlow_CallsOnRelease() {
        final boolean[] released = {false};
        AbstractConfigFlow flow = new TestConfigFlowWithId("flow-1") {
            @Override
            protected void onRelease() {
                released[0] = true;
            }
        };
        registry.registerActiveFlow("flow-1", flow);

        registry.finishActiveFlow("flow-1");

        assertTrue("onRelease 应该被调用", released[0]);
        assertNull("flow 应被移除", registry.getActiveFlow("flow-1"));
    }

    @Test
    public void testAbortActiveFlow_CallsOnRelease() {
        final boolean[] released = {false};
        AbstractConfigFlow flow = new TestConfigFlowWithId("flow-1") {
            @Override
            protected void onRelease() {
                released[0] = true;
            }
        };
        registry.registerActiveFlow("flow-1", flow);

        registry.abortActiveFlow("flow-1");

        assertTrue("onRelease 应该被调用", released[0]);
        assertNull("flow 应被移除", registry.getActiveFlow("flow-1"));
    }

    @Test
    public void testFinishActiveFlow_NotFound() {
        // 对不存在的 flowId 调用不应抛出异常
        registry.finishActiveFlow("non-existent");
    }

    @Test
    public void testAbortActiveFlow_NotFound() {
        // 对不存在的 flowId 调用不应抛出异常
        registry.abortActiveFlow("non-existent");
    }

    @Test
    public void testGetActiveFlowIds() {
        registry.registerActiveFlow("flow-1", new TestConfigFlowWithId("flow-1"));
        registry.registerActiveFlow("flow-2", new TestConfigFlowWithId("flow-2"));

        List<String> ids = registry.getActiveFlowIds();
        assertEquals("应有 2 个 active flow", 2, ids.size());
        assertTrue("应包含 flow-1", ids.contains("flow-1"));
        assertTrue("应包含 flow-2", ids.contains("flow-2"));
    }

    @Test
    public void testGetActiveFlowCount() {
        assertEquals("初始应为 0", 0, registry.getActiveFlowCount());

        registry.registerActiveFlow("flow-1", new TestConfigFlowWithId("flow-1"));
        assertEquals("应为 1", 1, registry.getActiveFlowCount());

        registry.registerActiveFlow("flow-2", new TestConfigFlowWithId("flow-2"));
        assertEquals("应为 2", 2, registry.getActiveFlowCount());

        registry.abortActiveFlow("flow-1");
        assertEquals("应为 1", 1, registry.getActiveFlowCount());
    }

    // ========== cleanupExpiredFlows 测试 ==========

    @Test
    public void testCleanupExpiredFlows_NoExpired() {
        registry.registerActiveFlow("flow-1", new TestConfigFlowWithId("flow-1"));
        registry.registerActiveFlow("flow-2", new TestConfigFlowWithId("flow-2"));

        int removed = registry.cleanupExpiredFlows(30 * 60 * 1000);
        assertEquals("不应清理任何 flow", 0, removed);
        assertEquals("应仍有 2 个 flow", 2, registry.getActiveFlowCount());
    }

    @Test
    public void testCleanupExpiredFlows_SomeExpired() throws Exception {
        final boolean[] released = {false};
        AbstractConfigFlow expiredFlow = new TestConfigFlowWithId("expired") {
            @Override
            protected void onRelease() {
                released[0] = true;
            }
        };
        registry.registerActiveFlow("expired", expiredFlow);
        registry.registerActiveFlow("active", new TestConfigFlowWithId("active"));

        // 通过反射修改 expired flow 的 lastUpdateTime 为 31 分钟前
        java.lang.reflect.Field trackedFlowsField = ConfigFlowRegistry.class.getDeclaredField("trackedFlows");
        trackedFlowsField.setAccessible(true);
        @SuppressWarnings("unchecked")
        Map<String, ConfigFlowRegistry.TrackedFlow> trackedFlows =
            (Map<String, ConfigFlowRegistry.TrackedFlow>) trackedFlowsField.get(registry);
        trackedFlows.get("expired").lastUpdateTime = System.currentTimeMillis() - 31 * 60 * 1000;

        int removed = registry.cleanupExpiredFlows(30 * 60 * 1000);

        assertEquals("应清理 1 个过期 flow", 1, removed);
        assertTrue("onRelease 应被调用", released[0]);
        assertEquals("应剩 1 个 flow", 1, registry.getActiveFlowCount());
        assertNull("过期 flow 应被移除", registry.getActiveFlow("expired"));
        assertNotNull("活跃 flow 应仍在", registry.getActiveFlow("active"));
    }

    // ========== abortAllActiveFlows 测试 ==========

    @Test
    public void testAbortAllActiveFlows() {
        final int[] releasedCount = {0};
        AbstractConfigFlow flow1 = new TestConfigFlowWithId("flow-1") {
            @Override
            protected void onRelease() { releasedCount[0]++; }
        };
        AbstractConfigFlow flow2 = new TestConfigFlowWithId("flow-2") {
            @Override
            protected void onRelease() { releasedCount[0]++; }
        };
        registry.registerActiveFlow("flow-1", flow1);
        registry.registerActiveFlow("flow-2", flow2);

        registry.abortAllActiveFlows();

        assertEquals("所有 onRelease 都应被调用", 2, releasedCount[0]);
        assertEquals("应无活跃 flow", 0, registry.getActiveFlowCount());
    }

    // ========== 便捷方法测试 ==========

    @Test
    public void testSubmitStep_Success() {
        // 注册一个有 steps 的 flow
        registry.registerFlow("com.ecat.integration:demo", new TestConfigFlowWithSteps());

        // 创建 flow 并设置上下文（含 flowId）
        AbstractConfigFlow flow = registry.createFlow("com.ecat.integration:demo");
        FlowContext ctx = new FlowContext("test-flow");
        ctx.setCoordinate("com.ecat.integration:demo");
        flow.setContext(ctx);
        registry.registerActiveFlow("test-flow", flow);

        // 提交用户入口步骤
        ConfigFlowResult result = registry.submitStep("test-flow", "user", new HashMap<>());

        assertNotNull("result 不应为 null", result);
        assertEquals("stepId 应为 next", "next", result.getStepId());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testSubmitStep_FlowNotFound() {
        registry.submitStep("non-existent", "user", new HashMap<>());
    }

    @Test
    public void testGetStatus_Success() {
        registry.registerFlow("com.ecat.integration:demo", new TestConfigFlowWithSteps());

        AbstractConfigFlow flow = registry.createFlow("com.ecat.integration:demo");
        FlowContext ctx = new FlowContext("test-flow");
        ctx.setCoordinate("com.ecat.integration:demo");
        flow.setContext(ctx);
        registry.registerActiveFlow("test-flow", flow);

        ConfigFlowResult result = registry.getStatus("test-flow");

        assertNotNull("result 不应为 null", result);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGetStatus_FlowNotFound() {
        registry.getStatus("non-existent");
    }

    @Test
    public void testGoPrevious_Success() {
        // 注册 flow 并设置多步骤
        registry.registerFlow("com.ecat.integration:demo", new TestConfigFlowWithSteps());

        AbstractConfigFlow flow = registry.createFlow("com.ecat.integration:demo");
        FlowContext ctx = new FlowContext("test-flow");
        ctx.setCoordinate("com.ecat.integration:demo");
        flow.setContext(ctx);
        registry.registerActiveFlow("test-flow", flow);

        // 第一步返回上一步 — 第一步调用 goPrevious 应抛异常
        try {
            registry.goPrevious("test-flow");
            fail("第一步不能返回上一步");
        } catch (IllegalStateException e) {
            assertTrue("异常消息应包含 '第一步'", e.getMessage().contains("第一步"));
        }
    }

    @Test(expected = IllegalArgumentException.class)
    public void testGoPrevious_FlowNotFound() {
        registry.goPrevious("non-existent");
    }

    // ==================== 辅助测试类 ====================

    /**
     * 测试用的 ConfigFlow 实现类（无入口步骤）
     */
    private static class TestConfigFlow extends AbstractConfigFlow {

        public TestConfigFlow() {
            super();
        }
    }

    /**
     * 测试用的 ConfigFlow 实现类（有入口步骤）
     */
    private static class TestConfigFlowWithSteps extends AbstractConfigFlow {

        public TestConfigFlowWithSteps() {
            super();

            // 注册所有入口步骤
            registerStepUser("user", "用户配置", (input, context) -> {
                return ConfigFlowResult.showForm("next", new ConfigSchema(), new java.util.HashMap<>(), context);
            });

            registerStepReconfigure("reconfigure", "重新配置", (input, context) -> {
                return ConfigFlowResult.showForm("next", new ConfigSchema(), new java.util.HashMap<>(), context);
            });

            registerStepDiscovery("discovery", "设备发现", (input, context) -> {
                return ConfigFlowResult.showForm("next", new ConfigSchema(), new java.util.HashMap<>(), context);
            });
        }
    }

    /**
     * 测试用的 ConfigFlow 实现类（接受 flowId 参数，用于 Active Flow 测试）
     */
    private static class TestConfigFlowWithId extends AbstractConfigFlow {

        public TestConfigFlowWithId(String flowId) {
            super();
            this.setContext(new FlowContext(flowId));
        }
    }
}
