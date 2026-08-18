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

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * executorFor 显式车道视图独立 JVM 冒烟（非 JUnit 用例，main 直跑，不起 core）：
 * 100 个 {@code executorFor("modbus-source:smoke-k{i}")} 视图混跑 execute / submit /
 * CompletableFuture.supplyAsync 30s，同时挂几条 MDC 推导通道周期任务证明两通道共存。
 *
 * <p>通过判据（任一违反即退出码 1）：
 * ① 全程引擎线程集合恒等于 6 根（1 timer + 4 worker + 1 watchdog），无 per-key 线程泄漏
 *   （禁每次调用建池/线程是 executorFor 的设计红线）；
 * ② 活线程数相对基线增长 ≤ 6 + 3（JIT/GC 瞬态容忍；出现 modbus-source 名字线程直接判死）；
 * ③ rejected 计数为 0（压测未触顶）；
 * ④ 车道数 = 100 显式键 + 1 core（MDC 通道归并），视图对象按 key 幂等缓存。
 *
 * <p>main 里的 Thread.sleep 是 30s 稳态观测窗的采样节拍（冒烟语义），不是测试同步。
 * 运行：mvnd -q -DincludeScope=test dependency:build-classpath -Dmdep.outputFile=target/cp.txt
 *      java -cp target/classes:target/test-classes:$(cat target/cp.txt) <本类>
 */
public final class ExecutorForSmokeMain {

    private static final int KEYS = 100;
    private static final long RUN_SECONDS = 30;
    /** 引擎默认配置线程预算：1 timer + 4 worker + 1 watchdog。 */
    private static final int ENGINE_THREADS = 6;

    public static void main(String[] args) throws Exception {
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        int baselineLive = threads.getThreadCount();
        List<String> baselineNames = allThreadNames();

        SchedulerEngine engine = new SchedulerEngine();
        LongAdder executed = new LongAdder();
        AtomicLong periodicTicks = new AtomicLong();

        List<ExecutorService> views = new ArrayList<>();
        for (int i = 0; i < KEYS; i++) {
            ExecutorService view = engine.executorFor("modbus-source:smoke-k" + i);
            if (views.contains(view)) {
                fail("视图对象跨 key 复用异常");
            }
            views.add(view);
        }
        if (engine.executorFor("modbus-source:smoke-k0") != views.get(0)) {
            fail("同 key 视图未按缓存幂等返回（每次新建对象=薄视图红线违反）");
        }

        // MDC 推导通道共存：core 车道上的周期心跳（主线程提交、无 MDC 坐标 → "core" 车道）
        engine.scheduleWithFixedDelay(periodicTicks::incrementAndGet, 0, 1, TimeUnit.SECONDS);

        long start = System.nanoTime();
        int maxLiveDuringRun = 0;
        List<String> previousSchedThreads = null;
        int lane = 0;
        while (TimeUnit.NANOSECONDS.toSeconds(System.nanoTime() - start) < RUN_SECONDS) {
            // 每轮经一个视图混提三种形态（P2 modbus 的实际用法形态在内）
            ExecutorService view = views.get(lane % KEYS);
            lane++;
            view.execute(executed::increment);
            CompletableFuture.supplyAsync(() -> {
                executed.increment();
                return null;
            }, view);
            if (lane % KEYS == 0) {
                // 每整轮一次同步 get：Future 语义在视图上可用
                if (view.submit(() -> "ok").get(5, TimeUnit.SECONDS) == null) {
                    fail("submit(Callable) Future 结果异常");
                }
                executed.increment();
            }

            Thread.sleep(5L);   // 提交节拍（冒烟观测窗的一部分，非测试同步）
            if (lane % (KEYS * 4) == 0) {
                int live = threads.getThreadCount();
                maxLiveDuringRun = Math.max(maxLiveDuringRun, live);
                List<String> schedThreads = schedThreadNames();
                if (schedThreads.size() != ENGINE_THREADS) {
                    fail("引擎线程集合漂移: 期望 " + ENGINE_THREADS + " 根，实测 " + schedThreads);
                }
                if (previousSchedThreads != null && !previousSchedThreads.equals(schedThreads)) {
                    fail("调度线程集合发生变化: " + schedThreads);
                }
                previousSchedThreads = schedThreads;
                for (Thread t : Thread.getAllStackTraces().keySet()) {
                    if (t.getName().contains("modbus-source")) {
                        fail("出现 modbus-source 命名线程（per-key 建池红线违反）: " + t.getName());
                    }
                }
                if (live > baselineLive + ENGINE_THREADS + 3) {
                    fail("活线程数越界: 基线=" + baselineLive + " 实测=" + live);
                }
            }
        }
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        long rejected = engine.getMetrics().getRejected().sum();
        if (rejected != 0L) {
            fail("压测期间出现排队拒绝 rejected=" + rejected);
        }
        int expectedLanes = KEYS + 1;   // 100 显式键 + "core"（MDC 通道周期任务）
        if (engine.laneCount() != expectedLanes) {
            fail("车道数异常: 期望 " + expectedLanes + " 实测 " + engine.laneCount());
        }

        engine.shutdownNow();
        Thread.sleep(500L);

        System.out.println("=== executorFor 冒烟结果 ===");
        System.out.println("视图数: " + KEYS + "（每 key 缓存 1 个薄对象）");
        System.out.println("运行时长: " + elapsedMs + "ms");
        System.out.println("执行次数: " + executed.sum() + "（视图 execute/supplyAsync/submit 混跑）");
        System.out.println("MDC 通道周期心跳: " + periodicTicks.get() + " 次（core 车道，与显式车道共存）");
        System.out.println("线程: 基线=" + baselineLive + " 运行中峰值=" + maxLiveDuringRun
                + " 引擎线程恒=" + previousSchedThreads);
        System.out.println("车道数: " + expectedLanes + "（100 显式 + core）  rejected=0  车道排队余量="
                + engine.queuedTotal());
        List<String> newNames = allThreadNames();
        newNames.removeAll(baselineNames);
        newNames.removeAll(previousSchedThreads == null ? Collections.emptyList() : previousSchedThreads);
        System.out.println("停机后非引擎新增线程（JVM 运行期懒启动）: " + newNames);
        System.out.println("PASS: 100 车道视图混跑 " + RUN_SECONDS + "s，引擎线程恒 6 根无泄漏");
    }

    private static void fail(String message) {
        System.out.println("FAIL: " + message);
        System.exit(1);
    }

    private static List<String> allThreadNames() {
        List<String> names = new ArrayList<>();
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            names.add(t.getName());
        }
        Collections.sort(names);
        return names;
    }

    private static List<String> schedThreadNames() {
        List<String> names = new ArrayList<>();
        for (Thread t : Thread.getAllStackTraces().keySet()) {
            if (t.getName().startsWith("ecat-sched-")) {
                names.add(t.getName());
            }
        }
        Collections.sort(names);
        return names;
    }

    private ExecutorForSmokeMain() {
    }
}
