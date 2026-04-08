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

package com.ecat.core.Integration;

import com.ecat.core.Bus.BusTopic;
import com.ecat.core.Bus.BusTopic.DispatchMode;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * IntegrationManager 事件发布相关单元测试
 *
 * <p>验证 INTEGRATIONS_ALL_LOADED 事件的定义和分发模式：
 * <ul>
 *   <li>BusTopic 枚举包含 INTEGRATIONS_ALL_LOADED 值</li>
 *   <li>INTEGRATIONS_ALL_LOADED 的分发模式为 SYNC</li>
 * </ul>
 *
 * <p>注意：IntegrationManager 自身的 loadIntegrations() 方法涉及复杂的
 * 文件 I/O 和类加载，不适合在单元测试中直接调用。
 * 本测试专注于验证事件定义的正确性。
 *
 * @author coffee
 */
public class IntegrationManagerEventPublishTest {

    /**
     * 测试：BusTopic 枚举包含 INTEGRATIONS_ALL_LOADED 值
     */
    @Test
    public void testIntegrationsAllLoadedTopicExists() {
        // 验证枚举值存在
        BusTopic topic = BusTopic.INTEGRATIONS_ALL_LOADED;
        assertNotNull("INTEGRATIONS_ALL_LOADED 应存在于 BusTopic 枚举中", topic);

        // 验证 topic name 不为空
        assertNotNull("topicName 不应为 null", topic.getTopicName());
        assertFalse("topicName 不应为空", topic.getTopicName().isEmpty());

        // 验证 dataClass 为 Instant
        assertEquals("eventData 类型应为 Instant.class",
            java.time.Instant.class, topic.getDataClass());
    }

    /**
     * 测试：INTEGRATIONS_ALL_LOADED 的分发模式为 SYNC
     */
    @Test
    public void testSyncDispatchMode() {
        // 通过 resolveMode() 验证 INTEGRATIONS_ALL_LOADED 的分发模式
        DispatchMode mode = BusTopic.resolveMode(BusTopic.INTEGRATIONS_ALL_LOADED.getTopicName());
        assertEquals("INTEGRATIONS_ALL_LOADED 应使用 SYNC 分发模式",
            DispatchMode.SYNC, mode);
    }

    /**
     * 测试：LOGIC_DEVICES_ALL_LOADED 的分发模式也为 SYNC
     */
    @Test
    public void testLogicDevicesAllLoadedSyncDispatchMode() {
        DispatchMode mode = BusTopic.resolveMode(BusTopic.LOGIC_DEVICES_ALL_LOADED.getTopicName());
        assertEquals("LOGIC_DEVICES_ALL_LOADED 应使用 SYNC 分发模式",
            DispatchMode.SYNC, mode);
    }

    /**
     * 测试：非生命周期事件（如 DEVICE_DATA_UPDATE）默认为 ASYNC
     */
    @Test
    public void testDeviceDataUpdateAsyncDispatchMode() {
        DispatchMode mode = BusTopic.resolveMode(BusTopic.DEVICE_DATA_UPDATE.getTopicName());
        assertEquals("DEVICE_DATA_UPDATE 应使用 ASYNC 分发模式",
            DispatchMode.ASYNC, mode);
    }

    /**
     * 测试：未注册的 topic 默认为 ASYNC（向后兼容）
     */
    @Test
    public void testUnregisteredTopicDefaultAsync() {
        DispatchMode mode = BusTopic.resolveMode("some.unknown.topic");
        assertEquals("未注册的 topic 应默认为 ASYNC",
            DispatchMode.ASYNC, mode);
    }
}
