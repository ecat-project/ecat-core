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

package com.ecat.core.Task.engine;

import java.util.List;
import java.util.concurrent.AbstractExecutorService;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RunnableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 指定车道键的 {@link ExecutorService} 视图——{@link SchedulerEngine#executorFor(String)} 的
 * 返回物（IO 收敛 P0：modbus source 事务须按「资源坐标」分道——同源串行防帧碰撞、异源并行，
 * 而资源坐标无法由提交方 MDC 集成坐标表达）。
 *
 * <p>薄路由对象：execute/submit/invokeAll 构造 {@link EngineTask} 时把车道键写死为构造传入的
 * 显式键（不查提交方 MDC），其余一切（MDC 捕获传播、有界排队、车道熔断、看门狗、硬超时）
 * 与门面 MDC 推导通道完全同权——车道/熔断/看门狗行为对指定键一视同仁。每 key 由引擎缓存
 * 一个实例（{@code executorFor(k)} 幂等同对象），不建池不建线程。
 *
 * <p>生命周期跟随引擎，视图自身无独立生命周期：引擎停机后提交抛
 * {@link java.util.concurrent.RejectedExecutionException}；{@code shutdown()}/{@code shutdownNow()}
 * 显式拒绝——车道视图是共享调度引擎的窗口，单个调用方销毁自己的资源时不得停掉全平台调度器，
 * 「资源已死」语义由调用方自持标志位表达（禁止借用 executor.isShutdown() 判死源）。
 *
 * @author coffee
 */
final class LaneKeyExecutor extends AbstractExecutorService {

    private final SchedulerEngine engine;
    private final String laneKey;

    LaneKeyExecutor(SchedulerEngine engine, String laneKey) {
        this.engine = engine;
        this.laneKey = laneKey;
    }

    @Override
    public void execute(Runnable command) {
        if (command instanceof EngineTask) {
            // AbstractExecutorService.submit/invokeAll 的内部通路：任务已带本视图车道键，直接派发
            engine.dispatchExplicit((EngineTask<?>) command);
            return;
        }
        engine.dispatchExplicit(engine.laneTaskFor(command, laneKey));
    }

    @Override
    protected <T> RunnableFuture<T> newTaskFor(Runnable runnable, T value) {
        // submit/invokeAll 默认机制经此构造任务再走 execute（与门面 newTaskFor 同款路径，仅键不同）
        return engine.laneTaskFor(runnable, value, laneKey);
    }

    @Override
    protected <T> RunnableFuture<T> newTaskFor(Callable<T> callable) {
        return engine.laneTaskFor(callable, laneKey);
    }

    // ---- 生命周期 = 引擎生命周期（共享视图，无独立停机语义） ----

    @Override
    public void shutdown() {
        throw new UnsupportedOperationException(
                "车道视图无独立生命周期（共享调度引擎窗口），停机请调 SchedulerEngine.shutdown()");
    }

    @Override
    public List<Runnable> shutdownNow() {
        throw new UnsupportedOperationException(
                "车道视图无独立生命周期（共享调度引擎窗口），停机请调 SchedulerEngine.shutdownNow()");
    }

    @Override
    public boolean isShutdown() {
        return engine.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return engine.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return engine.awaitTermination(timeout, unit);
    }
}
