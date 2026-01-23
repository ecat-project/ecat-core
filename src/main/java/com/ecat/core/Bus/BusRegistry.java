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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;

// 订阅句柄接口，包含取消订阅方法
interface Subscription {
    void unsubscribe();
}

/**
 * BusRegistry 类实现了一个事件总线注册表，用于管理事件的订阅和发布
 * 支持通配符模式匹配主题，并使用线程池异步处理事件
 * 
 * @author coffee
 */
public class BusRegistry {

    // 使用Map存储主题和对应的订阅者列表，键为主题名称，值为订阅者列表
    private final Map<String, List<EventSubscriber>> subscribers = new HashMap<>();
    // 创建一个固定大小的线程池来处理异步任务
    private final ExecutorService executorService = Executors.newFixedThreadPool(2);

    // 订阅方法，返回一个 Subscription 对象用于取消订阅
    public Subscription subscribe(String topic, EventSubscriber subscriber) {
        List<EventSubscriber> subs = subscribers.computeIfAbsent(topic, k -> new ArrayList<>());
        if (!subs.contains(subscriber)) {
            subs.add(subscriber);
        }
        return () -> {
            List<EventSubscriber> subList = subscribers.get(topic);
            if (subList != null) {
                subList.remove(subscriber);
                if (subList.isEmpty()) {
                    subscribers.remove(topic);
                }
            }
        };
    }

    public void publish(String topic, Object eventData) {
        for (Map.Entry<String, List<EventSubscriber>> entry : subscribers.entrySet()) {
            String pattern = entry.getKey().replace("*", ".*");
            if (Pattern.matches(pattern, topic)) {
                for (EventSubscriber subscriber : entry.getValue()) {
                    // 将事件处理任务提交到线程池进行异步处理
                    executorService.submit(() -> subscriber.handleEvent(topic, eventData));
                }
            }
        }
    }

    // 关闭线程池的方法，在不再需要使用时调用
    public void shutdown() {
        executorService.shutdown();
    }
}  
