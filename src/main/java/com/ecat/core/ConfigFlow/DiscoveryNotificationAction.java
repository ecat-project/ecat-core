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

import com.ecat.core.Bus.NotificationAction;

/**
 * discovery（设备自发现）通知的强类型 action——"发现新设备"提示携带的结构化数据。
 *
 * <p>字段为待处理发现的核心标识，供前端 actionable toast 直接消费：
 * <ul>
 *   <li>{@link #getFlowId()} ——【配置】按钮跳 {@code config-flow} 向导（复用 ?flowId 恢复）；</li>
 *   <li>{@link #getCoordinate()} / {@link #getUniqueId()} / {@link #getTitle()} ——【忽略】按钮建 IGNORE entry。</li>
 * </ul>
 *
 * <p>字段语义与 {@link DiscoveryFlowInfo}（/discoveries 列表 DTO）<strong>共用同一组字段</strong>——
 * 单一真相源，避免 discovery 字段在 Map / DTO 两处各表达一份（G-TYPE-3）。
 *
 * @author coffee
 */
public class DiscoveryNotificationAction implements NotificationAction {

    private final String flowId;
    /** 发现源（SourceType.name()：IMPORT_FLOW / ZEROCONF / MQTT）*/
    private final String source;
    private final String coordinate;
    private final String uniqueId;
    private final String title;

    public DiscoveryNotificationAction(String flowId, String source, String coordinate, String uniqueId, String title) {
        this.flowId = flowId;
        this.source = source;
        this.coordinate = coordinate;
        this.uniqueId = uniqueId;
        this.title = title;
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

    public String getUniqueId() {
        return uniqueId;
    }

    public String getTitle() {
        return title;
    }

    @Override
    public String getCategory() {
        return "discovery";
    }

    @Override
    public String toString() {
        return "DiscoveryNotificationAction{flowId=" + flowId + ", source=" + source
            + ", coordinate=" + coordinate + ", uniqueId=" + uniqueId + ", title=" + title + "}";
    }
}
