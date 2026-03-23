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

import java.util.HashMap;
import java.util.Map;

import com.ecat.core.ConfigEntry.ConfigEntryRegistry;
import com.ecat.core.EcatCore;

/**
 * 流程上下文 - 跨 Step/Flow 共享状态的唯一数据源
 * <p>
 * 数据分区：
 * <ul>
 *   <li>{@link #data} — 业务数据（将写入 ConfigEntry.data）</li>
 *   <li>{@link #stepInputs} — 各步骤用户输入 (stepId → userInput)</li>
 *   <li>{@link #entryTitle} — Entry 标题（createEntry 时使用）</li>
 *   <li>{@link #entryUniqueId} — Entry 业务唯一标识（createEntry 时使用）</li>
 * </ul>
 *
 * @author coffee
 */
public class FlowContext {

    private final String flowId;
    private String coordinate;
    private String currentStep;

    /** 业务数据（将写入 ConfigEntry.data） */
    private Map<String, Object> data = new HashMap<>();

    /** 各步骤用户输入 (stepId → userInput)，与 data 同级 */
    private Map<String, Object> stepInputs = new HashMap<>();

    /** Entry 标题（createEntry 时使用，不存入 data） */
    private String entryTitle;

    /** Entry 业务唯一标识（createEntry 时使用，不存入 data） */
    private String entryUniqueId;

    /**
     * 创建流程上下文
     *
     * @param flowId 流程标识符
     */
    public FlowContext(String flowId) {
        this.flowId = flowId;
    }

    // ========== Entry 业务数据 ==========

    /**
     * 设置 Entry 业务数据
     *
     * @param key   键
     * @param value 值
     */
    public void setEntryData(String key, Object value) {
        data.put(key, value);
    }

    /**
     * 获取 Entry 业务数据
     *
     * @param key 键
     * @return 值，不存在返回 null
     */
    public Object getEntryData(String key) {
        return data.get(key);
    }

    /**
     * 获取指定类型的 Entry 业务数据
     *
     * @param <T>  值的类型
     * @param key  键
     * @param type 类型
     * @return 值，不存在或类型不匹配返回 null
     */
    public <T> T getEntryData(String key, Class<T> type) {
        Object value = data.get(key);
        return value != null ? type.cast(value) : null;
    }

    /**
     * 获取 Entry 业务数据映射（用于批量操作）
     * <p>
     * 不要在此存储 step_inputs、title、uniqueId — 框架已提供独立 API。
     *
     * @return 业务数据映射
     */
    public Map<String, Object> getEntryData() {
        return data;
    }

    // ========== StepInputs ==========

    /**
     * 获取所有步骤输入数据
     *
     * @return 步骤输入映射 (stepId → userInput)
     */
    public Map<String, Object> getStepInputs() {
        return stepInputs;
    }

    /**
     * 设置所有步骤输入数据
     *
     * @param stepInputs 步骤输入映射
     */
    public void setStepInputs(Map<String, Object> stepInputs) {
        this.stepInputs = stepInputs != null ? stepInputs : new HashMap<>();
    }

    // ========== EntryTitle ==========

    /**
     * 获取 Entry 标题
     *
     * @return Entry 标题
     */
    public String getEntryTitle() {
        return entryTitle;
    }

    /**
     * 设置 Entry 标题（createEntry 时使用，不存入 data）
     *
     * @param entryTitle Entry 标题
     */
    public void setEntryTitle(String entryTitle) {
        this.entryTitle = entryTitle;
    }

    // ========== EntryUniqueId ==========

    /**
     * 获取 Entry 业务唯一标识
     *
     * @return Entry 唯一标识
     */
    public String getEntryUniqueId() {
        return entryUniqueId;
    }

    /**
     * 设置 Entry 业务唯一标识（自动校验唯一性）
     * <p>
     * CREATE 模式下使用此方法，校验 activeFlows + 持久化 entry。
     *
     * @param entryUniqueId Entry 唯一标识
     * @throws ConfigEntryRegistry.DuplicateUniqueIdException 如果 uniqueId 已存在
     */
    public void setEntryUniqueId(String entryUniqueId) throws ConfigEntryRegistry.DuplicateUniqueIdException {
        setEntryUniqueId(entryUniqueId, false);
    }

    /**
     * 设置 Entry 业务唯一标识
     * <p>
     * RECONFIGURE 模式下使用此方法（skipValidation=true），跳过所有唯一性校验。
     * 原因：reconfigure 时 uniqueId 属于正在修改的 entry，校验无意义；
     * 多个 flow 同时 reconfigure 同一 entry 是合法场景（last write wins），
     * 并发控制由 flow 业务层自行处理。
     *
     * @param entryUniqueId Entry 唯一标识
     * @param skipValidation 是否跳过唯一性校验（RECONFIGURE 时传 true）
     */
    public void setEntryUniqueId(String entryUniqueId, boolean skipValidation) throws ConfigEntryRegistry.DuplicateUniqueIdException {
        if (entryUniqueId != null && !skipValidation) {
            EcatCore core = EcatCore.getInstance();
            if (core != null) {
                // 检查其他运行中的 flow 是否占用此 uniqueId
                ConfigFlowRegistry flowRegistry = core.getFlowRegistry();
                if (flowRegistry != null && flowRegistry.hasActiveFlowWithUniqueId(entryUniqueId, this.flowId)) {
                    throw new ConfigEntryRegistry.DuplicateUniqueIdException(entryUniqueId);
                }
                // 检查已持久化的 entry
                ConfigEntryRegistry entryRegistry = core.getEntryRegistry();
                if (entryRegistry != null && entryRegistry.getByUniqueId(entryUniqueId) != null) {
                    throw new ConfigEntryRegistry.DuplicateUniqueIdException(entryUniqueId);
                }
            }
        }
        this.entryUniqueId = entryUniqueId;
    }

    // ========== 基础属性 ==========

    /**
     * 获取流程标识符
     *
     * @return 流程标识符
     */
    public String getFlowId() {
        return flowId;
    }

    /**
     * 获取当前步骤
     *
     * @return 当前步骤标识符
     */
    public String getCurrentStep() {
        return currentStep;
    }

    /**
     * 设置当前步骤
     *
     * @param step 步骤标识符
     */
    public void setCurrentStep(String step) {
        this.currentStep = step;
    }

    /**
     * 获取集成标识符 (coordinate)
     *
     * @return 集成标识符
     */
    public String getCoordinate() {
        return coordinate;
    }

    /**
     * 设置集成标识符 (coordinate)
     *
     * @param coordinate 集成标识符
     */
    public void setCoordinate(String coordinate) {
        this.coordinate = coordinate;
    }
}
