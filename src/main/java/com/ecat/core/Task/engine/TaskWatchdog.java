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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;

import com.ecat.core.Task.NamedThreadFactory;
import com.ecat.core.Utils.Log;
import com.ecat.core.Utils.LogFactory;

/**
 * 执法层：blocking 探针 + 慢任务看门狗 + 硬超时（单线程承担三项，线程预算 1(timer)+N(worker)+1(watchdog)）。
 *
 * <p>blocking 探针（1s，{@link #BLOCKING_THRESHOLD_MILLIS}）：任务运行超 1s 未完即计数
 * （blockingTasks，每执行一次）并进入 {@link #blockingSnapshot()} 当前清单——103000 观测补盲，
 * 「worker 被单任务钉死」从 jstack 偶然抓取变成 system_health 常设指标。
 *
 * <p>慢任务点名（默认 10s）：任务运行超阈值即采样执行线程栈帧打一行 WARN（前 5 帧，不打全栈——
 * 全栈进日志是 47GB/日 事故的成分之一），每次执行至多一行。这是「告警即违规」哲学在调度域的
 * 对应物：阻塞轮询当场点名车道与任务类，不再靠周期漂移猜。
 *
 * <p>硬超时（默认 30s）：中断执行线程 + 标记失败 + WARN 点名（车道键 + 任务类 + 已运行时长）。
 * 设计承认的边界：阻塞在不可中断 IO（如无超时参数的 native socket 读）时 interrupt 无效，
 * 该 worker 被占住直到 IO 自然返回——此时兜底是车道隔离：这个车道只烧掉自己那一个 worker，
 * 其他车道不受牵连；连续硬超时由熔断把该车道 OPEN 进冷却，worker 在冷却结束后由 probe
 * 之外的调度释放。不为此引入「替换线程」（线程总数铁律）。
 *
 * <p>中断竞窗说明：扫描快照里看到的 runner 可能在 interrupt 前一瞬完成了该任务并开始下一个，
 * 双重校验（重读 task.runner）把窗口压缩到指令级；即便命中，下一个任务收到的是一次可恢复的
 * 中断标志，且 worker 的中断卫生会在任务边界清掉。接受此残余风险（watchdog 周期 1s，远大于
 * 竞窗）。
 *
 * <p>测试注入：时钟注入 + {@link #scanOnce()} 手动驱动（禁 sleep 同步）。
 *
 * @author coffee
 */
final class TaskWatchdog {

    private static final Log log = LogFactory.getLogger(TaskWatchdog.class);

    private static final int REPORTED_STACK_FRAMES = 5;

    /**
     * blocking 探针阈值（毫秒）：任务运行超此值即计入 blockingTasks 计数并出现在
     * {@link #blockingSnapshot()} 清单——103000 观测补盲：归因时「worker 被单任务钉死」
     * 只能靠 jstack 偶然抓到（6/6 worker 阻塞在生产早段即可见、看门狗 slow 10s 点名之前），
     * 本阈值把钉死早段变成 system_health 常设指标。固定 1s（slow 阈值 10s 的早段）、
     * 不做配置：观测口径须跨代稳定可比。
     */
    static final long BLOCKING_THRESHOLD_MILLIS = 1_000L;

    private final SchedulerConfig config;
    private final SchedulerClock clock;
    private final SchedulerMetrics metrics;
    private final Set<EngineTask<?>> running = ConcurrentHashMap.newKeySet();
    private final Thread thread;

    TaskWatchdog(SchedulerConfig config, SchedulerClock clock, SchedulerMetrics metrics, boolean startThread) {
        this.config = config;
        this.clock = clock;
        this.metrics = metrics;
        if (startThread) {
            this.thread = new NamedThreadFactory("ecat-sched-watchdog", true).newThread(this::loop);
            thread.start();
        } else {
            this.thread = null;
        }
    }

    void register(EngineTask<?> task) {
        running.add(task);
    }

    /** 停机：中断扫描线程（parkNanos 提前返回后经中断检查退出）。 */
    void shutdown() {
        if (thread != null) {
            thread.interrupt();
        }
    }

    void unregister(EngineTask<?> task) {
        running.remove(task);
    }

    int runningCount() {
        return running.size();
    }

    /**
     * 当前运行超 blocking 阈值（{@link #BLOCKING_THRESHOLD_MILLIS}）的任务快照
     * （system_health 直读）：lane + 任务类 + 已跑 ms。读端点时组装，热路径零成本。
     */
    List<BlockingTaskStatus> blockingSnapshot() {
        long now = clock.nanoTime();
        long blockingNanos = TimeUnit.MILLISECONDS.toNanos(BLOCKING_THRESHOLD_MILLIS);
        List<BlockingTaskStatus> out = new ArrayList<>(running.size());
        for (EngineTask<?> task : running) {
            long runtimeNanos = now - task.startNanos;
            if (runtimeNanos >= blockingNanos) {
                out.add(new BlockingTaskStatus(task.laneKey, task.taskLabel, runtimeNanos / 1_000_000L));
            }
        }
        return out;
    }

    private void loop() {
        long intervalNanos = TimeUnit.MILLISECONDS.toNanos(config.getWatchdogIntervalMillis());
        long nextScan = clock.nanoTime() + intervalNanos;
        while (true) {
            long now = clock.nanoTime();
            if (now < nextScan) {
                LockSupport.parkNanos(nextScan - now);
                if (thread.isInterrupted()) {
                    return;
                }
                continue;
            }
            scanOnce();
            nextScan = clock.nanoTime() + intervalNanos;
        }
    }

    /** 单轮扫描：blocking 探针计数 + 慢任务点名 + 硬超时中断。生产由 watchdog 线程周期调用；测试手动调用。 */
    void scanOnce() {
        long now = clock.nanoTime();
        long blockingNanos = TimeUnit.MILLISECONDS.toNanos(BLOCKING_THRESHOLD_MILLIS);
        long slowNanos = TimeUnit.MILLISECONDS.toNanos(config.getSlowThresholdMillis());
        long hardNanos = TimeUnit.MILLISECONDS.toNanos(config.getHardTimeoutMillis());
        for (EngineTask<?> task : running) {
            long runtimeNanos = now - task.startNanos;
            if (runtimeNanos >= blockingNanos && !task.blockingReported) {
                task.blockingReported = true;
                metrics.getBlockingTasks().increment();
            }
            if (runtimeNanos >= hardNanos && !task.timedOut) {
                task.timedOut = true;
                metrics.getHardTimeouts().increment();
                Thread runner = task.runner;
                if (runner != null && task.runner == runner) {
                    runner.interrupt();
                }
                log.warn("SCHED_HARD_TIMEOUT lane={} task={} 运行 {}ms 超过硬超时 {}ms，已中断执行线程"
                                + "（不可中断 IO 时由车道隔离兜底）",
                        task.laneKey, task.taskLabel, runtimeNanos / 1_000_000L,
                        config.getHardTimeoutMillis());
            } else if (runtimeNanos >= slowNanos && !task.slowReported) {
                task.slowReported = true;
                metrics.getSlowTaskReports().increment();
                log.warn("SCHED_SLOW_TASK lane={} task={} 已运行 {}ms，栈顶: {}",
                        task.laneKey, task.taskLabel, runtimeNanos / 1_000_000L,
                        sampleFrames(task.runner));
            }
        }
    }

    private String sampleFrames(Thread runner) {
        if (runner == null) {
            return "(已退出)";
        }
        StackTraceElement[] stack = runner.getStackTrace();
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < stack.length && i < REPORTED_STACK_FRAMES; i++) {
            if (i > 0) {
                sb.append(" <- ");
            }
            sb.append(stack[i].getClassName()).append('.').append(stack[i].getMethodName())
                    .append(':').append(stack[i].getLineNumber());
        }
        return sb.length() == 0 ? "(空栈)" : sb.toString();
    }
}
