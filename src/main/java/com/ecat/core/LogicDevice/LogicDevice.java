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

import com.ecat.core.ConfigEntry.ConfigEntry;
import com.ecat.core.Device.DeviceBase;
import com.ecat.core.LogicMapping.IDeviceMapping;
import com.ecat.core.LogicMapping.LogicMappingManager;
import com.ecat.core.LogicState.ILogicAttribute;
import com.ecat.core.LogicState.LogicAttributeDefine;
import com.ecat.core.State.AttributeBase;

import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 逻辑设备抽象基类，扩展 DeviceBase，提供逻辑属性管理能力。
 *
 * <p>逻辑设备（LogicDevice）是一种特殊的设备，它的属性不是直接来自物理设备通信，
 * 而是通过 {@link IDeviceMapping} 从一个或多个物理设备的属性映射计算而来。
 * 逻辑设备将不同厂商、不同型号的物理设备统一抽象为标准化的业务设备。
 *
 * <p>核心职责：
 * <ul>
 *   <li><b>属性映射初始化</b>：从 ConfigEntry 的 mappings 配置中读取映射关系，
 *       通过 LogicMappingManager 查找对应的 IDeviceMapping，创建逻辑属性</li>
 *   <li><b>属性定义应用</b>：将 LogicAttributeDefine 中的元数据（单位、精度等）
 *       应用到逻辑属性上</li>
 *   <li><b>设备引用重定向</b>：将逻辑属性的 device 引用从物理设备重定向到
 *       逻辑设备自身，确保总线事件和状态发布的正确归属</li>
 * </ul>
 *
 * <p>初始化流程（由框架调用）：
 * <ol>
 *   <li>{@link #load(com.ecat.core.EcatCore)} - 由框架设置 core 引用</li>
 *   <li>{@link #init()} - 从 mappings 配置创建逻辑属性并设置设备引用</li>
 *   <li>{@link #start()} - 启动设备（子类实现）</li>
 * </ol>
 *
 * <p>ConfigEntry 的 mappings 配置格式示例：
 * <pre>
 *   data:
 *     name: "SO2 Monitor"
 *     mappings:
 *       so2:
 *         device_id: "phy-device-001"
 *       status:
 *         device_id: "phy-device-002"
 * </pre>
 *
 * @see IDeviceMapping
 * @see LogicMappingManager
 * @see ILogicAttribute
 * @author coffee
 */
public abstract class LogicDevice extends DeviceBase {

    /**
     * 逻辑属性映射表：逻辑属性ID -> ILogicAttribute 实例。
     * 使用 LinkedHashMap 保持插入顺序，确保属性按配置顺序展示。
     */
    @Getter
    private Map<String, ILogicAttribute<?>> attrMap;

    /**
     * 静态 LogicMappingManager 引用，由集成模块（LogicdeviceIntegration）在 onLoad 时设置。
     *
     * <p>由于 LogicMappingManager 不是单例（Task 8 设计为普通类），
     * 需要通过静态 setter 注入，使 LogicDevice 可以在 init() 时查找映射。
     */
    private static LogicMappingManager logicMappingManager;

    /**
     * 设置静态 LogicMappingManager 引用。
     * 由 LogicdeviceIntegration.onLoad() 调用。
     *
     * @param mgr 逻辑映射管理器实例
     */
    public static void setLogicMappingManager(LogicMappingManager mgr) {
        logicMappingManager = mgr;
    }

    /**
     * 获取静态 LogicMappingManager 引用。
     *
     * @return 逻辑映射管理器实例，可能为 null（在集成加载前）
     */
    public static LogicMappingManager getLogicMappingManager() {
        return logicMappingManager;
    }

    /**
     * 从 ConfigEntry 构建逻辑设备。
     *
     * @param entry 配置条目，包含 mappings 映射配置
     */
    public LogicDevice(ConfigEntry entry) {
        super(entry);
    }

    /**
     * 初始化逻辑设备，创建逻辑属性映射。
     *
     * <p>此方法在框架的生命周期中被调用（在 load 之后），
     * 依次执行：
     * <ol>
     *   <li>调用 {@link #genAttrMap(ConfigEntry)} 从 mappings 配置创建逻辑属性</li>
     *   <li>调用 {@link #createAttrs()} 将逻辑属性注册到设备的 attrs 中，
     *       并重定向设备引用</li>
     * </ol>
     */
    @Override
    public void init() {
        attrMap = genAttrMap(getEntry());
        createAttrs();
    }

    /**
     * 获取此逻辑设备的属性定义列表。
     * 子类必须实现，返回所有可能的逻辑属性定义。
     * 这些定义用于初始化逻辑属性的元数据（单位、精度、是否可修改等）。
     *
     * @return 属性定义列表，不会为 null
     */
    public abstract List<LogicAttributeDefine> getAttrDefs();

    /**
     * 获取此逻辑设备的映射类型标识。
     * 映射类型对应 LogicMappingManager 中的第一级 key（如 "SO2"、"UPS"）。
     *
     * @return 映射类型标识符
     */
    protected abstract String getMappingType();

    /**
     * 从 ConfigEntry 的 mappings 配置生成逻辑属性映射表。
     *
     * <p>处理流程：
     * <ol>
     *   <li>读取 entry.getData().get("mappings")</li>
     *   <li>遍历每个映射条目，获取 device_id</li>
     *   <li>通过 DeviceRegistry 查找物理设备</li>
     *   <li>通过 LogicMappingManager 查找对应的 IDeviceMapping</li>
     *   <li>调用 mapping.getAttr() 创建逻辑属性</li>
     *   <li>应用 LogicAttributeDefine 元数据</li>
     * </ol>
     *
     * @param entry 配置条目
     * @return 逻辑属性映射表，key 为逻辑属性ID
     */
    @SuppressWarnings("unchecked")
    private Map<String, ILogicAttribute<?>> genAttrMap(ConfigEntry entry) {
        Map<String, ILogicAttribute<?>> result = new LinkedHashMap<>();
        Map<String, Object> data = entry.getData();
        if (data == null) return result;

        Map<String, Object> mappings = (Map<String, Object>) data.get("mappings");
        if (mappings == null) return result;

        for (Map.Entry<String, Object> mappingEntry : mappings.entrySet()) {
            String attrId = mappingEntry.getKey();
            Map<String, Object> attrConfig = (Map<String, Object>) mappingEntry.getValue();
            if (attrConfig == null) continue;

            String deviceId = (String) attrConfig.get("device_id");
            if (deviceId == null || deviceId.isEmpty()) continue;

            // 查找物理设备：通过 core 的 DeviceRegistry
            if (this.core == null) {
                log.warn("LogicDevice core is null, cannot find physical device: {}", deviceId);
                continue;
            }

            DeviceBase phyDevice = this.core.getDeviceRegistry().getDeviceByID(deviceId);
            if (phyDevice == null) {
                log.warn("Physical device not found: {}", deviceId);
                continue;
            }

            // 获取物理设备的 coordinate 和 model
            String coordinate = phyDevice.getEntry().getCoordinate();
            String model = phyDevice.getModel();

            // 查找映射
            String mappingType = getMappingType();
            if (logicMappingManager == null) {
                log.warn("LogicMappingManager is null, cannot find mapping for type={}", mappingType);
                continue;
            }

            IDeviceMapping mapping = logicMappingManager.getMapping(mappingType, coordinate, model);
            if (mapping == null) {
                log.warn("No mapping found for type={}, coord={}, model={}", mappingType, coordinate, model);
                continue;
            }

            // 创建逻辑属性
            ILogicAttribute<?> lAttr = mapping.getAttr(attrId, phyDevice);
            if (lAttr == null) continue;

            // 查找匹配的 LogicAttributeDefine 并应用元数据
            LogicAttributeDefine ladef = findAttrDef(attrId);
            if (ladef != null) {
                lAttr.initFromDefinition(ladef);
            }
            result.put(attrId, lAttr);
        }
        return result;
    }

    /**
     * 在 getAttrDefs() 列表中查找指定 attrId 的定义。
     *
     * @param attrId 逻辑属性ID
     * @return 匹配的定义，未找到则返回 null
     */
    private LogicAttributeDefine findAttrDef(String attrId) {
        for (LogicAttributeDefine def : getAttrDefs()) {
            if (def.getAttrId().equals(attrId)) return def;
        }
        return null;
    }

    /**
     * 将 genAttrMap 创建的逻辑属性注册到设备的 attrs 中。
     *
     * <p>对于每个已定义的逻辑属性（在 getAttrDefs() 中），
     * 如果在 attrMap 中找到了对应的逻辑属性实例，则：
     * <ol>
     *   <li>调用 setAttribute(attr) 将其注册到 DeviceBase 的 attrs map 中</li>
     *   <li>setAttribute 内部会调用 attr.setDevice(this)，将设备引用从物理设备
     *       重定向到逻辑设备自身，确保总线事件和状态发布正确归属到逻辑设备</li>
     * </ol>
     *
     * <p>注意：attrMap 中的属性可能比 getAttrDefs() 中定义的更多或更少。
     * 只有在两者中都存在的属性才会被注册到设备的 attrs 中。
     * attrMap 中存在但 getAttrDefs() 中未定义的属性仍保留在 attrMap 中，
     * 但不注册到 DeviceBase 的 attrs（不参与总线状态发布）。
     */
    private void createAttrs() {
        List<LogicAttributeDefine> defs = getAttrDefs();
        for (LogicAttributeDefine def : defs) {
            ILogicAttribute<?> attr = attrMap.get(def.getAttrId());
            if (attr != null) {
                // CRITICAL: setAttribute(attr) 内部调用 attr.setDevice(this)，
                // 将设备引用从物理设备重定向到逻辑设备。
                // ILogicAttribute 的具体实现类（LNumericAttribute、LAQAttribute 等）
                // 都继承自 AttributeBase，因此可以安全地向上转型。
                if (attr instanceof AttributeBase) {
                    setAttribute((AttributeBase<?>) attr);
                }
            }
            // attr == null means this business attribute is not configured in mappings
        }
    }
}
