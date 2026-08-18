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
package com.ecat.core.State;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.Test;

import com.ecat.core.Bus.BusRegistry;
import com.ecat.core.Bus.BusTopic;
import com.ecat.core.Bus.event.DeviceDataChangedEvent;
import com.ecat.core.Device.DeviceBase;
import com.ecat.core.EcatCore;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.fail;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 红测试：复现 {@link AttributeBase#publicState()} 提交序列与并发 {@code updateValue} 的丢更新竞态。
 *
 * <p>被测缺陷（架构审查报告 01-F3 静态推演）：publicState 的提交序列不在 synchronized(this) 内——
 * 它先无锁读取在途 midState 作 newState，随后「发布总线事件 → 移位（lastState=newState）→ midState=null
 * → isValueUpdated=false」。若一个并发 updateValue（自身全程 synchronized(this)，与 publicState 无互斥）
 * 恰好在该窗口内完成，它重建的 midState 会被 midState=null 清掉、它置起的 isValueUpdated 会被复位，
 * 该次设备更新静默丢失：value 字段持有新值，但已提交 lastState、后续事件、最终状态读取全都是旧值。
 * DeviceDataChangedEvent 类注释宣称「两个 AttrState 都是发布时刻在 synchronized 块内原子捕获」，
 * 与实现不符——本测试用运行时证据验证。
 *
 * <p>编排（窗口无显式钩子，采用统计式压测；同步全部走 volatile 标志 + join，无 sleep）：
 * 更新线程循环 updateValue（每值全局唯一 float）；发布线程循环 publicState；总线用真实 BusRegistry
 * （publish 必须真正成功才会走到移位/清空——若 getCore 未接线，publicState 在发布处 NPE 被
 * 内部 catch 吞掉直接 return false，根本不会发生移位，竞态无从暴露）；订阅者记录每个事件的
 * newState.value。线程静默后由主线程做最终 flush（join 建立 happens-before，读到最新字段）。
 *
 * <p>丢失判定（红断言）：updateValue 写入的值既未出现在任何总线发布事件、也未出现在最终状态读取
 * → 记一次丢失。注意其中含「同周期 last-write-wins 合并」（下一值先于 publish 覆盖在途值，属设计内），
 * 故另设两个竞态特异指标作铁证：endDiverged（静默后最终可见值 != 末次写入值——此后无任何写可覆盖它，
 * 只能是被窗口清掉）与 lastWriteNeverPublished（末次写入连一次发布都没上过）。
 *
 * <p>断言的是正确行为（终态撕裂与末写从未发布两个竞态特异指标恒 0），故在缺陷代码上失败 = RED = 复现成功。
 * totalLost 含设计内同周期合并（见上），只输出证据不断言——规格修正依据见方法末断言处注释。
 */
public class PublicStateUpdateRaceRedTest {

    /** 独立压测轮数：每轮全新 attr/mock/总线，轮间无状态残留；多轮提高撞窗概率并给出稳定丢失率。 */
    private static final int ROUNDS = 20;

    /** 每轮 updateValue 次数（每次写入一个唯一值）。 */
    private static final int UPDATES_PER_ROUND = 30_000;

    /** 值块大小：所有值控制在 2^24 内（float 整数精确表示域），保证不同 i 必得不同 float。 */
    private static final float BLOCK = 100_000f;

    /** join 看门狗：编排缺陷导致线程不退时中断并失败，而不是无限挂起。 */
    private static final long JOIN_TIMEOUT_MS = 60_000L;

    @Test
    public void updateValueDuringPublicStateCommitWindow_mustNotBeSilentlyLost() {
        int totalWritten = 0;
        int totalLost = 0;
        int roundsWithEndDivergence = 0;
        int roundsWithLastWriteNeverPublished = 0;
        RoundResult last = null;

        for (int round = 0; round < ROUNDS; round++) {
            RoundResult res = runRound(round);
            totalWritten += res.written;
            totalLost += res.lostCount;
            if (res.endDiverged) {
                roundsWithEndDivergence++;
            }
            if (res.lastWriteNeverPublished) {
                roundsWithLastWriteNeverPublished++;
            }
            System.out.println("[race-round " + round + "] lost=" + res.lostCount + "/" + res.written
                    + " endDiverged=" + res.endDiverged
                    + " lastWriteNeverPublished=" + res.lastWriteNeverPublished
                    + " lastWritten=" + res.lastWritten
                    + " finalVisible=" + res.finalVisible
                    + " lastPublished=" + res.lastPublished
                    + " lostSample=" + res.lostSample);
            last = res;
        }

        String evidence = String.format(
                "并发丢更新竞态：总写入 %d 次，%d 个值从未出现在任何总线发布或最终状态读取"
                        + "（丢失率 %.4f%%）；%d/%d 轮终态撕裂（finalVisible=%s != lastWritten=%s）；"
                        + "%d/%d 轮末次写入从未发布（lastPublished=%s）；末轮丢失样本 i=%s。",
                totalWritten, totalLost, 100.0 * totalLost / totalWritten,
                roundsWithEndDivergence, ROUNDS, last.finalVisible, last.lastWritten,
                roundsWithLastWriteNeverPublished, ROUNDS, last.lastPublished, last.lostSample);
        System.out.println("[race-summary] " + evidence);

        // 断言两个竞态特异铁证指标（缺陷的真规格，修复后必须恒 0）：
        // ① 终态撕裂：静默后无任何写可再覆盖末次写入，最终可见值 != 末次写入 ⇒ 只能是被提交窗口清掉（缺陷基线 14/20 轮）
        // ② 末写从未发布：末次写入连一次总线发布都没上过（缺陷基线 20/20 轮）
        // totalLost 只作证据输出不作断言：其中含设计内「同周期 last-write-wins 合并」——
        // state-lifecycle-design §3.1 的 midState 覆盖语义，目的正是防同周期撕裂中间态上总线。
        // 合并值并未丢失：最终可见值与后续事件都反映最后写入。经三种实现实证（2026-08-16）：
        // 发布全程持锁→发布线程被非公平 monitor 饿死（93.9%）；公平 ReentrantLock→156/60 万仍非零且热路径 3×慢；
        // 每写一事件→会发布撕裂中间态违反 §3.1 语义。故「合并率归零」不可作为合法规格。
        assertEquals("终态撕裂必须为 0：静默后的末次写入不可被提交窗口清掉（缺陷本体）。" + evidence,
                0, roundsWithEndDivergence);
        assertEquals("末写从未发布必须为 0：线程静默并 flush 后，末值至少上总线一次（缺陷本体）。" + evidence,
                0, roundsWithLastWriteNeverPublished);
    }

    /** 单轮压测：返回该轮的写入数、丢失数与终态证据。 */
    private RoundResult runRound(int round) {
        // 真实 BusRegistry：publish 成功（同步扇出到本订阅者）才会走到 publicState 的移位/清空段。
        BusRegistry bus = new BusRegistry();
        final Set<Object> observed = ConcurrentHashMap.newKeySet();
        final AtomicReference<Object> lastPublished = new AtomicReference<>();
        bus.subscribe(BusTopic.DEVICE_DATA_UPDATE.getTopicName(), ev -> {
            DeviceDataChangedEvent payload = (DeviceDataChangedEvent) ev.getPayload();
            Object value = payload.getNewState().getValue();
            observed.add(value);
            lastPublished.set(value);
        });

        DeviceBase device = mock(DeviceBase.class);
        when(device.getId()).thenReturn("race-red-device-r" + round);
        // 硬门禁要求 READY 才允许 publish（isReady javadoc 明示 mock 设备可直接 stub 本方法）
        when(device.isReady()).thenReturn(true);
        EcatCore core = mock(EcatCore.class);
        when(core.getBusRegistry()).thenReturn(bus);
        when(device.getCore()).thenReturn(core);

        FloatAttribute attr = new FloatAttribute(
                "race_attr", AttributeClass.NUMERIC, null, null, 0, false, false);
        attr.setDevice(device);

        final AtomicBoolean done = new AtomicBoolean(false);
        final AtomicInteger lastWrittenIdx = new AtomicInteger(-1);
        final AtomicReference<Throwable> updaterFailure = new AtomicReference<>();
        final AtomicReference<Throwable> publisherFailure = new AtomicReference<>();

        Thread updater = new Thread(() -> {
            try {
                for (int i = 0; i < UPDATES_PER_ROUND; i++) {
                    attr.updateValue(roundValue(round, i));
                    lastWrittenIdx.set(i);
                }
            } catch (Throwable t) {
                updaterFailure.set(t);
            } finally {
                done.set(true);
            }
        }, "race-updater-r" + round);

        Thread publisher = new Thread(() -> {
            try {
                while (!done.get()) {
                    attr.publicState();
                }
            } catch (Throwable t) {
                publisherFailure.set(t);
            }
        }, "race-publisher-r" + round);

        updater.start();
        publisher.start();
        joinOrFail(updater);
        joinOrFail(publisher);

        Throwable failure = updaterFailure.get();
        if (failure != null) {
            fail("更新线程异常，压测编排失效: " + failure);
        }
        failure = publisherFailure.get();
        if (failure != null) {
            fail("发布线程异常，压测编排失效: " + failure);
        }
        assertEquals("更新线程应完成全部写入", UPDATES_PER_ROUND - 1, lastWrittenIdx.get());

        // 静默后最终 flush：提交仍存活的在途 midState（join 的 happens-before 保证读到最新字段）
        attr.publicState();
        AttrState<Float> finalState = attr.getState();
        Object finalVisible = (finalState != null) ? finalState.getValue() : null;

        int lost = 0;
        List<Integer> lostSample = new ArrayList<>();
        for (int i = 0; i < UPDATES_PER_ROUND; i++) {
            Float v = roundValue(round, i);
            if (!observed.contains(v) && !Objects.equals(finalVisible, v)) {
                lost++;
                if (lostSample.size() < 10) {
                    lostSample.add(i);
                }
            }
        }

        RoundResult res = new RoundResult();
        res.written = UPDATES_PER_ROUND;
        res.lostCount = lost;
        res.lostSample = lostSample;
        res.lastWritten = roundValue(round, lastWrittenIdx.get());
        res.finalVisible = finalVisible;
        res.lastPublished = lastPublished.get();
        // 终态撕裂：静默后无任何写可再覆盖末次写入，最终可见值仍旧于它 ⇒ 只能是被提交窗口清掉
        res.endDiverged = !Objects.equals(finalVisible, res.lastWritten);
        // 末次写入从未发布：值可能经 getState 可见（被搁浅的 midState），但从未上过总线
        res.lastWriteNeverPublished = !observed.contains(res.lastWritten);
        return res;
    }

    /** 每轮独立的唯一值空间：值域 < 2^24 保证 float 精确、互不相等。 */
    private static Float roundValue(int round, int i) {
        return (round + 1) * BLOCK + i;
    }

    private static void joinOrFail(Thread t) {
        try {
            t.join(JOIN_TIMEOUT_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("join 被中断: " + t.getName(), e);
        }
        if (t.isAlive()) {
            t.interrupt();
            throw new IllegalStateException("压测线程超时未退出（疑似编排缺陷）: " + t.getName());
        }
    }

    /** 单轮结果快照，供汇总统计与失败信息展示。 */
    private static final class RoundResult {
        int written;
        int lostCount;
        List<Integer> lostSample;
        Float lastWritten;
        Object finalVisible;
        Object lastPublished;
        boolean endDiverged;
        boolean lastWriteNeverPublished;
    }
}
