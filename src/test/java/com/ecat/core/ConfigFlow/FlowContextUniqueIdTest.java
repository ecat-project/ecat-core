package com.ecat.core.ConfigFlow;

import com.ecat.core.ConfigEntry.ConfigEntryRegistry;
import com.ecat.core.EcatCore;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class FlowContextUniqueIdTest {

    private FlowContext context;
    private EcatCore originalInstance;

    @Before
    public void setUp() {
        // 保存原始 EcatCore 实例
        originalInstance = EcatCore.getInstance();
        context = new FlowContext("test-flow");
    }

    @After
    public void tearDown() {
        // 恢复原始 EcatCore 实例
        EcatCore.setInstance(originalInstance);
    }

    // ========== null core 跳过校验 ==========

    @Test
    public void testSetEntryUniqueId_NullCore_SkipsValidation() {
        // 确保 core 为 null
        EcatCore.setInstance(null);

        // 不应抛出异常
        context.setEntryUniqueId("device_001");
        assertEquals("uniqueId 应被设置", "device_001", context.getEntryUniqueId());
    }

    // ========== Active Flow 冲突检测 ==========

    @Test
    public void testSetEntryUniqueId_ActiveFlowConflict() {
        // 创建并初始化真实的 EcatCore
        EcatCore core = new EcatCore();
        core.init();
        EcatCore.setInstance(core);

        ConfigFlowRegistry flowRegistry = core.getFlowRegistry();

        // 注册另一个 flow 使用相同 uniqueId
        AbstractConfigFlow otherFlow = new TestConfigFlow("other-flow");
        otherFlow.getContext().setEntryUniqueId("device_001");
        flowRegistry.registerActiveFlow("other-flow", otherFlow);

        // 当前 flow 设置相同 uniqueId 应抛出异常
        try {
            context.setEntryUniqueId("device_001");
            fail("应抛出 DuplicateUniqueIdException");
        } catch (ConfigEntryRegistry.DuplicateUniqueIdException e) {
            assertTrue("异常消息应包含 uniqueId", e.getMessage().contains("device_001"));
        }
    }

    @Test
    public void testSetEntryUniqueId_SelfExcluded() {
        EcatCore core = new EcatCore();
        core.init();
        EcatCore.setInstance(core);

        ConfigFlowRegistry flowRegistry = core.getFlowRegistry();

        // 注册当前 flow 到 registry
        AbstractConfigFlow currentFlow = new TestConfigFlow("test-flow");
        currentFlow.setContext(context);
        flowRegistry.registerActiveFlow("test-flow", currentFlow);

        // 同一个 flowId 设置相同 uniqueId 不应冲突（自身排除）
        context.setEntryUniqueId("device_001");
        assertEquals("uniqueId 应被设置", "device_001", context.getEntryUniqueId());
    }

    @Test
    public void testSetEntryUniqueId_NoActiveFlowConflict() {
        EcatCore core = new EcatCore();
        core.init();
        EcatCore.setInstance(core);

        ConfigFlowRegistry flowRegistry = core.getFlowRegistry();

        // 注册另一个 flow 使用不同 uniqueId
        AbstractConfigFlow otherFlow = new TestConfigFlow("other-flow");
        otherFlow.getContext().setEntryUniqueId("device_002");
        flowRegistry.registerActiveFlow("other-flow", otherFlow);

        // 不应抛出异常
        context.setEntryUniqueId("device_001");
        assertEquals("uniqueId 应被设置", "device_001", context.getEntryUniqueId());
    }

    // ========== null uniqueId 跳过校验 ==========

    @Test
    public void testSetEntryUniqueId_NullValue_SkipsValidation() {
        EcatCore core = new EcatCore();
        core.init();
        EcatCore.setInstance(core);

        // null uniqueId 不应校验
        context.setEntryUniqueId(null);
        assertNull("uniqueId 应为 null", context.getEntryUniqueId());
    }

    // ========== 改变 uniqueId ==========

    @Test
    public void testSetEntryUniqueId_ChangeValue() {
        EcatCore core = new EcatCore();
        core.init();
        EcatCore.setInstance(core);

        // 第一次设置
        context.setEntryUniqueId("device_001");
        assertEquals("第一次设置应成功", "device_001", context.getEntryUniqueId());

        // 修改为不同值
        context.setEntryUniqueId("device_002");
        assertEquals("修改应成功", "device_002", context.getEntryUniqueId());
    }

    // ========== skipValidation ==========

    @Test
    public void testSetEntryUniqueId_SkipValidation_BypassesAllChecks() {
        EcatCore core = new EcatCore();
        core.init();
        EcatCore.setInstance(core);

        ConfigFlowRegistry flowRegistry = core.getFlowRegistry();

        // 注册另一个 flow 使用相同 uniqueId（模拟冲突场景）
        AbstractConfigFlow otherFlow = new TestConfigFlow("other-flow");
        otherFlow.getContext().setEntryUniqueId("any_id");
        flowRegistry.registerActiveFlow("other-flow", otherFlow);

        // skipValidation=true 应全部跳过
        context.setEntryUniqueId("any_id", true);
        assertEquals("uniqueId 应被设置", "any_id", context.getEntryUniqueId());
    }

    @Test
    public void testSetEntryUniqueId_SkipValidation_NullValue() {
        // skipValidation=true + null 值也应正常工作
        context.setEntryUniqueId(null, true);
        assertNull("uniqueId 应为 null", context.getEntryUniqueId());
    }

    // ========== 辅助类 ==========

    private static class TestConfigFlow extends AbstractConfigFlow {
        public TestConfigFlow(String flowId) {
            super();
            // 框架通过 setContext 注入 flowId
            FlowContext ctx = new FlowContext(flowId);
            this.setContext(ctx);
        }
    }
}
