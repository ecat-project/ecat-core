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

package com.ecat.core.Task.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 触发层：哈希表轮（自研最小实现，无 Netty 依赖）。
 *
 * <p>为什么用表轮而不是优先队列：万级周期任务下 ScheduledThreadPoolExecutor 的DelayedWorkQueue
 * 是 O(log n) 堆 + 全局锁；表轮入槽/到期是 O(1)，用「到期精度 = 1 个 tick 的抖动」换吞吐，
 * 设备轮询周期（秒级）对 100ms 抖动不敏感。
 *
 * <p>结构：默认 512 槽 × 100ms tick = 51.2s 一圈；超过一圈的延迟用 remainingRounds 计圈。
 * 任意线程经 {@link #add} 投入无锁 pending 队列，只有驱动 expire 的线程（生产 = timer 线程，
 * 测试 = 手动调用 {@link #expireUpTo} 的测试线程）把任务挂槽、扫槽——槽链表无锁也线程安全。
 *
 * <p>tick 漂移补偿：expireUpTo 以墙钟 now 为准把错过的每个 tick 边界逐个补扫（timer 线程
 * 被调度延迟 3 个 tick 后醒来会连续推进 3 格），任务不会因线程晚醒而多等一圈。
 *
 * <p>到期精度：deadline 向上取整到 tick 边界，任务在 [deadline, deadline + tick) 内到期——
 * 这是表轮的标准取舍。
 *
 * @author coffee
 */
final class TimingWheel {

    /** 到期回调：把任务交给执行层（车道队列）。实现方保证不抛异常（timer 线程存活是全局命脉）。 */
    interface ExpiryHandler {
        void expire(EngineTask<?> task);
    }

    /** 槽：双向链表 + 哑头，支持 O(1) 摘链（仅 expire 驱动线程访问）。 */
    static final class Bucket {
        EngineTask<?> head;

        void addLast(EngineTask<?> task) {
            if (head == null) {
                task.prev = null;
                task.next = null;
                head = task;
                return;
            }
            EngineTask<?> tail = head;
            while (tail.next != null) {
                tail = tail.next;
            }
            tail.next = task;
            task.prev = tail;
            task.next = null;
        }

        void unlink(EngineTask<?> task) {
            if (task.prev != null) {
                task.prev.next = task.next;
            } else if (head == task) {
                head = task.next;
            }
            if (task.next != null) {
                task.next.prev = task.prev;
            }
            task.prev = null;
            task.next = null;
        }
    }

    private final long tickNanos;
    private final int mask;
    private final Bucket[] wheel;
    private final ExpiryHandler handler;
    /** 表轮纪元（构造时的时钟读数）：tick t 覆盖 [epoch + t·tick, epoch + (t+1)·tick)。 */
    private final long epochNanos;
    private final ConcurrentLinkedQueue<EngineTask<?>> pending = new ConcurrentLinkedQueue<>();
    /** 在轮任务总数（槽 + pending），供停机判定与 B2 观测。 */
    private final AtomicInteger liveCount = new AtomicInteger();

    /** 下一个待处理窗口号（单调递增；仅 expire 驱动线程读写）。窗口 t 在 epoch+(t+1)·tick ≤ now 时到期。 */
    private long tick = 0L;

    TimingWheel(int slots, long tickNanos, SchedulerClock clock, ExpiryHandler handler) {
        this.tickNanos = tickNanos;
        this.mask = slots - 1;
        this.wheel = new Bucket[slots];
        for (int i = 0; i < slots; i++) {
            wheel[i] = new Bucket();
        }
        this.epochNanos = clock.nanoTime();
        this.handler = handler;
    }

    /** 任意线程可调：任务入 pending 队列，下次 tick 由驱动线程挂槽。 */
    void add(EngineTask<?> task) {
        pending.offer(task);
        liveCount.incrementAndGet();
    }

    /** 在轮任务数（含 pending）。 */
    int liveTaskCount() {
        return liveCount.get();
    }

    /** 下一个未流逝窗口的结束边界（epoch 对齐）：timer 线程睡到该时刻再 expireUpTo 精度最优。 */
    long nextBoundaryNanos(long now) {
        long elapsedWindows = (now - epochNanos) / tickNanos;
        return epochNanos + (elapsedWindows + 1L) * tickNanos;
    }

    /**
     * 推进表轮到墙钟时刻 now：补扫所有已完全流逝的 tick 窗口，到期任务交 handler。
     * 生产由 timer 线程调用；测试由测试线程用虚拟时钟手动调用（确定性，零真实等待）。
     */
    void expireUpTo(long now) {
        // 挂槽只依赖绝对 deadline 与纪元（不依赖「当前 tick 是否已追平」），transfer 与补扫的
        // 先后顺序因此无关紧要——这是对首版「按 stale tick + delay 挂槽」缺陷的根治：
        // 首次调用/长时间未驱动后补扫数百万窗口时，新任务不会再被提前扫掉。
        transferPending();
        while (epochNanos + (tick + 1) * tickNanos <= now) {
            Bucket bucket = wheel[(int) (tick & mask)];
            tick++;
            expireBucket(bucket);
        }
    }

    /**
     * 摘除并取消轮内全部周期任务（优雅停机时由 timer 线程调用，对齐 STPE 默认
     * continueExistingPeriodicTasksAfterShutdown=false：停机后周期任务不再触发）。
     * 一次性任务保留在轮内照常到期（STPE 默认 executeExistingDelayedTasksAfterShutdown=true）。
     */
    void cancelPeriodicTasks() {
        for (Bucket bucket : wheel) {
            cancelPeriodicIn(bucket);
        }
        // pending 分拣：周期取消，一次性放回（直接丢会静默吞掉未到期的延时任务）
        List<EngineTask<?>> backlog = new ArrayList<>();
        EngineTask<?> t;
        while ((t = pending.poll()) != null) {
            if (t.isPeriodic() && !t.isCancelled()) {
                t.cancel(false);
                liveCount.decrementAndGet();
            } else {
                backlog.add(t);
            }
        }
        pending.addAll(backlog);
    }

    private void cancelPeriodicIn(Bucket bucket) {
        EngineTask<?> t = bucket.head;
        while (t != null) {
            EngineTask<?> next = t.next;
            if (t.isPeriodic() && !t.isCancelled()) {
                // 只摘周期任务；一次性任务保留在槽内照常到期（不能顺手摘掉——那是静默吞任务）
                bucket.unlink(t);
                liveCount.decrementAndGet();
                t.cancel(false);
            }
            t = next;
        }
    }

    /**
     * 清空表轮并返回全部未执行任务（shutdownNow 用；必须在 timer 线程停止后调用，
     * 槽链表才无并发访问）。
     */
    List<EngineTask<?>> drainAll() {
        List<EngineTask<?>> drained = new ArrayList<>();
        for (Bucket bucket : wheel) {
            EngineTask<?> t = bucket.head;
            while (t != null) {
                EngineTask<?> next = t.next;
                bucket.unlink(t);
                drained.add(t);
                t = next;
            }
        }
        EngineTask<?> p;
        while ((p = pending.poll()) != null) {
            drained.add(p);
        }
        liveCount.addAndGet(-drained.size());
        return drained;
    }

    private void transferPending() {
        EngineTask<?> t;
        while ((t = pending.poll()) != null) {
            if (t.isCancelled()) {
                liveCount.decrementAndGet();
                continue;
            }
            // 到期窗口号：deadline 落在窗口 [f·tick, (f+1)·tick) 内，该窗口完全流逝（首个 ≥ deadline
            // 的边界）时触发——总误差 [0, tick)，即「向上取整到下一个 tick 边界」。
            long delta = t.deadlineNanos - epochNanos;
            long fireTick = delta <= 0L ? tick : (delta + tickNanos - 1) / tickNanos - 1L;
            if (fireTick < tick) {
                fireTick = tick;  // 已错过（catch-up 重排）：下一个被处理的窗口立即触发
            }
            long ahead = fireTick - tick;
            // 该槽索引在 tick..fireTick 间会被扫过 ahead/len 次，最后一次（rounds 归零）恰好 fireTick
            t.remainingRounds = ahead / wheel.length;
            wheel[(int) (fireTick & mask)].addLast(t);
        }
    }

    private void expireBucket(Bucket bucket) {
        EngineTask<?> t = bucket.head;
        while (t != null) {
            // 先存 next 再摘链：relink 分支会把任务挂回本槽尾，不存 next 会在同一任务上打转
            EngineTask<?> next = t.next;
            bucket.unlink(t);
            liveCount.decrementAndGet();
            boolean fire = false;
            if (!t.isCancelled()) {
                if (t.remainingRounds > 0L) {
                    t.remainingRounds--;
                    bucket.addLast(t);
                    liveCount.incrementAndGet();
                } else {
                    fire = true;
                }
            }
            if (fire) {
                handler.expire(t);
            }
            t = next;
        }
    }
}
