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

package com.ecat.core.Bus;

/**
 * 通知 Bus 事件。
 *
 * <p>任何 ecat 模块可通过 Bus 发布此事件（{@link BusTopic#NOTIFICATION}），
 * EventController 订阅后转为 NotificationPayload 推送至前端。
 *
 * <p>使用示例：
 * <pre>
 * busRegistry.publish(
 *     BusTopic.NOTIFICATION.getTopicName(),
 *     new NotificationEvent("ERROR", "device.communication", "Modbus TCP 连接超时: 192.168.1.100:502"));
 * </pre>
 *
 * @author coffee
 */
public class NotificationEvent {

    /** 通知级别: INFO, WARNING, ERROR, CRITICAL */
    private final String level;

    /** 通知分类: device.communication, integration.error, system.health 等 */
    private final String category;

    /** 通知消息（人类可读） */
    private final String message;

    /**
     * 构造通知事件。
     *
     * @param level    通知级别，不能为 null
     * @param category 通知分类，不能为 null
     * @param message  通知消息，不能为 null
     * @throws IllegalArgumentException 如果任何参数为 null
     */
    public NotificationEvent(String level, String category, String message) {
        if (level == null || category == null || message == null) {
            throw new IllegalArgumentException("level, category, message must not be null");
        }
        this.level = level;
        this.category = category;
        this.message = message;
    }

    /** 获取通知级别 */
    public String getLevel() { return level; }

    /** 获取通知分类 */
    public String getCategory() { return category; }

    /** 获取通知消息 */
    public String getMessage() { return message; }

    @Override
    public String toString() {
        return "NotificationEvent{level=" + level + ", category=" + category
            + ", message=" + message + "}";
    }
}
