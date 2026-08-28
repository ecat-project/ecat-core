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

package com.ecat.core.Task.runner;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import com.ecat.core.Device.RemovalHost;
import com.ecat.core.Task.NamedThreadFactory;
import com.ecat.core.Utils.Log;
import com.ecat.core.Utils.LogFactory;
import com.ecat.core.Utils.Mdc.TraceContext;

/**
 * 生命周期绑定的有界执行器工厂（应用场景：集成/设备需要自有工作道——
 * 调度互斥、阻塞 IO 卸载、per-设备命令背压）。
 * 名字内部派生自宿主类，线程数由调用者定义；关闭动作经 host.onRemove
 * 注册，宿主拆卸时自动注销——忘了关池在签名层面不可能。
 * 无全局态、无键（实例即道）。
 *
 * <p><b>为什么是宿主绑定而非共享池</b>：共享池必须回答「键怎么输入、键冲突谁守卫、
 * 线程数对谁合适」三个中心治理问题，而工作道的真实粒度就是宿主本身（一个设备、
 * 一个集成、一个端点）——把池挂回宿主生命周期，治理问题消失：不学键词汇、
 * 不学线程治理、不学收尾，使用者全程唯一接触点是 {@code bounded(n, this)}。
 *
 * <p><b>有界与拒绝</b>：排队界 {@link #QUEUE_CAPACITY}=64——宿主消费停滞时积压超界
 * 即显式信号（{@link RejectedExecutionException} 同步抛：过期即弃，善后决策强制留在
 * 调用点，不吞不兜底）。
 *
 * <p><b>逐任务异常隔离</b>：{@code execute(Runnable)} 路径每任务 catch 记 warn 续跑
 * （裸 ThreadPoolExecutor 的未捕获异常会杀 worker 线程再补新线程并走默认 uncaught
 * handler，不镜像）；{@code submit(Callable)} 路径异常经 future 原样透传（FutureTask
 * 自捕获，包装层不重复处理）。MDC：提交时捕获、执行时恢复（TraceContext，与既有
 * 执行域同权）。
 *
 * <p><b>拆卸=shutdownNow（终态过期即弃）</b>：宿主停止（设备 stop/集成 release）是
 * 终态——排队任务即过期工作，丢弃；在飞任务被中断。与进程停机的优雅排空是
 * 有意差异（两种停机语义不同，非回归）。
 *
 * <p>用法：
 * <pre>{@code
 * ExecutorService lane = HostedExecutors.bounded(1, this);   // 串行单飞=threads 1
 * lane.execute(this::drainQueue);
 * }</pre>
 */
public final class HostedExecutors {

    private static final Log log = LogFactory.getLogger(HostedExecutors.class);

    /** 宿主内排队界：超界=该宿主消费停滞的显式拒绝信号（过期即弃），防无界积压。 */
    static final int QUEUE_CAPACITY = 64;

    private HostedExecutors() {
    }

    /**
     * 建一个生命周期绑定宿主的有界执行器。
     *
     * <p>线程名派生 {@code 宿主类名@实例哈希-序号}（{@code HikvisionIsapiDevice@1a2b-0}——
     * 类名=谁的池，实例后缀=哪个实例，per-设备道天然区分）；daemon 线程。
     * 关闭动作在工厂内部经 {@code host.onRemove(executor::shutdownNow)} 注册，
     * 宿主拆卸 sweep 时统一执行——调用方不接触收尾。
     *
     * @param threads 线程数（1 即串行单飞道；由调用方按自身阻塞/互斥需求定义）
     * @param host    宿主（设备或集成，移除动作注册面）
     * @return 有界执行器（排队界 {@value #QUEUE_CAPACITY}，满拒同步抛 REE）
     * @throws IllegalArgumentException threads &lt; 1 或 host 为 null（调用错误非边界，严格模式）
     * @throws RejectedExecutionException 宿主已停止后建池属病态调用（刚建的池随拒绝
     *         立即 shutdownNow，不留无人拆卸的泄漏，异常原样上抛）
     */
    public static ExecutorService bounded(int threads, RemovalHost host) {
        if (threads < 1) {
            throw new IllegalArgumentException("threads 不能小于 1（调用错误，无默认值兜底）: " + threads);
        }
        if (host == null) {
            throw new IllegalArgumentException("host 不能为 null（池的生命周期必须有宿主可挂）");
        }
        String derivedName = host.getClass().getSimpleName()
                + "@" + Integer.toHexString(System.identityHashCode(host));
        HostedBoundedExecutor executor = new HostedBoundedExecutor(threads, derivedName);
        try {
            host.onRemove(executor::shutdownNow);
        } catch (RejectedExecutionException hostAlreadySwept) {
            // 宿主已终态：调用方拿不到池引用（本方法尚未返回），无人能再关它——
            // 工厂就地关闭再原样上抛，不留「注册被拒但池已活」的泄漏
            executor.shutdownNow();
            throw hostAlreadySwept;
        }
        return executor;
    }

    /**
     * 有界池本体：唯一增强点是 {@link #execute(Runnable)} 的逐任务守卫（MDC 恢复 +
     * 异常记 warn 续跑）。submit 系（FutureTask 自捕获异常进 future）同样经
     * execute 上道——守卫的 MDC 部分对其生效，catch 部分因 FutureTask.run 永不抛
     * 而天然不触发（无双重处理）。
     */
    private static final class HostedBoundedExecutor extends ThreadPoolExecutor {

        HostedBoundedExecutor(int threads, String derivedName) {
            super(threads, threads, 0L, TimeUnit.MILLISECONDS,
                    new LinkedBlockingQueue<Runnable>(QUEUE_CAPACITY),
                    new NamedThreadFactory(derivedName, true),
                    new ThreadPoolExecutor.AbortPolicy());
        }

        @Override
        public void execute(Runnable command) {
            if (command == null) {
                throw new NullPointerException("task 不能为 null");
            }
            super.execute(guarded(command));
        }
    }

    /**
     * 逐任务守卫：MDC 提交时捕获、执行时恢复（TraceContext.wrapRunnable）；外层
     * catch Throwable 记 warn 续跑——单任务异常不杀 worker（守卫在线程名上下文中
     * 输出，池与宿主可从日志定位）。
     */
    private static Runnable guarded(Runnable command) {
        Runnable wrapped = TraceContext.wrapRunnable(command);
        return () -> {
            try {
                wrapped.run();
            } catch (Throwable t) {
                log.warn("[hosted-executor] 任务异常（记 warn 续跑，不杀 worker）", t);
            }
        };
    }
}
