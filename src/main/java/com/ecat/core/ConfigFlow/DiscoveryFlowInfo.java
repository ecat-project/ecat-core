/*
 * Copyright (c) 2026 ECAT Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.ecat.core.ConfigFlow;

/**
 * Discovery flow 待处理项的只读快照（对外 DTO）。
 * <p>由 {@link ConfigFlowService#listDiscoveryFlows()} 产出——遍历 active flow，
 * 过滤 {@code SourceType.isPayloadDiscoverySource()} 的非终态（滞留 SHOW_FORM）flow，
 * 从 {@code FlowContext} 读取字段构造。
 *
 * <p>**不持有** {@link AbstractConfigFlow} 引用——纯数据，供 REST 序列化 / 前端消费。
 * <p>普适于三类发现源（IMPORT_FLOW / ZEROCONF / MQTT），由 {@code source} 字段区分。
 *
 * @author coffee
 */
public final class DiscoveryFlowInfo {

    /** flowId（UUID），前端据此进入向导（{@code /config-flow?flowId=...}）。 */
    private final String flowId;
    /** 发现源（SourceType.name()：IMPORT_FLOW / ZEROCONF / MQTT）。 */
    private final String source;
    /** 目标集成 coordinate（groupId:artifactId）。 */
    private final String coordinate;
    /** 集成显示名（controller 层用 integrationRegistry 补；DTO 不依赖集成）。 */
    private final String integrationName;
    /** 设备名/标题（context.entryData.name 或 coordinate 兜底）。 */
    private final String title;
    /** 预派生 uniqueId（discovery handler stash，供忽略时建 IGNORE entry）。 */
    private final String uniqueId;
    /** 当前 stepId（SHOW_FORM 落点）。 */
    private final String currentStep;
    /** 发现时间（TrackedFlow.lastUpdateTime 近似，ms epoch）。 */
    private final long discoveredAt;

    public DiscoveryFlowInfo(String flowId, String source, String coordinate, String integrationName,
                             String title, String uniqueId, String currentStep, long discoveredAt) {
        this.flowId = flowId;
        this.source = source;
        this.coordinate = coordinate;
        this.integrationName = integrationName;
        this.title = title;
        this.uniqueId = uniqueId;
        this.currentStep = currentStep;
        this.discoveredAt = discoveredAt;
    }

    public String getFlowId() {
        return flowId;
    }

    public String getSource() {
        return source;
    }

    public String getCoordinate() {
        return coordinate;
    }

    public String getIntegrationName() {
        return integrationName;
    }

    public String getTitle() {
        return title;
    }

    public String getUniqueId() {
        return uniqueId;
    }

    public String getCurrentStep() {
        return currentStep;
    }

    public long getDiscoveredAt() {
        return discoveredAt;
    }
}
