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

import java.util.concurrent.Callable;
import java.util.concurrent.Delayed;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RunnableScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 调度 v2 引擎的任务单元：{@link FutureTask} 之上叠加表轮链表、周期参数与执法层印记。
 *
 * <p>周期编码沿用 ScheduledThreadPoolExecutor 约定：{@code periodNanos > 0} = fixedRate
 * （下一轮 = 本轮名义起始时刻 + period），{@code periodNanos < 0} = fixedDelay（下一轮 =
 * 本轮完成时刻 + delay），0 = 一次性。141 个调用点对两种周期语义的既有依赖由此保持。
 *
 * <p>异常可见性：FutureTask.runAndReset 吞掉任务体异常（STPE 同款静默），执法层需要异常做
 * 熔断计数与死亡点名，故任务体先经 {@link Instrumented} 包装捕获失败再上抛，worker 在运行后
 * 读取 {@link Instrumented#failure}——同线程写读，无竞态。
 *
 * <p>线程纪律：表轮链表字段（bucket/prev/next/remainingRounds）只由驱动 expire 的线程触碰
 * （生产 = timer 线程；虚拟时钟测试 = 测试线程）；cancel 不改链表，由到期扫描惰性丢弃
 * （避免跨线程摘链的竞态，代价是被取消任务最长占用一个周期槽位，可忽略）。
 *
 * @author coffee
 */
final class EngineTask<V> extends FutureTask<V> implements RunnableScheduledFuture<V> {

    /** 捕获任务体失败的包装（执法层数据源；成功/失败判定统一收口在 worker 终态处）。 */
    static final class Instrumented<V> implements Callable<V> {
        private final Callable<V> delegate;
        volatile Throwable failure;

        Instrumented(Callable<V> delegate) {
            this.delegate = delegate;
        }

        @Override
        public V call() throws Exception {
            // 周期任务复用同一实例：执行成功时清掉上一轮的失败记录（worker 在每次执行后读取判定）
            failure = null;
            try {
                return delegate.call();
            } catch (Throwable t) {
                failure = t;
                throw t;
            }
        }
    }

    final long periodNanos;
    final String laneKey;
    /** 提交方任务类名（lambda 后缀已剥），供车道键回退与死亡/看门狗点名——原始类被 Instrumented 遮蔽。 */
    final String taskLabel;
    final Instrumented<V> body;
    private final SchedulerClock clock;

    /** 绝对到期时刻（纳秒）。周期任务每次重排前更新；worker 读取它做 fixedRate 名义网格推算。 */
    volatile long deadlineNanos;
    /** 本任务被熔断器以探测身份放行（取消时须归还探测名额）。 */
    volatile boolean admittedAsProbe;
    /** 看门狗硬超时印记：执行结果按失败计，且周期任务继续重排（见 worker 的超时续排决策）。 */
    volatile boolean timedOut;
    /** 看门狗慢任务点名是否已发（每次执行至多一行，避免周期扫描重复刷屏）。 */
    boolean slowReported;
    /** blocking 探针是否已计（每次执行至多一次，见 TaskWatchdog#BLOCKING_THRESHOLD_MILLIS）。 */
    boolean blockingReported;
    /** 执行线程引用：看门狗硬超时中断目标。双重校验后仍存在极小竞窗，见看门狗注释。 */
    volatile Thread runner;
    volatile long startNanos;

    // ---- 表轮链表（仅 expire 驱动线程访问；见类注释线程纪律） ----
    TimingWheel.Bucket bucket;
    EngineTask<?> next;
    EngineTask<?> prev;
    long remainingRounds;

    EngineTask(Instrumented<V> body, long deadlineNanos, long periodNanos, String laneKey,
               String taskLabel, SchedulerClock clock) {
        super(body);
        this.body = body;
        this.deadlineNanos = deadlineNanos;
        this.periodNanos = periodNanos;
        this.laneKey = laneKey;
        this.taskLabel = taskLabel;
        this.clock = clock;
    }

    @Override
    public boolean isPeriodic() {
        return periodNanos != 0L;
    }

    @Override
    public long getDelay(TimeUnit unit) {
        return unit.convert(deadlineNanos - clock.nanoTime(), TimeUnit.NANOSECONDS);
    }

    @Override
    public int compareTo(Delayed other) {
        if (other == this) {
            return 0;
        }
        if (other instanceof EngineTask) {
            return Long.compare(deadlineNanos, ((EngineTask<?>) other).deadlineNanos);
        }
        return Long.compare(getDelay(TimeUnit.NANOSECONDS), other.getDelay(TimeUnit.NANOSECONDS));
    }

    @Override
    public boolean cancel(boolean mayInterruptIfRunning) {
        // FutureTask 完成取消状态机；表轮/车道队列侧靠 isCancelled 惰性清理
        return super.cancel(mayInterruptIfRunning);
    }

    /** 失败摘要（熔断日志用一行，不打印全栈——全栈在 DEBUG/看门狗侧）。 */
    String failureSummary() {
        Throwable f = body.failure;
        if (f == null) {
            return timedOut ? "硬超时中断" : "无异常信息";
        }
        return f.getClass().getSimpleName() + ": " + String.valueOf(f.getMessage());
    }

    /** 以指定异常完成一次性任务（排队被拒等引擎侧失败；周期任务走 cancel 而非这里）。 */
    void failNow(Throwable failure) {
        setException(failure);
    }

    /**
     * worker 执行入口：FutureTask.runAndReset 是 protected（仅子类可调），周期执行走它
     * （不写结果、异常吞掉——由 Instrumented 捕获）；一次性走公共 run()（结果/异常入 Future）。
     */
    void runOnce() {
        if (isPeriodic()) {
            runAndReset();
        } else {
            run();
        }
    }
}
