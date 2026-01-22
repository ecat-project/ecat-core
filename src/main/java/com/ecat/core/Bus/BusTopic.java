package com.ecat.core.Bus;

import com.ecat.core.State.AttributeBase;

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
