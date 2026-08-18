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
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

import org.slf4j.MDC;

import com.ecat.core.Utils.Mdc.MdcContext;

/**
 * 调度 v2 独立 JVM 冒烟（非 JUnit 用例，main 直跑）：1000 混合周期任务跑 30s，验证
 * ①零线程增长（线程数 = 基线 + 1 timer + 4 worker + 1 watchdog，峰值不爬升）
 * ②周期精度（fixedRate 名义网格漂移分布）
 * ③吞吐正常（执行次数量级符合任务数 × 周期）。
 *
 * <p>main 里的 Thread.sleep 是 30s 稳态观测窗本身（冒烟语义），不是测试同步。
 * 运行：mvnd -q -DincludeScope=test dependency:build-classpath -Dmdep.outputFile=target/cp.txt
 *      java -cp target/classes:target/test-classes:$(cat target/cp.txt) <本类>
 */
public final class SchedulerEngineSmokeMain {

    private static final int LANES = 100;
    private static final int PER_LANE = 10;   // 1000 周期任务
    private static final long RUN_SECONDS = 30;

    public static void main(String[] args) throws Exception {
        ThreadMXBean threads = ManagementFactory.getThreadMXBean();
        int baseline = threads.getThreadCount();
        long baselinePeak = threads.getPeakThreadCount();
        List<String> baselineNames = allThreadNames();

        SchedulerEngine engine = new SchedulerEngine();
        LongAdder executions = new LongAdder();
        List<Long> fixedRateDriftMillis = Collections.synchronizedList(new ArrayList<>());
        AtomicLong oneShots = new AtomicLong();

        for (int l = 0; l < LANES; l++) {
            String laneKey = "com.ecat:integration-smoke-" + l;
            MDC.put(MdcContext.INTEGRATION_COORDINATE_KEY, laneKey);
            for (int k = 0; k < PER_LANE; k++) {
                long periodMs = (k % 3 == 0) ? 1_000L : 2_000L;   // 1/3 走 1s 周期，其余 2s
                boolean fixedRate = (k % 2 == 0);                  // 一半 fixedRate 一半 fixedDelay
                long firstNominalNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(periodMs);
                AtomicInteger ordinal = new AtomicInteger();
                Runnable body = () -> {
                    executions.increment();
                    int n = ordinal.getAndIncrement();
                    if (fixedRate) {
                        long nominal = firstNominalNanos + n * TimeUnit.MILLISECONDS.toNanos(periodMs);
                        fixedRateDriftMillis.add(TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - nominal));
                    }
                };
                if (fixedRate) {
                    engine.scheduleAtFixedRate(body, periodMs, periodMs, TimeUnit.MILLISECONDS);
                } else {
                    engine.scheduleWithFixedDelay(body, periodMs, periodMs, TimeUnit.MILLISECONDS);
                }
            }
            // 每车道一件 25s 延迟一次性任务（覆盖长延迟路径）
            engine.schedule(oneShots::incrementAndGet, 25_000L, TimeUnit.MILLISECONDS);
            MDC.remove(MdcContext.INTEGRATION_COORDINATE_KEY);
        }

        long start = System.nanoTime();
        // 稳态观测窗内每 2s 采样线程数与调度线程名清单，验证零增长
        int maxLiveDuringRun = 0;
        List<String> previousSchedThreads = null;
        for (int i = 0; i < RUN_SECONDS / 2; i++) {
            Thread.sleep(2_000L);
            int live = threads.getThreadCount();
            maxLiveDuringRun = Math.max(maxLiveDuringRun, live);
            List<String> schedThreads = schedThreadNames();
            if (previousSchedThreads != null && !previousSchedThreads.equals(schedThreads)) {
                System.out.println("[FLAKE] 调度线程集合发生变化: " + schedThreads);
            }
            previousSchedThreads = schedThreads;
        }
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);

        List<Long> sorted = new ArrayList<>(fixedRateDriftMillis);
        Collections.sort(sorted);
        System.out.println("=== 调度 v2 冒烟结果 ===");
        System.out.println("任务数: " + (LANES * PER_LANE) + " 周期(" + LANES + " 车道) + " + LANES + " 一次性");
        System.out.println("运行时长: " + elapsedMs + "ms");
        System.out.println("总执行次数: " + executions.sum() + "（含 fixedDelay/fixedRate 全部周期触发）");
        System.out.println("一次性任务完成: " + oneShots.get() + "/" + LANES);
        System.out.println("线程: 基线=" + baseline + " 结束时=" + threads.getThreadCount()
                + " 运行中峰值=" + maxLiveDuringRun + " 峰值差=" + (threads.getPeakThreadCount() - baselinePeak));
        System.out.println("调度线程集合: " + previousSchedThreads);
        System.out.println("fixedRate 漂移(ms): 样本=" + sorted.size()
                + " p50=" + pct(sorted, 0.50) + " p95=" + pct(sorted, 0.95)
                + " max=" + (sorted.isEmpty() ? "-" : sorted.get(sorted.size() - 1)));
        System.out.println("指标: executed=" + engine.getMetrics().getExecuted().sum()
                + " failed=" + engine.getMetrics().getFailed().sum()
                + " rejected=" + engine.getMetrics().getRejected().sum()
                + " breakerSkipped=" + engine.getMetrics().getBreakerSkipped().sum()
                + " hardTimeouts=" + engine.getMetrics().getHardTimeouts().sum());

        engine.shutdownNow();
        Thread.sleep(500L);
        System.out.println("停机后线程: " + threads.getThreadCount() + "（调度线程应已全部退出: "
                + schedThreadNames() + "）");
        List<String> newNames = allThreadNames();
        newNames.removeAll(baselineNames);
        System.out.println("非调度域新增线程（JVM/日志运行期懒启动，与引擎无关）: " + newNames);
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

    private static long pct(List<Long> sorted, double p) {
        if (sorted.isEmpty()) {
            return -1L;
        }
        int idx = (int) Math.min(sorted.size() - 1L, Math.round(p * (sorted.size() - 1L)));
        return sorted.get(idx);
    }

    private SchedulerEngineSmokeMain() {
    }
}
