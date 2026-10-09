/*
 * Copyright (c) 2026 ECAT Team
 */

package com.ecat.core.Upgrade;

import com.alibaba.fastjson2.JSONObject;
import com.ecat.core.Upgrade.QueuePlan.PlanState;
import com.ecat.core.Utils.YamlAtomicFileWriter;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * QueuePlan 单测:manifest/state 反序列化与损坏负向(§7 用例面)。
 *
 * <p>锁定风险:双文件成对装载的键位契约(与队列写入器 wire 同名)、解析失败/词表外
 * 状态/planId 三方不一致的显形(不猜不补)、state 写面原子替换落盘可读回、
 * from==to 乱序拒绝、db 子标记增量合并、dump 域清单 DONE 过滤。</p>
 *
 * @author coffee
 */
public class QueuePlanTest {

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private Path planDir;

    @Before
    public void setUp() throws IOException {
        // 目录名=planId(装载时三方一致性校验的同一契约)
        planDir = temp.getRoot().toPath().resolve("plan-id-1");
        Files.createDirectories(planDir);
    }

    // ==================== 装载与契约键位 ====================

    /** 双文件成对装载:wire 键位逐字段对齐队列写入器契约 */
    @Test
    public void loadPairedFiles_wireFieldsAligned() throws IOException {
        writeBytes(planDir.resolve("manifest.json"),
                validManifest().toJSONString().getBytes(StandardCharsets.UTF_8));
        writeStateFile(planDir, "PENDING", "2026-10-01T08:30:00Z");

        QueuePlan plan = QueuePlan.load(planDir);

        assertEquals("plan-id-1", plan.getPlanId());
        assertEquals(PlanState.PENDING, plan.getState());
        assertEquals(0, plan.getAttempts());
        assertNull(plan.getError());
        assertEquals("2026-10-01T08:30:00Z", plan.getEnqueuedAt());
        assertEquals(1, plan.getManifest().getItems().size());
        QueuePlan.Item item = plan.getManifest().getItems().get(0);
        assertEquals("com.ecat", item.getGroupId());
        assertEquals("app", item.getArtifactId());
        assertEquals("com.ecat:app", item.getCoordinate());
        assertEquals("1.0.0", item.getInstalledVersion());
        assertEquals("2.0.0", item.getTargetVersion());
        assertEquals("^4.0.0", item.getRequiresCore());
        assertEquals(1, item.getFiles().size());
        assertEquals("aabbcc", item.getFiles().get(0).getSha256());
    }

    /**
     * 旧清单容忍:已退役的 db_conventions 键(空数组/带块两形态)装载零报错、
     * 不进模型——过渡期旧清单仍在盘,解析器必须未知键忽略(禁报错)。
     */
    @Test
    public void loadToleratesLegacyDbConventionsKey() throws IOException {
        JSONObject legacy = validManifest();
        JSONObject block = new JSONObject(new LinkedHashMap<String, Object>());
        block.put("domain", "adm");
        block.put("engine", "pg");
        block.put("mechanism", "flyway");
        block.put("historyTable", "flyway_schema_history_adm");
        block.put("dumpPolicy", "full");
        List<Object> blocks = new ArrayList<>();
        blocks.add(block);
        legacy.getJSONArray("items").getJSONObject(0).put("db_conventions", blocks);
        writeBytes(planDir.resolve("manifest.json"), legacy.toJSONString().getBytes(StandardCharsets.UTF_8));
        writeStateFile(planDir, "PENDING", "2026-10-01T08:30:00Z");

        QueuePlan plan = QueuePlan.load(planDir);
        assertEquals("带遗留 db_conventions 块的旧清单照常装载",
                "com.ecat:app", plan.getManifest().getItems().get(0).getCoordinate());
    }

    /** manifest 缺必填键/缺 files/type 非 UPGRADE → 解析期 UpgradeStateException(残缺请求不进管道) */
    @Test
    public void loadManifestMissingRequiredKeys_rejected() throws IOException {
        JSONObject manifest = validManifest();
        manifest.remove("planId");
        writeBytes(planDir.resolve("manifest.json"), manifest.toJSONString().getBytes(StandardCharsets.UTF_8));
        writeStateFile(planDir, "PENDING", "2026-10-01T08:30:00Z");
        assertLoadRejected("缺 planId 应拒绝");

        manifest = validManifest();
        manifest.getJSONArray("items").getJSONObject(0).remove("files");
        writeBytes(planDir.resolve("manifest.json"), manifest.toJSONString().getBytes(StandardCharsets.UTF_8));
        assertLoadRejected("缺 files 应拒绝");

        manifest = validManifest();
        manifest.put("type", "INSTALL");
        writeBytes(planDir.resolve("manifest.json"), manifest.toJSONString().getBytes(StandardCharsets.UTF_8));
        assertLoadRejected("type 非 UPGRADE 应拒绝");
    }

    /** state.yml 坏 YAML/词表外状态/缺 enqueuedAt → UpgradeStateException */
    @Test
    public void loadStateCorrupted_rejected() throws IOException {
        writeBytes(planDir.resolve("manifest.json"),
                validManifest().toJSONString().getBytes(StandardCharsets.UTF_8));
        writeBytes(planDir.resolve("state.yml"), "state: [unclosed\n".getBytes(StandardCharsets.UTF_8));
        assertLoadRejected("坏 YAML 应拒绝");

        writeStateFile(planDir, "SOMETHING_ELSE", "2026-10-01T08:30:00Z");
        assertLoadRejected("词表外状态应拒绝");

        writeBytes(planDir.resolve("state.yml"),
                "schemaVersion: 1\nplanId: plan-id-1\nstate: PENDING\n".getBytes(StandardCharsets.UTF_8));
        assertLoadRejected("缺 enqueuedAt(排序依据)应拒绝");
    }

    /** planId 三方不一致(目录≠manifest≠state)=状态乱序损坏显形 */
    @Test
    public void loadPlanIdMismatch_rejected() throws IOException {
        writeBytes(planDir.resolve("manifest.json"),
                validManifest().toJSONString().getBytes(StandardCharsets.UTF_8));
        writeStateFile(planDir, "PENDING", "2026-10-01T08:30:00Z");
        Path renamed = planDir.resolveSibling("plan-other");
        Files.move(planDir, renamed);
        try {
            QueuePlan.load(renamed);
            fail("planId 三方不一致应拒绝");
        } catch (UpgradeStateException expected) {
            assertTrue(expected.getMessage().contains("三方不一致"));
        }
    }

    // ==================== state 写面 ====================

    /** 状态迁移:history 追加+落盘可读回(重新装载断言持久化);from==to=乱序拒绝 */
    @Test
    public void stateTransition_persistsAndGuardsOrder() throws IOException {
        writeBytes(planDir.resolve("manifest.json"),
                validManifest().toJSONString().getBytes(StandardCharsets.UTF_8));
        writeStateFile(planDir, "PENDING", "2026-10-01T08:30:00Z");
        QueuePlan plan = QueuePlan.load(planDir);

        plan.stateTransition(PlanState.VERIFYING);
        plan.recordError("验证失败原因");
        plan.stateTransition(PlanState.PREREQ_FAILED);

        QueuePlan reloaded = QueuePlan.load(planDir);
        assertEquals(PlanState.PREREQ_FAILED, reloaded.getState());
        assertEquals("验证失败原因", reloaded.getError());
        assertEquals(2, transitionCount(reloaded));
        assertEquals("PENDING", firstTransitionFrom(reloaded));

        try {
            reloaded.stateTransition(PlanState.PREREQ_FAILED);
            fail("from==to 应拒绝");
        } catch (UpgradeStateException expected) {
            assertTrue(expected.getMessage().contains("from==to"));
        }
    }

    /** db 子标记读取容差:前代在盘 state.yml 的 db 块照常读出(写入面已退役,读取不撕裂) */
    @Test
    public void dbMarkers_legacyStateYmlStillReadable() throws IOException {
        writeBytes(planDir.resolve("manifest.json"),
                validManifest().toJSONString().getBytes(StandardCharsets.UTF_8));
        Map<String, Object> legacyState = new LinkedHashMap<>();
        legacyState.put("schemaVersion", 1);
        legacyState.put("planId", "plan-id-1");
        legacyState.put("state", PlanState.DB_UPGRADING.name());
        legacyState.put("enqueuedAt", "2026-10-01T08:30:00Z");
        legacyState.put("attempts", 0);
        Map<String, Object> db = new LinkedHashMap<>();
        db.put("adm", "DONE");
        db.put("media", "PENDING");
        legacyState.put("db", db);
        new YamlAtomicFileWriter().write(legacyState, planDir.resolve("state.yml").toFile());

        QueuePlan reloaded = QueuePlan.load(planDir);
        assertEquals("DONE", reloaded.getDbMarkers().get("adm"));
        assertEquals("PENDING", reloaded.getDbMarkers().get("media"));
        assertEquals(2, reloaded.getDbMarkers().size());
    }

    /** LOADING 重试计数落盘(D24 重试可观察) */
    @Test
    public void attempts_incrementAndPersist() throws IOException {
        writeBytes(planDir.resolve("manifest.json"),
                validManifest().toJSONString().getBytes(StandardCharsets.UTF_8));
        writeStateFile(planDir, "LOADING", "2026-10-01T08:30:00Z");
        QueuePlan plan = QueuePlan.load(planDir);

        plan.incrementAttempts();

        assertEquals(1, QueuePlan.load(planDir).getAttempts());
    }

    // ==================== 损坏修复写与快照面 ====================

    /** rewriteMinimal:最小文档重建,error 携带,装载读回 */
    @Test
    public void rewriteMinimal_rebuildsReadableState() throws IOException {
        writeBytes(planDir.resolve("manifest.json"),
                validManifest().toJSONString().getBytes(StandardCharsets.UTF_8));
        writeStateFile(planDir, "LOADING", "2026-10-01T08:30:00Z");

        QueuePlan repaired = QueuePlan.rewriteMinimal(planDir, "plan-id-1",
                PlanState.ROLLING_BACK, "队列状态损坏,保守回滚: 撕裂");

        assertEquals(PlanState.ROLLING_BACK, repaired.getState());
        QueuePlan reloaded = QueuePlan.load(planDir);
        assertEquals(PlanState.ROLLING_BACK, reloaded.getState());
        assertTrue(reloaded.getError().contains("保守回滚"));
    }

    /** 快照成立标志(快照 manifest.json 在) */
    @Test
    public void snapshotFacets_establishedFlag() throws IOException {
        writeBytes(planDir.resolve("manifest.json"),
                validManifest().toJSONString().getBytes(StandardCharsets.UTF_8));
        writeStateFile(planDir, "LOADING", "2026-10-01T08:30:00Z");
        QueuePlan plan = QueuePlan.load(planDir);
        Path snapshotRoot = temp.newFolder("backups").toPath();

        assertFalse("无快照目录=未成立", plan.isSnapshotEstablished(snapshotRoot));

        Files.createDirectories(snapshotRoot.resolve("plan-id-1"));
        Files.write(snapshotRoot.resolve("plan-id-1").resolve("manifest.json"),
                "{\"planId\":\"plan-id-1\"}".getBytes(StandardCharsets.UTF_8));

        assertTrue("快照 manifest.json 在=成立", plan.isSnapshotEstablished(snapshotRoot));
    }

    // ==================== 夹具 ====================

    private JSONObject validManifest() {
        JSONObject manifest = new JSONObject(new LinkedHashMap<>());
        manifest.put("schemaVersion", 1);
        manifest.put("planId", "plan-id-1");
        manifest.put("type", "UPGRADE");
        manifest.put("createdAt", "2026-10-01T08:29:00Z");
        JSONObject file = new JSONObject(new LinkedHashMap<>());
        file.put("kind", "jar");
        file.put("group_id", "com.ecat");
        file.put("artifact_id", "app");
        file.put("version", "2.0.0");
        file.put("filename", "app-2.0.0.jar");
        file.put("sha256", "aabbcc");
        JSONObject item = new JSONObject(new LinkedHashMap<>());
        item.put("group_id", "com.ecat");
        item.put("artifact_id", "app");
        item.put("installed_version", "1.0.0");
        item.put("target_version", "2.0.0");
        item.put("requires_core", "^4.0.0");
        item.put("files", new ArrayList<>(Arrays.asList(file)));
        // db_conventions 已退役:夹具保留该遗留键=全部装载用例持续演练「未知键忽略」容忍面
        item.put("db_conventions", new ArrayList<>());
        manifest.put("items", new ArrayList<>(Arrays.asList(item)));
        return manifest;
    }

    private void writeStateFile(Path dir, String state, String enqueuedAt) throws IOException {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("schemaVersion", 1);
        doc.put("planId", "plan-id-1");
        doc.put("state", state);
        doc.put("enqueuedAt", enqueuedAt);
        doc.put("attempts", 0);
        new YamlAtomicFileWriter().write(doc, dir.resolve("state.yml").toFile());
    }

    private void writeBytes(Path file, byte[] bytes) throws IOException {
        Files.write(file, bytes);
    }

    private void assertLoadRejected(String message) {
        try {
            QueuePlan.load(planDir);
            fail(message);
        } catch (UpgradeStateException expected) {
            // 显形即断言目标
        }
    }

    private int transitionCount(QueuePlan plan) throws IOException {
        Map<String, Object> doc = new Yaml()
                .load(Files.newInputStream(plan.getPlanDir().resolve("state.yml")));
        List<Map<String, Object>> history = (List<Map<String, Object>>) doc.get("history");
        return history == null ? 0 : history.size();
    }

    private String firstTransitionFrom(QueuePlan plan) throws IOException {
        Map<String, Object> doc = new Yaml()
                .load(Files.newInputStream(plan.getPlanDir().resolve("state.yml")));
        List<Map<String, Object>> history = (List<Map<String, Object>>) doc.get("history");
        return (String) history.get(0).get("from");
    }
}
