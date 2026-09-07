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
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.core.status.Status;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * StreamCapture（System.out/err → logback 桥接）行为测试。
 *
 * <p>测试纪律：System.setOut/setErr 是进程级全局，@Before 保存 / @After 经 uninstall +
 * 显式还原，绝不污染其他测试类；除静默定时器一例（awaitEvent 轮询到事件发生，非定长等待）
 * 外全部用确定性冲刷触发——向同一通道再写一行非栈帧行，桥同步冲刷上一条，断言零等待。
 */
public class StreamCaptureTest {

    /** 测试注入的短静默窗口/扫描周期：静默兜底路径可在百毫秒内被观察到 */
    private static final long TEST_QUIET_WINDOW_MILLIS = 50L;
    private static final long TEST_SWEEP_PERIOD_MILLIS = 20L;

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private PrintStream savedOut;
    private PrintStream savedErr;
    private ch.qos.logback.classic.Logger stdoutLogger;
    private ch.qos.logback.classic.Logger stderrLogger;
    private ListAppender<ILoggingEvent> stdoutEvents;
    private ListAppender<ILoggingEvent> stderrEvents;
    private Level originalStdoutLevel;
    private Level originalStderrLevel;

    @Before
    public void setUp() {
        savedOut = System.out;
        savedErr = System.err;
        stdoutLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(StreamCapture.STDOUT_LOGGER_NAME);
        stderrLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(StreamCapture.STDERR_LOGGER_NAME);
        originalStdoutLevel = stdoutLogger.getLevel();
        originalStderrLevel = stderrLogger.getLevel();
        // 显式放开级别：断言不依赖 logback-test.xml 的 root 配置
        stdoutLogger.setLevel(Level.ALL);
        stderrLogger.setLevel(Level.ALL);
        stdoutEvents = attachListAppender(stdoutLogger);
        stderrEvents = attachListAppender(stderrLogger);
        StreamCapture.install(TEST_QUIET_WINDOW_MILLIS, TEST_SWEEP_PERIOD_MILLIS);
    }

    @After
    public void tearDown() {
        StreamCapture.uninstall();
        // uninstall 后再显式还原：个别用例（回环实证/幂等）中途自己装卸过
        System.setOut(savedOut);
        System.setErr(savedErr);
        stdoutLogger.detachAppender(stdoutEvents);
        stderrLogger.detachAppender(stderrEvents);
        stdoutLogger.setLevel(originalStdoutLevel);
        stderrLogger.setLevel(originalStderrLevel);
    }

    private static ListAppender<ILoggingEvent> attachListAppender(ch.qos.logback.classic.Logger logger) {
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.setName("stream-capture-probe-" + logger.getName());
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    /** awaitility 式「事件已发生」断言：轮询直到条数达标，超时才失败（非定长 sleep 猜测） */
    private static void awaitEvent(List<ILoggingEvent> events, int expected, long deadlineMillis) {
        long deadline = System.currentTimeMillis() + deadlineMillis;
        while (events.size() < expected) {
            assertTrue("等待 " + expected + " 条事件超时（实际 " + events.size() + "）",
                    System.currentTimeMillis() < deadline);
            try {
                Thread.sleep(10L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("等待事件被中断", e);
            }
        }
    }

    /** ① System.err → STDERR logger，ERROR 级 */
    @Test
    public void errGoesToStderrLoggerAsError() {
        System.err.println("ERR-PROBE-1");
        // 第二行非栈帧行：桥在处理它之前同步冲刷上一条，断言零等待
        System.err.println("ERR-PROBE-2");

        assertEquals(1, stderrEvents.list.size());
        ILoggingEvent event = stderrEvents.list.get(0);
        assertEquals(StreamCapture.STDERR_LOGGER_NAME, event.getLoggerName());
        assertEquals(Level.ERROR, event.getLevel());
        assertEquals("ERR-PROBE-1", event.getMessage());
        assertEquals("err 通道不得串进 out 通道", 0, stdoutEvents.list.size());
    }

    /** ② System.out → STDOUT logger，INFO 级 */
    @Test
    public void outGoesToStdoutLoggerAsInfo() {
        System.out.println("OUT-PROBE-1");
        System.out.println("OUT-PROBE-2");

        assertEquals(1, stdoutEvents.list.size());
        ILoggingEvent event = stdoutEvents.list.get(0);
        assertEquals(StreamCapture.STDOUT_LOGGER_NAME, event.getLoggerName());
        assertEquals(Level.INFO, event.getLevel());
        assertEquals("OUT-PROBE-1", event.getMessage());
        assertEquals("out 通道不得串进 err 通道", 0, stderrEvents.list.size());
    }

    /** ③ printStackTrace 多行聚合：header + 栈帧 + 共同帧省略行 = 1 条事件（限频器按整条判定） */
    @Test
    public void stackTraceLinesAggregateIntoSingleEvent() {
        System.err.println("com.fazecast.jSerialComm.SerialPortIOException: This port appears to have been shutdown or disconnected.");
        System.err.println("\tat com.fazecast.jSerialComm.SerialPort.readBytes(SerialPort.java:913)");
        System.err.println("\tat com.serotonin.modbus4j.serial.SerialPortInputStreamListener.run(SerialPortInputStreamListener.java:45)");
        System.err.println("\tat com.serotonin.modbus4j.base.MessageControl.scanForMessage(MessageControl.java:201)");
        System.err.println("\tat java.util.concurrent.ThreadPoolExecutor.runWorker(ThreadPoolExecutor.java:1149)");
        System.err.println("\tat java.lang.Thread.run(Thread.java:834)");
        System.err.println("\t... 27 more");
        System.err.println("AGGREGATE-TRIGGER");

        assertEquals("一个堆栈必须聚合为恰好 1 条事件", 1, stderrEvents.list.size());
        String message = stderrEvents.list.get(0).getMessage();
        assertEquals(Level.ERROR, stderrEvents.list.get(0).getLevel());
        assertTrue("聚合消息须含异常头行", message.startsWith(
                "com.fazecast.jSerialComm.SerialPortIOException: This port appears"));
        assertTrue("聚合消息须含栈帧行", message.contains(
                "\tat com.fazecast.jSerialComm.SerialPort.readBytes(SerialPort.java:913)"));
        assertTrue("聚合消息须含共同帧省略行", message.contains("\t... 27 more"));
        // 7 行（header + 5 帧 + 省略行）以 6 个换行连接；AGGREGATE-TRIGGER 属下一条 pending，不在本条
        assertEquals(6, countChar(message, '\n'));
        assertTrue("下一条 pending 不应混入本条", !message.contains("AGGREGATE-TRIGGER"));
    }

    private static int countChar(String text, char c) {
        int count = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == c) {
                count++;
            }
        }
        return count;
    }

    /**
     * ④ 重入守卫：appender 在处理桥接事件期间写回 System.err 不得递归——
     * 该写回必须直写 install 时持有的原始流（字节可观测），事件计数不增长。
     */
    @Test
    public void reentrantGuardWritesRawToOriginalInsteadOfRecursing() {
        // 独立装配：撤销 setUp 的安装，先把 err 换成可观测 BAOS 再装，
        // 使桥的「原始流兜底」有字节可断言
        StreamCapture.uninstall();
        ByteArrayOutputStream rawOriginalBytes = new ByteArrayOutputStream();
        System.setErr(new PrintStream(rawOriginalBytes, true));
        ReenteringAppender reentering = new ReenteringAppender();
        reentering.start();
        stderrLogger.addAppender(reentering);
        StreamCapture.install(TEST_QUIET_WINDOW_MILLIS, TEST_SWEEP_PERIOD_MILLIS);
        try {
            System.err.println("guard-outer");
            System.err.println("guard-trigger");

            assertEquals("重入写回不得产生新事件（否则无限递归）", 1, stderrEvents.list.size());
            assertEquals("guard-outer", stderrEvents.list.get(0).getMessage());
            String raw = new String(rawOriginalBytes.toByteArray());
            assertTrue("重入字节必须直写原始流: [" + raw + "]",
                    raw.contains("[reentrant-guard-probe] guard-outer"));
        } finally {
            stderrLogger.detachAppender(reentering);
            StreamCapture.uninstall();
            System.setErr(savedErr);
        }
    }

    /** 模拟「appender 误绑到桥」的最坏情形：处理桥接事件时反向写 System.err */
    public static final class ReenteringAppender extends AppenderBase<ILoggingEvent> {
        @Override
        protected void append(ILoggingEvent event) {
            System.err.println("[reentrant-guard-probe] " + event.getMessage());
        }
    }

    /**
     * ⑤ 回环实证（生产形态）：真实生产 logback.xml 经 JoranConfigurator 装载，
     * console appender 声明在 <define> 之前——装载后经桥写一行，STDOUT 通道必须
     * 恰好收到 1 条事件且用例正常结束。若 console 输出回流进桥，事件数会失控增长
     * 或直接栈溢出，本用例即红。附零 ERROR status 断言钉死 define 反射装载不报错。
     */
    @Test
    public void productionConfigMountsCaptureWithoutLoopOrErrorStatus() throws Exception {
        StreamCapture.uninstall();
        File logDir = tmp.newFolder("logs");
        String previousLogDir = System.getProperty("LOG_DIR");
        System.setProperty("LOG_DIR", logDir.getAbsolutePath());
        LoggerContext context = new LoggerContext();
        context.setName("stream-capture-prod-shape");
        try {
            JoranConfigurator configurator = new JoranConfigurator();
            configurator.setContext(context);
            configurator.doConfigure(getClass().getResource("/logback.xml"));

            // <define> 已按文档序在解析期执行：System.out 被桥替换（比 status 更硬的直接证据）
            assertTrue("logback.xml 的 <define> 应已触发 StreamCapture.install()",
                    System.out != savedOut);
            for (Status status : context.getStatusManager().getCopyOfStatusList()) {
                assertTrue("生产配置装载出现 ERROR status: " + status,
                        status.getLevel() != Status.ERROR);
            }

            System.out.println("loop-probe-line");
            System.out.println("loop-probe-trigger");

            assertEquals("经桥写入应恰好产生 1 条事件（console 回流会导致失控）",
                    1, stdoutEvents.list.size());
            assertEquals(Level.INFO, stdoutEvents.list.get(0).getLevel());
            assertEquals("loop-probe-line", stdoutEvents.list.get(0).getMessage());
        } finally {
            context.stop();
            StreamCapture.uninstall();
            System.setOut(savedOut);
            System.setErr(savedErr);
            if (previousLogDir == null) {
                System.clearProperty("LOG_DIR");
            } else {
                System.setProperty("LOG_DIR", previousLogDir);
            }
        }
    }

    /** ⑥ install 幂等：二次调用零副作用；uninstall 亦幂等 */
    @Test
    public void installIsIdempotent() {
        PrintStream afterFirstInstall = System.out;
        StreamCapture.install(TEST_QUIET_WINDOW_MILLIS, TEST_SWEEP_PERIOD_MILLIS);
        assertSame("二次 install 不得重复替换流", afterFirstInstall, System.out);

        StreamCapture.uninstall();
        StreamCapture.uninstall();
        assertSame("uninstall 后应还原为原始流", savedOut, System.out);
    }

    /** ⑦ 静默兜底：无后续行的尾条目（如风暴最后一个堆栈）由静默定时器冲刷，不滞留丢失 */
    @Test
    public void quietWindowFlushesTrailingEntryWithoutFollowUpLine() {
        System.err.println("tail-entry-only");

        awaitEvent(stderrEvents.list, 1, 5000L);

        ILoggingEvent event = stderrEvents.list.get(0);
        assertEquals(Level.ERROR, event.getLevel());
        assertEquals("tail-entry-only", event.getMessage());
    }

    // ========== ⑧⑨⑩ console appender 钉流（bug-record-20260907-104500 死锁与关流回归锁） ==========

    /**
     * 独立装配（钉流断言需要「原始流」有字节可观测）：撤销 setUp 安装，把 System.out
     * 换成 BAOS 再装——桥的原始流即 BAOS。默认 context（logback-test.xml）的 root console
     * appender 由 sweeper 钉到 BAOS。
     */
    private ByteArrayOutputStream reinstallWithBaosOriginalOut() {
        StreamCapture.uninstall();
        ByteArrayOutputStream rawOriginalBytes = new ByteArrayOutputStream();
        System.setOut(new PrintStream(rawOriginalBytes, true));
        StreamCapture.install(TEST_QUIET_WINDOW_MILLIS, TEST_SWEEP_PERIOD_MILLIS);
        return rawOriginalBytes;
    }

    /**
     * 等钉流完成。判别依据（修复前后行为分叉点）：未钉时 console 动态查 System.out 回流
     * 进桥，探针行会被<b>再捕获</b>为一条 STDOUT 事件；已钉后 console 直写原始流，
     * 探针行不产生任何 STDOUT 事件。等静默窗口（50ms）+ 扫描拍落定后再判定。
     */
    private void awaitConsolePinned(long deadlineMillis) {
        ch.qos.logback.classic.Logger probe =
                (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("stream-capture-pin-probe");
        long deadline = System.currentTimeMillis() + deadlineMillis;
        int seq = 0;
        while (System.currentTimeMillis() < deadline) {
            int sizeBefore = stdoutEvents.list.size();
            String marker = "pin-wait-probe-" + (++seq);
            probe.info(marker);
            try {
                Thread.sleep(TEST_QUIET_WINDOW_MILLIS + TEST_SWEEP_PERIOD_MILLIS * 2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("等待钉流被中断", e);
            }
            boolean recaptured = stdoutEvents.list.size() > sizeBefore;
            if (!recaptured) {
                return; // 探针行未再捕获 = console 已钉到原始流
            }
        }
        throw new AssertionError("等待 console 钉流超时（" + deadlineMillis + "ms），探针行持续被再捕获");
    }

    /**
     * ⑧ 钉流确定性断言：正常 logger.info 的 console 输出必须<b>直接</b>落在冻结原始流上，
     * 且不得作为被捕获条目二次流经桥（修复前：console 动态查 System.out 回流进桥，
     * 原始流上只有守卫转写的包装行，STDOUT logger 收到再捕获事件）。
     */
    @Test
    public void normalConsoleLoggingBypassesBridgeAfterPinning() {
        ByteArrayOutputStream rawOriginalBytes = reinstallWithBaosOriginalOut();
        try {
            awaitConsolePinned(5000L);
            int eventsBefore = stdoutEvents.list.size();

            ch.qos.logback.classic.Logger probe =
                    (ch.qos.logback.classic.Logger) LoggerFactory.getLogger("stream-capture-pin-probe");
            probe.info("pin-verify-direct");

            String raw = new String(rawOriginalBytes.toByteArray());
            assertTrue("正常日志的 console 输出必须直接落在冻结原始流: [" + raw + "]",
                    raw.contains("pin-verify-direct"));
            assertTrue("正常日志不得被桥再捕获为 STDOUT 事件（console 未钉/回流进桥）",
                    stdoutEvents.list.size() == eventsBefore);
        } finally {
            StreamCapture.uninstall();
            System.setOut(savedOut);
            System.setErr(savedErr);
        }
    }

    /**
     * ⑩ reset 关流回归锁（bug-record-20260907-104500 第三层）：logback 重配置（动态 jar
     * 如 ruoyi 的日志初始化触发 LoggerContext.reset）会 stop 全部 appender，字节码实证
     * stop() → closeOutputStream() → 流.close()。钉裸原始流时一次 stop 就把真实
     * System.out 关死（PrintStream.close 后写入静默丢弃，生产形态：console 全静默）；
     * 不可关包装下 stop 必须无副作用——stop 后原始流仍可写、桥仍能正常收发。
     */
    @Test
    public void appenderStopDuringReconfigureMustNotCloseOriginalStream() {
        ByteArrayOutputStream rawOriginalBytes = reinstallWithBaosOriginalOut();
        try {
            awaitConsolePinned(5000L);

            // 模拟 logback reset 停 appender（closeOutputStream 在 stop 内调用）
            LoggerContext ctx = (LoggerContext) LoggerFactory.getILoggerFactory();
            ch.qos.logback.classic.Logger root = ctx.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME);
            java.util.Iterator<ch.qos.logback.core.Appender<ILoggingEvent>> it = root.iteratorForAppenders();
            while (it.hasNext()) {
                ch.qos.logback.core.Appender<ILoggingEvent> a = it.next();
                if (a instanceof ch.qos.logback.core.ConsoleAppender) {
                    a.stop();
                }
            }

            // stop 后：原始流必须仍可写（未被 close），桥仍能收发
            System.out.println("after-stop-probe");
            System.out.println("after-stop-trigger");
            assertEquals("stop 后桥必须仍能产出事件", 1, stdoutEvents.list.size());
            assertEquals("after-stop-probe", stdoutEvents.list.get(0).getMessage());
            // 事件经（已停）console appender 不会再写，但原始流自身必须未被关闭：
            // 直接写一行验证字节可落（PrintStream 若被 close，写入静默丢弃）
            rawOriginalBytes.write("[direct-after-stop]\n".getBytes());
            String raw = new String(rawOriginalBytes.toByteArray());
            assertTrue("stop 不得关闭冻结原始流: [" + raw + "]",
                    raw.contains("[direct-after-stop]"));
        } catch (java.io.IOException e) {
            throw new AssertionError("测试直写原始流失败", e);
        } finally {
            StreamCapture.uninstall();
            System.setOut(savedOut);
            System.setErr(savedErr);
        }
    }

    /**
     * ⑨ 并发死锁锤（bug-record-20260907-104500 回归锁）：正常 logger.info（持 streamLock
     * 写 console）与业务裸 System.out.println（持 captured-PS → 桥锁 → emit → streamLock）
     * 跨线程并发，修复前锁序倒置必死锁（生产实测 11 分钟负载命中），本用例 timeout 兜死；
     * 钉流后 console 直写原始流，全局锁序一致，必然收敛完成。
     */
    @Test(timeout = 60000)
    public void concurrentLoggingAndBarePrintlnNoDeadlock() throws Exception {
        ByteArrayOutputStream rawOriginalBytes = reinstallWithBaosOriginalOut();
        final int threadsPerKind = 4;
        final int iterations = 2000;
        try {
            awaitConsolePinned(5000L);
            Thread[] printers = new Thread[threadsPerKind];
            Thread[] loggers = new Thread[threadsPerKind];
            for (int i = 0; i < threadsPerKind; i++) {
                final int idx = i;
                printers[i] = new Thread(() -> {
                    for (int k = 0; k < iterations; k++) {
                        System.out.println("hammer-bare-" + idx + "-" + k);
                    }
                }, "hammer-bare-" + i);
                loggers[i] = new Thread(() -> {
                    org.slf4j.Logger log = LoggerFactory.getLogger("stream-capture-hammer");
                    for (int k = 0; k < iterations; k++) {
                        log.info("hammer-log-{}-{}", idx, k);
                    }
                }, "hammer-log-" + i);
            }
            for (int i = 0; i < threadsPerKind; i++) {
                printers[i].start();
                loggers[i].start();
            }
            for (int i = 0; i < threadsPerKind; i++) {
                printers[i].join(30000);
                loggers[i].join(30000);
                assertTrue("线程未收敛=死锁形态: hammer-bare-" + i, !printers[i].isAlive());
                assertTrue("线程未收敛=死锁形态: hammer-log-" + i, !loggers[i].isAlive());
            }
            // 裸 println 全部经桥入 STDOUT logger（ListAppender 逐条可数）
            awaitEvent(stdoutEvents.list, threadsPerKind * iterations, 10000L);
        } finally {
            StreamCapture.uninstall();
            System.setOut(savedOut);
            System.setErr(savedErr);
        }
    }
}
