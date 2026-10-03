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

import com.ecat.core.ConfigEntry.ConfigEntry;
import com.ecat.core.EcatCore;
import com.ecat.core.Integration.IntegrationBase;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * USER 源 startFlow 入口默认 last-writer-win 的行为测试。
 *
 * <p>背景：45 仓设备向导（USER 源）在 SN 步尽早 setEntryUniqueId 占坑；用户 abandon 向导后
 * 30min TTL 窗内同 SN 重试是正常路径——入口默认应让新向导顶替（abort）泄漏的旧向导，
 * 而非误报「设备已存在」。三层语义分开锁：
 * <ol>
 *   <li>startFlow 无 config 入口默认注入 LWW（新向导顶替旧向导）；</li>
 *   <li>持久化 entry 冲突不受 LWW 影响、仍 fail-loud（经 drive 转 clean ABORT(already_configured)）；</li>
 *   <li>{@link FlowContextConfig#defaults()} 本身保持保守 false（不经 startFlow 的构造遇冲突即停），
 *       显式传 config（含显式关闭）尊重传入不被入口覆盖。</li>
 * </ol>
 * discovery 源（startDiscoveryFlow）不经本入口，不注入 LWW——由 ConfigFlowServiceTest /
 * FlowContextUniqueIdTest 既有用例覆盖。
 *
 * @author coffee
 */
public class ConfigFlowStartFlowLwwTest {

    private static final String COORDINATE = "test:lww-wizard";
    private static final String SN = "SN_LWW_001";

    private EcatCore core;
    private ConfigFlowService service;

    /**
     * SN 步尽早 setEntryUniqueId 占坑的最小 USER 向导（模拟设备集成向导形态）：
     * null 输入 → SHOW_FORM(sn_step)；提交 {sn} → 占 uniqueId → SHOW_FORM(confirm_step) 待确认。
     * 冲突异常透传给 drive（框架把 {@code DuplicateUniqueIdException} 收口为 clean ABORT，reason 按
     * 持久化 entry / 活跃 flow 冲突分别取 already_configured / already_in_progress）——测框架契约，不在 flow 内吞。
     * 公有无参构造（ConfigFlowRegistry.createFlow 反射 newInstance 要求）。
     */
    public static class SnWizardFlow extends AbstractConfigFlow {
        public SnWizardFlow() {
            super();
            registerStepUser("sn_step", "SN 输入", this::stepSn);
            registerStep("confirm_step", this::stepConfirm);
        }

        private ConfigFlowResult stepSn(Map<String, Object> input, FlowContext ctx) {
            if (input == null || input.isEmpty()) {
                return showForm("sn_step", new ConfigSchema(), new HashMap<>());
            }
            ctx.setEntryUniqueId(String.valueOf(input.get("sn")));
            return showForm("confirm_step", new ConfigSchema(), new HashMap<>());
        }

        private ConfigFlowResult stepConfirm(Map<String, Object> input) {
            return createEntry();
        }
    }

    @Before
    public void setUp() {
        core = new EcatCore();
        core.init();
        EcatCore.setInstance(core);
        service = new ConfigFlowService(core);
        core.getFlowRegistry().registerFlow(COORDINATE, new SnWizardFlow());
        // createEntry 盖戳契约要求 coordinate 的集成已加载（取其 entryFormatVersion() 声明）：
        // 注册最小 stub 集成（默认声明 "4.0"），createEntry 回调走 UnsupportedOperationException 警告路径即可
        core.getIntegrationRegistry().register(COORDINATE, new StubIntegration());
    }

    /** 最小集成桩：只为盖戳契约提供 coordinate→声明版本 解析，不承载任何设备逻辑。 */
    private static class StubIntegration extends IntegrationBase {
        @Override public void onInit() { }
        @Override public void onStart() { }
        @Override public void onPause() { }
    }

    @After
    public void tearDown() {
        EcatCore.setInstance(null);
    }

    /** 把 flow 推进到「已占坑 SN、停在确认步」的在途状态（模拟用户 abandon 前的向导）。 */
    private ConfigFlowService.ConfigFlowInstance startAndOccupy() throws Exception {
        ConfigFlowService.ConfigFlowInstance flow = service.startFlow(COORDINATE);
        assertEquals("新 flow 应落 SN 步", "sn_step", flow.getStepId());
        ConfigFlowService.ConfigFlowInstance after =
                service.submitStep(flow.getFlowId(), "sn_step", Collections.singletonMap("sn", SN));
        assertEquals("占坑后应停在确认步", "confirm_step", after.getStepId());
        assertTrue("flow 应已占用 " + SN,
                core.getFlowRegistry().hasActiveFlowWithUniqueId(SN, "none"));
        return flow;
    }

    /**
     * 入口默认 LWW：无 config 的 startFlow 启动的新向导，同 SN 提交时顶替泄漏的旧向导、自身正常推进。
     */
    @Test
    public void testStartFlow_NoConfig_DefaultLwwReplacesAbandonedWizard() throws Exception {
        ConfigFlowService.ConfigFlowInstance flowA = startAndOccupy();

        // 新向导（无 config 入口）同 SN 重试
        ConfigFlowService.ConfigFlowInstance flowB = service.startFlow(COORDINATE);
        assertNotEquals("应创建新 flow", flowA.getFlowId(), flowB.getFlowId());
        assertTrue("USER 入口应注入 lastWriterWins=true 默认",
                flowB.getFlow().getContext().getConfig().isLastWriterWins());

        ConfigFlowService.ConfigFlowInstance after =
                service.submitStep(flowB.getFlowId(), "sn_step", Collections.singletonMap("sn", SN));

        assertEquals("新向导应正常推进到确认步（不被误报设备已存在）", "confirm_step", after.getStepId());
        assertEquals("新向导结果应为 SHOW_FORM", ConfigFlowResult.ResultType.SHOW_FORM,
                after.getResult().getType());
        assertNull("旧向导 flow A 应被顶替（abort 清出 registry）",
                core.getFlowRegistry().getActiveFlow(flowA.getFlowId()));
        assertNotNull("新向导 flow B 应保持活跃", core.getFlowRegistry().getActiveFlow(flowB.getFlowId()));
    }

    /** 带初始数据的 USER 入口重载同样注入 LWW 默认（两条无 config 公开重载路径一致）。 */
    @Test
    public void testStartFlow_WithInitialData_AlsoInjectsLww() {
        ConfigFlowService.ConfigFlowInstance flow =
                service.startFlow(COORDINATE, Collections.singletonMap("name", "预填名称"));
        assertTrue("带初始数据的 USER 入口也应注入 lastWriterWins=true",
                flow.getFlow().getContext().getConfig().isLastWriterWins());
    }

    /**
     * 落盘 entry 冲突不受 LWW 影响：USER 入口默认 LWW 开着，同 uniqueId entry 已持久化仍 fail-loud
     * （经 drive 转 clean ABORT(already_configured)，不留孤儿、不动已提交设备）。
     */
    @Test
    public void testStartFlow_DefaultLww_PersistedEntryConflictStillFailsLoud() {
        ConfigEntry persisted = core.getEntryRegistry().createEntry(new ConfigEntry.Builder()
                .coordinate(COORDINATE).uniqueId(SN).title("已添加设备").build());
        try {
            ConfigFlowService.ConfigFlowInstance flow = service.startFlow(COORDINATE);
            ConfigFlowService.ConfigFlowInstance after =
                    service.submitStep(flow.getFlowId(), "sn_step", Collections.singletonMap("sn", SN));

            assertEquals("落盘冲突应转 clean ABORT", ConfigFlowResult.ResultType.ABORT,
                    after.getResult().getType());
            assertTrue("reason 应为 already_configured（持久化冲突，非 active-flow 冲突）",
                    after.getResult().getReason().startsWith(AbortReason.ALREADY_CONFIGURED));
            assertNull("冲突 flow 应被清理不留孤儿", core.getFlowRegistry().getActiveFlow(flow.getFlowId()));
            assertNotNull("已持久化 entry 不应被顶替删除",
                    core.getEntryRegistry().getByUniqueId(COORDINATE, SN));
        } finally {
            core.getEntryRegistry().removeEntry(persisted.getEntryId());
        }
    }

    /** 显式 config 尊重传入：显式 lastWriterWins=false 不被入口默认覆盖，active-flow 冲突仍 fail。 */
    @Test
    public void testStartFlow_ExplicitConfigRespected_LwwOffStillFails() throws Exception {
        ConfigFlowService.ConfigFlowInstance flowA = startAndOccupy();

        ConfigFlowService.ConfigFlowInstance flowB = service.startFlow(COORDINATE,
                FlowContextConfig.builder().lastWriterWins(false).build());
        ConfigFlowService.ConfigFlowInstance after =
                service.submitStep(flowB.getFlowId(), "sn_step", Collections.singletonMap("sn", SN));

        assertEquals("显式关闭 LWW：冲突应转 clean ABORT", ConfigFlowResult.ResultType.ABORT,
                after.getResult().getType());
        assertTrue("reason 应为 already_in_progress（active-flow 冲突）",
                after.getResult().getReason().startsWith(AbortReason.ALREADY_IN_PROGRESS));
        assertNotNull("显式关闭 LWW：旧向导不应被顶替", core.getFlowRegistry().getActiveFlow(flowA.getFlowId()));
        assertNull("冲突 flow B 应被清理", core.getFlowRegistry().getActiveFlow(flowB.getFlowId()));
    }

    /**
     * 保守层与入口默认是两层语义：defaults() 本身保持 lastWriterWins=false——
     * 直接构造 flow（不经 startFlow）遇冲突即停（行为面由 FlowContextUniqueIdTest
     * .testSetEntryUniqueId_DefaultConfig_StillThrowsOnActiveFlowConflict 锁定）。
     */
    @Test
    public void testFlowContextConfig_Defaults_StayConservative() {
        assertFalse("defaults() 必须保持 lastWriterWins=false",
                FlowContextConfig.defaults().isLastWriterWins());
    }
}
