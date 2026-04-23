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

package com.ecat.core.LogicMapping;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 设备映射管理器，负责注册和查找 IDeviceMapping 实例。
 *
 * <p>管理器使用两级 Map 结构组织映射：
 * <ul>
 *   <li>第一级：mappingType (逻辑设备类型，如 "SO2"、"UPS")</li>
 *   <li>第二级：coordinate-model (集成坐标-设备型号，如 "com.ecat:integration-saimosen-SMS8200")</li>
 * </ul>
 *
 * <p>这允许同一个物理设备（相同的 coordinate-model）被注册到多种逻辑设备类型，
 * 也允许同一种逻辑设备类型包含来自不同集成和型号的物理设备。
 *
 * <p>使用示例：
 * <pre>
 *   LogicMappingManager manager = new LogicMappingManager();
 *   manager.registerMapping(new SaimosenSO2Mapping());
 *   manager.registerMapping(new SaimosenUPSMapping());
 *
 *   IDeviceMapping so2 = manager.getMapping("SO2", "com.ecat:integration-saimosen", "SMS8200");
 *   List&lt;IDeviceMapping&gt; allSo2 = manager.getMappingsByType("SO2");
 * </pre>
 *
 * @see IDeviceMapping
 * @author coffee
 */
public class LogicMappingManager {

    /**
     * 两级映射结构：mappingType -> (coordinate-model) -> IDeviceMapping
     *
     * <p>例如：
     * <pre>
     *   "SO2" -> {
     *     "com.ecat:integration-saimosen-SMS8200" -> SaimosenSO2Mapping,
     *     "com.ecat:integration-other-OTHER" -> OtherSO2Mapping
     *   }
     *   "UPS" -> {
     *     "com.ecat:integration-saimosen-QCDevice" -> SaimosenUPSMapping
     *   }
     * </pre>
     */
    private final Map<String, Map<String, IDeviceMapping>> mappingMap = new HashMap<>();

    /**
     * 注册一个设备映射。
     *
     * <p>映射以 mappingType + coordinate + model 为复合键存储。
     * 如果同一个 mappingType + coordinate + model 已存在映射，将被覆盖。
     *
     * @param mapping 要注册的设备映射实例
     */
    public void registerMapping(IDeviceMapping mapping) {
        String mtype = mapping.getMappingType();
        String key = mapping.getDeviceCoordinate() + "-" + mapping.getDeviceModel();
        mappingMap.computeIfAbsent(mtype, k -> new LinkedHashMap<>()).put(key, mapping);
    }

    /**
     * 根据映射类型、集成坐标和设备型号精确查找设备映射。
     *
     * @param mtype 映射类型（如 "SO2"）
     * @param coordinate 集成坐标（如 "com.ecat:integration-saimosen"）
     * @param model 设备型号（如 "SMS8200"）
     * @return 匹配的设备映射实例，未找到则返回 null
     */
    public IDeviceMapping getMapping(String mtype, String coordinate, String model) {
        Map<String, IDeviceMapping> typeMap = mappingMap.get(mtype);
        if (typeMap == null) {
            return null;
        }
        return typeMap.get(coordinate + "-" + model);
    }

    /**
     * 获取所有已注册的映射类型集合。
     *
     * @return 不可修改的映射类型集合
     */
    public Set<String> getAllMappingTypes() {
        return Collections.unmodifiableSet(mappingMap.keySet());
    }

    /**
     * 获取指定映射类型下的所有设备映射。
     *
     * @param mtype 映射类型（如 "SO2"）
     * @return 该类型下所有映射的列表，未找到则返回空列表
     */
    public List<IDeviceMapping> getMappingsByType(String mtype) {
        Map<String, IDeviceMapping> typeMap = mappingMap.get(mtype);
        if (typeMap == null) {
            return Collections.emptyList();
        }
        return new ArrayList<>(typeMap.values());
    }

    /**
     * 获取指定映射类型下注册顺序的第一个设备映射。
     *
     * 这个调用只用于 getAttrDefs() -
     * 提供属性定义列表。所有 vendor mapping（如 SO2DeviceMapping_SMS8200）共享基类 SO2LogicDeviceMapping.getAttrDefs() 的定义，所以选哪个 mapping
     * 都一样的场景
     * 
     * <p>注意：此方法仅应在无法确定具体 vendor 时使用（如 ConfigFlow 获取属性定义、
     * 无物理设备绑定的 fallback 场景）。应优先使用精确匹配方法
     * {@link #getMapping(String, String, String)} 通过 coordinate + model 确定正确的映射。
     *
     * @param mtype 映射类型（如 "SO2"）
     * @return 该类型下注册顺序的第一个映射实例，无映射则返回 null
     */
    public IDeviceMapping getFirstMappingByType(String mtype) {
        List<IDeviceMapping> mappings = getMappingsByType(mtype);
        return mappings.isEmpty() ? null : mappings.get(0);
    }

    /**
     * 获取指定映射类型下的任意一个设备映射。
     *
     * @deprecated 使用 {@link #getFirstMappingByType(String)} 替代，方法名更明确语义。
     *             或优先使用精确匹配 {@link #getMapping(String, String, String)}。
     * @param mtype 映射类型
     * @return 该类型下的第一个映射实例
     */
    @Deprecated
    public IDeviceMapping getAnyMappingByType(String mtype) {
        return getFirstMappingByType(mtype);
    }
}
