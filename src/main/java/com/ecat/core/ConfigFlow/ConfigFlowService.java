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
import com.ecat.core.ConfigEntry.ConfigEntryRegistry;
import com.ecat.core.ConfigEntry.SourceType;
import com.ecat.core.Bus.BusRegistry;
import com.ecat.core.Bus.BusTopic;
import com.ecat.core.Bus.event.BusEvent;
import com.ecat.core.Bus.event.EventContext;
import com.ecat.core.Bus.event.NotificationEvent;
import com.ecat.core.EcatCore;
import com.ecat.core.Integration.IntegrationBase;
import com.ecat.core.Integration.IntegrationRegistry;
import com.ecat.core.Utils.Log;
import com.ecat.core.Utils.LogFactory;

import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * 配置流程服务（flow 推进与管理能力）
 * <p>发现 Provider 并管理流程实例。Flow 实例管理完全委托给 {@link ConfigFlowRegistry}
 * （唯一的 active flow 管理点）。
 *
 * <p><b>能力归属（2026-06-22 下沉）</b>：本类原位于 ecat-core-api，现已下沉到 ecat-core。
 * 动机——flow 推进/管理是核心机制，REST（ecat-core-api 的 controller）与同进程第三方使用集成
 * （如 test-discovery 的 import-flow、未来的 mqtt/zeroconf discovery 监听器）都应只依赖 ecat-core，
 * 而非引用 ecat-core-api。core 经 {@link EcatCore#getConfigFlowService()} 暴露本能力。
 *
 * @author coffee
 */
public class ConfigFlowService {

    private final Log log = LogFactory.getLogger(getClass());
    private final EcatCore core;
    private final IntegrationRegistry integrationRegistry;

    /**
     * 流程过期时间（毫秒）：30 分钟
     */
    private static final long FLOW_EXPIRATION_MS = 30 * 60 * 1000;

    /**
     * 待处理 discovery（pending）容量上限：防止异常/恶意发现源刷爆内存。
     * 超出则拒绝新发现入列，等用户处理（添加/忽略）腾位后再放行。
     */
    private static final int PENDING_DISCOVERY_LIMIT = 100;

    /**
     * 流程实例包装类
     */
    public static class ConfigFlowInstance {
        private String flowId;
        private String stepId;
        private ConfigFlowResult result;
        private AbstractConfigFlow flow;

        public String getFlowId() { return flowId; }
        public void setFlowId(String flowId) { this.flowId = flowId; }

        public String getStepId() { return stepId; }
        public void setStepId(String stepId) { this.stepId = stepId; }

        public ConfigFlowResult getResult() { return result; }
        public void setResult(ConfigFlowResult result) { this.result = result; }

        public AbstractConfigFlow getFlow() { return flow; }
        public void setFlow(AbstractConfigFlow flow) { this.flow = flow; }

        /** 终态 CREATE_ENTRY 持久化后的 entryId（由 service 统一持久化）*/
        private String savedEntryId;
        public String getSavedEntryId() { return savedEntryId; }
        public void setSavedEntryId(String savedEntryId) { this.savedEntryId = savedEntryId; }

        /** 持久化/通知 warning（如设备加载通知失败但条目已存）*/
        private String warningMessage;
        public String getWarningMessage() { return warningMessage; }
        public void setWarningMessage(String warningMessage) { this.warningMessage = warningMessage; }
    }

    public ConfigFlowService(EcatCore core) {
        this.core = core;
        this.integrationRegistry = core.getIntegrationRegistry();
        // P3（defense-in-depth）：周期清扫过期 flow，与各入口的懒清理互补。
        // 借 core 共享调度器（TaskManager.getMdcScheduledExecutorService，StateManager 等复用同一实例，
        // 由 TaskManager.shutdownAll() 统一关闭——无新增线程池、无线程泄漏）。间隔 = FLOW_EXPIRATION_MS。
        core.getTaskManager().getMdcScheduledExecutorService().scheduleAtFixedRate(
                this::cleanupExpiredFlows, FLOW_EXPIRATION_MS, FLOW_EXPIRATION_MS, TimeUnit.MILLISECONDS);
        log.info("ConfigFlowService 周期清理已启动，间隔 {}min（兜底，与入口懒清理互补）", FLOW_EXPIRATION_MS / 60000);
    }

    /**
     * 获取 EcatCore 实例
     */
    public EcatCore getCore() {
        return core;
    }

    /**
     * 清理过期的流程实例
     * @return 清理的数量
     */
    private int cleanupExpiredFlows() {
        ConfigFlowRegistry flowRegistry = core.getFlowRegistry();
        if (flowRegistry == null) {
            return 0;
        }
        return flowRegistry.cleanupExpiredFlows(FLOW_EXPIRATION_MS);
    }

    /**
     * 发现所有支持配置流程的集成
     * 仅限于包含UserStep的flow ，为用户前端使用的
     * <p>
     * 使用 ConfigFlowRegistry.hasUserStep() 检测，不再依赖 ConfigFlowProvider 接口。
     * @return Map<coordinate, provider>
     */
    public Map<String, ConfigFlowProvider> discoverProviders() {
        Map<String, ConfigFlowProvider> providers = new LinkedHashMap<>();

        // 使用 ConfigFlowRegistry 获取支持用户配置步骤的集成
        ConfigFlowRegistry flowRegistry = core.getFlowRegistry();
        if (flowRegistry == null) {
            log.warn("ConfigFlowRegistry not initialized");
            return providers;
        }

        List<String> coordinates = flowRegistry.getCoordinatesWithUserStep();
        log.info("发现 {} 个支持配置流程的集成", coordinates.size());

        for (String coordinate : coordinates) {
            try {
                // 获取显示名称
                String displayName = coordinate;
                IntegrationBase integration = integrationRegistry.getIntegration(coordinate);
                if (integration != null) {
                    displayName = integration.getName(); // 使用 getName() 方法
                }

                // 创建 provider 包装器
                final String finalDisplayName = displayName;
                ConfigFlowProvider provider = new ConfigFlowProvider() {
                    @Override
                    public String getDisplayName() {
                        return finalDisplayName;
                    }

                    @Override
                    public String getFlowType() {
                        return coordinate;
                    }

                    @Override
                    public AbstractConfigFlow createFlow() {
                        return flowRegistry.createFlow(coordinate);
                    }
                };

                providers.put(coordinate, provider);
                log.info("发现配置流程提供者: {} ({})", displayName, coordinate);
            } catch (Exception e) {
                log.warn("处理集成 {} 时出错: {}", coordinate, e.getMessage());
            }
        }

        return providers;
    }

    // ==================== 统一驱动器（drive）+ 终态处理（applyResult）====================
    // 入口（startFlow/startReconfigureFlow/startDiscoveryFlow 的 start 步）与 submitStep（具名步）
    // 全部收口到 drive——异常策略 + 结果策略只此一处，杜绝多入口各自处理导致的终态漂移（孤儿/漏持久化）。

    /**
     * 唯一驱动器：幂等注册 + {@code handleStep} + 统一异常策略 + 统一结果处理。
     * <p>幂等 {@link ConfigFlowRegistry#registerIfAbsent}——入口新 flow 注册、submitStep 已 active 的 flow no-op。
     * <p>异常策略（唯一一处）：R5 {@code DuplicateUniqueIdException} → clean ABORT(already_configured/in_progress)，
     * 不抛到调用方（重复发现=正常路径）；{@link ConfigFlowException} 透传；其它 RuntimeException 记录后透传。
     */
    private ConfigFlowInstance drive(AbstractConfigFlow flow, String stepId, Map<String, Object> input) {
        ConfigFlowRegistry flowRegistry = core.getFlowRegistry();
        flowRegistry.registerIfAbsent(flow.getFlowId(), flow);

        ConfigFlowResult result;
        try {
            result = flow.handleStep(stepId, input);
        } catch (ConfigEntryRegistry.DuplicateUniqueIdException e) {
            // R5 去重命中 → clean ABORT（不抛到 broker、不留孤儿）
            String uniqueId = e.getUniqueId();
            String reason = flowRegistry.hasActiveFlowWithUniqueId(uniqueId, flow.getFlowId())
                    ? AbortReason.ALREADY_IN_PROGRESS : AbortReason.ALREADY_CONFIGURED;
            result = ConfigFlowResult.abort(reason + ": " + uniqueId);
            log.info("去重命中({}): flowId={}, uniqueId={}", reason, flow.getFlowId(), uniqueId);
        } catch (ConfigFlowException e) {
            throw e;
        } catch (RuntimeException e) {
            log.error("flow 执行异常: flowId={}", flow.getFlowId(), e);
            throw e;
        }
        return applyResult(flow, result);
    }

    /**
     * 唯一终态副作用：CREATE/REMOVE→finish、ABORT→abort（清理 active flow）+ 持久化（applyTerminalResult）；
     * 非终态（SHOW_FORM 等）no-op（留 active 等下一步驱动）。
     */
    private ConfigFlowInstance applyResult(AbstractConfigFlow flow, ConfigFlowResult result) {
        ConfigFlowRegistry flowRegistry = core.getFlowRegistry();
        String flowId = flow.getFlowId();
        if (result.getType() == ConfigFlowResult.ResultType.CREATE_ENTRY
                || result.getType() == ConfigFlowResult.ResultType.REMOVE_ENTRY) {
            flowRegistry.finishActiveFlow(flowId);
            log.info("流程完成: flowId={}, type={}", flowId, result.getType());
        } else if (result.getType() == ConfigFlowResult.ResultType.ABORT) {
            flowRegistry.abortActiveFlow(flowId);
            log.info("流程中止: flowId={}, reason={}", flowId, result.getReason());
        }
        ConfigFlowInstance instance = buildInstance(flow, result);
        applyTerminalResult(instance);
        return instance;
    }

    /**
     * 启动指定集成的配置流程
     * <p>
     * 使用 ConfigFlowRegistry 获取 Flow 实例，不再依赖 ConfigFlowProvider 接口。
     *
     * @param providerCoordinate 提供者的 Maven coordinate (groupId:artifactId)
     */
    public ConfigFlowInstance startFlow(String providerCoordinate) {
        return startFlow(providerCoordinate, null);
    }

    /**
     * 启动指定集成的配置流程（带初始上下文数据）
     * <p>
     * 初始数据会被预填充到 FlowContext 中，使 Flow 可以根据数据路由到不同步骤。
     *
     * @param providerCoordinate 提供者的 Maven coordinate (groupId:artifactId)
     * @param initialData 初始上下文数据（可为 null）
     */
    public ConfigFlowInstance startFlow(String providerCoordinate, Map<String, Object> initialData) {
        cleanupExpiredFlows();
        ConfigFlowRegistry flowRegistry = core.getFlowRegistry();
        if (flowRegistry == null) {
            throw new ConfigFlowException("ConfigFlowRegistry not initialized");
        }
        if (!flowRegistry.hasUserStep(providerCoordinate)) {
            throw new ConfigFlowException("Integration does not provide config flow with user step: " + providerCoordinate);
        }
        AbstractConfigFlow flow = flowRegistry.createFlow(providerCoordinate);
        if (flow == null) {
            throw new ConfigFlowException("Failed to create config flow for: " + providerCoordinate);
        }
        // setup：coordinate + 预填初始数据（sourceType 默认 USER → startStepId 路由到 userStep）
        flow.getContext().setCoordinate(providerCoordinate);
        if (initialData != null && !initialData.isEmpty()) {
            flow.getContext().getEntryData().putAll(initialData);
        }
        log.info("启动配置流程: flowId={}, provider={}", flow.getFlowId(), providerCoordinate);
        return drive(flow, flow.startStepId(), null);   // register + handleStep(userStep) + 统一异常/终态 全在 drive
    }

    /**
     * 提交步骤数据（外部统一推进）。收口到 {@link #drive}——异常/终态处理与入口一致。
     */
    public ConfigFlowInstance submitStep(String flowId, String stepId, Map<String, Object> userInput) {
        ConfigFlowRegistry flowRegistry = core.getFlowRegistry();
        AbstractConfigFlow flow = flowRegistry.getActiveFlow(flowId);   // 自动 touch；null 则未找到
        if (flow == null) {
            throw new FlowNotFoundException("Flow not found: " + flowId);
        }
        return drive(flow, stepId, userInput);   // register 幂等 no-op(已 active) + handleStep + 统一异常/终态
    }

    /**
     * 返回上一步
     */
    public ConfigFlowInstance goPrevious(String flowId) {
        ConfigFlowRegistry flowRegistry = core.getFlowRegistry();
        ConfigFlowResult result;
        try {
            result = flowRegistry.goPrevious(flowId);
        } catch (IllegalArgumentException e) {
            throw new FlowNotFoundException("Flow not found: " + flowId);
        }
        return buildInstance(flowRegistry.getActiveFlow(flowId), result);
    }

    /**
     * 获取流程状态
     */
    public ConfigFlowInstance getStatus(String flowId) {
        ConfigFlowRegistry flowRegistry = core.getFlowRegistry();
        ConfigFlowResult result;
        try {
            result = flowRegistry.getStatus(flowId);
        } catch (IllegalArgumentException e) {
            throw new FlowNotFoundException("Flow not found: " + flowId);
        }
        return buildInstance(flowRegistry.getActiveFlow(flowId), result);
    }

    /**
     * 取消/删除流程
     */
    public void cancelFlow(String flowId) {
        ConfigFlowRegistry flowRegistry = core.getFlowRegistry();
        if (flowRegistry.getActiveFlow(flowId) != null) {
            flowRegistry.abortActiveFlow(flowId);
            log.info("取消配置流程: flowId={}", flowId);
        }
    }

    /**
     * 构建流程实例响应
     */
    private ConfigFlowInstance buildInstance(AbstractConfigFlow flow, ConfigFlowResult result) {
        ConfigFlowInstance instance = new ConfigFlowInstance();
        instance.setFlowId(flow.getFlowId());
        String stepId = result.getStepId();
        if (stepId == null || stepId.isEmpty()) {
            stepId = flow.getCurrentStep();
        }
        instance.setStepId(stepId);
        instance.setResult(result);
        instance.setFlow(flow);
        return instance;
    }

    /**
     * 清理所有流程实例
     */
    public void clearAllFlows() {
        ConfigFlowRegistry flowRegistry = core.getFlowRegistry();
        if (flowRegistry != null) {
            flowRegistry.abortAllActiveFlows();
        }
        log.info("已清理所有配置流程实例");
    }

    /**
     * 获取当前活动流程数量
     */
    public int getActiveFlowCount() {
        ConfigFlowRegistry flowRegistry = core.getFlowRegistry();
        if (flowRegistry == null) {
            return 0;
        }
        return flowRegistry.getActiveFlowCount();
    }

    // ==================== 新增方法 ====================

    /**
     * 启动重新配置流程
     *
     * @param coordinate 集成标识
     * @param uniqueId   配置条目唯一标识
     * @return 配置流程实例
     */
    public ConfigFlowInstance startReconfigureFlow(String coordinate, String uniqueId) {
        return startReconfigureFlow(coordinate, uniqueId, null);
    }

    /**
     * 启动重新配置流程（带额外上下文数据）
     *
     * @param coordinate 集成标识
     * @param uniqueId   配置条目唯一标识
     * @param extraData  额外上下文数据（可为 null）
     * @return 配置流程实例
     */
    public ConfigFlowInstance startReconfigureFlow(String coordinate, String uniqueId, Map<String, Object> extraData) {
        ConfigEntryRegistry entryRegistry = core.getEntryRegistry();
        if (entryRegistry == null) {
            throw new ConfigFlowException("ConfigEntryRegistry not initialized");
        }
        ConfigEntry entry = entryRegistry.getByUniqueId(coordinate, uniqueId);
        if (entry == null) {
            throw new ConfigFlowException("ConfigEntry not found: " + uniqueId);
        }
        ConfigFlowRegistry flowRegistry = core.getFlowRegistry();
        if (flowRegistry == null) {
            throw new ConfigFlowException("ConfigFlowRegistry not initialized");
        }
        AbstractConfigFlow flow = flowRegistry.createFlow(coordinate);
        if (flow == null) {
            throw new ConfigFlowException("ConfigFlow not found for: " + coordinate);
        }
        if (!flow.hasReconfigureStep()) {
            throw new ConfigFlowException("Flow does not support reconfigure: " + coordinate);
        }
        // setup：预填 entry 数据 + 恢复 stepInputs + 额外数据 + RECONFIGURE 标记
        FlowContext context = flow.getContext();
        context.setCoordinate(coordinate);
        context.getEntryData().putAll(entry.getData());
        context.setEntryTitle(entry.getTitle());
        context.setEntryUniqueId(entry.getUniqueId(), true);  // RECONFIGURE: skip validation
        if (entry.getStepInputs() != null) {
            context.setStepInputs(new HashMap<>(entry.getStepInputs()));
        }
        if (extraData != null && !extraData.isEmpty()) {
            context.getEntryData().putAll(extraData);
        }
        flow.setSourceType(SourceType.RECONFIGURE);
        flow.setReconfigureEntryId(entry.getEntryId());
        log.info("Started reconfigure flow: flowId={}, entryId={}", flow.getFlowId(), entry.getEntryId());
        return drive(flow, flow.startStepId(), null);   // sourceType=RECONFIGURE → startStepId=reconfigureStep
    }

    // ==================== Discovery 触发（IMPORT_FLOW/ZEROCONF/MQTT 统一入口）====================

    /**
     * 触发一次 discovery flow——**所有 discovery 源（IMPORT_FLOW/ZEROCONF/MQTT）的统一入口**。
     * <p>调用方：
     * <ul>
     *   <li>IMPORT_FLOW：同进程 SDK（如 test-discovery ImportFlowTestDriver）直接调本方法（source=IMPORT_FLOW）。</li>
     *   <li>ZEROCONF/MQTT：协议集成 broker（integration-zeroconf/mqtt）监听 + 匹配订阅表后解析出目标 coordinate，调本方法。</li>
     * </ul>
     * core 对 payload **完全不透明**（当 Object 透传）。
     *
     * <p><b>严格模式</b>：core 未就绪 / 目标集成未加载 / Layer2 命中 / 集成不支持该 source 均抛
     * {@link ConfigFlowException}（**不暂存**——调用方自行重试）。
     * <b>Layer2 matching（R12）</b>：同 (coordinate, source, payload) 已有活跃 flow 时拒绝重复创建。
     * <b>R5 去重（已添加设备重复触发）</b>：discovery handler 内 setEntryUniqueId 抛 DuplicateUniqueIdException
     * 时，由 {@link #drive} 转 clean ABORT(already_configured/in_progress)，不抛到调用方、不留孤儿。
     *
     * @param coordinate 目标设备集成坐标
     * @param source     discovery 源（IMPORT_FLOW / MQTT / ZEROCONF）
     * @param payload    discovery payload（core 不透明；须有合规 equals/hashCode 供 Layer2 去重）
     * @return 流程实例（首步结果；若 handler 直接 CREATE_ENTRY 则已持久化）
     * @throws ConfigFlowException source 非法 / core 未就绪 / 集成不支持该 source / Layer2 命中
     */
    public ConfigFlowInstance startDiscoveryFlow(String coordinate, SourceType source, Object payload) {
        Objects.requireNonNull(coordinate, "coordinate 不能为 null");
        Objects.requireNonNull(source, "source 不能为 null");
        Objects.requireNonNull(payload, "payload 不能为 null");
        if (coordinate.isEmpty()) {
            throw new ConfigFlowException("coordinate 不能为空");
        }
        if (!source.isPayloadDiscoverySource()) {
            throw new ConfigFlowException("source 须为 payload-discovery 源(IMPORT_FLOW/MQTT/ZEROCONF): " + source);
        }

        cleanupExpiredFlows();

        ConfigFlowRegistry flowRegistry = core.getFlowRegistry();
        if (flowRegistry == null) {
            throw new ConfigFlowException("ConfigFlowRegistry 未初始化，core 未就绪（owner 可重试）");
        }
        if (core.getEntryRegistry() == null) {
            throw new ConfigFlowException("ConfigEntryRegistry 未初始化，core 未就绪（owner 可重试）");
        }

        // pending 容量上限（防泛洪）：仅数 discovery 源待处理 flow（终态 flow 已从 registry 移除，留存即 SHOW_FORM 待处理）。
        // 超出则拒绝新发现入列，等用户处理（添加/忽略）腾位后再放行。
        long pending = flowRegistry.getActiveFlowSnapshots().stream()
                .filter(s -> s.getFlow().getSourceType() != null
                        && s.getFlow().getSourceType().isPayloadDiscoverySource())
                .count();
        if (pending >= PENDING_DISCOVERY_LIMIT) {
            throw new ConfigFlowException("待处理发现已达上限（" + PENDING_DISCOVERY_LIMIT
                    + "），请先处理现有发现后再触发新 discovery: " + coordinate);
        }

        // Layer2 matching（R12）：同 (coordinate, source, payload) 已有活跃 flow → 续期现有 flow 并返回
        // （不重建 flow、不重发提示）。续期由 getStatus 内部 getActiveFlow 的 touch 完成（刷新 30min 过期）。
        // 语义：重复广播（如 zeroconf 周期 reannounce）= 设备仍在线 → 续期保留；设备消失 → 30min 无续期自动清除。
        String existingFlowId = flowRegistry.findDiscoveryFlowId(coordinate, source, payload);
        if (existingFlowId != null) {
            log.info("R12 去重命中，续期现有 discovery flow: flowId={}, coordinate={}", existingFlowId, coordinate);
            return getStatus(existingFlowId);
        }

        AbstractConfigFlow flow = flowRegistry.createFlow(coordinate);
        if (flow == null) {
            throw new ConfigFlowException("ConfigFlow 未注册（core 可能尚未加载该集成）: " + coordinate);
        }
        if (!flow.hasDiscoverySource(source)) {
            throw new ConfigFlowException("集成未注册 " + source + " discovery handler: " + coordinate);
        }

        // setup：coordinate + source + stash payload（discovery handler 经 stepDefinitions adapter 从 flow 字段读）
        flow.getContext().setCoordinate(coordinate);
        flow.setSourceType(source);
        flow.setDiscoveryPayload(payload);
        log.info("触发 discovery: flowId={}, coordinate={}, source={}", flow.getFlowId(), coordinate, source);
        ConfigFlowInstance instance = drive(flow, flow.startStepId(), null);
        notifyDiscoveryIfPending(flow, instance);   // 落 SHOW_FORM 时发 actionable 提示（前端实时收到）
        return instance;
    }

    // ==================== Discovery flow 暴露（前端"待处理发现"列表）====================

    /**
     * 列出所有"待用户处理"的 discovery flow（只读快照，对外 DTO）。
     * <p>遍历 active flow，过滤 {@link SourceType#isPayloadDiscoverySource()} 的非终态 flow
     * （终态 ABORT/CREATE_ENTRY 已从 registry 移除，故留存即待处理 SHOW_FORM）。
     * <p><b>不调</b> {@code getStatus()}（会重跑步骤）；直接从 {@code FlowContext} 读字段构造 {@link DiscoveryFlowInfo}。
     * 普适 IMPORT_FLOW/ZEROCONF/MQTT 三源——前端据 source 区分展示，据 flowId 进向导。
     *
     * @return 待处理 discovery flow 快照列表（空列表若无）
     */
    public List<DiscoveryFlowInfo> listDiscoveryFlows() {
        ConfigFlowRegistry flowRegistry = core.getFlowRegistry();
        if (flowRegistry == null) {
            return Collections.emptyList();
        }
        List<DiscoveryFlowInfo> list = new ArrayList<>();
        for (ConfigFlowRegistry.ActiveFlowSnapshot snap : flowRegistry.getActiveFlowSnapshots()) {
            AbstractConfigFlow flow = snap.getFlow();
            SourceType st = flow.getSourceType();
            if (st == null || !st.isPayloadDiscoverySource()) {
                continue;   // 仅 discovery 源
            }
            FlowContext ctx = flow.getContext();
            String coordinate = ctx.getCoordinate();
            list.add(new DiscoveryFlowInfo(
                    snap.getFlowId(),
                    st.name(),
                    coordinate,
                    resolveIntegrationName(coordinate),
                    resolveDiscoveryTitle(ctx, coordinate),
                    ctx.getEntryUniqueId(),
                    ctx.getCurrentStep(),
                    snap.getLastUpdateTime()));
        }
        return list;
    }

    /** discovery 标题：优先 entryData.name，否则 coordinate 兜底。 */
    private String resolveDiscoveryTitle(FlowContext ctx, String coordinate) {
        Map<String, Object> entryData = ctx.getEntryData();
        if (entryData != null) {
            Object name = entryData.get("name");
            if (name != null && !name.toString().isEmpty()) {
                return name.toString();
            }
        }
        return coordinate;
    }

    /** 集成显示名（查不到则回退 coordinate）；遍历查询容错——单集成异常不影响整列表。 */
    private String resolveIntegrationName(String coordinate) {
        if (coordinate == null || coordinate.isEmpty()) {
            return null;
        }
        try {
            IntegrationBase integration = integrationRegistry.getIntegration(coordinate);
            return integration != null ? integration.getName() : coordinate;
        } catch (RuntimeException e) {
            log.warn("listDiscoveryFlows 解析集成名失败，回退 coordinate: {}", coordinate, e);
            return coordinate;
        }
    }

    /**
     * discovery flow 落 SHOW_FORM（待用户处理）时发 actionable notification，
     * 让前端实时收到"发现新设备"提示 + flowId（点击进向导）。
     * <p>仅 SHOW_FORM 发（ABORT 去重/CREATE_ENTRY 已完成不发）；去重规则保证同 uniqueId 不重复建 flow → 不重复通知。
     * bus 未就绪则跳过（discovery 本身不受影响，仅无前端提示）。
     * <p>action 用强类型 {@link DiscoveryNotificationAction}（非 Map 透传）——消费方有类型保障。
     */
    private void notifyDiscoveryIfPending(AbstractConfigFlow flow, ConfigFlowInstance instance) {
        if (instance == null || instance.getResult() == null) {
            return;
        }
        if (instance.getResult().getType() != ConfigFlowResult.ResultType.SHOW_FORM) {
            return;   // 仅待处理（SHOW_FORM）发；ABORT(去重)/CREATE_ENTRY(已完成)不发
        }
        BusRegistry bus = core.getBusRegistry();
        if (bus == null) {
            log.debug("BusRegistry 未就绪，discovery 提示未发（flow 仍正常）: flowId={}", flow.getFlowId());
            return;
        }
        FlowContext ctx = flow.getContext();
        String title = resolveDiscoveryTitle(ctx, ctx.getCoordinate());
        // sourceType 在 startDiscoveryFlow 中已 Objects.requireNonNull 保证非 null（discovery flow 必有源）
        DiscoveryNotificationAction action = new DiscoveryNotificationAction(
                flow.getFlowId(),
                flow.getSourceType().name(),
                ctx.getCoordinate(),
                ctx.getEntryUniqueId(),
                title);
        bus.publish(BusEvent.of(BusTopic.NOTIFICATION.getTopicName(),
                new NotificationEvent("INFO", "discovery", "发现新设备：" + title, action),
                EventContext.root(EventContext.Source.SYSTEM, null)));
        log.info("已发送 discovery 提示: flowId={}, coordinate={}", flow.getFlowId(), ctx.getCoordinate());
    }

    // ==================== 终态结果持久化（CREATE_ENTRY / REMOVE_ENTRY 统一在 service）====================

    /**
     * 对终态结果（CREATE_ENTRY / REMOVE_ENTRY）做持久化（含集成设备加载通知）。
     * <p>持久化统一在 service 层：REST 路径（controller）与同进程 SDK 路径（import-flow）共用，
     * 避免 controller-only 持久化导致非 REST 入口无法落库。结果（entryId / 通知失败 warning）写入 instance。
     */
    private void applyTerminalResult(ConfigFlowInstance instance) {
        if (instance == null || instance.getResult() == null) {
            return;
        }
        ConfigFlowResult result = instance.getResult();
        if (result.getType() == ConfigFlowResult.ResultType.CREATE_ENTRY) {
            try {
                instance.setSavedEntryId(persistCreateEntry(result, instance.getFlow()));
            } catch (ConfigEntryRegistry.EntryNotificationException e) {
                // 条目已落库，但集成设备加载通知失败——保留 entryId，记 warning（与原 controller 行为一致）
                instance.setSavedEntryId(e.getEntry().getEntryId());
                String op = "create".equals(e.getOperation()) ? "创建" : "重新配置";
                instance.setWarningMessage("设备" + op + "失败: " + e.getCause().getMessage()
                        + "（条目已保存，可稍后重新配置）");
                log.warn("Entry notification failed but entry persisted: {}", e.getMessage());
            }
        } else if (result.getType() == ConfigFlowResult.ResultType.REMOVE_ENTRY) {
            persistRemoveEntry(result);
        }
    }

    /**
     * 持久化 CREATE_ENTRY：按 sourceType 决定 create / reconfigure（与原 controller.handleCreateEntry 等价）。
     */
    private String persistCreateEntry(ConfigFlowResult result, AbstractConfigFlow flow) {
        ConfigEntry entry = result.getEntry();
        if (entry == null) {
            log.warn("CREATE_ENTRY result without entry data");
            return null;
        }
        ConfigEntryRegistry registry = core.getEntryRegistry();
        if (registry == null) {
            log.error("ConfigEntryRegistry not initialized");
            return null;
        }
        ConfigEntry saved;
        if (flow != null && flow.getSourceType() == SourceType.RECONFIGURE) {
            saved = registry.reconfigureEntry(entry.getEntryId(), entry);
            log.info("Reconfigured config entry: {}", saved.getEntryId());
        } else {
            saved = registry.createEntry(entry);
            log.info("Created config entry: {} (source={})", saved.getEntryId(),
                    flow != null ? flow.getSourceType() : SourceType.USER);
        }
        return saved.getEntryId();
    }

    /**
     * 持久化 REMOVE_ENTRY：按 uniqueId 删除已存在 entry（与原 controller.handleRemoveEntry 等价）。
     */
    private void persistRemoveEntry(ConfigFlowResult result) {
        ConfigEntry entry = result.getEntry();
        if (entry == null) {
            log.warn("REMOVE_ENTRY result without entry data");
            return;
        }
        ConfigEntryRegistry registry = core.getEntryRegistry();
        if (registry == null) {
            log.error("ConfigEntryRegistry not initialized");
            return;
        }
        ConfigEntry existing = registry.getByUniqueId(entry.getCoordinate(), entry.getUniqueId());
        if (existing != null) {
            registry.removeEntry(existing.getEntryId());
            log.info("Entry removed via flow: {}", entry.getUniqueId());
        } else {
            log.warn("REMOVE_ENTRY: entry not found for uniqueId: {}", entry.getUniqueId());
        }
    }

    /**
     * 获取支持用户入口的 Flow 列表 (用于前端展示)
     *
     * @return FlowProviderInfo 列表
     */
    public List<FlowProviderInfo> getProvidersWithUserStep() {
        ConfigFlowRegistry flowRegistry = core.getFlowRegistry();
        if (flowRegistry == null) {
            return Collections.emptyList();
        }

        return flowRegistry.getFlowsWithUserStep().stream()
                .map(flow -> {
                    String coordinate = flow.getContext().getCoordinate();
                    return new FlowProviderInfo(coordinate, flow.getUserStep().getDisplayName());
                })
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * Flow 提供者信息
     */
    public static class FlowProviderInfo {
        private final String coordinate;
        private final String displayName;

        public FlowProviderInfo(String coordinate, String displayName) {
            this.coordinate = coordinate;
            this.displayName = displayName;
        }

        public String getCoordinate() {
            return coordinate;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
