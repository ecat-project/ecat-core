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

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import com.ecat.core.Integration.IntegrationBase;
import com.ecat.core.Task.NamedThreadFactory;
import com.ecat.core.Utils.SchedulerClock;
import com.ecat.core.Utils.Log;
import com.ecat.core.Utils.LogFactory;

/**
 * 对一组集成有界执行 onPause / onRelease 的停机阶段。
 *
 * <p><b>为什么要 guard 线程</b>：onPause/onRelease 是集成自定义代码（停轮询、关串口/网络连接），
 * 无法证明其必然返回（如对失联 TCP 对端做优雅断开可能挂分钟级）。直接在 shutdown hook 线程
 * 串行调用会把「停机必须最终能退」托付给最差的集成。本阶段把每个调用放到单线程守护 executor
 * 上，按 deadline 有界等待：超时则 WARN 未完成项 + 中断 + 放弃（守护线程随 JVM 退出），继续
 * 下一个集成。
 *
 * <p><b>双层预算</b>：单集成等待上限（perIntegration，防一个 stuck 集成饿死同阶段其余集成）
 * 与阶段 deadline（防整阶段超支）取小。串行而非并行：与运行时 disable 路径
 * （IntegrationManager.disableIntegration）同样串行语义；停机期无吞吐诉求，串行还能避免
 * 并发 onPause 抢占共享资源（同一串口/连接池）。
 *
 * @author coffee
 */
public final class IntegrationLifecycleStage implements ShutdownStage {

    /** 对集成执行的生命周期动作。 */
    public enum LifecycleAction {
        PAUSE {
            @Override
            void apply(IntegrationBase integration) {
                integration.onPause();
            }
        },
        RELEASE {
            @Override
            void apply(IntegrationBase integration) {
                integration.onRelease();
            }
        };

        abstract void apply(IntegrationBase integration);
    }

    private static final Log log = LogFactory.getLogger(IntegrationLifecycleStage.class);

    private final String name;
    private final long budgetMillis;
    /** 单集成上限：一个卡死集成只吃掉自己的份额，不饿死同阶段其余集成（阶段预算仍是总闸）。 */
    private final long perIntegrationMillis;
    private final LifecycleAction action;
    /** 目标集成快照提供者（阶段执行时取值；已按确定性排序、完成设备/服务分区）。 */
    private final Supplier<List<IntegrationBase>> targetsProvider;

    public IntegrationLifecycleStage(String name, long budgetMillis, long perIntegrationMillis,
            LifecycleAction action, Supplier<List<IntegrationBase>> targetsProvider) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("name 不能为空");
        }
        if (budgetMillis <= 0 || perIntegrationMillis <= 0) {
            throw new IllegalArgumentException("budget/perIntegration 必须 > 0: name=" + name
                    + " budget=" + budgetMillis + " perIntegration=" + perIntegrationMillis);
        }
        if (action == null || targetsProvider == null) {
            throw new IllegalArgumentException("action/targetsProvider 不能为 null: name=" + name);
        }
        this.name = name;
        this.budgetMillis = budgetMillis;
        this.perIntegrationMillis = perIntegrationMillis;
        this.action = action;
        this.targetsProvider = targetsProvider;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public long budgetMillis() {
        return budgetMillis;
    }

    @Override
    public boolean execute(long deadlineNanos, SchedulerClock clock) {
        List<IntegrationBase> targets = targetsProvider.get();
        if (targets.isEmpty()) {
            return true;
        }
        // 守护线程：被放弃的 straggler 不得阻止 JVM 退出（非守护线程会阻塞 hook 完成后的退出）
        ExecutorService guard = Executors.newSingleThreadExecutor(
                new NamedThreadFactory("shutdown-" + action.name().toLowerCase(), true));
        boolean allCompleted = true;
        try {
            for (IntegrationBase integration : targets) {
                long stageRemaining = deadlineNanos - clock.nanoTime();
                if (stageRemaining <= 0L) {
                    ShutdownLog.warn(log, "SHUTDOWN_INTEGRATION_SKIPPED stage={} integration={} reason=stage-deadline-exhausted",
                            name, label(integration));
                    allCompleted = false;
                    continue;
                }
                // 单集成份额与阶段剩余取小：既保证「stuck 只吃自己份额」，也不越过阶段预算
                long waitNanos = Math.min(perIntegrationMillis * 1_000_000L, stageRemaining);
                Future<?> future = guard.submit(() -> action.apply(integration));
                try {
                    future.get(waitNanos, TimeUnit.NANOSECONDS);
                } catch (TimeoutException e) {
                    // 中断请求已发出；不 join——若 onPause 响应中断即可很快退出，否则放弃等待
                    future.cancel(true);
                    allCompleted = false;
                    ShutdownLog.warn(log, "SHUTDOWN_INTEGRATION_STUCK stage={} integration={} waitMs={}（已放弃等待，该项收尾未完成，其尾批可能丢失；继续停机）",
                            name, label(integration), TimeUnit.NANOSECONDS.toMillis(waitNanos));
                } catch (ExecutionException e) {
                    allCompleted = false;
                    ShutdownLog.error(log, "SHUTDOWN_INTEGRATION_FAILED stage={} integration={}",
                            name, label(integration), e.getCause());
                } catch (InterruptedException e) {
                    // shutdown hook 线程不应被中断；如实记录并终止本阶段（finally 收 guard）
                    Thread.currentThread().interrupt();
                    allCompleted = false;
                    ShutdownLog.warn(log, "SHUTDOWN_STAGE_INTERRUPTED stage={} integration={}（hook 线程被中断，提前结束本阶段）",
                            name, label(integration));
                    return allCompleted;
                }
            }
        } finally {
            guard.shutdownNow();
        }
        return allCompleted;
    }

    /** 日志标识：优先 coordinate（全局唯一），未 onLoad 的实例退回类名。 */
    private static String label(IntegrationBase integration) {
        String coordinate = integration.getCoordinate();
        return coordinate != null ? coordinate : integration.getClass().getSimpleName();
    }
}
