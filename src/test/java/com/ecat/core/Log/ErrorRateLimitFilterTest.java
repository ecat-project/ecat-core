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
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * {@link ErrorRateLimitFilter} 单元测试。
 *
 * <p>时间控制全部经 {@link MutableClock} 注入，禁依赖真实时间；
 * 并发用例用 {@link CountDownLatch} 同步，禁 Thread.sleep。
 */
public class ErrorRateLimitFilterTest {

    private static final long WINDOW_MILLIS = 3000L;
    /** 缩小的签名槽上限，验证淘汰路径（淘汰最旧签名并补汇总行） */
    private static final int MAX_SIGNATURES = 3;

    private final MutableClock clock = new MutableClock(1_000_000L);
    private LoggerContext context;
    private ListAppender<ILoggingEvent> appender;
    private Logger testLogger;
    private Logger summaryLogger;
    private ErrorRateLimitFilter filter;

    @Before
    public void setUp() {
        context = new LoggerContext();
        context.setName("error-rate-limit-test");
        filter = new ErrorRateLimitFilter(WINDOW_MILLIS, MAX_SIGNATURES, clock);
        filter.setContext(context);
        context.addTurboFilter(filter);
        filter.start();

        appender = new ListAppender<>();
        appender.start();
        testLogger = context.getLogger("com.ecat.test.DevicePoller");
        testLogger.addAppender(appender);
        testLogger.setAdditive(false);
        summaryLogger = context.getLogger(ErrorRateLimitFilter.class.getName());
        summaryLogger.addAppender(appender);
        summaryLogger.setAdditive(false);
    }

    @After
    public void tearDown() {
        context.stop();
    }

    @Test
    public void firstWarnWithinWindowPasses() {
        testLogger.warn("poll failed: {}", "deviceA", new IllegalStateException("boom"));

        assertEquals(1, appender.list.size());
        assertEquals(Level.WARN, appender.list.get(0).getLevel());
    }

    @Test
    public void duplicateWithinWindowDeniedAndCounted() {
        IllegalStateException boom = new IllegalStateException("boom");
        testLogger.warn("poll failed: {}", "deviceA", boom);
        clock.advance(1000);
        testLogger.warn("poll failed: {}", "deviceA", boom);
        clock.advance(500);
        testLogger.warn("poll failed: {}", "deviceA", boom);

        // 同签名 3s 窗口内只放行首次，后续在 turbo 层被拒（不进任何 appender）
        assertEquals(1, appender.list.size());
    }

    @Test
    public void windowCloseEmitsSummaryLine() {
        IllegalStateException boom = new IllegalStateException("boom");
        // warn(String, Throwable) 重载：throwable 经显式 t 参数进入 turbo 层，走异常签名路径
        testLogger.warn("poll failed", boom);
        clock.advance(1000);
        testLogger.warn("poll failed", boom);
        clock.advance(500);
        testLogger.warn("poll failed", boom);
        // 窗口过期后的下一次同签名事件：先补一行汇总，再放行本条作为新窗口首次
        clock.advance(1501);
        testLogger.warn("poll failed", boom);

        assertEquals(3, appender.list.size());
        ILoggingEvent summary = appender.list.get(1);
        assertEquals(Level.WARN, summary.getLevel());
        assertEquals(LogMarkers.RATE_LIMIT_SUMMARY, summary.getMarker());
        String message = summary.getFormattedMessage();
        assertTrue("汇总行应包含重复次数，实际=" + message, message.contains("再出现 2 次"));
        assertTrue("汇总行应包含签名标识（异常类），实际=" + message,
                message.contains(IllegalStateException.class.getName()));
    }

    @Test
    public void sameMessageTemplateDifferentParamsIsSameSignature() {
        // 实测场景复现：env-data-handle 同句 WARN 毫秒级三连发（参数不同）——按消息模板去重
        testLogger.warn("文本类型参数不支持avg聚合: {}", "paramA");
        testLogger.warn("文本类型参数不支持avg聚合: {}", "paramB");

        assertEquals(1, appender.list.size());
    }

    @Test
    public void differentSignaturesDoNotInterfere() {
        testLogger.warn("poll failed", new IllegalStateException("a"));
        testLogger.warn("poll failed", new IllegalArgumentException("b"));

        assertEquals(2, appender.list.size());
    }

    @Test
    public void infoLevelIsNeverRateLimited() {
        testLogger.info("same line {}", 1);
        testLogger.info("same line {}", 2);
        testLogger.info("same line {}", 3);

        // 限频只作用于 WARN/ERROR；INFO 主通道体积治理靠级别，不靠这里
        assertEquals(3, appender.list.size());
    }

    @Test
    public void summaryLineItselfIsNeverRateLimited() {
        summaryLogger.warn(LogMarkers.RATE_LIMIT_SUMMARY, "汇总行 {}", 1);
        summaryLogger.warn(LogMarkers.RATE_LIMIT_SUMMARY, "汇总行 {}", 2);

        assertEquals(2, appender.list.size());
    }

    @Test
    public void concurrentDuplicateLogsPassExactlyOnce() throws InterruptedException {
        final IllegalStateException boom = new IllegalStateException("boom");
        int threads = 8;
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        for (int i = 0; i < threads; i++) {
            new Thread(() -> {
                try {
                    startGate.await();
                    testLogger.warn("poll failed: {}", "deviceA", boom);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            }).start();
        }
        startGate.countDown();
        assertTrue("并发用例必须在超时前完成", done.await(10, TimeUnit.SECONDS));

        // 时钟静止（同一窗口时间戳）：8 并发同签名恰有 1 条放行、7 条拒绝，无丢失也无重复放行
        assertEquals(1, appender.list.size());
    }

    @Test
    public void evictOldestSignatureFlushesPendingSummary() {
        Logger loggerA = attachedLogger("com.ecat.test.A");
        Logger loggerD = attachedLogger("com.ecat.test.D");
        IllegalStateException boomA = new IllegalStateException("boomA");
        IllegalArgumentException boomD = new IllegalArgumentException("boomD");

        loggerA.warn("a failed", boomA);
        attachedLogger("com.ecat.test.B").warn("b failed", new NullPointerException("boomB"));
        attachedLogger("com.ecat.test.C").warn("c failed", new UnsupportedOperationException("boomC"));
        loggerA.warn("a failed", boomA);
        // 签名槽已满（3），插入 D 的签名淘汰最旧的 A：A 有 1 条未汇报计数，须先补汇总
        loggerD.warn("d failed", boomD);

        assertEquals(5, appender.list.size());
        ILoggingEvent summary = appender.list.get(3);
        assertEquals(LogMarkers.RATE_LIMIT_SUMMARY, summary.getMarker());
        String message = summary.getFormattedMessage();
        assertTrue("淘汰汇总应点名被淘汰签名（A 的异常类），实际=" + message,
                message.contains(IllegalStateException.class.getName()));
        assertTrue("淘汰汇总应包含重复次数，实际=" + message, message.contains("再出现 1 次"));
    }

    @Test
    public void startWithoutLoggerContextRefusesToStart() {
        // 严格模式：未挂 LoggerContext 时无法发射汇总行，必须显式拒绝启动而非静默不限频
        ErrorRateLimitFilter bare = new ErrorRateLimitFilter();
        bare.start();

        assertFalse(bare.isStarted());
    }

    /** 为淘汰用例构造独立 logger（不同 logger 名参与签名，保证签名互不相同） */
    private Logger attachedLogger(String name) {
        Logger logger = context.getLogger(name);
        logger.addAppender(appender);
        logger.setAdditive(false);
        return logger;
    }

    /** 可手动推进的测试时钟：窗口判定只看注入时间，禁真实时钟 */
    static final class MutableClock extends Clock {
        private long millis;

        MutableClock(long startMillis) {
            this.millis = startMillis;
        }

        void advance(long deltaMillis) {
            millis += deltaMillis;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException("测试时钟不支持时区切换");
        }

        @Override
        public Instant instant() {
            return Instant.ofEpochMilli(millis);
        }
    }
}
