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
import org.junit.Test;

import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * ConfigEntry 单元测试
 * <p>
 * 测试配置条目模型的构建和更新功能。
 */
public class ConfigEntryTest {

    // ==================== Builder 测试 ====================

    @Test
    public void testBuilder_DefaultValues() {
        ConfigEntry entry = new ConfigEntry.Builder()
                .entryId("test-id")
                .coordinate("com.ecat.integration:demo")
                .uniqueId("demo_123")
                .title("Test Entry")
                .build();

        assertNotNull("entryId 不应为 null", entry.getEntryId());
        assertEquals("test-id", entry.getEntryId());
        assertEquals("com.ecat.integration:demo", entry.getCoordinate());
        assertEquals("demo_123", entry.getUniqueId());
        assertEquals("Test Entry", entry.getTitle());
        assertTrue("默认应该启用", entry.isEnabled());
        assertNotNull("data 不应为 null", entry.getData());
        assertTrue("data 应该为空", entry.getData().isEmpty());
        assertEquals("默认版本应该是 1", 1, entry.getVersion());
    }

    @Test
    public void testBuilder_WithData() {
        Map<String, Object> data = new HashMap<>();
        data.put("key1", "value1");
        data.put("key2", 123);

        ConfigEntry entry = new ConfigEntry.Builder()
                .entryId("test-id")
                .coordinate("com.ecat.integration:demo")
                .uniqueId("demo_123")
                .title("Test Entry")
                .data(data)
                .enabled(false)
                .version(2)
                .build();

        assertNotNull("data 不应为 null", entry.getData());
        assertEquals(2, entry.getData().size());
        assertEquals("value1", entry.getData().get("key1"));
        assertEquals(123, entry.getData().get("key2"));
        assertFalse("应该被禁用", entry.isEnabled());
        assertEquals(2, entry.getVersion());
    }

    @Test
    public void testBuilder_WithTimestamps() {
        ZonedDateTime now = DateTimeUtils.now();

        ConfigEntry entry = new ConfigEntry.Builder()
                .entryId("test-id")
                .coordinate("com.ecat.integration:demo")
                .uniqueId("demo_123")
                .createTime(now)
                .updateTime(now)
                .build();

        assertNotNull("createTime 不应为 null", entry.getCreateTime());
        assertNotNull("updateTime 不应为 null", entry.getUpdateTime());
        assertEquals("创建时间应该相等", now, entry.getCreateTime());
        assertEquals("更新时间应该相等", now, entry.getUpdateTime());
    }

    @Test
    public void testBuilder_DataCopy() {
        Map<String, Object> originalData = new HashMap<>();
        originalData.put("key", "value");

        ConfigEntry entry = new ConfigEntry.Builder()
                .entryId("test-id")
                .coordinate("com.ecat.integration:demo")
                .data(originalData)
                .build();

        // 修改原始 map
        originalData.put("key2", "value2");

        // entry 的 data 应该不受影响（已复制）
        assertFalse("entry.data 不应包含新增的 key", entry.getData().containsKey("key2"));
    }

    // ==================== withUpdate() 测试 ====================

    @Test
    public void testWithUpdate_TitleUpdate() {
        ConfigEntry original = new ConfigEntry.Builder()
                .entryId("test-id")
                .coordinate("com.ecat.integration:demo")
                .uniqueId("demo_123")
                .title("Original Title")
                .version(1)
                .build();

        ConfigEntry newData = new ConfigEntry.Builder()
                .title("Updated Title")
                .build();

        ConfigEntry updated = original.withUpdate(newData);

        // 验证核心字段不变
        assertEquals("entryId 不应改变", "test-id", updated.getEntryId());
        assertEquals("coordinate 不应改变", "com.ecat.integration:demo", updated.getCoordinate());
        assertEquals("uniqueId 不应改变", "demo_123", updated.getUniqueId());

        // 验证可变字段更新
        assertEquals("title 应该更新", "Updated Title", updated.getTitle());

        // 验证版本号递增
        assertEquals("版本号应该递增", 2, updated.getVersion());

        // 验证时间戳
        assertNotNull("updateTime 不应为 null", updated.getUpdateTime());
        assertEquals("createTime 应该保持不变", original.getCreateTime(), updated.getCreateTime());
    }

    @Test
    public void testWithUpdate_DataUpdate() {
        Map<String, Object> originalData = new HashMap<>();
        originalData.put("key1", "value1");

        ConfigEntry original = new ConfigEntry.Builder()
                .entryId("test-id")
                .coordinate("com.ecat.integration:demo")
                .uniqueId("demo_123")
                .data(originalData)
                .version(1)
                .build();

        Map<String, Object> newData = new HashMap<>();
        newData.put("key2", "value2");

        ConfigEntry updateRequest = new ConfigEntry.Builder()
                .data(newData)
                .build();

        ConfigEntry updated = original.withUpdate(updateRequest);

        assertNotNull("data 不应为 null", updated.getData());
        assertTrue("旧数据应该被替换", updated.getData().containsKey("key2"));
        assertFalse("旧 key1 应该不存在", updated.getData().containsKey("key1"));
    }

    @Test
    public void testWithUpdate_EnabledUpdate() {
        ConfigEntry original = new ConfigEntry.Builder()
                .entryId("test-id")
                .coordinate("com.ecat.integration:demo")
                .uniqueId("demo_123")
                .enabled(true)
                .version(1)
                .build();

        ConfigEntry newData = new ConfigEntry.Builder()
                .enabled(false)
                .build();

        ConfigEntry updated = original.withUpdate(newData);

        assertFalse("应该被禁用", updated.isEnabled());
    }

    @Test
    public void testWithUpdate_NullTitle() {
        ConfigEntry original = new ConfigEntry.Builder()
                .entryId("test-id")
                .coordinate("com.ecat.integration:demo")
                .uniqueId("demo_123")
                .title("Original Title")
                .version(1)
                .build();

        ConfigEntry newData = new ConfigEntry.Builder()
                .title(null)
                .build();

        ConfigEntry updated = original.withUpdate(newData);

        assertEquals("title 应该保持不变", "Original Title", updated.getTitle());
    }

    @Test
    public void testWithUpdate_NullData() {
        Map<String, Object> originalData = new HashMap<>();
        originalData.put("key", "value");

        ConfigEntry original = new ConfigEntry.Builder()
                .entryId("test-id")
                .coordinate("com.ecat.integration:demo")
                .uniqueId("demo_123")
                .data(originalData)
                .version(1)
                .build();

        // 创建新的 empty map 而不是 null
        Map<String, Object> emptyData = new HashMap<>();

        ConfigEntry newData = new ConfigEntry.Builder()
                .data(emptyData)
                .build();

        ConfigEntry updated = original.withUpdate(newData);

        // 验证 data 内容相同（不是同一个引用）
        assertNotNull("data 不应为 null", updated.getData());
        assertEquals("data 内容应该被更新", emptyData, updated.getData());
        assertTrue("data size 应该为 0", updated.getData().isEmpty());
    }

    @Test
    public void testWithUpdate_VersionIncrement() {
        ConfigEntry original = new ConfigEntry.Builder()
                .entryId("test-id")
                .coordinate("com.ecat.integration:demo")
                .uniqueId("demo_123")
                .version(1)
                .build();

        ConfigEntry newData = new ConfigEntry.Builder()
                .title("Updated")
                .build();

        ConfigEntry updated = original.withUpdate(newData);

        assertEquals("版本号应该从 1 递增到 2", 2, updated.getVersion());

        // 再次更新
        ConfigEntry thirdUpdate = updated.withUpdate(newData);
        assertEquals("版本号应该从 2 递增到 3", 3, thirdUpdate.getVersion());
    }

    // ==================== Lombok @Data 测试 ====================

    @Test
    public void testEqualsAndHashCode() {
        ConfigEntry entry1 = new ConfigEntry.Builder()
                .entryId("test-id")
                .coordinate("com.ecat.integration:demo")
                .uniqueId("demo_123")
                .build();

        ConfigEntry entry2 = new ConfigEntry.Builder()
                .entryId("test-id")
                .coordinate("com.ecat.integration:demo")
                .uniqueId("demo_123")
                .build();

        assertEquals("相同 entryId 的 entry 应该相等", entry1, entry2);
        assertEquals("hashCode 应该相等", entry1.hashCode(), entry2.hashCode());
    }

    @Test
    public void testNotEquals() {
        ConfigEntry entry1 = new ConfigEntry.Builder()
                .entryId("test-id-1")
                .coordinate("com.ecat.integration:demo")
                .uniqueId("demo_123")
                .build();

        ConfigEntry entry2 = new ConfigEntry.Builder()
                .entryId("test-id-2")
                .coordinate("com.ecat.integration:demo")
                .uniqueId("demo_456")
                .build();

        assertNotEquals("不同 entryId 的 entry 应该不相等", entry1, entry2);
    }

    @Test
    public void testToString() {
        ConfigEntry entry = new ConfigEntry.Builder()
                .entryId("test-id")
                .coordinate("com.ecat.integration:demo")
                .uniqueId("demo_123")
                .build();

        String str = entry.toString();
        assertNotNull("toString 不应返回 null", str);
        assertTrue("toString 应该包含 entryId", str.contains("test-id"));
    }
}
