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

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.ConsoleAppender;
import ch.qos.logback.core.PropertyDefinerBase;
import ch.qos.logback.core.spi.AppenderAttachable;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * System.out / System.err 裸打印收编进 logback（级别 + 限频 + 轮转统一管辖）。
 *
 * <p>为什么需要：第三方库的异常路径常走 {@code Throwable.printStackTrace()}（裸写 stderr，
 * 不经 slf4j），而生产启动脚本把 stdout/stderr 整体重定向到无轮转、无限频的单文件——
 * 串口 IO 异常风暴（3000 堆栈/秒 ≈ 25.9MB/s）正是经该通道刷爆磁盘的
 * （bug-record-20260907-093000 层 3）。桥接后：err → logger "STDERR" ERROR、
 * out → logger "STDOUT" INFO，全部事件复用既有 ErrorRateLimitFilter 限频 +
 * RollingFileAppender 轮转体系；两通道各自挂级别闸门（logback.xml），需要全静默
 * legacy System.out 时把 STDOUT logger 调成 OFF 即可。
 *
 * <p>挂载方式（logback 1.2.13 字节码实证）：logback.xml 在 console appender 声明
 * <b>之后</b>放 {@code <define name="streamCaptureMounted" class="...StreamCapture"/>}，
 * {@code DefinePropertyAction.end()} 按文档序在解析期同步调用 {@link #getPropertyValue()}
 * → {@link #install()}。选 {@code <define>} 而非 {@code <contextListener>} 的理由：
 * 前者单方法接口（PropertyDefiner）、执行时机就是文档序（字节码可证），后者还要实现
 * logback 上下文生命周期回调且时序语义无额外收益。
 *
 * <p>防回环（bug-record-20260907-104500 事故教训，字节级实证）：logback 1.2.13 的
 * ConsoleTarget 匿名 OutputStream.write 是 {@code getstatic System.out} <b>每次调用动态
 * 查流</b>——本类替换 System.out 后，console appender 的每次写入都回流进桥。此时：
 * ① 每条 console 日志被桥二次包装（正常日志变 "INFO STDOUT - ..." 条目）；② 跨线程锁序
 * 倒置死锁——业务裸 println 持 captured-PS 监视器→等 Bridge 锁→等 appender streamLock，
 * 正常日志持 streamLock→写 console→等 captured-PS 监视器，成环后全 JVM 日志冻结。
 * 根修 = {@link #ensureConsoleAppendersPinned()}：把宿主/默认 context 内所有
 * ConsoleAppender 经 setOutputStream <b>钉到 install 时冻结的原始流</b>，console 写入
 * 结构性不再进桥。{@link #IN_LOGGING} 重入守卫降级为兜底（防误绑 appender 的单线程
 * 无限递归），不再承担跨线程安全。
 */
public final class StreamCapture extends PropertyDefinerBase {

    /** legacy System.out 桥接后的 logger 名（logback.xml 挂级别闸门用） */
    public static final String STDOUT_LOGGER_NAME = "STDOUT";

    /** 第三方裸 printStackTrace 等裸 stderr 桥接后的 logger 名 */
    public static final String STDERR_LOGGER_NAME = "STDERR";

    /** 生产默认：静默 500ms 即认为条目完整（printStackTrace 尾段没有后续行时兜底落日志） */
    static final long DEFAULT_QUIET_WINDOW_MILLIS = 500L;

    /** 生产默认：守护线程 200ms 周期扫描静默条目（空转成本可忽略） */
    static final long DEFAULT_SWEEP_PERIOD_MILLIS = 200L;

    /** 聚合条目行数上限：畸形输出（连续栈帧行超百行）强制切分，防 StringBuilder 无界 */
    static final int MAX_ENTRY_LINES = 100;

    private static final Object INSTALL_LOCK = new Object();

    private static volatile boolean installed;

    private static Bridge stdoutBridge;
    private static Bridge stderrBridge;
    private static ScheduledExecutorService sweeper;
    private static PrintStream originalOut;
    private static PrintStream originalErr;

    /**
     * 钉流用的<b>不可关包装</b>（bug-record-20260907-104500 第三层：reset 致命关流）。
     * ConsoleAppender 的流生命周期归 logback 管：任何重配置（动态 jar 如 ruoyi 自带
     * 日志初始化触发 LoggerContext.reset）都会 stop 全部 appender，字节码实证
     * {@code OutputStreamAppender.stop() → closeOutputStream() → 流.close()}。若钉的是
     * 裸原始流，reset 一次就把真实 System.out/err 关死（PrintStream.close 后所有写入
     * 静默丢弃，生产形态：core 启动 26 秒后 console 全静默）。包装的 close() 恒 no-op，
     * 原始流生命周期由本类独占（uninstall 才还原），logback 不得越权关闭。
     */
    private static OutputStream pinnedOut;
    private static OutputStream pinnedErr;

    /**
     * 宿主 LoggerContext（执行 {@code <define>} 的那个 context，logback begin() 注入，
     * 字节码实证 setContext 先于 getPropertyValue）。生产中 = 默认 context；测试中可能
     * 是独立装配的 context。钉流遍历以此为准，辅以默认 factory context 兜底。
     */
    private static volatile ch.qos.logback.core.Context ownerContext;

    /** 钉流状态机：PENDING=待钉（install / define 重执行后）→ RUNNING → DONE；DONE 后周期性复查 */
    private static final AtomicInteger consolePinState = new AtomicInteger();
    private static final int PIN_PENDING = 0;
    private static final int PIN_RUNNING = 1;
    private static final int PIN_DONE = 2;

    /** 完成态后的重钉周期（拍数）：覆盖 reset 后新挂 / late-attached console appender。生产 200ms×300=60s */
    private static final int REPIN_INTERVAL_SWEEPS = 300;

    /** sweeper 单线程独占的拍数计数（无需并发原语），用于完成态的周期性重钉判定 */
    private static int sweepCount;

    /**
     * 重入守卫：本线程正处于「桥 → logger → appender」调用链内。此期间任何写回
     * System.out/err 的字节（appender 误绑到桥、logback 自身往 stderr 报错）都直写
     * install 时持有的原始流，绝不再次进 logger——终止单线程无限递归。console appender
     * 钉流（{@link #ensureConsoleAppendersPinned}）是防回环的根修，本守卫降级为兜底：
     * 钉流完成前的窗口期、或不经 logback 配置的误绑 appender 由它保正确性；跨线程
     * 死锁防护只依赖钉流（守卫按线程隔离，鞭长莫及）。
     */
    private static final ThreadLocal<Boolean> IN_LOGGING = new ThreadLocal<>();

    /** logback {@code <define>} 入口：按文档序在解析期触发一次安装 + 武装钉流 */
    @Override
    public String getPropertyValue() {
        install();
        // 宿主 context 记录 + 钉流重武装：logback 每次（重）装载本配置都会重执行 define——
        // reset 后 appender 全新（旧钉失效），这里归零让 sweeper 下一拍重钉
        ownerContext = getContext();
        consolePinState.set(PIN_PENDING);
        return "installed";
    }

    /** 生产入口（默认静默窗口/扫描周期） */
    public static void install() {
        install(DEFAULT_QUIET_WINDOW_MILLIS, DEFAULT_SWEEP_PERIOD_MILLIS);
    }

    /** 测试可见重载：注入短静默窗口/扫描周期，避免测试真实等待 */
    static void install(long quietWindowMillis, long sweepPeriodMillis) {
        synchronized (INSTALL_LOCK) {
            if (installed) {
                // 幂等：logback reset/重复装载会多次走 <define>，零副作用
                return;
            }
            // 先取 logger 再换流：若本 JVM 尚未初始化 slf4j，此调用触发 logback 初始化
            Logger outLogger = LoggerFactory.getLogger(STDOUT_LOGGER_NAME);
            Logger errLogger = LoggerFactory.getLogger(STDERR_LOGGER_NAME);
            consolePinState.set(PIN_PENDING);
            sweepCount = 0;

            originalOut = System.out;
            originalErr = System.err;
            stdoutBridge = new Bridge(Channel.STDOUT, outLogger, originalOut);
            stderrBridge = new Bridge(Channel.STDERR, errLogger, originalErr);
            pinnedOut = unclosable(originalOut);
            pinnedErr = unclosable(originalErr);
            // autoflush=true：行到达即推进桥（原 System.err 本就 autoflush；out 侧为了
            // 行缓冲语义也必须在 \n 把字节交给桥，否则行滞留在 PrintStream 内部缓冲）
            System.setOut(new PrintStream(stdoutBridge, true));
            System.setErr(new PrintStream(stderrBridge, true));
            installed = true;

            final long quietNanos = TimeUnit.MILLISECONDS.toNanos(quietWindowMillis);
            sweeper = Executors.newSingleThreadScheduledExecutor(runnable -> {
                Thread thread = new Thread(runnable, "stream-capture-flusher");
                thread.setDaemon(true);
                return thread;
            });
            sweeper.scheduleWithFixedDelay(() -> {
                try {
                    if (originalOut == null) {
                        // uninstall 竞态窗口（sweeper 已 shutdownNow，本拍可能在途）：直接放弃
                        return;
                    }
                    ensureConsoleAppendersPinned();
                    stdoutBridge.flushIfQuiet(quietNanos);
                    stderrBridge.flushIfQuiet(quietNanos);
                } catch (Throwable t) {
                    // scheduleWithFixedDelay 的任务抛异常会停摆后续调度，必须就地拦住；
                    // 故障信息直写原始 stderr（不经 logger，不给日志体系再制造输入）
                    t.printStackTrace(originalErr);
                }
            }, sweepPeriodMillis, sweepPeriodMillis, TimeUnit.MILLISECONDS);
            // 装载可观测性由 logback 自身的 define status 行承载（"Popping property definer
            // for property named [streamCaptureMounted]"），且 System.out 被替换即是事实证据
        }
    }

    /**
     * 还原全局流并冲掉尾部 pending 条目（防尾条目丢失）。测试与配置装载测试专用
     * （LogbackConfigLoadTest 加载生产 logback.xml 会经 define 触发安装，装卸对称
     * 才不污染同 JVM 的其他测试）；生产运行期无卸载语义。
     */
    static void uninstall() {
        synchronized (INSTALL_LOCK) {
            if (!installed) {
                return;
            }
            sweeper.shutdownNow();
            sweeper = null;
            // 先停 sweeper 再还原流，最后冲 pending：冲刷走 logger，此刻 System.out/err
            // 已是原始流，即使某 appender 反向写桥也天然落在原始流上，无递归窗口
            System.setOut(originalOut);
            System.setErr(originalErr);
            ownerContext = null;
            pinnedOut = null;
            pinnedErr = null;
            consolePinState.set(PIN_PENDING);
            stdoutBridge.flushPending();
            stderrBridge.flushPending();
            stdoutBridge = null;
            stderrBridge = null;
            originalOut = null;
            originalErr = null;
            installed = false;
        }
    }

    // ========== console appender 钉流（防回环根修，bug-record-20260907-104500） ==========

    /**
     * 把宿主/默认 context 内所有 ConsoleAppender 的输出流钉到 install 时冻结的原始流。
     * 只在 sweeper 线程调用——本方法此刻<b>不持有任何 Bridge/PS/appender 锁</b>。
     *
     * <p>为什么必须在无锁上下文执行：{@code ConsoleAppender.setOutputStream} 内部取
     * appender 的 streamLock（字节码实证）。若在 Bridge 锁内（emit 路径）执行：钉流者持
     * Bridge 等 streamLock，正常日志线程持 streamLock 等 captured-PS，业务打印线程持
     * captured-PS 等 Bridge——三线程成环即死锁。sweeper 无第二把锁，等待链无环。
     *
     * <p>时序：install/define 在 logback 配置中途执行，appender 尚未 attach 到 logger，
     * 此刻无法遍历——故惰性化：sweeper 首拍（sweepPeriodMillis 后）配置已完成再钉。
     * 未钉的窗口期内 console 写入仍会回流进桥，由 {@link #IN_LOGGING} 守卫终止递归，
     * 无死锁窗口内的正确性风险（死锁需跨线程锁序倒置，与钉流与否的触发概率相同，
     * 生产实测 11 分钟负载才命中一次；钉流后结构性消失）。
     */
    private static void ensureConsoleAppendersPinned() {
        int sweep = ++sweepCount;
        if (consolePinState.get() == PIN_PENDING) {
            if (!consolePinState.compareAndSet(PIN_PENDING, PIN_RUNNING)) {
                return;
            }
            pinAllKnownContexts();
            consolePinState.set(PIN_DONE);
        } else if (consolePinState.get() == PIN_DONE && sweep % REPIN_INTERVAL_SWEEPS == 0) {
            // 周期性复查：覆盖 reset 后新配置（define 重执行会归零状态，此处兜底
            // 不经 define 的新挂 console appender）
            pinAllKnownContexts();
        }
    }

    /** 宿主 context + 默认 factory context（生产中两者相同，测试中可能分离）各钉一遍 */
    private static void pinAllKnownContexts() {
        pinConsoleAppenders(ownerContext);
        ILoggerFactory factory = LoggerFactory.getILoggerFactory();
        if (factory instanceof LoggerContext && factory != ownerContext) {
            pinConsoleAppenders((LoggerContext) factory);
        }
    }

    /**
     * 原始流的不可关包装：write/flush 委托原始流，close 恒 no-op。钉流只钉包装——
     * appender.stop()（logback reset/重配置必经，动态 jar 如 ruoyi 的日志初始化会触发）
     * 的 closeOutputStream 落到 no-op 上，真实 System.out/err 不被 logback 越权关闭
     * （见 {@link #pinnedOut} 字段注释的事故形态）。
     */
    private static OutputStream unclosable(PrintStream original) {
        return new OutputStream() {
            @Override
            public void write(int b) {
                original.write(b);
            }

            @Override
            public void write(byte[] b, int off, int len) {
                original.write(b, off, len);
            }

            @Override
            public void flush() {
                original.flush();
            }

            @Override
            public void close() {
                // 故意 no-op：流生命周期归 StreamCapture 独占（uninstall 才还原）
            }
        };
    }

    /**
     * 遍历 context 的 ROOT + 全部 logger，钉住每个可达的 ConsoleAppender。
     *
     * <p>按 appender 身份去重与「已是目标流则跳过」都是<b>正确性必需</b>而非优化：
     * root 本身在 loggerCache 中（1.2 构造器字节码即 put），显式 ROOT 钉 + getLoggerList
     * 遍历会命中同一 appender 两次；同一 appender 也可能被多个 logger 共享挂接。而
     * setOutputStream <b>非幂等</b>：每次调用先 closeOutputStream 关旧流——重复钉会把
     * 刚钉上的原始流关掉（PrintStream.close 后所有写入静默丢弃，事故形态：console 全静默）。
     * 周期性重钉因此必须先比对当前流，仅在漂移（reset 新配/late-attach）时才真正换流。
     */
    private static void pinConsoleAppenders(ch.qos.logback.core.Context context) {
        if (!(context instanceof LoggerContext)) {
            return;
        }
        LoggerContext loggerContext = (LoggerContext) context;
        Set<Appender<ILoggingEvent>> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        pinConsolesOf(loggerContext.getLogger(ch.qos.logback.classic.Logger.ROOT_LOGGER_NAME), seen);
        for (ch.qos.logback.classic.Logger logger : loggerContext.getLoggerList()) {
            pinConsolesOf(logger, seen);
        }
    }

    private static void pinConsolesOf(ch.qos.logback.classic.Logger logger, Set<Appender<ILoggingEvent>> seen) {
        Iterator<Appender<ILoggingEvent>> it = logger.iteratorForAppenders();
        while (it.hasNext()) {
            pinConsole(it.next(), seen);
        }
    }

    /**
     * 单个 appender 钉流：ConsoleAppender 按其 target 换成冻结原始流的<b>不可关包装</b>
     * （{@link #unclosable}，防 reset 关流）；复合 appender（如 AsyncAppender 实现
     * AppenderAttachable）递归处理内层。appender 挂接图是 DAG（logback 配置无法构造环），
     * 无环风险。
     *
     * @param appender 待钉的 appender（ConsoleAppender 直钉，复合型递归）
     * @param seen     本轮 walk 已钉过的 appender（身份去重，防重复 setOutputStream 关流）
     */
    private static void pinConsole(Appender<ILoggingEvent> appender, Set<Appender<ILoggingEvent>> seen) {
        if (!seen.add(appender)) {
            return;
        }
        if (appender instanceof ConsoleAppender) {
            ConsoleAppender<ILoggingEvent> console = (ConsoleAppender<ILoggingEvent>) appender;
            String target = console.getTarget();
            if ("System.out".equals(target)) {
                if (console.getOutputStream() != pinnedOut) {
                    console.setOutputStream(pinnedOut);
                }
            } else if ("System.err".equals(target)) {
                if (console.getOutputStream() != pinnedErr) {
                    console.setOutputStream(pinnedErr);
                }
            }
        } else if (appender instanceof AppenderAttachable) {
            @SuppressWarnings("unchecked")
            AppenderAttachable<ILoggingEvent> composite = (AppenderAttachable<ILoggingEvent>) appender;
            Iterator<Appender<ILoggingEvent>> it = composite.iteratorForAppenders();
            while (it.hasNext()) {
                pinConsole(it.next(), seen);
            }
        }
    }

    /** 双通道分级（用户拍板）：err 是错误语义（第三方异常堆栈所在），out 是常规输出 */
    private enum Channel {
        STDOUT {
            @Override
            void emit(Logger logger, String entry) {
                logger.info(entry);
            }
        },
        STDERR {
            @Override
            void emit(Logger logger, String entry) {
                logger.error(entry);
            }
        };

        abstract void emit(Logger logger, String entry);
    }

    /**
     * 桥接 OutputStream：按行缓冲（\n 触发），printStackTrace 的续行并入当前条目，
     * 条目经 logger 整条发出（限频器按条判定，一个堆栈 = 一条日志）。
     *
     * <p>并发模型：PrintStream 的 write 均在自身监视器内调用本类（天然串行）；
     * pending 状态由内部锁保护（静默扫描线程与写入线程的冲刷互斥）。锁序为
     * 「captured-PS 监视器 → 桥锁 → logback streamLock → 原始流」——前提是 console
     * appender 已钉流（否则正常日志线程反向「streamLock → captured-PS」成环死锁，
     * bug-record-20260907-104500；钉流见 {@link #ensureConsoleAppendersPinned}）。
     */
    private static final class Bridge extends OutputStream {

        private final Channel channel;
        private final Logger logger;
        /** 重入直写兜底：install 时刻的原始流 */
        private final PrintStream original;
        private final Charset charset = Charset.defaultCharset();
        private final Object lock = new Object();
        /** 行内原始字节缓冲：0x0A 在 UTF-8/GBK 多字节序列中不出现，按字节扫 \n 不会切断字符 */
        private final ByteArrayOutputStream rawLine = new ByteArrayOutputStream();
        /** 聚合中的条目（多行以 \n 连接） */
        private final StringBuilder pendingEntry = new StringBuilder();
        private int pendingLines;
        private long lastAppendNanos;

        private Bridge(Channel channel, Logger logger, PrintStream original) {
            this.channel = channel;
            this.logger = logger;
            this.original = original;
        }

        @Override
        public void write(int b) {
            if (IN_LOGGING.get() != null) {
                // 重入（本线程正在 logger 调用链内写回）：直写原始流，绝不二次进 logger
                original.write(b);
                return;
            }
            if (b == '\n') {
                lineCompleted();
            } else {
                rawLine.write(b);
            }
        }

        @Override
        public void write(byte[] b, int off, int len) {
            if (IN_LOGGING.get() != null) {
                original.write(b, off, len);
                return;
            }
            for (int i = off; i < off + len; i++) {
                if (b[i] == '\n') {
                    lineCompleted();
                } else {
                    rawLine.write(b[i]);
                }
            }
        }

        private void lineCompleted() {
            String line = new String(rawLine.toByteArray(), charset);
            rawLine.reset();
            // CRLF 边界：\r\n 平台的行尾 \r 剥掉保消息干净（Linux 无影响）
            if (line.endsWith("\r")) {
                line = line.substring(0, line.length() - 1);
            }
            synchronized (lock) {
                if (pendingLines > 0 && isStackContinuation(line)) {
                    // 栈帧续行并入当前条目，暂不发出（等非续行/静默窗口收口）
                    pendingEntry.append('\n').append(line);
                } else {
                    // 新条目首行：先把上一条目（含完整堆栈）整条落日志
                    flushLocked();
                    pendingEntry.append(line);
                }
                pendingLines++;
                lastAppendNanos = System.nanoTime();
                if (pendingLines >= MAX_ENTRY_LINES) {
                    // 兜底：畸形超长条目强制切分，防无界攒行
                    flushLocked();
                }
            }
        }

        /**
         * printStackTrace 的续行：{@code "\tat ..."}（栈帧）与 {@code "\t... N more"}
         * （Cause 共同帧省略行）——两者都是堆栈的一部分，并入同一条才满足
         * 「一条日志含完整堆栈」。{@code Caused by:}/{@code Suppressed:} 顶格开头，
         * 按新条目处理（其后续帧并入它），异常头行的签名不受影响。
         */
        private static boolean isStackContinuation(String line) {
            return line.startsWith("\tat ") || line.startsWith("\t...");
        }

        /** 持锁调用：把聚合条目整条发出并清空（空条目无操作） */
        private void flushLocked() {
            if (pendingLines == 0) {
                return;
            }
            String entry = pendingEntry.toString();
            pendingEntry.setLength(0);
            pendingLines = 0;
            emit(entry);
        }

        /**
         * 守卫只在此处设置：emit 的三个来源（行完成/静默扫描/卸载冲刷）都在守卫之外
         * 进入，保证「appender 写回桥」的字节必先撞上 write() 的守卫检查走原始流。
         * logger 调用若抛出（appender 深度故障）不吞——沿用严格模式，让故障可见。
         */
        private void emit(String entry) {
            IN_LOGGING.set(Boolean.TRUE);
            try {
                channel.emit(logger, entry);
            } finally {
                IN_LOGGING.remove();
            }
        }

        /** 静默扫描：条目超过静默窗口没有新行（如风暴最后一个堆栈的尾段）即冲刷 */
        void flushIfQuiet(long quietNanos) {
            synchronized (lock) {
                if (pendingLines > 0 && System.nanoTime() - lastAppendNanos >= quietNanos) {
                    flushLocked();
                }
            }
        }

        void flushPending() {
            synchronized (lock) {
                flushLocked();
            }
        }
    }
}
