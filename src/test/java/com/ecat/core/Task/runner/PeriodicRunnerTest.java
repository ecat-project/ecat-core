package com.ecat.core.Task.runner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.ecat.core.Utils.Mdc.MdcContext;
import com.ecat.core.Utils.Mdc.TraceContext;

/**
 * PeriodicRunner 契约测试（29 号 v2 库级工具：执行器/缝由消费方传入，零中心运行时足迹）。
 * 测试缝三件套与 http 域 S0 穿刺同构：捕获型 ShotScheduler 替身（记录每发命令/延迟/可取消
 * 桩）+ 测试线程手动到拍 + 脚本化 RoundSchedule——零后台线程、零真实时钟（禁 sleep 同步）。
 *
 * <p>覆盖面（对应 S0 滚动模板评估五个抽取面）：
 * <ul>
 *   <li>面1 单发+MDC：fireAfter 提交时捕获（coordinate）、到拍恢复、无 traceId 补生成；</li>
 *   <li>面2 完成点重排骨架：首发按策略延迟、轮体在「提交 coordinate+每轮新 traceId」下执行、
 *       结算续段恢复本轮上下文后取下一拍延迟；</li>
 *   <li>面3 句柄竞态收口：cancel 撤销待发单发、取消后到拍空操作、在飞轮结算不重排、
 *       chain.fireAfter 中段单发被 track 收口；</li>
 *   <li>面4 过期即弃：onFire 判 drop 时不触碰轮体、按 drop 延迟重排；</li>
 *   <li>轮体 begin 同步抛=异常轮等价（结算照常重排，永不注销）；停机 REE 首发上抛/续段收口。</li>
 * </ul>
 */
public class PeriodicRunnerTest {

    private CaptureShots shots;

    @Before
    public void setUp() {
        shots = new CaptureShots();
    }

    @After
    public void tearDown() {
        TraceContext.restore(null);
        MdcContext.clearCoordinate();
    }

    private PeriodicRunner runner() {
        return PeriodicRunner.on(shots);
    }

    // ==================== 面1：单发原语 + MDC ====================

    @Test
    public void fireAfterPropagatesSubmitterMdcAndEnsuresTraceId() {
        TraceContext.clearTraceId();
        MdcContext.setCoordinate("com-serial:test");
        final AtomicReference<String> seenCoordinate = new AtomicReference<>();
        final AtomicReference<String> seenTraceId = new AtomicReference<>();
        runner().fireAfter(() -> {
            seenCoordinate.set(MdcContext.getCoordinate());
            seenTraceId.set(TraceContext.getTraceId());
        }, 5L);

        // 到拍侧：清空当前线程 MDC 后触发，命令内看到恢复出的坐标 + 补生成的 traceId
        TraceContext.restore(null);
        MdcContext.clearCoordinate();
        shots.fire(0);
        assertEquals("到拍恢复提交时 coordinate", "com-serial:test", seenCoordinate.get());
        assertNotNull("无 traceId 的提交在到拍时补生成", seenTraceId.get());
    }

    // ==================== 面2：周期链骨架 + 逐轮 MDC ====================

    @Test
    public void firstShotArmsAtStrategyFirstDelay() {
        ScriptedSchedule schedule = new ScriptedSchedule();
        schedule.firstDelay = 1_234L;
        runner().periodic("t", completedRound(true), schedule).start();
        assertEquals("首发延迟来自策略 firstDelayMillis", 1, shots.shots.size());
        assertEquals(1_234L, shots.shots.get(0).delayMillis);
    }

    @Test
    public void roundBodyRunsUnderSubmitCoordinateWithFreshTraceIdPerRound() {
        TraceContext.clearTraceId();
        MdcContext.setCoordinate("com-modbus:test");
        final List<String> traceIds = new CopyOnWriteArrayList<>();
        final List<String> coordinates = new CopyOnWriteArrayList<>();
        ScriptedSchedule schedule = new ScriptedSchedule();
        runner().periodic("t", () -> {
            traceIds.add(TraceContext.getTraceId());
            coordinates.add(MdcContext.getCoordinate());
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }, schedule).start();

        shots.fire(0);
        shots.fire(1);

        assertEquals("每轮都执行（两轮）", 2, traceIds.size());
        assertEquals("轮内 coordinate=提交时捕获", "com-modbus:test", coordinates.get(0));
        assertEquals("轮内 coordinate=提交时捕获", "com-modbus:test", coordinates.get(1));
        assertNotEquals("周期轮每轮换新 traceId", traceIds.get(0), traceIds.get(1));
    }

    @Test
    public void settleRearmsAtStrategyDelayUnderRoundContext() {
        TraceContext.clearTraceId();
        ScriptedSchedule schedule = new ScriptedSchedule();
        schedule.settleDelay = 4_321L;
        final AtomicReference<String> bodyTraceId = new AtomicReference<>();
        runner().periodic("t", () -> {
            bodyTraceId.set(TraceContext.getTraceId());
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }, schedule).start();

        shots.fire(0);
        // 结算续段在「本轮上下文」下取下一拍（同步完成 CF → settle 在到拍线程内联执行）
        assertEquals("结算重排延迟来自策略 onSettleRearmMillis", 4_321L,
                shots.shots.get(1).delayMillis);
        assertEquals("结算续段恢复本轮 traceId（非结算线程原上下文）",
                bodyTraceId.get(), schedule.traceIdSeenAtSettle);
    }

    // ==================== 面4：过期即弃 ====================

    @Test
    public void staleDropSkipsRoundBodyAndRearmsAtDropDelay() {
        AtomicInteger roundCalls = new AtomicInteger();
        ScriptedSchedule schedule = new ScriptedSchedule();
        schedule.dropDelay = 3_500L;
        runner().periodic("t", () -> {
            roundCalls.incrementAndGet();
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }, schedule).start();

        shots.fire(0);
        assertEquals("过期即弃：轮体不得触碰", 0, roundCalls.get());
        assertEquals("弃拍按 drop 延迟重排", 3_500L, shots.shots.get(1).delayMillis);
        assertEquals("弃拍不结算（不问 onSettleRearmMillis）", 0, schedule.settleCount);
    }

    // ==================== 轮体异常=异常轮等价（永不注销） ====================

    @Test
    public void roundBodyThrowingSettlesAsFailedRoundAndChainContinues() {
        ScriptedSchedule schedule = new ScriptedSchedule();
        schedule.settleDelay = 100L;
        runner().periodic("t", () -> {
            throw new IllegalStateException("begin 炸了");
        }, schedule).start();

        shots.fire(0);
        assertEquals("begin 同步抛按 failedFuture 等价：结算照常重排", 2, shots.shots.size());
        assertEquals(100L, shots.shots.get(1).delayMillis);
    }

    @Test
    public void nullRoundBodySettlesAsFailedRoundWithExplicitCause() {
        ScriptedSchedule schedule = new ScriptedSchedule();
        schedule.settleDelay = 100L;
        runner().periodic("t", () -> null, schedule).start();

        shots.fire(0);
        assertEquals("null 轮体=契约违反按异常轮结算：链不得无声终止", 2, shots.shots.size());
        assertEquals(100L, shots.shots.get(1).delayMillis);
    }

    // ==================== 面3：句柄竞态收口 ====================

    @Test
    public void cancelRevokesPendingShotAndLaterFireIsNoOp() {
        ScriptedSchedule schedule = new ScriptedSchedule();
        AtomicInteger roundCalls = new AtomicInteger();
        PeriodicChain chain = runner().periodic("t", () -> {
            roundCalls.incrementAndGet();
            return CompletableFuture.completedFuture(Boolean.TRUE);
        }, schedule).start();

        assertTrue(chain.isRunning());
        chain.cancel();
        assertTrue(chain.isCancelled());
        assertTrue("cancel 须撤销待发单发", shots.shots.get(0).future.isCancelled());

        shots.fire(0);
        assertEquals("取消后到拍不得执行轮体", 0, roundCalls.get());
        assertEquals("取消后不得重排下一拍", 1, shots.shots.size());
    }

    @Test
    public void cancelDuringInFlightRoundDiscardsSettleRearm() {
        ScriptedSchedule schedule = new ScriptedSchedule();
        final CompletableFuture<Boolean> pending = new CompletableFuture<>();
        PeriodicChain chain = runner().periodic("t", () -> pending, schedule).start();

        shots.fire(0);
        chain.cancel();
        pending.complete(Boolean.TRUE);
        assertEquals("在飞轮取消后结算不得重排", 1, shots.shots.size());
        assertEquals("取消后的结算不推进策略锚点（不问 onSettleRearmMillis）", 0,
                schedule.settleCount);
    }

    @Test
    public void chainFireAfterIsMdcWrappedAndCancelRevoked() {
        TraceContext.clearTraceId();
        MdcContext.setCoordinate("com-tcp:test");
        final AtomicReference<String> seenCoordinate = new AtomicReference<>();
        ScriptedSchedule schedule = new ScriptedSchedule();
        PeriodicChain chain = runner().periodic("t", completedRound(true), schedule).start();

        chain.fireAfter(() -> seenCoordinate.set(MdcContext.getCoordinate()), 50L);
        assertEquals("链上中段单发（重试/超时）经同一缝提交", 2, shots.shots.size());
        assertEquals(50L, shots.shots.get(1).delayMillis);

        TraceContext.restore(null);
        MdcContext.clearCoordinate();
        chain.cancel();
        assertTrue("链 cancel 须撤销链上待发中段单发（trackPending 收口）",
                shots.shots.get(1).future.isCancelled());

        TraceContext.restore(null);
        MdcContext.setCoordinate("com-tcp:test");
        shots.fire(1);
        assertEquals("中段单发到拍恢复提交时 coordinate", "com-tcp:test", seenCoordinate.get());
    }

    // ==================== 停机（REE）收口 ====================

    @Test
    public void rejectedExecutionOnRearmTerminatesChainWithoutThrowing() {
        final AtomicInteger invocations = new AtomicInteger();
        ShotScheduler rejectingAfterFirst = (command, delayMillis) -> {
            if (invocations.incrementAndGet() > 1) {
                throw new RejectedExecutionException("已停机");
            }
            return shots.fireAfter(command, delayMillis);
        };
        ScriptedSchedule schedule = new ScriptedSchedule();
        PeriodicRunner.on(rejectingAfterFirst)
                .periodic("t", completedRound(true), schedule).start();

        shots.fire(0);
        assertEquals("续段遇停机：链终止且异常不上抛（显式收口可观测）", 1, shots.shots.size());
    }

    @Test
    public void startPropagatesRejectedExecutionFromFirstShot() {
        ShotScheduler rejected = (command, delayMillis) -> {
            throw new RejectedExecutionException("已停机");
        };
        ScriptedSchedule schedule = new ScriptedSchedule();
        try {
            PeriodicRunner.on(rejected).periodic("t", completedRound(true), schedule).start();
            fail("停机执行器上起链必须上抛 REE（调用方错误，严格模式）");
        } catch (RejectedExecutionException expected) { }
    }

    // ==================== start 严格性 / FireDecision 契约 ====================

    @Test
    public void startTwiceIsRejected() {
        ScriptedSchedule schedule = new ScriptedSchedule();
        PeriodicChain chain = runner().periodic("t", completedRound(true), schedule).start();
        try {
            chain.start();
            fail("链不可二次 start（双链竞态）");
        } catch (IllegalStateException expected) { }
    }

    @Test
    public void fireDecisionDropRejectsNegativeDelay() {
        try {
            FireDecision.drop(-1L);
            fail("drop 负延迟必须 IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    @Test
    public void runDecisionHasNoRearmDelay() {
        FireDecision run = FireDecision.run();
        assertTrue(run.shouldRun());
        try {
            run.rearmDelayMillis();
            fail("run 决策无重排延迟，读取必须 IllegalStateException");
        } catch (IllegalStateException expected) { }
    }

    // ==================== 测试缝三件套（面5，域复制模板） ====================

    /** 捕获型单发缝替身：记录每发（已包装命令/延迟/可取消桩/提交时 MDC），测试线程手动到拍。 */
    static final class CaptureShots implements ShotScheduler {

        static final class Shot {
            final Runnable command;
            final long delayMillis;
            final StubFuture future = new StubFuture();
            final Map<String, String> submitMdc;

            Shot(Runnable command, long delayMillis) {
                this.command = command;
                this.delayMillis = delayMillis;
                this.submitMdc = TraceContext.capture();
            }
        }

        final List<Shot> shots = new CopyOnWriteArrayList<>();

        @Override
        public ScheduledFuture<?> fireAfter(Runnable command, long delayMillis) {
            Shot shot = new Shot(command, delayMillis);
            shots.add(shot);
            return shot.future;
        }

        void fire(int index) {
            shots.get(index).command.run();
        }
    }

    /** 可取消的最小 ScheduledFuture 桩：只承载 cancel/isCancelled 语义。 */
    static final class StubFuture implements ScheduledFuture<Object> {
        volatile boolean cancelled;

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            cancelled = true;
            return true;
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }

        @Override
        public boolean isDone() {
            return cancelled;
        }

        @Override
        public Object get() {
            throw new UnsupportedOperationException("测试桩不支持取值");
        }

        @Override
        public Object get(long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException("测试桩不支持取值");
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return 0L;
        }

        @Override
        public int compareTo(java.util.concurrent.Delayed other) {
            return 0;
        }
    }

    /** 脚本化周期策略：核心测骨架驱动，网格公式正确性归域侧策略测试。 */
    static final class ScriptedSchedule implements RoundSchedule {

        long firstDelay = 0L;
        Long dropDelay;
        long settleDelay = 0L;
        int settleCount;
        String traceIdSeenAtSettle;

        @Override
        public long firstDelayMillis() {
            return firstDelay;
        }

        @Override
        public FireDecision onFire() {
            return dropDelay != null ? FireDecision.drop(dropDelay) : FireDecision.run();
        }

        @Override
        public long onSettleRearmMillis() {
            settleCount++;
            traceIdSeenAtSettle = TraceContext.getTraceId();
            return settleDelay;
        }
    }

    private static Supplier<CompletableFuture<Boolean>> completedRound(boolean value) {
        return () -> CompletableFuture.completedFuture(value);
    }
}
