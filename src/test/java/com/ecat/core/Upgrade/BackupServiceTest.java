/*
 * Copyright (c) 2026 ECAT Team
 */

package com.ecat.core.Upgrade;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * BackupService 快照/恢复单测(临时目录+user.home 重定向+受控时钟,零真实进程)。
 *
 * <p>锁定风险:快照-恢复对称(捕获了必还原,恢复面缺树还原即红)、篡改检出执法面
 * (拒绝用坏快照回滚,yml 未被坏快照覆盖)、硬链接+跨 FS 回退、步骤时间戳严格递增
 * (受控时钟,不赌墙钟精度)、保留清理(RUNNING 恒保/剪除入台账)、首装 absent 形态。</p>
 *
 * @author coffee
 */
public class BackupServiceTest {

    private Path tempDir;
    private Path integrationsYml;
    private Path configEntriesRoot;
    private Path integrationsItemDir;
    private Path snapshotRoot;
    private Path coreProgramFixture;
    private String originalUserHome;
    private TestClock clock;
    private InstallationLedgerStore ledgerStore;

    /** 注入失败形态的链接缝(false=回退拷贝路径) */
    private boolean failHardLink;

    @Before
    public void setUp() throws IOException {
        tempDir = Files.createTempDirectory("backup-service-test");
        integrationsYml = tempDir.resolve("core").resolve("integrations.yml");
        configEntriesRoot = tempDir.resolve("core").resolve("config_entries");
        integrationsItemDir = tempDir.resolve("integrations");
        snapshotRoot = tempDir.resolve("backups");
        coreProgramFixture = tempDir.resolve("ecat-core-fixture.jar");
        Files.createDirectories(integrationsYml.getParent());
        writeYml(integrationsYml, "com.ecat", "app", "1.0.0");
        writeEntryFile(configEntriesRoot, "com.ecat", "app", "entry-1",
                "key: before\n");
        writeItemFile(integrationsItemDir, "app", "enabled: true\n");
        Files.write(coreProgramFixture, "CORE-JAR-BYTES".getBytes(StandardCharsets.UTF_8));
        writeFixtureJar("com.ecat", "app", "1.0.0");

        originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", tempDir.toFile().getAbsolutePath());

        failHardLink = false;
        clock = new TestClock(Instant.parse("2026-10-01T08:30:00Z"));
        ledgerStore = new InstallationLedgerStore(tempDir.resolve("installations"),
                snapshotRoot, clock);
    }

    @After
    public void tearDown() throws IOException {
        System.setProperty("user.home", originalUserHome);
        DbDumpExecutor.deleteRecursively(tempDir);
    }

    private BackupService service() {
        DbDumpExecutor dumpExecutor = new DbDumpExecutor((command, pgEnv) -> {
            // dump 桩:落非空产物即可(执行器只校验产物存在+非空)
            for (String arg : command) {
                if (arg.startsWith("--file=")) {
                    Files.write(java.nio.file.Paths.get(arg.substring("--file=".length())),
                            "PGDMP".getBytes(StandardCharsets.UTF_8));
                }
            }
            return new DbDumpExecutor.ExecResult(0, "");
        }, k -> "stub-pg-env", tempDir.resolve("storage"));
        return new BackupService(snapshotRoot, integrationsYml, configEntriesRoot,
                integrationsItemDir, dumpExecutor, ledgerStore) {
            @Override
            protected Path coreProgramLocation() {
                return coreProgramFixture;
            }

            @Override
            protected void createHardLink(Path source, Path target) throws IOException {
                if (failHardLink) {
                    throw new IOException("跨文件系统模拟");
                }
                super.createHardLink(source, target);
            }
        };
    }

    private InstallationLedgerStore.CoordinateTrack track(String coordinate) {
        return new InstallationLedgerStore.CoordinateTrack(coordinate, "1.0.0", "2.0.0");
    }

    // ==================== T2 升级快照全流程 ====================

    @Test
    public void t2_upgrade_snapshot_steps_have_timestamps_and_manifest_verifies() throws IOException {
        String planId = "plan-upg";
        ledgerStore.create(planId, InstallationLedgerStore.PlanType.UPGRADE,
                snapshotRoot.resolve(planId).toString(), Collections.singletonList(track("com.ecat:app")),
                null);
        List<DbDumpSpec> dbBlocks = Collections.singletonList(
                DbDumpSpec.builder().domain("his").engine("pg").mechanism("flyway")
                        .historyTable("flyway_schema_history").dumpPolicy("table-family:his_data")
                        .groupId("com.ecat").artifactId("integration-his").build());
        service().snapshotForUpgrade(planId, dbBlocks);

        InstallationLedgerStore.Ledger ledger = ledgerStore.read(planId).get();
        assertTrue("快照各阶段步骤在册", ledger.getSteps().size() >= 5);
        for (InstallationLedgerStore.Step step : ledger.getSteps()) {
            assertNotNull("startedAt 非空: " + step.getName(), step.getStartedAt());
            assertNotNull("finishedAt 非空: " + step.getName(), step.getFinishedAt());
            assertTrue("startedAt<finishedAt(受控时钟严格判): " + step.getName(),
                    step.getStartedAt().isBefore(step.getFinishedAt()));
        }
        // jar 面/配置面/程序面产物在位
        assertTrue(Files.isRegularFile(snapshotRoot.resolve(planId).resolve("jars")
                .resolve("com/ecat/app/1.0.0/app-1.0.0.jar")));
        assertTrue(Files.isRegularFile(snapshotRoot.resolve(planId).resolve("core")
                .resolve("ecat-core-fixture.jar")));
        assertTrue(Files.isRegularFile(snapshotRoot.resolve(planId).resolve("config")
                .resolve("integrations.yml")));
        assertTrue(Files.isRegularFile(snapshotRoot.resolve(planId).resolve("db")
                .resolve("his").resolve("dump.pgdump")));
        // manifest 逐条 sha256 对账过(restoreProgramFace 内部走同一对账,能还原=对账成立)
        service().restoreProgramFace(planId);
    }

    @Test
    public void dump_state_marker_records_domain_done() throws IOException {
        String planId = "plan-dumpstate";
        ledgerStore.create(planId, InstallationLedgerStore.PlanType.UPGRADE,
                snapshotRoot.resolve(planId).toString(), null, null);
        List<DbDumpSpec> dbBlocks = Arrays.asList(
                DbDumpSpec.builder().domain("his").engine("pg").dumpPolicy("table-family:his_data")
                        .groupId("com.ecat").artifactId("a").build(),
                DbDumpSpec.builder().domain("adm").engine("pg").dumpPolicy("table-family:adm_data_sample")
                        .groupId("com.ecat").artifactId("b").build());
        service().snapshotForUpgrade(planId, dbBlocks);

        Yaml yaml = new Yaml();
        Map<String, Object> state = yaml.load(new String(Files.readAllBytes(
                snapshotRoot.resolve(planId).resolve("db").resolve("dump-state.yml")),
                StandardCharsets.UTF_8));
        Map<String, Object> dump = (Map<String, Object>) state.get("dump");
        assertEquals("DONE", dump.get("his"));
        assertEquals("DONE", dump.get("adm"));
    }

    // ==================== T1b 快照-恢复对称 ====================

    @Test
    public void t1b_restored_config_tree_equals_snapshot_bytes_not_post_snapshot_drift() throws IOException {
        String planId = "plan-sym";
        ledgerStore.create(planId, InstallationLedgerStore.PlanType.UPGRADE,
                snapshotRoot.resolve(planId).toString(), null, null);
        service().snapshotForUpgrade(planId, null);

        // 升级窗写面模拟:T-2-2 entry 迁移持久化改写 config_entries + 集成单件 yml
        writeEntryFile(configEntriesRoot, "com.ecat", "app", "entry-1", "key: migrated\n");
        writeItemFile(integrationsItemDir, "app", "enabled: false\n");

        service().restoreProgramFace(planId);

        assertEquals("entry 文件==快照字节(恢复面若无树还原此断言即红)",
                "key: before\n",
                new String(Files.readAllBytes(entryFile(configEntriesRoot, "com.ecat", "app", "entry-1")),
                        StandardCharsets.UTF_8));
        assertEquals("集成单件 yml==快照字节",
                "enabled: true\n",
                new String(Files.readAllBytes(itemFile(integrationsItemDir, "app")),
                        StandardCharsets.UTF_8));
        assertEquals("integrations.yml==快照字节", integrationsYmlContent("com.ecat", "app", "1.0.0"),
                new String(Files.readAllBytes(integrationsYml), StandardCharsets.UTF_8));
    }

    // ==================== T5 硬链接+跨 FS 回退 ====================

    @Test
    public void t5_same_fs_snapshot_jar_is_same_file_as_repo_jar() throws IOException {
        writeFixtureJar("com.ecat", "app", "1.0.0");
        String planId = "plan-link";
        ledgerStore.create(planId, InstallationLedgerStore.PlanType.UPGRADE,
                snapshotRoot.resolve(planId).toString(), null, null);
        service().snapshotForUpgrade(planId, null);

        Path snapshotJar = snapshotRoot.resolve(planId).resolve("jars")
                .resolve("com/ecat/app/1.0.0/app-1.0.0.jar");
        Path repoJar = fixtureJar("com.ecat", "app", "1.0.0");
        assertTrue("同盘硬链接=isSameFile", Files.isSameFile(snapshotJar, repoJar));
    }

    @Test
    public void t5_link_failure_falls_back_to_copy_with_same_content() throws IOException {
        writeFixtureJar("com.ecat", "app", "1.0.0");
        failHardLink = true;
        String planId = "plan-copy";
        ledgerStore.create(planId, InstallationLedgerStore.PlanType.UPGRADE,
                snapshotRoot.resolve(planId).toString(), null, null);
        service().snapshotForUpgrade(planId, null);

        Path snapshotJar = snapshotRoot.resolve(planId).resolve("jars")
                .resolve("com/ecat/app/1.0.0/app-1.0.0.jar");
        Path repoJar = fixtureJar("com.ecat", "app", "1.0.0");
        assertFalse("回退拷贝非同 inode", Files.isSameFile(snapshotJar, repoJar));
        assertEquals("回退拷贝内容一致", "FIXTURE-JAR",
                new String(Files.readAllBytes(snapshotJar), StandardCharsets.UTF_8));
    }

    @Test
    public void jar_missing_fails_closed_with_clear_message() throws IOException {
        Files.delete(fixtureJar("com.ecat", "app", "1.0.0"));
        String planId = "plan-missing";
        ledgerStore.create(planId, InstallationLedgerStore.PlanType.UPGRADE,
                snapshotRoot.resolve(planId).toString(), null, null);
        try {
            service().snapshotForUpgrade(planId, null);
            fail("jar 缺失必须 fail-closed(快照必须完整)");
        } catch (IllegalStateException e) {
            assertTrue("消息含坐标与拒绝语义: " + e.getMessage(),
                    e.getMessage().contains("com.ecat:app:1.0.0") && e.getMessage().contains("拒绝进入变更"));
        }
        assertFalse("无半快照:失败目录已清理", Files.exists(snapshotRoot.resolve(planId)));
    }

    // ==================== T4 篡改检出(执行器侧执法面) ====================

    @Test
    public void t4_tampered_snapshot_rejected_and_live_yml_untouched() throws IOException {
        String planId = "plan-tamper";
        ledgerStore.create(planId, InstallationLedgerStore.PlanType.INSTALL,
                snapshotRoot.resolve(planId).toString(), Collections.singletonList(track("com.ecat:app")),
                null);
        service().snapshotForInstall(planId, Collections.singletonList(
                new BackupService.AddedFile("com.ecat:app", "1.0.0",
                        fixtureJar("com.ecat", "app", "1.0.0").toString(), "deadbeef")));

        // 篡改快照内 yml 一字节
        Path snapshotYml = snapshotRoot.resolve(planId).resolve("config").resolve("integrations.yml");
        Files.write(snapshotYml, (integrationsYmlContent("com.ecat", "app", "1.0.0") + "# tampered\n")
                .getBytes(StandardCharsets.UTF_8));

        try {
            service().restoreProgramFace(planId);
            fail("坏快照必须被检出");
        } catch (IllegalStateException e) {
            assertTrue("消息含快照校验失败: " + e.getMessage(), e.getMessage().contains("快照校验失败"));
        }
        assertEquals("live yml 未被坏快照覆盖(有牙)",
                integrationsYmlContent("com.ecat", "app", "1.0.0"),
                new String(Files.readAllBytes(integrationsYml), StandardCharsets.UTF_8));
    }

    // ==================== 安装快照:三面+absent 形态 ====================

    @Test
    public void install_snapshot_captures_config_tree_and_added_files_manifest() throws IOException {
        String planId = "plan-inst";
        ledgerStore.create(planId, InstallationLedgerStore.PlanType.INSTALL,
                snapshotRoot.resolve(planId).toString(), Collections.singletonList(track("com.ecat:app")),
                null);
        BackupService.AddedFile added = new BackupService.AddedFile("com.ecat:app", "1.0.0",
                fixtureJar("com.ecat", "app", "1.0.0").toString(), "deadbeef");
        service().snapshotForInstall(planId, Collections.singletonList(added));

        assertEquals("added-files.json wire 形态读回一致", Collections.singletonList(added),
                service().readAddedFiles(planId));
        assertEquals("yml 字节快照", integrationsYmlContent("com.ecat", "app", "1.0.0"),
                new String(Files.readAllBytes(snapshotRoot.resolve(planId).resolve("config")
                        .resolve("integrations.yml")), StandardCharsets.UTF_8));
    }

    @Test
    public void install_absent_form_restores_to_no_yml() throws IOException {
        Files.delete(integrationsYml);
        String planId = "plan-absent";
        ledgerStore.create(planId, InstallationLedgerStore.PlanType.INSTALL,
                snapshotRoot.resolve(planId).toString(), null, null);
        service().snapshotForInstall(planId, new ArrayList<>());

        assertTrue("absent 标记在快照中",
                Files.isRegularFile(snapshotRoot.resolve(planId).resolve("config")
                        .resolve("integrations.yml.absent")));

        // 模拟安装写了 yml → 按标记还原回「无 yml」
        writeYml(integrationsYml, "com.ecat", "app", "1.0.0");
        service().restoreProgramFace(planId);
        assertFalse("首装还原=删 yml 回无 yml 形态", Files.exists(integrationsYml));
    }

    @Test
    public void restore_without_manifest_refused() throws IOException {
        String planId = "plan-nomanifest";
        Path planDir = snapshotRoot.resolve(planId);
        Files.createDirectories(planDir.resolve("config"));
        try {
            service().restoreProgramFace(planId);
            fail("无 manifest(快照未成立)必须拒绝恢复");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("快照未成立"));
        }
    }

    // ==================== T8 保留清理 ====================

    @Test
    public void t8_retention_prunes_oldest_records_into_newest_and_keeps_running() throws IOException {
        List<String> planIds = Arrays.asList("p1", "p2", "p3", "p4");
        for (String planId : planIds) {
            clock.advanceSeconds(60);
            ledgerStore.create(planId, InstallationLedgerStore.PlanType.UPGRADE,
                    snapshotRoot.resolve(planId).toString(), null, null);
            // 终态化(非 RUNNING 才参与剪除)
            ledgerStore.finish(planId, InstallationLedgerStore.PlanResult.SUCCESS);
            Files.createDirectories(snapshotRoot.resolve(planId));
            Files.write(snapshotRoot.resolve(planId).resolve("marker"), new byte[1]);
        }
        // p0=更老但 RUNNING:恒保留
        clock.advanceSeconds(60);
        ledgerStore.create("p0-running", InstallationLedgerStore.PlanType.UPGRADE,
                snapshotRoot.resolve("p0-running").toString(), null, null);
        Files.createDirectories(snapshotRoot.resolve("p0-running"));

        ledgerStore.pruneRetention();

        assertFalse("最旧 p1 已删", Files.exists(snapshotRoot.resolve("p1")));
        assertTrue("p2 在保留窗内", Files.exists(snapshotRoot.resolve("p2")));
        assertTrue("RUNNING 恒保留", Files.exists(snapshotRoot.resolve("p0-running")));
        List<String> pruned = ledgerStore.read("p4").get().getRetentionPruned();
        assertEquals("剪除名单入最新台账", Collections.singletonList("p1"), pruned);
    }

    @Test
    public void t8_retention_config_respected() throws IOException {
        String original = System.getProperty("ecat.upgrade.backup.retention");
        System.setProperty("ecat.upgrade.backup.retention", "1");
        try {
            for (String planId : Arrays.asList("q1", "q2")) {
                clock.advanceSeconds(60);
                ledgerStore.create(planId, InstallationLedgerStore.PlanType.UPGRADE,
                        snapshotRoot.resolve(planId).toString(), null, null);
                ledgerStore.finish(planId, InstallationLedgerStore.PlanResult.SUCCESS);
                Files.createDirectories(snapshotRoot.resolve(planId));
            }
            ledgerStore.pruneRetention();
            assertFalse("保留 1 个:更旧的 q1 已剪", Files.exists(snapshotRoot.resolve("q1")));
            assertTrue(Files.exists(snapshotRoot.resolve("q2")));
        } finally {
            if (original == null) {
                System.clearProperty("ecat.upgrade.backup.retention");
            } else {
                System.setProperty("ecat.upgrade.backup.retention", original);
            }
        }
    }

    // ==================== 台账读写语义 ====================

    @Test
    public void ledger_read_missing_returns_empty_and_record_step_requires_ledger() {
        Optional<InstallationLedgerStore.Ledger> absent = ledgerStore.read("no-such");
        assertFalse(absent.isPresent());
        try {
            ledgerStore.recordStep("no-such", "snapshot", InstallationLedgerStore.StepState.DONE, null);
            fail("无台账记步骤=调用方时序缺陷,必须显形");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("无台账记录"));
        }
    }

    @Test
    public void ledger_record_step_idempotent_and_double_finish_rejected() {
        String planId = "plan-ledger";
        ledgerStore.create(planId, InstallationLedgerStore.PlanType.INSTALL,
                "/tmp/snap", Collections.singletonList(track("c1")), null);
        ledgerStore.recordStep(planId, "commit", InstallationLedgerStore.StepState.PENDING, null);
        ledgerStore.recordStep(planId, "rollback", InstallationLedgerStore.StepState.PENDING, null);
        ledgerStore.recordStep(planId, "commit", InstallationLedgerStore.StepState.DONE, null);
        ledgerStore.recordStep(planId, "commit", InstallationLedgerStore.StepState.DONE, null); // 幂等重写
        InstallationLedgerStore.Ledger ledger = ledgerStore.read(planId).get();
        assertEquals("无重复行", 2, ledger.getSteps().size());
        assertTrue("commit 步时间戳严格递增(受控时钟)",
                stepOfLedger(ledger, "commit").getStartedAt()
                        .isBefore(stepOfLedger(ledger, "commit").getFinishedAt()));
        assertNull("纯 PENDING 步骤无 finishedAt", stepOfLedger(ledger, "rollback").getFinishedAt());

        ledgerStore.finish(planId, InstallationLedgerStore.PlanResult.SUCCESS);
        try {
            ledgerStore.finish(planId, InstallationLedgerStore.PlanResult.FAILED);
            fail("已终态再 finish=状态机违例,必须拒绝");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("已终态"));
        }
    }

    @Test
    public void ledger_cancelled_terminal_state_round_trips() {
        // 停队作废留台账:_CANCELLED 落账路径(队列计划由停队方写,词汇与状态机一致)
        String planId = "plan-cancelled";
        ledgerStore.create(planId, InstallationLedgerStore.PlanType.UPGRADE,
                "/tmp/snap", null, null);
        ledgerStore.finish(planId, InstallationLedgerStore.PlanResult.CANCELLED);
        InstallationLedgerStore.Ledger ledger = ledgerStore.read(planId).get();
        assertEquals(InstallationLedgerStore.PlanResult.CANCELLED, ledger.getResult());
        assertNotNull("终态时间戳", ledger.getFinishedAt());
        assertTrue("CANCELLED=终态", ledger.isTerminal());
    }

    private InstallationLedgerStore.Step stepOfLedger(InstallationLedgerStore.Ledger ledger, String name) {
        for (InstallationLedgerStore.Step step : ledger.getSteps()) {
            if (step.getName().equals(name)) {
                return step;
            }
        }
        throw new AssertionError("步骤不存在: " + name);
    }

    @Test
    public void ledger_degradations_round_trip() {
        String planId = "plan-deg";
        ledgerStore.create(planId, InstallationLedgerStore.PlanType.INSTALL, "/tmp/snap",
                Collections.singletonList(track("com.ecat:a")),
                Collections.singletonList(new InstallationLedgerStore.Degradation(
                        "com.ecat:b", "带 DB 约定不当场生效(his 域)", "queue-plan-1")));
        InstallationLedgerStore.Degradation degradation =
                ledgerStore.read(planId).get().getDegradations().get(0);
        assertEquals("com.ecat:b", degradation.getCoordinate());
        assertEquals("带 DB 约定不当场生效(his 域)", degradation.getReason());
        assertEquals("queue-plan-1", degradation.getQueuePlanId());
    }

    // ==================== 夹具 ====================

    private void writeYml(Path yml, String groupId, String artifactId, String version) throws IOException {
        Files.createDirectories(yml.getParent());
        Files.write(yml, integrationsYmlContent(groupId, artifactId, version)
                .getBytes(StandardCharsets.UTF_8));
    }

    private String integrationsYmlContent(String groupId, String artifactId, String version) {
        return "version: \"1.0\"\nintegrations:\n  " + artifactId + ":\n"
                + "    groupId: " + groupId + "\n"
                + "    artifactId: " + artifactId + "\n"
                + "    version: \"" + version + "\"\n"
                + "    enabled: true\n";
    }

    private Path entryFile(Path root, String groupId, String artifactId, String entryId) {
        return root.resolve(groupId).resolve(artifactId).resolve(entryId + ".yml");
    }

    private void writeEntryFile(Path root, String groupId, String artifactId,
                                String entryId, String content) throws IOException {
        Path file = entryFile(root, groupId, artifactId, entryId);
        Files.createDirectories(file.getParent());
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    }

    private Path itemFile(Path dir, String name) {
        return dir.resolve(name + ".yml");
    }

    private void writeItemFile(Path dir, String name, String content) throws IOException {
        Path file = itemFile(dir, name);
        Files.createDirectories(file.getParent());
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
    }

    private Path fixtureJar(String groupId, String artifactId, String version) {
        return tempDir.resolve(".m2").resolve("repository").resolve(groupId.replace('.', '/'))
                .resolve(artifactId).resolve(version).resolve(artifactId + "-" + version + ".jar");
    }

    private void writeFixtureJar(String groupId, String artifactId, String version) throws IOException {
        Path jar = fixtureJar(groupId, artifactId, version);
        Files.createDirectories(jar.getParent());
        Files.write(jar, "FIXTURE-JAR".getBytes(StandardCharsets.UTF_8));
    }

    /** 受控时钟:每读自增 1s——同线程内时间戳严格递增,startedAt<finishedAt 确定性成立 */
    private static class TestClock extends Clock {
        private Instant current;

        TestClock(Instant start) {
            this.current = start;
        }

        void advanceSeconds(long seconds) {
            current = current.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            current = current.plusSeconds(1);
            return current;
        }
    }
}
