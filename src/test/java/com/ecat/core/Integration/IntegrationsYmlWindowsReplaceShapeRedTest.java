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
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
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
 * Windows 替换形状下的静默撕裂复现（Linux 注入）。
 *
 * <p>背景（bug-record-20260831）：同事 Windows+mvn 跑 {@link IntegrationsYmlConcurrentWriteRedTest}
 * 失败——仅「读者静默撕裂（条目数&lt;60）」计数非零，其余坏果全零。Linux 原子 rename 下不可复现，
 * 根因是 Windows 特有的两个瞬态读失败路径，都被 loadIntegrationsConfig 当作「无配置」返回空 map：
 * <ol>
 *   <li><b>目标瞬时不存在的窗口</b>：JDK8 Windows 非原子 move 降级路径是显式
 *       DeleteFile(target)+MoveFileEx（WindowsFileCopy.move）；ATOMIC_MOVE 单次
 *       MoveFileEx(MOVEFILE_REPLACE_EXISTING) 的原子性无契约保证（MSFT 确认 not always atomic，
 *       杀毒/过滤驱动会放大窗口）。窗口内读者 exists()==false → 建空文件+返回空 map，
 *       读者建的空文件还会被另一读者解析（同一坏果放大）。</li>
 *   <li><b>open 瞬态失败</b>：java.io 在 Windows 打开文件不带 FILE_SHARE_DELETE
 *       （io_util_md.c winFileHandleOpen），任何 open 失败（含替换在飞的共享冲突）一律映射
 *       FileNotFoundException → catch IOException → 返回空 map。</li>
 * </ol>
 *
 * <p>复现手段：覆写 {@code moveAtomicallyWithRetry} 注入「删除目标 + 窗口停顿 + 重命名」的
 * Windows 替换形状（删除停顿=模拟非原子窗口/过滤驱动放大），读者侧生产代码零改动——
 * 与真实 Windows 的可观察行为（空 map 静默撕裂、无 YAML 异常、无丢更新、无持久损坏）逐项对齐。
 *
 * <p>红 = 静默撕裂 &gt; 0（证明该机制足以产生实测签名）；绿 = 全零（读者侧窗口治理生效：
 * 缺席/不可开先与写方在 configFileSync 汇合再复检，不把「替换中」误判为「无配置」）。
 *
 * @author coffee
 */
public class IntegrationsYmlWindowsReplaceShapeRedTest {

    private static final int SEED_ENTRIES = 60;
    private static final int WRITES_PER_WRITER = 40;
    private static final int ROUNDS = 2;
    private static final int WRITERS = 2;
    private static final int READERS = 2;
    /** 注入的「目标不存在」窗口毫秒数：放大窗口保证确定性复现（真实 Windows 为亚毫秒级）。 */
    private static final long ABSENT_WINDOW_MS = 3L;
    private static final long JOIN_TIMEOUT_MS = 60_000L;

    private IntegrationManager manager;
    private File testDir;
    private static String originalConfigPath;
    private static String originalItemPath;

    @Before
    public void setUp() throws Exception {
        testDir = new File("target", ".ecat-yml-win-shape-red");
        deleteRecursively(testDir);
        assertTrue("测试目录创建失败: " + testDir, testDir.mkdirs() || testDir.exists());

        manager = new WindowsReplaceShapeManager(
            mock(EcatCore.class), new IntegrationRegistry(), mock(StateManager.class));

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
    public void windowsShapedReplaceMustNotServeAbsentOrEmptyConfigToReaders() throws Exception {
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

        assertTrue(
            "Windows 替换形状（删除目标+重命名非原子窗口）下 integrations.yml 读者观察到静默撕裂"
                + "（" + ROUNDS + " 轮汇总）：丢更新缺失键=" + totalMissingKeys
                + "，读者撕裂读异常=" + totalReaderTornExceptions
                + "，读者静默撕裂(条目数<" + SEED_ENTRIES + ")=" + totalReaderSilentTorn
                + "，写者内部撕裂读=" + totalWriterTornReads
                + "，轮末文件持久损坏=" + totalFinalReadCorrupt
                + "（读者总迭代=" + totalReaderIterations + "）。"
                + "缺陷：目标瞬时缺席/不可开被 loadIntegrationsConfig 当作「无配置」返回空 map",
            totalMissingKeys == 0 && totalReaderTornExceptions == 0
                && totalReaderSilentTorn == 0 && totalWriterTornReads == 0
                && totalFinalReadCorrupt == 0);
    }

    /** 单轮：seed → 栅栏同启 2 写者 + 2 紧循环读者 → join → 统计。与主红测试同款场景。 */
    private RoundResult runRound(int round) throws Exception {
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
                        writerTornReads.incrementAndGet();
                    }
                }
            }, "win-shape-writer-" + round + "-" + w);
            threads.add(t);
        }

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
            }, "win-shape-reader-" + round + "-" + r);
            threads.add(t);
        }

        for (Thread t : threads) {
            t.start();
        }
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

    /**
     * Windows 替换形状注入：删除目标 → 停顿（放大瞬时缺席窗口）→ 重命名覆盖。
     *
     * <p>对应真实 Windows 行为：JDK8 非原子 move 降级路径显式 DeleteFile+MoveFileEx；
     * MoveFileEx(REPLACE) 原子性无契约（MSFT not always atomic）；停顿模拟过滤驱动/杀毒放大。
     * 停顿发生在写方 configFileSync 临界区内——与生产替换的位置一致，读者不受写锁保护
     * （锁自由读），恰是被测窗口。
     */
    private static final class WindowsReplaceShapeManager extends IntegrationManager {
        WindowsReplaceShapeManager(EcatCore core, IntegrationRegistry registry, StateManager stateManager) {
            super(core, registry, stateManager);
        }

        @Override
        void moveAtomicallyWithRetry(java.io.File tmpFile, java.io.File configFile) throws java.io.IOException {
            Files.deleteIfExists(configFile.toPath());
            try {
                Thread.sleep(ABSENT_WINDOW_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new java.io.IOException("Windows 形状替换被中断", e);
            }
            Files.move(tmpFile.toPath(), configFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
        }
    }

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

    private static final class RoundResult {
        long missingKeys;
        long finalReadCorrupt;
        long readerTornExceptions;
        long readerSilentTorn;
        long writerTornReads;
        long readerIterations;
    }
}
