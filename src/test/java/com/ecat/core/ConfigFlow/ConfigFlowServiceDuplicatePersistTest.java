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
import com.ecat.core.ConfigEntry.ConfigEntryRegistry;
import com.ecat.core.EcatCore;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * ConfigFlowService 持久化点身份冲突回归测试。
 *
 * <p>场景：确认屏首屏排重通过（身份已入 context）后、提交前，同身份 entry 并发出现
 * （双向导并发窗口）——createEntry 的唯一性收口在持久化点
 * （ConfigEntryRegistry.createEntry）触发。此前该异常裸抛到 REST 层变 500，
 * 且流程已被 finish 无法继续。契约：服务层把持久化点身份冲突转成
 * 与其它步校验失败同款的 show_form+errors 响应（flow 以 null 输入重驱当前步、
 * 用自家约定把错误挂回表单），流程保持存活、不落新 entry。
 *
 * @author coffee
 */
public class ConfigFlowServiceDuplicatePersistTest {

    private static final String COORDINATE = "test:dup-persist";
    private static final String UNIQUE_ID = "dup_persist_target_001";
    private static final String CONFLICT_MSG = "该设备已存在，请返回上一步修改";

    private EcatCore core;
    private ConfigFlowService service;
    private ConfigEntry racingEntry;

    /**
     * 确认屏首屏做真实排重（命中挂 config_summary，本 flow 自家约定形态）的 flow；
     * 提交分支不再复查——身份沿用首屏建立值，冲突由持久化点收口（并发窗口路径）。
     */
    private static class FirstScreenDedupFlow extends AbstractConfigFlow {
        FirstScreenDedupFlow(String flowId) {
            super();
            FlowContext ctx = new FlowContext(flowId);
            setContext(ctx);
            registerStep("final_confirm", this::stepFinalConfirm, "确认配置");
        }

        private ConfigFlowResult stepFinalConfirm(Map<String, Object> userInput) {
            if (userInput == null || userInput.isEmpty()) {
                try {
                    getContext().setEntryUniqueId(UNIQUE_ID, false);
                } catch (ConfigEntryRegistry.DuplicateUniqueIdException e) {
                    Map<String, Object> errors = new HashMap<>();
                    errors.put("config_summary", CONFLICT_MSG);
                    return showForm("final_confirm", new ConfigSchema(), errors);
                }
                return showForm("final_confirm", new ConfigSchema(), new HashMap<>());
            }
            return createEntry();
        }
    }

    @Before
    public void setUp() {
        core = new EcatCore();
        core.init();
        EcatCore.setInstance(core);
        service = new ConfigFlowService(core);
    }

    @After
    public void tearDown() {
        if (core != null && core.getEntryRegistry() != null && racingEntry != null) {
            core.getEntryRegistry().removeEntry(racingEntry.getEntryId());
        }
        EcatCore.setInstance(null);
    }

    @Test
    public void testForceSubmitOnConfirmPage_PersistDuplicateReturnsShowFormNotException() {
        FirstScreenDedupFlow flow = new FirstScreenDedupFlow("dup-persist-flow-001");
        flow.getContext().setCoordinate(COORDINATE);
        core.getFlowRegistry().registerActiveFlow(flow.getFlowId(), flow);

        // 首屏：尚无冲突 → 排重通过、身份入 context、确认屏干净重显
        ConfigFlowService.ConfigFlowInstance firstScreen =
                service.submitStep(flow.getFlowId(), "final_confirm", null);
        assertEquals("首屏应重显确认屏", ConfigFlowResult.ResultType.SHOW_FORM,
                firstScreen.getResult().getType());
        assertEquals("无冲突首屏不应带错误", 0, firstScreen.getResult().getErrors().size());

        // 首屏与提交之间，同身份 entry 并发出现（双向导并发窗口的确定性模拟）
        racingEntry = core.getEntryRegistry().createEntry(new ConfigEntry.Builder()
                .coordinate(COORDINATE)
                .uniqueId(UNIQUE_ID)
                .title("并发抢先创建的设备")
                .build());

        // 强行提交：confirmed=true，身份沿用 context（提交分支不复查，撞持久化点收口）
        Map<String, Object> confirmed = new HashMap<>();
        confirmed.put("confirmed", "true");
        ConfigFlowService.ConfigFlowInstance result =
                service.submitStep(flow.getFlowId(), "final_confirm", confirmed);

        // 契约 1：不抛异常，返回与其它步校验失败同款的 show_form+errors
        assertEquals("强行提交应返回 show_form 而非异常", ConfigFlowResult.ResultType.SHOW_FORM,
                result.getResult().getType());
        assertEquals("应重显确认屏", "final_confirm", result.getResult().getStepId());
        assertEquals("错误应按 flow 约定挂回 config_summary", CONFLICT_MSG,
                result.getResult().getErrors().get("config_summary"));

        // 契约 2：流程保持存活（可上一步修改后重提交），未被 finish
        assertNotNull("流程应保持存活", core.getFlowRegistry().getActiveFlow(flow.getFlowId()));

        // 契约 3：未落新 entry（同身份仍是并发抢先创建的那一条）
        ConfigEntry current = core.getEntryRegistry().getByUniqueId(COORDINATE, UNIQUE_ID);
        assertNotNull("并发 entry 不应被删", current);
        assertEquals("不应落新 entry", racingEntry.getEntryId(), current.getEntryId());
    }
}
