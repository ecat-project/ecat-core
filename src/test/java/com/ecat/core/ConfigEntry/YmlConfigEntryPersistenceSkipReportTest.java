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

package com.ecat.core.ConfigEntry;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * entry yml 静默丢弃改显式（C3）：坏文件在启动日志里必须操作员可见。
 *
 * <p>被测缺陷：损坏 entry 文件（如 yml 里带全局标签 {@code !!com.ecat...} 的历史残留）被
 * loadAll 跳过时，此前只有每文件的英文 WARN（带全栈、落 app 通道）——无汇总、无缺额计数，
 * 空文件更是完全静默；操作员对「为什么少了 N 个设备」无从下手。
 *
 * <p>被测契约（loadAll）：
 * <ul>
 *   <li>坏文件/空文件不出现在返回的 entries 里（既有行为不回退）；</li>
 *   <li>每文件一行 WARN 带原因（异常摘要/空文件），不再带全栈；</li>
 *   <li>启动汇总一行 ERROR：点名被跳过数量 + 坐标列表（从存储布局推导 groupId:artifactId(entryId)）。</li>
 * </ul>
 *
 * <p>红预测：无汇总行时 ERROR 断言失败；空文件无日志时对应 WARN 断言失败。
 *
 * @author coffee
 */
public class YmlConfigEntryPersistenceSkipReportTest {

    /** 夹具目录（BASE_DIR 布局：{groupId}/{artifactId}/{entryId}.yml）。 */
    private static final String GROUP_ID = "com.ecat";
    private static final String ARTIFACT_ID = "skip-report-test";

    private YmlConfigEntryPersistence persistence;
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;
    private final List<File> created = new ArrayList<>();

    @Before
    public void setUp() {
        // 挂 ListAppender 到被测 logger（LogFactory.getLog(clazz) 的底层 logback logger 名 = 类 FQN）
        logger = (Logger) LoggerFactory.getLogger(YmlConfigEntryPersistence.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);

        persistence = new YmlConfigEntryPersistence();
    }

    @After
    public void tearDown() throws Exception {
        logger.detachAppender(appender);
        for (int i = created.size() - 1; i >= 0; i--) {
            File f = created.get(i);
            if (f.exists() && !f.delete()) {
                f.deleteOnExit();
            }
        }
    }

    @Test
    public void corruptAndEmptyFiles_skippedButOperatorVisibleInLogs() throws Exception {
        // 与生产实证同款损坏形态（vision-analysis 历史 entry 的 data 里带全局标签，
        // core 类路径上无该类，SnakeYAML 拒绝构造——正是被静默丢弃的那 2 个文件的根因）
        File broken = writeFixture("broken-1.yml",
            "entryId: broken-1\n"
            + "coordinate: " + GROUP_ID + ":" + ARTIFACT_ID + "\n"
            + "data:\n"
            + "  _ffprobe_result: !!com.ecat.integration.vision.analysis.model.FfprobeResult {}\n");
        File empty = writeFixture("empty-2.yml", "");
        File good = writeFixture("good-1.yml",
            "entryId: good-1\n"
            + "coordinate: " + GROUP_ID + ":" + ARTIFACT_ID + "\n"
            + "uniqueId: skip-report-good\n"
            + "title: SkipReportGood\n"
            + "enabled: true\n");

        List<ConfigEntry> entries = persistence.loadAll();

        // 既有行为不回退：坏/空文件不进 entries；好文件正常加载
        assertTrue("好文件正常加载", containsEntryId(entries, "good-1"));
        assertFalse("解析失败文件被跳过", containsEntryId(entries, "broken-1"));
        assertFalse("空文件被跳过", containsEntryId(entries, "empty-2"));

        // 每文件一行 WARN 带原因（两个条件的消息模板不同，不受 ErrorRateLimitFilter 同签名限频影响）
        assertTrue("解析失败须有一行 WARN 点名文件+原因: " + messages(),
            hasLog(Level.WARN, "跳过无法解析的 entry 文件") && hasLog(Level.WARN, broken.getName()));
        assertTrue("WARN 须带异常摘要（原因）: " + messages(), hasLog(Level.WARN, "原因: "));
        assertTrue("空文件（原先完全静默）须有一行 WARN: " + messages(),
            hasLog(Level.WARN, "跳过空 entry 文件") && hasLog(Level.WARN, empty.getName()));

        // 启动汇总一行 ERROR：数量 + 坐标列表
        assertTrue("须有 ERROR 汇总行点名跳过数量: " + messages(), hasLog(Level.ERROR, "2 个 entry 文件解析失败被跳过"));
        assertTrue("汇总须含坐标定位（布局推导）: " + messages(),
            hasLog(Level.ERROR, GROUP_ID + ":" + ARTIFACT_ID + "(broken-1)")
                && hasLog(Level.ERROR, GROUP_ID + ":" + ARTIFACT_ID + "(empty-2)"));
    }

    // ==================== 夹具与断言辅助 ====================

    /**
     * 夹具文件写进 BASE_DIR 布局（BASE_DIR 是静态相对路径 .ecat-data/core/config_entries，
     * 测试进程 cwd 即模块根），@After 逐个删除——与既有 YmlConfigEntryPersistenceTest 的清理先例一致。
     */
    private File writeFixture(String fileName, String content) throws Exception {
        File baseDirFile = new File(".ecat-data/core/config_entries/" + GROUP_ID + "/" + ARTIFACT_ID);
        assertTrue("BASE_DIR 布局目录创建失败: " + baseDirFile,
            baseDirFile.mkdirs() || baseDirFile.exists());
        File file = new File(baseDirFile, fileName);
        try (Writer writer = new FileWriter(file)) {
            writer.write(content);
        }
        created.add(file);
        created.add(baseDirFile);
        created.add(baseDirFile.getParentFile());
        return file;
    }

    private static boolean containsEntryId(List<ConfigEntry> entries, String entryId) {
        for (ConfigEntry entry : entries) {
            if (entryId.equals(entry.getEntryId())) {
                return true;
            }
        }
        return false;
    }

    private boolean hasLog(Level level, String fragment) {
        for (ILoggingEvent event : appender.list) {
            if (event.getLevel() == level && event.getFormattedMessage().contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    private String messages() {
        StringBuilder sb = new StringBuilder("已捕获日志: ");
        for (ILoggingEvent event : appender.list) {
            sb.append('[').append(event.getLevel()).append("] ")
                .append(event.getFormattedMessage().split("\n", 2)[0]).append("; ");
        }
        return sb.toString();
    }
}
