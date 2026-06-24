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
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * 配置流程基类
 *
 * <p>使用 registerStep() 注册步骤处理器和显示信息
 * <p>支持入口步骤机制：registerStepUser()、registerStepReconfigure()、registerStepDiscovery()（类型化多 source 发现 step）
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
 *         context.getEntryData().putAll(userInput);
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
     * 该 flow 的 discovery payload（仅 IMPORT_FLOW/MQTT/ZEROCONF 入口在 executeDiscoveryStep 设置；
     * 供 Layer2 matching 的 flow 级去重 R12 使用）。USER/RECONFIGURE 为 null。
     */
    private Object discoveryPayload;

    /**
     * 重配置时的 entryId
     */
    protected String reconfigureEntryId;

    // ========== 生命周期回调 ==========

    /**
     * 流程结束时的资源释放回调
     * <p>
     * 正常完成、用户取消、过期清理均触发。
     * 由 ConfigFlowRegistry 在 finishActiveFlow() / abortActiveFlow() 时自动调用。
     * <p>
     * 子类可覆盖此方法释放资源（如临时文件、网络连接等）。
     */
    protected void onRelease() {
        // 默认空实现
    }

    /**
     * 用户入口步骤 (每类只能注册一个)
     */
    protected EntryStepDefinition userStep;

    /**
     * 重配置入口步骤 (每类只能注册一个)
     */
    protected EntryStepDefinition reconfigureStep;

    /**
     * 按 SourceType 注册的类型化发现处理器（仅 IMPORT_FLOW/MQTT/ZEROCONF；每 source 一个）。
     * <p>替换原单 {@code discoveryStep} 弱类型入口——一个 source = 一个类型化 capability（见需求 BD-2）。
     * <p>peer 模型：core 对 payload 完全 opaque（P 擦除存储），不再保留 payload 类型绑定（原
     * {@code discoveryPayloadTypes} 已移除——运行时未参与校验）。
     */
    private final Map<SourceType, DiscoveryHandler<?>> discoveryHandlers = new LinkedHashMap<>();

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
     * <p>
     * 创建 FlowContext，自动生成默认 flowId。
     * <p>
     * flowId 格式：UUID 字符串。
     */
    protected AbstractConfigFlow() {
        this.context = new FlowContext(UUID.randomUUID().toString());
        this.i18n = I18nHelper.createProxy(this.getClass());
    }

    /**
     * 获取流程实例 ID
     *
     * @return 流程实例 ID
     */
    public String getFlowId() {
        return context.getFlowId();
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
     * <p> [重要]集成自定义的reconfigure flow原则上不要修改设备类型的配置，比如从电源变为空调，因为这会严重改变该设备被其他集成引用的含义作用而导致其他集成错误。
     * <p> [重要]推荐只修改sn、端口信息等不会
     * <p> [重要]对于更改设备类型的需求，让用户删除旧entry重新创建新的entry实现
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
     * 注册类型化发现处理器（一个 source 一个；source 必须是携带 payload 的发现源）。
     * <p>peer 模型（2026-06-23 命名统一）：原 {@code registerDiscoverySource(SourceType, Class<P>, handler)}
     * 改名为 {@code registerStepDiscovery}（与 {@code registerStepUser}/{@code registerStepReconfigure}
     * 同族——discovery handler 是 flow 的一个 step），并**去除 {@code Class<P>} 参数**（运行时不需要，
     * core 对 payload 完全 opaque、P 擦除存储）。
     *
     * <p>注册不变式（严格模式）：仅 {@link SourceType#isPayloadDiscoverySource()}（IMPORT_FLOW/MQTT/ZEROCONF）
     * 可注册；对其余值（USER/RECONFIGURE/IGNORE/UNIGNORE）注册直接抛 {@link IllegalStateException}。
     * 同一 source 重复注册亦抛异常。
     *
     * @param source  发现源（必须 isPayloadDiscoverySource）
     * @param handler 类型化处理器（接收 P 和 context；P 由 handler 签名推断，core 擦除存储）
     * @param <P>     payload 类型
     */
    protected final <P> void registerStepDiscovery(SourceType source, DiscoveryHandler<P> handler) {
        Objects.requireNonNull(source, "source 不能为 null");
        Objects.requireNonNull(handler, "handler 不能为 null");
        if (!source.isPayloadDiscoverySource()) {
            throw new IllegalStateException(
                    "仅 IMPORT_FLOW/MQTT/ZEROCONF 可注册 DiscoveryHandler，收到: " + source);
        }
        if (discoveryHandlers.containsKey(source)) {
            throw new IllegalStateException("该 source 已注册 handler: " + source);
        }
        discoveryHandlers.put(source, handler);

        // 同步进 stepDefinitions（与 registerStepUser 同构），让 handleStep 能跑 discovery——
        // discovery handler 的 payload 不从 handleStep 的 input(Map) 拿，而从 flow 的 discoveryPayload 字段读
        //（discovery 入口在 drive 前 stash）。这样 handleStep 成为唯一"跑一步"原语，discovery 不再自成一套。
        String stepId = discoveryStepId(source);
        if (stepDefinitions.containsKey(stepId)) {
            throw new IllegalStateException("discovery stepId 与已注册 step 碰撞: " + stepId);
        }
        final DiscoveryHandler<P> typed = handler;
        stepDefinitions.put(stepId, new StepDefinition(
            input -> invokeDiscoveryHandlerUnchecked(typed, this.discoveryPayload),
            StepInfo.of("discovery:" + source)
        ));
        log.debug("Registered discovery step handler (synced to stepDefinitions): {}", source);
    }

    /**
     * discovery start 节点的 stepId（内部派生，保留前缀防与用户 stepId 碰撞）。
     * <p>由 {@link #registerStepDiscovery} 注册时用作 stepDefinitions 的 key，供 {@link #handleStep} 路由。
     */
    static String discoveryStepId(SourceType source) {
        return "$discovery:" + source.name();
    }

    /**
     * 是否注册了指定 discovery source 的 handler。
     *
     * @param source 发现源
     * @return 若该 source 已注册 handler 则 true
     */
    public boolean hasDiscoverySource(SourceType source) {
        return discoveryHandlers.containsKey(source);
    }

    /**
     * 已注册的所有 discovery source（不可变快照）。
     *
     * @return 已注册 SourceType 集合
     */
    public Set<SourceType> getRegisteredDiscoverySources() {
        return Collections.unmodifiableSet(new LinkedHashSet<>(discoveryHandlers.keySet()));
    }

    /**
     * 执行发现入口步骤（类型化 handler）。
     * <p>设置 {@link #sourceType} + stash {@link #discoveryPayload} 后，<b>走 {@link #handleStep}</b>
     * （discovery start 节点已由 {@link #registerStepDiscovery} 同步进 stepDefinitions，key =
     * {@link #discoveryStepId(SourceType)}）。SHOW_FORM 后处理由 handleStep 统一完成。
     *
     * <p>本方法保留为 flow 级公有测试 API（众多集成单测直接调用）；内部已统一委托 handleStep，
     * 与 {@link #executeUserStep}/{@link #executeReconfigureStep} 同构。
     *
     * @param source  发现源（必须已注册 handler，否则抛 IllegalStateException）
     * @param payload provider 投递的 payload（须为注册时绑定的类型 P）
     * @return 流程结果（SHOW_FORM / CREATE_ENTRY / ABORT）
     * @throws IllegalStateException 若该 source 未注册 handler
     */
    public ConfigFlowResult executeDiscoveryStep(SourceType source, Object payload) {
        if (!discoveryHandlers.containsKey(source)) {
            throw new IllegalStateException("Flow 未注册该 discovery source: " + source);
        }
        this.sourceType = source;
        this.discoveryPayload = payload;                  // stash，供 stepDefinitions adapter 读
        return handleStep(discoveryStepId(source), null); // ← 走 handleStep（SHOW_FORM 后处理统一）
    }

    /**
     * core 内部唯一的 raw/unchecked 调用点（Java 泛型擦除所致）。
     * <p>安全由注册不变式保证：handler 注册时 P 与 source 绑定，provider 投递的 payload 必为该类型。
     * 该 unchecked 仅在 core 内部、对集成不可见——集成侧始终是强类型（P 直接入参）。
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private ConfigFlowResult invokeDiscoveryHandlerUnchecked(DiscoveryHandler handler, Object payload) {
        return handler.handle(payload, context);
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
     * 是否注册了任意 discovery source handler（兼容旧能力查询；新代码用 {@link #hasDiscoverySource(SourceType)}）。
     */
    public boolean hasDiscoveryStep() {
        return !discoveryHandlers.isEmpty();
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
     * 获取该 flow 的 discovery payload（仅 discovery 入口设置；供 Layer2 matching 去重用）。
     *
     * @return discovery payload，USER/RECONFIGURE 为 null
     */
    public Object getDiscoveryPayload() {
        return discoveryPayload;
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
        String stepId = userStep.getStepId();
        return handleStep(stepId, userInput);
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
        String stepId = reconfigureStep.getStepId();
        return handleStep(stepId, userInput);
    }

    /**
     * 按 {@link #sourceType} 返回当前 start 节点的 stepId（start 节点路由沉到 flow——DAG owner 内聚）。
     * <p>供 {@code ConfigFlowService.drive} 用：入口设好 sourceType + setup 后，drive(flow, startStepId(), ...)
     * 即可统一驱动 start 步（user/reconfigure/discovery），无需 service 感知 stepId 命名约定。
     *
     * @return 当前 sourceType 对应的 start 节点 stepId
     */
    public String startStepId() {
        switch (sourceType) {
            case RECONFIGURE:
                return reconfigureStep.getStepId();
            case IMPORT_FLOW:
            case MQTT:
            case ZEROCONF:
                return discoveryStepId(sourceType);
            case USER:
            default:
                return userStep.getStepId();
        }
    }

    /**
     * 设置 discovery payload（discovery 入口在 drive 前 stash，供 stepDefinitions 内的 discovery adapter 读）。
     * <p>与 {@link #setSourceType} 配合，由 {@code ConfigFlowService.startDiscoveryFlow} 在 setup 阶段调用。
     */
    public void setDiscoveryPayload(Object payload) {
        this.discoveryPayload = payload;
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
     * 设置流程上下文（替换构造函数创建的默认 context）
     *
     * @param context 流程上下文，不能为 null
     * @throws NullPointerException 如果 context 为 null
     */
    public void setContext(FlowContext context) {
        this.context = Objects.requireNonNull(context, "context must not be null");
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

    // ========== 步骤数据操作（含 copy 逻辑，保留在 Flow 层） ==========

    /**
     * 显示表单（使用新版 ConfigSchema）
     *
     * <p>框架自动处理步骤数据持久化：
     * <ul>
     *   <li>context.stepInputs 已包含各步骤数据</li>
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
        // context.stepInputs 已包含各步骤数据，前端从 step_inputs[stepId] 读取
        // 框架自动在 handleStep() 中保存数据到 stepInputs
        return ConfigFlowResult.showForm(stepId, schema, errors, context);
    }

    /**
     * 创建配置条目 (根据 sourceType 决定行为)
     * <p>
     * 从 context 获取 coordinate 和数据，根据 sourceType 决定创建或更新模式。
     * entry.data 仅包含业务数据（不含 step_inputs/title/uniqueId）。
     * stepInputs 作为 ConfigEntry 的独立字段持久化。
     *
     * @return CREATE_ENTRY 类型结果
     */
    protected ConfigFlowResult createEntry() {
        // 从 context 获取 coordinate (由 ConfigFlowRegistry 设置)
        String coordinate = context.getCoordinate();

        // 构建干净的 entry data（仅业务数据，不含 step_inputs/title/uniqueId）
        Map<String, Object> entryData = new HashMap<>(context.getEntryData());

        ConfigEntry.Builder builder = new ConfigEntry.Builder()
                .coordinate(coordinate)
                .data(entryData);

        // 从 context 专用字段获取 title 和 uniqueId
        if (context.getEntryTitle() != null) {
            builder.title(context.getEntryTitle());
        }
        if (context.getEntryUniqueId() != null) {
            builder.uniqueId(context.getEntryUniqueId());
        }

        // 持久化 stepInputs（用于重配置数据漫游）
        builder.stepInputs(context.getStepInputs());

        if (sourceType == SourceType.RECONFIGURE && reconfigureEntryId != null) {
            // 更新模式: 需要保留原 entryId（entry.source 由 withReconfigure 保留原值，此处不改写）
            builder.entryId(reconfigureEntryId);
            return ConfigFlowResult.updateEntry(builder.build(), context);
        } else {
            // 创建模式：记录创建来源（USER/IMPORT_FLOW/MQTT/ZEROCONF/IGNORE）
            builder.source(sourceType);
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
        context.setCurrentStep(stepId);

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
                context.setCurrentStep(displayStepId);
                if (!stepHistory.contains(displayStepId)) {
                    stepHistory.add(displayStepId);
                }
            }
        }

        return result;
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
     * 保存步骤数据到 context.stepInputs
     *
     * @param stepId 步骤 ID
     * @param userInput 用户输入数据
     */
    protected void saveStepData(String stepId, Map<String, Object> userInput) {
        context.getStepInputs().put(stepId, new HashMap<>(userInput));
    }

    /**
     * 获取特定步骤的用户输入（从 context.stepInputs 读取）
     *
     * @param stepId 步骤 ID
     * @return 该步骤的用户输入数据副本
     */
    @SuppressWarnings("unchecked")
    protected Map<String, Object> getStepData(String stepId) {
        Map<String, Object> stepInputs = context.getStepInputs();
        if (stepInputs.containsKey(stepId)) {
            return new HashMap<>((Map<String, Object>) stepInputs.get(stepId));
        }
        return new HashMap<>();
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
        context.setCurrentStep(previousStepId);

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
