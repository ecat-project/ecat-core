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

package com.ecat.core.LogicDevice;

import com.ecat.core.Device.DeviceBase;
import com.ecat.core.LogicState.ILogicAttribute;
import com.ecat.core.State.AttributeBase;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 逻辑设备注册表，管理所有逻辑设备实例并提供反向索引查找能力。
 *
 * <p>核心职责：
 * <ul>
 *   <li><b>设备注册表</b>：维护 deviceID -> DeviceBase 的映射，支持注册、注销、查询</li>
 *   <li><b>反向索引</b>：维护 物理设备ID:物理属性ID -> List&lt;LogicDeviceAttrRef&gt; 的映射，
 *       用于在物理属性值更新时快速找到需要通知的逻辑属性</li>
 * </ul>
 *
 * <p>线程安全：使用 {@link ConcurrentHashMap} 和 {@link CopyOnWriteArrayList} 保证并发安全。
 *
 * <p>使用流程：
 * <ol>
 *   <li>调用 {@link #register(String, DeviceBase)} 注册逻辑设备</li>
 *   <li>在 LogicDevice 初始化完成后，调用 {@link #buildReverseIndex(String, Map)} 构建反向索引</li>
 *   <li>当物理属性更新时，调用 {@link #findByPhysicalAttr(String, String)} 查找关联的逻辑属性</li>
 *   <li>调用 {@link #unregister(String)} 注销逻辑设备（自动清理反向索引）</li>
 * </ol>
 *
 * @author coffee
 */
public class LogicDeviceRegistry {

    /**
     * 逻辑设备注册表：deviceID -> DeviceBase
     */
    private final Map<String, DeviceBase> registry = new ConcurrentHashMap<>();

    /**
     * 反向索引：物理设备ID:物理属性ID -> 逻辑设备属性引用列表
     * key 格式为 "physicalDeviceID:physicalAttrID"
     */
    private final Map<String, List<LogicDeviceAttrRef>> reverseIndex = new ConcurrentHashMap<>();

    /**
     * DAG 依赖边：用于检测逻辑设备间的循环依赖。
     * key = "sourceDeviceID:sourceAttrID"（被依赖方）
     * value = "targetDeviceID:targetAttrID"（依赖方）
     *
     * <p>只有逻辑设备→逻辑设备的绑定关系会记录到此处，
     * 物理设备→逻辑设备的绑定不可能产生环路，无需记录。
     */
    private final Map<String, Set<String>> dependencyEdges = new HashMap<>();

    /**
     * 注册逻辑设备到注册表。
     *
     * @param deviceID 逻辑设备ID（通常为 ConfigEntry 的 entryId）
     * @param device   逻辑设备实例
     */
    public void register(String deviceID, DeviceBase device) {
        registry.put(deviceID, device);
    }

    /**
     * 构建指定逻辑设备的反向索引。
     *
     * <p>遍历逻辑设备的所有逻辑属性，对于每个逻辑属性绑定的每个物理属性，
     * 在反向索引中建立 "物理设备ID:物理属性ID" -> LogicDeviceAttrRef 的映射。
     *
     * <p>如果该设备已有反向索引条目，会先清除旧条目再重建。
     * 此方法设计为由 LogicDeviceIntegration 在 LogicDevice 初始化完成后调用。
     *
     * @param deviceID 逻辑设备ID（必须已注册）
     * @param attrMap  逻辑设备的属性映射：逻辑属性ID -> ILogicAttribute
     */
    public void buildReverseIndex(String deviceID, Map<String, ILogicAttribute<?>> attrMap) {
        // 先清除该设备的旧反向索引条目
        removeReverseIndex(deviceID);

        DeviceBase device = registry.get(deviceID);
        if (device == null || attrMap == null) {
            return;
        }

        for (Map.Entry<String, ILogicAttribute<?>> entry : attrMap.entrySet()) {
            ILogicAttribute<?> logicAttr = entry.getValue();
            for (AttributeBase<?> phyAttr : logicAttr.getBindedAttrs()) {
                String key = phyAttr.getDevice().getId() + ":" + phyAttr.getAttributeID();
                LogicDeviceAttrRef ref = new LogicDeviceAttrRef(device, logicAttr);
                reverseIndex.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(ref);
            }
        }

        // 校验逻辑设备间的依赖关系是否为 DAG（无环路）
        validateDAG();
    }

    /**
     * 注销逻辑设备，同时清除该设备的所有反向索引条目。
     *
     * @param deviceID 逻辑设备ID
     */
    public void unregister(String deviceID) {
        // 先清除反向索引
        removeReverseIndex(deviceID);
        // 再从注册表中移除
        registry.remove(deviceID);
    }

    /**
     * 移除指定设备的所有反向索引条目。
     *
     * <p>遍历所有反向索引条目，删除引用了该设备的 LogicDeviceAttrRef。
     * 如果某个 key 下的引用列表变为空，则移除该 key。
     *
     * @param deviceID 逻辑设备ID
     */
    public void removeReverseIndex(String deviceID) {
        Iterator<Map.Entry<String, List<LogicDeviceAttrRef>>> it = reverseIndex.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, List<LogicDeviceAttrRef>> entry = it.next();
            entry.getValue().removeIf(ref -> deviceID.equals(ref.getLogicDevice().getId()));
            if (entry.getValue().isEmpty()) {
                it.remove();
            }
        }
    }

    /**
     * 根据设备ID获取逻辑设备实例。
     *
     * @param deviceID 逻辑设备ID
     * @return 逻辑设备实例，如果不存在则返回 null
     */
    public DeviceBase getDeviceByID(String deviceID) {
        return registry.get(deviceID);
    }

    /**
     * 获取所有已注册的逻辑设备列表。
     *
     * @return 所有逻辑设备的新列表（返回副本，修改不影响注册表内部状态）
     */
    public List<DeviceBase> getAllDevices() {
        return new ArrayList<>(registry.values());
    }

    /**
     * 根据物理属性查找引用了该属性的所有逻辑设备属性引用。
     *
     * <p>当物理设备的某个属性值更新时，调用此方法查找需要通知的逻辑属性，
     * 然后依次调用 {@link ILogicAttribute#updateBindAttrValue} 进行值更新。
     *
     * @param deviceID 物理设备ID
     * @param attrID   物理属性ID
     * @return 逻辑设备属性引用列表，如果没有引用则返回 null
     */
    public List<LogicDeviceAttrRef> findByPhysicalAttr(String deviceID, String attrID) {
        return reverseIndex.get(deviceID + ":" + attrID);
    }

    /**
     * 根据物理设备ID查找所有引用了该设备属性的逻辑设备属性引用。
     *
     * <p>遍历反向索引，匹配 key 前缀为 "{deviceID}:" 的所有条目。
     * 用于物理设备被卸载（REMOVE/DISABLE）时，不需要物理设备对象在 DeviceRegistry 中存在，
     * 直接从反向索引中查找受影响的逻辑属性。
     *
     * @param deviceID 物理设备ID
     * @return 受影响的逻辑设备属性引用列表
     */
    public List<LogicDeviceAttrRef> findByPhysicalDevice(String deviceID) {
        List<LogicDeviceAttrRef> result = new ArrayList<>();
        String prefix = deviceID + ":";
        for (Map.Entry<String, List<LogicDeviceAttrRef>> entry : reverseIndex.entrySet()) {
            if (entry.getKey().startsWith(prefix)) {
                result.addAll(entry.getValue());
            }
        }
        return result;
    }

    // ========== DAG 依赖图校验 ==========

    /**
     * 添加一条依赖边（from -> to），用于构建逻辑设备间的依赖关系图。
     *
     * @param from 被依赖方节点标识（格式："deviceID:attrID"）
     * @param to   依赖方节点标识（格式："deviceID:attrID"）
     */
    void addDependencyEdge(String from, String to) {
        dependencyEdges.computeIfAbsent(from, k -> new HashSet<>()).add(to);
    }

    /**
     * 使用 DFS 检测依赖图是否存在环路。
     *
     * @return true 如果存在环路，false 如果无环路
     */
    boolean hasCycle() {
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();

        for (String node : dependencyEdges.keySet()) {
            if (!visited.contains(node)) {
                if (hasCycleDFS(node, visited, visiting)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * DFS 环路检测递归方法。
     *
     * @param node    当前节点
     * @param visited 已完成访问的节点集合
     * @param visiting 正在访问路径上的节点集合（用于检测回边）
     * @return true 如果从 node 出发能找到环路
     */
    private boolean hasCycleDFS(String node, Set<String> visited, Set<String> visiting) {
        visiting.add(node);
        Set<String> neighbors = dependencyEdges.get(node);
        if (neighbors != null) {
            for (String neighbor : neighbors) {
                if (visiting.contains(neighbor)) {
                    return true; // 发现回边 → 环路
                }
                if (!visited.contains(neighbor)) {
                    if (hasCycleDFS(neighbor, visited, visiting)) {
                        return true;
                    }
                }
            }
        }
        visiting.remove(node);
        visited.add(node);
        return false;
    }

    /**
     * 校验依赖图是否为 DAG（无环路）。
     * 如果存在环路，抛出 {@link IllegalStateException}。
     *
     * <p>在每次 {@link #buildReverseIndex(String, Map)} 末尾调用，
     * 确保逻辑设备间的绑定关系不会形成循环依赖。
     *
     * @throws IllegalStateException 如果检测到循环依赖
     */
    void validateDAG() {
        if (dependencyEdges.isEmpty()) {
            return;
        }
        Set<String> visited = new HashSet<>();
        Set<String> visiting = new HashSet<>();

        for (String node : dependencyEdges.keySet()) {
            if (!visited.contains(node)) {
                if (hasCycleDFS(node, visited, visiting)) {
                    throw new IllegalStateException(
                        "Dependency cycle detected in logic device bindings. Nodes in cycle: " + visiting);
                }
            }
        }
    }
}
