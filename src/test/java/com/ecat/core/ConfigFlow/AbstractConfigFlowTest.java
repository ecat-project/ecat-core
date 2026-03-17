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

import com.ecat.core.ConfigEntry.SourceType;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * AbstractConfigFlow 单元测试
 * <p>
 * 测试入口步骤机制（registerStepUser, registerStepReconfigure, registerStepDiscovery）。
 */
public class AbstractConfigFlowTest {

    // ==================== 入口步骤注册测试 ====================

    @Test
    public void testRegisterStepUser() {
        TestConfigFlow flow = new TestConfigFlow();

        flow.registerStepUser("user", "用户配置", (input, context) -> {
            FlowContext ctx = new FlowContext("test-flow");
            return ConfigFlowResult.showForm("next", new ConfigSchema(), new HashMap<>(), ctx);
        });

        assertTrue("应该支持用户入口", flow.hasUserStep());
        assertNotNull("用户入口步骤不应为 null", flow.getUserStep());
        assertEquals("步骤 ID 应该匹配", "user", flow.getUserStep().getStepId());
        assertEquals("显示名称应该匹配", "用户配置", flow.getUserStep().getDisplayName());
    }

    @Test
    public void testRegisterStepReconfigure() {
        TestConfigFlow flow = new TestConfigFlow();

        flow.registerStepReconfigure("reconfigure", "重新配置", (input, context) -> {
            FlowContext ctx = new FlowContext("test-flow");
            return ConfigFlowResult.showForm("next", new ConfigSchema(), new HashMap<>(), ctx);
        });

        assertTrue("应该支持重配置入口", flow.hasReconfigureStep());
        assertNotNull("重配置入口步骤不应为 null", flow.getReconfigureStep());
        assertEquals("步骤 ID 应该匹配", "reconfigure", flow.getReconfigureStep().getStepId());
        assertEquals("显示名称应该匹配", "重新配置", flow.getReconfigureStep().getDisplayName());
    }

    @Test
    public void testRegisterStepDiscovery() {
        TestConfigFlow flow = new TestConfigFlow();

        flow.registerStepDiscovery("discovery", "设备发现", (input, context) -> {
            FlowContext ctx = new FlowContext("test-flow");
            return ConfigFlowResult.showForm("next", new ConfigSchema(), new HashMap<>(), ctx);
        });

        assertTrue("应该支持发现入口", flow.hasDiscoveryStep());
        assertNotNull("发现入口步骤不应为 null", flow.getDiscoveryStep());
        assertEquals("步骤 ID 应该匹配", "discovery", flow.getDiscoveryStep().getStepId());
        assertEquals("显示名称应该匹配", "设备发现", flow.getDiscoveryStep().getDisplayName());
    }

    @Test
    public void testDefaultNoEntrySteps() {
        TestConfigFlow flow = new TestConfigFlow();

        assertFalse("默认不应支持用户入口", flow.hasUserStep());
        assertFalse("默认不应支持重配置入口", flow.hasReconfigureStep());
        assertFalse("默认不应支持发现入口", flow.hasDiscoveryStep());
    }

    // ==================== 入口执行测试 ====================

    @Test
    public void testExecuteUserStep() {
        TestConfigFlow flow = new TestConfigFlow();
        FlowContext context = new FlowContext("test-flow");
        context.setCoordinate("com.ecat.integration:demo");
        flow.setContext(context);

        flow.registerStepUser("user", "用户配置", (input, ctx) -> {
            if (input == null || input.isEmpty()) {
                return ConfigFlowResult.showForm("user", new ConfigSchema(), new HashMap<>(), ctx);
            }
            return ConfigFlowResult.showForm("next", new ConfigSchema(), new HashMap<>(), ctx);
        });

        // 首次调用（无输入）
        ConfigFlowResult result = flow.executeUserStep(null);
        assertEquals("应该显示表单", ConfigFlowResult.ResultType.SHOW_FORM, result.getType());
        assertEquals("步骤 ID 应该是 user", "user", result.getStepId());

        // 第二次调用（有输入）
        Map<String, Object> input = new HashMap<>();
        input.put("key", "value");
        result = flow.executeUserStep(input);
        assertEquals("应该显示下一步", "next", result.getStepId());
    }

    @Test
    public void testExecuteUserStep_NotRegistered() {
        TestConfigFlow flow = new TestConfigFlow();
        FlowContext context = new FlowContext("test-flow");
        flow.setContext(context);

        try {
            flow.executeUserStep(null);
            fail("应该抛出 IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue("异常消息应该包含提示信息", e.getMessage().contains("User entry step"));
        }
    }

    @Test
    public void testExecuteReconfigureStep() {
        TestConfigFlow flow = new TestConfigFlow();
        FlowContext context = new FlowContext("test-flow");
        context.setCoordinate("com.ecat.integration:demo");
        flow.setContext(context);

        flow.registerStepReconfigure("reconfigure", "重新配置", (input, ctx) -> {
            return ConfigFlowResult.showForm("next", new ConfigSchema(), new HashMap<>(), ctx);
        });

        ConfigFlowResult result = flow.executeReconfigureStep("entry-id-123", null);

        assertEquals("应该显示下一步", "next", result.getStepId());
        assertEquals("entryId 应该被设置", "entry-id-123", flow.reconfigureEntryId);
        assertEquals("sourceType 应该是 RECONFIGURE", SourceType.RECONFIGURE, flow.getSourceType());
    }

    @Test
    public void testExecuteReconfigureStep_NotRegistered() {
        TestConfigFlow flow = new TestConfigFlow();
        FlowContext context = new FlowContext("test-flow");
        flow.setContext(context);

        try {
            flow.executeReconfigureStep("entry-id", null);
            fail("应该抛出 IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue("异常消息应该包含提示信息", e.getMessage().contains("Reconfigure entry step"));
        }
    }

    // ==================== SourceType 测试 ====================

    @Test
    public void testSetSourceType() {
        TestConfigFlow flow = new TestConfigFlow();

        assertEquals("默认 sourceType 应该是 USER", SourceType.USER, flow.getSourceType());

        flow.setSourceType(SourceType.RECONFIGURE);
        assertEquals("sourceType 应该更新", SourceType.RECONFIGURE, flow.getSourceType());

        flow.setSourceType(SourceType.DISCOVERY);
        assertEquals("sourceType 应该更新", SourceType.DISCOVERY, flow.getSourceType());
    }

    @Test
    public void testSetReconfigureEntryId() {
        TestConfigFlow flow = new TestConfigFlow();

        assertNull("默认 reconfigureEntryId 应该是 null", flow.reconfigureEntryId);

        flow.setReconfigureEntryId("test-entry-id");
        assertEquals("reconfigureEntryId 应该被设置", "test-entry-id", flow.reconfigureEntryId);
    }

    // ==================== createEntry() 测试 ====================

    @Test
    public void testCreateEntry_UserSource() {
        TestConfigFlow flow = new TestConfigFlow();
        FlowContext context = new FlowContext("test-flow");
        context.setCoordinate("com.ecat.integration:demo");
        flow.setContext(context);

        // 设置数据
        context.getData().put("title", "Test Entry");
        context.getData().put("uniqueId", "demo_123");
        context.getData().put("key", "value");

        ConfigFlowResult result = flow.createEntry();

        assertEquals("结果类型应该是 CREATE_ENTRY", ConfigFlowResult.ResultType.CREATE_ENTRY, result.getType());
        assertNotNull("entry 不应为 null", result.getEntry());
        assertEquals("coordinate 应该匹配", "com.ecat.integration:demo", result.getEntry().getCoordinate());
        assertEquals("title 应该匹配", "Test Entry", result.getEntry().getTitle());
        assertEquals("uniqueId 应该匹配", "demo_123", result.getEntry().getUniqueId());
    }

    @Test
    public void testCreateEntry_ReconfigureSource() {
        TestConfigFlow flow = new TestConfigFlow();
        FlowContext context = new FlowContext("test-flow");
        context.setCoordinate("com.ecat.integration:demo");
        flow.setContext(context);

        // 设置为重配置模式
        flow.setSourceType(SourceType.RECONFIGURE);
        flow.setReconfigureEntryId("existing-entry-id");

        // 设置数据
        context.getData().put("title", "Updated Entry");

        ConfigFlowResult result = flow.createEntry();

        assertEquals("结果类型应该是 CREATE_ENTRY (update)", ConfigFlowResult.ResultType.CREATE_ENTRY, result.getType());
        assertNotNull("entry 不应为 null", result.getEntry());
        assertEquals("entryId 应该保持不变", "existing-entry-id", result.getEntry().getEntryId());
    }

    // ==================== 步骤数据管理测试 ====================

    @Test
    public void testSaveAndGetStepData() {
        TestConfigFlow flow = new TestConfigFlow();
        FlowContext context = new FlowContext("test-flow");
        flow.setContext(context);

        Map<String, Object> stepData = new HashMap<>();
        stepData.put("key1", "value1");
        stepData.put("key2", 123);

        flow.saveStepData("user", stepData);

        Map<String, Object> retrieved = flow.getStepData("user");

        assertNotNull("获取的数据不应为 null", retrieved);
        assertEquals("key1 应该匹配", "value1", retrieved.get("key1"));
        assertEquals("key2 应该匹配", 123, retrieved.get("key2"));
    }

    @Test
    public void testGetCurrentStepData() {
        TestConfigFlow flow = new TestConfigFlow();
        FlowContext context = new FlowContext("test-flow");
        flow.setContext(context);

        Map<String, Object> stepData = new HashMap<>();
        stepData.put("key", "value");

        flow.saveStepData("user", stepData);

        Map<String, Object> currentData = flow.getCurrentStepData();

        assertNotNull("当前步骤数据不应为 null", currentData);
        assertEquals("应该包含保存的数据", "value", currentData.get("key"));
    }

    @Test
    public void testGetStepData_NotFound() {
        TestConfigFlow flow = new TestConfigFlow();
        FlowContext context = new FlowContext("test-flow");
        flow.setContext(context);

        Map<String, Object> data = flow.getStepData("non-existent");

        assertNotNull("不应返回 null", data);
        assertTrue("应该返回空 Map", data.isEmpty());
    }

    // ==================== 步骤历史测试 ====================

    @Test
    public void testStepHistory() {
        TestConfigFlow flow = new TestConfigFlow();
        FlowContext context = new FlowContext("test-flow");
        flow.setContext(context);

        flow.registerStep("step1", input -> ConfigFlowResult.abort("test"));
        flow.registerStep("step2", input -> ConfigFlowResult.abort("test"));

        // 执行步骤
        flow.handleStep("step1", null);
        flow.handleStep("step2", null);

        assertEquals("应该记录 2 个步骤", 2, flow.getStepHistory().size());
        assertEquals("第一个步骤应该是 step1", "step1", flow.getStepHistory().get(0));
        assertEquals("第二个步骤应该是 step2", "step2", flow.getStepHistory().get(1));
    }

    @Test
    public void testGetPreviousStep() {
        TestConfigFlow flow = new TestConfigFlow();
        FlowContext context = new FlowContext("test-flow");
        flow.setContext(context);

        flow.registerStep("step1", input -> ConfigFlowResult.abort("test"));
        flow.registerStep("step2", input -> ConfigFlowResult.abort("test"));

        flow.handleStep("step1", null);
        flow.handleStep("step2", null);

        assertEquals("上一步应该是 step1", "step1", flow.getPreviousStep());
    }

    @Test
    public void testGetPreviousStep_FirstStep() {
        TestConfigFlow flow = new TestConfigFlow();
        FlowContext context = new FlowContext("test-flow");
        flow.setContext(context);

        flow.registerStep("step1", input -> ConfigFlowResult.abort("test"));

        flow.handleStep("step1", null);

        assertNull("第一步没有上一步", flow.getPreviousStep());
    }

    // ==================== 流程数据测试 ====================

    @Test
    public void testFlowDataManagement() {
        TestConfigFlow flow = new TestConfigFlow();
        FlowContext context = new FlowContext("test-flow");
        flow.setContext(context);

        flow.setFlowData("key1", "value1");
        flow.setFlowData("key2", 123);

        assertEquals("应该获取到 value1", "value1", flow.getFlowData("key1"));
        assertEquals("应该获取到 123", 123, flow.getFlowData("key2"));
        assertNull("不存在的 key 应该返回 null", flow.getFlowData("non-existent"));
    }

    @Test
    public void testGetFlowData_WithDefault() {
        TestConfigFlow flow = new TestConfigFlow();
        FlowContext context = new FlowContext("test-flow");
        flow.setContext(context);

        String value = flow.getFlowData("key", String.class, "default");
        assertEquals("应该返回默认值", "default", value);

        flow.setFlowData("key", "actual");
        value = flow.getFlowData("key", String.class, "default");
        assertEquals("应该返回实际值", "actual", value);
    }

    // ==================== 辅助测试类 ====================

    /**
     * 测试用的 ConfigFlow 实现类
     */
    private static class TestConfigFlow extends AbstractConfigFlow {

        public TestConfigFlow() {
            super("test-flow");
        }
    }
}
