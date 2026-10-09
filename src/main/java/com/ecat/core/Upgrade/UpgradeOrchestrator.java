/*
 * Copyright (c) 2026 ECAT Team
 */

package com.ecat.core.Upgrade;

import com.ecat.core.Const;
import com.ecat.core.EcatCore;
import com.ecat.core.Integration.DependencyInfo;
import com.ecat.core.Integration.IntegrationInfo;
import com.ecat.core.Integration.IntegrationState;
import com.ecat.core.Upgrade.InstallationLedgerStore.CoordinateTrack;
import com.ecat.core.Upgrade.InstallationLedgerStore.Ledger;
import com.ecat.core.Upgrade.InstallationLedgerStore.PlanResult;
import com.ecat.core.Upgrade.InstallationLedgerStore.PlanType;
import com.ecat.core.Upgrade.InstallationLedgerStore.StepState;
import com.ecat.core.Upgrade.QueuePlan.Item;
import com.ecat.core.Upgrade.QueuePlan.ManifestFile;
import com.ecat.core.Upgrade.QueuePlan.PlanState;
import com.ecat.core.Utils.JarDependencyLoader;
import com.ecat.core.Utils.Log;
import com.ecat.core.Utils.LogFactory;
import com.ecat.core.Version.CoreVersions;
import com.ecat.core.Version.Version;
import com.ecat.core.Version.VersionRange;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import lombok.Value;

/**
 * boot 升级状态机(D22):升级全部变更收敛到重启后单一窗口的唯一执行者。
 *
 * <p>线程模型:B0~B6 boot 窗口=主线程同步执行(boot 链无自有线程)。装配=EcatCore.init 尾
 * 构造+runPreLoadPhase(B0~B4a),main 在 core.load() 后调 runPostLoadPhase(B4b+B5 判定
 * +B6)——两插入点显式可见于 boot 链,loadIntegrations 本体零改动。无队列=零开销直通,
 * 与无编排器时代的启动路径无观测差异。人工回滚=异步受理:REST 线程受理即返回回执,
 * 整程执行转后台命名线程;显式单锁(stateLock)串行化判定窗与回滚执行体——REST 在
 * load 窗内即可达,进程时序天然互斥的假设不成立(rev-T-1-9 裁定)。</p>
 *
 * <p>不变量:①每步先写 state 再执行后验证再写下一 state;②下一步必查上一步标记,
 * 缺失/损坏/乱序→UpgradeStateException→保守回滚,不猜不补;③零队列=与现状零差异。
 * 台账写入权单认领:终态(含 ROLLED_BACK)一律由本类自记,委托方零落账;
 * LOADING_FAILED/PREREQ_FAILED 非真终态——台账保持 RUNNING+recordStep
 * (finish-once 守卫与保留期清理都以真终态为界,误用 finish 会把可重试计划钉死)。</p>
 *
 * <p>回滚恢复源=本地快照(断网可用);rollback 为自动触发与人工入口的同一执行体。
 * 台账终态先行、state.yml 终态随后的落盘次序使任一崩溃点都可由下个 boot 幂等重放收敛
 * (finish 同值重放=无操作)。</p>
 *
 * @author coffee
 */
public class UpgradeOrchestrator {

    private static final Log log = LogFactory.getLogger(UpgradeOrchestrator.class);

    /** 回滚执行体内部结果(执行面;对外受理面=RollbackReceipt,不外泄执行中态) */
    private enum RollbackOutcome { ROLLED_BACK, ROLLBACK_FAILED }

    /** 活动计划只读快照(T-1-9 渲染消费;零副作用) */
    @Value
    public static class PlanSnapshot {
        String planId;
        PlanState state;
        int attempts;
        String error;
        String enqueuedAt;
        Map<String, String> dbMarkers;
    }

    /** 受理回执状态(异步受理语义;终态本体经台账+currentPlanSnapshot 轮询可见) */
    public enum ReceiptState { ACCEPTED, REJECTED, VOIDED }

    /**
     * 人工回滚受理回执:ACCEPTED=已受理,整程执行转后台;REJECTED=显式拒绝(零已提交
     * 变更/无活动计划);VOIDED=整队作废(零已提交变更队首的整队语义,受理线程簿记完成)。
     * 回执≠终态回报(rev-T-1-9 裁定:同步等终态=REST 线程被回滚 IO 绑死,受理即返回)。
     */
    @Value
    public static class RollbackReceipt {
        String planId;
        ReceiptState state;
    }

    /**
     * 真终态集合(台账 finish 对齐;PREREQ_FAILED 不在内——态 3 非真终态,次 boot
     * 重验续推,修订轮 7 收口「永不重验/静默出清/台账 RUNNING 永挂」三重不一致)
     */
    private static final Set<PlanState> TERMINAL_STATES = new HashSet<>(Arrays.asList(
            PlanState.COMPLETE, PlanState.ROLLED_BACK, PlanState.CANCELLED,
            PlanState.FAILED, PlanState.ROLLBACK_FAILED));

    /** 良性终态(出清跳过,不做队首):终态中无人工义务的三态 */
    private static final Set<PlanState> BENIGN_TERMINAL_STATES = new HashSet<>(Arrays.asList(
            PlanState.COMPLETE, PlanState.ROLLED_BACK, PlanState.CANCELLED));

    /**
     * 首败冻结五态(=设计 §3.2 不变量 3/11/13/14/16):队首命中即首败停队,
     * 余队 PENDING 冻结显式上报;PREREQ_FAILED/LOADING_FAILED 对其自身保留
     * 次 boot 重验/重试语义(冻结只冻余队)
     */
    private static final Set<PlanState> FREEZING_STATES = new HashSet<>(Arrays.asList(
            PlanState.PREREQ_FAILED, PlanState.LOADING_FAILED, PlanState.ROLLED_BACK,
            PlanState.ROLLBACK_FAILED, PlanState.FAILED));

    /** 停队待人工态(冻结五态的真终态子集):作为队首即不再自动尝试 */
    private static final Set<PlanState> HALTING_STATES = new HashSet<>(Arrays.asList(
            PlanState.FAILED, PlanState.ROLLBACK_FAILED));

    private final EcatCore core;
    private final Path queueRoot;
    private final Path snapshotRoot;
    private final Path integrationsYml;
    private final Path configEntriesRoot;
    private final Path integrationsItemDir;
    private final BackupService backupService;
    private final InstallationLedgerStore ledgerStore;
    private final AtomicBoolean postLoadEntered = new AtomicBoolean(false);

    /**
     * 编排单锁(显式互斥):串行化 runPostLoadPhase 判定窗与后台回滚执行体——REST 在
     * load 窗内即可达,「boot 完成后才可能来 REST」的时序前提不成立(rev-T-1-9 裁定),
     * 判定与回滚对同一 state.yml 的写入必须互斥;Java 管程可重入,判定窗内联回滚不另取锁。
     */
    private final Object stateLock = new Object();

    /**
     * 生产构造:.ecat-data 默认布局;台账/快照组件由本构造单源创建——
     * core-api 组合根经 getLedgerStore()/getBackupService() 取用同一实例
     * (双装配收敛:一侧构造、另一侧取用,消灭同路径双实例漂移面)。
     */
    public UpgradeOrchestrator(EcatCore core) {
        this(core, Paths.get(".ecat-data", "upgrades"),
                Paths.get(".ecat-data", "backups"),
                Paths.get(".ecat-data", "core", "integrations.yml"),
                Paths.get(".ecat-data", "core", "config_entries"),
                Paths.get(".ecat-data", "integrations"),
                null, null);
    }

    /** 全参构造:路径/组件可注入=测试缝(单测重定向临时目录,不触真实 .ecat-data) */
    public UpgradeOrchestrator(EcatCore core, Path queueRoot, Path snapshotRoot,
                               Path integrationsYml, Path configEntriesRoot, Path integrationsItemDir,
                               BackupService backupService, InstallationLedgerStore ledgerStore) {
        this.core = core;
        this.queueRoot = queueRoot;
        this.snapshotRoot = snapshotRoot;
        this.integrationsYml = integrationsYml;
        this.configEntriesRoot = configEntriesRoot;
        this.integrationsItemDir = integrationsItemDir;
        if (backupService != null) {
            this.ledgerStore = ledgerStore;
            this.backupService = backupService;
        } else {
            this.ledgerStore = new InstallationLedgerStore(
                    Paths.get(".ecat-data", "installations"), snapshotRoot, Clock.systemUTC());
            this.backupService = new BackupService(snapshotRoot, integrationsYml,
                    configEntriesRoot, integrationsItemDir, core.getIntegrationRegistry(),
                    this.ledgerStore);
        }
    }

    /** 台账仓库单源取用点(core-api 组合根消费,双装配收敛) */
    public InstallationLedgerStore getLedgerStore() {
        return ledgerStore;
    }

    /** 快照服务单源取用点(core-api 组合根消费,双装配收敛) */
    public BackupService getBackupService() {
        return backupService;
    }

    // ==================== boot 入口 ====================

    /**
     * B0~B4a(init 尾调用):扫队列→拾取队首→按态分流推进到 LOADING(或单段终态)。
     * 无队列=零开销直通。每 boot 至多一个 plan 走完整链(apply 窗=唯一 load 窗,
     * 后续 PENDING 由下个 boot 续推);首败命中停队词表→余队冻结显式上报。
     */
    public void runPreLoadPhase() {
        runQueue(new HashSet<String>());
    }

    /**
     * 队列扫描主体:损坏计划先行保守处置(排序依据不可读;处置结果=冻结或出清);
     * 处置会波及余队盘面(余队 CANCELLED),故出清后整队重扫并以 handled 集合排除
     * 已处置目录——撕裂文件二次处置会把已定终态翻坏。
     *
     * <p>队首语义(修订轮 7,F3 生命周期收口):良性终态(COMPLETE/ROLLED_BACK/CANCELLED)
     * 出清跳过;队首=首个非良性终态计划——PREREQ_FAILED/LOADING_FAILED 作为队首**重验/重试**
     * (态 3 迁出/D24 自动重试,冻结只冻余队不冻队首自身);FAILED/ROLLBACK_FAILED 为队首=
     * 停队待人工(不再自动尝试,最高级错误)。队首卡住=余队 PENDING 天然冻结(fail-closed)。</p>
     */
    private void runQueue(Set<String> handledDirs) {
        if (!Files.isDirectory(queueRoot)) {
            log.debug("升级队列不存在,零开销直通: " + queueRoot);
            return;
        }
        List<Path> dirs = listPlanDirs();
        if (dirs.isEmpty()) {
            log.debug("升级队列为空,零开销直通");
            return;
        }
        List<QueuePlan> plans = new ArrayList<>();
        for (Path dir : dirs) {
            if (handledDirs.contains(dir.getFileName().toString())) {
                continue;
            }
            try {
                plans.add(QueuePlan.load(dir));
            } catch (UpgradeStateException e) {
                if (conservativeForCorrupt(dir, e)) {
                    return;   // 停队(fail-closed)
                }
                handledDirs.add(dir.getFileName().toString());
                runQueue(handledDirs);   // 重扫:余队盘面已被波及,新鲜读再判定
                return;
            }
        }
        plans.sort((a, b) -> a.getEnqueuedAt().compareTo(b.getEnqueuedAt()));
        QueuePlan head = null;
        for (QueuePlan plan : plans) {
            if (BENIGN_TERMINAL_STATES.contains(plan.getState())) {
                log.debug("出清终态计划(不删目录,清理窗归家务卡): " + plan.getPlanId() + "=" + plan.getState());
                continue;
            }
            head = plan;
            break;
        }
        if (head == null) {
            log.debug("队列无活动计划");
            return;
        }
        if (HALTING_STATES.contains(head.getState())) {
            log.error("[升级编排] 停队待人工: plan " + head.getPlanId() + " 处于 "
                    + head.getState() + "(error=" + head.getError() + "),不再自动尝试,"
                    + "全队列冻结,人工处置前不推进");
            return;
        }
        processPlan(head);
        reportQueueOutcome(head);
    }

    /**
     * B4b+B5 判定+B6(main 在 core.load() 后调用):逐 flyway 域宿主 registry 直查→
     * 全过 COMPLETE/宿主败回滚/目标败 LOADING_FAILED(D24 零自动回滚)。
     * 幂等防重入(AtomicBoolean):二次调用直接返回。
     */
    public void runPostLoadPhase() {
        if (!postLoadEntered.compareAndSet(false, true)) {
            return;
        }
        // 判定窗整体入单锁(装载+判定原子):与后台回滚执行体互斥——锁外装载的 plan
        // 可能已被受理回滚推至终态,陈旧实例再判定=终态被 LOADING_FAILED 复活(账实分裂)
        synchronized (stateLock) {
            QueuePlan plan = findPlanInState(PlanState.LOADING);
            if (plan == null) {
                log.debug("无 LOADING 态计划,post-load 判定跳过");
                return;
            }
            try {
                judgeAfterLoad(plan);
            } catch (RuntimeException e) {
                log.error("[升级编排] 编排器自身异常(变更段,保守回滚) plan=" + plan.getPlanId()
                        + ": " + e.getMessage(), e);
                rollbackInternal(plan, true);
            }
        }
    }

    // ==================== B0 拾取与分流 ====================

    private void processPlan(QueuePlan plan) {
        ensureLedger(plan);
        log.info("[升级编排] 拾取计划: " + plan);
        try {
            switch (plan.getState()) {
                case PENDING:
                case VERIFYING:
                case PREREQ_FAILED:      // 态 3 迁出:人工修复后重启→B1 重验,过则续推
                    runVerify(plan);
                    break;
                case BACKING_UP:         // 崩溃重做:先清半快照(零系统变更,重做安全)
                    runBackup(plan);
                    break;
                case BACKED_UP:          // 崩溃续推:快照已成立直进 B3
                case PROGRAM_UPGRADING:  // B3 幂等重跑(读 yml 已新=验证过;仍旧=重新提交)
                    runCommit(plan);
                    break;
                case PROGRAM_UPGRADED:
                case DB_UPGRADING:       // B4a 幂等续推(子标记增量合并)
                    runDbMark(plan);
                    break;
                case LOADING:            // 崩溃续推:迁移经域执行点幂等,判定窗重开
                case LOADING_FAILED:     // 态 11 迁出:D24 自动重试,attempts++
                    resumeLoading(plan);
                    break;
                case ROLLING_BACK:       // 崩溃于回滚中点:同一执行体幂等续推
                    rollbackInternal(plan, true);
                    break;
                default:
                    throw new UpgradeStateException("活动态分派不可达: " + plan.getState()
                            + " - " + plan.getPlanId());
            }
        } catch (RuntimeException e) {
            recoverPlanFailure(plan, e);
        }
    }

    private void runVerify(QueuePlan plan) {
        if (plan.getState() == PlanState.PENDING || plan.getState() == PlanState.PREREQ_FAILED) {
            plan.stateTransition(PlanState.VERIFYING);
        }
        List<String> failures = verifyManifest(plan);
        if (!failures.isEmpty()) {
            String reason = "B1 复验不过: " + String.join("; ", failures);
            plan.recordError(reason);
            recordStepQuiet(plan.getPlanId(), "verify", StepState.FAILED, reason);
            plan.stateTransition(PlanState.PREREQ_FAILED);
            log.error("[升级编排] " + reason);
            return;
        }
        recordStepQuiet(plan.getPlanId(), "verify", StepState.DONE, null);
        runBackup(plan);
    }

    private void runBackup(QueuePlan plan) {
        if (plan.getState() == PlanState.VERIFYING) {
            plan.stateTransition(PlanState.BACKING_UP);
        } else {
            clearHalfSnapshot(plan.getPlanId());   // BACKING_UP 重做:先清半快照(§3.3)
        }
        try {
            backupService.snapshotForUpgrade(plan.getPlanId());
        } catch (RuntimeException e) {
            String reason = "B2 备份失败(零变更段,无回滚动作): " + e.getMessage();
            plan.recordError(reason);
            recordStepQuiet(plan.getPlanId(), "snapshot", StepState.FAILED, reason);
            finishIfRunningQuiet(plan.getPlanId(), PlanResult.FAILED);
            plan.stateTransition(PlanState.FAILED);
            log.error("[升级编排] " + reason);
            return;
        }
        plan.stateTransition(PlanState.BACKED_UP);
        runCommit(plan);
    }

    private void runCommit(QueuePlan plan) {
        if (!plan.isSnapshotEstablished(snapshotRoot)) {
            throw new UpgradeStateException("B3 前置标记缺失(快照 manifest.json 不在): " + plan.getPlanId());
        }
        if (plan.getState() == PlanState.BACKED_UP) {
            plan.stateTransition(PlanState.PROGRAM_UPGRADING);
        }
        commitProgramFace(plan);
        recordStepQuiet(plan.getPlanId(), "program-commit", StepState.DONE, null);
        plan.stateTransition(PlanState.PROGRAM_UPGRADED);
        runDbMark(plan);
    }

    private void runDbMark(QueuePlan plan) {
        if (plan.getState() == PlanState.PROGRAM_UPGRADED) {
            plan.stateTransition(PlanState.DB_UPGRADING);
        }
        // 上一步标记必查(不变量②):声称已提交则 yml 必已是新值——账实冲突=保守回滚,不猜续推
        verifyCommittedFace(plan);
        plan.stateTransition(PlanState.LOADING);
        // 链止于此:apply 窗=唯一 load 窗,判定归 runPostLoadPhase
        // (DB_UPGRADING 态保留=状态机词表 wire 兼容;域级子标记随 db: 块退役不再写)
    }

    /** B3 提交标记核验:逐 item yml version==target;不符=提交声称与盘面冲突 */
    private void verifyCommittedFace(QueuePlan plan) {
        Map<String, Map<String, Object>> itgs = integrationsOf(
                core.getIntegrationManager().loadIntegrationsConfig());
        for (Item item : plan.getManifest().getItems()) {
            Map<String, Object> entry = itgs.get(item.getCoordinate());
            if (entry == null || !item.getTargetVersion().equals(entry.get("version"))) {
                throw new UpgradeStateException("已提交标记与 yml 盘面冲突(B3 声称已提交): "
                        + item.getCoordinate() + " 期望 " + item.getTargetVersion()
                        + " 实际 " + (entry == null ? "条目缺失" : entry.get("version")));
            }
        }
    }

    private void resumeLoading(QueuePlan plan) {
        if (plan.getState() == PlanState.LOADING_FAILED) {
            plan.stateTransition(PlanState.LOADING);   // D24 自动重试;attempts 在失败进入时已计
        }
        // LOADING:状态已就位,load 由 main 链执行,判定窗在 runPostLoadPhase
    }

    // ==================== B1 复验(纯读,零变更) ====================

    private List<String> verifyManifest(QueuePlan plan) {
        List<String> failures = new ArrayList<>();
        Map<String, Map<String, Object>> doc = core.getIntegrationManager().loadIntegrationsConfig();
        Map<String, Map<String, Object>> itgs = integrationsOf(doc);
        Set<String> planCoordinates = new HashSet<>();
        for (Item item : plan.getManifest().getItems()) {
            planCoordinates.add(item.getCoordinate());
        }
        for (Item item : plan.getManifest().getItems()) {
            String coordinate = item.getCoordinate();
            if (Const.CORE_COORDINATE.equals(coordinate)) {
                failures.add(coordinate + " 为 core 自身坐标——core 升级走发车头位人工链,"
                        + "自动化排除(升级队列不收 core 坐标)");
                continue;
            }
            Map<String, Object> entry = itgs.get(coordinate);
            // 计划前提一致性:升级形态条目必在、新装形态条目必不在(否则 B3 分路前提破坏)
            if (item.getInstalledVersion() != null && entry == null) {
                failures.add(coordinate + " 声明升级但 integrations.yml 无该条目(计划前提破坏)");
                continue;
            }
            if (item.getInstalledVersion() == null && entry != null) {
                failures.add(coordinate + " 声明新装但 integrations.yml 已有该条目(计划前提破坏)");
                continue;
            }
            if (item.getFiles().isEmpty()) {
                // 零验通道(写侧已提交契约「重启窗按 manifest 复验语义零验」,修订轮 7 补读侧):
                // 门①降级条目 files=[] 无 jar 可复验——跳过 jar 复验面(sha256/依赖扫描)直入装载链;
                // 坐标守卫/前提一致性/requires_core 不属 jar 复验面,照验
                // (requires_core 复验是 C1 版本门纵深+态 3 修复续推的判定依据,不豁免)。
                // jar 实缺在 B5 装载显形(LOADING_FAILED,零自动回滚),不在 B1 捏造拒绝。
                failures.addAll(verifyRequiresCore(item));
                continue;
            }
            failures.addAll(verifyFiles(item));
            failures.addAll(verifyRequiresCore(item));
            failures.addAll(verifyDependencies(item, planCoordinates, itgs));
        }
        return failures;
    }

    /** 完整性:files[] 逐条对 ~/.m2 落盘文件重算 sha256 比对(防下载后落盘损坏) */
    private List<String> verifyFiles(Item item) {
        List<String> failures = new ArrayList<>();
        for (ManifestFile file : item.getFiles()) {
            Path local = m2Path(file.getGroupId(), file.getArtifactId(), file.getVersion(), file.getFilename());
            if (!Files.isRegularFile(local)) {
                failures.add(item.getCoordinate() + " 落盘文件缺失: " + local);
                continue;
            }
            String actual;
            try {
                actual = sha256Hex(Files.readAllBytes(local));
            } catch (IOException e) {
                failures.add(item.getCoordinate() + " 落盘文件不可读: " + local + " - " + e.getMessage());
                continue;
            }
            if (!actual.equalsIgnoreCase(file.getSha256())) {
                failures.add(item.getCoordinate() + " 落盘文件 sha256 不符: " + file.getFilename()
                        + "(期望 " + file.getSha256() + " 实测 " + actual + ")");
            }
        }
        return failures;
    }

    /** requires_core 复验:与运行期加载门同判定器同语义(缺失=拒绝,解析失败=拒绝) */
    private List<String> verifyRequiresCore(Item item) {
        List<String> failures = new ArrayList<>();
        String constraint = item.getRequiresCore();
        if (constraint == null) {
            failures.add(item.getCoordinate() + " 未声明 requires_core(缺失=null)——版本契约 fail-closed,"
                    + "与运行期门同语义拒绝");
            return failures;
        }
        try {
            VersionRange range = VersionRange.parse(constraint);
            if (!range.satisfies(Version.parse(CoreVersions.current()))) {
                failures.add(item.getCoordinate() + " requires_core(" + constraint + ") 与当前 core 版本("
                        + CoreVersions.current() + ")不满足");
            }
        } catch (IllegalArgumentException e) {
            failures.add(item.getCoordinate() + " requires_core 无法解析: " + constraint);
        }
        return failures;
    }

    /** 依赖齐全:读目标 jar 声明依赖,逐个验「yml enabled 或 manifest items 覆盖」 */
    private List<String> verifyDependencies(Item item, Set<String> planCoordinates,
                                            Map<String, Map<String, Object>> itgs) {
        List<String> failures = new ArrayList<>();
        ManifestFile jarFile = jarEntryOf(item);
        if (jarFile == null) {
            failures.add(item.getCoordinate() + " manifest 无 jar 文件条目");
            return failures;
        }
        Path jarPath = m2Path(jarFile.getGroupId(), jarFile.getArtifactId(),
                jarFile.getVersion(), jarFile.getFilename());
        IntegrationInfo info;
        try {
            info = JarDependencyLoader.readPartialIntegrationInfoFromJar(jarPath.toFile());
        } catch (Exception e) {
            failures.add(item.getCoordinate() + " 目标 jar 依赖声明不可读: " + e.getMessage());
            return failures;
        }
        if (info.getDependencyInfoList() == null) {
            return failures;
        }
        for (DependencyInfo dep : info.getDependencyInfoList()) {
            String depCoordinate = dep.getGroupId() + ":" + dep.getArtifactId();
            if (planCoordinates.contains(depCoordinate)) {
                continue;   // manifest items 覆盖(同批升级/新装)
            }
            Map<String, Object> depEntry = itgs.get(depCoordinate);
            if (depEntry == null || !Boolean.TRUE.equals(depEntry.get("enabled"))) {
                failures.add(item.getCoordinate() + " 依赖缺失或未启用: " + depCoordinate);
            }
        }
        return failures;
    }

    // ==================== B3 程序面提交(单批原子) ====================

    /**
     * 单次原子提交:全量 config 构造→updateIntegrationsConfig 一次写全批(非逐坐标)→
     * 读回验证(补偿写吞)。升级形态翻转字段与既有升级入口形一致(legacy 展示链兼容);
     * 新装降级形态按安装链新条目形出生(PENDING_ADDED)。在线翻转被本步取代——
     * 翻转只发生在 boot 变更窗内。
     */
    private void commitProgramFace(QueuePlan plan) {
        Map<String, Map<String, Object>> doc = core.getIntegrationManager().loadIntegrationsConfig();
        Map<String, Map<String, Object>> itgs = integrationsOf(doc);
        for (Item item : plan.getManifest().getItems()) {
            String coordinate = item.getCoordinate();
            Map<String, Object> entry = itgs.get(coordinate);
            if (item.getInstalledVersion() != null) {
                if (entry == null) {
                    throw new UpgradeStateException("B3 升级条目缺失(计划前提破坏,B1 后被外力改动): " + coordinate);
                }
                entry.put("version", item.getTargetVersion());
                entry.put("oldVersion", item.getInstalledVersion());
                entry.put("pendingVersion", item.getTargetVersion());
                entry.put("state", IntegrationState.PENDING_UPGRADE.name());
                entry.put("update", new Date().toString());
            } else {
                if (entry != null) {
                    throw new UpgradeStateException("B3 新装条目已存在(计划前提破坏,B1 后被外力改动): " + coordinate);
                }
                entry = new LinkedHashMap<>();
                entry.put("groupId", item.getGroupId());
                entry.put("artifactId", item.getArtifactId());
                entry.put("version", item.getTargetVersion());
                entry.put("enabled", true);
                entry.put("state", IntegrationState.PENDING_ADDED.name());
                entry.put("pendingVersion", item.getTargetVersion());
                itgs.put(coordinate, entry);
            }
        }
        core.getIntegrationManager().updateIntegrationsConfig(doc);
        // 验证步:重新读回逐 item 比对 version==target(写失败吞异常的补偿执法)
        Map<String, Map<String, Object>> readBackItgs = integrationsOf(
                core.getIntegrationManager().loadIntegrationsConfig());
        for (Item item : plan.getManifest().getItems()) {
            Map<String, Object> entry = readBackItgs.get(item.getCoordinate());
            if (entry == null || !item.getTargetVersion().equals(entry.get("version"))) {
                throw new UpgradeStateException("B3 提交验证失败(version!=target): " + item.getCoordinate()
                        + " 期望 " + item.getTargetVersion()
                        + " 实际 " + (entry == null ? "条目缺失" : entry.get("version")));
            }
        }
    }

    // ==================== B5 判定+B6 ====================

    private void judgeAfterLoad(QueuePlan plan) {
        // B5 判定(registry 直查权威;enabled=false 目标豁免加载要求=合法停用终态)。
        // 数据迁移不再由本窗判定(db: 块已退役,迁移由装载注册点机制自然执行),
        // 本判定只认目标坐标装载成败。
        List<String> missingTargets = new ArrayList<>();
        Map<String, Map<String, Object>> itgs = integrationsOf(
                core.getIntegrationManager().loadIntegrationsConfig());
        for (Item item : plan.getManifest().getItems()) {
            Map<String, Object> entry = itgs.get(item.getCoordinate());
            if (entry != null && Boolean.FALSE.equals(entry.get("enabled"))) {
                continue;
            }
            if (!isCoordinateLoaded(item.getCoordinate())) {
                missingTargets.add(item.getCoordinate());
            }
        }
        if (missingTargets.isEmpty()) {
            completePlan(plan);
            return;
        }
        loadingFailed(plan, missingTargets);
    }

    /** LOADING_FAILED(D24):零自动回滚;台账保持 RUNNING+recordStep(finish 会撞 finish-once+保留期双陷阱) */
    private void loadingFailed(QueuePlan plan, List<String> missingTargets) {
        String reason = "坐标未加载(分类见启动报告): " + String.join(", ", missingTargets);
        plan.recordError(reason);
        plan.incrementAttempts();
        recordStepQuiet(plan.getPlanId(), "loading", StepState.FAILED, reason);
        plan.stateTransition(PlanState.LOADING_FAILED);
        log.error("[升级编排] LOADING_FAILED: " + reason + " —— 零自动回滚,下次重启自动重试(attempts="
                + plan.getAttempts() + "),人工可走整队回滚");
    }

    private void completePlan(QueuePlan plan) {
        finishIfRunningQuiet(plan.getPlanId(), PlanResult.SUCCESS);
        // 一次原子写清 pendingVersion/oldVersion(升级链遗留展示字段随成功退休)
        Map<String, Map<String, Object>> doc = core.getIntegrationManager().loadIntegrationsConfig();
        Map<String, Map<String, Object>> itgs = integrationsOf(doc);
        for (Item item : plan.getManifest().getItems()) {
            Map<String, Object> entry = itgs.get(item.getCoordinate());
            if (entry != null) {
                entry.remove("pendingVersion");
                entry.remove("oldVersion");
            }
        }
        core.getIntegrationManager().updateIntegrationsConfig(doc);
        plan.recordError(null);
        plan.stateTransition(PlanState.COMPLETE);
        ledgerStore.pruneRetention();
        log.info("[升级编排] COMPLETE: " + plan.getPlanId() + " —— 台账 SUCCESS,遗留展示字段已清");
    }

    // ==================== 回滚(自动触发与人工入口同一执行体) ====================

    /**
     * 回滚执行体:备份面还原(账本驱动,只到 backup 成功者)→程序面三面还原→
     * 三面逐字节验证→ROLLED_BACK。任一步失败→ROLLBACK_FAILED(最高级错误:
     * 停机报告,不再自动尝试)。恢复后需重启生效——运行中实例不因回滚自动卸载。
     */
    private RollbackOutcome rollbackInternal(QueuePlan plan, boolean cancelRest) {
        String planId = plan.getPlanId();
        if (plan.getState() != PlanState.ROLLING_BACK) {
            plan.stateTransition(PlanState.ROLLING_BACK);
        }
        try {
            backupService.restoreBackups(planId);
            backupService.restoreProgramFace(planId);
            verifyRestoredFaces(planId);
            finishIfRunningQuiet(planId, PlanResult.ROLLED_BACK);
            plan.recordError(null);
            plan.stateTransition(PlanState.ROLLED_BACK);
            log.warn("[升级编排] ROLLED_BACK: " + planId + " —— 系统回到升级前(程序+数据),恢复需重启生效");
            if (cancelRest) {
                cancelRemainingPlans(planId);
            }
            return RollbackOutcome.ROLLED_BACK;
        } catch (RuntimeException | IOException e) {
            String reason = "回滚执行失败: " + e.getMessage();
            log.error("[升级编排] 停机报告(最高级错误,不再自动尝试,人工介入唯一出口) plan=" + planId
                    + ": " + reason, e);
            if (plan.getState() == PlanState.ROLLED_BACK) {
                // 终态转移已达成后的尾巴异常(余队作废面):回滚本体已成功,不翻已定终态
                log.warn("[升级编排] 回滚终态已达成,尾巴异常不翻 ROLLED_BACK: " + reason);
                return RollbackOutcome.ROLLED_BACK;
            }
            plan.recordError(reason);
            recordStepQuiet(planId, "rollback", StepState.FAILED, reason);
            finishIfRunningQuiet(planId, PlanResult.FAILED);
            try {
                plan.stateTransition(PlanState.ROLLBACK_FAILED);
            } catch (RuntimeException stateFailure) {
                log.error("[升级编排] ROLLBACK_FAILED 状态写失败(双写皆败,盘面以日志为真相): "
                        + stateFailure.getMessage());
            }
            return RollbackOutcome.ROLLBACK_FAILED;
        }
    }

    /** 恢复验证(与恢复面同三面):yml 读回==快照字节 ∧ config_entries 树逐文件==快照 ∧ integrations 单件树==快照 */
    private void verifyRestoredFaces(String planId) throws IOException {
        Path snapConfig = snapshotRoot.resolve(planId).resolve("config");
        Path snapYml = snapConfig.resolve("integrations.yml");
        Path snapAbsent = snapConfig.resolve("integrations.yml.absent");
        if (Files.isRegularFile(snapAbsent)) {
            if (Files.exists(integrationsYml)) {
                throw new UpgradeStateException("回滚验证失败: 快照为 absent 形态但现文件存在: " + integrationsYml);
            }
        } else if (Files.isRegularFile(snapYml)) {
            if (!Arrays.equals(Files.readAllBytes(snapYml), Files.readAllBytes(integrationsYml))) {
                throw new UpgradeStateException("回滚验证失败: integrations.yml 字节与快照不一致");
            }
        } else {
            throw new UpgradeStateException("回滚验证失败: 快照既无 integrations.yml 也无 absent 标记: " + snapConfig);
        }
        compareTree(snapConfig.resolve("config_entries"), configEntriesRoot, "config_entries");
        compareTree(snapConfig.resolve("integrations"), integrationsItemDir, "integrations");
    }

    private void compareTree(Path snapshotTree, Path liveTree, String face) throws IOException {
        if (!Files.isDirectory(snapshotTree)) {
            if (Files.exists(liveTree)) {
                throw new UpgradeStateException("回滚验证失败: 快照无 " + face + " 树但现网存在: " + liveTree);
            }
            return;
        }
        Map<String, Path> snapshotFiles = relativeRegularFiles(snapshotTree);
        Map<String, Path> liveFiles = Files.isDirectory(liveTree)
                ? relativeRegularFiles(liveTree) : new LinkedHashMap<>();
        if (!snapshotFiles.keySet().equals(liveFiles.keySet())) {
            Set<String> onlySnapshot = new HashSet<>(snapshotFiles.keySet());
            onlySnapshot.removeAll(liveFiles.keySet());
            Set<String> onlyLive = new HashSet<>(liveFiles.keySet());
            onlyLive.removeAll(snapshotFiles.keySet());
            throw new UpgradeStateException("回滚验证失败: " + face + " 文件集合不一致(快照独有="
                    + onlySnapshot + " 现网独有=" + onlyLive + ")");
        }
        for (Map.Entry<String, Path> entry : snapshotFiles.entrySet()) {
            byte[] snapshotBytes = Files.readAllBytes(entry.getValue());
            byte[] liveBytes = Files.readAllBytes(liveFiles.get(entry.getKey()));
            if (!Arrays.equals(snapshotBytes, liveBytes)) {
                throw new UpgradeStateException("回滚验证失败: " + face + "/" + entry.getKey() + " 字节与快照不一致");
            }
        }
    }

    private Map<String, Path> relativeRegularFiles(Path root) throws IOException {
        Map<String, Path> result = new LinkedHashMap<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile).forEach(file ->
                    result.put(root.relativize(file).toString(), file));
        }
        return result;
    }

    // ==================== 人工入口(三入口分工边界;异步受理语义) ====================

    /** 可回退态=已提交变更段(B3 起);受理与后台执行体资格重验共用同一判据 */
    private static boolean isRollbackEligible(PlanState state) {
        switch (state) {
            case PROGRAM_UPGRADING:
            case PROGRAM_UPGRADED:
            case DB_UPGRADING:
            case LOADING:
            case LOADING_FAILED:
                return true;
            default:
                return false;
        }
    }

    /** 后台执行体:命名线程可归属(线程普查纪律);持单锁执行,与判定窗/其他执行体互斥 */
    private void spawnBackgroundRollback(String planId, Runnable task) {
        Thread worker = new Thread(() -> {
            synchronized (stateLock) {
                task.run();
            }
        }, "upgrade-rollback-" + planId);
        worker.start();
    }

    /** 受理后的执行体重验:盘面可能已在你来我往间至终态/不可回退态——显式跳过不猜 */
    private void executeAcceptedRollback(String planId, boolean cancelRest) {
        QueuePlan plan;
        try {
            plan = QueuePlan.load(queueRoot.resolve(planId));
        } catch (UpgradeStateException e) {
            conservativeForCorrupt(queueRoot.resolve(planId), e);   // 受理后盘面损坏:保守分路(持锁)
            return;
        }
        if (TERMINAL_STATES.contains(plan.getState())) {
            log.info("[升级编排] 受理后计划已至终态(" + plan.getState() + "),回滚不执行: " + planId);
            return;
        }
        if (!isRollbackEligible(plan.getState())) {
            log.info("[升级编排] 受理后计划处于不可回退态(" + plan.getState() + "),回滚不执行: " + planId);
            return;
        }
        rollbackInternal(plan, cancelRest);
    }

    /**
     * 单 plan 已提交变更回退——**异步受理口**:受理即建账/校验状态,同步返回受理回执,
     * 整程执行转后台命名线程;终态经台账+currentPlanSnapshot 轮询可见。不波及余队。
     * 零已提交变更(PENDING~BACKED_UP 段)=REJECTED 显式拒绝;ROLLING_BACK/终态/未知
     * plan=IllegalArgumentException(与无台账语义同族)。
     */
    public RollbackReceipt manualRollbackPlan(String planId) {
        if (planId == null || planId.isEmpty()) {
            throw new IllegalArgumentException("planId 必填");
        }
        Path planDir = queueRoot.resolve(planId);
        if (!Files.isDirectory(planDir)) {
            throw new IllegalArgumentException("未知 plan: " + planId);
        }
        QueuePlan plan;
        try {
            plan = QueuePlan.load(planDir);
        } catch (UpgradeStateException cause) {
            // 损坏:保守分路转后台(处置结果经台账/状态可见),受理线程零重活
            log.info("[升级编排] 单 plan 回退受理(状态损坏,保守处置转后台): " + planId);
            spawnBackgroundRollback(planId, () -> conservativeForCorrupt(planDir, cause));
            return new RollbackReceipt(planId, ReceiptState.ACCEPTED);
        }
        if (isRollbackEligible(plan.getState())) {
            ensureLedger(plan);   // 受理即建账(B0 同构)
            spawnBackgroundRollback(planId, () -> executeAcceptedRollback(planId, false));
            log.info("[升级编排] 单 plan 回退受理(执行转后台): " + planId + "=" + plan.getState());
            return new RollbackReceipt(planId, ReceiptState.ACCEPTED);
        }
        if (plan.getState() == PlanState.ROLLING_BACK) {
            throw new IllegalArgumentException("回滚进行中,拒绝并发回滚: " + planId);
        }
        if (plan.getState() == PlanState.PENDING || plan.getState() == PlanState.VERIFYING
                || plan.getState() == PlanState.BACKING_UP || plan.getState() == PlanState.BACKED_UP) {
            log.info("[升级编排] 单 plan 回退拒绝(零已提交变更): " + planId + "=" + plan.getState());
            return new RollbackReceipt(planId, ReceiptState.REJECTED);
        }
        throw new IllegalArgumentException("终态 plan 不可回滚(" + plan.getState() + "): " + planId);
    }

    /**
     * 整队回退(D24 一键)——**异步受理口**:可回退态队首受理(建账+后台执行,整队语义
     * 内余队 PENDING→CANCELLED 留台账);零已提交变更队首→整队作废(纯簿记,受理线程
     * 同步完成,回执 VOIDED);无活动计划→REJECTED;ROLLING_BACK→对称拒绝(与单 plan
     * 入口 IAE 同族)。回滚自身失败时余队冻结显式上报(不在未知基座上继续作废,证据保全)。
     */
    public RollbackReceipt manualRollbackAll() {
        QueuePlan head = firstPlan(plan -> !TERMINAL_STATES.contains(plan.getState()));
        if (head == null) {
            log.info("[升级编排] 整队回退: 无活动计划");
            return new RollbackReceipt(null, ReceiptState.REJECTED);
        }
        if (isRollbackEligible(head.getState())) {
            ensureLedger(head);
            String planId = head.getPlanId();
            spawnBackgroundRollback(planId, () -> executeAcceptedRollback(planId, true));
            log.info("[升级编排] 整队回退受理(执行转后台): " + planId);
            return new RollbackReceipt(planId, ReceiptState.ACCEPTED);
        }
        if (head.getState() == PlanState.ROLLING_BACK) {
            throw new IllegalArgumentException("回滚进行中,拒绝并发整队回滚: " + head.getPlanId());
        }
        log.info("[升级编排] 整队回退: 队首零已提交变更(" + head.getState() + "),整队作废");
        cancelPlan(head);
        cancelRemainingPlans(head.getPlanId());
        return new RollbackReceipt(head.getPlanId(), ReceiptState.VOIDED);
    }

    /** 活动计划只读快照(T-1-9 渲染);无活动计划=null;损坏计划跳过(只读面零副作用) */
    public PlanSnapshot currentPlanSnapshot() {
        QueuePlan active = firstPlan(candidate -> !TERMINAL_STATES.contains(candidate.getState()));
        if (active == null) {
            return null;
        }
        return new PlanSnapshot(active.getPlanId(), active.getState(), active.getAttempts(),
                active.getError(), active.getEnqueuedAt(),
                Collections.unmodifiableMap(active.getDbMarkers()));
    }

    // ==================== 台账(单认领:B0 拾取即建,终态自记) ====================

    /**
     * B0 拾取即建(接缝钉死):B2 recordStep/委托缝 REJECTED 可达性/回滚准入三处硬前置。
     * 崩溃重入幂等:RUNNING 记录复用;类型/终态不符=账实冲突显形。
     * degradations 不在本表承载——降级原因串的权威载体=降级源 INSTALL 台账
     * (其 degradations[].queuePlanId 交叉引用本 planId),升级台账不重复记第二份。
     */
    private void ensureLedger(QueuePlan plan) {
        Optional<Ledger> existing = ledgerStore.read(plan.getPlanId());
        if (existing.isPresent()) {
            Ledger ledger = existing.get();
            if (ledger.getType() != PlanType.UPGRADE) {
                throw new UpgradeStateException("台账类型冲突(队列计划为 UPGRADE,台账为 "
                        + ledger.getType() + "): " + plan.getPlanId());
            }
            if (ledger.isTerminal()) {
                throw new UpgradeStateException("计划非终态但台账已终态(" + ledger.getResult()
                        + "),账实冲突: " + plan.getPlanId());
            }
            return;
        }
        ledgerStore.create(plan.getPlanId(), PlanType.UPGRADE,
                snapshotRoot.resolve(plan.getPlanId()).toString(), coordinateTracksOf(plan), null);
        log.info("[升级编排] 台账建账(UPGRADE/RUNNING): " + plan.getPlanId());
    }

    /** 幂等终态写入:同值重放=无操作(崩溃重放收敛);异值/缺失=账实冲突记日志不掩盖主结果 */
    private void finishIfRunningQuiet(String planId, PlanResult result) {
        try {
            Optional<Ledger> existing = ledgerStore.read(planId);
            if (!existing.isPresent()) {
                log.error("[升级编排] 台账缺失,终态写入不可达(账实冲突): " + planId + " ← " + result);
                return;
            }
            Ledger ledger = existing.get();
            if (ledger.isTerminal()) {
                if (ledger.getResult() == result) {
                    return;   // 崩溃重放:终态已写,幂等收敛
                }
                log.error("[升级编排] 台账已终态(" + ledger.getResult() + ")拒绝再写(" + result
                        + "),账实冲突: " + planId);
                return;
            }
            ledgerStore.finish(planId, result);
        } catch (RuntimeException e) {
            log.error("[升级编排] 台账终态写入失败(主结果不变): " + planId + " ← " + result
                    + " - " + e.getMessage());
        }
    }

    /** 簿记 quiet:步骤记录失败记日志不掩盖主流程(与安装链簿记语义同类) */
    private void recordStepQuiet(String planId, String name, StepState state, String error) {
        try {
            ledgerStore.recordStep(planId, name, state, error);
        } catch (RuntimeException e) {
            log.error("[升级编排] 台账步骤记录失败(主流程不变): " + planId + " " + name
                    + "=" + state + " - " + e.getMessage());
        }
    }

    // ==================== 队列治理(余队/停队/损坏) ====================

    /** 余队 PENDING→CANCELLED(留台账;排除计划自身);损坏计划跳过留待其自身保守路径 */
    private void cancelRemainingPlans(String excludePlanId) {
        for (Path dir : listPlanDirs()) {
            String planId = dir.getFileName().toString();
            if (planId.equals(excludePlanId)) {
                continue;
            }
            QueuePlan plan;
            try {
                plan = QueuePlan.load(dir);
            } catch (UpgradeStateException e) {
                log.warn("[升级编排] 余队计划损坏,跳过作废留待其保守路径: " + planId + " - " + e.getMessage());
                continue;
            }
            if (plan.getState() == PlanState.PENDING) {
                cancelPlan(plan);
            }
        }
    }

    private void cancelPlan(QueuePlan plan) {
        String planId = plan.getPlanId();
        try {
            if (!ledgerStore.read(planId).isPresent()) {
                ledgerStore.create(planId, PlanType.UPGRADE,
                        snapshotRoot.resolve(planId).toString(), coordinateTracksOf(plan), null);
            }
            finishIfRunningQuiet(planId, PlanResult.CANCELLED);
            plan.recordError("整队回滚波及作废");
            plan.stateTransition(PlanState.CANCELLED);
            log.info("[升级编排] 余队作废(CANCELLED,留台账): " + planId);
        } catch (RuntimeException e) {
            log.error("[升级编排] 余队作废失败(不影响回滚主结果): " + planId + " - " + e.getMessage());
        }
    }

    /**
     * 队列状态损坏的保守分路(§3.3):快照存在→重写为 ROLLING_BACK 走回滚执行体;
     * 快照不存在(可证零变更:B3 前置=快照标志)→作废 FAILED+台账 FAILED。
     * 返回 true=队列冻结(停队)/回滚失败;false=保守回滚已完成(出清)。
     */
    private boolean conservativeForCorrupt(Path planDir, UpgradeStateException cause) {
        String planId = planDir.getFileName().toString();
        boolean snapshotEstablished = Files.isRegularFile(
                snapshotRoot.resolve(planId).resolve("manifest.json"));
        if (snapshotEstablished) {
            log.error("[升级编排] 队列状态损坏,保守回滚: " + planId + " - " + cause.getMessage());
            QueuePlan repaired = QueuePlan.rewriteMinimal(planDir, planId, PlanState.ROLLING_BACK,
                    "队列状态损坏,保守回滚: " + cause.getMessage());
            return rollbackInternal(repaired, true) == RollbackOutcome.ROLLBACK_FAILED;
        }
        log.error("[升级编排] 队列状态损坏且无快照(零变更段),计划作废: " + planId + " - " + cause.getMessage());
        finishAbsentLedgerFailed(planId);
        QueuePlan.rewriteMinimal(planDir, planId, PlanState.FAILED,
                "状态损坏且无快照,计划作废: " + cause.getMessage());
        return true;   // 停队(fail-closed)
    }

    /** 损坏作废的台账面:无账则建后记 FAILED;RUNNING 记 FAILED;已终态=幂等/账实冲突记日志 */
    private void finishAbsentLedgerFailed(String planId) {
        try {
            if (!ledgerStore.read(planId).isPresent()) {
                ledgerStore.create(planId, PlanType.UPGRADE,
                        snapshotRoot.resolve(planId).toString(), new ArrayList<>(), null);
            }
            finishIfRunningQuiet(planId, PlanResult.FAILED);
        } catch (RuntimeException e) {
            log.error("[升级编排] 作废台账写入失败: " + planId + " - " + e.getMessage());
        }
    }

    /** 编排器自身异常顶层处置(§6):零变更段(PENDING~BACKED_UP,B3 未提交)→FAILED(态 16);变更段→保守回滚 */
    private void recoverPlanFailure(QueuePlan plan, Exception e) {
        log.error("[升级编排] 编排器自身异常 plan=" + plan.getPlanId() + " state=" + plan.getState()
                + ": " + e.getMessage(), e);
        switch (plan.getState()) {
            case PENDING:
            case VERIFYING:
            case BACKING_UP:
            case BACKED_UP:
                String reason = "编排器自身异常(零变更段): " + e.getMessage();
                plan.recordError(reason);
                finishIfRunningQuiet(plan.getPlanId(), PlanResult.FAILED);
                plan.stateTransition(PlanState.FAILED);
                break;
            default:             // 变更段(B3 起):保守回滚
                rollbackInternal(plan, true);
                break;
        }
    }

    private void reportQueueOutcome(QueuePlan processed) {
        long pendingCount = 0;
        for (Path dir : listPlanDirs()) {
            try {
                if (QueuePlan.load(dir).getState() == PlanState.PENDING) {
                    pendingCount++;
                }
            } catch (UpgradeStateException e) {
                // 损坏计划已在扫描段处置,此处只计数面跳过
            }
        }
        if (FREEZING_STATES.contains(processed.getState())) {
            log.error("[升级编排] 首败停队(fail-closed): plan " + processed.getPlanId() + " 至 "
                    + processed.getState() + ",余 " + pendingCount + " 个 PENDING 计划冻结显式上报,"
                    + "不在回滚后基座上续推"
                    + (processed.getState() == PlanState.PREREQ_FAILED
                            || processed.getState() == PlanState.LOADING_FAILED
                            ? "(队首自身保留次 boot 重验/重试语义)" : ""));
        } else if (processed.getState() == PlanState.COMPLETE && pendingCount > 0) {
            log.info("[升级编排] 队首完成,余 " + pendingCount + " 个 PENDING 计划下个 boot 续推");
        }
    }

    // ==================== 枚举与查询辅助 ====================

    private List<Path> listPlanDirs() {
        try {
            return Files.list(queueRoot)
                    .filter(Files::isDirectory)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            throw new UpgradeStateException("队列目录枚举失败: " + queueRoot + " - " + e.getMessage(), e);
        }
    }

    /** 按入队序扫描,返回首个谓词命中的计划;损坏计划跳过(调用方语境决定处置面) */
    private QueuePlan firstPlan(Predicate<QueuePlan> predicate) {
        if (!Files.isDirectory(queueRoot)) {
            return null;
        }
        List<QueuePlan> plans = new ArrayList<>();
        for (Path dir : listPlanDirs()) {
            try {
                plans.add(QueuePlan.load(dir));
            } catch (UpgradeStateException e) {
                log.warn("[升级编排] 计划损坏,查询面跳过: " + dir.getFileName() + " - " + e.getMessage());
            }
        }
        plans.sort((a, b) -> a.getEnqueuedAt().compareTo(b.getEnqueuedAt()));
        for (QueuePlan plan : plans) {
            if (predicate.test(plan)) {
                return plan;
            }
        }
        return null;
    }

    private QueuePlan findPlanInState(PlanState state) {
        return firstPlan(plan -> plan.getState() == state);
    }

    private boolean isCoordinateLoaded(String coordinate) {
        return core.getIntegrationRegistry().getIntegration(coordinate) != null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> integrationsOf(Map<String, Map<String, Object>> doc) {
        Map<String, Object> raw = doc.get("integrations");
        if (raw == null) {
            raw = new LinkedHashMap<>();
            doc.put("integrations", raw);
        }
        return (Map<String, Map<String, Object>>) (Map<?, ?>) raw;
    }

    private List<CoordinateTrack> coordinateTracksOf(QueuePlan plan) {
        List<CoordinateTrack> tracks = new ArrayList<>();
        for (Item item : plan.itemsOrEmpty()) {
            tracks.add(new CoordinateTrack(item.getCoordinate(),
                    item.getInstalledVersion(), item.getTargetVersion()));
        }
        return tracks;
    }

    // ==================== 落盘映射辅助 ====================

    private static ManifestFile jarEntryOf(Item item) {
        for (ManifestFile file : item.getFiles()) {
            if ("jar".equals(file.getKind())) {
                return file;
            }
        }
        return null;
    }

    private static Path m2Path(String groupId, String artifactId, String version, String filename) {
        String repo = System.getProperty("user.home") + "/.m2/repository";
        return Paths.get(repo, groupId.replace('.', '/'), artifactId, version, filename);
    }

    private static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            StringBuilder hex = new StringBuilder();
            for (byte b : digest.digest(data)) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new UpgradeStateException("SHA-256 摘要算法不可用", e);
        }
    }

    /** 半快照清理(B2 重做前;快照面属编排器辖区,失败即显形不给后续步留脏盘面) */
    private void clearHalfSnapshot(String planId) {
        Path planSnapshot = snapshotRoot.resolve(planId);
        if (!Files.exists(planSnapshot)) {
            return;
        }
        try {
            SnapshotFiles.deleteRecursively(planSnapshot);
        } catch (IOException e) {
            throw new UpgradeStateException("半快照清理失败: " + planSnapshot + " - " + e.getMessage(), e);
        }
    }
}
