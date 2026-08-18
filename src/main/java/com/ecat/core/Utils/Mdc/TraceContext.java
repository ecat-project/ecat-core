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

package com.ecat.core.Utils.Mdc;

import org.slf4j.MDC;

import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 追踪上下文，管理 Trace ID 和 MDC 上下文的传播
 *
 * <p>提供跨线程、跨组件的追踪能力，用于日志链路追踪。
 *
 * <p>核心功能：
 * <ul>
 *   <li>生成和管理 Trace ID（26 字符单调 ULID，因果链原语）</li>
 *   <li>捕获和恢复 MDC 上下文</li>
 *   <li>包装 Runnable/Callable 以自动传播上下文</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>
 * // 在业务入口生成 Trace ID
 * TraceContext.getOrCreateTraceId();
 *
 * // 在异步任务中包装
 * executor.submit(TraceContext.wrapRunnable(() -> doWork()));
 *
 * // 获取当前 Trace ID
 * String traceId = TraceContext.getTraceId();
 * </pre>
 *
 * @author coffee
 */
public final class TraceContext {
    /**
     * MDC 中 Trace ID 的键名
     */
    public static final String TRACE_ID_KEY = "traceId";

    /** ULID 字符集（Crockford Base32：去 I/L/O/U 防手抄/口读混淆）。 */
    private static final char[] ULID_ALPHABET = "0123456789ABCDEFGHJKMNPQRSTVWXYZ".toCharArray();

    /** ULID 总长：48bit 时间戳(10 字符) + 80bit 熵(16 字符) = 26 字符。 */
    private static final int ULID_LENGTH = 26;

    /**
     * 单调 ULID 生成状态：同毫秒内熵 +1 递增（generateUlid 是 synchronized，
     * 这三个字段只在锁内读写）。时钟回拨时沿用上次毫秒继续递增——单调性优先于时间精度。
     */
    private static long ulidLastTime = -1L;
    private static long ulidEntropyHi = 0L;
    private static long ulidEntropyLo = 0L;

    private TraceContext() {
    }

    /**
     * 生成新的 Trace ID（26 字符单调 ULID）。
     *
     * <p>因果链原语（14 号架构 §5.4-3，HA Context 模型）：ULID 全局唯一 + 字典序即时间序，
     * 一次 poll 执行的 traceId 就是该次数据变化的因果锚点——bus 事件信封的 causationId、
     * 消费者续传的 MDC traceId 都指向它，grep 一个 ULID 即串起 poll→发布→消费全链路。
     * 单调性（同毫秒内严格递增）让日志按 id 排序即按发生顺序排列。
     *
     * <p>仅在任务/执行边界调用（每次 poll 一次），不在事件热路径调用；synchronized 足够便宜。
     *
     * @return 新的 ULID Trace ID
     */
    public static String generateTraceId() {
        return generateUlid();
    }

    /**
     * 生成单调 ULID：48bit 毫秒时间戳 + 80bit 熵。
     *
     * <p>同毫秒（含时钟回拨）时熵 +1 递增保持字典序单调；80bit 熵在单毫秒内耗尽（概率 ~2^-80，
     * 代码路径仍完备）则借位到下一毫秒并重掷随机熵。
     *
     * @return 26 字符 Crockford Base32 ULID
     */
    public static synchronized String generateUlid() {
        long time = System.currentTimeMillis();
        if (time <= ulidLastTime) {
            // 同毫秒或时钟回拨：熵递增保持单调（回拨场景沿用上次毫秒，id 不回退）
            ulidEntropyLo++;
            if (ulidEntropyLo == 0L && ++ulidEntropyHi > 0xFFFFL) {
                time = ulidLastTime + 1L;
                ulidEntropyHi = ThreadLocalRandom.current().nextLong() & 0xFFFFL;
                ulidEntropyLo = ThreadLocalRandom.current().nextLong();
            } else {
                time = ulidLastTime;
            }
        } else {
            ulidEntropyHi = ThreadLocalRandom.current().nextLong() & 0xFFFFL;
            ulidEntropyLo = ThreadLocalRandom.current().nextLong();
        }
        ulidLastTime = time;
        return encodeUlid(time, ulidEntropyHi, ulidEntropyLo);
    }

    /** ULID 编码：时间戳 48bit → 前 10 字符（大端 5bit 组），熵 80bit(hi16&lt;&lt;64|lo64) → 后 16 字符。 */
    private static String encodeUlid(long time48, long entropyHi16, long entropyLo64) {
        final char[] out = new char[ULID_LENGTH];
        for (int i = 9; i >= 0; i--) {
            out[i] = ULID_ALPHABET[(int) (time48 & 0x1F)];
            time48 >>>= 5;
        }
        for (int i = 0; i < 16; i++) {
            final int bitPos = 75 - i * 5;
            int group;
            if (bitPos >= 64) {
                group = (int) ((entropyHi16 >>> (bitPos - 64)) & 0x1F);
            } else if (bitPos > 59) {
                // 跨 hi/lo 边界的 5bit 组：低位取自 lo 顶端，高位取自 hi 底端；最外层 & 0x1F
                // 截掉 hi 移位后泄漏到 5bit 之外的高位（曾致 ULID_ALPHABET 越界）
                final int fromLo = 64 - bitPos;
                group = (int) ((((entropyLo64 >>> bitPos) & ((1 << fromLo) - 1)) | (entropyHi16 << fromLo)) & 0x1F);
            } else {
                group = (int) ((entropyLo64 >>> bitPos) & 0x1F);
            }
            out[10 + i] = ULID_ALPHABET[group];
        }
        return new String(out);
    }

    /**
     * 获取当前 Trace ID
     *
     * @return Trace ID，如果不存在返回 null
     */
    public static String getTraceId() {
        return MDC.get(TRACE_ID_KEY);
    }

    /**
     * 获取当前 Trace ID，如果不存在则生成一个新的
     *
     * <p>通常在业务入口调用，确保整个调用链有统一的 Trace ID。
     *
     * @return Trace ID
     */
    public static String getOrCreateTraceId() {
        String traceId = MDC.get(TRACE_ID_KEY);
        if (traceId == null || traceId.isEmpty()) {
            traceId = generateTraceId();
            MDC.put(TRACE_ID_KEY, traceId);
        }
        return traceId;
    }

    /**
     * 设置 Trace ID
     *
     * @param traceId Trace ID
     */
    public static void setTraceId(String traceId) {
        if (traceId != null && !traceId.isEmpty()) {
            MDC.put(TRACE_ID_KEY, traceId);
        }
    }

    /**
     * 清除 Trace ID
     */
    public static void clearTraceId() {
        MDC.remove(TRACE_ID_KEY);
    }

    /**
     * 捕获当前 MDC 上下文（包括 Trace ID 和其他所有 MDC 值）
     *
     * @return MDC 上下文副本
     */
    public static Map<String, String> capture() {
        return MDC.getCopyOfContextMap();
    }

    /**
     * 恢复 MDC 上下文
     *
     * @param context 要恢复的上下文
     */
    public static void restore(Map<String, String> context) {
        MDC.clear();
        if (context != null) {
            context.forEach(MDC::put);
        }
    }

    /**
     * 包装 Runnable，使其在执行时使用捕获的上下文
     *
     * <p>上下文包括 Trace ID、coordinate 等所有 MDC 值。
     * 如果当前没有 Trace ID，会自动生成一个。
     *
     * @param task 原始任务
     * @return 包装后的任务
     */
    public static Runnable wrapRunnable(Runnable task) {
        Map<String, String> context = capture();
        // 确保有 Trace ID
        if (context == null || !context.containsKey(TRACE_ID_KEY)) {
            String traceId = generateTraceId();
            if (context != null) {
                context.put(TRACE_ID_KEY, traceId);
            }
        }
        return wrapRunnable(task, context);
    }

    /**
     * 包装 Runnable，使其在执行时使用指定的上下文
     *
     * <p>如果上下文中没有 Trace ID，会自动生成一个。
     *
     * @param task 原始任务
     * @param context MDC 上下文
     * @return 包装后的任务
     */
    public static Runnable wrapRunnable(Runnable task, Map<String, String> context) {
        return () -> {
            Map<String, String> previousContext = capture();
            try {
                restore(context);
                // 确保 Trace ID 存在，如果不存在则生成一个
                getOrCreateTraceId();
                task.run();
            } finally {
                restore(previousContext);
            }
        };
    }

    /**
     * 包装 Callable，使其在执行时使用捕获的上下文
     *
     * <p>上下文包括 Trace ID、coordinate 等所有 MDC 值。
     * 如果当前没有 Trace ID，会自动生成一个。
     *
     * @param task 原始任务
     * @return 包装后的任务
     */
    public static <V> Callable<V> wrapCallable(Callable<V> task) {
        Map<String, String> context = capture();
        // 确保有 Trace ID
        if (context == null || !context.containsKey(TRACE_ID_KEY)) {
            String traceId = generateTraceId();
            if (context != null) {
                context.put(TRACE_ID_KEY, traceId);
            }
        }
        return wrapCallable(task, context);
    }

    /**
     * 包装 Callable，使其在执行时使用指定的上下文
     *
     * <p>如果上下文中没有 Trace ID，会自动生成一个。
     *
     * @param task 原始任务
     * @param context MDC 上下文
     * @return 包装后的任务
     */
    public static <V> Callable<V> wrapCallable(Callable<V> task, Map<String, String> context) {
        return () -> {
            Map<String, String> previousContext = capture();
            try {
                restore(context);
                // 确保 Trace ID 存在，如果不存在则生成一个
                getOrCreateTraceId();
                return task.call();
            } finally {
                restore(previousContext);
            }
        };
    }

    /**
     * 包装周期性任务：恢复提交时上下文（coordinate 等）+ 每次执行生成新 Trace ID。
     *
     * <p>为什么周期任务与一次性任务（{@link #wrapRunnable(Runnable, Map)}）不同：周期任务的
     * 每次执行是独立的业务操作（一次轮询、一次清扫），应有独立 Trace ID 追踪单次链路；
     * 而 coordinate 等长命上下文保持提交时的值。
     *
     * <p>调度引擎（SchedulerEngine）与 MdcScheduledExecutorService 共用此实现，
     * 保证两种执行器下周期任务的 MDC 语义一致。
     *
     * @param task 原始任务
     * @param context 提交时捕获的 MDC 上下文（不含当次执行的 traceId）
     * @return 包装后的任务
     */
    public static Runnable wrapPeriodicRunnable(Runnable task, Map<String, String> context) {
        return () -> {
            Map<String, String> previousContext = capture();
            try {
                restore(context);
                setTraceId(generateTraceId());
                task.run();
            } finally {
                restore(previousContext);
            }
        };
    }

    /**
     * 在新 Trace ID 上下文中执行任务
     *
     * <p>适用于需要独立追踪的业务操作。
     *
     * @param task 要执行的任务
     */
    public static void runWithNewTraceId(Runnable task) {
        Map<String, String> previousContext = capture();
        try {
            String newTraceId = generateTraceId();
            MDC.put(TRACE_ID_KEY, newTraceId);
            task.run();
        } finally {
            restore(previousContext);
        }
    }

    /**
     * 在指定 Trace ID 上下文中执行任务
     *
     * @param traceId Trace ID
     * @param task 要执行的任务
     */
    public static void runWithTraceId(String traceId, Runnable task) {
        Map<String, String> previousContext = capture();
        try {
            MDC.put(TRACE_ID_KEY, traceId);
            task.run();
        } finally {
            restore(previousContext);
        }
    }
}
