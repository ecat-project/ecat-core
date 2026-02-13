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

package com.ecat.core.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 日志缓冲区
 *
 * <p>使用环形缓冲区存储日志条目，支持 SSE 订阅广播。
 *
 * <p>功能：
 * <ul>
 *   <li>存储最近 N 条日志（超出自动淘汰旧日志）</li>
 *   <li>支持多个 SSE 订阅者</li>
 *   <li>定时批量广播（减少网络开销）</li>
 * </ul>
 * 
 * @author coffee
 */
public class LogBuffer implements AutoCloseable {
    private final ConcurrentLinkedQueue<LogEntry> buffer;
    private final int maxCapacity;
    private final CopyOnWriteArraySet<LogSubscriber> subscribers;
    private final ConcurrentHashMap<LogSubscriber, Long> subscriberTimestamps;
    private final PriorityQueue<LogEntry> broadcastQueue;
    private ScheduledExecutorService broadcastScheduler;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    private static final long BROADCAST_DELAY_MS = 50L;

    public LogBuffer(int maxCapacity) {
        this.maxCapacity = maxCapacity;
        this.buffer = new ConcurrentLinkedQueue<>();
        this.subscribers = new CopyOnWriteArraySet<>();
        this.subscriberTimestamps = new ConcurrentHashMap<>();
        this.broadcastQueue = new PriorityQueue<>(Comparator.comparingLong(LogEntry::getTimestamp));
        startBroadcastScheduler();
    }

    private void startBroadcastScheduler() {
        broadcastScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "LogBuffer-Broadcast");
            t.setDaemon(true);
            return t;
        });
        broadcastScheduler.scheduleWithFixedDelay(this::flushBroadcastQueue, BROADCAST_DELAY_MS, BROADCAST_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    /**
     * 添加日志条目到缓冲区
     *
     * @param entry 日志条目
     */
    public void put(LogEntry entry) {
        if (closed.get()) {
            return;
        }
        buffer.add(entry);
        synchronized (broadcastQueue) {
            broadcastQueue.offer(entry);
        }
        // 超出容量时移除旧日志
        while (buffer.size() > maxCapacity) {
            buffer.poll();
        }
    }

    /**
     * 刷新广播队列
     */
    private void flushBroadcastQueue() {
        if (closed.get() || subscribers.isEmpty()) {
            return;
        }
        List<LogEntry> toBroadcast = new ArrayList<>();
        synchronized (broadcastQueue) {
            while (!broadcastQueue.isEmpty()) {
                toBroadcast.add(broadcastQueue.poll());
            }
        }
        if (toBroadcast.isEmpty()) {
            return;
        }
        // 按时间戳排序
        toBroadcast.sort(Comparator.comparingLong(LogEntry::getTimestamp));
        for (LogEntry entry : toBroadcast) {
            broadcastToSubscribers(entry);
        }
    }

    /**
     * 广播日志到所有订阅者
     *
     * @param entry 日志条目
     */
    private void broadcastToSubscribers(LogEntry entry) {
        if (subscribers.isEmpty() || closed.get()) {
            return;
        }
        subscribers.removeIf(subscriber -> {
            try {
                Long subscribeTime = subscriberTimestamps.get(subscriber);
                if (subscribeTime == null || entry.getTimestamp() >= subscribeTime) {
                    subscriber.send(entry);
                }
                return false;
            } catch (IOException e) {
                subscriberTimestamps.remove(subscriber);
                return true;
            }
        });
    }

    /**
     * 获取最近的日志
     *
     * @param limit 最大数量
     * @return 日志列表
     */
    public List<LogEntry> getRecent(int limit) {
        List<LogEntry> all = new ArrayList<>(buffer);
        all.sort(Comparator.comparingLong(LogEntry::getTimestamp));
        if (all.size() <= limit) {
            return all;
        }
        return all.subList(all.size() - limit, all.size());
    }

    /**
     * 获取所有日志
     *
     * @return 日志列表
     */
    public List<LogEntry> getAll() {
        List<LogEntry> all = new ArrayList<>(buffer);
        all.sort(Comparator.comparingLong(LogEntry::getTimestamp));
        return all;
    }

    /**
     * 清空缓冲区
     */
    public void clear() {
        buffer.clear();
        synchronized (broadcastQueue) {
            broadcastQueue.clear();
        }
    }

    /**
     * 获取缓冲区大小
     *
     * @return 日志数量
     */
    public int size() {
        return buffer.size();
    }

    /**
     * 订阅日志
     *
     * @param subscriber 订阅者
     */
    public void subscribe(LogSubscriber subscriber) {
        if (!closed.get() && subscriber != null) {
            boolean added = subscribers.add(subscriber);
            if (added) {
                subscriberTimestamps.put(subscriber, System.currentTimeMillis());
            }
        }
    }

    /**
     * 取消订阅
     *
     * @param subscriber 订阅者
     */
    public void unsubscribe(LogSubscriber subscriber) {
        subscribers.remove(subscriber);
        subscriberTimestamps.remove(subscriber);
    }

    /**
     * 获取订阅者数量
     *
     * @return 订阅者数量
     */
    public int getSubscriberCount() {
        return subscribers.size();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            flushBroadcastQueue();
            if (broadcastScheduler != null) {
                broadcastScheduler.shutdown();
                try {
                    broadcastScheduler.awaitTermination(1L, TimeUnit.SECONDS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            for (LogSubscriber subscriber : subscribers) {
                try {
                    subscriber.close();
                } catch (Exception e) {
                    // ignore
                }
            }
            subscribers.clear();
        }
    }
}
