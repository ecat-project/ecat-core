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

package com.ecat.core.Integration;

import com.ecat.core.EcatCore;
import com.ecat.core.State.StateManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * 红测试：integrations.yml 读-改-写竞态（架构审查 01-F5 静态推演的运行时复现）。
 *
 * <p>被测缺陷（IntegrationManager）：
 * <ul>
 *   <li>{@code loadIntegrationsConfig()}（:1712）每次全量读文件，无任何锁；</li>
 *   <li>{@code updateIntegrationsConfig()}（:1782）直接 {@code new FileOutputStream(configFile)}
 *       ——打开即截断为 0 再逐步写回，无 tmp+rename 原子替换；</li>
 *   <li>{@code saveIntegrationConfig()}（:1732）三段式「load 全量 → 内存改 → 覆盖写回」，段间无互斥。</li>
 * </ul>
 *
 * <p>并发下的可观察坏果，本测试断言其正确不变量（均为 0）：
 * <ol>
 *   <li><b>丢更新</b>：两个并发 save 各自基于旧快照覆盖写回，后写者抹掉先写者的字段
 *       ——轮末应有键缺失；</li>
 *   <li><b>撕裂读</b>：读者在写者「已截断、未写完」窗口打开文件——
 *       a) 读到半截 YAML 抛 SnakeYAML RuntimeException（loadIntegrationsConfig 只 catch IOException，
 *       运行时异常直接外溢）；b) 更隐蔽的静默撕裂：读到空/半截内容返回
 *       {@code new HashMap<>()}，integrations 节点条目数低于种子数；</li>
 *   <li><b>落盘持久损坏</b>：双写者并发截断同一文件时，先开者按旧偏移继续写在已被后开者截断的
 *       文件上，产生 NUL 稀疏洞——竞态平息后轮末终读仍抛异常，损坏留盘波及后续所有读者。</li>
 * </ol>
 *
 * <p>稳定性设计：3 轮，每轮 2 写者 × 40 次全量读改写 + 2 读者紧循环；种子 60 条使单次 dump
 * 达毫秒级，竞态窗口充足。有界时间窗口：写者迭代固定、读者由 stop 标志收口、join 带超时兜底。
 *
 * <p>红 = 断言失败且失败计数正是上述缺陷之一；绿 = 全部计数为 0（缺陷不存在）。
 *
 * @author coffee
 */
public class IntegrationsYmlConcurrentWriteRedTest {

    /** 种子条目数：撑大单次 dump 的耗时，放大截断窗口。 */
    private static final int SEED_ENTRIES = 60;
    /** 每写者每轮的全量读改写次数。 */
    private static final int WRITES_PER_WRITER = 40;
    /** 稳定性轮数。 */
    private static final int ROUNDS = 3;
    private static final int WRITERS = 2;
    private static final int READERS = 2;
    /** join 兜底超时（毫秒）：写者迭代有界、读者受 stop 收口，正常远小于此值。 */
    private static final long JOIN_TIMEOUT_MS = 60_000L;

    private IntegrationManager manager;
    private File testDir;
    /** 静态路径字段先值，@After 恢复，避免泄漏到同 JVM 的后续测试。 */
    private static String originalConfigPath;
    private static String originalItemPath;

    @Before
    public void setUp() throws Exception {
        testDir = new File("target", ".ecat-yml-race-red");
        deleteRecursively(testDir);
        assertTrue("测试目录创建失败: " + testDir, testDir.mkdirs() || testDir.exists());

        manager = new IntegrationManager(mock(EcatCore.class), new IntegrationRegistry(), mock(StateManager.class));

        // INTEGRATIONS_CONFIG_PATH 是 private static（ IntegrationManager :80），测试注入临时路径，
        // 与 IntegrationManagerTest 的反射注入先例一致；记录先值，@After 恢复。
        originalConfigPath = (String) getStaticField("INTEGRATIONS_CONFIG_PATH");
        originalItemPath = (String) getStaticField("INTEGRATION_ITEM_PATH");
        setStaticField("INTEGRATIONS_CONFIG_PATH", testDir.getAbsolutePath() + "/core/integrations.yml");
        setStaticField("INTEGRATION_ITEM_PATH", testDir.getAbsolutePath() + "/integrations/%s.yml");
    }

    @After
    public void tearDown() throws Exception {
        if (originalConfigPath != null) {
            setStaticField("INTEGRATIONS_CONFIG_PATH", originalConfigPath);
        }
        if (originalItemPath != null) {
            setStaticField("INTEGRATION_ITEM_PATH", originalItemPath);
        }
        deleteRecursively(testDir);
    }

    @Test
    public void concurrentSaveAndLoadMustNotLoseUpdatesNorServeTornReads() throws Exception {
        long totalMissingKeys = 0;
        long totalReaderTornExceptions = 0;
        long totalReaderSilentTorn = 0;
        long totalWriterTornReads = 0;
        long totalFinalReadCorrupt = 0;
        long totalReaderIterations = 0;

        for (int round = 1; round <= ROUNDS; round++) {
            RoundResult r = runRound(round);
            totalMissingKeys += r.missingKeys;
            totalReaderTornExceptions += r.readerTornExceptions;
            totalReaderSilentTorn += r.readerSilentTorn;
            totalWriterTornReads += r.writerTornReads;
            totalFinalReadCorrupt += r.finalReadCorrupt;
            totalReaderIterations += r.readerIterations;
        }

        // 正确不变量：写入持久（无丢更新）+ 零撕裂读（异常与静默均无）+ 轮末文件可读（无持久损坏）。
        // 预测红因：updateIntegrationsConfig 截断-写非原子、saveIntegrationConfig 读改写无锁。
        assertTrue(
            "integrations.yml 并发读写观察到竞态坏果（" + ROUNDS + " 轮汇总）：丢更新缺失键=" + totalMissingKeys
                + "，读者撕裂读异常=" + totalReaderTornExceptions
                + "，读者静默撕裂(条目数<" + SEED_ENTRIES + ")=" + totalReaderSilentTorn
                + "，写者内部撕裂读=" + totalWriterTornReads
                + "，轮末文件持久损坏(终读抛异常)=" + totalFinalReadCorrupt
                + "（读者总迭代=" + totalReaderIterations + "）。"
                + "缺陷：loadIntegrationsConfig/updateIntegrationsConfig/saveIntegrationConfig 无锁无原子替换",
            totalMissingKeys == 0 && totalReaderTornExceptions == 0
                && totalReaderSilentTorn == 0 && totalWriterTornReads == 0
                && totalFinalReadCorrupt == 0);
    }

    /** 单轮竞态场景：seed 全量写 → 栅栏同启 2 写者 + 2 读者 → join → 统计坏果。 */
    private RoundResult runRound(int round) throws Exception {
        // 种子：SEED_ENTRIES 条集成配置一次写入（此时单线程，无竞态）。
        Map<String, Object> seedIntegrations = new LinkedHashMap<>();
        for (int s = 0; s < SEED_ENTRIES; s++) {
            seedIntegrations.put("seed:r" + round + ":g" + s, integrationConfig("seed", s));
        }
        Map<String, Map<String, Object>> seed = new LinkedHashMap<>();
        seed.put("integrations", seedIntegrations);
        manager.updateIntegrationsConfig(seed);

        final AtomicBoolean stop = new AtomicBoolean(false);
        final CyclicBarrier barrier = new CyclicBarrier(WRITERS + READERS);
        final List<Thread> threads = new ArrayList<>();

        // 写者：每轮各做 WRITES_PER_WRITER 次 saveIntegrationConfig（全量读-改-写）。
        // 正常返回的键轮末必须仍在文件中（正确语义）；内部 load 读到截断文件会抛 RuntimeException，计数不中断。
        final List<Set<String>> savedOkPerWriter = Collections.synchronizedList(new ArrayList<Set<String>>());
        final AtomicLong writerTornReads = new AtomicLong(0);
        for (int w = 0; w < WRITERS; w++) {
            final int writerId = w;
            final Set<String> savedOk = Collections.synchronizedSet(new TreeSet<String>());
            savedOkPerWriter.add(savedOk);
            Thread t = new Thread(() -> {
                try {
                    barrier.await();
                } catch (Exception e) {
                    return;
                }
                for (int k = 0; k < WRITES_PER_WRITER; k++) {
                    String coordinate = "race:r" + round + ":w" + writerId + ":k" + k;
                    try {
                        manager.saveIntegrationConfig(coordinate, integrationConfig("race", k));
                        savedOk.add(coordinate);
                    } catch (RuntimeException e) {
                        // saveIntegrationConfig 内部 loadIntegrationsConfig 读到截断 YAML —— 撕裂读外溢，缺陷证据。
                        writerTornReads.incrementAndGet();
                    }
                }
            }, "yml-writer-" + round + "-" + w);
            threads.add(t);
        }

        // 读者：紧循环 loadIntegrationsConfig。
        // a) RuntimeException = 半截 YAML 解析失败（loadIntegrationsConfig 只 catch IOException）；
        // b) integrations 节点条目数 < SEED_ENTRIES = 静默撕裂（读到已截断未写完的内容被当作空/半截配置返回）。
        final AtomicLong readerTornExceptions = new AtomicLong(0);
        final AtomicLong readerSilentTorn = new AtomicLong(0);
        final AtomicLong readerIterations = new AtomicLong(0);
        for (int r = 0; r < READERS; r++) {
            Thread t = new Thread(() -> {
                try {
                    barrier.await();
                } catch (Exception e) {
                    return;
                }
                while (!stop.get()) {
                    try {
                        Map<String, Map<String, Object>> config = manager.loadIntegrationsConfig();
                        readerIterations.updateAndGet(v -> v + 1);
                        Object node = config.get("integrations");
                        if (!(node instanceof Map) || ((Map<?, ?>) node).size() < SEED_ENTRIES) {
                            readerSilentTorn.incrementAndGet();
                        }
                    } catch (RuntimeException e) {
                        readerTornExceptions.incrementAndGet();
                    }
                }
            }, "yml-reader-" + round + "-" + r);
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

        // 轮末静置状态：文件必须可读且包含种子 + 两写者全部正常返回的保存键。
        // 双写者并发 new FileOutputStream 互相截断时，先开者按自身偏移继续写在已截断文件上，
        // 产生 NUL 稀疏洞——轮末终读本身抛 SnakeYAML RuntimeException，即「落盘持久损坏」，
        // 比瞬态撕裂读更严重（损坏留盘，波及后续所有读者）。
        Set<String> expectedKeys = new TreeSet<>(seedIntegrations.keySet());
        for (Set<String> savedOk : savedOkPerWriter) {
            expectedKeys.addAll(savedOk);
        }
        Set<String> finalKeys = new TreeSet<>();
        boolean finalReadCorrupt = false;
        try {
            Map<String, Map<String, Object>> finalConfig = manager.loadIntegrationsConfig();
            Object finalNode = finalConfig.get("integrations");
            if (finalNode instanceof Map) {
                for (Object k : ((Map<?, ?>) finalNode).keySet()) {
                    finalKeys.add(String.valueOf(k));
                }
            }
        } catch (RuntimeException e) {
            finalReadCorrupt = true;
        }
        Set<String> missing = new TreeSet<>(expectedKeys);
        missing.removeAll(finalKeys);

        RoundResult result = new RoundResult();
        result.missingKeys = missing.size();
        result.finalReadCorrupt = finalReadCorrupt ? 1 : 0;
        result.readerTornExceptions = readerTornExceptions.get();
        result.readerSilentTorn = readerSilentTorn.get();
        result.writerTornReads = writerTornReads.get();
        result.readerIterations = readerIterations.get();
        return result;
    }

    /** 与生产写入字段一致的最小集成配置（saveIntegrationConfig 仅持久化这些字段）。 */
    private Map<String, Object> integrationConfig(String group, int seq) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("groupId", "com.ecat");
        config.put("artifactId", group + "-" + seq);
        config.put("version", "1.0." + seq);
        config.put("enabled", true);
        config.put("state", "RUNNING");
        return config;
    }

    private static Object getStaticField(String name) throws Exception {
        Field field = IntegrationManager.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    private static void setStaticField(String name, Object value) throws Exception {
        Field field = IntegrationManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        if (!file.delete()) {
            file.deleteOnExit();
        }
    }

    /** 单轮统计。 */
    private static final class RoundResult {
        long missingKeys;
        long finalReadCorrupt;
        long readerTornExceptions;
        long readerSilentTorn;
        long writerTornReads;
        long readerIterations;
    }
}
