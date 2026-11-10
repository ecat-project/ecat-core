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
import com.ecat.core.Utils.Mdc.LogScope;
import com.ecat.core.Utils.Mdc.MdcContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.MDC;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
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
        // 身份向量用例写入的 MDC 键不得泄漏到同 JVM 后续用例（mvnd 同进程跑全套）
        MDC.clear();
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

    /**
     * 「7 集成 6 条被吞」机制级反向锁（223801 E3）：同一错误点模板、异 coordinate=不同受害者，
     * 限频必须各自放行——身份向量上线前全部撞同一模板级签名，后到 6 家的死法明细被首家吞掉。
     */
    @Test
    public void differentCoordinateSameTemplate_bothPass() {
        try {
            MDC.put(MdcContext.INTEGRATION_COORDINATE_KEY, "com.ecat:integration-sailhero");
            testLogger.error("Failed to load entry {}: {}", "entry-a", "config validation failed");
            MDC.put(MdcContext.INTEGRATION_COORDINATE_KEY, "com.ecat:integration-saimosen");
            testLogger.error("Failed to load entry {}: {}", "entry-b", "port ttyUSB201 missing");
        } finally {
            MDC.remove(MdcContext.INTEGRATION_COORDINATE_KEY);
        }
        assertEquals("同模板异 coordinate 是不同受害者，须各自放行", 2, appender.list.size());
    }

    /**
     * E3 回归锁（entry 通知点）：同 entry 窗口内重复仍压制（限频本职），
     * 但异 entry 同模板必须放行——entry.id 不进签名时 (e=B) 的首条死法明细被 (e=A) 的窗口吞掉。
     */
    @Test
    public void sameEntryIdRepeats_suppressed_butOtherEntryPasses() {
        IllegalStateException boom = new IllegalStateException("boom");
        try (LogScope scope = LogScope.entry("entry-A")) {
            testLogger.error("Failed to notify create entry: {}", boom);  // (e=A) 首过
            testLogger.error("Failed to notify create entry: {}", boom);  // (e=A) 窗内压制
        }
        try (LogScope scope = LogScope.entry("entry-B")) {
            testLogger.error("Failed to notify create entry: {}", boom);  // (e=B) 异受害者放行
        }
        assertEquals("(e=A) 首过 + (e=B) 首过；A 的窗内重复被压", 2, appender.list.size());
    }

    /**
     * 汇总行身份可检索：压制计数收口成汇总时 description 必须带身份——
     * 「相同错误再出现 N 次」要能说出是谁的，否则跨受害者场景无法定位受害 entry。
     */
    @Test
    public void summaryLineCarriesIdentity() {
        IllegalStateException boom = new IllegalStateException("boom");
        try (LogScope scope = LogScope.entry("entry-cc")) {
            testLogger.error("Failed to notify create entry: {}", boom);
            clock.advance(100);
            testLogger.error("Failed to notify create entry: {}", boom);  // 压 1 条挂账
        }
        clock.advance(WINDOW_MILLIS + 1);
        try (LogScope scope = LogScope.entry("entry-cc")) {
            testLogger.error("Failed to notify create entry: {}", boom);  // 过期：补汇总 + 新窗首过
        }
        assertEquals("首条 + 汇总 + 新窗首条", 3, appender.list.size());
        ILoggingEvent summary = appender.list.get(1);
        assertEquals(LogMarkers.RATE_LIMIT_SUMMARY, summary.getMarker());
        assertTrue("汇总行必须带身份键值（可检索是谁的），实际=" + summary.getFormattedMessage(),
                summary.getFormattedMessage().contains("entry.id=entry-cc"));
    }

    /**
     * 汇总饥饿兜底（案 8 B 面新增语义——搭车清算）：A 签名 2 连发后归于沉寂（抑制计数挂账），
     * 其后任意**其他**签名的 WARN/ERROR 活动必须把 A 的过期窗口清算补汇总——修复前 A 的计数
     * 只能等「A 自己再犯错」或「签名槽满淘汰」，静默失败明细长期不可见（223801 E3 形态）。
     * 红形态：B 事件后 appender 无汇总行（挂账计数丢失）。
     */
    @Test
    public void pendingSuppressedCountFlushesOnNextUnrelatedActivity() {
        IllegalStateException boomA = new IllegalStateException("boomA");
        IllegalArgumentException boomB = new IllegalArgumentException("boomB");
        Logger loggerA = attachedLogger("com.ecat.test.StormA");
        Logger loggerB = attachedLogger("com.ecat.test.StormB");

        // A 风暴：首条放行 + 窗口内两条抑制挂账，随后 A 沉寂
        loggerA.error("storm failed", boomA);
        clock.advance(100);
        loggerA.error("storm failed", boomA);
        clock.advance(100);
        loggerA.error("storm failed", boomA);
        assertEquals("窗口内仍只放行首条", 1, appender.list.size());

        // B 签名的无关活动（窗口过期后）搭车清算 A 的挂账计数；B 首条自身亦放行
        clock.advance(3000);
        loggerB.error("unrelated failed", boomB);

        assertEquals("A 汇总 + B 明细都必须落", 3, appender.list.size());
        ILoggingEvent summary = appender.list.get(1);
        assertEquals("A 的挂账计数应随 B 活动清算为汇总行", LogMarkers.RATE_LIMIT_SUMMARY, summary.getMarker());
        assertTrue("汇总应点名 A 的签名，实际=" + summary.getFormattedMessage(),
                summary.getFormattedMessage().contains(IllegalStateException.class.getName()));
        assertTrue("汇总应含抑制次数 2，实际=" + summary.getFormattedMessage(),
                summary.getFormattedMessage().contains("再出现 2 次"));
        assertNull("B 明细应为普通事件（不带汇总 marker）", appender.list.get(2).getMarker());
        assertEquals("B 明细级别应为 ERROR", Level.ERROR, appender.list.get(2).getLevel());
    }

    /**
     * 搭车清算只动过期窗口：未过期窗口的其他签名活动不得误清（计数与窗口保持原状）。
     */
    @Test
    public void piggybackFlushLeavesUnexpiredWindowsAlone() {
        IllegalStateException boomA = new IllegalStateException("boomA");
        IllegalArgumentException boomB = new IllegalArgumentException("boomB");
        Logger loggerA = attachedLogger("com.ecat.test.KeepA");
        Logger loggerB = attachedLogger("com.ecat.test.KeepB");

        loggerA.error("keep failed", boomA);
        clock.advance(100);
        loggerA.error("keep failed", boomA);   // 抑制 1 条挂账，窗口未过期
        clock.advance(100);
        loggerB.error("other failed", boomB);  // B 活动：A 窗口未过期，不得清算

        // A 首条 + B 首条 = 2；A 的挂账计数未过期不得提前吐汇总
        assertEquals(2, appender.list.size());
        // A 窗口过期后同签名再犯：仍按「补汇总 + 新窗口放行」路径走（计数未丢）
        clock.advance(3000);
        loggerA.error("keep failed", boomA);
        assertEquals(4, appender.list.size());
        assertEquals(LogMarkers.RATE_LIMIT_SUMMARY, appender.list.get(2).getMarker());
        assertTrue(appender.list.get(2).getFormattedMessage().contains("再出现 1 次"));
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
