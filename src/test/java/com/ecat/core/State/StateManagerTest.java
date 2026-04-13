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

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

import com.ecat.core.ConfigEntry.ConfigEntry;
import com.ecat.core.Device.DeviceBase;
import com.ecat.core.EcatCore;

/**
 * StateManager 单元测试
 *
 * 测试属性状态持久化的核心功能：
 * - 保存/加载不同类型属性（数值、文本、布尔）
 * - 恢复属性状态（含单位校验和默认值兜底）
 * - DB 文件路径结构
 * - 设备删除/关闭/关闭时的文件管理
 */
public class StateManagerTest {

    private static final String TEST_DIR = ".ecat-data/test-states/";
    private StateManager stateManager;
    private ScheduledExecutorService scheduler;

    @Before
    public void setUp() {
        scheduler = Executors.newScheduledThreadPool(1);
        stateManager = new StateManager(TEST_DIR, scheduler);
    }

    @After
    public void tearDown() {
        if (stateManager != null) {
            stateManager.shutdown();
        }
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        deleteRecursive(new File(TEST_DIR));
    }

    private void deleteRecursive(File file) {
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursive(child);
                }
            }
        }
        file.delete();
    }

    /**
     * 创建测试设备，使用反射注入 StateManager 到 EcatCore
     */
    private DeviceBase createTestDevice(String deviceId, String coordinate) {
        Map<String, Object> data = new HashMap<>();
        data.put("name", "test-device");

        ConfigEntry entry = new ConfigEntry.Builder()
            .entryId(deviceId)
            .coordinate(coordinate)
            .uniqueId("test_" + deviceId)
            .data(data)
            .build();

        DeviceBase device = new DeviceBase(entry) {
            @Override public void init() {}
            @Override public void start() {}
            @Override public void stop() {}
            @Override public void release() {}
        };

        // 注入带有 stateManager 的 mock EcatCore
        EcatCore mockCore = new EcatCore();
        try {
            java.lang.reflect.Field smField = EcatCore.class.getDeclaredField("stateManager");
            smField.setAccessible(true);
            smField.set(mockCore, stateManager);
        } catch (Exception e) {
            throw new RuntimeException("Failed to inject StateManager into EcatCore", e);
        }
        device.load(mockCore);

        return device;
    }

    // ========== saveState / loadState 基本功能测试 ==========

    @Test
    public void testSaveAndLoad_numericAttribute() {
        DeviceBase device = createTestDevice("device-001", "com.test:integration-test");
        NumericAttribute attr = new NumericAttribute("temperature",
            AttributeClass.TEMPERATURE, null, null, 1, false, false);
        attr.setPersistable(true);
        device.setAttribute(attr);

        attr.updateValue(25.5, AttributeStatus.NORMAL);
        stateManager.commitAll();

        PersistedState loaded = stateManager.loadState(device, "temperature");
        assertNotNull("Should load persisted state", loaded);
        assertEquals(25.5, ((Number) loaded.value).doubleValue(), 0.001);
        assertEquals(AttributeStatus.NORMAL.getId(), loaded.statusCode);
    }

    @Test
    public void testSaveAndLoad_textAttribute() {
        DeviceBase device = createTestDevice("device-002", "com.test:integration-test");
        TextAttribute attr = new TextAttribute("status_text",
            AttributeClass.TEXT, null, null, false);
        attr.setPersistable(true);
        device.setAttribute(attr);

        attr.updateValue("running");
        stateManager.commitAll();

        PersistedState loaded = stateManager.loadState(device, "status_text");
        assertNotNull("Should load persisted state", loaded);
        assertEquals("running", loaded.value);
    }

    @Test
    public void testSaveAndLoad_binaryAttribute() {
        DeviceBase device = createTestDevice("device-003", "com.test:integration-test");
        BinaryAttribute attr = new BinaryAttribute("switch",
            AttributeClass.STATUS, false);
        attr.setPersistable(true);
        device.setAttribute(attr);

        attr.turnOn();
        stateManager.commitAll();

        PersistedState loaded = stateManager.loadState(device, "switch");
        assertNotNull("Should load persisted state", loaded);
        assertEquals(true, loaded.value);
    }

    @Test
    public void testLoadState_noData_returnsNull() {
        DeviceBase device = createTestDevice("device-004", "com.test:integration-test");
        PersistedState loaded = stateManager.loadState(device, "nonexistent");
        assertNull("Should return null for nonexistent attr", loaded);
    }

    // ========== restoreAttributeState 恢复测试 ==========

    @Test
    public void testRestoreAttributeState_withPersistedData() {
        DeviceBase device = createTestDevice("device-005", "com.test:integration-test");

        // 第一次: 创建并保存
        NumericAttribute attr1 = new NumericAttribute("temp",
            AttributeClass.TEMPERATURE, null, null, 1, false, false);
        attr1.setPersistable(true);
        device.setAttribute(attr1);
        attr1.updateValue(30.0, AttributeStatus.NORMAL);
        stateManager.commitAll();

        // 从 attrs map 中移除
        device.getAttrs().remove("temp");

        // 第二次: 创建新的 attr 并通过 restoreAttributeState 恢复
        NumericAttribute attr2 = new NumericAttribute("temp",
            AttributeClass.TEMPERATURE, null, null, 1, false, false);
        attr2.setPersistable(true);
        stateManager.restoreAttributeState(device, attr2);

        assertEquals(Double.valueOf(30.0), attr2.getValue());
        assertEquals(AttributeStatus.NORMAL, attr2.getStatus());
    }

    @Test
    public void testRestoreAttributeState_withDefaultValue() {
        DeviceBase device = createTestDevice("device-006", "com.test:integration-test");

        NumericAttribute attr = new NumericAttribute("temp",
            AttributeClass.TEMPERATURE, null, null, 1, false, false);
        attr.setPersistable(true);
        attr.setDefaultValue(0.0);
        stateManager.restoreAttributeState(device, attr);

        assertEquals(Double.valueOf(0.0), attr.getValue());
        assertEquals(AttributeStatus.NORMAL, attr.getStatus());
    }

    @Test
    public void testRestoreAttributeState_noData_noDefault() {
        DeviceBase device = createTestDevice("device-007", "com.test:integration-test");

        NumericAttribute attr = new NumericAttribute("temp",
            AttributeClass.TEMPERATURE, null, null, 1, false, false);
        attr.setPersistable(true);
        stateManager.restoreAttributeState(device, attr);

        assertNull(attr.getValue());
        assertEquals(AttributeStatus.EMPTY, attr.getStatus());
    }

    // ========== DB 文件路径结构测试 ==========

    @Test
    public void testDbFilePath_structure() {
        DeviceBase device = createTestDevice("abc-123", "com.ecat:integration-sailhero");
        NumericAttribute attr = new NumericAttribute("so2",
            AttributeClass.SO2, null, null, 1, false, false);
        attr.setPersistable(true);
        device.setAttribute(attr);
        attr.updateValue(10.0);
        stateManager.commitAll();

        File dbFile = new File(TEST_DIR + "com.ecat/integration-sailhero/abc-123.db");
        assertTrue("DB file should exist at correct path", dbFile.exists());
    }

    // ========== removeDevice / closeDevice 测试 ==========

    @Test
    public void testRemoveDevice_deletesFile() {
        DeviceBase device = createTestDevice("device-rm", "com.test:integration-test");
        NumericAttribute attr = new NumericAttribute("temp",
            AttributeClass.TEMPERATURE, null, null, 1, false, false);
        attr.setPersistable(true);
        device.setAttribute(attr);
        attr.updateValue(25.0);
        stateManager.commitAll();

        stateManager.removeDevice(device);

        File dbFile = new File(TEST_DIR + "com.test/integration-test/device-rm.db");
        assertFalse("DB file should be deleted after removeDevice", dbFile.exists());
    }

    @Test
    public void testCloseDevice_keepsFile() {
        DeviceBase device = createTestDevice("device-close", "com.test:integration-test");
        NumericAttribute attr = new NumericAttribute("temp",
            AttributeClass.TEMPERATURE, null, null, 1, false, false);
        attr.setPersistable(true);
        device.setAttribute(attr);
        attr.updateValue(25.0);
        stateManager.commitAll();

        stateManager.closeDevice("device-close");

        File dbFile = new File(TEST_DIR + "com.test/integration-test/device-close.db");
        assertTrue("DB file should still exist after close", dbFile.exists());
    }

    // ========== shutdown commit 测试 ==========

    @Test
    public void testShutdown_commitsPendingWrites() {
        DeviceBase device = createTestDevice("device-shutdown", "com.test:integration-test");
        NumericAttribute attr = new NumericAttribute("temp",
            AttributeClass.TEMPERATURE, null, null, 1, false, false);
        attr.setPersistable(true);
        device.setAttribute(attr);
        attr.updateValue(99.9);
        // 注意: 不调用 commitAll，直接 shutdown 应该也能持久化

        stateManager.shutdown();
        stateManager = null; // 防止 @After 重复 shutdown

        // 重新打开并验证数据已持久化
        StateManager sm2 = new StateManager(TEST_DIR, scheduler);
        PersistedState loaded = sm2.loadState(device, "temp");
        sm2.shutdown();
        stateManager = null;

        assertNotNull("Should load data after reopen", loaded);
        assertEquals(99.9, ((Number) loaded.value).doubleValue(), 0.001);
    }

    // ========== 无持久化模式测试 ==========

    @Test
    public void testDefaultConstructor_noPersistence() {
        StateManager noOp = new StateManager();
        // 不应抛异常，所有操作静默返回
        DeviceBase device = createTestDevice("device-noop", "com.test:integration-test");
        NumericAttribute attr = new NumericAttribute("temp",
            AttributeClass.TEMPERATURE, null, null, 1, false, false);
        attr.setPersistable(true);
        device.setAttribute(attr);

        noOp.saveState(device, attr);
        assertNull(noOp.loadState(device, "temp"));
        noOp.shutdown();
    }
}
