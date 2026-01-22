package com.ecat.core.Bus;
// 事件订阅者接口
public interface EventSubscriber {

    void handleEvent(String topic, Object eventData);
}