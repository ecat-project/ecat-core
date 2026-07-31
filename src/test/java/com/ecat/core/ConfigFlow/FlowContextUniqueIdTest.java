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

    // ========== lastWriterWins（业务驱动 flow：强制结束占同 uniqueId 的对手 active flow）==========

    /**
     * T1：lastWriterWins=true 时，遇其他 active flow 占同 uniqueId → 强制结束对手、自身不抛。
     * 场景：env-air-device-manager 用户 abandon 后以同 SN 重试，新 flow 应取代泄漏的旧 flow。
     */
    @Test
    public void testSetEntryUniqueId_LastWriterWins_ForceTerminatesOther() {
        EcatCore core = new EcatCore();
        core.init();
        EcatCore.setInstance(core);

        ConfigFlowRegistry flowRegistry = core.getFlowRegistry();

        // 另一个 active flow 占用 device_001
        AbstractConfigFlow otherFlow = new TestConfigFlow("other-flow");
        otherFlow.getContext().setEntryUniqueId("device_001");
        flowRegistry.registerActiveFlow("other-flow", otherFlow);
        assertTrue("对手 flow 应已占用 device_001",
                flowRegistry.hasActiveFlowWithUniqueId("device_001", "test-flow"));

        // 当前 flow 开 lastWriterWins
        context.setConfig(FlowContextConfig.builder().lastWriterWins(true).build());

        // 设置同 uniqueId → 不抛 + 对手被结束
        context.setEntryUniqueId("device_001");
        assertEquals("自身 uniqueId 应已设置", "device_001", context.getEntryUniqueId());
        assertFalse("对手 flow 应被强制结束", flowRegistry.hasActiveFlowWithUniqueId("device_001", "test-flow"));
    }

    /**
     * T1b：lastWriterWins=true 但同时存在多个同 uniqueId 对手 flow → 全部结束。
     */
    @Test
    public void testSetEntryUniqueId_LastWriterWins_TerminatesAllConflicting() {
        EcatCore core = new EcatCore();
        core.init();
        EcatCore.setInstance(core);

        ConfigFlowRegistry flowRegistry = core.getFlowRegistry();
        // 先设 uid（两个都未注册→无冲突），再注册；否则第二个 setEntryUniqueId 会撞已注册的第一个
        AbstractConfigFlow other1 = new TestConfigFlow("other-1");
        other1.getContext().setEntryUniqueId("device_001");
        AbstractConfigFlow other2 = new TestConfigFlow("other-2");
        other2.getContext().setEntryUniqueId("device_001");
        flowRegistry.registerActiveFlow("other-1", other1);
        flowRegistry.registerActiveFlow("other-2", other2);

        context.setConfig(FlowContextConfig.builder().lastWriterWins(true).build());
        context.setEntryUniqueId("device_001");
        assertEquals("自身 uniqueId 应已设置", "device_001", context.getEntryUniqueId());
        assertNull("对手1应被结束", flowRegistry.getActiveFlow("other-1"));
        assertNull("对手2应被结束", flowRegistry.getActiveFlow("other-2"));
    }

    /**
     * T1c：默认 config（lastWriterWins=false）遇 active-flow 冲突仍抛（通用 flow 行为回归）。
     */
    @Test
    public void testSetEntryUniqueId_DefaultConfig_StillThrowsOnActiveFlowConflict() {
        EcatCore core = new EcatCore();
        core.init();
        EcatCore.setInstance(core);

        ConfigFlowRegistry flowRegistry = core.getFlowRegistry();
        AbstractConfigFlow otherFlow = new TestConfigFlow("other-flow");
        otherFlow.getContext().setEntryUniqueId("device_001");
        flowRegistry.registerActiveFlow("other-flow", otherFlow);

        // 默认 config（lastWriterWins=false）→ 抛异常（对手保留）
        try {
            context.setEntryUniqueId("device_001");
            fail("默认 config 遇冲突应抛 DuplicateUniqueIdException");
        } catch (ConfigEntryRegistry.DuplicateUniqueIdException e) {
            assertTrue("异常消息含 uniqueId", e.getMessage().contains("device_001"));
        }
        assertTrue("默认模式下对手 flow 应保留", flowRegistry.hasActiveFlowWithUniqueId("device_001", "test-flow"));
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
