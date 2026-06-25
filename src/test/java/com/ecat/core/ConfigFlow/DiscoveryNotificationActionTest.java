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

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@link DiscoveryNotificationAction} 单测——验证 discovery 通知的强类型 action 字段与类型化分发判别符。
 *
 * <p>这是"actionable notification 用强类型而非 Map 透传"（G-TYPE）的落点：闭合已知字段
 * 用 final 字段 + getter 表达，消费方有类型保障。
 *
 * @author coffee
 */
public class DiscoveryNotificationActionTest {

    @Test
    public void testFieldsAndCategory() {
        DiscoveryNotificationAction action = new DiscoveryNotificationAction(
                "flow-001", "IMPORT_FLOW", "com.ecat:integration-x", "uid-001", "发现设备A");

        assertEquals("flowId", "flow-001", action.getFlowId());
        assertEquals("source", "IMPORT_FLOW", action.getSource());
        assertEquals("coordinate", "com.ecat:integration-x", action.getCoordinate());
        assertEquals("uniqueId", "uid-001", action.getUniqueId());
        assertEquals("title", "发现设备A", action.getTitle());
        assertEquals("category 判别符", "discovery", action.getCategory());
    }

    @Test
    public void testImplementsNotificationAction() {
        // 作为 NotificationAction 接口实例使用（类型化分发的多态基础）
        NotificationAction action = new DiscoveryNotificationAction(
                "f", "ZEROCONF", "c", "u", "t");
        assertTrue("应实现 NotificationAction", action instanceof DiscoveryNotificationAction);
        assertEquals("discovery", action.getCategory());
    }

    @Test
    public void testToStringContainsKeyFields() {
        DiscoveryNotificationAction action = new DiscoveryNotificationAction(
                "flow-002", "MQTT", "com.ecat:y", "uid-002", "MQTT设备");
        String s = action.toString();
        assertTrue("toString 含 flowId", s.contains("flow-002"));
        assertTrue("toString 含 coordinate", s.contains("com.ecat:y"));
        assertTrue("toString 含 uniqueId", s.contains("uid-002"));
    }
}
