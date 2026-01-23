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

/**
 * Enum for topic management
 * 
 * @author coffee
 */
public enum BusTopic {
    DEVICE_DATA_UPDATE("device.data.update", AttributeBase.class);
    
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
    
}
