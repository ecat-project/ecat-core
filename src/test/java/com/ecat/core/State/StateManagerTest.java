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
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

import com.ecat.core.ConfigEntry.ConfigEntry;
import com.ecat.core.Device.DeviceBase;
import com.ecat.core.EcatCore;

/**
 * StateManager 单元测试
 *
 * 测试属性状态持久化的核心功能：
 * - 保存/加载不同类型属性（数值、文本、布尔）
 * - 恢复属性状态（含默认值兜底）
 * - 单库布局（{baseDir}/states.db）与库级 meta（storeSchemaVersion 等）
 * - 单轴 schema 版本策略（降级旁置 / 迁移链推进）
 * - 复合键隔离（跨设备同 attributeId 互不串扰）
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
        // ready gate：publicState 在 device 未就绪时挂起发布（含 commit 点持久化）。
        // 本测试聚焦 persist 落盘机制，需 device 已就绪，故 setup 即 markReady。
        device.markReady();

        return device;
    }

    /** 关库后直接以 MapDB 打开落盘文件读取 meta/states（释放文件锁供断言段复用） */
    private DB reopenStoreFile() {
        return DBMaker.fileDB(new File(TEST_DIR, "states.db"))
            .transactionEnable().make();
    }

    private static ConcurrentMap<String, String> openStringMap(DB db, String name) {
        return db.hashMap(name)
            .keySerializer(Serializer.STRING)
            .valueSerializer(Serializer.STRING)
            .createOrOpen();
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
        attr.publicState(); // 新设计：持久化在 commit 点 publicState 触发（写 WAL），commitAll 负责刷盘
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
        attr.publicState(); // 新设计：持久化在 commit 点 publicState 触发（写 WAL），commitAll 负责刷盘
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
        attr.publicState(); // 新设计：持久化在 commit 点 publicState 触发（写 WAL），commitAll 负责刷盘
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
        attr1.publicState(); // 新设计：持久化在 commit 点 publicState 触发（写 WAL），commitAll 负责刷盘
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

    // ========== 单库布局与库级 meta 测试 ==========

    /**
     * 单库布局：首次 saveState 后 {baseDir}/states.db 存在（不再有 per-device 子目录树，
     * 全部设备共用一个库文件）。
     */
    @Test
    public void testDbFilePath_structure() {
        DeviceBase device = createTestDevice("abc-123", "com.ecat:integration-sailhero");
        NumericAttribute attr = new NumericAttribute("so2",
            AttributeClass.SO2, null, null, 1, false, false);
        attr.setPersistable(true);
        device.setAttribute(attr);
        attr.updateValue(10.0);
        attr.publicState(); // 持久化在 commit 点 publicState 触发：saveState 懒开单库（建文件+meta），commitAll 刷盘

        assertTrue("单状态库 states.db 应存在于 baseDir 根",
            new File(TEST_DIR, "states.db").exists());
        stateManager.commitAll();
    }

    /**
     * 新库建库：首次持久化后 meta 三键在位——storeSchemaVersion 是单轴版本策略的判代依据，
     * 缺它无法区分新库/旧库/降级库。
     */
    @Test
    public void testOpenNewStore_writesMetaKeys() {
        DeviceBase device = createTestDevice("device-meta", "com.test:integration-test");
        NumericAttribute attr = new NumericAttribute("temp",
            AttributeClass.TEMPERATURE, null, null, 1, false, false);
        attr.setPersistable(true);
        device.setAttribute(attr);
        attr.updateValue(21.0, AttributeStatus.NORMAL);
        attr.publicState(); // saveState 懒开单库：新库应写入 meta 三键
        stateManager.commitAll();

        stateManager.shutdown();
        stateManager = null; // 防 @After 重复 shutdown

        DB db = reopenStoreFile();
        try {
            ConcurrentMap<String, String> meta = openStringMap(db, "meta");
            assertEquals("新库 storeSchemaVersion 应为当前版本", "1", meta.get("storeSchemaVersion"));
            assertNotNull("storeCreatedEpochMs 应在位", meta.get("storeCreatedEpochMs"));
            assertNotNull("coreVersion 诊断标注应在位", meta.get("coreVersion"));
        } finally {
            db.close();
        }
    }

    /**
     * 降级语义：meta 版本高于当前实现（程序降级运行）→ 整库改名旁置
     * （states.db.incompatible-时间戳，旧数据原样保留供检查）、重开新库照常读写。
     * 状态是可重建缓存，不因版本冲突阻塞运行；改名后文件存在即 WARN 路径生效的落盘证据。
     */
    @Test
    public void testDowngradeStore_renamedAside_freshStoreUsable() {
        // 手造 v99 库（直接 MapDB 建库写 meta 关库）
        DB old = DBMaker.fileDB(new File(TEST_DIR, "states.db"))
            .transactionEnable().make();
        openStringMap(old, "meta").put("storeSchemaVersion", "99");
        openStringMap(old, "states").put("legacy-device legacy-attr", "{\"value\":\"legacy\"}");
        old.commit();
        old.close();

        DeviceBase device = createTestDevice("device-dg", "com.test:integration-test");
        NumericAttribute attr = new NumericAttribute("temp",
            AttributeClass.TEMPERATURE, null, null, 1, false, false);
        attr.setPersistable(true);
        device.setAttribute(attr);
        attr.updateValue(7.5, AttributeStatus.NORMAL);
        attr.publicState(); // saveState 打开 v99 库 → 旁置 + 新库写入
        stateManager.commitAll();

        File[] asides = new File(TEST_DIR).listFiles(
            (dir, name) -> name.startsWith("states.db.incompatible-"));
        assertNotNull("baseDir 应可枚举", asides);
        assertEquals("应恰好旁置一个旧库文件", 1, asides.length);

        // 旁置库旧数据原样保留
        DB aside = DBMaker.fileDB(asides[0]).transactionEnable().make();
        try {
            assertEquals("旁置库应保留原记录",
                "{\"value\":\"legacy\"}",
                openStringMap(aside, "states").get("legacy-device legacy-attr"));
        } finally {
            aside.close();
        }

        // 新库可写可读
        PersistedState loaded = stateManager.loadState(device, "temp");
        assertNotNull("旁置后新库应可读", loaded);
        assertEquals(7.5, ((Number) loaded.value).doubleValue(), 0.001);
    }

    /**
     * 迁移链演练：注入 0→1 测试迁移 + 手造 v0 库 → 打开时 rewrite 执行、meta 推进到 1，
     * 且 rewrite+版本推进经 commit 原子落盘。生产默认空链，迁移机制本身只能靠注入演练
     * （禁真实秒拍等待，构造即确定性触发）。
     */
    @Test
    public void testMigrationChain_v0Rewritten_versionAdvanced() {
        // 手造 v0 库：meta=0 + 一条待迁移记录
        DB old = DBMaker.fileDB(new File(TEST_DIR, "states.db"))
            .transactionEnable().make();
        openStringMap(old, "meta").put("storeSchemaVersion", "0");
        openStringMap(old, "states").put("device-old temp", "v0-payload");
        old.commit();
        old.close();

        StateManager sm = new StateManager(TEST_DIR, null,
            Arrays.<StoreMigration>asList(new StoreMigration() {
                @Override public int fromVersion() { return 0; }
                @Override public int toVersion() { return 1; }
                @Override public void rewrite(ConcurrentMap<String, String> states) {
                    states.put("device-old temp", states.get("device-old temp") + "-migrated");
                }
            }));
        try {
            DeviceBase device = createTestDevice("device-old", "com.test:integration-test");
            // loadState 懒开库：触发 0→1 迁移（读不存在的键只借开库路径，不碰解析）
            assertNull(sm.loadState(device, "nonexistent"));
        } finally {
            sm.shutdown(); // 幂等；关闭释放文件锁供断言段重开
        }

        DB reopened = reopenStoreFile();
        try {
            assertEquals("迁移后 meta 应推进到当前版本",
                "1", openStringMap(reopened, "meta").get("storeSchemaVersion"));
            assertEquals("迁移 rewrite 应已改写旧记录",
                "v0-payload-migrated",
                openStringMap(reopened, "states").get("device-old temp"));
        } finally {
            reopened.close();
        }
    }

    /**
     * 复合键隔离：单库单 map 下 key 必须带 deviceId——两设备同 attributeId 互不串扰
     * （若 key 退化为仅 attributeId，后写会覆盖先写）。
     */
    @Test
    public void testCompositeKey_sameAttrId_acrossDevices_isolated() {
        DeviceBase d1 = createTestDevice("device-iso-1", "com.test:integration-test");
        DeviceBase d2 = createTestDevice("device-iso-2", "com.test:integration-test");
        NumericAttribute a1 = new NumericAttribute("shared_attr",
            AttributeClass.TEMPERATURE, null, null, 1, false, false);
        a1.setPersistable(true);
        d1.setAttribute(a1);
        NumericAttribute a2 = new NumericAttribute("shared_attr",
            AttributeClass.TEMPERATURE, null, null, 1, false, false);
        a2.setPersistable(true);
        d2.setAttribute(a2);

        a1.updateValue(11.0, AttributeStatus.NORMAL);
        a1.publicState();
        a2.updateValue(22.0, AttributeStatus.NORMAL);
        a2.publicState();
        stateManager.commitAll();

        PersistedState s1 = stateManager.loadState(d1, "shared_attr");
        PersistedState s2 = stateManager.loadState(d2, "shared_attr");
        assertNotNull("设备1 应读到自己的记录", s1);
        assertNotNull("设备2 应读到自己的记录", s2);
        assertEquals("设备1 应读回自己的值", 11.0, ((Number) s1.value).doubleValue(), 0.001);
        assertEquals("设备2 应读回自己的值", 22.0, ((Number) s2.value).doubleValue(), 0.001);
    }

    /**
     * removeDevice 硬删除（单库 = 复合键前缀清除）：devA 条目全无（多 attr）、devB 完整。
     * devB 的 id 以 devA 的 id 为前缀——裸 deviceId 前缀清除会误伤 devB，拼上 SEP 后不得
     * 误伤（恰好锁死该边界）。清除后即时持久：关库重开（从磁盘读）后 devA 仍干净——若无
     * 即时 commit，未提交删除会被 WAL 回放丢弃、已删条目复活，此断言锁死该语义。
     */
    @Test
    public void testRemoveDevice_prefixSweep_hardDeleteDurable() {
        DeviceBase dA = createTestDevice("device-del", "com.test:integration-test");
        DeviceBase dB = createTestDevice("device-del-extra", "com.test:integration-test");
        NumericAttribute aTemp = new NumericAttribute("temp",
            AttributeClass.TEMPERATURE, null, null, 1, false, false);
        aTemp.setPersistable(true);
        dA.setAttribute(aTemp);
        TextAttribute aNote = new TextAttribute("note", AttributeClass.TEXT, null, null, false);
        aNote.setPersistable(true);
        dA.setAttribute(aNote);
        NumericAttribute bTemp = new NumericAttribute("temp",
            AttributeClass.TEMPERATURE, null, null, 1, false, false);
        bTemp.setPersistable(true);
        dB.setAttribute(bTemp);

        aTemp.updateValue(11.0, AttributeStatus.NORMAL);
        aTemp.publicState();
        aNote.updateValue("note-v");
        aNote.publicState();
        bTemp.updateValue(22.0, AttributeStatus.NORMAL);
        bTemp.publicState();
        stateManager.commitAll();

        stateManager.removeDevice(dA);

        assertNull("devA temp 应被清除", stateManager.loadState(dA, "temp"));
        assertNull("devA note 应被清除（前缀清除覆盖该设备全部 attr）",
            stateManager.loadState(dA, "note"));
        PersistedState b = stateManager.loadState(dB, "temp");
        assertNotNull("devB 不应被误伤（id 前缀重叠设备）", b);
        assertEquals(22.0, ((Number) b.value).doubleValue(), 0.001);

        // 硬删除即时持久：关库重开（不经本进程内存）后仍干净
        stateManager.shutdown();
        stateManager = null;
        StateManager sm2 = new StateManager(TEST_DIR, scheduler);
        try {
            assertNull("重开库后 devA 条目不得复活（即时 commit 使删除已持久）",
                sm2.loadState(dA, "temp"));
            PersistedState b2 = sm2.loadState(dB, "temp");
            assertNotNull("重开库后 devB 应完整", b2);
            assertEquals(22.0, ((Number) b2.value).doubleValue(), 0.001);
        } finally {
            sm2.shutdown();
            stateManager = null;
        }
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
        attr.publicState(); // 新设计：持久化在 commit 点 publicState 触发（写 WAL）
        // 注意: 不调用 commitAll，直接 shutdown 应该也能把 WAL 刷盘持久化

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

    // ========== 生产构造（自有 IO 提交计时器） ==========

    /**
     * 生产构造自持 ecat-state-commit 单线程（每秒 MapDB commit 是文件 IO，禁入业务池/
     * 不占引擎车道，见 StateManager 构造注释）；shutdown 先停该计时器再关库。
     * 断言锚定本实例调度器（包内字段缝）——全部实例计时线程同名 ecat-state-commit-N，
     * 扫全 JVM 线程表会被其他实例残留线程误伤（bug-record-20260831-115342）。
     */
    @Test
    public void testProductionConstructor_selfCommitThreadLifecycle() throws Exception {
        StateManager sm = new StateManager(TEST_DIR);
        try {
            ScheduledExecutorService selfCommit = sm.selfCommitScheduler;
            assertNotNull("生产构造应创建自有 commit 调度器", selfCommit);

            // 具名线程自证：让本调度器执行任务回报线程名——确定性且锚定本实例
            Future<String> workerName = selfCommit.submit(() -> Thread.currentThread().getName());
            String name = workerName.get(5, TimeUnit.SECONDS);
            assertTrue("生产构造应启动具名 ecat-state-commit 计时线程，实际: " + name,
                name.startsWith("ecat-state-commit-"));

            sm.shutdown();
            assertTrue("shutdown 应停自有计时线程（graceful：在飞轮次完成后终止，不再发起新一轮 commitAll）",
                selfCommit.awaitTermination(5, TimeUnit.SECONDS));
            assertTrue(selfCommit.isTerminated());
        } finally {
            sm.shutdown(); // 幂等；失败路径不泄漏计时线程
        }
    }
}
