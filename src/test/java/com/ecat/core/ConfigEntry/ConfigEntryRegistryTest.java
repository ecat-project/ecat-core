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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * ConfigEntryRegistry 单元测试
 * <p>
 * 测试配置条目注册器的 CRUD 功能和唯一性校验。
 */
public class ConfigEntryRegistryTest {

    private ConfigEntryRegistry registry;
    private Path testDir;
    private ConfigEntryPersistence persistence;

    @Before
    public void setUp() throws IOException {
        // 创建临时测试目录
        testDir = Files.createTempDirectory("config-entry-test");
        String testBaseDir = testDir.toAbsolutePath().toString() + "/config_entries";

        // 创建测试用的 persistence（使用自定义目录）
        persistence = new YmlConfigEntryPersistence() {
            @Override
            public List<ConfigEntry> loadAll() {
                // 返回空列表，不从文件系统加载
                return new java.util.ArrayList<>();
            }

            @Override
            public void save(ConfigEntry entry) {
                // 不实际保存到文件系统
            }

            @Override
            public void update(ConfigEntry entry) {
                // 不实际保存到文件系统
            }

            @Override
            public void delete(String entryId) {
                // 不实际删除文件
            }
        };

        registry = new ConfigEntryRegistry(persistence);
    }

    @After
    public void tearDown() throws IOException {
        // 清理临时目录
        if (testDir != null && Files.exists(testDir)) {
            deleteDirectory(testDir.toFile());
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

    // ==================== createEntry() 测试 ====================

    @Test
    public void testCreateEntry_Success() {
        ConfigEntry entry = new ConfigEntry.Builder()
                .coordinate("com.ecat.integration:demo")
                .uniqueId("demo_123")
                .title("Test Entry")
                .build();

        ConfigEntry created = registry.createEntry(entry);

        assertNotNull("创建的 entry 不应为 null", created);
        assertNotNull("entryId 应该自动生成", created.getEntryId());
        assertEquals("coordinate 应该保持不变", "com.ecat.integration:demo", created.getCoordinate());
        assertEquals("uniqueId 应该保持不变", "demo_123", created.getUniqueId());
        assertEquals("title 应该保持不变", "Test Entry", created.getTitle());
        assertNotNull("createTime 应该自动设置", created.getCreateTime());
        assertNotNull("updateTime 应该自动设置", created.getUpdateTime());
        assertEquals("默认版本应该是 1", 1, created.getVersion());
    }

    @Test
    public void testCreateEntry_WithExistingEntryId() {
        String existingId = "existing-id";

        ConfigEntry entry = new ConfigEntry.Builder()
                .entryId(existingId)
                .coordinate("com.ecat.integration:demo")
                .uniqueId("demo_123")
                .title("Test Entry")
                .build();

        ConfigEntry created = registry.createEntry(entry);

        assertEquals("entryId 应该保持指定值", existingId, created.getEntryId());
    }

    @Test
    public void testCreateEntry_DuplicateUniqueId() {
        String uniqueId = "demo_123";

        // 创建第一个 entry
        ConfigEntry entry1 = new ConfigEntry.Builder()
                .coordinate("com.ecat.integration:demo")
                .uniqueId(uniqueId)
                .title("Entry 1")
                .build();

        registry.createEntry(entry1);

        // 尝试创建相同 uniqueId 的 entry
        ConfigEntry entry2 = new ConfigEntry.Builder()
                .coordinate("com.ecat.integration:demo")
                .uniqueId(uniqueId)
                .title("Entry 2")
                .build();

        try {
            registry.createEntry(entry2);
            fail("应该抛出 DuplicateUniqueIdException");
        } catch (ConfigEntryRegistry.DuplicateUniqueIdException e) {
            assertTrue("异常消息应该包含 uniqueId", e.getMessage().contains(uniqueId));
        }
    }

    @Test
    public void testCreateEntry_NullUniqueId() {
        ConfigEntry entry = new ConfigEntry.Builder()
                .coordinate("com.ecat.integration:demo")
                .uniqueId(null)
                .title("Test Entry")
                .build();

        // null uniqueId 不应该抛出异常
        ConfigEntry created = registry.createEntry(entry);
        assertNotNull("创建的 entry 不应为 null", created);
    }

    @Test
    public void testCreateEntry_AutoSetTimestamps() {
        long beforeCreate = System.currentTimeMillis();

        ConfigEntry entry = new ConfigEntry.Builder()
                .coordinate("com.ecat.integration:demo")
                .uniqueId("demo_123")
                .title("Test Entry")
                .build();

        ConfigEntry created = registry.createEntry(entry);

        long afterCreate = System.currentTimeMillis();

        assertNotNull("createTime 不应为 null", created.getCreateTime());
        assertNotNull("updateTime 不应为 null", created.getUpdateTime());

        // 验证时间在合理范围内
        long createTimeMillis = created.getCreateTime().toInstant().toEpochMilli();
        assertTrue("createTime 应该在创建时间附近",
            createTimeMillis >= beforeCreate && createTimeMillis <= afterCreate);
    }

    // ==================== updateEntry() 测试 ====================

    @Test
    public void testUpdateEntry_Success() {
        // 创建初始 entry
        ConfigEntry original = new ConfigEntry.Builder()
                .coordinate("com.ecat.integration:demo")
                .uniqueId("demo_123")
                .title("Original Title")
                .build();

        ConfigEntry created = registry.createEntry(original);
        String entryId = created.getEntryId();

        // 更新 entry
        Map<String, Object> newData = new HashMap<>();
        newData.put("key", "value");

        ConfigEntry updateRequest = new ConfigEntry.Builder()
                .title("Updated Title")
                .data(newData)
                .enabled(false)
                .build();

        ConfigEntry updated = registry.updateEntry(entryId, updateRequest);

        assertNotNull("更新后的 entry 不应为 null", updated);
        assertEquals("entryId 不应改变", entryId, updated.getEntryId());
        assertEquals("title 应该更新", "Updated Title", updated.getTitle());
        assertEquals("data 应该更新", "value", updated.getData().get("key"));
        assertFalse("enabled 应该更新", updated.isEnabled());
        assertEquals("版本号应该递增", 2, updated.getVersion());
    }

    @Test
    public void testUpdateEntry_NotFound() {
        ConfigEntry updateRequest = new ConfigEntry.Builder()
                .title("Updated Title")
                .build();

        try {
            registry.updateEntry("non-existent-id", updateRequest);
            fail("应该抛出 EntryNotFoundException");
        } catch (ConfigEntryRegistry.EntryNotFoundException e) {
            assertTrue("异常消息应该包含 entryId", e.getMessage().contains("non-existent-id"));
        }
    }

    @Test
    public void testUpdateEntry_PreservesCoreFields() {
        // 创建初始 entry
        ConfigEntry original = new ConfigEntry.Builder()
                .coordinate("com.ecat.integration:demo")
                .uniqueId("demo_123")
                .title("Original Title")
                .build();

        ConfigEntry created = registry.createEntry(original);
        String entryId = created.getEntryId();

        // 更新 entry（只提供新数据）
        ConfigEntry updateRequest = new ConfigEntry.Builder()
                .title("Updated Title")
                .build();

        ConfigEntry updated = registry.updateEntry(entryId, updateRequest);

        // 验证核心字段不变
        assertEquals("coordinate 不应改变", "com.ecat.integration:demo", updated.getCoordinate());
        assertEquals("uniqueId 不应改变", "demo_123", updated.getUniqueId());
        assertEquals("createTime 不应改变", created.getCreateTime(), updated.getCreateTime());
    }

    // ==================== removeEntry() 测试 ====================

    @Test
    public void testRemoveEntry_Success() {
        // 创建 entry
        ConfigEntry entry = new ConfigEntry.Builder()
                .coordinate("com.ecat.integration:demo")
                .uniqueId("demo_123")
                .title("Test Entry")
                .build();

        ConfigEntry created = registry.createEntry(entry);
        String entryId = created.getEntryId();

        // 删除 entry
        registry.removeEntry(entryId);

        // 验证已删除
        ConfigEntry retrieved = registry.getByEntryId(entryId);
        assertNull("entry 应该被删除", retrieved);
    }

    @Test
    public void testRemoveEntry_NotFound() {
        try {
            registry.removeEntry("non-existent-id");
            fail("应该抛出 EntryNotFoundException");
        } catch (ConfigEntryRegistry.EntryNotFoundException e) {
            assertTrue("异常消息应该包含 entryId", e.getMessage().contains("non-existent-id"));
        }
    }

    // ==================== 查询方法测试 ====================

    @Test
    public void testGetByEntryId() {
        // 创建 entry
        ConfigEntry entry = new ConfigEntry.Builder()
                .coordinate("com.ecat.integration:demo")
                .uniqueId("demo_123")
                .title("Test Entry")
                .build();

        ConfigEntry created = registry.createEntry(entry);
        String entryId = created.getEntryId();

        // 查询
        ConfigEntry retrieved = registry.getByEntryId(entryId);

        assertNotNull("应该找到 entry", retrieved);
        assertEquals("entryId 应该匹配", entryId, retrieved.getEntryId());
    }

    @Test
    public void testGetByEntryId_NotFound() {
        ConfigEntry retrieved = registry.getByEntryId("non-existent-id");
        assertNull("未找到 entry 应该返回 null", retrieved);
    }

    @Test
    public void testGetByUniqueId() {
        // 创建 entry
        ConfigEntry entry = new ConfigEntry.Builder()
                .coordinate("com.ecat.integration:demo")
                .uniqueId("demo_123")
                .title("Test Entry")
                .build();

        ConfigEntry created = registry.createEntry(entry);

        // 通过 uniqueId 查询
        ConfigEntry retrieved = registry.getByUniqueId("demo_123");

        assertNotNull("应该找到 entry", retrieved);
        assertEquals("uniqueId 应该匹配", "demo_123", retrieved.getUniqueId());
        assertEquals("entryId 应该匹配", created.getEntryId(), retrieved.getEntryId());
    }

    @Test
    public void testGetByUniqueId_NotFound() {
        ConfigEntry retrieved = registry.getByUniqueId("non-existent-unique-id");
        assertNull("未找到 entry 应该返回 null", retrieved);
    }

    @Test
    public void testListByCoordinate() {
        // 创建多个 entries
        ConfigEntry entry1 = new ConfigEntry.Builder()
                .coordinate("com.ecat.integration:demo")
                .uniqueId("demo_1")
                .title("Entry 1")
                .build();

        ConfigEntry entry2 = new ConfigEntry.Builder()
                .coordinate("com.ecat.integration:demo")
                .uniqueId("demo_2")
                .title("Entry 2")
                .build();

        ConfigEntry entry3 = new ConfigEntry.Builder()
                .coordinate("com.ecat.integration:other")
                .uniqueId("other_1")
                .title("Other Entry")
                .build();

        registry.createEntry(entry1);
        registry.createEntry(entry2);
        registry.createEntry(entry3);

        // 查询特定 coordinate
        List<ConfigEntry> demoEntries = registry.listByCoordinate("com.ecat.integration:demo");

        assertEquals("应该找到 2 个 entries", 2, demoEntries.size());
        assertTrue("所有结果应该是 demo coordinate", demoEntries.stream()
            .allMatch(e -> "com.ecat.integration:demo".equals(e.getCoordinate())));
    }

    @Test
    public void testHasEntries() {
        String coordinate = "com.ecat.integration:demo";

        // 初始状态
        assertFalse("初始时应该没有 entries", registry.hasEntries(coordinate));

        // 添加 entry
        ConfigEntry entry = new ConfigEntry.Builder()
                .coordinate(coordinate)
                .uniqueId("demo_123")
                .title("Test Entry")
                .build();

        registry.createEntry(entry);

        // 验证
        assertTrue("添加后应该有 entries", registry.hasEntries(coordinate));
    }

    @Test
    public void testListAll() {
        // 创建多个 entries
        ConfigEntry entry1 = new ConfigEntry.Builder()
                .coordinate("com.ecat.integration:demo")
                .uniqueId("demo_1")
                .title("Entry 1")
                .build();

        ConfigEntry entry2 = new ConfigEntry.Builder()
                .coordinate("com.ecat.integration:demo")
                .uniqueId("demo_2")
                .title("Entry 2")
                .build();

        registry.createEntry(entry1);
        registry.createEntry(entry2);

        // 列出所有
        List<ConfigEntry> all = registry.listAll();

        assertEquals("应该找到 2 个 entries", 2, all.size());
    }

    // ==================== setEnabled() 测试 ====================

    @Test
    public void testSetEnabled_Enable() {
        // 创建禁用的 entry
        ConfigEntry entry = new ConfigEntry.Builder()
                .coordinate("com.ecat.integration:demo")
                .uniqueId("demo_123")
                .title("Test Entry")
                .enabled(false)
                .build();

        ConfigEntry created = registry.createEntry(entry);
        String entryId = created.getEntryId();

        // 启用
        ConfigEntry updated = registry.setEnabled(entryId, true);

        assertNotNull("更新后的 entry 不应为 null", updated);
        assertTrue("应该被启用", updated.isEnabled());
        assertEquals("版本号应该递增", 2, updated.getVersion());
    }

    @Test
    public void testSetEnabled_Disable() {
        // 创建启用的 entry
        ConfigEntry entry = new ConfigEntry.Builder()
                .coordinate("com.ecat.integration:demo")
                .uniqueId("demo_123")
                .title("Test Entry")
                .enabled(true)
                .build();

        ConfigEntry created = registry.createEntry(entry);
        String entryId = created.getEntryId();

        // 禁用
        ConfigEntry updated = registry.setEnabled(entryId, false);

        assertFalse("应该被禁用", updated.isEnabled());
    }

    @Test
    public void testSetEnabled_NotFound() {
        try {
            registry.setEnabled("non-existent-id", true);
            fail("应该抛出 EntryNotFoundException");
        } catch (ConfigEntryRegistry.EntryNotFoundException e) {
            // Expected
        }
    }
}
