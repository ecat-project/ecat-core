package com.ecat.core.Device;

import static org.junit.Assert.assertTrue;

import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.Test;

import com.ecat.core.ConfigEntry.ConfigEntry;

/**
 * RemovalHost 并发契约测试（bug-record-20261009-232607 根因收口）：
 * 注册线程 {@code DeviceBase.onRemove} 与生命周期线程 {@code DeviceBase.cancelManagedTasks}
 * 并发首达时，每个注册动作只有两种合法结局——
 * <ul>
 *   <li>注册成功且必然被执行（sweep 排空包含它，撤销/拆卸真正生效）；</li>
 *   <li>注册被 {@code RejectedExecutionException} 显式拒绝（严格模式防泄漏）。</li>
 * </ul>
 * 禁止第三种结局「注册成功却永不执行」：onRemove 的 check+add 与 sweep 的置位+排空
 * 必须互斥——否则撤销动作静默丢失，设备停机后轮询/延迟照常发射
 * （tianhong 校准托管停机全量 flake 的实测形态，修复前 5 万轮对撞 leaked=9）。
 */
public class DeviceRemovalHostConcurrentSweepTest {

    private static final int ROUNDS = 50_000;

    private static DeviceBase newDevice(int round) {
        ConfigEntry entry = new ConfigEntry();
        entry.setUniqueId("removal-host-concurrent-" + round);
        Map<String, Object> data = new java.util.HashMap<>();
        data.put("name", "removal-host-concurrent-diagnostic");
        entry.setData(data);
        return new DeviceBase(entry) {
            @Override public void init() {}
            @Override public void start() {}
            @Override public void stop() {}
            @Override public void release() {}
        };
    }

    @Test
    public void concurrentRegistrationAndSweep_noSilentLeak() throws Exception {
        AtomicInteger registeredOk = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();
        AtomicInteger executed = new AtomicInteger();
        AtomicInteger leaked = new AtomicInteger();
        int firstLeakRound = -1;

        DeviceBase[] devices = new DeviceBase[ROUNDS];
        for (int i = 0; i < ROUNDS; i++) {
            devices[i] = newDevice(i);
        }

        CyclicBarrier start = new CyclicBarrier(3);
        CyclicBarrier done = new CyclicBarrier(3);

        Thread registrar = new Thread(() -> runRegistrar(devices, start, done,
                registeredOk, rejected), "diag-registrar");
        Thread sweeper = new Thread(() -> runSweeper(devices, start, done), "diag-sweeper");
        registrar.start();
        sweeper.start();

        for (int round = 0; round < ROUNDS; round++) {
            await(start);
            await(done);
            // 本轮两侧都已返回：注册动作若「成功注册却未执行」即泄漏（无人再会执行它——sweep 已完）
            if (registrarOutcome[round] == OUTCOME_OK && !actionRan[round].get()) {
                if (leaked.incrementAndGet() == 1) {
                    firstLeakRound = round;
                }
            } else if (registrarOutcome[round] == OUTCOME_OK) {
                executed.incrementAndGet();
            }
        }
        registrar.join(10_000);
        sweeper.join(10_000);

        System.out.println("[removal-host-concurrent] rounds=" + ROUNDS + " registeredOk=" + registeredOk
                + " rejected=" + rejected + " executed=" + executed + " leaked=" + leaked
                + " firstLeakRound=" + firstLeakRound);
        assertTrue("并发注册 vs sweep 出现「注册成功却永不执行」的静默泄漏：leaked=" + leaked
                        + "（firstLeakRound=" + firstLeakRound + "）——onRemove check-then-act 与 sweep 竞态命中",
                leaked.get() == 0);
    }

    private static final int OUTCOME_PENDING = 0;
    private static final int OUTCOME_OK = 1;
    private static final int OUTCOME_REJECTED = 2;
    private final int[] registrarOutcome = new int[ROUNDS];
    private final AtomicBoolean[] actionRan = new AtomicBoolean[ROUNDS];

    {
        for (int i = 0; i < ROUNDS; i++) {
            actionRan[i] = new AtomicBoolean(false);
        }
    }

    private void runRegistrar(DeviceBase[] devices, CyclicBarrier start, CyclicBarrier done,
            AtomicInteger ok, AtomicInteger rejected) {
        for (int round = 0; round < ROUNDS; round++) {
            await(start);
            try {
                AtomicBoolean ran = actionRan[round];
                devices[round].onRemove(() -> ran.set(true));
                registrarOutcome[round] = OUTCOME_OK;
                ok.incrementAndGet();
            } catch (RuntimeException e) {
                registrarOutcome[round] = OUTCOME_REJECTED;
                rejected.incrementAndGet();
            }
            await(done);
        }
    }

    private void runSweeper(DeviceBase[] devices, CyclicBarrier start, CyclicBarrier done) {
        for (int round = 0; round < ROUNDS; round++) {
            await(start);
            devices[round].cancelManagedTasks();
            await(done);
        }
    }

    private static void await(CyclicBarrier barrier) {
        try {
            barrier.await(30, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("并发对撞 barrier 超时/中断", e);
        }
    }
}
