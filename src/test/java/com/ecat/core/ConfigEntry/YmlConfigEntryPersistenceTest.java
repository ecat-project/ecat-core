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

import com.ecat.core.Integration.IntegrationBase;
import com.ecat.core.ConfigFormatException;
import com.ecat.core.Utils.DateTimeUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * YmlConfigEntryPersistence 单元测试
 * <p>
 * 测试 YAML 持久化的读写功能。
 */
public class YmlConfigEntryPersistenceTest {

    private YmlConfigEntryPersistence persistence;
    private Path testDir;

    @Before
    public void setUp() throws IOException {
        // 创建临时测试目录
        testDir = Files.createTempDirectory("yml-config-test");
        String testBaseDir = testDir.toAbsolutePath().toString() + "/config_entries";
        new File(testBaseDir).mkdirs();
    }

    @After
    public void tearDown() throws IOException {
        // 清理临时目录
        if (testDir != null && Files.exists(testDir)) {
            deleteDirectory(testDir.toFile());
        }
        // 清理本测试类使用的 .ecat-data 分组目录（递归——文件实际落在
        // {groupId}/{artifactId}/{entryId}.yml 布局中；残留的非 String version 文件
        // 会让 loadAll 聚合 fail-closed，必须逐轮自愈清空）
        File groupDir = new File(".ecat-data/core/config_entries/com.ecat.integration");
        if (groupDir.exists()) {
            deleteDirectory(groupDir);
        }
        File failClosedDir = new File(".ecat-data/core/config_entries/com.ecat.it");
        if (failClosedDir.exists()) {
            deleteDirectory(failClosedDir);
        }
    }

    private void deleteDirectory(File directory) throws IOException {
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isDirectory()) {
                        deleteDirectory(file);
                    } else {
                        file.delete();
                    }
                }
            }
            directory.delete();
        }
    }

    // ==================== save() 测试 ====================

    @Test
    public void testSave_CreateFile() {
        persistence = new YmlConfigEntryPersistence();

        ConfigEntry entry = new ConfigEntry.Builder()
                .entryId("test-id-save")
                .coordinate("com.ecat.integration:demo")
                .uniqueId("demo_123")
                .title("Test Entry")
                .version(IntegrationBase.DEFAULT_ENTRY_FORMAT_VERSION)
                .build();

        persistence.save(entry);

        // 验证文件已创建 - 新路径： {groupId}/{artifactId}/{entryId}.yml
        File file = new File(".ecat-data/core/config_entries/com.ecat.integration/demo/test-id-save.yml");
        assertTrue("配置文件应该存在", file.exists());

        // 清理
        file.delete();
        new File(".ecat-data/core/config_entries/com.ecat.integration/demo").delete();
        new File(".ecat-data/core/config_entries/com.ecat.integration").delete();
    }

    @Test
    public void testSave_WithData() {
        persistence = new YmlConfigEntryPersistence();

        Map<String, Object> data = new HashMap<>();
        data.put("key1", "value1");
        data.put("key2", 123);
        data.put("key3", true);

        ConfigEntry entry = new ConfigEntry.Builder()
                .entryId("test-id-data")
                .coordinate("com.ecat.integration:demo")
                .uniqueId("demo_123")
                .title("Test Entry")
                .data(data)
                .enabled(false)
                .version(IntegrationBase.DEFAULT_ENTRY_FORMAT_VERSION)
                .build();

        persistence.save(entry);

        // 清理
        File file = new File(".ecat-data/core/config_entries/com.ecat.integration/demo/test-id-data.yml");
        if (file.exists()) {
            file.delete();
        }
        new File(".ecat-data/core/config_entries/com.ecat.integration/demo").delete();
        new File(".ecat-data/core/config_entries/com.ecat.integration").delete();
    }

    @Test
    public void testSave_WithTimestamps() {
        persistence = new YmlConfigEntryPersistence();

        ZonedDateTime now = ZonedDateTime.of(2026, 3, 11, 12, 30, 45, 0, java.time.ZoneOffset.UTC);

        ConfigEntry entry = new ConfigEntry.Builder()
                .entryId("test-id-time")
                .coordinate("com.ecat.integration:demo")
                .uniqueId("demo_123")
                .title("Test Entry")
                .createTime(now)
                .updateTime(now)
                .version(IntegrationBase.DEFAULT_ENTRY_FORMAT_VERSION)
                .build();

        persistence.save(entry);

        // 清理（文件实际落在 {groupId}/{artifactId}/ 布局中，须删真实路径——
        // 历史上此处删的是根级路径，残留文件会让后续 loadAll 撞聚合 fail-closed）
        File file = new File(".ecat-data/core/config_entries/com.ecat.integration/demo/test-id-time.yml");
        if (file.exists()) {
            file.delete();
        }
    }

    // ==================== loadAll() 测试 ====================

    @Test
    public void testLoadAll_AfterSave() {
        persistence = new YmlConfigEntryPersistence();

        // 创建并保存 entry
        ConfigEntry entry = new ConfigEntry.Builder()
                .entryId("test-id-load")
                .coordinate("com.ecat.integration:demo")
                .uniqueId("demo_123")
                .title("Test Entry")
                .version(IntegrationBase.DEFAULT_ENTRY_FORMAT_VERSION)
                .build();

        persistence.save(entry);

        // 加载所有 entries
        List<ConfigEntry> entries = persistence.loadAll();

        assertFalse("应该加载到至少一个 entry", entries.isEmpty());

        // 查找我们保存的 entry
        ConfigEntry loaded = entries.stream()
            .filter(e -> "test-id-load".equals(e.getEntryId()))
            .findFirst()
            .orElse(null);

        assertNotNull("应该找到保存的 entry", loaded);
        assertEquals("entryId 应该匹配", "test-id-load", loaded.getEntryId());
        assertEquals("coordinate 应该匹配", "com.ecat.integration:demo", loaded.getCoordinate());
        assertEquals("uniqueId 应该匹配", "demo_123", loaded.getUniqueId());
        assertEquals("title 应该匹配", "Test Entry", loaded.getTitle());
        assertEquals("格式版本应为 String round-trip 保持", IntegrationBase.DEFAULT_ENTRY_FORMAT_VERSION, loaded.getVersion());

        // 清理
        File dir = new File(".ecat-data/core/config_entries/com.ecat.integration/demo");
        if (dir.exists()) {
            try { deleteDirectory(dir); } catch (IOException e) { /* ignore */ }
        }
    }

    // ==================== update() 测试 ====================

    @Test
    public void testUpdate() {
        persistence = new YmlConfigEntryPersistence();

        // 创建并保存 entry
        ConfigEntry entry = new ConfigEntry.Builder()
                .entryId("test-id-update")
                .coordinate("com.ecat.integration:demo")
                .uniqueId("demo_123")
                .title("Original Title")
                .version(IntegrationBase.DEFAULT_ENTRY_FORMAT_VERSION)
                .build();

        persistence.save(entry);

        // 更新 entry（格式版本是文件级契约：update 重写文件后须 round-trip 保持）
        ConfigEntry updated = new ConfigEntry.Builder()
                .entryId("test-id-update")
                .coordinate("com.ecat.integration:demo")
                .uniqueId("demo_123")
                .title("Updated Title")
                .version(IntegrationBase.DEFAULT_ENTRY_FORMAT_VERSION)
                .build();

        persistence.update(updated);

        // 验证更新
        List<ConfigEntry> entries = persistence.loadAll();
        ConfigEntry loaded = entries.stream()
            .filter(e -> "test-id-update".equals(e.getEntryId()))
            .findFirst()
            .orElse(null);

        assertNotNull("应该找到 entry", loaded);
        assertEquals("title 应该更新", "Updated Title", loaded.getTitle());
        assertEquals("格式版本应 round-trip 保持", IntegrationBase.DEFAULT_ENTRY_FORMAT_VERSION, loaded.getVersion());

        // 清理
        File dir = new File(".ecat-data/core/config_entries/com.ecat.integration/demo");
        if (dir.exists()) {
            try { deleteDirectory(dir); } catch (IOException e) { /* ignore */ }
        }
    }

    // ==================== delete() 测试 ====================

    @Test
    public void testDelete() {
        persistence = new YmlConfigEntryPersistence();

        // 创建并保存 entry
        ConfigEntry entry = new ConfigEntry.Builder()
                .entryId("test-id-delete")
                .coordinate("com.ecat.integration:demo")
                .uniqueId("demo_123")
                .title("Test Entry")
                .version(IntegrationBase.DEFAULT_ENTRY_FORMAT_VERSION)
                .build();

        persistence.save(entry);

        // 验证文件存在 - 新路径： {groupId}/{artifactId}/{entryId}.yml
        File file = new File(".ecat-data/core/config_entries/com.ecat.integration/demo/test-id-delete.yml");
        assertTrue("文件应该存在", file.exists());

        // 删除
        persistence.delete("test-id-delete");

        // 验证文件已删除
        assertFalse("文件应该被删除", file.exists());
    }

    @Test
    public void testDelete_NonExistentFile() {
        persistence = new YmlConfigEntryPersistence();

        // 删除不存在的文件不应抛出异常
        persistence.delete("non-existent-id");
    }

    // ==================== 往返测试 ====================

    @Test
    public void testRoundTrip_CompleteEntry() {
        persistence = new YmlConfigEntryPersistence();

        // 创建完整的 entry
        Map<String, Object> data = new HashMap<>();
        data.put("host", "192.168.1.1");
        data.put("port", 502);
        data.put("timeout", 5000L);  // 使用 Long 类型

        ZonedDateTime createTime = ZonedDateTime.of(2026, 3, 11, 12, 30, 45, 0, java.time.ZoneOffset.UTC);

        ConfigEntry original = new ConfigEntry.Builder()
                .entryId("test-id-roundtrip")
                .coordinate("com.ecat.integration:demo")
                .uniqueId("demo_123")
                .title("Test Entry")
                .data(data)
                .enabled(true)
                .createTime(createTime)
                .updateTime(createTime)
                .version(IntegrationBase.DEFAULT_ENTRY_FORMAT_VERSION)
                .build();

        // 保存
        persistence.save(original);

        // 加载
        List<ConfigEntry> entries = persistence.loadAll();
        ConfigEntry loaded = entries.stream()
            .filter(e -> "test-id-roundtrip".equals(e.getEntryId()))
            .findFirst()
            .orElse(null);

        assertNotNull("应该找到 entry", loaded);
        assertEquals("entryId 应该匹配", original.getEntryId(), loaded.getEntryId());
        assertEquals("coordinate 应该匹配", original.getCoordinate(), loaded.getCoordinate());
        assertEquals("uniqueId 应该匹配", original.getUniqueId(), loaded.getUniqueId());
        assertEquals("title 应该匹配", original.getTitle(), loaded.getTitle());
        assertEquals("enabled 应该匹配", original.isEnabled(), loaded.isEnabled());
        assertEquals("格式版本应该匹配", IntegrationBase.DEFAULT_ENTRY_FORMAT_VERSION, loaded.getVersion());
        assertTrue("加载后的格式版本应为 String 类型", loaded.getVersion() instanceof String);

        // 验证 data (注意 YAML 可能会改变数字类型)
        assertNotNull("data 不应为 null", loaded.getData());
        assertEquals("192.168.1.1", loaded.getData().get("host"));
        // port 和 timeout 可能被解析为 Integer
        assertEquals(502, ((Number) loaded.getData().get("port")).intValue());
        assertEquals(5000L, ((Number) loaded.getData().get("timeout")).longValue());

        // 验证时间（允许一定的误差）
        assertNotNull("createTime 不应为 null", loaded.getCreateTime());
        assertNotNull("updateTime 不应为 null", loaded.getUpdateTime());

        // 清理
        File dir = new File(".ecat-data/core/config_entries/com.ecat.integration/demo");
        if (dir.exists()) {
            try { deleteDirectory(dir); } catch (IOException e) { /* ignore */ }
        }
    }

    @Test
    public void testRoundTrip_SimpleEntry() {
        persistence = new YmlConfigEntryPersistence();

        ConfigEntry original = new ConfigEntry.Builder()
                .entryId("test-id-simple")
                .coordinate("com.ecat.integration:demo")
                .uniqueId("demo_123")
                .title("Test Entry")
                .version(IntegrationBase.DEFAULT_ENTRY_FORMAT_VERSION)
                .build();

        persistence.save(original);

        List<ConfigEntry> entries = persistence.loadAll();
        ConfigEntry loaded = entries.stream()
            .filter(e -> "test-id-simple".equals(e.getEntryId()))
            .findFirst()
            .orElse(null);

        assertNotNull("应该找到 entry", loaded);
        assertEquals("entryId 应该匹配", original.getEntryId(), loaded.getEntryId());
        assertEquals("coordinate 应该匹配", original.getCoordinate(), loaded.getCoordinate());

        // 清理
        File dir = new File(".ecat-data/core/config_entries/com.ecat.integration/demo");
        if (dir.exists()) {
            try { deleteDirectory(dir); } catch (IOException e) { /* ignore */ }
        }
    }

    @Test
    public void testRoundTrip_Source() {
        persistence = new YmlConfigEntryPersistence();

        ConfigEntry original = new ConfigEntry.Builder()
                .entryId("test-id-source")
                .coordinate("com.ecat.integration:demo")
                .uniqueId("demo_import")
                .title("Imported")
                .version(IntegrationBase.DEFAULT_ENTRY_FORMAT_VERSION)
                .source(SourceType.IMPORT_FLOW)
                .build();

        persistence.save(original);

        List<ConfigEntry> entries = persistence.loadAll();
        ConfigEntry loaded = entries.stream()
            .filter(e -> "test-id-source".equals(e.getEntryId()))
            .findFirst()
            .orElse(null);

        assertNotNull("应该找到 entry", loaded);
        assertEquals("source 应往返保持 IMPORT_FLOW", SourceType.IMPORT_FLOW, loaded.getSource());

        // 清理
        File dir = new File(".ecat-data/core/config_entries/com.ecat.integration/demo");
        if (dir.exists()) {
            try { deleteDirectory(dir); } catch (IOException e) { /* ignore */ }
        }
    }

    // ==================== 边界情况测试 ====================

    @Test
    public void testSave_NullValues() {
        persistence = new YmlConfigEntryPersistence();

        // 使用 empty map 而不是 null
        ConfigEntry entry = new ConfigEntry.Builder()
                .entryId("test-id-nulls")
                .coordinate("com.ecat.integration:demo")
                .uniqueId(null)  // null uniqueId
                .title(null)     // null title
                .data(new HashMap<>())  // empty data
                .version(IntegrationBase.DEFAULT_ENTRY_FORMAT_VERSION)
                .build();

        persistence.save(entry);

        // 加载验证
        List<ConfigEntry> entries = persistence.loadAll();
        ConfigEntry loaded = entries.stream()
            .filter(e -> "test-id-nulls".equals(e.getEntryId()))
            .findFirst()
            .orElse(null);

        assertNotNull("应该找到 entry", loaded);
        assertNull("uniqueId 应该是 null", loaded.getUniqueId());
        assertNull("title 应该是 null", loaded.getTitle());
        assertNotNull("data 不应为 null", loaded.getData());

        // 清理
        File dir = new File(".ecat-data/core/config_entries/com.ecat.integration/demo");
        if (dir.exists()) {
            try { deleteDirectory(dir); } catch (IOException e) { /* ignore */ }
        }
    }

    // ==================== 格式版本 fail-closed 测试 ====================

    /** 落一个原始 yml entry 文件（不做任何转义加工），body 为文件全部内容。 */
    private File writeRawEntryFile(String artifactId, String entryId, String ymlBody) throws IOException {
        File dir = new File(".ecat-data/core/config_entries/com.ecat.it/" + artifactId);
        if (dir.exists()) {
            deleteDirectory(dir);
        }
        dir.mkdirs();
        File file = new File(dir, entryId + ".yml");
        java.nio.file.Files.write(file.toPath(), ymlBody.getBytes("UTF-8"));
        return file;
    }

    private static String entryYmlBody(String entryId, String versionLine) {
        return "entryId: \"" + entryId + "\"\n"
                + "coordinate: \"com.ecat.it:failclosed\"\n"
                + "uniqueId: \"fc_" + entryId + "\"\n"
                + "title: \"fail closed fixture\"\n"
                + "data: {}\n"
                + "stepInputs: {}\n"
                + "enabled: true\n"
                + "createTime: \"2026-10-01T10:00:00+08:00[Asia/Shanghai]\"\n"
                + "updateTime: \"2026-10-01T10:00:00+08:00[Asia/Shanghai]\"\n"
                + versionLine + "\n"
                + "source: USER\n";
    }

    /** 旧计数器 int（yaml 裸 `version: 1`→Integer）必须 fail-closed 聚合抛出，不静默跳过。 */
    @Test
    public void loadAll_legacyIntVersionFailsClosed() throws IOException {
        persistence = new YmlConfigEntryPersistence();
        File offender = writeRawEntryFile("fc1", "fc-entry-1",
                entryYmlBody("fc-entry-1", "version: 1"));

        try {
            persistence.loadAll();
            fail("旧计数器 int 版本应聚合 fail-closed 抛出，而非静默跳过");
        } catch (ConfigFormatException e) {
            String message = e.getMessage();
            assertTrue("消息应含 offender 文件绝对路径", message.contains(offender.getAbsolutePath()));
            assertTrue("消息应含 coordinate 定位", message.contains("com.ecat.it:failclosed"));
            assertTrue("消息应含 entryId", message.contains("fc-entry-1"));
            assertTrue("actual 应携带类型:值（Integer:1）", e.getActual().contains("Integer:1"));
        }
    }

    /** 聚合性：两个 offender 文件须在一条异常消息里同时点名（不给「修一个跑一次」的循环）。 */
    @Test
    public void loadAll_aggregatesAllVersionOffendersInOneThrow() throws IOException {
        persistence = new YmlConfigEntryPersistence();
        File offenderA = writeRawEntryFile("fc2", "fc-entry-a",
                entryYmlBody("fc-entry-a", "version: 1"));
        File offenderB = writeRawEntryFile("fc2b", "fc-entry-b",
                entryYmlBody("fc-entry-b", "version: 7"));

        try {
            persistence.loadAll();
            fail("两个 offender 应聚合为一次抛出");
        } catch (ConfigFormatException e) {
            String message = e.getMessage();
            assertTrue("消息应同时点名第一个 offender 文件",
                    message.contains(offenderA.getAbsolutePath()));
            assertTrue("消息应同时点名第二个 offender 文件",
                    message.contains(offenderB.getAbsolutePath()));
            assertTrue("聚合清单应含 2 个文件计数", e.getActual().contains("共 2 个"));
        }
    }

    /** 未加引号小数（yaml 裸 `version: 4.0`→Double）同样拒绝，actual 标明 Double 类型。 */
    @Test
    public void loadAll_unquotedDecimalVersionFails() throws IOException {
        persistence = new YmlConfigEntryPersistence();
        writeRawEntryFile("fc3", "fc-entry-decimal",
                entryYmlBody("fc-entry-decimal", "version: 4.0"));

        try {
            persistence.loadAll();
            fail("未加引号被解析为 Double 的 version 应拒绝");
        } catch (ConfigFormatException e) {
            assertTrue("actual 应含 Double 类型标注", e.getActual().contains("Double"));
        }
    }
}
