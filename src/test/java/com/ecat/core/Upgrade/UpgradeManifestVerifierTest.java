/*
 * Copyright (c) 2026 ECAT Team
 */

package com.ecat.core.Upgrade;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ecat.core.Upgrade.UpgradeManifestVerifier.VerifyResult;
import com.ecat.core.Version.CoreVersionsTestAccess;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * 升级清单复验门单测:四门全量收集+负向自检(§7 用例面)。
 *
 * <p>锁定风险:产源值域门(resolve/local 扩认,local-hack 拒)、缺一文件即拒
 * (FILE_MISSING,放回转绿=门有牙非恒红)、sha256 全链篡改即现形、requires_core
 * 有效负样本双例(^3.1.0×4.0.0 拒 / ×3.2.0 过)、manifest JSON 往返零丢失
 * (含 null installed_version)、旧清单 db_conventions 遗留键容忍(解析不报错、
 * 复验不拒绝——db: 块退役后过渡期旧清单仍在盘)。</p>
 *
 * <p>夹具:user.home 重定向临时目录(门2/门4 文件在位检查走临时 ~/.m2 布局);
 * 文件内容即普通字节(复验门只做存在性+哈希面,不开 jar);sha256 期望值=夹具
 * 写入后实测。core 版本经 CoreVersionsTestAccess 版本缝注值。零网络零 sleep。</p>
 *
 * @author coffee
 */
public class UpgradeManifestVerifierTest {

    private static final String ACTUAL_CORE = "4.0.0";

    private final UpgradeManifestVerifier verifier = new UpgradeManifestVerifier();

    private Path tempRoot;
    private String originalUserHome;
    private Supplier<String> originalVersionSource;

    @Before
    public void setUp() throws IOException {
        tempRoot = Files.createTempDirectory("manifest-verifier-test");
        originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", tempRoot.toString());
        originalVersionSource = CoreVersionsTestAccess.currentSource();
        CoreVersionsTestAccess.injectSource(() -> ACTUAL_CORE);
    }

    @After
    public void tearDown() {
        System.setProperty("user.home", originalUserHome);
        CoreVersionsTestAccess.injectSource(originalVersionSource);
    }

    // ==================== T1 manifest 往返零丢失+遗留键容忍 ====================

    /** build→JSON→parse→逐字段深等(含 null installed_version/蛇形键位) */
    @Test
    public void manifestJsonRoundtripLossless() {
        UpgradeManifest manifest = greenManifest();
        manifest.getItems().get(0).setInstalledVersion(null);

        String json = JSON.toJSONString(manifest,
                com.alibaba.fastjson2.JSONWriter.Feature.WriteMapNullValue);
        UpgradeManifest parsed = JSON.parseObject(json, UpgradeManifest.class);

        assertEquals(manifest.getPlanId(), parsed.getPlanId());
        assertEquals(manifest.getCreatedAt(), parsed.getCreatedAt());
        assertEquals(manifest.getCoreVersion(), parsed.getCoreVersion());
        assertEquals(manifest.getSource(), parsed.getSource());
        assertEquals(1, parsed.getItems().size());
        UpgradeManifest.UpgradeItem source = manifest.getItems().get(0);
        UpgradeManifest.UpgradeItem restored = parsed.getItems().get(0);
        assertEquals(source.getGroupId(), restored.getGroupId());
        assertEquals(source.getArtifactId(), restored.getArtifactId());
        assertEquals(source.getInstalledVersion(), restored.getInstalledVersion());
        assertEquals(source.getTargetVersion(), restored.getTargetVersion());
        assertEquals(source.getRequiresCore(), restored.getRequiresCore());
        assertEquals(source.getFiles().size(), restored.getFiles().size());
        assertEquals(source.getFiles().get(0).getSha256(), restored.getFiles().get(0).getSha256());
        assertEquals(manifest.getResolutionMap().size(), parsed.getResolutionMap().size());
        assertEquals(manifest.getResolutionMap().get(0).getSource(),
                parsed.getResolutionMap().get(0).getSource());

        // 蛇形键位锁:@JSONField 显式映射生效(wire 契约键名,非 camelCase 漂移)
        assertTrue("wire 键位须为 group_id", json.contains("\"group_id\""));
        assertTrue("wire 键位须为 resolution_map", json.contains("\"resolution_map\""));
        assertFalse("db_conventions 已退役,新清单不再产出该键", json.contains("db_conventions"));
    }

    /**
     * 旧清单容忍:残留 db_conventions 键(空数组/带块两形态)解析零报错、
     * 复验绿——db: 块退役后过渡期旧清单仍在盘,禁因遗留键拒绝(禁报错)。
     */
    @Test
    public void legacyDbConventionsKey_tolerated_parseAndVerifyGreen() throws IOException {
        JSONObject item = baseItemJson();
        item.put("db_conventions", new ArrayList<Map<String, Object>>());
        assertLegacyKeyTolerated(item);

        JSONObject block = new JSONObject();
        block.put("domain", "adm");
        block.put("engine", "pg");
        block.put("mechanism", "flyway");
        block.put("historyTable", "flyway_schema_history_adm");
        block.put("dumpPolicy", "full");
        List<Object> blocks = new ArrayList<>();
        blocks.add(block);
        JSONObject withBlocks = baseItemJson();
        withBlocks.put("db_conventions", blocks);
        assertLegacyKeyTolerated(withBlocks);
    }

    /** 单条目裸 JSON 清单(手动铸,与 greenManifest 解耦;文件经 fileEntry 落盘+实测哈希) */
    private JSONObject baseItemJson() {
        UpgradeManifest.FileEntry entry = fileEntry("com.ecat", "modbus", "2.0.0",
                "modbus-2.0.0.jar", "modbus-jar-bytes".getBytes(StandardCharsets.UTF_8));
        JSONObject item = new JSONObject();
        item.put("group_id", entry.getGroupId());
        item.put("artifact_id", entry.getArtifactId());
        item.put("installed_version", "1.0.0");
        item.put("target_version", entry.getVersion());
        item.put("requires_core", ">=4.0.0");
        JSONObject file = new JSONObject();
        file.put("kind", entry.getKind());
        file.put("group_id", entry.getGroupId());
        file.put("artifact_id", entry.getArtifactId());
        file.put("version", entry.getVersion());
        file.put("filename", entry.getFilename());
        file.put("sha256", entry.getSha256());
        item.put("files", new ArrayList<>(Arrays.asList(file)));
        return item;
    }

    /** 带 db_conventions 键的 item → 完整 manifest JSON → 解析+复验必须绿 */
    private void assertLegacyKeyTolerated(JSONObject item) throws IOException {
        JSONObject manifest = new JSONObject();
        manifest.put("planId", "plan-legacy");
        manifest.put("type", "UPGRADE");
        manifest.put("createdAt", "2026-10-04T08:30:00Z");
        manifest.put("coreVersion", ACTUAL_CORE);
        manifest.put("source", UpgradeManifestVerifier.SOURCE_RESOLVE);
        manifest.put("items", new ArrayList<>(Arrays.asList(item)));
        manifest.put("resolution_map", new ArrayList<>(Arrays.asList(
                resolutionEntry("com.ecat", "modbus", "2.0.0", "resolved"))));

        UpgradeManifest parsed = JSON.parseObject(manifest.toJSONString(), UpgradeManifest.class);
        VerifyResult result = verifier.verify(parsed);
        assertTrue("遗留 db_conventions 键不得拒绝清单: " + result.getFailures(), result.isPassed());
    }

    // ==================== T2 清单缺一文件即拒(负向+自检) ====================

    /** 两文件删其一→FILE_MISSING+passed=false;放回→转绿(门有牙非恒红) */
    @Test
    public void missingFileRejectedAndRestoredPasses() throws IOException {
        UpgradeManifest manifest = greenManifest();
        UpgradeManifest.FileEntry second = fileEntry("com.ecat", "modbus-extra", "2.0.0",
                "modbus-extra-2.0.0.jar", "extra-content-bytes".getBytes(StandardCharsets.UTF_8));
        manifest.getItems().get(0).getFiles().add(second);

        Path secondPath = m2Path("com.ecat", "modbus-extra", "2.0.0", "modbus-extra-2.0.0.jar");
        Files.deleteIfExists(secondPath);
        // 第一个文件在位,仅第二个缺失
        VerifyResult missing = verifier.verify(manifest);
        assertFalse("缺一文件必须拒绝(不装半个)", missing.isPassed());
        assertEquals(1, codesOf(missing, "FILE_MISSING").size());

        // 负向自检:文件放回→转绿
        Files.createDirectories(secondPath.getParent());
        Files.write(secondPath, "extra-content-bytes".getBytes(StandardCharsets.UTF_8));
        VerifyResult restored = verifier.verify(manifest);
        assertTrue("文件放回后必须转绿", restored.isPassed());
    }

    // ==================== T3 sha256 全链校验 ====================

    /** 夹具文件篡改 1 字节→SHA256_MISMATCH,明细含期望/实际哈希 */
    @Test
    public void tamperedFileYieldsSha256MismatchWithDetails() throws IOException {
        UpgradeManifest manifest = greenManifest();
        Path jarPath = m2Path("com.ecat", "modbus", "2.0.0", "modbus-2.0.0.jar");
        byte[] tampered = "modbus-jar-bytes-tampered!".getBytes(StandardCharsets.UTF_8);
        Files.write(jarPath, tampered);

        VerifyResult result = verifier.verify(manifest);
        assertFalse(result.isPassed());
        List<VerifyFailure> mismatches = codesOf(result, "SHA256_MISMATCH");
        assertEquals(1, mismatches.size());
        String expected = manifest.getItems().get(0).getFiles().get(0).getSha256();
        String actual = SnapshotFiles.sha256Hex(jarPath);
        assertTrue("明细须含期望哈希: " + mismatches.get(0).getDetail(),
                mismatches.get(0).getDetail().contains(expected));
        assertTrue("明细须含实际哈希: " + mismatches.get(0).getDetail(),
                mismatches.get(0).getDetail().contains(actual));
    }

    // ==================== 门1 完整性 ====================

    /** T8:伪造产源 local-hack→MANIFEST_MALFORMED(防手编分支有牙) */
    @Test
    public void forgedSourceRejected() {
        UpgradeManifest manifest = greenManifest();
        manifest.setSource("local-hack");

        VerifyResult result = verifier.verify(manifest);
        assertFalse(result.isPassed());
        assertEquals(1, codesOf(result, "MANIFEST_MALFORMED").size());
        assertTrue(codesOf(result, "MANIFEST_MALFORMED").get(0).getDetail().contains("source"));
    }

    /** rev-T-1-7-T-1-6 扩认面:本地产源 local 合法过门1(非豁免——其余门照验) */
    @Test
    public void localSourceAcceptedAsRealProvenance() {
        UpgradeManifest manifest = greenManifest();
        manifest.setSource(UpgradeManifestVerifier.SOURCE_LOCAL);

        VerifyResult result = verifier.verify(manifest);
        assertTrue("local 产源=真实产源语义,门1 必须放行: " + result.getFailures(),
                result.isPassed());
    }

    /** 壳字段缺失逐项报:source 缺失→MANIFEST_MALFORMED */
    @Test
    public void missingSourceFieldRejected() {
        UpgradeManifest manifest = greenManifest();
        manifest.setSource(null);

        VerifyResult result = verifier.verify(manifest);
        assertFalse(result.isPassed());
        assertTrue(codesOf(result, "MANIFEST_MALFORMED").stream()
                .anyMatch(f -> f.getDetail().contains("source")));
    }

    /** sha256 词形非法(非 64 位十六进制)→MANIFEST_MALFORMED */
    @Test
    public void malformedSha256Rejected() {
        UpgradeManifest manifest = greenManifest();
        manifest.getItems().get(0).getFiles().get(0).setSha256("abc123");

        VerifyResult result = verifier.verify(manifest);
        assertFalse(result.isPassed());
        assertEquals(1, codesOf(result, "MANIFEST_MALFORMED").size());
        assertTrue(codesOf(result, "MANIFEST_MALFORMED").get(0).getDetail().contains("sha256"));
    }

    /** items 空→MANIFEST_MALFORMED(空计划不入队,manifest 恒非空) */
    @Test
    public void emptyItemsRejected() {
        UpgradeManifest manifest = greenManifest();
        manifest.setItems(new ArrayList<>());

        VerifyResult result = verifier.verify(manifest);
        assertFalse(result.isPassed());
        assertEquals(1, codesOf(result, "MANIFEST_MALFORMED").size());
    }

    // ==================== T5 requires_core 门(有效负样本) ====================

    /** 约束 ^3.1.0×core 4.0.0→REQUIRES_CORE_UNSATISFIED;×core 3.2.0→过(Wave3 双例) */
    @Test
    public void requiresCoreNegativeSampleBothDirections() {
        UpgradeManifest red = greenManifest();
        red.getItems().get(0).setRequiresCore("^3.1.0");
        VerifyResult redResult = verifier.verify(red);
        assertFalse("^3.1.0 对 4.0.0 必须拒(4.0.0 不在 [3.1.0,4.0.0))", redResult.isPassed());
        List<VerifyFailure> unsat = codesOf(redResult, "REQUIRES_CORE_UNSATISFIED");
        assertEquals(1, unsat.size());

        CoreVersionsTestAccess.injectSource(() -> "3.2.0");
        try {
            UpgradeManifest green = greenManifest();
            green.getItems().get(0).setRequiresCore("^3.1.0");
            VerifyResult greenResult = verifier.verify(green);
            assertTrue("3.2.0∈[3.1.0,4.0.0) 必须过: " + greenResult.getFailures(),
                    greenResult.isPassed());
        } finally {
            CoreVersionsTestAccess.injectSource(() -> ACTUAL_CORE);
        }
    }

    /** requires_core 缺失=null→REQUIRES_CORE_UNSATISFIED(fail-closed 与运行期门同语义) */
    @Test
    public void missingRequiresCoreRejected() {
        UpgradeManifest manifest = greenManifest();
        manifest.getItems().get(0).setRequiresCore(null);

        VerifyResult result = verifier.verify(manifest);
        assertFalse(result.isPassed());
        assertEquals(1, codesOf(result, "REQUIRES_CORE_UNSATISFIED").size());
    }

    // ==================== 门4 依赖齐全 ====================

    /** resolved_dependencies 有终态且 jar 在位→过;终态 jar 缺→DEPENDENCY_INCOMPLETE */
    @Test
    public void dependencyTerminalJarMissingRejected() throws IOException {
        UpgradeManifest manifest = greenManifest();
        manifest.getItems().get(0).getResolvedDependencies().add(
                resolvedDependency("com.ecat", "serial", "2.2.0"));
        manifest.getResolutionMap().add(
                resolutionEntry("com.ecat", "serial", "2.2.0", "resolved"));
        // 终态坐标 jar 不在位(仅主 jar 在位)
        VerifyResult missing = verifier.verify(manifest);
        assertFalse(missing.isPassed());
        assertEquals(1, codesOf(missing, "DEPENDENCY_INCOMPLETE").size());

        // 依赖件放回→转绿
        Path depJar = m2Path("com.ecat", "serial", "2.2.0", "serial-2.2.0.jar");
        Files.createDirectories(depJar.getParent());
        Files.write(depJar, "serial-jar-bytes".getBytes(StandardCharsets.UTF_8));
        assertTrue(verifier.verify(manifest).isPassed());
    }

    /** 依赖无 resolution_map 终态条目→DEPENDENCY_INCOMPLETE(闭包不全,缺一即半个) */
    @Test
    public void dependencyWithoutTerminalEntryRejected() {
        UpgradeManifest manifest = greenManifest();
        manifest.getItems().get(0).getResolvedDependencies().add(
                resolvedDependency("com.ecat", "ghost", "9.9.9"));

        VerifyResult result = verifier.verify(manifest);
        assertFalse(result.isPassed());
        List<VerifyFailure> incomplete = codesOf(result, "DEPENDENCY_INCOMPLETE");
        assertEquals(1, incomplete.size());
        assertTrue(incomplete.get(0).getDetail().contains("resolution_map"));
    }

    // ==================== type 硬门接缝(writer 侧单侧闭合) ====================

    /**
     * 云端形态 manifest 经 QueuePlan 硬门放行:type 键由本类壳默认携带(writer 侧单侧
     * 闭合,零动 T-1-5 读面)——manifest.json+state.yml 成对落盘后 QueuePlan.load
     * 不因 type 缺失被 UpgradeStateException 拒,云端产物端到端可达重启窗。
     */
    @Test
    public void cloudFormManifestPassesQueuePlanTypeHardGate() throws IOException {
        Path planDir = tempRoot.resolve("plan-green");
        Files.createDirectories(planDir);
        UpgradeManifest manifest = greenManifest();
        Files.write(planDir.resolve("manifest.json"), JSON.toJSONString(manifest,
                com.alibaba.fastjson2.JSONWriter.Feature.WriteMapNullValue)
                .getBytes(StandardCharsets.UTF_8));
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("schemaVersion", 1);
        state.put("planId", manifest.getPlanId());
        state.put("state", "PENDING");
        state.put("enqueuedAt", manifest.getCreatedAt());
        state.put("attempts", 0);
        new com.ecat.core.Utils.YamlAtomicFileWriter().write(state,
                planDir.resolve("state.yml").toFile());

        com.ecat.core.Upgrade.QueuePlan plan = com.ecat.core.Upgrade.QueuePlan.load(planDir);
        assertEquals("云端产物经 type 硬门放行", manifest.getPlanId(), plan.getPlanId());
        assertEquals(1, plan.itemsOrEmpty().size());
    }

    // ==================== 夹具 ====================

    /** 全绿 manifest:单条目单文件,文件已落临时 ~/.m2 且 sha256 实测一致 */
    private UpgradeManifest greenManifest() {
        byte[] content = "modbus-jar-bytes".getBytes(StandardCharsets.UTF_8);
        UpgradeManifest.FileEntry jar = fileEntry("com.ecat", "modbus", "2.0.0",
                "modbus-2.0.0.jar", content);

        UpgradeManifest.UpgradeItem item = new UpgradeManifest.UpgradeItem();
        item.setGroupId("com.ecat");
        item.setArtifactId("modbus");
        item.setInstalledVersion("1.0.0");
        item.setTargetVersion("2.0.0");
        item.setRequiresCore(">=4.0.0");
        item.getFiles().add(jar);

        UpgradeManifest manifest = new UpgradeManifest();
        manifest.setPlanId("plan-green");
        manifest.setCreatedAt("2026-10-04T08:30:00Z");
        manifest.setCoreVersion(ACTUAL_CORE);
        manifest.setSource(UpgradeManifestVerifier.SOURCE_RESOLVE);
        manifest.getItems().add(item);
        manifest.getResolutionMap().add(resolutionEntry("com.ecat", "modbus", "2.0.0", "resolved"));
        return manifest;
    }

    /** 文件条目+落盘临时 ~/.m2(sha256=内容实测) */
    private UpgradeManifest.FileEntry fileEntry(String groupId, String artifactId,
                                                String version, String filename, byte[] content) {
        try {
            Path path = m2Path(groupId, artifactId, version, filename);
            Files.createDirectories(path.getParent());
            Files.write(path, content);
        } catch (IOException e) {
            fail("夹具落盘失败: " + e.getMessage());
        }
        UpgradeManifest.FileEntry entry = new UpgradeManifest.FileEntry();
        entry.setKind("jar");
        entry.setGroupId(groupId);
        entry.setArtifactId(artifactId);
        entry.setVersion(version);
        entry.setFilename(filename);
        entry.setSha256(SnapshotFiles.sha256Hex(
                m2Path(groupId, artifactId, version, filename)));
        return entry;
    }

    private static UpgradeManifest.ResolutionEntry resolutionEntry(
            String groupId, String artifactId, String version, String source) {
        UpgradeManifest.ResolutionEntry entry = new UpgradeManifest.ResolutionEntry();
        entry.setGroupId(groupId);
        entry.setArtifactId(artifactId);
        entry.setVersion(version);
        entry.setSource(source);
        return entry;
    }

    private static UpgradeManifest.ResolvedDependency resolvedDependency(
            String groupId, String artifactId, String version) {
        UpgradeManifest.ResolvedDependency dep = new UpgradeManifest.ResolvedDependency();
        dep.setGroupId(groupId);
        dep.setArtifactId(artifactId);
        dep.setVersion(version);
        dep.setConstraint("^2.0.0");
        return dep;
    }

    private static Path m2Path(String groupId, String artifactId, String version, String filename) {
        String repo = System.getProperty("user.home") + "/.m2/repository";
        return Paths.get(repo, groupId.replace('.', '/'), artifactId, version, filename);
    }

    private static List<VerifyFailure> codesOf(VerifyResult result, String code) {
        return result.getFailures().stream()
                .filter(f -> code.equals(f.getCode()))
                .collect(Collectors.toList());
    }
}
