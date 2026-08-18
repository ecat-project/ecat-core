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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Pattern;

import com.ecat.core.Bus.event.BusEvent;
import com.ecat.core.Bus.event.BusPayload;
import com.ecat.core.Bus.event.EventContext;
import com.ecat.core.Utils.NumberFormatter;

/**
 * 总线热路径微基准（手工 main，不入 CI）：dispatch 匹配与 NumberFormatter.formatValue 的
 * 「旧实现复刻 vs 新实现」同 JVM 对比，为 A5 快路径改造提供前后数字。
 *
 * <p>复现：
 * <pre>
 * mvnd -q test-compile dependency:build-classpath -Dmdep.outputFile=/tmp/cp-ecat-core.txt
 * java -cp target/classes:target/test-classes:$(cat /tmp/cp-ecat-core.txt) \
 *      com.ecat.core.Bus.BusHotPathBench
 * </pre>
 *
 * <p>方法：100k 预热 + 1M 次 × 5 轮取中位数，System.nanoTime 计时；计数器黑洞防死码消除。
 * 非 JMH，ns 绝对值有 ±20% 抖动，但两臂同 JVM 同结构对比与数量级结论可靠。
 */
public class BusHotPathBench {

    private static final int WARMUP = 100_000;
    private static final int MEASURE = 1_000_000;
    private static final int ROUNDS = 5;

    /** BusTopic 全部 9 个生产 topic 名——生产真实订阅表基数。 */
    private static final String[] PRODUCTION_TOPICS = {
            "device.data.update", "device.lifecycle", "integration.all_loaded",
            "logic_device.all_loaded", "config.entry.lifecycle", "async.execution.completed",
            "async.execution.status_changed", "integration.lifecycle", "notification",
    };

    private static final String HOT_TOPIC = "device.data.update";

    /** 最小占位载荷（BusEvent 泛型上界要求）。 */
    private static final class BenchPayload implements BusPayload { }

    /** 计数黑洞：订阅者递增 volatile 计数，防止 JIT 消除死码。 */
    private static volatile long blackhole;

    public static void main(String[] args) {
        System.out.println("== 总线热路径微基准（旧实现复刻 vs 新实现，同 JVM 对比）==");
        System.out.println("环境: " + System.getProperty("java.vm.name") + " "
                + System.getProperty("java.version")
                + " | 预热 " + WARMUP + " 次，测 " + MEASURE + " 次 × " + ROUNDS + " 轮取中位数\n");
        benchDispatch();
        benchFormat();
    }

    // ==================== 基准 1：dispatch 匹配环节 ====================

    private static void benchDispatch() {
        BusEvent<BenchPayload> event = BusEvent.of(HOT_TOPIC, new BenchPayload(),
                EventContext.root(EventContext.Source.DEVICE_POLL, null));

        // --- 旧实现复刻臂：每 publish × 每 key replace + Pattern.matches（= Pattern.compile 每次） ---
        Map<String, List<EventSubscriber>> oldRegistry = new ConcurrentHashMap<>();
        for (String topic : PRODUCTION_TOPICS) {
            oldRegistry.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<EventSubscriber>())
                    .add(e -> blackhole++);
        }

        // --- 新实现臂：真实 BusRegistry（精确 Map 直配 + 通配 isEmpty 短路） ---
        BusRegistry newRegistry = new BusRegistry();
        for (String topic : PRODUCTION_TOPICS) {
            newRegistry.subscribe(topic, e -> blackhole++);
        }

        // --- 新实现 + 1 个通配订阅臂：展示通配路径（预编译 Pattern）有订阅时的成本 ---
        BusRegistry newRegistryWithWildcard = new BusRegistry();
        for (String topic : PRODUCTION_TOPICS) {
            newRegistryWithWildcard.subscribe(topic, e -> blackhole++);
        }
        newRegistryWithWildcard.subscribe("device.*", e -> blackhole++);

        // 热身 + 正确性 sanity：三臂投递数一致（1 exact 命中；通配臂 2 命中）
        long before = blackhole;
        for (int i = 0; i < WARMUP; i++) {
            dispatchOld(oldRegistry, event);
            newRegistry.publish(event);
            newRegistryWithWildcard.publish(event);
        }
        long deliveredOld = blackhole - before; // 仅 sanity 用，不做断言（基准输出供人工核对）

        double oldNs = medianNs(() -> { for (int i = 0; i < MEASURE; i++) dispatchOld(oldRegistry, event); });
        double newNs = medianNs(() -> { for (int i = 0; i < MEASURE; i++) newRegistry.publish(event); });
        double newWcNs = medianNs(() -> { for (int i = 0; i < MEASURE; i++) newRegistryWithWildcard.publish(event); });

        System.out.println("[dispatch] 订阅表 = BusTopic 9 个精确 topic（生产基数），发布 topic = " + HOT_TOPIC);
        System.out.println("  预热期投递计数 sanity（旧+新+新通配各1次/publish）: " + deliveredOld);
        System.out.printf("  旧实现（replace+Pattern.matches 重编译）: %,10.0f ns/事件%n", oldNs);
        System.out.printf("  新实现（Map equals 直配，零通配订阅）  : %,10.0f ns/事件   [x%,.0f 加速]%n", newNs, oldNs / newNs);
        System.out.printf("  新实现（含 1 个通配订阅 device.*）     : %,10.0f ns/事件%n", newWcNs);
        System.out.println();
    }

    /** 旧 dispatchToMatching 的逐行复刻（A5 改造前 BusRegistry 源码）。 */
    private static void dispatchOld(Map<String, List<EventSubscriber>> registry, BusEvent<?> event) {
        String topic = event.getType();
        for (Map.Entry<String, List<EventSubscriber>> entry : registry.entrySet()) {
            String pattern = entry.getKey().replace("*", ".*");
            if (Pattern.matches(pattern, topic)) {
                for (EventSubscriber subscriber : entry.getValue()) {
                    subscriber.handleEvent(event);
                }
            }
        }
    }

    // ==================== 基准 2：NumberFormatter.formatValue ====================

    private static void benchFormat() {
        // 正确性 sanity：两臂输出必须逐位一致（HALF_EVEN 语义等价）
        String outOld = formatValueOld(12.345, 2);
        String outNew = NumberFormatter.formatValue(12.345, 2);
        if (!outOld.equals(outNew)) {
            throw new IllegalStateException("基准 sanity 失败: 旧=" + outOld + " 新=" + outNew);
        }

        for (int i = 0; i < WARMUP; i++) {
            formatValueOld(12.345, 2);
            NumberFormatter.formatValue(12.345, 2);
        }
        double oldNs = medianNs(() -> { for (int i = 0; i < MEASURE; i++) formatValueOld(12.345, 2); });
        double newNs = medianNs(() -> { for (int i = 0; i < MEASURE; i++) NumberFormatter.formatValue(12.345, 2); });

        System.out.println("[formatValue] 入参 12.345 精度 2，两臂输出 sanity = " + outNew);
        System.out.printf("  旧实现（每次 new DecimalFormat）        : %,10.0f ns/调用%n", oldNs);
        System.out.printf("  新实现（ThreadLocal 按精度复用）        : %,10.0f ns/调用   [x%,.1f 加速]%n", newNs, oldNs / newNs);
        System.out.println();
    }

    /** 旧 formatValue 方法体逐行复刻（A5 改造前 NumberFormatter 源码）。 */
    private static String formatValueOld(Number value, int displayPrecision) {
        if (displayPrecision < 0) {
            throw new IllegalArgumentException("小数位数不能为负数: " + displayPrecision);
        }
        BigDecimal bd = new BigDecimal(value.toString());
        bd = bd.setScale(displayPrecision, RoundingMode.HALF_EVEN);
        String decimalPart = buildRepeatedString('0', displayPrecision);
        String pattern = (displayPrecision == 0) ? "0" : "0." + decimalPart;
        DecimalFormat df = new DecimalFormat(pattern);
        df.setRoundingMode(RoundingMode.HALF_EVEN);
        return df.format(bd);
    }

    private static String buildRepeatedString(char c, int count) {
        if (count <= 0) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(c);
        }
        return sb.toString();
    }

    // ==================== 计时骨架 ====================

    /** 跑 ROUNDS 轮 MEASURE 次，返回每轮 ns/op 的中位数。 */
    private static double medianNs(Runnable measuredLoop) {
        double[] perOpNs = new double[ROUNDS];
        for (int r = 0; r < ROUNDS; r++) {
            long t0 = System.nanoTime();
            measuredLoop.run();
            long elapsed = System.nanoTime() - t0;
            perOpNs[r] = (double) elapsed / MEASURE;
            blackhole += elapsed;
        }
        java.util.Arrays.sort(perOpNs);
        return perOpNs[ROUNDS / 2];
    }
}
