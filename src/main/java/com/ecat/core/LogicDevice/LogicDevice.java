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
import com.ecat.core.Device.DeviceStatus;
import com.ecat.core.LogicMapping.IDeviceMapping;
import com.ecat.core.LogicMapping.LogicMappingManager;
import com.ecat.core.LogicState.CommandAttrDef;
import com.ecat.core.LogicState.ILogicAttribute;
import com.ecat.core.LogicState.LCommandAttribute;
import com.ecat.core.LogicState.LNumericAttribute;
import com.ecat.core.LogicState.LStringSelectAttribute;
import com.ecat.core.LogicState.LTextAttribute;
import com.ecat.core.LogicState.LogicAttributeDefine;
import com.ecat.core.LogicState.SetupData;
import com.ecat.core.LogicState.PlaceholderCommandAttribute;
import com.ecat.core.LogicState.PlaceholderLogicAttribute;
import com.ecat.core.LogicState.PlaceholderNumericAttribute;
import com.ecat.core.LogicState.PlaceholderStringSelectAttribute;
import com.ecat.core.LogicState.PlaceholderTextAttribute;
import com.ecat.core.LogicState.StringSelectAttrDef;
import com.ecat.core.State.AttributeBase;
import com.ecat.core.State.AttributeStatus;
import com.ecat.core.State.SelectAttribute;

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
     * 返回逻辑设备的唯一标识（uniqueId），作为业务设备对外标识。
     *
     * <p>覆盖 DeviceBase 默认实现（返回 entryId），使 uniqueId 作为
     * LogicDeviceRegistry 的注册/注销 key，保持语义一致。
     *
     * @return ConfigEntry 的 uniqueId
     */
    @Override
    public String getId() {
        return getUniqueId();
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
        try {
            attrMap = genAttrMap(getEntry());
        } catch (Exception e) {
            log.error("LogicDevice [{}] genAttrMap failed: {}", getId(), e.getMessage(), e);
            throw new RuntimeException("LogicDevice creation failed: " + getId(), e);
        }
        createAttrs();
        setupAttributes();
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
    private Map<String, ILogicAttribute<?>> genAttrMap(ConfigEntry entry) throws Exception {
        Map<String, ILogicAttribute<?>> result = new LinkedHashMap<>();
        this.attrMap = result;  // 提前设置，使 mapping.getAttr() 中可用 getAttrMap() 获取已创建的属性

        // 读取 entry 中的 mappings 配置（物理设备绑定信息）
        Map<String, Object> data = entry.getData();
        Map<String, Object> mappings = null;
        if (data != null) {
            mappings = (Map<String, Object>) data.get("mappings");
        }

        // 记录第一个成功解析的 mapping 和 phyDevice，供所有非 YAML 映射的属性使用
        // 所有属性创建统一委托给 mapping.getAttr()
        IDeviceMapping resolvedMapping = null;
        DeviceBase firstPhyDevice = null;

        // 以 getAttrDefs() 为属性定义来源，以 mapping.getAttr() 为属性创建的唯一入口
        List<LogicAttributeDefine> attrDefs = getAttrDefs();

        for (LogicAttributeDefine def : attrDefs) {
            String attrId = def.getAttrId();
            ILogicAttribute<?> attr = null;
            // 跟踪是否曾向 mapping.getAttr() 传入非 null 的物理设备。
            // 用于区分：设备存在但无对应属性(NORMAL) vs 设备未配置(ALARM)
            boolean triedWithNonNullPhyDevice = false;

            if (mappings != null && mappings.containsKey(attrId)) {
                // YAML 配置了映射 → 用 YAML 指定的物理设备
                Map<String, Object> attrConfig = (Map<String, Object>) mappings.get(attrId);
                String deviceId = attrConfig != null ? (String) attrConfig.get("device_id") : null;

                if (deviceId != null && !deviceId.isEmpty()) {
                    // 用户配置了物理设备 → 解析 phyDevice + mapping
                    if (this.core == null) {
                        throw new RuntimeException("LogicDevice [" + getId() + "] attr '" + attrId
                            + "' mapped to device '" + deviceId + "' but core is null");
                    }
                    DeviceBase phyDevice = this.core.getDeviceRegistry().getDeviceByID(deviceId);
                    if (phyDevice == null) {
                        throw new RuntimeException("LogicDevice [" + getId() + "] attr '" + attrId
                            + "' mapped to device '" + deviceId + "' but device not found");
                    }

                    String coordinate = phyDevice.getEntry().getCoordinate();
                    String model = phyDevice.getModel();
                    String mappingType = getMappingType();

                    if (logicMappingManager == null) {
                        throw new RuntimeException("LogicMappingManager is null, cannot find mapping for type=" + mappingType);
                    }

                    IDeviceMapping mapping = logicMappingManager.getMapping(mappingType, coordinate, model);
                    if (mapping != null) {
                        if (resolvedMapping == null) {
                            resolvedMapping = mapping;
                            firstPhyDevice = phyDevice;
                        }
                        triedWithNonNullPhyDevice = true;
                        attr = mapping.getAttr(attrId, phyDevice, this);
                    }
                }
                // device_id 为 null（用户选择"无物理设备"）→ fall through，mapping 创建 standalone 属性
            }

            // 如果 YAML 映射未创建属性，统一委托给 resolvedMapping
            // mapping 负责所有属性的创建：standalone、computed、alarm、command 等
            if (attr == null && resolvedMapping != null) {
                if (firstPhyDevice != null) {
                    triedWithNonNullPhyDevice = true;
                }
                attr = resolvedMapping.getAttr(attrId, firstPhyDevice, this);
            } else if (attr == null && resolvedMapping == null && logicMappingManager != null) {
                // 没有通过物理设备解析到 mapping，尝试按类型查找任意 mapping
                // 处理场景：YAML 有映射配置但 device_id 为空（用户选择"无物理设备"），
                // 仍需 mapping 来创建 standalone 属性
                IDeviceMapping fallbackMapping = logicMappingManager.getFirstMappingByType(getMappingType());
                if (fallbackMapping != null) {
                    resolvedMapping = fallbackMapping;
                    attr = resolvedMapping.getAttr(attrId, firstPhyDevice, this);
                }
            }

            // 对 mappable 属性，当 mapping.getAttr() 返回 null 时创建占位属性：
            // - 如果曾传入非 null phyDevice → 设备存在但无对应属性 → Blank (NORMAL)
            // - 如果从未传入 phyDevice → 设备未配置 → Placeholder (ALARM)
            if (attr == null && def.isMapable()) {
                if (triedWithNonNullPhyDevice) {
                    attr = createBlankAttr(attrId, def);
                } else {
                    attr = createPlaceholderAttr(attrId, def);
                }
            }

            // 注入元数据 + 特殊 def 类型处理
            if (attr != null) {
                attr.initFromDefinition(def);
                // CommandAttrDef: 注入命令列表
                if (def instanceof CommandAttrDef && attr instanceof LCommandAttribute) {
                    List<String> commands = ((CommandAttrDef) def).getCommands();
                    if (commands != null) {
                        ((LCommandAttribute) attr).setCommandsFromDef(commands);
                    }
                }
                // StringSelectAttrDef: 注入选项列表
                if (def instanceof StringSelectAttrDef && attr instanceof LStringSelectAttribute) {
                    List<String> options = ((StringSelectAttrDef) def).getOptions();
                    if (options != null) {
                        ((LStringSelectAttribute) attr).setOptionsFromDef(options);
                    }
                }
                result.put(attrId, attr);
            }
        }
        return result;
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

    /**
     * 在所有属性创建完成、持久化值恢复后，统一触发每个属性的初始化回调。
     *
     * <p>遍历 attrMap，对每个 ILogicAttribute 调用
     * {@link ILogicAttribute#setupAfterDeviceAttrsCreated(SetupData)}。
     * 可安全重复调用（计算逻辑幂等）。
     *
     * <p>对于 {@code StationLogicDevice}，需要在 {@code createStatusAttrs()} 之后
     * 再次调用此方法，以确保状态属性（alarm_status、running_status 等）也触发 setup。
     */
    public void setupAttributes() {
        if (attrMap == null) return;
        SetupData data = new SetupData();
        for (ILogicAttribute<?> attr : attrMap.values()) {
            attr.setupAfterDeviceAttrsCreated(data);
        }
    }

    // ---- Device Status Aggregation ----

    /**
     * 聚合逻辑属性状态为设备级状态。
     *
     * <p>优先级链：
     * <ol>
     *   <li>manual_status != Normal → 映射为设备状态</li>
     *   <li>online_status == offline → UNKNOWN</li>
     *   <li>alarm_status == alarm → ALARM</li>
     *   <li>running_status → 映射为设备状态</li>
     * </ol>
     */
    @Override
    protected DeviceStatus computeDeviceStatus() {
        return computeAggregatedStatus();
    }

    private DeviceStatus computeAggregatedStatus() {
        // Priority 1: manual_status — if not Normal, it takes precedence
        String manual = getStatusAttrValue("manual_status");
        if (manual != null && !"Normal".equals(manual)) {
            return mapAttributeStatusToDeviceStatus(AttributeStatus.getEnum(manual));
        }

        // Priority 2: online_status — offline means device unreachable
        String online = getStatusAttrValue("online_status");
        if ("offline".equals(online)) {
            return DeviceStatus.UNKNOWN;
        }

        // Priority 3: alarm_status
        String alarm = getStatusAttrValue("alarm_status");
        if ("alarm".equals(alarm)) {
            return DeviceStatus.ALARM;
        }

        // Priority 4: running_status — map to device status
        String running = getStatusAttrValue("running_status");
        if (running != null) {
            return mapAttributeStatusToDeviceStatus(AttributeStatus.getEnum(running));
        }

        return DeviceStatus.UNKNOWN;
    }

    /**
     * 从 attrs 中读取状态属性当前值。
     *
     * @param attrId 属性ID（如 "online_status"、"running_status"）
     * @return 当前选项值的字符串形式，属性不存在或值为 null 时返回 null
     */
    private String getStatusAttrValue(String attrId) {
        AttributeBase<?> attr = getAttrs().get(attrId);
        if (attr instanceof SelectAttribute) {
            Object option = ((SelectAttribute<?>) attr).getCurrentOption();
            return option != null ? option.toString() : null;
        }
        return null;
    }

    // ==================== 属性替换方法 ====================

    /**
     * 替换指定 attrId 的属性为 placeholder（standalone、ALARM 状态）。
     * 用于物理设备离线时保留属性槽位，防止内存泄漏和悬垂引用。
     *
     * @param attrId 要替换的逻辑属性ID
     */
    public void replaceAttrWithPlaceholder(String attrId) {
        ILogicAttribute<?> existing = attrMap.get(attrId);
        if (existing == null) return;

        existing.dispose();

        attrMap.remove(attrId);
        getAttrs().remove(attrId);

        LogicAttributeDefine def = findAttrDef(attrId);
        if (def != null && def.isMapable()) {
            ILogicAttribute<?> placeholder = createPlaceholderAttr(attrId, def);
            if (placeholder != null) {
                attrMap.put(attrId, placeholder);
                setAttribute((AttributeBase<?>) placeholder);
            }
        }
    }

    /**
     * 将 placeholder attr 替换为真实绑定的 attr。
     *
     * @param attrId 属性ID
     * @param phyDevice 新的物理设备实例
     * @return true 如果替换成功
     */
    public boolean replacePlaceholderWithRealAttr(String attrId, DeviceBase phyDevice) {
        ILogicAttribute<?> existing = attrMap.get(attrId);
        if (existing == null) return false;

        existing.dispose();

        attrMap.remove(attrId);
        getAttrs().remove(attrId);

        ILogicAttribute<?> realAttr = createAttrFromMapping(attrId, phyDevice);
        if (realAttr != null) {
            LogicAttributeDefine def = findAttrDef(attrId);
            if (def != null) {
                realAttr.initFromDefinition(def);
            }
            attrMap.put(attrId, realAttr);
            setAttribute((AttributeBase<?>) realAttr);
            return true;
        }
        return false;
    }

    /**
     * 查找设备上所有处于 placeholder 状态的 attr。
     *
     * @return attrId -> 该 attr 等待绑定的 physical device uniqueId
     */
    @SuppressWarnings("unchecked")
    public Map<String, String> findPlaceholderAttrs() {
        Map<String, String> result = new LinkedHashMap<>();
        Map<String, Object> data = getEntry().getData();
        if (data == null) return result;
        Map<String, Object> mappings = (Map<String, Object>) data.get("mappings");
        if (mappings == null) return result;

        for (Map.Entry<String, ILogicAttribute<?>> entry : attrMap.entrySet()) {
            String attrId = entry.getKey();
            ILogicAttribute<?> attr = entry.getValue();
            if (isPlaceholder(attr)) {
                Map<String, Object> mappingData = (Map<String, Object>) mappings.get(attrId);
                if (mappingData != null && mappingData.get("device_id") != null) {
                    result.put(attrId, (String) mappingData.get("device_id"));
                }
            }
        }
        return result;
    }

    /**
     * 判断一个逻辑属性是否是 placeholder。
     * Placeholder 的特征：{@link ILogicAttribute#isPlaceholder()} 返回 true。
     */
    private boolean isPlaceholder(ILogicAttribute<?> attr) {
        return attr != null && attr.isPlaceholder();
    }

    /**
     * 从 getAttrDefs() 中查找指定 attrId 的定义。
     */
    private LogicAttributeDefine findAttrDef(String attrId) {
        for (LogicAttributeDefine def : getAttrDefs()) {
            if (attrId.equals(def.getAttrId())) {
                return def;
            }
        }
        return null;
    }

    /**
     * 使用 mapping 创建真实属性。
     */
    private ILogicAttribute<?> createAttrFromMapping(String attrId, DeviceBase phyDevice) {
        if (logicMappingManager == null) return null;

        String mappingType = getMappingType();
        String coordinate = phyDevice.getEntry().getCoordinate();
        String model = phyDevice.getModel();
        IDeviceMapping mapping = logicMappingManager.getMapping(mappingType, coordinate, model);

        if (mapping == null) {
            mapping = logicMappingManager.getFirstMappingByType(mappingType);
        }

        if (mapping != null) {
            try {
                return mapping.getAttr(attrId, phyDevice, this);
            } catch (Exception e) {
                log.error("LogicDevice [{}] createAttrFromMapping failed for attr '{}': {}",
                    getId(), attrId, e.getMessage(), e);
                return null;
            }
        }
        return null;
    }

    /**
     * 创建占位属性（Placeholder，ALARM 状态）。
     *
     * <p>当 mappable 属性没有映射到物理设备（设备未配置/未找到）时调用，
     * 创建一个带 ALARM 状态的占位属性，确保逻辑设备的属性集完整。
     * 占位属性在 reconfigure 绑定物理设备后可被替换。
     *
     * <p>返回的属性实现了 {@link PlaceholderLogicAttribute} 接口，
     * {@link PlaceholderLogicAttribute#getPlaceholderKind()} 返回
     * {@link PlaceholderLogicAttribute.Kind#ALARM_MISSING_DEVICE}。
     *
     * @param attrId 逻辑属性ID
     * @param def 属性定义，包含类型、单位、选项等信息
     * @return ALARM 状态的占位属性，如果无法识别类型则返回 null
     */
    private ILogicAttribute<?> createPlaceholderAttr(String attrId, LogicAttributeDefine def) {
        if (def instanceof StringSelectAttrDef) {
            return PlaceholderStringSelectAttribute.createAlarm(attrId, def.getAttrClass(),
                ((StringSelectAttrDef) def).getOptions());
        } else if (def instanceof CommandAttrDef) {
            return PlaceholderCommandAttribute.createAlarm(attrId, def.getAttrClass(),
                ((CommandAttrDef) def).getCommands());
        } else if (def.getAttrClassType() == LNumericAttribute.class) {
            return PlaceholderNumericAttribute.createAlarm(attrId, def.getAttrClass(),
                def.getNativeUnit(), def.getDisplayUnit(), def.getDisplayPrecision());
        } else if (def.getAttrClassType() == LTextAttribute.class) {
            return PlaceholderTextAttribute.createAlarm(attrId, def.getAttrClass());
        }
        return null;
    }

    /**
     * 创建空白占位属性（Blank，NORMAL 状态）。
     *
     * <p>当 mappable 属性的物理设备已找到，但该设备没有对应的物理属性时调用。
     * 创建一个带 NORMAL 状态的占位属性，表示该属性对当前设备不可用但设备正常。
     *
     * <p>返回的属性实现了 {@link PlaceholderLogicAttribute} 接口，
     * {@link PlaceholderLogicAttribute#getPlaceholderKind()} 返回
     * {@link PlaceholderLogicAttribute.Kind#NORMAL_NO_ATTR}。
     *
     * @param attrId 逻辑属性ID
     * @param def 属性定义，包含类型、单位、选项等信息
     * @return NORMAL 状态的空白占位属性，如果无法识别类型则返回 null
     */
    private ILogicAttribute<?> createBlankAttr(String attrId, LogicAttributeDefine def) {
        if (def instanceof StringSelectAttrDef) {
            return PlaceholderStringSelectAttribute.createBlank(attrId, def.getAttrClass(),
                ((StringSelectAttrDef) def).getOptions());
        } else if (def instanceof CommandAttrDef) {
            return PlaceholderCommandAttribute.createBlank(attrId, def.getAttrClass(),
                ((CommandAttrDef) def).getCommands());
        } else if (def.getAttrClassType() == LNumericAttribute.class) {
            return PlaceholderNumericAttribute.createBlank(attrId, def.getAttrClass(),
                def.getNativeUnit(), def.getDisplayUnit(), def.getDisplayPrecision());
        } else if (def.getAttrClassType() == LTextAttribute.class) {
            return PlaceholderTextAttribute.createBlank(attrId, def.getAttrClass());
        }
        return null;
    }
}
