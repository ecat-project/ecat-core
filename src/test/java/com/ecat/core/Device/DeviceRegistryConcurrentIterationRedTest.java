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

package com.ecat.core.Device;

import com.ecat.core.Bus.event.DeviceLifecycleEvent;
import com.ecat.core.ConfigEntry.ConfigEntry;

import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertTrue;

/**
 * 红测试：DeviceRegistry 无锁 HashMap 并发注册 vs 迭代（架构审查 01-F2 静态推演的运行时复现）。
 *
 * <p>被测缺陷（DeviceRegistry）：
 * <ul>
 *   <li>:45 {@code private final Map<String, DeviceBase> registry = new HashMap<>();} —— 裸 HashMap，零同步；</li>
 *   <li>生产写路径 {@code getOrCreate → commit}（:191/:201）与读路径
 *       {@code getAllDevices()}（:94，{@code new ArrayList<>(registry.values())} 直接迭代）、
 *       {@code getDeviceByUniqueId}/{@code getDevicesByCoordinate}（裸 values() 迭代）之间无任何互斥。</li>
 * </ul>
 *
 * <p>并发注册（HashMap 结构性修改 → modCount 递增）与迭代（HashIterator 校验 modCount）交错，
 * 迭代方抛 {@link ConcurrentModificationException}；极端情况下并发写自身还可丢条目（结构损坏）。
 *
 * <p>场景：3 轮；每轮 2 写者共注册 3000 台设备（生产路径 getOrCreate；persistence/busRegistry
 * 均未注入 → 纯内存 map 操作，无 IO 副作用），2 读者紧循环 getAllDevices。设备对象在并发开始前
 * 预建，热点区间只剩被测的 map 读写。join 后逐键回查，统计丢失条目。
 *
 * <p>正确不变量：并发注册+迭代期间 CME 恒为 0（线程安全注册表不得向调用方抛 CME），
 * 且全部注册成功的关键键可回查。预测红因：CME 总数 &gt; 0。
 *
 * @author coffee
 */
public class DeviceRegistryConcurrentIterationRedTest {

    private static final int ROUNDS = 3;
    private static final int TOTAL_DEVICES = 3000;
    private static final int WRITERS = 2;
    private static final int READERS = 2;
    private static final long JOIN_TIMEOUT_MS = 60_000L;

    private List<DeviceBase> devices;

    @Before
    public void setUp() {
        // 设备对象预建（DeviceBase 构造含 logger/i18n 开销，移出被测并发区间）；
        // 每台 coordinate+uniqueId 唯一 → getOrCreate 的 registry key（设备 UUID）互不相同。
        devices = new ArrayList<DeviceBase>(TOTAL_DEVICES);
        for (int i = 0; i < TOTAL_DEVICES; i++) {
            devices.add(newDevice("entry-" + i, "unique-" + i));
        }
    }

    @Test
    public void concurrentRegisterAndGetAllDevicesMustNotThrowNorLoseEntries() throws Exception {
        long totalCme = 0;
        long totalMissing = 0;
        long totalIterations = 0;

        for (int round = 1; round <= ROUNDS; round++) {
            RoundResult r = runRound(round);
            totalCme += r.cmeCount;
            totalMissing += r.missingCount;
            totalIterations += r.iterations;
        }

        // 正确不变量：零 CME + 零丢失。预测红因：registry 是无锁 HashMap，
        // 注册(结构性修改)与 getAllDevices 迭代交错必触 ConcurrentModificationException。
        assertTrue(
            "DeviceRegistry 并发注册 vs 迭代观察到坏果（" + ROUNDS + " 轮汇总）：CME 次数=" + totalCme
                + "，注册后回查丢失条目=" + totalMissing
                + "（读者总迭代=" + totalIterations + "）。"
                + "缺陷：registry(:45) 为裸 HashMap，register/commit 写与 getAllDevices 迭代无互斥",
            totalCme == 0 && totalMissing == 0);
    }

    /** 单轮：全新 DeviceRegistry，栅栏同启 2 写者（getOrCreate 生产路径）+ 2 读者（getAllDevices 紧循环）。 */
    private RoundResult runRound(int round) throws Exception {
        final DeviceRegistry registry = new DeviceRegistry();
        final AtomicBoolean stop = new AtomicBoolean(false);
        final CyclicBarrier barrier = new CyclicBarrier(WRITERS + READERS);
        final List<Thread> threads = new ArrayList<Thread>();

        // 写者：对分片设备逐台走生产注册路径 getOrCreate(CREATE)。
        // persistence 未注入 → commit 跳过落盘；busRegistry 未注入 → 跳过发事件；
        // 剩余动作恰是被测缺陷本体：registry.put + matchIndex.put（两个裸 HashMap）。
        final int slice = TOTAL_DEVICES / WRITERS;
        for (int w = 0; w < WRITERS; w++) {
            final int from = w * slice;
            final int to = (w == WRITERS - 1) ? TOTAL_DEVICES : (w + 1) * slice;
            Thread t = new Thread(() -> {
                try {
                    barrier.await();
                } catch (Exception e) {
                    return;
                }
                for (int i = from; i < to; i++) {
                    registry.getOrCreate(devices.get(i), DeviceLifecycleEvent.Action.CREATE);
                }
            }, "registry-writer-" + round + "-" + w);
            threads.add(t);
        }

        // 读者：紧循环 getAllDevices()（:94 直接迭代裸 HashMap 的 values 视图）。
        final AtomicLong cmeCount = new AtomicLong(0);
        final AtomicLong iterations = new AtomicLong(0);
        for (int r = 0; r < READERS; r++) {
            Thread t = new Thread(() -> {
                try {
                    barrier.await();
                } catch (Exception e) {
                    return;
                }
                while (!stop.get()) {
                    try {
                        registry.getAllDevices();
                        iterations.incrementAndGet();
                    } catch (ConcurrentModificationException e) {
                        cmeCount.incrementAndGet();
                    }
                }
            }, "registry-reader-" + round + "-" + r);
            threads.add(t);
        }

        for (Thread t : threads) {
            t.start();
        }
        // 先 join 写者（迭代有界，自然结束），再放行读者 stop 标志，最后 join 读者——顺序颠倒会互相等待。
        for (int i = 0; i < WRITERS; i++) {
            Thread writer = threads.get(i);
            writer.join(JOIN_TIMEOUT_MS);
            assertTrue("写者 " + writer.getName() + " 未在有界窗口内结束（测试自身缺陷）", !writer.isAlive());
        }
        stop.set(true);
        for (int i = WRITERS; i < threads.size(); i++) {
            Thread reader = threads.get(i);
            reader.join(JOIN_TIMEOUT_MS);
            assertTrue("读者 " + reader.getName() + " 未在有界窗口内结束（测试自身缺陷）", !reader.isAlive());
        }

        // join 后静置回查：全部注册过的设备必须可按 id 查得（并发写丢失条目 = 结构损坏证据）。
        long missing = 0;
        for (DeviceBase device : devices) {
            if (registry.getDeviceByID(device.getId()) == null) {
                missing++;
            }
        }

        RoundResult result = new RoundResult();
        result.cmeCount = cmeCount.get();
        result.missingCount = missing;
        result.iterations = iterations.get();
        return result;
    }

    /** 与 DeviceRegistryTest 相同的轻量设备样板：entry-backed + 匿名子类置空生命周期。 */
    private DeviceBase newDevice(String entryId, String uniqueId) {
        ConfigEntry entry = new ConfigEntry();
        entry.setEntryId(entryId);
        entry.setUniqueId(uniqueId);
        entry.setCoordinate("com.ecat:c");
        Map<String, Object> data = new HashMap<String, Object>();
        data.put("name", "n-" + entryId);
        entry.setData(data);
        return new DeviceBase(entry) {
            @Override public void init() {}
            @Override public void start() {}
            @Override public void stop() {}
            @Override public void release() {}
        };
    }

    /** 单轮统计。 */
    private static final class RoundResult {
        long cmeCount;
        long missingCount;
        long iterations;
    }
}
