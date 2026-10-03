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

package com.ecat.core.Utils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * YamlAtomicFileWriter 共用件直测。
 *
 * <p>锁死核心契约:move 失败时目标保持旧完整内容(负向:若有人改回裸截断写,目标被截断即红,
 * 有牙);并发写同文件不产生撕裂态;失败路径孤儿 tmp 被 finally 清理;回读验证对损坏内容拒绝。
 */
public class YamlAtomicFileWriterTest {

    private File dir;
    private File target;

    @Before
    public void setUp() throws IOException {
        dir = Files.createTempDirectory("yaml-atomic-writer-test").toFile();
        target = new File(dir, "target.yml");
    }

    @After
    public void tearDown() {
        deleteRecursively(dir);
    }

    private static void deleteRecursively(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                deleteRecursively(file);
            }
        }
        directory.delete();
    }

    private static byte[] readAllBytes(File file) throws IOException {
        return Files.readAllBytes(file.toPath());
    }

    private static void writeBytes(File file, byte[] bytes) throws IOException {
        Files.write(file.toPath(), bytes);
    }

    private File[] tmpFilesInDir() {
        return dir.listFiles((d, name) -> name.endsWith(".tmp"));
    }

    /** move 注入失败的形状:直接抛 IOException(同步注入,无定时)。 */
    private static final class FailingMoveWriter extends YamlAtomicFileWriter {
        @Override
        protected void moveAtomicallyWithRetry(File tmpFile, File targetFile) throws IOException {
            throw new IOException("注入的 move 失败");
        }
    }

    // ==================== ① move 失败时目标保持旧完整内容 ====================

    /**
     * 负向契约:写失败(注入 move 失败)后目标文件字节==预写的旧内容。
     * 若实现退回「new FileOutputStream(file) 打开即截断」的裸写,目标在 dump 前已被截断,
     * 本断言即红——截断损坏在共用件下不可能,有牙。
     */
    @Test
    public void interruptedMovePreservesOldContent() throws IOException {
        byte[] oldContent = "entryId: \"old\"\ntitle: \"old-content\"\n".getBytes("UTF-8");
        writeBytes(target, oldContent);

        Map<String, Object> newDoc = new LinkedHashMap<>();
        newDoc.put("entryId", "new");

        try {
            new FailingMoveWriter().write(newDoc, target);
            fail("move 注入失败应抛 IOException");
        } catch (IOException e) {
            assertTrue("异常应为注入的 move 失败: " + e.getMessage(),
                e.getMessage().contains("注入的 move 失败"));
        }

        assertArrayEquals("写失败后目标必须保持旧完整内容(不得截断)",
            oldContent, readAllBytes(target));
    }

    // ==================== ② 并发写同文件无撕裂态 ====================

    /**
     * CyclicBarrier 同启 2 线程 × N 次 write 同目标(各写结构可区分的完整 doc)→ join 后:
     * 文件可解析且==其中一线程的完整 doc;原始字节无 NUL(稀疏洞检测——并发裸写互相
     * 截断的产物即 NUL 洞,IntegrationsYmlConcurrentWriteRedTest 同款形态模式复用)。
     * 无 sleep:barrier 对齐启动,join 等终态。
     */
    @Test
    public void concurrentWritersSameFileNoTornState() throws Exception {
        final int rounds = 40;
        final int writers = 2;

        Map<String, Object> docA = new LinkedHashMap<>();
        docA.put("writer", "A");
        Map<String, Object> docAData = new LinkedHashMap<>();
        docAData.put("k1", "a-value-1");
        docAData.put("k2", 111);
        docA.put("data", docAData);

        Map<String, Object> docB = new LinkedHashMap<>();
        docB.put("writer", "B");
        Map<String, Object> docBData = new LinkedHashMap<>();
        docBData.put("k1", "b-value-1");
        docBData.put("k2", 222);
        docB.put("data", docBData);

        CyclicBarrier barrier = new CyclicBarrier(writers);
        CountDownLatch done = new CountDownLatch(writers);
        final IOException[] failure = new IOException[1];

        YamlAtomicFileWriter writer = new YamlAtomicFileWriter();
        Runnable work = () -> {
            try {
                barrier.await();
                for (int i = 0; i < rounds; i++) {
                    writer.write(i % 2 == 0 ? docA : docB, target);
                }
            } catch (IOException e) {
                failure[0] = e;
            } catch (Exception e) {
                failure[0] = new IOException(e);
            } finally {
                done.countDown();
            }
        };
        new Thread(work, "writer-A").start();
        new Thread(work, "writer-B").start();
        done.await();

        if (failure[0] != null) {
            fail("并发写不应失败: " + failure[0]);
        }

        byte[] raw = readAllBytes(target);
        for (byte b : raw) {
            assertFalse("文件含 NUL 字节=稀疏洞撕裂(并发截断写特征)", b == 0);
        }

        // 文件必须可解析且==其中一线程的完整 doc(终态=其一完整文件,不得是两 doc 的混合)
        Map<String, Object> parsed;
        try (FileInputStream in = new FileInputStream(target)) {
            parsed = new Yaml().load(in);
        }
        boolean matchesA = "A".equals(parsed.get("writer"))
            && docAData.equals(parsed.get("data"));
        boolean matchesB = "B".equals(parsed.get("writer"))
            && docBData.equals(parsed.get("data"));
        assertTrue("终态文件必须是某一线程的完整 doc,实际: " + parsed, matchesA || matchesB);
    }

    // ==================== ④ 失败路径孤儿 tmp 清理 ====================

    /** move 注入失败 → finally 清理:同目录不得残留 .tmp 文件。 */
    @Test
    public void orphanTmpCleanedOnMoveFailure() throws IOException {
        writeBytes(target, "entryId: \"old\"\n".getBytes("UTF-8"));

        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("entryId", "new");

        try {
            new FailingMoveWriter().write(doc, target);
            fail("move 注入失败应抛 IOException");
        } catch (IOException expected) {
            // 断言点在 tmp 清理
        }

        assertEquals("失败路径不得残留孤儿 tmp 文件", 0, tmpFilesInDir().length);
    }

    // ==================== ⑤ 回读验证负向直测(同包直调包级函数) ====================

    /** 损坏 YAML(解析不了)→ requireParseableMap 抛 IOException(负向有牙)。 */
    @Test
    public void requireParseableMapRejectsCorrupt() throws IOException {
        File tmp = new File(dir, "corrupt.tmp");
        writeBytes(tmp, "a: [unclosed".getBytes("UTF-8"));

        try {
            YamlAtomicFileWriter.requireParseableMap(tmp);
            fail("损坏 YAML 应回读验证拒绝");
        } catch (IOException e) {
            assertTrue("消息应标明回读验证失败: " + e.getMessage(),
                e.getMessage().contains("回读验证失败"));
        }
    }

    /** 合法 map 内容 → 放行(不抛)。 */
    @Test
    public void requireParseableMapAcceptsValidMap() throws IOException {
        File tmp = new File(dir, "valid.tmp");
        writeBytes(tmp, "entryId: \"x\"\nenabled: true\n".getBytes("UTF-8"));

        YamlAtomicFileWriter.requireParseableMap(tmp); // 不抛即放行
    }

    /** 解析为非 Map(标量/序列)同样拒绝:YAML 文档树契约=根级 Map。 */
    @Test
    public void requireParseableMapRejectsNonMap() throws IOException {
        File tmp = new File(dir, "scalar.tmp");
        writeBytes(tmp, "- a\n- b\n".getBytes("UTF-8"));

        try {
            YamlAtomicFileWriter.requireParseableMap(tmp);
            fail("非 Map 解析结果应拒绝");
        } catch (IOException e) {
            assertTrue("消息应标明非 Map: " + e.getMessage(),
                e.getMessage().contains("非 Map"));
        }
    }

    // ==================== 成功路径冒烟 ====================

    /** write 成功=目标已替换为新内容且可 round-trip(父目录不存在时自动创建)。 */
    @Test
    public void writeReplacesTargetAtomically() throws IOException {
        File nested = new File(dir, "a/b/target.yml");
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("entryId", "new");
        doc.put("version", "4.0");

        new YamlAtomicFileWriter().write(doc, nested);

        try (FileInputStream in = new FileInputStream(nested)) {
            Map<String, Object> parsed = new Yaml().load(in);
            assertEquals("new", parsed.get("entryId"));
            assertEquals("4.0", parsed.get("version"));
        }
        // tmp 与目标同目录(a/b/),残留检查落在真实目录
        File[] leftover = nested.getParentFile().listFiles((d, name) -> name.endsWith(".tmp"));
        assertEquals("成功路径不得残留 tmp", 0, leftover.length);
    }
}
