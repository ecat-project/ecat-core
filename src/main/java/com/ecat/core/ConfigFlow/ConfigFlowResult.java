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

import com.ecat.core.Utils.DynamicConfig.ConfigDefinition;
import lombok.Getter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 配置流程结果容器
 *
 * <p>封装配置流程执行的三种结果类型：
 * <ul>
 *   <li>{@link ResultType#SHOW_FORM} - 显示表单</li>
 *   <li>{@link ResultType#CREATE_ENTRY} - 创建配置条目（流程完成）</li>
 *   <li>{@link ResultType#ABORT} - 中止流程</li>
 * </ul>
 *
 * @author ECAT Core
 */
@Getter
public class ConfigFlowResult {

    /**
     * 结果类型枚举
     */
    public enum ResultType {
        /** 显示表单 */
        SHOW_FORM,
        /** 创建配置条目（流程完成） */
        CREATE_ENTRY,
        /** 中止流程 */
        ABORT
    }

    /**
     * 结果类型
     */
    private final ResultType type;

    /**
     * 步骤 ID（SHOW_FORM 类型时使用）
     */
    private final String stepId;

    /**
     * 配置定义（SHOW_FORM 类型时使用）
     */
    private final ConfigDefinition configDefinition;

    /**
     * 错误信息映射（SHOW_FORM 类型时使用）
     */
    private final Map<String, Object> errors;

    /**
     * 流程数据（SHOW_FORM 类型时使用）
     */
    private final Map<String, Object> flowData;

    /**
     * 结果数据（CREATE_ENTRY 类型时使用）
     */
    private final Map<String, Object> data;

    /**
     * 中止原因（ABORT 类型时使用）
     */
    private final String reason;

    /**
     * 私有构造函数
     */
    private ConfigFlowResult(ResultType type, String stepId, ConfigDefinition configDefinition,
                             Map<String, Object> errors, Map<String, Object> flowData,
                             Map<String, Object> data, String reason) {
        this.type = type;
        this.stepId = stepId;
        this.configDefinition = configDefinition;
        this.errors = errors != null ? new HashMap<>(errors) : Collections.emptyMap();
        this.flowData = flowData != null ? new HashMap<>(flowData) : Collections.emptyMap();
        this.data = data != null ? new HashMap<>(data) : Collections.emptyMap();
        this.reason = reason;
    }

    /**
     * 创建显示表单的结果
     *
     * @param stepId 步骤 ID
     * @param configDefinition 配置定义
     * @param errors 错误信息
     * @param flowData 流程数据
     * @return SHOW_FORM 类型的结果
     */
    public static ConfigFlowResult showForm(String stepId, ConfigDefinition configDefinition,
                                            Map<String, Object> errors, Map<String, Object> flowData) {
        return new ConfigFlowResult(ResultType.SHOW_FORM, stepId, configDefinition,
            errors, flowData, null, null);
    }

    /**
     * 创建配置条目的结果
     *
     * <p>此结果表示流程已完成，可以创建配置条目。
     *
     * @param data 最终配置数据
     * @return CREATE_ENTRY 类型的结果
     */
    public static ConfigFlowResult createEntry(Map<String, Object> data) {
        return new ConfigFlowResult(ResultType.CREATE_ENTRY, null, null,
            null, null, data, null);
    }

    /**
     * 创建中止流程的结果
     *
     * @param reason 中止原因
     * @return ABORT 类型的结果
     */
    public static ConfigFlowResult abort(String reason) {
        return new ConfigFlowResult(ResultType.ABORT, null, null,
            null, null, null, reason);
    }
}
