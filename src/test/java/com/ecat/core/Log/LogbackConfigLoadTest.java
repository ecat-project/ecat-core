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

import ch.qos.logback.classic.AsyncAppender;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.FileAppender;
import ch.qos.logback.core.filter.Filter;
import ch.qos.logback.core.rolling.RollingFileAppender;
import ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy;
import ch.qos.logback.core.spi.FilterReply;
import ch.qos.logback.core.status.Status;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * 五通道 logback.xml 配置装载测试。
 *
 * <p>用独立 {@link LoggerContext} + {@link JoranConfigurator} 加载主配置
 * （不触碰默认 LoggerFactory 全局）；日志目录经 LOG_DIR 系统属性注入临时目录，
 * 行为断言依赖 LoggerContext.stop() 确定性排空异步队列，禁 Thread.sleep。
 */
public class LogbackConfigLoadTest {

    private static final long ONE_GB = 1L << 30;

    /** 通道表：异步 appender 名 -> 通道文件名（诊断 DEBUG 是后续阶段结构缝，不落配置） */
    private static final String[][] CHANNELS = {
            {"async_lifecycle", "lifecycle.log"},
            {"async_comm", "comm-health.log"},
            {"async_error", "error.log"},
            {"async_app", "app.log"},
    };

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private LoggerContext context;
    private String previousLogDir;

    @Before
    public void setUp() throws Exception {
        File logDir = tmp.newFolder("logs");
        previousLogDir = System.getProperty("LOG_DIR");
        System.setProperty("LOG_DIR", logDir.getAbsolutePath());

        context = new LoggerContext();
        context.setName("logback-config-load-test");
        JoranConfigurator configurator = new JoranConfigurator();
        configurator.setContext(context);
        configurator.doConfigure(getClass().getResource("/logback.xml"));
    }

    @After
    public void tearDown() {
        context.stop();
        if (previousLogDir == null) {
            System.clearProperty("LOG_DIR");
        } else {
            System.setProperty("LOG_DIR", previousLogDir);
        }
    }

    private Logger comEcatLogger() {
        return context.getLogger("com.ecat");
    }

    private AsyncAppender asyncAppender(String name) {
        return (AsyncAppender) comEcatLogger().getAppender(name);
    }

    /** logback 1.2 的 AsyncAppender 只允许挂一个内层 appender，直接取其唯一文件通道并校验目标文件名 */
    private RollingFileAppender<ILoggingEvent> fileAppenderOf(String asyncName, String expectedFile) {
        AsyncAppender async = asyncAppender(asyncName);
        assertNotNull("缺少异步 appender: " + asyncName, async);
        FileAppender<ILoggingEvent> file = null;
        for (Iterator<Appender<ILoggingEvent>> it = async.iteratorForAppenders(); it.hasNext(); ) {
            Appender<ILoggingEvent> attached = it.next();
            assertTrue("AsyncAppender 只应挂载一个文件 appender", file == null);
            file = (FileAppender<ILoggingEvent>) attached;
        }
        assertNotNull("异步 appender " + asyncName + " 缺少内层文件 appender", file);
        assertTrue("通道文件名应为 " + expectedFile + "，实际 " + file.getFile(),
                file.getFile().endsWith(expectedFile));
        return (RollingFileAppender<ILoggingEvent>) file;
    }

    @Test
    public void fourFileChannelsConfiguredWithoutErrorStatus() {
        // 配置装载零 ERROR（自定义 filter/turboFilter 反射实例化失败会以 ERROR status 暴露）
        List<Status> statuses = context.getStatusManager().getCopyOfStatusList();
        for (Status status : statuses) {
            assertTrue("配置装载出现 ERROR status: " + status, status.getLevel() != Status.ERROR);
        }

        // 四个文件通道就位：生命周期 / 通讯健康 / 错误 / 应用
        for (String[] channel : CHANNELS) {
            fileAppenderOf(channel[0], channel[1]);
        }

        // console（dev）与 SSE 广播不异步化、保留直挂
        assertNotNull(comEcatLogger().getAppender("console"));
        assertNotNull(comEcatLogger().getAppender("broadcast"));
    }

    @Test
    public void rootAndEcatLevelsAreInfo() {
        // DEBUG 占实测体积 ~90%：root 与 com.ecat 默认 INFO 是止血主力
        assertEquals(Level.INFO, context.getLogger(Logger.ROOT_LOGGER_NAME).getLevel());
        assertEquals(Level.INFO, comEcatLogger().getLevel());
    }

    @Test
    public void ecatLevelCanBeOverriddenBySystemProperty() throws Exception {
        // 注释里承诺的临时排障开关必须真实生效：-Decat.log.level=debug 全量打开 ecat DEBUG
        String previous = System.setProperty("ecat.log.level", "debug");
        try {
            LoggerContext debugContext = new LoggerContext();
            debugContext.setName("logback-config-load-test-debug");
            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(debugContext);
            configurator.doConfigure(getClass().getResource("/logback.xml"));

            assertEquals(Level.DEBUG, debugContext.getLogger("com.ecat").getLevel());
            debugContext.stop();
        } finally {
            if (previous == null) {
                System.clearProperty("ecat.log.level");
            } else {
                System.setProperty("ecat.log.level", previous);
            }
        }
    }

    @Test
    public void asyncParametersAndTotalSizeCapBudget() {
        for (String[] channel : CHANNELS) {
            String asyncName = channel[0];
            AsyncAppender async = asyncAppender(asyncName);
            assertNotNull(asyncName + " 未配置", async);
            assertEquals(asyncName + " queueSize", 256, async.getQueueSize());
            assertEquals(asyncName + " discardingThreshold=queueSize*0.2", 51, async.getDiscardingThreshold());
            assertTrue(asyncName + " neverBlock", async.isNeverBlock());
            assertFalse(asyncName + " includeCallerData", async.isIncludeCallerData());
        }

        // 保留期（天）走 policy getter：生命周期 180 / 通讯健康 90 / 错误 90 / 应用 30
        int[] expectedHistory = {180, 90, 90, 30};
        for (int i = 0; i < CHANNELS.length; i++) {
            RollingFileAppender<ILoggingEvent> appender = fileAppenderOf(CHANNELS[i][0], CHANNELS[i][1]);
            SizeAndTimeBasedRollingPolicy policy = (SizeAndTimeBasedRollingPolicy) appender.getRollingPolicy();
            assertEquals(CHANNELS[i][0] + " maxHistory", expectedHistory[i], policy.getMaxHistory());
        }

        // logback 1.2 的 totalSizeCap 无 getter，从配置期 status 行（"setting totalSizeCap to N MB"，每策略一条）合计；
        // 预算兜底：四通道 cap 之和必须留出 1GB 上限的余量（诊断通道是后续阶段）
        Pattern capPattern = Pattern.compile("setting totalSizeCap to (\\d+) MB");
        long capSum = 0;
        int capCount = 0;
        for (Status status : context.getStatusManager().getCopyOfStatusList()) {
            Matcher matcher = capPattern.matcher(String.valueOf(status.getMessage()));
            if (matcher.find()) {
                capCount++;
                capSum += Long.parseLong(matcher.group(1)) * 1024 * 1024;
            }
        }
        assertEquals("应有且仅有四个通道配置 totalSizeCap", CHANNELS.length, capCount);
        assertTrue("totalSizeCap 合计 " + capSum + " 超出 1GB 兜底预算", capSum < ONE_GB);
    }

    @Test
    public void markerRoutingFiltersAttached() {
        // 生命周期/通讯健康通道：marker 命中 ACCEPT、未命中 DENY（独占式通道）
        assertExclusiveMarkerFilter("async_lifecycle", "LIFECYCLE");
        assertExclusiveMarkerFilter("async_comm", "COMM");

        // 应用/错误通道：LIFECYCLE、COMM 命中即 DENY（已划入专属通道的事件不双写）
        for (String asyncName : new String[]{"async_app", "async_error"}) {
            assertEquals(2, markerDenyFilterCount(asyncName));
        }
    }

    @Test
    public void infoStillReachesAppChannelAfterSplit() throws Exception {
        Logger service = context.getLogger("com.ecat.core.ConfigService");
        service.info("app-channel-regression-line");
        service.warn("warn-line-to-error-channel");
        service.info(LogMarkers.LIFECYCLE, "lifecycle-transition-line");

        // LoggerContext.stop() 确定性排空异步队列并关闭文件流，之后读文件内容即最终结果
        context.stop();

        String app = readFile("app.log");
        assertTrue("常规 INFO 必须仍进应用通道", app.contains("app-channel-regression-line"));
        assertTrue("WARN 同样不低于应用通道 INFO 阈值", app.contains("warn-line-to-error-channel"));
        assertTrue("生命周期事件独占专属通道，不得双写进应用通道", !app.contains("lifecycle-transition-line"));

        String lifecycle = readFile("lifecycle.log");
        assertTrue("LIFECYCLE marker 事件必须进生命周期通道", lifecycle.contains("lifecycle-transition-line"));

        String error = readFile("error.log");
        assertTrue("WARN 必须进错误通道", error.contains("warn-line-to-error-channel"));
        assertTrue("生命周期事件不得进错误通道", !error.contains("lifecycle-transition-line"));
    }

    private void assertExclusiveMarkerFilter(String asyncName, String markerName) {
        for (Filter<ILoggingEvent> filter : asyncAppender(asyncName).getCopyOfAttachedFiltersList()) {
            if (filter instanceof MarkerRoutingFilter
                    && markerName.equals(((MarkerRoutingFilter) filter).getMarker())) {
                assertEquals("marker 命中应 ACCEPT", FilterReply.ACCEPT, ((MarkerRoutingFilter) filter).getOnMatch());
                assertEquals("marker 未命中应 DENY", FilterReply.DENY, ((MarkerRoutingFilter) filter).getOnMismatch());
                return;
            }
        }
        throw new AssertionError(asyncName + " 缺少 marker=" + markerName + " 的路由过滤器");
    }

    private long markerDenyFilterCount(String asyncName) {
        long count = 0;
        for (Filter<ILoggingEvent> filter : asyncAppender(asyncName).getCopyOfAttachedFiltersList()) {
            if (filter instanceof MarkerRoutingFilter) {
                MarkerRoutingFilter routing = (MarkerRoutingFilter) filter;
                assertEquals("应用/错误通道上的 marker 过滤器只能是命中即拒（防双写）", FilterReply.DENY,
                        routing.getOnMatch());
                assertEquals("未命中须 NEUTRAL（继续走阈值过滤）", FilterReply.NEUTRAL, routing.getOnMismatch());
                count++;
            }
        }
        return count;
    }

    private String readFile(String name) throws Exception {
        File file = new File(System.getProperty("LOG_DIR"), name);
        assertTrue("日志文件应已生成: " + name, file.exists());
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
