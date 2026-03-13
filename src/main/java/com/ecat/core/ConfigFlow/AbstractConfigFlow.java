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
import com.ecat.core.ConfigEntry.SourceType;
import com.ecat.core.I18n.I18nHelper;
import com.ecat.core.I18n.I18nProxy;
import com.ecat.core.Utils.Log;
import com.ecat.core.Utils.LogFactory;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * 配置流程基类
 *
 * <p>约定：step_user 入口，step_create_entry 出口
 * <p>使用 registerStep() 注册步骤处理器和显示信息
 * <p>支持入口步骤机制：registerStepUser()、registerStepReconfigure()、registerStepDiscovery()
 *
 * <p>使用方法：
 * <ol>
 *   <li>继承此类</li>
 *   <li>在构造函数中使用 {@link #registerStep(String, Function)} 注册步骤</li>
 *   <li>使用 {@link #registerStepUser(String, String, BiFunction)} 注册用户入口步骤</li>
 *   <li>使用 {@link #showForm(String, com.ecat.core.ConfigFlow.ConfigSchema, Map)} 显示表单</li>
 *   <li>使用 {@link #createEntry()} 完成流程</li>
 * </ol>
 *
 * <p>示例：
 * <pre>{@code
 * class DemoConfigFlow extends AbstractConfigFlow {
 *
 *     public DemoConfigFlow(String flowId) {
 *         super(flowId);
 *         registerStepUser("user", "用户配置", this::handleUserStep);
 *         registerStep("device_config", this::stepDeviceConfig, "设备基本配置");
 *         registerStep("create_entry", this::stepCreateEntry, "完成");
 *     }
 *
 *     private ConfigFlowResult handleUserStep(Map<String, Object> userInput, FlowContext ctx) {
 *         if (userInput == null || userInput.isEmpty()) {
 *             return showForm("user", createUserSchema(), new HashMap<>());
 *         }
 *         getData().putAll(userInput);
 *         return showForm("device_config", createDeviceSchema(), new HashMap<>());
 *     }
 *
 *     // ... 其他步骤方法
 * }
 * }</pre>
 *
 * @author coffee
 */
public abstract class AbstractConfigFlow {

    /**
     * i18n Key 前缀
     */
    private static final String PREFIX = "config_flow";

    /**
     * 日志实例
     */
    protected final Log log = LogFactory.getLogger(getClass());

    /**
     * 流程实例 ID
     */
    protected final String flowId;

    /**
     * 流程上下文（唯一数据源）
     */
    protected FlowContext context;

    /**
     * Flow 注册表
     */
    protected ConfigFlowRegistry registry;

    /**
     * 步骤定义映射（处理器 + 显示信息）
     */
    protected final Map<String, StepDefinition> stepDefinitions = new LinkedHashMap<>();

    // ========== 入口步骤机制 ==========

    /**
     * 来源类型
     */
    protected SourceType sourceType = SourceType.USER;

    /**
     * 重配置时的 entryId
     */
    protected String reconfigureEntryId;

    /**
     * 用户入口步骤 (每类只能注册一个)
     */
    protected EntryStepDefinition userStep;

    /**
     * 重配置入口步骤 (每类只能注册一个)
     */
    protected EntryStepDefinition reconfigureStep;

    /**
     * 发现入口步骤 (可选，每类只能注册一个)
     */
    protected EntryStepDefinition discoveryStep;

    /**
     * 当前步骤 ID
     */
    @Getter
    protected String currentStep = "user";

    /**
     * 步骤历史记录
     */
    private List<String> stepHistory = new ArrayList<>();

    /**
     * I18n 代理实例
     * 用于获取翻译资源
     */
    protected final I18nProxy i18n;

    /**
     * 构造函数
     *
     * @param flowId 流程实例 ID
     */
    protected AbstractConfigFlow(String flowId) {
        this.flowId = flowId;
        this.i18n = I18nHelper.createProxy(this.getClass());
    }

    /**
     * 获取流程实例 ID
     * <p>
     * 优先从 context 获取，如果 context 未设置则返回构造函数传入的 flowId。
     *
     * @return 流程实例 ID
     */
    public String getFlowId() {
        return context != null ? context.getFlowId() : flowId;
    }

    // ========== Step 注册 API ==========

    /**
     * 注册步骤（只注册处理器，无显示信息）
     *
     * @param stepId 步骤 ID
     * @param handler 步骤处理函数
     */
    protected void registerStep(String stepId,
                                 Function<Map<String, Object>, ConfigFlowResult> handler) {
        stepDefinitions.put(stepId, new StepDefinition(handler, null));
    }

    /**
     * 注册步骤（带显示名称）
     *
     * @param stepId 步骤 ID
     * @param handler 步骤处理函数
     * @param displayName 显示名称
     */
    protected void registerStep(String stepId,
                                 Function<Map<String, Object>, ConfigFlowResult> handler,
                                 String displayName) {
        stepDefinitions.put(stepId, new StepDefinition(handler, StepInfo.of(displayName)));
    }

    /**
     * 注册步骤（带完整 StepInfo）
     *
     * @param stepId 步骤 ID
     * @param handler 步骤处理函数
     * @param stepInfo 步骤信息
     */
    protected void registerStep(String stepId,
                                 Function<Map<String, Object>, ConfigFlowResult> handler,
                                 StepInfo stepInfo) {
        stepDefinitions.put(stepId, new StepDefinition(handler, stepInfo));
    }

    // ========== 入口步骤注册 ==========

    /**
     * 注册用户入口步骤 (每个 Flow 只能注册一个)
     *
     * @param stepId 步骤 ID
     * @param displayName 显示名称
     * @param handler 处理器 (接收 userInput 和 context)
     */
    protected void registerStepUser(String stepId, String displayName,
                                    BiFunction<Map<String, Object>, FlowContext, ConfigFlowResult> handler) {
        this.userStep = new EntryStepDefinition(stepId, displayName, handler);
        // 同时加入 stepDefinitions，这样 handleStep() 能自然找到
        stepDefinitions.put(stepId, new StepDefinition(
            input -> handler.apply(input, context),
            StepInfo.of(displayName)
        ));
        log.debug("Registered user entry step: {}", stepId);
    }

    /**
     * 注册重配置入口步骤 (每个 Flow 只能注册一个)
     *
     * @param stepId 步骤 ID
     * @param displayName 显示名称
     * @param handler 处理器 (接收 userInput 和 context)
     */
    protected void registerStepReconfigure(String stepId, String displayName,
                                           BiFunction<Map<String, Object>, FlowContext, ConfigFlowResult> handler) {
        this.reconfigureStep = new EntryStepDefinition(stepId, displayName, handler);
        // 同时加入 stepDefinitions，这样 handleStep() 能自然找到
        stepDefinitions.put(stepId, new StepDefinition(
            input -> handler.apply(input, context),
            StepInfo.of(displayName)
        ));
        log.debug("Registered reconfigure entry step: {}", stepId);
    }

    /**
     * 注册发现入口步骤 (可选，每个 Flow 只能注册一个)
     *
     * @param stepId 步骤 ID
     * @param displayName 显示名称
     * @param handler 处理器 (接收 userInput 和 context)
     */
    protected void registerStepDiscovery(String stepId, String displayName,
                                         BiFunction<Map<String, Object>, FlowContext, ConfigFlowResult> handler) {
        this.discoveryStep = new EntryStepDefinition(stepId, displayName, handler);
        // 同时加入 stepDefinitions，这样 handleStep() 能自然找到
        stepDefinitions.put(stepId, new StepDefinition(
            input -> handler.apply(input, context),
            StepInfo.of(displayName)
        ));
        log.debug("Registered discovery entry step: {}", stepId);
    }

    // ========== 能力查询接口 ==========

    /**
     * 是否支持用户入口
     */
    public boolean hasUserStep() {
        return userStep != null;
    }

    /**
     * 是否支持重配置入口
     */
    public boolean hasReconfigureStep() {
        return reconfigureStep != null;
    }

    /**
     * 是否支持发现入口
     */
    public boolean hasDiscoveryStep() {
        return discoveryStep != null;
    }

    // ========== 入口获取方法 ==========

    /**
     * 获取用户入口步骤
     */
    public EntryStepDefinition getUserStep() {
        return userStep;
    }

    /**
     * 获取重配置入口步骤
     */
    public EntryStepDefinition getReconfigureStep() {
        return reconfigureStep;
    }

    /**
     * 获取发现入口步骤
     */
    public EntryStepDefinition getDiscoveryStep() {
        return discoveryStep;
    }

    // ========== SourceType 管理 ==========

    /**
     * 设置来源类型
     */
    public void setSourceType(SourceType type) {
        this.sourceType = type;
    }

    /**
     * 获取来源类型
     */
    public SourceType getSourceType() {
        return sourceType;
    }

    /**
     * 设置重配置 entryId
     */
    public void setReconfigureEntryId(String entryId) {
        this.reconfigureEntryId = entryId;
    }

    // ========== 入口执行方法 ==========

    /**
     * 执行用户入口步骤
     *
     * @param userInput 用户输入数据
     * @return 流程结果
     * @throws IllegalStateException 如果用户入口未注册
     */
    public ConfigFlowResult executeUserStep(Map<String, Object> userInput) {
        if (userStep == null) {
            throw new IllegalStateException("User entry step not registered");
        }
        return userStep.getHandler().apply(userInput, context);
    }

    /**
     * 执行重配置入口步骤
     *
     * @param entryId 配置条目 ID
     * @param userInput 用户输入数据
     * @return 流程结果
     * @throws IllegalStateException 如果重配置入口未注册
     */
    public ConfigFlowResult executeReconfigureStep(String entryId, Map<String, Object> userInput) {
        if (reconfigureStep == null) {
            throw new IllegalStateException("Reconfigure entry step not registered");
        }
        this.reconfigureEntryId = entryId;
        this.sourceType = SourceType.RECONFIGURE;
        return reconfigureStep.getHandler().apply(userInput, context);
    }

    // ========== 步骤信息获取 ==========

    /**
     * 获取步骤信息映射 - 从注册信息自动生成
     * 子类不需要覆盖此方法
     *
     * @return 步骤信息映射
     */
    public Map<String, StepInfo> getStepInfos() {
        Map<String, StepInfo> infos = new LinkedHashMap<>();
        for (Map.Entry<String, StepDefinition> entry : stepDefinitions.entrySet()) {
            if (entry.getValue().getStepInfo() != null) {
                infos.put(entry.getKey(), entry.getValue().getStepInfo());
            }
        }
        return infos;
    }

    /**
     * 获取步骤显示名称
     * 优先级：i18n 资源 -> 注册的 StepInfo -> stepId
     *
     * @param stepId 步骤 ID
     * @return 步骤显示名称
     */
    public String getStepDisplayName(String stepId) {
        // 1. 优先从 i18n 资源获取
        if (i18n != null) {
            String key = PREFIX + ".step_" + stepId + ".display_name";
            String translated = i18n.t(key);
            if (!translated.equals(key)) {
                return translated;
            }
        }

        // 2. 从注册的 StepInfo 获取
        StepDefinition definition = stepDefinitions.get(stepId);
        if (definition != null && definition.getStepInfo() != null) {
            String displayName = definition.getStepInfo().getDisplayName();
            if (displayName != null) {
                return displayName;
            }
        }

        // 3. 兜底：返回 stepId
        return stepId;
    }

    // ========== 上下文管理 ==========

    /**
     * 设置流程上下文
     *
     * @param context 流程上下文
     */
    public void setContext(FlowContext context) {
        this.context = context;
    }

    /**
     * 获取流程上下文
     *
     * @return 流程上下文
     */
    public FlowContext getContext() {
        return context;
    }

    /**
     * 设置 Flow 注册表
     *
     * @param registry Flow 注册表
     */
    public void setRegistry(ConfigFlowRegistry registry) {
        this.registry = registry;
    }

    /**
     * 获取流程数据（便捷方法）
     *
     * @return 流程数据映射
     */
    protected Map<String, Object> getData() {
        if (context != null) {
            return context.getData();
        }
        // 兼容旧代码：如果没有 context，返回空 Map
        return new HashMap<>();
    }

    // ========== 辅助方法 ==========

    /**
     * 显示表单（使用新版 ConfigSchema）
     *
     * <p>框架自动处理步骤数据持久化：
     * <ul>
     *   <li>context.getData() 已包含 step_inputs 中的步骤数据</li>
     *   <li>前端从 data.step_inputs[stepId] 读取已填写内容</li>
     *   <li>支持步骤漫游时数据不丢失</li>
     * </ul>
     *
     * @param stepId 步骤 ID
     * @param schema 配置 Schema
     * @param errors 错误信息
     * @return SHOW_FORM 类型结果
     */
    protected ConfigFlowResult showForm(String stepId, com.ecat.core.ConfigFlow.ConfigSchema schema,
                                         Map<String, Object> errors) {
        // context.getData() 已包含 step_inputs，前端从 step_inputs[stepId] 读取
        // 框架自动在 handleStep() 中保存数据到 step_inputs
        return ConfigFlowResult.showForm(stepId, schema, errors, context);
    }

    /**
     * 创建配置条目 (根据 sourceType 决定行为)
     * <p>
     * 从 context 获取 coordinate 和数据，根据 sourceType 决定创建或更新模式。
     *
     * @return CREATE_ENTRY 类型结果
     */
    protected ConfigFlowResult createEntry() {
        // 从 context 获取 coordinate (由 ConfigFlowRegistry 设置)
        String coordinate = context.getCoordinate();

        // 构建 entry 数据
        ConfigEntry.Builder builder = new ConfigEntry.Builder()
                .coordinate(coordinate)
                .data(new HashMap<>(getData()));

        // 从 data 中获取 title 和 uniqueId
        if (getData().containsKey("title")) {
            builder.title((String) getData().get("title"));
        }
        if (getData().containsKey("uniqueId")) {
            builder.uniqueId((String) getData().get("uniqueId"));
        }

        if (sourceType == SourceType.RECONFIGURE && reconfigureEntryId != null) {
            // 更新模式: 需要保留原 entryId
            builder.entryId(reconfigureEntryId);
            return ConfigFlowResult.updateEntry(builder.build(), context);
        } else {
            // 创建模式
            return ConfigFlowResult.createEntry(builder.build(), context);
        }
    }

    /**
     * 统一的步骤处理入口 - 只查找注册的处理器
     * <p>
     * Public 方法，允许 ConfigFlowService 从外部调用。
     * <p>
     * 框架自动处理步骤数据持久化：
     * <ul>
     *   <li>当 userInput 非空时，自动保存到 step_inputs[stepId]</li>
     *   <li>子类无需手动调用 saveStepData()</li>
     * </ul>
     *
     * @param stepId 步骤 ID
     * @param userInput 用户输入数据
     * @return 配置流程结果
     */

    public ConfigFlowResult handleStep(String stepId, Map<String, Object> userInput) {
        // 记录步骤历史
        if (!stepHistory.contains(stepId)) {
            stepHistory.add(stepId);
        }
        this.currentStep = stepId;
        if (context != null) {
            context.setCurrentStep(stepId);
        }

        // 框架自动保存步骤数据（用户提交时）
        // 注意：在处理器调用前保存，确保即使验证失败，数据也不丢失
        if (userInput != null && !userInput.isEmpty()) {
            saveStepData(stepId, userInput);
        }

        // 统一从 stepDefinitions 查找（包括 entry steps 和普通 steps）
        StepDefinition definition = stepDefinitions.get(stepId);
        if (definition == null) {
            return ConfigFlowResult.abort("Unknown step: " + stepId);
        }

        ConfigFlowResult result = definition.getHandler().apply(userInput);

        // 如果返回的是 SHOW_FORM，更新当前步骤为显示的步骤
        if (result.getType() == ConfigFlowResult.ResultType.SHOW_FORM) {
            String displayStepId = result.getStepId();
            if (displayStepId != null && !displayStepId.equals(this.currentStep)) {
                this.currentStep = displayStepId;
                if (context != null) {
                    context.setCurrentStep(displayStepId);
                }
                if (!stepHistory.contains(displayStepId)) {
                    stepHistory.add(displayStepId);
                }
            }
        }

        return result;
    }

    /**
     * 获取 Flow 实例（共享上下文）
     *
     * @param flowClass Flow 类
     * @param <T> Flow 类型
     * @return Flow 实例
     */
    protected <T extends AbstractConfigFlow> T getFlow(Class<T> flowClass) {
        if (registry == null) {
            throw new IllegalStateException("Registry not set");
        }
        return registry.getFlow(() -> {
            try {
                return flowClass.getDeclaredConstructor(String.class).newInstance(flowId);
            } catch (Exception e) {
                throw new RuntimeException("Failed to create flow: " + flowClass, e);
            }
        }, context);
    }

    // ========== 兼容旧方法（保留以不破坏现有代码） ==========

    /**
     * 中止流程
     *
     * @param reason 中止原因
     * @return ABORT 类型结果
     */
    protected final ConfigFlowResult abort(String reason) {
        return ConfigFlowResult.abort(reason);
    }

    /**
     * 保存步骤数据到独立命名空间
     *
     * @param stepId 步骤 ID
     * @param userInput 用户输入数据
     */
    protected void saveStepData(String stepId, Map<String, Object> userInput) {
        Map<String, Object> flowData = getFlowDataMap();
        synchronized (flowData) {
            @SuppressWarnings("unchecked")
            Map<String, Object> stepInputs = (Map<String, Object>)
                flowData.computeIfAbsent("step_inputs", k -> new HashMap<>());

            stepInputs.put(stepId, new HashMap<>(userInput));
        }
    }

    /**
     * 获取特定步骤的用户输入
     *
     * @param stepId 步骤 ID
     * @return 该步骤的用户输入数据副本
     */
    @SuppressWarnings("unchecked")
    protected Map<String, Object> getStepData(String stepId) {
        Map<String, Object> flowData = getFlowDataMap();
        synchronized (flowData) {
            Map<String, Object> stepInputs = (Map<String, Object>) flowData.get("step_inputs");
            if (stepInputs != null && stepInputs.containsKey(stepId)) {
                return new HashMap<>((Map<String, Object>) stepInputs.get(stepId));
            }
            return new HashMap<>();
        }
    }

    /**
     * 获取当前步骤的用户输入
     *
     * @return 当前步骤的用户输入数据
     */
    protected Map<String, Object> getCurrentStepData() {
        return getStepData(currentStep);
    }

    /**
     * 获取上一步的 ID
     *
     * @return 上一步的 ID，如果是第一步则返回 null
     */
    public String getPreviousStep() {
        int currentIndex = stepHistory.indexOf(currentStep);
        if (currentIndex > 0) {
            return stepHistory.get(currentIndex - 1);
        }
        return null;
    }

    /**
     * 回退到上一步
     *
     * @throws IllegalStateException 如果已经是第一步
     */
    public void goToPreviousStep() {
        String previousStepId = getPreviousStep();
        if (previousStepId == null) {
            throw new IllegalStateException("已经是第一步，无法回退");
        }

        this.currentStep = previousStepId;
        if (context != null) {
            context.setCurrentStep(previousStepId);
        }

        int currentIndex = stepHistory.indexOf(previousStepId);
        if (currentIndex >= 0 && currentIndex < stepHistory.size() - 1) {
            stepHistory = new ArrayList<>(stepHistory.subList(0, currentIndex + 1));
        }
    }

    /**
     * 获取步骤历史
     *
     * @return 步骤历史列表的副本
     */
    public List<String> getStepHistory() {
        return new ArrayList<>(stepHistory);
    }

    /**
     * 获取流程数据中的值
     *
     * @param key 键
     * @return 值，不存在时返回 null
     */
    protected Object getFlowData(String key) {
        return getFlowDataMap().get(key);
    }

    /**
     * 设置流程数据
     *
     * @param key 键
     * @param value 值
     */
    protected void setFlowData(String key, Object value) {
        getFlowDataMap().put(key, value);
    }

    /**
     * 获取流程数据中的值（带类型和默认值）
     *
     * @param key 键
     * @param type 期望的类型
     * @param defaultValue 默认值
     * @param <T> 值的类型
     * @return 值，不存在或类型不匹配时返回默认值
     */
    protected <T> T getFlowData(String key, Class<T> type, T defaultValue) {
        Object value = getFlowDataMap().get(key);
        return value != null && type.isInstance(value) ? type.cast(value) : defaultValue;
    }

    /**
     * 获取流程数据映射（兼容旧代码）
     *
     * @return 流程数据映射
     */
    private Map<String, Object> getFlowDataMap() {
        if (context != null) {
            return context.getData();
        }
        // 兼容旧代码：如果没有 context，返回临时的 Map
        // 注意：这里使用线程本地存储以保证并发安全
        return flowDataHolder.get();
    }

    /**
     * 临时流程数据存储（兼容旧代码）
     */
    private static final ThreadLocal<Map<String, Object>> flowDataHolder =
            ThreadLocal.withInitial(() -> new ConcurrentHashMap<>());

    // ========== I18n 约定方法 ==========

    /**
     * 获取字段显示名称
     * <p>
     * 优先级：i18n 翻译 -> 返回 fieldKey（由 SchemaConversionService 回退到 item.getDisplayName()）
     *
     * @param stepId 步骤 ID
     * @param fieldKey 字段键
     * @return 字段显示名称，找不到 i18n 翻译时返回 fieldKey
     */
    public String getFieldDisplayName(String stepId, String fieldKey) {
        if (i18n != null) {
            String key = PREFIX + ".step_" + stepId + ".items." + fieldKey + ".display_name";
            String translated = i18n.t(key);
            if (!translated.equals(key)) {
                return translated;
            }
        }
        // 返回 null 表示找不到 i18n 翻译，让调用者回退到 item.getDisplayName()
        return null;
    }

    /**
     * 获取字段占位符
     *
     * @param stepId 步骤 ID
     * @param fieldKey 字段键
     * @return 字段占位符，找不到时返回 null
     */
    public String getFieldPlaceholder(String stepId, String fieldKey) {
        if (i18n != null) {
            String key = PREFIX + ".step_" + stepId + ".items." + fieldKey + ".placeholder";
            String translated = i18n.t(key);
            if (!translated.equals(key)) {
                return translated;
            }
        }
        return null;
    }

    /**
     * 获取字段描述
     *
     * @param stepId 步骤 ID
     * @param fieldKey 字段键
     * @return 字段描述，找不到时返回 null
     */
    public String getFieldDescription(String stepId, String fieldKey) {
        if (i18n != null) {
            String key = PREFIX + ".step_" + stepId + ".items." + fieldKey + ".description";
            String translated = i18n.t(key);
            if (!translated.equals(key)) {
                return translated;
            }
        }
        return null;
    }

    /**
     * 获取选项显示名称
     *
     * @param stepId 步骤 ID
     * @param fieldKey 字段键
     * @param optionValue 选项值
     * @return 选项显示名称
     */
    public String getOptionDisplayName(String stepId, String fieldKey, String optionValue) {
        if (i18n != null) {
            String key = PREFIX + ".step_" + stepId + ".items." + fieldKey + ".options." + optionValue;
            String translated = i18n.t(key);
            if (!translated.equals(key)) {
                return translated;
            }
        }
        return optionValue;
    }

    /**
     * 获取带翻译的选项 Map
     *
     * @param stepId 步骤 ID
     * @param fieldKey 字段键
     * @param options 原始选项 Map (value -> label)
     * @return 翻译后的选项 Map (value -> translated label)
     */
    public Map<String, String> getTranslatedOptions(String stepId, String fieldKey, Map<String, String> options) {
        Map<String, String> translated = new LinkedHashMap<>();
        if (options == null) {
            return translated;
        }
        for (Map.Entry<String, String> entry : options.entrySet()) {
            translated.put(entry.getKey(), getOptionDisplayName(stepId, fieldKey, entry.getKey()));
        }
        return translated;
    }

    /**
     * 获取自定义消息
     *
     * @param i18nKey 国际化 key
     * @return 翻译后的消息
     */
    protected String t(String i18nKey) {
        if (i18n != null) {
            return i18n.t(i18nKey);
        }
        return i18nKey;
    }

    /**
     * 获取自定义消息（带参数）
     *
     * @param i18nKey 国际化 key
     * @param args 参数列表
     * @return 翻译后的消息
     */
    protected String t(String i18nKey, Object... args) {
        if (i18n != null) {
            return i18n.t(i18nKey, args);
        }
        return i18nKey;
    }

    /**
     * 第一步处理方法（抽象方法，保留以兼容旧代码）
     *
     * @param userInput 用户输入数据（首次进入时为 null）
     * @return 配置流程结果
     */
    protected abstract ConfigFlowResult step_user(Map<String, Object> userInput);

    // ========== 内部类 ==========

    /**
     * 步骤定义 - 封装步骤处理器和显示信息
     */
    @Data
    @AllArgsConstructor
    protected static class StepDefinition {
        /**
         * 步骤处理函数
         */
        private final Function<Map<String, Object>, ConfigFlowResult> handler;

        /**
         * 步骤显示信息
         */
        private final StepInfo stepInfo;
    }
}
