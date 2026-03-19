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

import com.ecat.core.Utils.Log;
import com.ecat.core.Utils.LogFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import java.util.stream.Collectors;

/**
 * 配置流程注册表 - 管理 Flow 注册和实例创建
 * <p>
 * 设计说明：
 * <ul>
 *   <li>IntegrationManager 通过 getConfigFlow() 获取实例，注册到此 Registry</li>
 *   <li>注册时从实例提取类定义和能力信息，存储到 FlowRegistration</li>
 *   <li>后续通过类定义创建新实例，保证每次使用状态隔离</li>
 *   <li>能力查询基于缓存的能力信息，无需创建实例</li>
 * </ul>
 *
 * @author coffee
 */
public class ConfigFlowRegistry {
    private static final Log log = LogFactory.getLogger(ConfigFlowRegistry.class);

    /**
     * Flow 注册信息 (类定义 + 能力)
     * key: coordinate (groupId:artifactId)
     */
    private final Map<String, FlowRegistration> registrations = new ConcurrentHashMap<>();

    /**
     * 运行中的 Flow 实例 (key: flowId)
     * <p>
     * 所有集成共享的 active flow 管理点。
     * Flow 存在于此 map 中即视为隐式占位其 uniqueId。
     * TrackedFlow 包装 Flow 实例和最后更新时间，getActiveFlow() 自动调用 touch()。
     */
    private final Map<String, TrackedFlow> trackedFlows = new ConcurrentHashMap<>();

    /**
     * 跟踪 Flow 实例及其最后更新时间
     */
    static class TrackedFlow {
        final AbstractConfigFlow flow;
        volatile long lastUpdateTime;

        TrackedFlow(AbstractConfigFlow flow) {
            this.flow = flow;
            this.lastUpdateTime = System.currentTimeMillis();
        }

        void touch() {
            this.lastUpdateTime = System.currentTimeMillis();
        }

        boolean isExpired(long expirationMs) {
            return System.currentTimeMillis() - lastUpdateTime > expirationMs;
        }
    }

    // ========== Flow 注册 ==========

    /**
     * 注册 Flow (由 IntegrationManager 调用)
     * <p>
     * 从 Flow 实例提取类定义和能力信息，存储到 FlowRegistration。
     *
     * @param coordinate  集成标识 (groupId:artifactId)
     * @param flowInstance Flow 实例 (用于提取类定义和能力)
     */
    public void registerFlow(String coordinate, AbstractConfigFlow flowInstance) {
        FlowRegistration registration = new FlowRegistration(
                coordinate,
                flowInstance.getClass(),
                flowInstance.hasUserStep(),
                flowInstance.hasReconfigureStep(),
                flowInstance.hasDiscoveryStep()
        );

        registrations.put(coordinate, registration);
        log.info("Registered config flow: {} -> {}", coordinate, flowInstance.getClass().getSimpleName());
    }

    /**
     * 注销 Flow (集成卸载时调用)
     *
     * @param coordinate 集成标识
     */
    public void unregisterFlow(String coordinate) {
        registrations.remove(coordinate);
        log.info("Unregistered config flow: {}", coordinate);
    }

    // ========== Flow 实例创建 ==========

    /**
     * 创建 Flow 实例 (每次返回新对象，保证状态隔离)
     *
     * @param coordinate 集成标识
     * @return 新创建的 Flow 实例，如果未注册则返回 null
     */
    public AbstractConfigFlow createFlow(String coordinate) {
        FlowRegistration reg = registrations.get(coordinate);
        if (reg == null) {
            return null;
        }

        try {
            return reg.getFlowClass().getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            log.error("Failed to create flow instance: {}", coordinate, e);
            return null;
        }
    }

    // ========== Active Flow 管理 ==========

    /**
     * 注册运行中的 Flow 实例
     */
    public void registerActiveFlow(String flowId, AbstractConfigFlow flow) {
        trackedFlows.put(flowId, new TrackedFlow(flow));
        log.info("Registered active flow: flowId={}, class={}", flowId, flow.getClass().getSimpleName());
    }

    /**
     * 获取运行中的 Flow 实例（自动 touch 更新活跃时间）
     */
    public AbstractConfigFlow getActiveFlow(String flowId) {
        TrackedFlow tracked = trackedFlows.get(flowId);
        if (tracked != null) {
            tracked.touch();  // 自动更新活跃时间，外部无需管理
            return tracked.flow;
        }
        return null;
    }

    /**
     * 检查是否有其他 flow 正在使用指定 uniqueId
     */
    public boolean hasActiveFlowWithUniqueId(String uniqueId, String excludeFlowId) {
        return trackedFlows.entrySet().stream()
                .filter(e -> !e.getKey().equals(excludeFlowId))
                .anyMatch(e -> uniqueId.equals(e.getValue().flow.getContext().getEntryUniqueId()));
    }

    /**
     * 正常完成 flow（CREATE_ENTRY / UPDATE_ENTRY / REMOVE_ENTRY）
     */
    public void finishActiveFlow(String flowId) {
        TrackedFlow tracked = trackedFlows.remove(flowId);
        if (tracked != null) {
            tracked.flow.onRelease();
            log.info("Finished active flow: flowId={}", flowId);
        }
    }

    /**
     * 异常终止 flow（用户取消 / 过期清理 / step 返回 ABORT）
     */
    public void abortActiveFlow(String flowId) {
        TrackedFlow tracked = trackedFlows.remove(flowId);
        if (tracked != null) {
            tracked.flow.onRelease();
            log.info("Aborted active flow: flowId={}", flowId);
        }
    }

    /**
     * 获取所有运行中的 flow ID
     */
    public List<String> getActiveFlowIds() {
        return new ArrayList<>(trackedFlows.keySet());
    }

    /**
     * 获取运行中的 flow 数量
     */
    public int getActiveFlowCount() {
        return trackedFlows.size();
    }

    /**
     * 清理过期的 flow 实例
     *
     * @param expirationMs 过期时间（毫秒）
     * @return 清理的数量
     */
    public int cleanupExpiredFlows(long expirationMs) {
        int removed = 0;
        Iterator<Map.Entry<String, TrackedFlow>> it = trackedFlows.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, TrackedFlow> entry = it.next();
            if (entry.getValue().isExpired(expirationMs)) {
                String expiredFlowId = entry.getKey();
                it.remove();
                entry.getValue().flow.onRelease();
                removed++;
                log.info("清理过期流程: flowId={}", expiredFlowId);
            }
        }
        if (removed > 0) {
            log.info("已清理 {} 个过期流程，剩余 {} 个", removed, trackedFlows.size());
        }
        return removed;
    }

    /**
     * 中止所有运行中的 flow
     */
    public void abortAllActiveFlows() {
        for (String flowId : new ArrayList<>(trackedFlows.keySet())) {
            abortActiveFlow(flowId);
        }
    }

    /**
     * 便捷方法：提交步骤（自动 touch）
     *
     * @param flowId 流程 ID
     * @param stepId 步骤 ID
     * @param userInput 用户输入
     * @return 步骤执行结果
     * @throws IllegalArgumentException 如果 flow 不存在
     */
    public ConfigFlowResult submitStep(String flowId, String stepId, Map<String, Object> userInput) {
        AbstractConfigFlow flow = getActiveFlow(flowId);  // 自动 touch
        if (flow == null) {
            throw new IllegalArgumentException("Flow not found: " + flowId);
        }
        return flow.handleStep(stepId, userInput);
    }

    /**
     * 便捷方法：获取流程状态（自动 touch）
     *
     * @param flowId 流程 ID
     * @return 当前步骤的结果
     * @throws IllegalArgumentException 如果 flow 不存在
     */
    public ConfigFlowResult getStatus(String flowId) {
        AbstractConfigFlow flow = getActiveFlow(flowId);  // 自动 touch
        if (flow == null) {
            throw new IllegalArgumentException("Flow not found: " + flowId);
        }
        return flow.handleStep(flow.getCurrentStep(), null);
    }

    /**
     * 便捷方法：返回上一步（自动 touch）
     *
     * @param flowId 流程 ID
     * @return 上一步的结果
     * @throws IllegalArgumentException 如果 flow 不存在
     */
    public ConfigFlowResult goPrevious(String flowId) {
        AbstractConfigFlow flow = getActiveFlow(flowId);  // 自动 touch
        if (flow == null) {
            throw new IllegalArgumentException("Flow not found: " + flowId);
        }
        flow.goToPreviousStep();
        return flow.handleStep(flow.getCurrentStep(), null);
    }

    // ========== 能力查询 (基于缓存) ==========

    /**
     * 获取注册信息
     *
     * @param coordinate 集成标识
     * @return Flow 注册信息，如果未注册则返回 null
     */
    public FlowRegistration getRegistration(String coordinate) {
        return registrations.get(coordinate);
    }

    /**
     * 获取支持用户入口的所有 coordinate
     *
     * @return 支持 user entry 的 coordinate 列表
     */
    public List<String> getCoordinatesWithUserStep() {
        return registrations.values().stream()
                .filter(FlowRegistration::hasUserStep)
                .map(FlowRegistration::getCoordinate)
                .collect(Collectors.toList());
    }

    /**
     * 获取支持重配置入口的所有 coordinate
     *
     * @return 支持 reconfigure entry 的 coordinate 列表
     */
    public List<String> getCoordinatesWithReconfigureStep() {
        return registrations.values().stream()
                .filter(FlowRegistration::hasReconfigureStep)
                .map(FlowRegistration::getCoordinate)
                .collect(Collectors.toList());
    }

    /**
     * 检查指定 Flow 是否支持用户入口
     *
     * @param coordinate 集成标识
     * @return 如果支持用户入口则返回 true
     */
    public boolean hasUserStep(String coordinate) {
        FlowRegistration reg = registrations.get(coordinate);
        return reg != null && reg.hasUserStep();
    }

    /**
     * 检查指定 Flow 是否支持重配置入口
     *
     * @param coordinate 集成标识
     * @return 如果支持重配置入口则返回 true
     */
    public boolean hasReconfigureStep(String coordinate) {
        FlowRegistration reg = registrations.get(coordinate);
        return reg != null && reg.hasReconfigureStep();
    }

    /**
     * 列出所有已注册的 coordinate
     *
     * @return 已注册的 coordinate 列表
     */
    public List<String> listAllCoordinates() {
        return new ArrayList<>(registrations.keySet());
    }

    /**
     * 获取支持用户入口的 Flow 实例列表
     * <p>
     * 每次返回新实例，调用方负责设置上下文。
     *
     * @return 支持 user entry 的 Flow 实例列表
     */
    public List<AbstractConfigFlow> getFlowsWithUserStep() {
        return registrations.values().stream()
                .filter(FlowRegistration::hasUserStep)
                .map(reg -> {
                    try {
                        return reg.getFlowClass().getDeclaredConstructor().newInstance();
                    } catch (Exception e) {
                        log.error("Failed to create flow instance: {}", reg.getCoordinate(), e);
                        return null;
                    }
                })
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }
}
