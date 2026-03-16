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

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;
import java.util.function.BiFunction;

/**
 * 入口步骤定义 (用于 user、reconfigure、discovery 等入口)
 * <p>
 * 设计说明：
 * <ul>
 *   <li>每类入口每个 Flow 只能注册一个</li>
 *   <li>handler 接收 userInput 和 FlowContext，返回 ConfigFlowResult</li>
 *   <li>通过 registerStepUser()、registerStepReconfigure()、registerStepDiscovery() 注册</li>
 * </ul>
 *
 * @author coffee
 */
@Data
@AllArgsConstructor
public class EntryStepDefinition {
    /**
     * 步骤 ID
     */
    private final String stepId;

    /**
     * 显示名称
     */
    private final String displayName;

    /**
     * 处理器
     * <p>
     * 接收用户输入和流程上下文，返回流程结果
     */
    private final BiFunction<Map<String, Object>, FlowContext, ConfigFlowResult> handler;
}
