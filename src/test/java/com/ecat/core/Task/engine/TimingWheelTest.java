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

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.Before;
import org.junit.Test;

/**
 * 表轮单测：虚拟时钟手动推进，全部确定性断言（无真实时间依赖、无 sleep）。
 *
 * <p>到期精度契约：deadline 向上取整到 tick 边界，任务在 [deadline, deadline+tick) 窗口内触发。
 */
public class TimingWheelTest {

    private static final long TICK_MS = 100L;
    private static final int SLOTS = 512;

    private MutableClock clock;
    private List<EngineTask<?>> fired;
    private TimingWheel wheel;

    @Before
    public void setUp() {
        clock = new MutableClock();
        fired = new ArrayList<>();
        wheel = new TimingWheel(SLOTS, TimeUnit.MILLISECONDS.toNanos(TICK_MS), clock, fired::add);
    }

    private EngineTask<Void> taskWithDeadlineMillis(long deadlineFromNowMillis) {
        return new EngineTask<>(new EngineTask.Instrumented<>(() -> null),
                clock.nanoTime() + TimeUnit.MILLISECONDS.toNanos(deadlineFromNowMillis),
                0L, "test-lane", "TestTask", clock);
    }

    @Test
    public void taskFiresWithinOneTickWindowAfterDeadline() {
        // delay=250ms、tick=100ms → 取整到第 3 个边界（300ms）：200ms 时未到期，300ms 时到期
        EngineTask<Void> task = taskWithDeadlineMillis(250);
        wheel.add(task);

        wheel.expireUpTo(clock.nanoTime());
        clock.advanceMillis(200);
        wheel.expireUpTo(clock.nanoTime());
        assertThat("200ms（第 2 个边界）不应触发 250ms 到期任务", fired.size(), is(0));

        clock.advanceMillis(100);
        wheel.expireUpTo(clock.nanoTime());
        assertThat("300ms（第 3 个边界）应触发 250ms 到期任务", fired.size(), is(1));
        assertThat((EngineTask<Void>) fired.get(0), is(task));
    }

    @Test
    public void taskDueExactlyAtBoundaryFiresOnThatBoundary() {
        EngineTask<Void> task = taskWithDeadlineMillis(TICK_MS);
        wheel.add(task);

        clock.advanceMillis(TICK_MS);
        wheel.expireUpTo(clock.nanoTime());
        assertThat(fired.size(), is(1));
    }

    @Test
    public void delayBeyondWheelSpanUsesRemainingRounds() {
        // 一圈 = 512 × 100ms = 51.2s；60s 延迟需要走 1 圈再 88 tick
        EngineTask<Void> task = taskWithDeadlineMillis(60_000);
        wheel.add(task);

        clock.advanceMillis(59_900);
        wheel.expireUpTo(clock.nanoTime());
        assertThat("59.9s 不应触发 60s 任务", fired.size(), is(0));

        clock.advanceMillis(100);
        wheel.expireUpTo(clock.nanoTime());
        assertThat("60.0s 应触发", fired.size(), is(1));
        assertThat("到期后表轮应清空", wheel.liveTaskCount(), is(0));
    }

    @Test
    public void lateWakeUpProcessesAllMissedBoundaries() {
        // tick 漂移补偿：驱动线程晚醒 5 个 tick，一次推进必须补扫全部错过边界
        for (int i = 1; i <= 5; i++) {
            wheel.add(taskWithDeadlineMillis(i * TICK_MS));
        }

        clock.advanceMillis(5 * TICK_MS);
        wheel.expireUpTo(clock.nanoTime());
        assertThat("5 个错过边界应全部补扫触发", fired.size(), is(5));
    }

    @Test
    public void cancelledTaskIsSkippedOnExpiry() {
        EngineTask<Void> task = taskWithDeadlineMillis(TICK_MS);
        wheel.add(task);
        assertThat(wheel.liveTaskCount(), is(1));

        assertThat(task.cancel(false), is(true));
        clock.advanceMillis(2 * TICK_MS);
        wheel.expireUpTo(clock.nanoTime());

        assertThat("被取消任务到期应被惰性丢弃", fired.size(), is(0));
        assertThat("丢弃后计数应归零", wheel.liveTaskCount(), is(0));
    }

    @Test
    public void zeroDelayTaskAddedAfterAdvanceFiresOnNextBoundary() {
        clock.advanceMillis(3 * TICK_MS);
        wheel.expireUpTo(clock.nanoTime());

        // 已过时刻的 deadline（catch-up 重排场景）：挂入 pending 后下个边界触发，不多等一圈
        EngineTask<Void> task = taskWithDeadlineMillis(-50);
        wheel.add(task);
        clock.advanceMillis(TICK_MS);
        wheel.expireUpTo(clock.nanoTime());
        assertThat(fired.size(), is(1));
    }

    @Test
    public void periodicTaskRearmFromExpiryHandlerFiresEveryPeriod() {
        // 表轮与重排闭环：到期回调里按 fixedRate 名义网格重挂（模拟引擎 fireTask→rearm 路径）
        long periodMillis = 200L;
        EngineTask<Void> periodic = new EngineTask<>(new EngineTask.Instrumented<>(() -> null),
                clock.nanoTime(), TimeUnit.MILLISECONDS.toNanos(periodMillis), "test-lane", "Periodic", clock);
        wheel.add(periodic);

        for (int round = 1; round <= 3; round++) {
            clock.advanceMillis(periodMillis);
            wheel.expireUpTo(clock.nanoTime());
            assertThat("第 " + round + " 轮应触发", fired.size(), is(round));
            // fixedRate 重排：名义网格 +period
            periodic.deadlineNanos += TimeUnit.MILLISECONDS.toNanos(periodMillis);
            wheel.add(periodic);
        }
        assertThat(wheel.liveTaskCount(), is(1));
    }

    @Test
    public void cancelPeriodicTasksCancelsOnlyPeriodic() {
        EngineTask<Void> periodic = new EngineTask<>(new EngineTask.Instrumented<>(() -> null),
                clock.nanoTime() + TimeUnit.MILLISECONDS.toNanos(50), TimeUnit.MILLISECONDS.toNanos(200L),
                "test-lane", "Periodic", clock);
        EngineTask<Void> oneShot = taskWithDeadlineMillis(50);
        wheel.add(periodic);
        wheel.add(oneShot);

        wheel.cancelPeriodicTasks();

        assertThat("周期任务应被取消", periodic.isCancelled(), is(true));
        assertThat("一次性任务不受影响", oneShot.isCancelled(), is(false));
        assertThat(wheel.liveTaskCount(), is(1));
    }

    @Test
    public void cancelPeriodicTasksAfterTransferKeepsOneShotInBucket() {
        // 覆盖槽内路径（上一用例两件任务都还在 pending）：先推进到任务已挂槽再取消
        EngineTask<Void> periodic = new EngineTask<>(new EngineTask.Instrumented<>(() -> null),
                clock.nanoTime() + TimeUnit.MILLISECONDS.toNanos(300), TimeUnit.MILLISECONDS.toNanos(200L),
                "test-lane", "Periodic", clock);
        EngineTask<Void> oneShot = taskWithDeadlineMillis(300);
        wheel.add(periodic);
        wheel.add(oneShot);
        clock.advanceMillis(100);                 // 首个边界：pending → 挂槽
        wheel.expireUpTo(clock.nanoTime());
        assertThat(wheel.liveTaskCount(), is(2));

        wheel.cancelPeriodicTasks();

        assertThat("槽内周期任务应被取消", periodic.isCancelled(), is(true));
        assertThat("槽内一次性任务保留", oneShot.isCancelled(), is(false));
        assertThat(wheel.liveTaskCount(), is(1));

        clock.advanceMillis(300);                 // 300ms 到期边界
        wheel.expireUpTo(clock.nanoTime());
        assertThat("保留的一次性任务应照常到期", fired.size(), is(1));
    }

    @Test
    public void drainAllReturnsAndClearsEverything() {
        EngineTask<Void> first = taskWithDeadlineMillis(50);
        EngineTask<Void> second = taskWithDeadlineMillis(50_000);
        wheel.add(first);
        wheel.add(second);

        java.util.List<EngineTask<?>> drained = wheel.drainAll();
        assertThat(drained.size(), is(2));
        assertThat(wheel.liveTaskCount(), is(0));

        clock.advanceMillis(100);
        wheel.expireUpTo(clock.nanoTime());
        assertThat("清空后不再触发", fired.size(), is(0));
    }
}
