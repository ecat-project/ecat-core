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
import lombok.Getter;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 配置流程结果容器（新版，仅支持 ConfigSchema）
 *
 * <p>封装配置流程执行的三种结果类型：
 * <ul>
 *   <li>{@link ResultType#SHOW_FORM} - 显示表单</li>
 *   <li>{@link ResultType#CREATE_ENTRY} - 创建配置条目（流程完成）</li>
 *   <li>{@link ResultType#ABORT} - 中止流程</li>
 * </ul>
 *
 * <p>数据存储： 所有数据通过 {@link FlowContext} 统一管理，避免数据复制。
 *
 * @author coffee
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
     * 配置 Schema（SHOW_FORM 类型时使用）
     */
    private final ConfigSchema schema;

    /**
     * 错误信息映射（SHOW_FORM 类型时使用）
     */
    private final Map<String, Object> errors;

    /**
     * 流程上下文（单一数据源）
     * <p>
     * 所有流程数据通过 FlowContext 管理，避免数据复制。
     */
    private final FlowContext context;

    /**
     * 中止原因（ABORT 类型时使用）
     */
    private final String reason;

    /**
     * 配置条目（CREATE_ENTRY 类型时使用）
     */
    private final ConfigEntry entry;

    /**
     * 私有构造函数
     */
    private ConfigFlowResult(ResultType type, String stepId, ConfigSchema schema,
                             Map<String, Object> errors, FlowContext context, String reason, ConfigEntry entry) {
        this.type = type;
        this.stepId = stepId;
        this.schema = schema;
        this.errors = errors != null ? new HashMap<>(errors) : Collections.emptyMap();
        this.context = context;
        this.reason = reason;
        this.entry = entry;
    }

    /**
     * 创建显示表单的结果
     *
     * @param stepId 步骤 ID
     * @param schema 配置 Schema
     * @param errors 错误信息
     * @param context 流程上下文
     * @return SHOW_FORM 类型的结果
     */
    public static ConfigFlowResult showForm(String stepId, ConfigSchema schema,
                                            Map<String, Object> errors, FlowContext context) {
        return new ConfigFlowResult(ResultType.SHOW_FORM, stepId, schema, errors, context, null, null);
    }

    /**
     * 创建配置条目的结果（仅包含上下文）
     *
     * <p>此结果表示流程已完成，可以创建配置条目。
     *
     * @param context 流程上下文（包含最终配置数据）
     * @return CREATE_ENTRY 类型的结果
     */
    public static ConfigFlowResult createEntry(FlowContext context) {
        return new ConfigFlowResult(ResultType.CREATE_ENTRY, null, null, null, context, null, null);
    }

    /**
     * 创建配置条目的结果（包含 ConfigEntry）
     *
     * <p>此结果表示流程已完成，已创建配置条目。
     *
     * @param entry 配置条目
     * @param context 流程上下文
     * @return CREATE_ENTRY 类型的结果
     */
    public static ConfigFlowResult createEntry(ConfigEntry entry, FlowContext context) {
        return new ConfigFlowResult(ResultType.CREATE_ENTRY, null, null, null, context, null, entry);
    }

    /**
     * 更新配置条目的结果
     *
     * <p>此结果表示重配置流程已完成，已更新配置条目。
     *
     * @param entry 配置条目
     * @param context 流程上下文
     * @return CREATE_ENTRY 类型的结果
     */
    public static ConfigFlowResult updateEntry(ConfigEntry entry, FlowContext context) {
        return new ConfigFlowResult(ResultType.CREATE_ENTRY, null, null, null, context, null, entry);
    }

    /**
     * 创建中止流程的结果
     *
     * @param reason 中止原因
     * @return ABORT 类型的结果
     */
    public static ConfigFlowResult abort(String reason) {
        return new ConfigFlowResult(ResultType.ABORT, null, null, null, null, reason, null);
    }

    /**
     * 获取流程数据
     * <p>
     * 统一的数据访问方法，从 FlowContext 获取数据。
     *
     * @return 流程数据
     */
    public Map<String, Object> getData() {
        return context != null ? context.getData() : Collections.emptyMap();
    }

    /**
     * 获取流程数据（兼容别名）
     *
     * @return 流程数据
     * @deprecated 使用 {@link #getData()} 代替
     */
    @Deprecated
    public Map<String, Object> getFlowData() {
        return getData();
    }
}
