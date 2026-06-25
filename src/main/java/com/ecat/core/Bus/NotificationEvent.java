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
 * 通知 Bus 事件。
 *
 * <p>任何 ecat 模块可通过 Bus 发布此事件（{@link BusTopic#NOTIFICATION}），
 * EventController 订阅后转为 NotificationPayload 推送至前端。
 *
 * <p>使用示例：
 * <pre>
 * // 纯提示通知
 * busRegistry.publish(BusTopic.NOTIFICATION.getTopicName(),
 *     new NotificationEvent("ERROR", "device.communication", "Modbus TCP 连接超时: 192.168.1.100:502"));
 *
 * // actionable 通知（带强类型 action，前端可据 action 执行动作）
 * busRegistry.publish(BusTopic.NOTIFICATION.getTopicName(),
 *     new NotificationEvent("INFO", "discovery", "发现新设备：XXX",
 *         new DiscoveryNotificationAction(flowId, source, coordinate, uniqueId, title)));
 * </pre>
 *
 * @author coffee
 */
public class NotificationEvent {

    /** 通知级别: INFO, WARNING, ERROR, CRITICAL */
    private final String level;

    /** 通知分类: device.communication, integration.error, discovery, system.health 等 */
    private final String category;

    /** 通知消息（人类可读） */
    private final String message;

    /**
     * 可选强类型 action（如 discovery 通知的 {@code DiscoveryNotificationAction}，携带 flowId/coordinate 等）。
     * <p>null 表示纯提示通知（无 action）；非 null 时前端可据 action 执行动作（如点击跳转向导）。
     * <p>用 {@link NotificationAction} 接口而非 {@code Map<String,Object>}——按 category 类型化分发，
     * 消费方有类型保障、key 不会拼错（严格模式：闭合已知字段必须强类型）。
     */
    private final NotificationAction action;

    /**
     * 构造纯提示通知（无 action）。
     *
     * @param level    通知级别，不能为 null
     * @param category 通知分类，不能为 null
     * @param message  通知消息，不能为 null
     * @throws IllegalArgumentException 如果任何参数为 null
     */
    public NotificationEvent(String level, String category, String message) {
        this(level, category, message, null);
    }

    /**
     * 构造带强类型 action 的通知（如 discovery 提示带 flowId）。
     *
     * @param level    通知级别，不能为 null
     * @param category 通知分类，不能为 null
     * @param message  通知消息，不能为 null
     * @param action   强类型 action 负载（可为 null）；线层由 EventController 按 category 序列化为 JSON
     * @throws IllegalArgumentException 如果 level/category/message 为 null
     */
    public NotificationEvent(String level, String category, String message, NotificationAction action) {
        if (level == null || category == null || message == null) {
            throw new IllegalArgumentException("level, category, message must not be null");
        }
        this.level = level;
        this.category = category;
        this.message = message;
        this.action = action;
    }

    /** 获取通知级别 */
    public String getLevel() {
        return level;
    }

    /** 获取通知分类 */
    public String getCategory() {
        return category;
    }

    /** 获取通知消息 */
    public String getMessage() {
        return message;
    }

    /** 获取强类型 action（null 表示纯提示无 action）。 */
    public NotificationAction getAction() {
        return action;
    }

    @Override
    public String toString() {
        return "NotificationEvent{level=" + level + ", category=" + category
            + ", message=" + message + (action != null ? ", action=" + action : "") + "}";
    }
}
