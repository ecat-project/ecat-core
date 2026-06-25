/*
 * Copyright (c) 2026 ECAT Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.ecat.core.Bus;

/**
 * 类型化通知 action 负载标记接口——让 {@link NotificationEvent} 携带<strong>强类型</strong>、
 * 按 category 类型化分发的结构化数据，而非用 {@code Map<String,Object>} 做"多态信封"
 * （Map 透传已知字段会导致：消费方零类型保障、key 拼错即静默失败、与领域 DTO 字段重复表达）。
 *
 * <p>每类 action 实现为<strong>独立强类型 class</strong>（如 discovery 提示的
 * {@code DiscoveryNotificationAction}），字段闭合、已知、有领域语义。
 *
 * <p><b>线层序列化</b>（强类型 → JSON → SSE 前端）由 EventController 按实现类型处理（W2 边界：
 * 对外传输的序列化层，源头已强类型，JSONObject 在此合理）。ecat-core 本身不依赖 JSON 库。
 *
 * @author coffee
 */
public interface NotificationAction {

    /**
     * 该 action 的分类判别符——与 {@link NotificationEvent#getCategory()} 对应
     * （如 {@code "discovery"}），供 EventController 按 category 路由序列化。
     *
     * @return 分类名（非 null）
     */
    String getCategory();
}
