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

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import org.slf4j.Marker;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * WARN/ERROR 错误限频 TurboFilter（日志预算策略：错误通道预算 ~5MB/日）。
 *
 * <p>同一 logger + 异常签名（throwable 类名 + 顶层栈帧；无异常时消息模板首行 hash）
 * 在 {@value #DEFAULT_WINDOW_MILLIS}ms 窗口内：首次放行（保留全栈），后续在 turbo 层拒绝
 * （格式化之前，零字符串成本）并计数；窗口被下一次同签名事件关闭时补一行汇总
 * 「相同错误再出现 N 次」。实测对照：env-data-handle 同句 WARN 毫秒级三连发无去重，
 * 该模式直接消解。
 *
 * <p>汇总行自带 {@link LogMarkers#RATE_LIMIT_SUMMARY} marker，本过滤器对它无条件放行：
 * 既防止限频器处理自己发射的事件（递归），也保证汇总自身不受限频。
 *
 * <p>线程安全与内存有界：窗口表由单锁保护（WARN/ERROR 经级别过滤后流量低，竞争可忽略）；
 * 签名槽上限 {@value #DEFAULT_MAX_SIGNATURES}，超限按插入序淘汰最旧签名，被淘汰签名
 * 若有未汇报的计数先补汇总行，不静默丢失。
 */
public class ErrorRateLimitFilter extends TurboFilter {

    /** 限频窗口（ms）：实测重复错误为毫秒级连发，3s 足以合并一阵 */
    public static final long DEFAULT_WINDOW_MILLIS = 3000L;

    /** 签名槽上限：95 集成 × 每集成少量错误点，1024 覆盖充分且内存有界（防签名风暴撑爆内存） */
    public static final int DEFAULT_MAX_SIGNATURES = 1024;

    /** throwable 栈帧为空时的显式占位（JVM 优化后 getStackTrace 可能为空数组，属合法边界） */
    private static final String NO_FRAME_PLACEHOLDER = "no-frame";

    /** 汇总行签名描述截断长度：保一行可读，防超长消息模板把汇总行放大 */
    private static final int DESCRIPTION_MAX_LENGTH = 160;

    private final long windowMillis;
    private final int maxSignatures;
    private final Clock clock;

    /** 签名 -> 窗口状态；LinkedHashMap 保持插入序，头部即最旧（淘汰对象） */
    private final LinkedHashMap<String, SignatureWindow> windows = new LinkedHashMap<>();

    private final Object lock = new Object();

    /** 汇总行发射 logger，start() 时从所挂 LoggerContext 解析 */
    private volatile Logger summaryLogger;

    /** Joran 反射实例化入口（logback.xml turboFilter 配置用），系统时钟 + 默认参数 */
    public ErrorRateLimitFilter() {
        this(DEFAULT_WINDOW_MILLIS, DEFAULT_MAX_SIGNATURES, Clock.systemUTC());
    }

    /** 测试可见构造：注入窗口长度 / 签名槽上限 / 时钟（窗口判定不依赖真实时间） */
    ErrorRateLimitFilter(long windowMillis, int maxSignatures, Clock clock) {
        this.windowMillis = windowMillis;
        this.maxSignatures = maxSignatures;
        this.clock = clock;
    }

    @Override
    public void start() {
        if (!(getContext() instanceof LoggerContext)) {
            // 严格模式：挂不到 LoggerContext 就无法发射汇总行，显式拒绝启动，不降级为「不限频」
            addError("ErrorRateLimitFilter 必须挂在 LoggerContext 上（当前 context=" + getContext() + "）");
            return;
        }
        summaryLogger = ((LoggerContext) getContext()).getLogger(ErrorRateLimitFilter.class.getName());
        super.start();
    }

    @Override
    public void stop() {
        synchronized (lock) {
            windows.clear();
        }
        super.stop();
    }

    @Override
    public FilterReply decide(Marker marker, Logger logger, Level level, String format, Object[] params,
                              Throwable t) {
        if (!isStarted()) {
            return FilterReply.NEUTRAL;
        }
        if (marker != null && marker.contains(LogMarkers.RATE_LIMIT_SUMMARY.getName())) {
            // 汇总行放行：防递归 + 汇总自身不限频
            return FilterReply.NEUTRAL;
        }
        if (level.toInt() < Level.WARN_INT) {
            // 只限频 WARN/ERROR；INFO 及以下不进窗口（主通道体积治理靠级别，不靠限频器）
            return FilterReply.NEUTRAL;
        }

        Signature signature = signatureOf(logger.getName(), format, t);
        boolean pass;
        List<Flush> flushes = new ArrayList<>(1);
        long now = clock.millis();
        synchronized (lock) {
            SignatureWindow window = windows.get(signature.key);
            if (window == null) {
                evictIfNeeded(flushes);
                windows.put(signature.key, new SignatureWindow(now, signature.description));
                pass = true;
            } else if (now - window.firstMillis < windowMillis) {
                window.suppressedCount++;
                pass = false;
            } else {
                closeWindow(signature.key, window, flushes);
                windows.put(signature.key, new SignatureWindow(now, signature.description));
                pass = true;
            }
        }
        emitFlushes(flushes);
        return pass ? FilterReply.NEUTRAL : FilterReply.DENY;
    }

    /** 签名槽满时按插入序淘汰最旧签名；有待汇报计数则先记入 flushes（不静默丢失） */
    private void evictIfNeeded(List<Flush> flushes) {
        while (windows.size() >= maxSignatures) {
            Iterator<Map.Entry<String, SignatureWindow>> eldest = windows.entrySet().iterator();
            Map.Entry<String, SignatureWindow> victim = eldest.next();
            eldest.remove();
            if (victim.getValue().suppressedCount > 0) {
                flushes.add(new Flush(victim.getValue().description, victim.getValue().suppressedCount));
            }
        }
    }

    private void closeWindow(String key, SignatureWindow window, List<Flush> flushes) {
        windows.remove(key);
        if (window.suppressedCount > 0) {
            flushes.add(new Flush(window.description, window.suppressedCount));
        }
    }

    /** 汇总行在锁外发射（logback 调用本身线程安全；避免持锁做 IO） */
    private void emitFlushes(List<Flush> flushes) {
        for (Flush flush : flushes) {
            summaryLogger.warn(LogMarkers.RATE_LIMIT_SUMMARY, "[错误限频] {} 相同错误再出现 {} 次（{}s 窗口内已抑制）",
                    flush.description, flush.suppressedCount, windowMillis / 1000);
        }
    }

    /**
     * 签名 = logger 名 + 异常指纹（throwable 类名 + 顶层栈帧；无异常时消息模板首行 hash）。
     * 用消息模板而非格式化结果：参数（设备 id 等）变化不产生新签名，同一错误点的重复才被合并。
     */
    private Signature signatureOf(String loggerName, String format, Throwable t) {
        if (t != null) {
            StackTraceElement[] frames = t.getStackTrace();
            String topFrame = frames.length == 0 ? NO_FRAME_PLACEHOLDER
                    : frames[0].getClassName() + ":" + frames[0].getLineNumber();
            return new Signature(
                    loggerName + "|throwable|" + t.getClass().getName() + "|" + topFrame,
                    t.getClass().getName() + " @ " + topFrame + " (" + loggerName + ")");
        }
        String firstLine = firstLineOf(format);
        return new Signature(
                loggerName + "|message|" + Integer.toHexString(firstLine.hashCode()),
                (firstLine.isEmpty() ? "(空消息)" : firstLine) + " (" + loggerName + ")");
    }

    private String firstLineOf(String format) {
        if (format == null || format.isEmpty()) {
            return "";
        }
        int newline = format.indexOf('\n');
        return newline >= 0 ? format.substring(0, newline) : format;
    }

    /** 限频窗口：首次时间戳 + 待汇报的抑制计数 + 汇总行用的签名描述 */
    private static final class SignatureWindow {
        private final long firstMillis;
        private final String description;
        private int suppressedCount;

        private SignatureWindow(long firstMillis, String description) {
            this.firstMillis = firstMillis;
            this.description = description;
        }
    }

    /** 窗口关闭/签名淘汰时待发射的汇总（description + 抑制次数） */
    private static final class Flush {
        private final String description;
        private final int suppressedCount;

        private Flush(String description, int suppressedCount) {
            this.description = description;
            this.suppressedCount = suppressedCount;
        }
    }

    /** 签名键 + 汇总行可读描述（描述截断，防超长消息模板放大汇总行） */
    private static final class Signature {
        private final String key;
        private final String description;

        private Signature(String key, String description) {
            this.key = key;
            this.description = description.length() > DESCRIPTION_MAX_LENGTH
                    ? description.substring(0, DESCRIPTION_MAX_LENGTH)
                    : description;
        }
    }
}
