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
import java.util.function.Supplier;
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
     * Flow 实例缓存（key: className:flowId）- 兼容旧代码
     */
    private final Map<String, AbstractConfigFlow> flowInstances = new ConcurrentHashMap<>();

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

    // ========== 兼容旧代码的 Flow 实例管理 ==========

    /**
     * 获取 Flow 实例（共享上下文）- 兼容旧代码
     * <p>
     * 如果实例已存在且上下文匹配，则返回现有实例；
     * 否则创建新实例并设置共享上下文。
     *
     * @param creator        Flow 创建器
     * @param sharedContext  共享的流程上下文
     * @param <T>            Flow 类型
     * @return Flow 实例
     * @deprecated 使用 createFlow(coordinate) 创建新实例
     */
    @Deprecated
    public <T extends AbstractConfigFlow> T getFlow(
            Supplier<T> creator,
            FlowContext sharedContext) {

        String cacheKey = creator.getClass().getName() + ":" + sharedContext.getFlowId();

        @SuppressWarnings("unchecked")
        T flow = (T) flowInstances.computeIfAbsent(cacheKey, k -> {
            T f = creator.get();
            f.setContext(sharedContext);
            f.setRegistry(this);
            return f;
        });

        return flow;
    }

    /**
     * 清除指定 Flow 实例 - 兼容旧代码
     *
     * @param flowId 流程 ID
     */
    public void clearFlow(String flowId) {
        flowInstances.entrySet().removeIf(entry -> entry.getKey().endsWith(":" + flowId));
    }

    /**
     * 清除所有 Flow 实例 - 兼容旧代码
     */
    public void clearAll() {
        flowInstances.clear();
    }
}
