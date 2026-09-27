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
import com.ecat.core.ConfigFlow.ConfigItem.TextConfigItem;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * 子 flow 复用机制（registerFlowStep / AbstractSubConfigFlow / SUBFLOW_COMPLETE）单元测试。
 * <p>
 * 重点锁死重入收敛（出口翻译 handleStep 嵌套 handleStep 的历史/当前步终态）与挂载校验、
 * 结构禁令、context 共享、reconfigure 漫游——这些是机制的风险面。
 */
public class SubConfigFlowTest {

    // ==================== 测试用子 flow / 宿主 ====================

    /** 测试子 flow：sub_first（入口，必填校验）→ sub_second（落盘 + 出口）。 */
    private static class TestSubFlow extends AbstractSubConfigFlow {

        /** 子 flow handler 观测到的 sourceType（验证包装器逐调用同步）。 */
        final List<SourceType> observedSourceTypes = new ArrayList<>();

        TestSubFlow() {
            super();
            registerStepEntry("sub_first", this::stepFirst, "子首步");
            registerStep("sub_second", this::stepSecond, "子末步");
        }

        private ConfigFlowResult stepFirst(Map<String, Object> userInput) {
            observedSourceTypes.add(getSourceType());
            if (userInput == null || userInput.isEmpty()) {
                return showForm("sub_first", createSubSchema(), new HashMap<>());
            }
            ConfigSchema schema = createSubSchema();
            Map<String, Object> errors = schema.validate(userInput);
            if (!errors.isEmpty()) {
                return showForm("sub_first", schema, errors);
            }
            context.getEntryData().putAll(userInput);
            return showForm("sub_second", new ConfigSchema(), new HashMap<>());
        }

        private ConfigFlowResult stepSecond(Map<String, Object> userInput) {
            observedSourceTypes.add(getSourceType());
            if (userInput == null || userInput.isEmpty()) {
                return showForm("sub_second", new ConfigSchema(), new HashMap<>());
            }
            context.getEntryData().put("sub_done", true);
            return subFlowComplete();
        }

        private ConfigSchema createSubSchema() {
            return new ConfigSchema()
                    .addField(new TextConfigItem("sn", true)
                            .displayName("序列号"));
        }
    }

    /** 步骤注册了但从未声明入口步的子 flow（挂载必须被拒）。 */
    private static class SubFlowNoEntry extends AbstractSubConfigFlow {

        SubFlowNoEntry() {
            super();
            registerStep("some_step", input -> showForm("some_step", new ConfigSchema(), new HashMap<>()),
                    "无入口声明");
        }
    }

    /** 尝试把自身挂进自身的子 flow（挂载必须被拒）。 */
    private static class SelfMountSubFlow extends AbstractSubConfigFlow {

        SelfMountSubFlow() {
            super();
            registerStepEntry("self_step", input -> showForm("self_step", new ConfigSchema(), new HashMap<>()),
                    "自挂载");
        }

        void mountSelf() {
            registerFlowStep(this, "self_step");
        }
    }

    /** 标准宿主：user/reconfigure 入口 + device_config + final_confirm，挂载 TestSubFlow（尾步 final_confirm）。 */
    private static class HostFlow extends AbstractConfigFlow {

        final TestSubFlow sub;
        final String subEntryStepId;

        HostFlow() {
            this(new TestSubFlow());
        }

        HostFlow(TestSubFlow sub) {
            super();
            this.sub = sub;
            registerStepUser("user", "用户配置", this::stepUser);
            registerStepReconfigure("reconfigure", "重新配置", this::stepReconfigure);
            registerStep("device_config", this::stepDeviceConfig, "设备配置");
            registerStep("final_confirm", this::stepFinalConfirm, "确认配置");
            subEntryStepId = registerFlowStep(sub, "final_confirm");
        }

        private ConfigFlowResult stepUser(Map<String, Object> userInput, FlowContext ctx) {
            if (userInput == null || userInput.isEmpty()) {
                return showForm("user", new ConfigSchema(), new HashMap<>());
            }
            return showForm("device_config", new ConfigSchema(), new HashMap<>());
        }

        private ConfigFlowResult stepReconfigure(Map<String, Object> userInput, FlowContext ctx) {
            if (userInput == null || userInput.isEmpty()) {
                return showForm("reconfigure", new ConfigSchema(), new HashMap<>());
            }
            return showForm("device_config", new ConfigSchema(), new HashMap<>());
        }

        private ConfigFlowResult stepDeviceConfig(Map<String, Object> userInput) {
            if (userInput == null || userInput.isEmpty()) {
                return showForm("device_config", new ConfigSchema(), new HashMap<>());
            }
            context.getEntryData().putAll(userInput);
            // 与 core 启动 flow、goPrevious 同一习语进入子 flow：handleStep(stepId, null)
            return handleStep(subEntryStepId, null);
        }

        private ConfigFlowResult stepFinalConfirm(Map<String, Object> userInput) {
            if (userInput == null || userInput.isEmpty()) {
                return showForm("final_confirm", new ConfigSchema(), new HashMap<>());
            }
            context.getEntryData().put("confirmed", true);
            return createEntry();
        }
    }

    /** 手写基线宿主：自持两步（protocol_select → comm_config），comm_config 成功后 showForm 落尾步——改造前形状。 */
    private static class HandWrittenHostFlow extends AbstractConfigFlow {

        HandWrittenHostFlow() {
            super();
            registerStepUser("user", "用户配置", this::stepUser);
            registerStep("device_config", this::stepDeviceConfig, "设备配置");
            registerStep("protocol_select", this::stepProtocolSelect, "协议选择");
            registerStep("comm_config", this::stepCommConfig, "通讯配置");
            registerStep("final_confirm", this::stepFinalConfirm, "确认配置");
        }

        private ConfigFlowResult stepUser(Map<String, Object> userInput, FlowContext ctx) {
            if (userInput == null || userInput.isEmpty()) {
                return showForm("user", new ConfigSchema(), new HashMap<>());
            }
            return showForm("device_config", new ConfigSchema(), new HashMap<>());
        }

        private ConfigFlowResult stepDeviceConfig(Map<String, Object> userInput) {
            if (userInput == null || userInput.isEmpty()) {
                return showForm("device_config", new ConfigSchema(), new HashMap<>());
            }
            return showForm("protocol_select", new ConfigSchema(), new HashMap<>());
        }

        private ConfigFlowResult stepProtocolSelect(Map<String, Object> userInput) {
            if (userInput == null || userInput.isEmpty()) {
                return showForm("protocol_select", new ConfigSchema(), new HashMap<>());
            }
            return showForm("comm_config", new ConfigSchema(), new HashMap<>());
        }

        private ConfigFlowResult stepCommConfig(Map<String, Object> userInput) {
            if (userInput == null || userInput.isEmpty()) {
                return showForm("comm_config", new ConfigSchema(), new HashMap<>());
            }
            return showForm("final_confirm", new ConfigSchema(), new HashMap<>());
        }

        private ConfigFlowResult stepFinalConfirm(Map<String, Object> userInput) {
            return showForm("final_confirm", new ConfigSchema(), new HashMap<>());
        }
    }

    // ==================== 全程走读 + 重入收敛 ====================

    @Test
    public void testMountedFlow_fullTraversal_convergesLikeHandWritten() {
        HostFlow flow = new HostFlow();
        assertEquals("挂载返回入口步声明值", "sub_first", flow.subEntryStepId);

        assertTrue("发起 flow 应显示欢迎屏", isShowFormOf(flow.executeUserStep(null), "user"));
        assertTrue("提交欢迎步应到 device_config",
                isShowFormOf(flow.handleStep("user", input("k", "v")), "device_config"));
        ConfigFlowResult enterSub = flow.handleStep("device_config", input("class", "weather.sensor"));
        assertTrue("device_config 提交后应进入子 flow 首屏", isShowFormOf(enterSub, "sub_first"));
        assertEquals("currentStep 应已落到子 flow 首步", "sub_first", flow.getCurrentStep());

        assertTrue("子 flow 首步提交应到子次步",
                isShowFormOf(flow.handleStep("sub_first", input("sn", "SN001")), "sub_second"));

        ConfigFlowResult exit = flow.handleStep("sub_second", input("x", "1"));
        assertEquals("子 flow 出口信号须被翻译为宿主尾步表单（不逃逸 SUBFLOW_COMPLETE）",
                ConfigFlowResult.ResultType.SHOW_FORM, exit.getType());
        assertEquals("出口翻译应落到宿主尾步", "final_confirm", exit.getStepId());
        assertEquals("重入收敛后 currentStep = 尾步", "final_confirm", flow.getCurrentStep());

        // 手写路由基线：同一驱动节奏，终态与 history 结构逐位对齐
        HandWrittenHostFlow baseline = new HandWrittenHostFlow();
        baseline.executeUserStep(null);
        baseline.handleStep("user", input("k", "v"));
        baseline.handleStep("device_config", input("class", "weather.sensor"));
        baseline.handleStep("protocol_select", input("p", "RTU"));
        baseline.handleStep("comm_config", input("ip", "1.2.3.4"));

        assertEquals("history 长度与手写路由一致", baseline.getStepHistory().size(),
                flow.getStepHistory().size());
        assertEquals("history 首两步一致", baseline.getStepHistory().subList(0, 2),
                flow.getStepHistory().subList(0, 2));
        assertEquals("history 尾步一致", baseline.getStepHistory().get(4), flow.getStepHistory().get(4));
        assertEquals("挂载路径 history 全序", Arrays.asList(
                "user", "device_config", "sub_first", "sub_second", "final_confirm"),
                flow.getStepHistory());
    }

    @Test
    public void testGoPreviousFromTail_returnsToSubStep() {
        HostFlow flow = new HostFlow();
        flow.executeUserStep(null);
        flow.handleStep("user", input("k", "v"));
        flow.handleStep("device_config", input("class", "weather.sensor"));
        flow.handleStep("sub_first", input("sn", "SN001"));
        flow.handleStep("sub_second", input("x", "1"));

        flow.goToPreviousStep();
        assertEquals("尾步回退应回到子 flow 末步（history 与手写路由同一终态的回退面）",
                "sub_second", flow.getCurrentStep());
    }

    // ==================== 子 flow 数据落盘进宿主 context ====================

    @Test
    public void testSubStepsWriteIntoHostContext() {
        HostFlow flow = new HostFlow();

        flow.executeUserStep(null);
        flow.handleStep("user", input("k", "v"));
        flow.handleStep("device_config", input("class", "weather.sensor"));
        flow.handleStep("sub_first", input("sn", "SN001"));
        flow.handleStep("sub_second", input("x", "1"));

        Map<String, Object> entryData = flow.getContext().getEntryData();
        assertEquals("子 flow 首步输入应落进宿主 entryData（无第二份数据）",
                "SN001", entryData.get("sn"));
        assertEquals("子 flow 落盘动作应生效", Boolean.TRUE, entryData.get("sub_done"));
        assertEquals("宿主自身步骤输入也在同一 entryData", "weather.sensor", entryData.get("class"));

        Map<String, Object> stepInputs = flow.getContext().getStepInputs();
        assertTrue("子 flow 步骤输入应进宿主 stepInputs（漫游面）", stepInputs.containsKey("sub_first"));
        assertEquals("stepInputs[sub_first] 内容一致", "SN001",
                ((Map<?, ?>) stepInputs.get("sub_first")).get("sn"));
        assertTrue(stepInputs.containsKey("sub_second"));
    }

    @Test
    public void testMountInjectsHostContextInstance() {
        HostFlow flow = new HostFlow();
        assertSame("挂载时子 flow context 必须是宿主同一实例（落盘写的就是宿主数据）",
                flow.getContext(), flow.sub.getContext());
    }

    // ==================== 子 flow 中途校验失败 ====================

    @Test
    public void testSubStepValidationFailure_showsSubStepFormWithErrors() {
        HostFlow flow = new HostFlow();
        flow.executeUserStep(null);
        flow.handleStep("user", input("k", "v"));
        flow.handleStep("device_config", input("class", "weather.sensor"));

        // 缺必填字段 sn 的提交（非空 Map 触发校验路径）
        ConfigFlowResult result = flow.handleStep("sub_first", input("other", "x"));
        assertEquals("校验失败应回显子 flow 该步", ConfigFlowResult.ResultType.SHOW_FORM, result.getType());
        assertEquals("sub_first", result.getStepId());
        assertNotNull("应携带错误信息", result.getErrors().get("sn"));
        assertEquals("currentStep 停留在子 flow 该步", "sub_first", flow.getCurrentStep());
    }

    // ==================== 挂载校验 ====================

    @Test
    public void testMount_nullSub_throwsIllegalArgument() {
        HostFlow flow = new HostFlow();
        try {
            flow.registerFlowStep(null, "final_confirm");
            fail("null 子 flow 应抛 IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // 预期
        }
    }

    @Test
    public void testMount_self_throws() {
        SelfMountSubFlow sub = new SelfMountSubFlow();
        try {
            sub.mountSelf();
            fail("子 flow 挂载自身应抛异常");
        } catch (IllegalArgumentException e) {
            assertEquals("sub flow 不能为 null 或自身", e.getMessage());
        }
    }

    @Test
    public void testMount_sameInstanceTwice_throws() {
        TestSubFlow shared = new TestSubFlow();
        new HostFlow(shared);
        try {
            new HostFlow(shared);
            fail("同一子 flow 实例二次挂载应抛 IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("已被挂载"));
        }
    }

    @Test
    public void testMount_withoutEntryDeclared_throws() {
        HostFlow flow = new HostFlow();
        try {
            flow.registerFlowStep(new SubFlowNoEntry(), "final_confirm");
            fail("未声明入口步的子 flow 应抛 IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("未声明入口步"));
        }
    }

    @Test
    public void testMount_tailNotRegistered_throws() {
        HostFlow flow = new HostFlow();
        try {
            flow.registerFlowStep(new TestSubFlow(), "no_such_tail");
            fail("尾步未注册应抛 IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("尾步尚未注册"));
        }
    }

    @Test
    public void testMount_stepIdConflict_throws() {
        // 宿主已有 sub_first 步，再挂载含同名步骤的子 flow 必须拒（fail-loud，不静默覆盖）
        AbstractConfigFlow host = new AbstractConfigFlow() {
            {
                registerStep("sub_first", input -> showForm("sub_first", new ConfigSchema(), new HashMap<>()));
                registerStep("tail", input -> showForm("tail", new ConfigSchema(), new HashMap<>()));
            }
        };
        try {
            host.registerFlowStep(new TestSubFlow(), "tail");
            fail("stepId 与宿主已有步骤冲突应抛 IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("冲突"));
        }
    }

    @Test
    public void testMount_entryDeclaration_notRegistrationOrder() {
        // 入口由 registerStepEntry 显式声明，与注册顺序无关（注册顺序≠流转顺序）
        AbstractSubConfigFlow sub = new AbstractSubConfigFlow() {
            {
                registerStep("sub_late", input -> showForm("sub_late", new ConfigSchema(), new HashMap<>()));
                registerStep("sub_early", input -> showForm("sub_early", new ConfigSchema(), new HashMap<>()));
                registerStepEntry("sub_entry_last", input -> showForm("sub_entry_last",
                        new ConfigSchema(), new HashMap<>()), "入口步最后注册");
            }
        };
        HostFlow flow = new HostFlow();
        assertEquals("返回的入口步 = 声明值，不是注册顺序的第一个",
                "sub_entry_last", flow.registerFlowStep(sub, "final_confirm"));
    }

    // ==================== 结构禁令（构造期） ====================

    @Test
    public void testSubFlow_registerStepUser_banned() {
        try {
            new AbstractSubConfigFlow() {
                {
                    registerStepUser("user", "用户配置", (input, ctx) ->
                            showForm("user", new ConfigSchema(), new HashMap<>()));
                }
            };
            fail("子 flow 声明 user 入口步应抛 IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("user 入口步"));
        }
    }

    @Test
    public void testSubFlow_registerStepReconfigure_banned() {
        try {
            new AbstractSubConfigFlow() {
                {
                    registerStepReconfigure("reconfigure", "重新配置", (input, ctx) ->
                            showForm("reconfigure", new ConfigSchema(), new HashMap<>()));
                }
            };
            fail("子 flow 声明 reconfigure 入口步应抛 IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("reconfigure 入口步"));
        }
    }

    @Test
    public void testSubFlow_createEntry_banned() {
        AbstractSubConfigFlow sub = new TestSubFlow();
        try {
            sub.createEntry();
            fail("子 flow 创建 entry 应抛 IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("subFlowComplete"));
        }
    }

    // ==================== reconfigure 往返（漫游恢复） ====================

    @Test
    public void testReconfigureRoundTrip_subStepRoamingPreserved() {
        // 持久化侧：上一轮会话落下的 step_inputs（device_config 与子 flow 步）
        Map<String, Object> persisted = new LinkedHashMap<>();
        persisted.put("device_config", input("class", "weather.sensor"));
        persisted.put("sub_first", input("sn", "SN-OLD"));

        HostFlow flow = new HostFlow();
        FlowContext ctx = new FlowContext("reconfig-session");
        ctx.setStepInputs(new HashMap<>(persisted));   // service.startReconfigureFlow 的恢复动作
        flow.setContext(ctx);

        assertTrue(isShowFormOf(flow.executeReconfigureStep("entry-1", null), "reconfigure"));
        assertEquals("reconfigure 入口应置 RECONFIGURE", SourceType.RECONFIGURE, flow.getSourceType());

        assertTrue(isShowFormOf(flow.handleStep("reconfigure", input("k", "v")), "device_config"));
        assertTrue(isShowFormOf(flow.handleStep("device_config", input("class", "air.quality.detector")),
                "sub_first"));

        // 子 flow 步以 null 输入进入（回显）：stepId 不变 → 漫游数据可达
        ConfigFlowResult redisplay = flow.handleStep("sub_first", null);
        assertTrue(isShowFormOf(redisplay, "sub_first"));
        assertEquals("子 flow 步预填回显 = 持久化的 step_inputs[stepId]",
                "SN-OLD", ((Map<?, ?>) redisplay.getStepInputs().get("sub_first")).get("sn"));
        assertEquals("包装器须把 reconfigure 模式同步给子 flow（否则子 flow 误读 USER）",
                SourceType.RECONFIGURE, flow.sub.observedSourceTypes.get(0));
    }

    // ==================== 断言辅助 ====================

    private static boolean isShowFormOf(ConfigFlowResult result, String stepId) {
        return result.getType() == ConfigFlowResult.ResultType.SHOW_FORM
                && stepId.equals(result.getStepId());
    }

    private static Map<String, Object> input(String k, Object v) {
        Map<String, Object> m = new HashMap<>();
        m.put(k, v);
        return m;
    }
}
