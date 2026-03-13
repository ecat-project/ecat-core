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

import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * ConfigFlowRegistry 单元测试
 * <p>
 * 测试 Flow 注册、实例创建和能力查询功能。
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
        TestConfigFlowWithSteps flow = new TestConfigFlowWithSteps();
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

    // ==================== 辅助测试类 ====================

    /**
     * 测试用的 ConfigFlow 实现类（无入口步骤）
     */
    private static class TestConfigFlow extends AbstractConfigFlow {

        public TestConfigFlow() {
            super(null);
        }

        @Override
        protected ConfigFlowResult step_user(Map<String, Object> userInput) {
            return ConfigFlowResult.abort("test completed");
        }
    }

    /**
     * 测试用的 ConfigFlow 实现类（有入口步骤）
     */
    private static class TestConfigFlowWithSteps extends AbstractConfigFlow {

        public TestConfigFlowWithSteps() {
            super(null);

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

        @Override
        protected ConfigFlowResult step_user(Map<String, Object> userInput) {
            return ConfigFlowResult.abort("test completed");
        }
    }
}
