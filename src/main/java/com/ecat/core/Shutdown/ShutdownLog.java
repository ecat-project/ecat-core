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

package com.ecat.core.Shutdown;

import java.io.PrintStream;

import com.ecat.core.Utils.Log;

/**
 * 停机期专用双通道日志出口：同一行先同步写 stdout、再走原 logger（app 通道尽力而为）。
 * 仅限 Shutdown 包内使用——停机恰恰是最需要日志的时刻（哪阶段超时/卡死要可见），
 * 而这恰是 app 通道最不可靠的时刻。
 *
 * <p><b>为什么 stdout 可靠而 app 通道不可靠（论证链）</b>：
 * <ul>
 * <li>logback 五通道全部是 AsyncAppender（daemon worker + neverBlock=true）：事件只进
 *     内存队列，由 daemon 线程异步落盘。JVM 退出序列不保证该 daemon 排空队列——终验实证
 *     SIGTERM 停机 26s 编排完整完成，而 logs 只留下第一行 SHUTDOWN_SEQUENCE_START，
 *     后续 STAGE_DONE/COMPLETE 全丢。neverBlock 意味着入队永不阻塞也永不告警，丢失是静默的。</li>
 * <li>System.out 是 fd 1 直写：start 脚本以 {@code nohup java ... >> core-api.log 2>&1}
 *     启动，fd 1 已指向日志文件；{@link PrintStream#println(String)} 在调用线程内同步
 *     write(2)，无用户态队列、无 daemon 线程参与——字节进入内核页缓存即与 JVM 存亡无关。
 *     显式 {@code flush()} 不依赖 System.out 的 autoFlush 构造细节（被 System.setOut
 *     替换过的流无此保证）。</li>
 * <li>先写 stdout 再走 logger：顺序保证「logger 调用若失败，stdout 行也已落」，
 *     可见性不依赖 app 通道存活。</li>
 * </ul>
 *
 * <p>stdout 写失败被显式吞掉（见 {@link #write}）——停机绝不能因日志死。
 *
 * @author coffee
 */
final class ShutdownLog {

    /** stdout 通道出口：生产恒为 System.out；包内可替换（单测 seam）。 */
    private static volatile PrintStream out = System.out;

    private ShutdownLog() {
    }

    /** 单测 seam：替换 stdout 通道出口（伪造 System.out 断言行内容）；测试结束须还原。 */
    static void setOut(PrintStream stream) {
        out = stream;
    }

    static void info(Log logger, String format, Object... args) {
        emit(format, args);
        logger.info(format, args);
    }

    static void warn(Log logger, String format, Object... args) {
        emit(format, args);
        logger.warn(format, args);
    }

    /** 末位参数是 Throwable 时 app 通道照常落全栈（slf4j 末参语义不变）。 */
    static void error(Log logger, String format, Object... args) {
        emit(format, args);
        logger.error(format, args);
    }

    /**
     * 仅 stdout 的单行输出：最终汇总专用。app 通道已有 COMPLETE/INCOMPLETE 行携带同等内容
     * 不再重复；而 stdout 是停机期唯一有保底的出口，汇总必须无条件落在它上面。
     */
    static void emitLine(String line) {
        write(line);
    }

    private static void emit(String format, Object... args) {
        write(format(format, args));
    }

    /**
     * stdout 同步写一行。
     *
     * <p>停机路径的显式例外（非业务兜底）：stdout 可能已被关闭 / 替换为失效流（不猜具体成因），
     * 日志通道失效绝不能终止停机序列——捕获后继续。PrintStream 的 IO 失败按其 API 契约记内部
     * trouble 标志而不抛 checked IOException（编译器亦不允许 catch 不可达的 IOException），
     * 故此处只需兜 RuntimeException。
     */
    private static void write(String line) {
        try {
            out.println(line);
            out.flush();
        } catch (RuntimeException e) {
            // 吞掉继续：停机必须最终能退，日志只是尽力而为
        }
    }

    /**
     * {} 占位替换（slf4j 同语义的简化版）；未被占位消耗的末位 Throwable 以异常摘要收尾
     * （stdout 单行，全栈走 app 通道）。
     */
    private static String format(String format, Object... args) {
        StringBuilder sb = new StringBuilder(format.length() + 48);
        int argIndex = 0;
        for (int i = 0; i < format.length(); i++) {
            char c = format.charAt(i);
            if (c == '{' && i + 1 < format.length() && format.charAt(i + 1) == '}' && argIndex < args.length) {
                sb.append(args[argIndex++]);
                i++;
            } else {
                sb.append(c);
            }
        }
        if (argIndex < args.length && args[args.length - 1] instanceof Throwable) {
            sb.append(" :: ").append(args[args.length - 1]);
        }
        return sb.toString();
    }
}
