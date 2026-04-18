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

import com.ecat.core.State.AttributeBase;

import java.time.Instant;

/**
 * Enum for topic management
 * 
 * @author coffee
 */
public enum BusTopic {
    DEVICE_DATA_UPDATE("device.data.update", AttributeBase.class),
    INTEGRATIONS_ALL_LOADED("integration.all_loaded", Instant.class),
    LOGIC_DEVICES_ALL_LOADED("logic_device.all_loaded", Instant.class),
    CONFIG_ENTRY_LIFECYCLE("config.entry.lifecycle", ConfigEntryEvent.class);

    /**
     * Dispatch mode for bus events.
     * SYNC events block the publisher until all subscribers finish.
     * ASYNC events are dispatched to subscribers without blocking.
     */
    public enum DispatchMode {
        SYNC,
        ASYNC
    }

    private final String topicName;   // 名称，英文
    private final Class<?> dataClass; // 信息的数据类型
    BusTopic(String topicName, Class<?> dataClass) {
        this.topicName = topicName;
        this.dataClass = dataClass;
    }
    public String getTopicName() {
        return topicName;
    }
    public Class<?> getDataClass() {
        return dataClass;
    }

    /**
     * Resolve the dispatch mode for a given topic string.
     * <p>
     * Returns SYNC for lifecycle topics that require all subscribers to complete
     * before the publisher continues (e.g. integration.all_loaded, logic_device.all_loaded).
     * Returns ASYNC for all other topics.
     *
     * @param topic the topic name string
     * @return the dispatch mode for the topic
     */
    public static DispatchMode resolveMode(String topic) {
        if (INTEGRATIONS_ALL_LOADED.getTopicName().equals(topic)
                || LOGIC_DEVICES_ALL_LOADED.getTopicName().equals(topic)
                || CONFIG_ENTRY_LIFECYCLE.getTopicName().equals(topic)) {
            return DispatchMode.SYNC;
        }
        return DispatchMode.ASYNC;
    }

}
