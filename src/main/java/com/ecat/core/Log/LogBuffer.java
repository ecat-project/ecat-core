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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 日志缓冲区
 *
 * <p>使用缓冲区存储最近 N 条日志条目，支持 SSE 订阅推送。
 *
 * <p>设计原则：
 * <ul>
 *   <li>LogEntry 只存储一份（单一引用，ring 为唯一长期引用），避免内存泄漏</li>
 *   <li>超出容量时自动淘汰旧日志（ring 容量即 SSE 投递背压上界，投递跟不上时丢最旧）</li>
 *   <li>支持 SSE 订阅者：put() 只写 ring + 发唤醒信号，由懒启动的单写者投递线程异步推送，
 *       与 put 解耦——慢/阻塞的 SSE 订阅者不阻塞日志热路径，且投递时不持任何 ecat 锁
 *       （消除旧的 CopyOnWrite 锁 ↔ SSE 连接锁 顺序反转死锁）</li>
 * </ul>
 *
 * @author coffee
 */
public class LogBuffer implements AutoCloseable {
    private final ConcurrentLinkedQueue<LogEntry> buffer;
    private final int maxCapacity;
    private final CopyOnWriteArraySet<LogSubscriber> subscribers;
    private final ConcurrentHashMap<LogSubscriber, Long> subscriberTimestamps;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    // ========== 异步投递（ring 驱动的信号式单写者，与 put 解耦） ==========
    // 投递唤醒信号：put 时 release（不携带 LogEntry，保持「ring 为 LogEntry 唯一长期引用」的不变量，
    // 避免历史广播队列的双引用内存泄漏）；单写者投递线程 acquire 后读 ring 增量投递。
    private final Semaphore deliverySignal = new Semaphore(0);
    // 单写者投递线程，懒启动（首个订阅者到来才起）；null 表示从未启动。volatile 供双检。
    private volatile Thread broadcasterThread;
    // ensureBroadcasterStarted 的幂等临界区锁
    private final Object broadcasterStartLock = new Object();
    // 已投递高水位（已投递条目的最大 seq）：仅由单写者投递线程维护，避免重复投递同一增量。
    // 用 seq（非毫秒时间戳）作游标——同毫秒并发日志 timestamp 相同,按 ts 去重会丢;seq 唯一递增无碰撞。
    // 新订阅者靠「订阅时间过滤 + 订阅时历史拉取」补齐，与本字段正交。
    private volatile long lastDeliveredSeq = 0L;
    // LogEntry seq 分配器（put 时 incrementAndGet），保证每条日志唯一递增序号。
    private final AtomicLong seqCounter = new AtomicLong(0);

    public LogBuffer(int maxCapacity) {
        this.maxCapacity = maxCapacity;
        this.buffer = new ConcurrentLinkedQueue<>();
        this.subscribers = new CopyOnWriteArraySet<>();
        this.subscriberTimestamps = new ConcurrentHashMap<>();
    }

    /**
     * 添加日志条目到缓冲区，并唤醒投递线程异步推送给 SSE 订阅者。
     *
     * <p>本方法不阻塞、不做 I/O：只写 ring + 发信号。投递由单写者线程在另一线程完成，
     * 慢/阻塞的 SSE 订阅者不会卡住 put（日志热路径），且 ring 容量即背压上界（投递跟不上时丢最旧）。
     *
     * @param entry 日志条目
     */
    public void put(LogEntry entry) {
        if (closed.get()) {
            return;
        }
        entry.setSeq(seqCounter.incrementAndGet());
        buffer.add(entry);
        // 超出容量时移除旧日志（ring 自带丢最旧背压）
        while (buffer.size() > maxCapacity) {
            buffer.poll();
        }
        // 有订阅者时唤醒投递线程：只发信号（不携带 LogEntry），投递与 put 解耦
        if (!subscribers.isEmpty()) {
            deliverySignal.release();
        }
    }

    /**
     * 投递 ring 增量到所有订阅者（仅由单写者投递线程调用）。
     *
     * <p>关键不变量（消除 SSE 广播死锁根因）：
     * <ul>
     *   <li>调用 {@code subscriber.send}（取 Undertow SSE 连接锁）时，不持有任何 ecat 锁。
     *       旧实现用 {@code subscribers.removeIf} 在持 CopyOnWrite 锁期间做 I/O，
     *       与 SSE 连接关闭回调（持 SSE 连接锁）里的 {@code subscribers.remove}（取 CopyOnWrite 锁）
     *       形成 CopyOnWrite 锁 ↔ SSE 连接锁的锁顺序反转死锁。本实现先无锁快照订阅者、再逐个 send，
     *       send 时一锁不持，即便与 SSE 关闭路径争连接锁也只是普通竞争、无环。</li>
     *   <li>ring 为 LogEntry 唯一长期引用（防双引用内存泄漏）；{@code toDeliver} 仅本方法栈上暂存，
     *       返回即释放，不引入第二份长期引用。</li>
     * </ul>
     *
     * <p>丢最旧：投递慢于生产时，ring 按容量淘汰最旧，本方法只投递还留在 ring 里的增量，被淘汰的即丢弃。
     */
    private void deliverDelta() {
        if (subscribers.isEmpty() || closed.get()) {
            return;
        }
        long fromSeq = lastDeliveredSeq;
        List<LogEntry> toDeliver = new ArrayList<>();
        long maxSeq = fromSeq;
        for (LogEntry e : buffer) { // ConcurrentLinkedQueue 弱一致快照，仅栈上暂存
            long seq = e.getSeq();
            if (seq > fromSeq) {
                toDeliver.add(e);
                if (seq > maxSeq) {
                    maxSeq = seq;
                }
            }
        }
        if (toDeliver.isEmpty()) {
            return;
        }
        LogSubscriber[] snapshot = subscribers.toArray(new LogSubscriber[0]);
        List<LogSubscriber> dead = null;
        boolean interrupted = Thread.currentThread().isInterrupted();
        for (LogSubscriber sub : snapshot) {
            if (closed.get() || interrupted) {
                break; // close 中断时尽快退出，剩余订阅者随 close 终止
            }
            Long subscribeTime = subscriberTimestamps.get(sub);
            for (LogEntry e : toDeliver) {
                // 订阅时间过滤：只发订阅后产生的日志，避免与订阅时历史拉取重复
                if (subscribeTime != null && e.getTimestamp() < subscribeTime) {
                    continue;
                }
                try {
                    sub.send(e);
                } catch (Throwable t) {
                    // 任一订阅者发送失败（IOException 或运行时异常）不得拖死投递线程：记录后踢出
                    if (dead == null) {
                        dead = new ArrayList<>();
                    }
                    dead.add(sub);
                    break;
                }
            }
            interrupted = Thread.currentThread().isInterrupted();
        }
        lastDeliveredSeq = maxSeq;
        if (dead != null) {
            for (LogSubscriber s : dead) {
                subscribers.remove(s);
                subscriberTimestamps.remove(s);
            }
        }
    }

    /**
     * 单写者投递线程主循环：阻塞等信号 → 排空累积信号（合并多次 put 为一次投递）→ 投递 ring 增量。
     */
    private void runBroadcaster() {
        while (!closed.get()) {
            deliverySignal.acquireUninterruptibly();
            if (closed.get()) {
                return;
            }
            deliverySignal.drainPermits(); // 合并：多次唤醒只触发一次增量投递
            try {
                deliverDelta();
            } catch (Throwable t) {
                // deliverDelta 已对单订阅者异常兜底；此处仅防御性保命——投递线程绝不能因任何异常退出，
                // 否则后续日志将无人投递。吞掉并继续等下一轮信号。
            }
        }
    }

    /**
     * 懒启动单写者投递线程（首个订阅者到来时调用）。幂等：已存活则跳过。
     */
    private void ensureBroadcasterStarted() {
        if (broadcasterThread != null && broadcasterThread.isAlive()) {
            return;
        }
        synchronized (broadcasterStartLock) {
            if (broadcasterThread != null && broadcasterThread.isAlive()) {
                return;
            }
            Thread t = new Thread(this::runBroadcaster, "log-broadcast");
            t.setDaemon(true);
            t.start();
            broadcasterThread = t;
        }
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
            // 懒启动单写者投递线程：有订阅者才有投递需求；线程守护，无订阅者时本就不会启动
            ensureBroadcasterStarted();
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
            // 先唤醒并停止投递线程（若已懒启动），避免它在关闭过程中继续 send
            deliverySignal.release();
            Thread t = broadcasterThread;
            if (t != null) {
                t.interrupt(); // 解除投递线程可能在 subscriber.send 上的阻塞
                try {
                    t.join(2000);
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
            subscriberTimestamps.clear();
        }
    }
}
