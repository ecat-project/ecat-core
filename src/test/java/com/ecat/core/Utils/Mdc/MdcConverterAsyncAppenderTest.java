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

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.PatternLayout;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.MDC;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

/**
 * TraceIdConverter / CoordinateConverter 经 AsyncAppender 真实路径的回归测试。
 *
 * <p>生产形态：文件通道全包 AsyncAppender，格式化（converter convert 调用）发生在
 * 异步投递 worker 线程上，该线程的 ThreadLocal MDC 恒为空——converter 必须读
 * {@code event.getMDCPropertyMap()}（ILoggingEvent 构造时的快照）而非线程 MDC。
 * 本测试用独立 LoggerContext + 捕获 appender（在 append 内即 worker 线程执行
 * layout.doLayout）复刻该路径，曾在旧实现（读 ThreadLocal）下复现生产空槽。
 *
 * @author coffee
 */
public class MdcConverterAsyncAppenderTest {

    /** ULID 字符集（Crockford Base32，去 I/L/O/U）。 */
    private static final String ULID_REGEX = "[0-9A-HJKMNP-TV-Z]{26}";

    private static final String ASYNC_LOGGER_NAME = "mdc-converter-async-test";
    private static final String SYNC_LOGGER_NAME = "mdc-converter-sync-test";
    private static final String COORDINATE = "com.test:integration-foo";

    /**
     * 在 append 调用线程（经 AsyncAppender 即为 worker 线程）立即执行 layout 格式化，
     * 复刻 FileAppender「事件投递到哪个线程就在哪个线程格式化」的真实时序。
     */
    public static class LayoutCapturingAppender extends AppenderBase<ILoggingEvent> {
        public final List<String> lines = new CopyOnWriteArrayList<>();
        public final List<String> formatThreads = new CopyOnWriteArrayList<>();
        public final CountDownLatch latch = new CountDownLatch(1);
        private final PatternLayout layout = new PatternLayout();

        /** Joran 配置装载：<pattern> 元素映射到本 setter。 */
        public void setPattern(String pattern) {
            layout.setPattern(pattern);
        }

        @Override
        public void start() {
            layout.setContext(context);
            layout.start();
            super.start();
        }

        @Override
        protected void append(ILoggingEvent eventObject) {
            lines.add(layout.doLayout(eventObject).trim());
            formatThreads.add(Thread.currentThread().getName());
            latch.countDown();
        }
    }

    private LoggerContext loggerContext;
    private LayoutCapturingAppender asyncCapture;
    private LayoutCapturingAppender syncCapture;

    @Before
    public void setUp() throws Exception {
        MDC.clear();
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<configuration>\n"
                + "  <conversionRule conversionWord=\"traceId\""
                + " converterClass=\"com.ecat.core.Utils.Mdc.TraceIdConverter\"/>\n"
                + "  <conversionRule conversionWord=\"coordinate\""
                + " converterClass=\"com.ecat.core.Utils.CoordinateConverter\"/>\n"
                + "  <appender name=\"asyncCapture\" class=\""
                + LayoutCapturingAppender.class.getName() + "\">\n"
                + "    <pattern>[%traceId] [%coordinate] %msg%n</pattern>\n"
                + "  </appender>\n"
                + "  <appender name=\"async\" class=\"" + AsyncAppender.class.getName() + "\">\n"
                + "    <queueSize>256</queueSize>\n"
                + "    <neverBlock>true</neverBlock>\n"
                + "    <appender-ref ref=\"asyncCapture\"/>\n"
                + "  </appender>\n"
                + "  <appender name=\"syncCapture\" class=\""
                + LayoutCapturingAppender.class.getName() + "\">\n"
                + "    <pattern>[%traceId] [%coordinate] %msg%n</pattern>\n"
                + "  </appender>\n"
                + "  <logger name=\"" + ASYNC_LOGGER_NAME + "\" level=\"INFO\">\n"
                + "    <appender-ref ref=\"async\"/>\n"
                + "  </logger>\n"
                + "  <logger name=\"" + SYNC_LOGGER_NAME + "\" level=\"INFO\">\n"
                + "    <appender-ref ref=\"syncCapture\"/>\n"
                + "  </logger>\n"
                + "</configuration>\n";
        loggerContext = new LoggerContext();
        loggerContext.setName("mdc-converter-test");
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(loggerContext);
        configurator.doConfigure(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
        AsyncAppender async = (AsyncAppender) ((ch.qos.logback.classic.Logger)
                loggerContext.getLogger(ASYNC_LOGGER_NAME)).getAppender("async");
        asyncCapture = (LayoutCapturingAppender) async.getAppender("asyncCapture");
        syncCapture = (LayoutCapturingAppender) ((ch.qos.logback.classic.Logger)
                loggerContext.getLogger(SYNC_LOGGER_NAME)).getAppender("syncCapture");
    }

    @After
    public void tearDown() {
        MDC.clear();
        if (loggerContext != null) {
            loggerContext.stop();
        }
    }

    /** 异步路径（生产形态）：MDC 在打日志线程设置，格式化在 AsyncAppender worker 线程。 */
    @Test
    public void asyncAppender_formatsOnWorkerThread_outputsUlidAndCoordinate() throws Exception {
        String traceId = TraceContext.generateTraceId();
        TraceContext.setTraceId(traceId);
        MdcCoordinateConverter.setCoordinate(COORDINATE);
        assertEquals(traceId, TraceContext.getTraceId());

        loggerContext.getLogger(ASYNC_LOGGER_NAME).info("hello async mdc");

        assertTrue("等待异步投递超时", asyncCapture.latch.await(10, TimeUnit.SECONDS));
        // 格式化确实发生在非打日志线程（AsyncAppender worker），坐实 ThreadLocal 读法失效的场景
        assertNotEquals(Thread.currentThread().getName(), asyncCapture.formatThreads.get(0));

        String line = asyncCapture.lines.get(0);
        // ULID 26 位透传保真（不截断）
        assertTrue("traceId 应为 26 位 ULID，实际输出: " + line,
                line.contains("[" + traceId + "]"));
        assertTrue(traceId.matches(ULID_REGEX));
        assertTrue("coordinate 应透传，实际输出: " + line, line.contains("[" + COORDINATE + "]"));
    }

    /** 异步路径：MDC 缺失时占位符语义不变（traceId=-、coordinate=core）。 */
    @Test
    public void asyncAppender_missingMdc_placeholdersUnchanged() throws Exception {
        MDC.clear();
        loggerContext.getLogger(ASYNC_LOGGER_NAME).info("no mdc");
        assertTrue(asyncCapture.latch.await(10, TimeUnit.SECONDS));
        assertEquals("[-] [core] no mdc", asyncCapture.lines.get(0));
    }

    /** 同步路径回归：converter 直接挂主线程 appender 时行为不变。 */
    @Test
    public void syncAppender_outputsUlidAndCoordinate() {
        String traceId = TraceContext.generateTraceId();
        TraceContext.setTraceId(traceId);
        MdcCoordinateConverter.setCoordinate(COORDINATE);

        loggerContext.getLogger(SYNC_LOGGER_NAME).info("hello sync mdc");

        assertEquals("[" + traceId + "] [" + COORDINATE + "] hello sync mdc",
                syncCapture.lines.get(0));
    }

    /** 同步路径：coordinate 缺失时默认 core（回归占位行为）。 */
    @Test
    public void syncAppender_missingCoordinate_defaultsToCore() {
        TraceContext.setTraceId(TraceContext.generateTraceId());
        MDC.remove(MdcCoordinateConverter.COORDINATE_KEY);

        loggerContext.getLogger(SYNC_LOGGER_NAME).info("no coordinate");

        String line = syncCapture.lines.get(0);
        assertTrue("coordinate 缺失应默认 core，实际输出: " + line, line.contains("[core]"));
    }
}
