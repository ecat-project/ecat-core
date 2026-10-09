/*
 * Copyright (c) 2026 ECAT Team
 */

package com.ecat.core.Upgrade;

import com.alibaba.fastjson2.JSONObject;
import com.ecat.core.EcatCore;
import com.ecat.core.Integration.IntegrationBase;
import com.ecat.core.Integration.IntegrationManager;
import com.ecat.core.Integration.IntegrationRegistry;
import com.ecat.core.Upgrade.InstallationLedgerStore.Ledger;
import com.ecat.core.Upgrade.QueuePlan.PlanState;
import com.ecat.core.Utils.YamlAtomicFileWriter;
import com.ecat.core.Version.CoreVersionsTestAccess;

import java.util.Collections;
import java.util.LinkedHashSet;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Supplier;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * UpgradeOrchestrator 状态机单测:崩溃注入/回滚/多任务串行/人工入口(§7 用例矩阵)。
 *
 * <p>技法:临时 queueRoot/backups/installations 注入;真实 BackupService(夹具核程序缝)
 * + spy(可验证回滚调用面);IntegrationManager mock(内存配置仓+真实 yml 文件
 * 双写同步,终盘 yml 对账有据);崩溃注入=盘面真实推进到中点后改写 state 词(或撕裂文件),
 * 重建编排器实例重放——单测内的「重启」=新实例+同盘面,确定性零真实进程杀;全同步零 sleep。
 * 夹具 jar 用 JarOutputStream 现刻(user.home 重定向 ~/.m2,一次成型保 sha 稳定),
 * requires_core 经 CoreVersionsTestAccess 版本缝判定。</p>
 *
 * <p>负向自检纪律:损坏/乱序用例断言必进回滚/FAILED 而非续推;LOADING_FAILED 断言
 * 零回滚调用(D24 有牙);台账映射钉 RUNNING(误用 finish 即红)。</p>
 *
 * @author coffee
 */
public class UpgradeOrchestratorStateTest {

    private static final String ACTUAL_CORE = "4.0.0";
    private static final String COORD = "com.ecat:app";

    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    private Path root;
    private Path queueRoot;
    private Path snapshotRoot;
    private Path installationsRoot;
    private Path integrationsYml;
    private Path configEntriesRoot;
    private Path integrationsItemDir;
    private Path coreProgramFixture;
    private String originalUserHome;
    private Supplier<String> originalVersionSource;

    private EcatCore coreMock;
    private IntegrationManager managerMock;
    private IntegrationRegistry registryMock;
    private IntegrationBase loadedInstance;
    /** integrations.yml 内存权威仓(mock 读=深拷贝,写=落仓+真实 yml 文件同步) */
    private Map<String, Map<String, Object>> configStore;
    private InstallationLedgerStore ledgerStore;
    private BackupService backupService;
    private UpgradeOrchestrator orchestrator;

    @Before
    public void setUp() throws IOException {
        root = temp.newFolder("ecat-upgrade-test").toPath();
        queueRoot = root.resolve("upgrades");
        snapshotRoot = root.resolve("backups");
        installationsRoot = root.resolve("installations");
        integrationsYml = root.resolve("core").resolve("integrations.yml");
        configEntriesRoot = root.resolve("core").resolve("config_entries");
        integrationsItemDir = root.resolve("integrations");
        coreProgramFixture = root.resolve("ecat-core-fixture.jar");
        Files.createDirectories(integrationsYml.getParent());
        Files.write(coreProgramFixture, "CORE-JAR-BYTES".getBytes(StandardCharsets.UTF_8));

        originalUserHome = System.getProperty("user.home");
        System.setProperty("user.home", root.toFile().getAbsolutePath());
        originalVersionSource = CoreVersionsTestAccess.currentSource();
        CoreVersionsTestAccess.injectSource(() -> ACTUAL_CORE);

        coreMock = mock(EcatCore.class);
        managerMock = mock(IntegrationManager.class);
        registryMock = mock(IntegrationRegistry.class);
        loadedInstance = mock(IntegrationBase.class);
        when(coreMock.getIntegrationManager()).thenReturn(managerMock);
        when(coreMock.getIntegrationRegistry()).thenReturn(registryMock);

        configStore = new LinkedHashMap<>();
        configStore.put("integrations", new LinkedHashMap<>());
        putEntry(COORD, "1.0.0", true);
        syncYmlFile();

        // 快照 jar 面(snapshotJars 按 yml 坐标硬链接 ~/.m2):现装版本夹具一次成型
        fixtureJar("app", "1.0.0", "*");
        fixtureJar("lib", "1.5.0", "*");

        when(managerMock.loadIntegrationsConfig()).thenAnswer(inv -> deepCopy(configStore));
        doAnswer(inv -> {
            Map<String, Map<String, Object>> doc = typedDoc(inv.getArgument(0));
            configStore.clear();
            configStore.putAll(doc);
            syncYmlFile();
            return null;
        }).when(managerMock).updateIntegrationsConfig(any());

        ledgerStore = new InstallationLedgerStore(installationsRoot, snapshotRoot, Clock.systemUTC());
        // 默认无 BackupHook 参与者(注册表空集);钩子形态用例在用例内注入参与者
        when(registryMock.getAllCoordinates()).thenReturn(new LinkedHashSet<>());
        backupService = spy(new BackupService(snapshotRoot, integrationsYml, configEntriesRoot,
                integrationsItemDir, registryMock, ledgerStore) {
            @Override
            protected Path coreProgramLocation() {
                return coreProgramFixture;
            }
        });
        orchestrator = new UpgradeOrchestrator(coreMock, queueRoot, snapshotRoot,
                integrationsYml, configEntriesRoot, integrationsItemDir, backupService, ledgerStore);
    }

    @After
    public void tearDown() {
        System.setProperty("user.home", originalUserHome);
        CoreVersionsTestAccess.injectSource(originalVersionSource);
    }

    // ==================== T5 无队列零差异(回归) ====================

    /** 空 queueRoot:两阶段直通毫秒返回;配置零写调用,与现状启动路径无观测差异 */
    @Test
    public void emptyQueue_zeroCostPassthrough_zeroConfigWrites() {
        long start = System.nanoTime();
        orchestrator.runPreLoadPhase();
        orchestrator.runPostLoadPhase();
        long elapsedMillis = (System.nanoTime() - start) / 1_000_000;

        assertTrue("无队列应毫秒返回,实测 " + elapsedMillis + "ms", elapsedMillis < 2000);
        verify(managerMock, never()).updateIntegrationsConfig(any());
        assertFalse("队列目录不存在时两阶段直通不应创建它", Files.exists(queueRoot));
    }

    // ==================== T9 core 坐标守卫 ====================

    /** manifest 目标=core 自身坐标 → PREREQ_FAILED,消息含发车纪律指引;零变更零提交 */
    @Test
    public void coreCoordinate_prereqFailedWithDispatchGuidance() throws IOException {
        JSONObject file = fixtureFileJson("ecat-core", ACTUAL_CORE, "x");
        JSONObject item = baseItem("com.ecat", "ecat-core", "9.9.9", ACTUAL_CORE);
        item.put("files", new ArrayList<>(Arrays.asList(file)));
        enqueuePlan("plan-core", "2026-10-01T08:30:00Z", item);

        orchestrator.runPreLoadPhase();

        assertEquals(PlanState.PREREQ_FAILED, loadPlan("plan-core").getState());
        assertTrue("消息应含发车纪律指引: " + loadPlan("plan-core").getError(),
                loadPlan("plan-core").getError().contains("发车"));
        verify(managerMock, never()).updateIntegrationsConfig(any());
    }

    // ==================== T4 多任务串行+首败停队 ====================

    /** 三 plan 入队,首 plan B1 败(sha256 不符)→ PREREQ_FAILED;后两个保持 PENDING 冻结 */
    @Test
    public void multiPlan_firstFailureStopsQueue_restStayPending() throws IOException {
        JSONObject item = baseItem("com.ecat", "app", "1.0.0", "2.0.0");
        JSONObject file = fixtureFileJson("app", "2.0.0", "deadbeef");   // 故意错 sha256
        item.put("files", new ArrayList<>(Arrays.asList(file)));
        enqueuePlan("plan-a", "2026-10-01T08:30:00Z", item);
        enqueuePlan("plan-b", "2026-10-01T08:31:00Z", validUpgradeItem());
        enqueuePlan("plan-c", "2026-10-01T08:32:00Z", validUpgradeItem());

        orchestrator.runPreLoadPhase();

        assertEquals(PlanState.PREREQ_FAILED, loadPlan("plan-a").getState());
        assertEquals("plan-b 保持 PENDING(首败停队)", PlanState.PENDING, loadPlan("plan-b").getState());
        assertEquals("plan-c 保持 PENDING(首败停队)", PlanState.PENDING, loadPlan("plan-c").getState());
        assertTrue("失败原因应含 sha256 不符", loadPlan("plan-a").getError().contains("sha256"));
        // B0 拾取即建账:首败计划的台账在(verify 步 FAILED 记录),终态保持 RUNNING
        Optional<Ledger> ledger = ledgerStore.read("plan-a");
        assertTrue("B0 拾取即建账(接缝钉死)", ledger.isPresent());
        assertEquals("PREREQ_FAILED 非真终态,台账保持 RUNNING",
                InstallationLedgerStore.PlanResult.RUNNING, ledger.get().getResult());
    }

    // ==================== T1 崩溃注入(逐矩阵行重建重放) ====================

    /** VERIFYING 中杀:重跑 B1(纯读复验,幂等安全)→ 续推全链 → COMPLETE */
    @Test
    public void crashInVerifying_rerunB1_resumeForward() throws IOException {
        enqueuePlan("plan-a", "2026-10-01T08:30:00Z", validUpgradeItem());
        rewriteState("plan-a", PlanState.VERIFYING);
        loadedCoordinate(COORD);

        rebuildOrchestrator().runPreLoadPhase();
        rebuildOrchestrator().runPostLoadPhase();

        assertEquals("VERIFYING 重放应续推到 COMPLETE", PlanState.COMPLETE, loadPlan("plan-a").getState());
    }

    /** BACKING_UP 中杀:重做 B2(先清半快照;半快照垃圾不残留)→ 续推 */
    @Test
    public void crashInBackingUp_redoB2_halfSnapshotCleared() throws IOException {
        enqueuePlan("plan-a", "2026-10-01T08:30:00Z", validUpgradeItem());
        rewriteState("plan-a", PlanState.BACKING_UP);
        Path halfSnapshot = snapshotRoot.resolve("plan-a");
        Files.createDirectories(halfSnapshot);
        Files.write(halfSnapshot.resolve("stale-partial.bin"), "GARBAGE".getBytes(StandardCharsets.UTF_8));
        loadedCoordinate(COORD);

        rebuildOrchestrator().runPreLoadPhase();
        rebuildOrchestrator().runPostLoadPhase();

        assertEquals(PlanState.COMPLETE, loadPlan("plan-a").getState());
        assertFalse("重做 B2 应清半快照垃圾", Files.exists(halfSnapshot.resolve("stale-partial.bin")));
    }

    /** BACKED_UP 中杀(快照已成立,B3 未启动):直进 B3 → 续推 */
    @Test
    public void crashAfterBackedUp_directIntoB3() throws IOException {
        // 真实链推到快照成立(registry 空→判定窗前停),再回拨 state=BACKED_UP 模拟「B3 前杀」
        enqueuePlan("plan-a", "2026-10-01T08:30:00Z", validUpgradeItem());
        rebuildOrchestrator().runPreLoadPhase();
        assertEquals(PlanState.LOADING, loadPlan("plan-a").getState());
        rewriteState("plan-a", PlanState.BACKED_UP);
        loadedCoordinate(COORD);

        rebuildOrchestrator().runPreLoadPhase();
        rebuildOrchestrator().runPostLoadPhase();

        assertEquals("BACKED_UP 直进 B3 应续推", PlanState.COMPLETE, loadPlan("plan-a").getState());
    }

    /** PROGRAM_UPGRADING 提交后杀(yml 已新,load 前):B3 幂等重跑→按标记续推 */
    @Test
    public void crashAfterCommitWhileProgramUpgrading_b3IdempotentRerun() throws IOException {
        enqueuePlan("plan-a", "2026-10-01T08:30:00Z", validUpgradeItem());
        rebuildOrchestrator().runPreLoadPhase();
        assertEquals("提交已发生(yml 已新)",
                "2.0.0", readYmlEntry(COORD).get("version"));
        rewriteState("plan-a", PlanState.PROGRAM_UPGRADING);
        loadedCoordinate(COORD);

        rebuildOrchestrator().runPreLoadPhase();
        rebuildOrchestrator().runPostLoadPhase();

        assertEquals("B3 幂等重跑后按标记续推", PlanState.COMPLETE, loadPlan("plan-a").getState());
        assertEquals("终盘 yml version=新值(零幽灵态)",
                "2.0.0", readYmlEntry(COORD).get("version"));
    }

    /** LOADING 中杀(registry 半注册,判定未跑):新实例判定窗重开→COMPLETE */
    @Test
    public void crashWhileLoading_judgmentWindowReopens() throws IOException {
        enqueuePlan("plan-a", "2026-10-01T08:30:00Z", validUpgradeItem());
        rebuildOrchestrator().runPreLoadPhase();
        assertEquals(PlanState.LOADING, loadPlan("plan-a").getState());
        loadedCoordinate(COORD);

        rebuildOrchestrator().runPostLoadPhase();

        assertEquals(PlanState.COMPLETE, loadPlan("plan-a").getState());
    }

    /** state.yml 撕裂:快照在→保守回滚 ROLLED_BACK;余队 PENDING→CANCELLED;台账 ROLLED_BACK */
    @Test
    public void stateFileTorn_withSnapshot_conservativeRollback() throws IOException {
        enqueuePlan("plan-a", "2026-10-01T08:30:00Z", validUpgradeItem());
        enqueuePlan("plan-b", "2026-10-01T08:31:00Z", validUpgradeItem());
        // 真实链推到快照成立(registry 空),再撕裂 state
        rebuildOrchestrator().runPreLoadPhase();
        Files.write(queueRoot.resolve("plan-a").resolve("state.yml"),
                "state: [torn\n".getBytes(StandardCharsets.UTF_8));

        rebuildOrchestrator().runPreLoadPhase();

        assertEquals("撕裂+快照在=保守回滚", PlanState.ROLLED_BACK, loadPlan("plan-a").getState());
        assertEquals("余队 PENDING 波及作废", PlanState.CANCELLED, loadPlan("plan-b").getState());
        assertEquals(InstallationLedgerStore.PlanResult.ROLLED_BACK,
                ledgerStore.read("plan-a").get().getResult());
        assertEquals(InstallationLedgerStore.PlanResult.CANCELLED,
                ledgerStore.read("plan-b").get().getResult());
        // 三面对账:程序面回到升级前
        assertEquals("yml 回到旧值", "1.0.0", readYmlEntry(COORD).get("version"));
    }

    /** state.yml 撕裂且无快照(PENDING 期):计划作废 FAILED+停队;余队冻结不续推 */
    @Test
    public void stateFileTorn_withoutSnapshot_planVoided_queueFrozen() throws IOException {
        enqueuePlan("plan-a", "2026-10-01T08:30:00Z", validUpgradeItem());
        enqueuePlan("plan-b", "2026-10-01T08:31:00Z", validUpgradeItem());
        Files.write(queueRoot.resolve("plan-a").resolve("state.yml"),
                "state: {torn\n".getBytes(StandardCharsets.UTF_8));

        rebuildOrchestrator().runPreLoadPhase();

        assertEquals("撕裂+无快照(零变更段)=作废", PlanState.FAILED, loadPlan("plan-a").getState());
        assertEquals("停队:余队保持 PENDING", PlanState.PENDING, loadPlan("plan-b").getState());
        assertEquals(InstallationLedgerStore.PlanResult.FAILED,
                ledgerStore.read("plan-a").get().getResult());
    }

    /** manifest 撕裂(state 完好):同保守回滚路径(manifest 为 B1 复验依据,不可信即回滚) */
    @Test
    public void manifestTorn_conservativeRollback() throws IOException {
        enqueuePlan("plan-a", "2026-10-01T08:30:00Z", validUpgradeItem());
        rebuildOrchestrator().runPreLoadPhase();
        Files.write(queueRoot.resolve("plan-a").resolve("manifest.json"),
                "{torn".getBytes(StandardCharsets.UTF_8));

        rebuildOrchestrator().runPreLoadPhase();

        // manifest 仍撕裂,QueuePlan.load 不可用——终态直读 state.yml 断言
        assertEquals(PlanState.ROLLED_BACK.name(),
                PlanState.fromWire((String) readStateDoc("plan-a").get("state")).name());
        assertEquals("yml 恢复为升级前", "1.0.0", readYmlEntry(COORD).get("version"));
        assertEquals(InstallationLedgerStore.PlanResult.ROLLED_BACK,
                ledgerStore.read("plan-a").get().getResult());
    }

    // ==================== T2 负向:乱序必被抓 ====================

    /** state 声称已提交但 yml 仍旧值(账实冲突):必进保守回滚,不猜续推 */
    @Test
    public void committedStateWithStaleYml_thrownToConservativeRollback() throws IOException {
        enqueuePlan("plan-a", "2026-10-01T08:30:00Z", validUpgradeItem());
        rebuildOrchestrator().runPreLoadPhase();
        // 回拨:state 声称已提交(DB_UPGRADING 续推窗),yml 被篡改回旧值(模拟外力/撕裂态)
        rewriteState("plan-a", PlanState.DB_UPGRADING);
        storeEntry(COORD).put("version", "1.0.0");
        syncYmlFile();

        rebuildOrchestrator().runPreLoadPhase();

        assertEquals("账实冲突必须保守回滚而非续推", PlanState.ROLLED_BACK, loadPlan("plan-a").getState());
        assertEquals("yml 恢复为快照值", "1.0.0", readYmlEntry(COORD).get("version"));
    }

    // ==================== B2 备份钩子失败=窗口中止(零变更段) ====================

    /** BackupHook 参与者 backup 抛出:B2 中止→FAILED(零变更段,无回滚动作);账本 FAILED;程序面零动 */
    @Test
    public void backupHookFailure_abortsWindow_planFailed_zeroProgramFaceChange() throws IOException {
        IntegrationBase hookParticipant = mock(IntegrationBase.class,
                Mockito.withSettings().extraInterfaces(BackupHook.class));
        Mockito.doThrow(new IllegalStateException("钩子备份失败")).when((BackupHook) hookParticipant).backup();
        when(registryMock.getAllCoordinates()).thenReturn(new LinkedHashSet<>(Collections.singletonList(COORD)));
        when(registryMock.getIntegration(COORD)).thenReturn(hookParticipant);

        enqueuePlan("plan-a", "2026-10-01T08:30:00Z", validUpgradeItem());
        enqueuePlan("plan-b", "2026-10-01T08:31:00Z", validUpgradeItem());

        rebuildOrchestrator().runPreLoadPhase();

        assertEquals("backup 失败=窗口中止,零变更段直 FAILED", PlanState.FAILED, loadPlan("plan-a").getState());
        assertTrue("失败原因应含 B2 备份语义: " + loadPlan("plan-a").getError(),
                loadPlan("plan-a").getError().contains("B2 备份失败"));
        assertEquals("首败停队:余队 PENDING 冻结", PlanState.PENDING, loadPlan("plan-b").getState());
        assertEquals("账本 backup 步 FAILED 且记参与者",
                InstallationLedgerStore.StepState.FAILED,
                stepOfLedger("plan-a", "backup:" + COORD).getState());
        assertEquals("台账 FAILED(真终态,零变更段无回滚义务)",
                InstallationLedgerStore.PlanResult.FAILED, ledgerStore.read("plan-a").get().getResult());
        assertEquals("程序面零动:yml 仍旧值", "1.0.0", readYmlEntry(COORD).get("version"));
        verify(backupService, never()).restoreProgramFace(anyString());
        Mockito.verify((BackupHook) hookParticipant, never()).restore();
    }

    // ==================== T6 LOADING_FAILED 语义(D24) ====================

    /** 目标坐标 registry 空(无 db 块):LOADING_FAILED+attempts=1+零回滚调用;台账钉 RUNNING */
    @Test
    public void targetNotLoaded_loadingFailed_zeroRollback_ledgerStaysRunning() throws IOException {
        enqueuePlan("plan-a", "2026-10-01T08:30:00Z", validUpgradeItem());

        rebuildOrchestrator().runPreLoadPhase();
        rebuildOrchestrator().runPostLoadPhase();

        assertEquals(PlanState.LOADING_FAILED, loadPlan("plan-a").getState());
        assertEquals("attempts=1(重试可观察)", 1, loadPlan("plan-a").getAttempts());
        assertTrue("失败原因应指认坐标+人读通道", loadPlan("plan-a").getError().contains(COORD));
        verify(backupService, never()).restoreProgramFace(anyString());
        verify(backupService, never()).restoreBackups(anyString());
        // 接缝钉死:LOADING_FAILED 非真终态,台账 RUNNING+recordStep,禁 finish
        assertEquals("台账必须保持 RUNNING(finish-once+保留期双陷阱)",
                InstallationLedgerStore.PlanResult.RUNNING,
                ledgerStore.read("plan-a").get().getResult());
    }

    /** LOADING_FAILED 重建实例→回 LOADING 自动重试(D24);再败 attempts 累加 */
    @Test
    public void loadingFailed_retryOnNextBoot_attemptsAccumulate() throws IOException {
        enqueuePlan("plan-a", "2026-10-01T08:30:00Z", validUpgradeItem());
        rebuildOrchestrator().runPreLoadPhase();
        rebuildOrchestrator().runPostLoadPhase();
        assertEquals(1, loadPlan("plan-a").getAttempts());

        rebuildOrchestrator().runPreLoadPhase();   // 下个 boot:B0 分流回 LOADING

        assertEquals("重试 boot 应回到 LOADING", PlanState.LOADING, loadPlan("plan-a").getState());
        rebuildOrchestrator().runPostLoadPhase();
        assertEquals("再败 attempts=2", 2, loadPlan("plan-a").getAttempts());
        assertEquals(PlanState.LOADING_FAILED, loadPlan("plan-a").getState());
    }

    // ==================== T7/T7b 人工入口(异步受理语义,修订轮 6) ====================

    /** manualRollbackAll 受理:回执 ACCEPTED 即返;终态轮询判达;余队由整队语义作废 */
    @Test
    public void manualRollbackAll_acceptsAsync_restCancelledByWholeQueueSemantics() throws Exception {
        enqueuePlan("plan-a", "2026-10-01T08:30:00Z", validUpgradeItem());
        enqueuePlan("plan-b", "2026-10-01T08:31:00Z", validUpgradeItem());
        rebuildOrchestrator().runPreLoadPhase();          // plan-a 推到 LOADING(registry 空)
        assertEquals(PlanState.LOADING, loadPlan("plan-a").getState());

        UpgradeOrchestrator.RollbackReceipt receipt = rebuildOrchestrator().manualRollbackAll();

        assertEquals("受理回执=ACCEPTED(执行转后台,不阻塞受理线程)",
                UpgradeOrchestrator.ReceiptState.ACCEPTED, receipt.getState());
        assertEquals("回执携带 planId", "plan-a", receipt.getPlanId());
        awaitPlanState("plan-a", PlanState.ROLLED_BACK, "受理后终态轮询");
        awaitPlanState("plan-b", PlanState.CANCELLED, "整队语义余队作废");
        assertEquals("yml 回到升级前", "1.0.0", readYmlEntry(COORD).get("version"));
        assertEquals(InstallationLedgerStore.PlanResult.ROLLED_BACK,
                ledgerStore.read("plan-a").get().getResult());
    }

    /** manualRollbackPlan 受理:仅目标 plan 回滚,余队保持 PENDING(与 All 分工锁);REJECTED 显式拒绝 */
    @Test
    public void manualRollbackPlan_acceptsAsync_singlePlanOnly_restUntouched() throws Exception {
        enqueuePlan("plan-a", "2026-10-01T08:30:00Z", validUpgradeItem());
        enqueuePlan("plan-b", "2026-10-01T08:31:00Z", validUpgradeItem());
        rebuildOrchestrator().runPreLoadPhase();

        UpgradeOrchestrator.RollbackReceipt receipt = rebuildOrchestrator().manualRollbackPlan("plan-a");

        assertEquals(UpgradeOrchestrator.ReceiptState.ACCEPTED, receipt.getState());
        awaitPlanState("plan-a", PlanState.ROLLED_BACK, "受理后终态轮询");
        assertEquals("单 plan 回退不波及余队(负向锁)", PlanState.PENDING, loadPlan("plan-b").getState());

        assertEquals("PENDING 段 plan=零已提交变更,显式拒绝",
                UpgradeOrchestrator.ReceiptState.REJECTED,
                rebuildOrchestrator().manualRollbackPlan("plan-b").getState());
        assertEquals("被拒 plan 状态不变", PlanState.PENDING, loadPlan("plan-b").getState());
    }

    /** 对称拒绝:ROLLING_BACK 态对 Plan/All 双入口均 IllegalArgumentException(修订轮 6) */
    @Test
    public void rollingBackState_symmetricRejectionOnBothEntries() throws IOException {
        enqueuePlan("plan-a", "2026-10-01T08:30:00Z", validUpgradeItem());
        rewriteState("plan-a", PlanState.ROLLING_BACK);

        try {
            rebuildOrchestrator().manualRollbackPlan("plan-a");
            fail("Plan 入口对 ROLLING_BACK 应 IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // 显形即断言目标
        }
        try {
            rebuildOrchestrator().manualRollbackAll();
            fail("All 入口对 ROLLING_BACK 应对称拒绝");
        } catch (IllegalArgumentException expected) {
            // 显形即断言目标
        }
    }

    /** 双执行体互斥红测:同一实例双受理→恢复面恰执行一次(单锁串行+执行体重验并发拒绝) */
    @Test
    public void dualAcceptance_rollbackFaceExecutedExactlyOnce() throws Exception {
        enqueuePlan("plan-a", "2026-10-01T08:30:00Z", validUpgradeItem());
        UpgradeOrchestrator orchestrator = rebuildOrchestrator();
        orchestrator.runPreLoadPhase();                   // → LOADING(registry 空)
        // 同一实例(生产=单实例;跨实例锁无共享面,双受理互斥以单实例为断言模型)

        assertEquals(UpgradeOrchestrator.ReceiptState.ACCEPTED,
                orchestrator.manualRollbackPlan("plan-a").getState());
        assertEquals("双受理(第二受理在第一执行起跑前到达,受理态校验放行)",
                UpgradeOrchestrator.ReceiptState.ACCEPTED,
                orchestrator.manualRollbackPlan("plan-a").getState());

        awaitPlanState("plan-a", PlanState.ROLLED_BACK, "双受理收敛终态");
        verify(backupService, times(1)).restoreProgramFace("plan-a");
        assertEquals(InstallationLedgerStore.PlanResult.ROLLED_BACK,
                ledgerStore.read("plan-a").get().getResult());
    }

    /** 判定窗/回滚执行体互斥:同一实例受理后立即判定,两序终态收敛一致(ROLLED_BACK)且恢复单次 */
    @Test
    public void judgmentAndAcceptedRollback_serializedByStateLock_convergeRolledBack() throws Exception {
        enqueuePlan("plan-a", "2026-10-01T08:30:00Z", validUpgradeItem());
        UpgradeOrchestrator orchestrator = rebuildOrchestrator();
        orchestrator.runPreLoadPhase();                   // → LOADING(registry 空)

        assertEquals(UpgradeOrchestrator.ReceiptState.ACCEPTED,
                orchestrator.manualRollbackPlan("plan-a").getState());
        orchestrator.runPostLoadPhase();                  // 与后台回滚抢判定窗(单锁互斥,序不确定)

        awaitPlanState("plan-a", PlanState.ROLLED_BACK, "两序终态收敛");
        verify(backupService, times(1)).restoreProgramFace("plan-a");
        assertEquals("回滚账实一致", InstallationLedgerStore.PlanResult.ROLLED_BACK,
                ledgerStore.read("plan-a").get().getResult());
    }

    /** 受理零变更整队:VOIDED 回执(纯簿记受理线程同步完成),全队列 CANCELLED 留台账 */
    @Test
    public void manualRollbackAll_zeroChangeHead_voidReceipt_queueVoidedSynchronously()
            throws IOException {
        enqueuePlan("plan-a", "2026-10-01T08:30:00Z", validUpgradeItem());
        enqueuePlan("plan-b", "2026-10-01T08:31:00Z", validUpgradeItem());

        UpgradeOrchestrator.RollbackReceipt receipt = rebuildOrchestrator().manualRollbackAll();

        assertEquals("零已提交变更整队=VOIDED(无可恢复面,停队语义已达成)",
                UpgradeOrchestrator.ReceiptState.VOIDED, receipt.getState());
        assertEquals("簿记受理线程同步完成(无需轮询)", PlanState.CANCELLED, loadPlan("plan-a").getState());
        assertEquals(PlanState.CANCELLED, loadPlan("plan-b").getState());
        assertEquals(InstallationLedgerStore.PlanResult.CANCELLED,
                ledgerStore.read("plan-a").get().getResult());
    }

    /** 损坏计划受理:保守处置转后台,回执 ACCEPTED,终态轮询可达(撕裂+快照在→ROLLED_BACK) */
    @Test
    public void corruptPlanAcceptance_conservativeHandlingInBackground() throws Exception {
        enqueuePlan("plan-a", "2026-10-01T08:30:00Z", validUpgradeItem());
        rebuildOrchestrator().runPreLoadPhase();          // 真实快照成立
        Files.write(queueRoot.resolve("plan-a").resolve("state.yml"),
                "state: [torn\n".getBytes(StandardCharsets.UTF_8));

        UpgradeOrchestrator.RollbackReceipt receipt = rebuildOrchestrator().manualRollbackPlan("plan-a");

        assertEquals(UpgradeOrchestrator.ReceiptState.ACCEPTED, receipt.getState());
        awaitPlanState("plan-a", PlanState.ROLLED_BACK, "保守处置后台完成轮询");
        assertEquals("yml 回到升级前", "1.0.0", readYmlEntry(COORD).get("version"));
    }

    /** manualRollbackPlan:未知/终态 plan=IllegalArgumentException(与无台账语义同族) */
    @Test
    public void manualRollbackPlan_unknownAndTerminal_rejectedAsIllegalArgument() throws IOException {
        enqueuePlan("plan-a", "2026-10-01T08:30:00Z", validUpgradeItem());
        rebuildOrchestrator().runPreLoadPhase();

        try {
            rebuildOrchestrator().manualRollbackPlan("no-such-plan");
            fail("未知 plan 应 IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // 显形即断言目标
        }
        loadedCoordinate(COORD);
        rebuildOrchestrator().runPostLoadPhase();   // → COMPLETE(终态)
        try {
            rebuildOrchestrator().manualRollbackPlan("plan-a");
            fail("终态 plan 应 IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            // 显形即断言目标
        }
    }

    // ==================== T8 B3 单批原子提交 ====================

    /** 双 item manifest:updateIntegrationsConfig 恰两次(B3 全批一次+B6 清理一次,非逐坐标分写) */
    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void twoItemManifest_singleBatchCommit() throws IOException {
        JSONObject itemA = validUpgradeItem();
        JSONObject itemB = baseItem("com.ecat", "lib", "1.5.0", "2.5.0");
        JSONObject fileB = fixtureFileJson("lib", "2.5.0", fixtureJar("lib", "2.5.0", "*"));
        itemB.put("files", new ArrayList<>(Arrays.asList(fileB)));
        putEntry("com.ecat:lib", "1.5.0", true);
        syncYmlFile();
        enqueuePlan("plan-ab", "2026-10-01T08:30:00Z", itemA, itemB);
        loadedCoordinate(COORD);
        loadedCoordinate("com.ecat:lib");

        rebuildOrchestrator().runPreLoadPhase();
        rebuildOrchestrator().runPostLoadPhase();

        assertEquals(PlanState.COMPLETE, loadPlan("plan-ab").getState());
        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        verify(managerMock, times(2)).updateIntegrationsConfig(captor.capture());
        Map<?, ?> commitDoc = captor.getAllValues().get(0);
        Map<String, Map<String, Object>> commitItgs =
                (Map<String, Map<String, Object>>) commitDoc.get("integrations");
        assertEquals("B3 单批:B3 提交调用内两坐标齐改",
                "2.0.0", commitItgs.get(COORD).get("version"));
        assertEquals("B3 单批:第二坐标同批翻转",
                "2.5.0", commitItgs.get("com.ecat:lib").get("version"));
    }

    // ==================== F1 读侧零验通道 / F3 PREREQ_FAILED 生命周期(修订轮 7) ====================

    /** 门①降级零验形态(files=[]):跳过 jar 复验直入装载链——B1 不再恒拒,红锁零复验可达 */
    @Test
    public void degradedZeroVerifyForm_b1Passes_reachesLoadingChain() throws IOException {
        JSONObject item = baseItem("com.ecat", "app", "1.0.0", "2.0.0");   // files=[] 门①降级形态
        enqueuePlan("plan-a", "2026-10-01T08:30:00Z", item);

        rebuildOrchestrator().runPreLoadPhase();

        assertEquals("零验形态应过 B1 直入装载链(而非 PREREQ_FAILED 恒拒)",
                PlanState.LOADING, loadPlan("plan-a").getState());
    }

    /** 零验 ≠ 零版本门:files=[] 但 requires_core 缺失仍按 fail-closed 显形拒绝 */
    @Test
    public void zeroVerifyForm_stillEnforcesRequiresCoreGate() throws IOException {
        JSONObject item = baseItem("com.ecat", "app", "1.0.0", "2.0.0");
        item.put("requires_core", null);
        enqueuePlan("plan-a", "2026-10-01T08:30:00Z", item);

        rebuildOrchestrator().runPreLoadPhase();

        assertEquals("requires_core 复验不属 jar 复验面,缺失仍拒", PlanState.PREREQ_FAILED,
                loadPlan("plan-a").getState());
        assertTrue(loadPlan("plan-a").getError().contains("requires_core"));
    }

    /** 态 3 生命周期收口:次 boot 重验(坏盘面复验复败且余队冻结;环境修复后续推解冻) */
    @Test
    public void prereqFailed_retriedNextBoot_environmentFixedContinuesAndUnfreezes() throws Exception {
        JSONObject broken = baseItem("com.ecat", "app", "1.0.0", "2.0.0");
        JSONObject badFile = fixtureFileJson("app", "2.0.0", "deadbeef");
        broken.put("files", new ArrayList<>(Arrays.asList(badFile)));
        enqueuePlan("plan-a", "2026-10-01T08:30:00Z", broken);
        enqueuePlan("plan-b", "2026-10-01T08:31:00Z", validUpgradeItem());

        rebuildOrchestrator().runPreLoadPhase();          // boot1:首败 PREREQ_FAILED
        assertEquals(PlanState.PREREQ_FAILED, loadPlan("plan-a").getState());
        assertEquals(PlanState.PENDING, loadPlan("plan-b").getState());

        rebuildOrchestrator().runPreLoadPhase();          // boot2:态 3 队首重验(仍坏)
        assertEquals("次 boot 队首重验(非静默出清)", PlanState.PREREQ_FAILED,
                loadPlan("plan-a").getState());
        assertEquals("余队冻结不续推", PlanState.PENDING, loadPlan("plan-b").getState());

        // 环境修复=写侧重铸 manifest(正确 sha256)
        JSONObject fixed = baseItem("com.ecat", "app", "1.0.0", "2.0.0");
        JSONObject goodFile = fixtureFileJson("app", "2.0.0", fixtureJar("app", "2.0.0", "*"));
        fixed.put("files", new ArrayList<>(Arrays.asList(goodFile)));
        Files.write(queueRoot.resolve("plan-a").resolve("manifest.json"),
                new JSONObject(new LinkedHashMap<String, Object>() {{
                    put("schemaVersion", 1);
                    put("planId", "plan-a");
                    put("type", "UPGRADE");
                    put("createdAt", "2026-10-01T08:30:00Z");
                    put("items", new ArrayList<>(Arrays.asList(fixed)));
                }}).toJSONString().getBytes(StandardCharsets.UTF_8));

        UpgradeOrchestrator boot3 = rebuildOrchestrator();
        boot3.runPreLoadPhase();                          // boot3:重验过→续推
        assertEquals("环境修复后重验过续推", PlanState.LOADING, loadPlan("plan-a").getState());
        loadedCoordinate(COORD);
        boot3.runPostLoadPhase();
        assertEquals(PlanState.COMPLETE, loadPlan("plan-a").getState());

        rebuildOrchestrator().runPreLoadPhase();          // boot4:队首解冻,余队续推
        assertEquals("队首完成后余队获得推进", PlanState.LOADING, loadPlan("plan-b").getState());
    }

    // ==================== B6 COMPLETE 终盘对账 ====================

    /** 全链成功:registry 全在→COMPLETE;台账 SUCCESS;pendingVersion/oldVersion 一次写清 */
    @Test
    public void fullChainComplete_ledgerSuccess_pendingFieldsCleared() throws IOException {
        enqueuePlan("plan-a", "2026-10-01T08:30:00Z", validUpgradeItem());
        rebuildOrchestrator().runPreLoadPhase();
        assertEquals("preload 后 yml 已翻转+state=LOADING",
                PlanState.LOADING, loadPlan("plan-a").getState());
        assertEquals("B3 已提交新版本", "2.0.0", readYmlEntry(COORD).get("version"));
        assertEquals("pendingVersion 翻转形态与既有升级入口一致",
                "2.0.0", readYmlEntry(COORD).get("pendingVersion"));
        loadedCoordinate(COORD);

        rebuildOrchestrator().runPostLoadPhase();

        assertEquals(PlanState.COMPLETE, loadPlan("plan-a").getState());
        assertNull("pendingVersion 随成功清除", readYmlEntry(COORD).get("pendingVersion"));
        assertNull("oldVersion 随成功清除", readYmlEntry(COORD).get("oldVersion"));
        assertEquals(InstallationLedgerStore.PlanResult.SUCCESS,
                ledgerStore.read("plan-a").get().getResult());
    }

    /** enabled=false 目标坐标豁免加载要求(升级文件已提交、集成保持停用=合法终态) */
    @Test
    public void disabledTarget_exemptFromLoadRequirement_completes() throws IOException {
        enqueuePlan("plan-a", "2026-10-01T08:30:00Z", validUpgradeItem());
        rebuildOrchestrator().runPreLoadPhase();
        // 提交后把该坐标置停用(合法运维形态),registry 不注入
        putEntry(COORD, "2.0.0", false);
        syncYmlFile();

        rebuildOrchestrator().runPostLoadPhase();

        assertEquals("停用豁免应 COMPLETE 而非 LOADING_FAILED",
                PlanState.COMPLETE, loadPlan("plan-a").getState());
    }

    // ==================== 夹具与辅助 ====================

    private UpgradeOrchestrator rebuildOrchestrator() {
        // 单测内的「重启」=新实例+同盘面(确定性,零真实进程杀)
        return new UpgradeOrchestrator(coreMock, queueRoot, snapshotRoot,
                integrationsYml, configEntriesRoot, integrationsItemDir, backupService, ledgerStore);
    }

    /**
     * 后台执行完成判定(修订轮 6 异步受理):条件到达式轮询——断言**终态已达成**,
     * 非「等固定时间后猜」(测试纪律:确定性同步,禁 sleep 猜测)。轮询期 UpgradeStateException
     * =尚未到达的合法未达态(撕裂文件待后台保守修复重写/原子 rename 后才可读),继续等;
     * atomic rename 保证读侧无半态,读到旧态=执行未到。
     */
    private void awaitPlanState(String planId, PlanState expected, String message) throws IOException {
        long deadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        String lastState = "unreadable";
        while (System.nanoTime() < deadlineNanos) {
            try {
                PlanState current = QueuePlan.load(queueRoot.resolve(planId)).getState();
                lastState = current.name();
                if (current == expected) {
                    return;
                }
            } catch (UpgradeStateException notYetReached) {
                // 未达态(待后台修复/推进),继续轮询
            }
            LockSupport.parkNanos(5_000_000);
        }
        fail(message + "(5s 内未达 " + expected + ",现态=" + lastState
                + ",ledger=" + ledgerStore.read(planId).map(l -> l.getResult().name()).orElse("none") + ")");
    }

    private void loadedCoordinate(String coordinate) {
        when(registryMock.getIntegration(coordinate)).thenReturn(loadedInstance);
    }

    /** 合法升级条目:夹具 jar(现刻+sha256 实算)+requires_core 满足+条目一致性成立 */
    private JSONObject validUpgradeItem() throws IOException {
        JSONObject item = baseItem("com.ecat", "app", "1.0.0", "2.0.0");
        JSONObject file = fixtureFileJson("app", "2.0.0", fixtureJar("app", "2.0.0", "*"));
        item.put("files", new ArrayList<>(Arrays.asList(file)));
        return item;
    }

    private JSONObject baseItem(String groupId, String artifactId,
                                String installedVersion, String targetVersion) {
        JSONObject item = new JSONObject(new LinkedHashMap<>());
        item.put("group_id", groupId);
        item.put("artifact_id", artifactId);
        item.put("installed_version", installedVersion);
        item.put("target_version", targetVersion);
        item.put("requires_core", "*");
        item.put("files", new ArrayList<>());
        return item;
    }

    private JSONObject fixtureFileJson(String artifactId, String version, String knownSha) {
        JSONObject file = new JSONObject(new LinkedHashMap<>());
        file.put("kind", "jar");
        file.put("group_id", "com.ecat");
        file.put("artifact_id", artifactId);
        file.put("version", version);
        file.put("filename", artifactId + "-" + version + ".jar");
        file.put("sha256", knownSha != null ? knownSha
                : sha256Of(jarPath(artifactId, version)));
        return file;
    }

    private Path jarPath(String artifactId, String version) {
        return root.resolve(".m2/repository/com/ecat/" + artifactId + "/" + version
                + "/" + artifactId + "-" + version + ".jar");
    }

    /** 夹具 jar 一次成型(重复调用幂等,保 sha 稳定);含 ecat-config.yml 供依赖门读取 */
    private String fixtureJar(String artifactId, String version, String requiresCore) throws IOException {
        Path jarPath = jarPath(artifactId, version);
        if (!Files.exists(jarPath)) {
            Files.createDirectories(jarPath.getParent());
            try (JarOutputStream jarOut = new JarOutputStream(Files.newOutputStream(jarPath))) {
                jarOut.putNextEntry(new JarEntry("ecat-config.yml"));
                jarOut.write(("requires_core: \"" + requiresCore + "\"\n").getBytes(StandardCharsets.UTF_8));
                jarOut.closeEntry();
            }
        }
        return sha256Of(jarPath);
    }

    private String sha256Of(Path file) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest(Files.readAllBytes(file))) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            throw new IllegalStateException("sha256 计算失败: " + file, e);
        }
    }

    private void enqueuePlan(String planId, String enqueuedAt, JSONObject... items) throws IOException {
        Path planDir = queueRoot.resolve(planId);
        Files.createDirectories(planDir);
        JSONObject manifest = new JSONObject(new LinkedHashMap<>());
        manifest.put("schemaVersion", 1);
        manifest.put("planId", planId);
        manifest.put("type", "UPGRADE");
        manifest.put("createdAt", enqueuedAt);
        manifest.put("items", new ArrayList<>(Arrays.asList(items)));
        Files.write(planDir.resolve("manifest.json"),
                manifest.toJSONString().getBytes(StandardCharsets.UTF_8));
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("schemaVersion", 1);
        state.put("planId", planId);
        state.put("state", PlanState.PENDING.name());
        state.put("enqueuedAt", enqueuedAt);
        state.put("attempts", 0);
        new YamlAtomicFileWriter().write(state, planDir.resolve("state.yml").toFile());
    }

    private void rewriteState(String planId, PlanState state) throws IOException {
        Map<String, Object> doc = readStateDoc(planId);
        doc.put("state", state.name());
        new YamlAtomicFileWriter().write(doc, queueRoot.resolve(planId).resolve("state.yml").toFile());
    }

    private Map<String, Object> readStateDoc(String planId) throws IOException {
        try (InputStream in = Files.newInputStream(queueRoot.resolve(planId).resolve("state.yml"))) {
            return new Yaml().load(in);
        }
    }

    private QueuePlan loadPlan(String planId) throws IOException {
        return QueuePlan.load(queueRoot.resolve(planId));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> storeEntry(String coordinate) {
        Map<String, Object> integrations = configStore.get("integrations");
        return ((Map<String, Map<String, Object>>) (Map<?, ?>) integrations).get(coordinate);
    }

    private void putEntry(String coordinate, String version, boolean enabled) {
        String[] parts = coordinate.split(":");
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("groupId", parts[0]);
        entry.put("artifactId", parts[1]);
        entry.put("version", version);
        entry.put("enabled", enabled);
        entry.put("state", "RUNNING");
        configStore.get("integrations").put(coordinate, entry);
    }

    private void syncYmlFile() throws IOException {
        new YamlAtomicFileWriter().write(new LinkedHashMap<>(configStore), integrationsYml.toFile());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> readYmlEntry(String coordinate) throws IOException {
        Map<String, Object> doc;
        try (InputStream in = Files.newInputStream(integrationsYml)) {
            doc = new Yaml().load(in);
        }
        return ((Map<String, Map<String, Object>>) doc.get("integrations")).get(coordinate);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> typedDoc(Object raw) {
        return (Map<String, Map<String, Object>>) raw;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Map<String, Object>> deepCopy(Map<String, Map<String, Object>> doc) {
        Map<String, Map<String, Object>> copy = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : doc.entrySet()) {
            if (entry.getValue() instanceof Map) {
                copy.put(entry.getKey(), new LinkedHashMap<>(entry.getValue()));
            } else {
                copy.put(entry.getKey(), entry.getValue());
            }
        }
        return copy;
    }

    private Path writeEntryFile(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        return file;
    }

    private void writeItemFile(Path rootDir, String name, String content) throws IOException {
        Files.createDirectories(rootDir);
        Files.write(rootDir.resolve(name + ".yml"), content.getBytes(StandardCharsets.UTF_8));
    }

    /** 台账步骤按名取用(不存在=AssertionError,断言目标显形) */
    private InstallationLedgerStore.Step stepOfLedger(String planId, String name) {
        for (InstallationLedgerStore.Step step : ledgerStore.read(planId).get().getSteps()) {
            if (step.getName().equals(name)) {
                return step;
            }
        }
        throw new AssertionError("步骤不存在: " + name);
    }
}
