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
        // 清理 .ecat-data 目录
        File ecatDataDir = new File(".ecat-data/core/config_entries");
        if (ecatDataDir.exists()) {
            File[] files = ecatDataDir.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.getName().startsWith("test-id")) {
                        file.delete();
                    }
                }
            }
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
                .version(2)
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
                .build();

        persistence.save(entry);

        // 清理
        File file = new File(".ecat-data/core/config_entries/test-id-time.yml");
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
                .version(1)
                .build();

        persistence.save(entry);

        // 更新 entry
        ConfigEntry updated = new ConfigEntry.Builder()
                .entryId("test-id-update")
                .coordinate("com.ecat.integration:demo")
                .uniqueId("demo_123")
                .title("Updated Title")
                .version(2)
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
        assertEquals("版本号应该更新", 2, loaded.getVersion());

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
                .version(1)
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
        assertEquals("版本号应该匹配", original.getVersion(), loaded.getVersion());

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
}
