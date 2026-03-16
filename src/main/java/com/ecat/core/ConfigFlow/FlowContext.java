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

/**
 * 流程上下文 - 跨 Step/Flow 共享状态的唯一数据源
 * <p>
 * 用于在整个配置流程中共享数据，包括用户输入、中间状态等。
 *
 * @author coffee
 */
public class FlowContext {

    private final String flowId;
    private String coordinate;
    private String currentStep;
    private final Map<String, Object> data;

    /**
     * 创建流程上下文
     *
     * @param flowId 流程标识符
     */
    public FlowContext(String flowId) {
        this.flowId = flowId;
        this.data = new HashMap<>();
    }

    /**
     * 获取数据值
     *
     * @param key 键
     * @return 值
     */
    public Object get(String key) {
        return data.get(key);
    }

    /**
     * 设置数据值
     *
     * @param key   键
     * @param value 值
     */
    public void put(String key, Object value) {
        data.put(key, value);
    }

    /**
     * 获取指定类型的值
     *
     * @param <T>  值的类型
     * @param key  键
     * @param type 类型
     * @return 值，不存在或类型不匹配返回 null
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        Object value = data.get(key);
        return value != null ? type.cast(value) : null;
    }

    /**
     * 获取数据映射
     *
     * @return 数据映射
     */
    public Map<String, Object> getData() {
        return data;
    }

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
