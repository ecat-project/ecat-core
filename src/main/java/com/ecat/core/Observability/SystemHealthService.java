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

package com.ecat.core.Observability;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ecat.core.Bus.BusRegistry;
import com.ecat.core.State.AttributeBase;

/**
 * 平台自观测快照服务（14 号架构 §5.4-2，HA system_health 同名思想的 Java 版）。
 *
 * <p>三层信号里的「指标」层：执行（写命令失败累计 counter——指标注册柜台退役
 * （21 号 D-21-1）后唯一幸存指标，长在 AttributeBase 咽喉自持）、总线（发布计数 +
 * 消费者队列深度/丢批）、线程（普查 + churn 率）。全部内存读数，不落盘、不打日志、不启动
 * 任何采样线程——快照只在端点被读时组装，事件热路径上唯一的可观测成本是一次原子自增。
 *
 * <p><b>速率差分模型</b>：每次快照记录一个样本（时刻 + 各累计计数），速率 = 最新样本与窗口内
 * 最旧样本的差 ÷ 时间差。窗口 60s：常规周期性拉取（监控/脚本）时速率即 60s 滑窗均值；只有一次
 * 读数时速率如实为 null（不编 0），两次读数间隔超过窗口时仍可计算（锚点样本保留），并把实际
 * 窗口跨度随 rateWindowMillis 一并暴露——读者看到的是真实口径而非假装的 60s。
 *
 * <p>严格模式：组件未装配（null）对应节如实输出 null，不输出编造的零值结构。execution 节
 * 来自 AttributeBase 进程级静态 counter（属性写必经咽喉），恒可采集。
 */
public class SystemHealthService {

    /** 速率窗口（毫秒）：窗口内样本参与差分；锚点样本不受此限（见类注释）。 */
    private static final long WINDOW_MS = 60_000L;

    /** 样本数上限：锚点 + 最多 63 个窗口内样本（防高频拉取无限膨胀）。 */
    private static final int MAX_SAMPLES = 64;

    private final BusRegistry busRegistry;

    /** 读时差分样本（仅 snapshot 调用线程读写；synchronized 保护——读端点频率极低）。 */
    private final List<Sample> samples = new ArrayList<>();

    public SystemHealthService(BusRegistry busRegistry) {
        this.busRegistry = busRegistry;
    }

    /**
     * 全量健康快照：{timestamp, execution, bus, threads}。
     * bus 节在总线未装配时为 null；execution（写命令失败 counter）与 threads 节恒可采集。
     */
    public Map<String, Object> snapshot() {
        return snapshot(System.currentTimeMillis());
    }

    /**
     * 指定时刻的快照（测试注入时钟用——速率窗口的差分须可确定性驱动，不靠真实时间流逝）。
     */
    Map<String, Object> snapshot(long atMs) {
        long published = busRegistry != null ? busRegistry.getPublishedCount() : 0L;
        long threadStarts = ManagementFactory.getThreadMXBean().getTotalStartedThreadCount();

        Sample sample = new Sample(atMs, published, threadStarts);
        Window window;
        synchronized (samples) {
            recordAndPrune(sample);
            window = currentWindow();
        }

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("timestamp", sample.atMs);

        // 执行节（21 号）：写命令失败累计——旧注册柜台 9 键中唯一有真实判读的指标
        // （F27 验收 + 消费测试），counter 长在 AttributeBase 咽喉自持，不经注册表
        Map<String, Object> execution = new LinkedHashMap<>();
        execution.put("commandFailed", AttributeBase.commandFailedCount());
        out.put("execution", execution);

        Map<String, Object> bus = BusHealth.collect(busRegistry);
        if (bus != null) {
            bus.put("publishPerSecond",
                    window == null ? null : rate(window, sample.publishedTotal, window.oldest.publishedTotal));
        }
        out.put("bus", bus);

        Map<String, Object> threads = ThreadsHealth.census();
        threads.put("churnPerSecond",
                window == null ? null : rate(window, sample.threadStarts, window.oldest.threadStarts));
        threads.put("rateWindowMillis", window == null ? null : window.spanMs());
        out.put("threads", threads);
        return out;
    }

    /**
     * 线程明细快照（诊断端点）：完整线程清单 + 各线程栈首帧。体积可达数百条，独立于主快照。
     */
    public Map<String, Object> threadSnapshot() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("timestamp", System.currentTimeMillis());
        out.put("threads", ThreadsHealth.threadList());
        return out;
    }

    // =====================================================================
    // 速率窗口
    // =====================================================================

    /** 一次读数的原始计数样本。 */
    private static final class Sample {
        final long atMs;
        final long publishedTotal;
        final long threadStarts;

        Sample(long atMs, long publishedTotal, long threadStarts) {
            this.atMs = atMs;
            this.publishedTotal = publishedTotal;
            this.threadStarts = threadStarts;
        }
    }

    /** 可用差分窗口：最旧（锚点）与最新样本。 */
    private static final class Window {
        final Sample oldest;
        final Sample newest;

        Window(Sample oldest, Sample newest) {
            this.oldest = oldest;
            this.newest = newest;
        }

        long spanMs() {
            return newest.atMs - oldest.atMs;
        }
    }

    /** 记录新样本并裁剪：中间样本超出窗口即清除；索引 0 的锚点样本始终保留（见类注释）。 */
    private void recordAndPrune(Sample sample) {
        samples.add(sample);
        int firstInWindow = samples.size();
        for (int i = 1; i < samples.size(); i++) {
            if (sample.atMs - samples.get(i).atMs <= WINDOW_MS) {
                firstInWindow = i;
                break;
            }
        }
        if (firstInWindow > 1) {
            samples.subList(1, firstInWindow).clear();
        }
        if (samples.size() > MAX_SAMPLES) {
            samples.subList(1, samples.size() - (MAX_SAMPLES - 1)).clear();
        }
    }

    private Window currentWindow() {
        if (samples.size() < 2) {
            return null;
        }
        return new Window(samples.get(0), samples.get(samples.size() - 1));
    }

    /**
     * 差分速率：零跨度或差值为负（极端时钟回拨等异常）时如实返回 null（不编数）。
     * 调用方自行传对应字段的 newest/oldest 值——窗口对象只提供时间跨度。
     */
    private static Double rate(Window window, long newestValue, long oldestValue) {
        long spanMs = window.spanMs();
        if (spanMs <= 0L) {
            return null;
        }
        long delta = newestValue - oldestValue;
        if (delta < 0L) {
            return null;
        }
        double perSecond = delta * 1000.0 / spanMs;
        return Math.round(perSecond * 1000.0) / 1000.0;
    }
}
