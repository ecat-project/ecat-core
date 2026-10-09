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

package com.ecat.core.Upgrade;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.TypeReference;
import com.ecat.core.EcatCore;
import com.ecat.core.Integration.IntegrationBase;
import com.ecat.core.Integration.IntegrationRegistry;
import com.ecat.core.Utils.Log;
import com.ecat.core.Utils.LogFactory;
import lombok.Value;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Stream;

/**
 * 快照到 {@code .ecat-data/backups/{planId}/} 的唯一入口,与快照对称的恢复面,
 * 兼升级窗 BackupHook 广播面(时机归平台,数据/路径/方式全归实现者)。
 *
 * <p>快照三件套:①jar 集(integrations.yml 全坐标,enabled 不限——回滚要恢复完整
 * 上次已知世界;Maven 布局按版本分目录永不覆盖,硬链接所指 inode 在升级中不被改写,
 * 硬链接安全;跨文件系统回退整拷)②core 程序文件 ③配置树(integrations.yml 字节+
 * config_entries/ 整树+integrations/*.yml 整树)。升级窗另在快照前广播
 * {@link BackupHook#backup()}:遍历集成注册表取 {@code instanceof BackupHook} 参与者,
 * 逐个同步调用(带超时),账本按 {@code backup:<坐标>} 记参与者与结果;实现者自捕获
 * 自己的数据(Android BackupAgent 同构的两事件形态),core 零数据库知识。</p>
 *
 * <p>不变量:①manifest.json 最后写(它是「快照成立」标志,先写内容后写清单;无
 * manifest 的快照目录=未成立,恢复面拒绝);②manifest 各条目 sha256 在写入时计算,
 * 恢复前逐条对账(篡改检出执法面——拒绝用坏快照回滚);③任何一件失败→已写内容
 * 清理+抛异常(无半快照),backup 钩子失败=窗口中止(Velero {@code on-error: Fail}
 * 语义)。</p>
 *
 * <p>捕获面=恢复面对称:restoreProgramFace 三面全还原(integrations.yml 字节/
 * config_entries/ 整树/integrations/*.yml 整树)——升级窗 entry 迁移会持久化改写
 * 配置树,只还原 yml 会留「旧 jar+新格式 entry」混合态;快照未捕获的面(安装路径
 * 快照只有 yml)恢复时跳过(未捕获=无可还原)。首次安装形态(absent 标记)随安装
 * 路径现状交接:快照落 {@code integrations.yml.absent} 标记,还原按标记删除 yml
 * 回到「无 yml」,不覆盖坏字节。备份面恢复({@link #restoreBackups})账本驱动:
 * 只派发给 {@code backup:<坐标>} 步 DONE 的参与者——窗口可能在备份阶段中止,盲广播
 * 会让未备份者从空槽恢复=数据事故。</p>
 *
 * @author coffee
 */
public class BackupService {

    private static final Log log = LogFactory.getLogger(BackupService.class);

    static final String MANIFEST_FILE = "manifest.json";
    static final String ADDED_FILES_JSON = "added-files.json";

    /** 备份参与者步骤名前缀(账本步骤形态 {@code backup:<坐标>};restore 派发的账本依据) */
    static final String BACKUP_STEP_PREFIX = "backup:";
    /** 恢复参与者步骤名前缀(与 backup 前缀对称,账本可见派发面) */
    static final String RESTORE_STEP_PREFIX = "restore:";

    /**
     * 单参与者 backup/restore 同步调用的超时(秒)。取值依据:钩子=实现者本地
     * IO(宿主全库 pg_dump、介质目录拷贝等),边缘机全库导出为秒到分钟级;
     * 120s 是单参与者的宽硬顶——真卡死(网络卷挂死/锁死锁)不让停机窗无限挂,
     * 超时即窗口中止走回滚分支。批量派发逐个计时,总窗时长随参与者数线性,
     * 停机窗预算由发车纪律人工把控,不在此放大单参与者上限。
     */
    static final int BACKUP_HOOK_TIMEOUT_SECONDS = 120;

    /** 安装新增文件清单条目(wire 键位与安装路径 added-files.json 契约一致) */
    @Value
    public static class AddedFile {
        String coordinate;
        String version;
        String path;
        String sha256;
    }

    private final Path snapshotRoot;
    private final Path integrationsYml;
    private final Path configEntriesRoot;
    private final Path integrationsItemDir;
    private final IntegrationRegistry integrationRegistry;
    private final InstallationLedgerStore ledgerStore;
    /** 派发超时(秒):生产=常量;测试缝注入小值使超时形态确定性可测(不 sleep 等 120s) */
    private final int hookTimeoutSeconds;

    public BackupService(Path snapshotRoot, Path integrationsYml, Path configEntriesRoot,
                         Path integrationsItemDir, IntegrationRegistry integrationRegistry,
                         InstallationLedgerStore ledgerStore) {
        this(snapshotRoot, integrationsYml, configEntriesRoot, integrationsItemDir,
                integrationRegistry, ledgerStore, BACKUP_HOOK_TIMEOUT_SECONDS);
    }

    /** 全参构造:超时秒数可注入=测试缝(单测注入 1s 构造超时形态,确定性) */
    BackupService(Path snapshotRoot, Path integrationsYml, Path configEntriesRoot,
                  Path integrationsItemDir, IntegrationRegistry integrationRegistry,
                  InstallationLedgerStore ledgerStore, int hookTimeoutSeconds) {
        this.snapshotRoot = snapshotRoot;
        this.integrationsYml = integrationsYml;
        this.configEntriesRoot = configEntriesRoot;
        this.integrationsItemDir = integrationsItemDir;
        this.integrationRegistry = integrationRegistry;
        this.ledgerStore = ledgerStore;
        this.hookTimeoutSeconds = hookTimeoutSeconds;
    }

    /**
     * 轻量安装快照(安装路径:配置树三面全捕+新增文件清单;无 jar 集/core 程序——
     * 安装路径不进升级窗,无备份广播)。首装形态:integrations.yml 不存在→落
     * absent 标记(此时无配置树可捕,还原=删 yml 回「无 yml」)。
     *
     * @return 快照目录({@code snapshotRoot/{planId}}),台账 snapshotPath 载体
     */
    public Path snapshotForInstall(String planId, List<AddedFile> addedFiles) {
        Path planDir = snapshotRoot.resolve(planId);
        try {
            Files.createDirectories(planDir.resolve("config"));
            if (Files.exists(integrationsYml)) {
                snapshotConfigTree(planDir);
            } else {
                // 安装前无 yml=首次安装形态:absent 标记使还原回到「无 yml」而非覆盖坏字节
                Files.createFile(planDir.resolve("config").resolve("integrations.yml.absent"));
            }
            List<Map<String, Object>> wire = new ArrayList<>();
            for (AddedFile added : addedFiles) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("coordinate", added.getCoordinate());
                item.put("version", added.getVersion());
                item.put("path", added.getPath());
                item.put("sha256", added.getSha256());
                wire.add(item);
            }
            Files.write(planDir.resolve(ADDED_FILES_JSON),
                    JSON.toJSONString(wire).getBytes(StandardCharsets.UTF_8));
            writeManifest(planDir);
            ledgerStore.recordStep(planId, "snapshot", InstallationLedgerStore.StepState.DONE, null);
        } catch (IOException e) {
            cleanupQuietly(planDir, planId);
            throw new IllegalStateException("安装快照创建失败(未提交): " + planId + " - " + e.getMessage(), e);
        } catch (RuntimeException e) {
            cleanupQuietly(planDir, planId);
            throw e;
        }
        ledgerStore.pruneRetention();
        return planDir;
    }

    /**
     * 全量升级快照(升级编排器变更窗第一步)。时序=窗口协议:先广播
     * {@link BackupHook#backup()}(失败=窗口中止,不迁移不开快照成立标志),
     * 再快照 core 自己的三件套(jars/程序/配置)。参与者在册与结果进账本
     * ({@code backup:<坐标>} 步),恢复面据其账本驱动派发。
     *
     * @return 快照目录
     */
    public Path snapshotForUpgrade(String planId) {
        Path planDir = snapshotRoot.resolve(planId);
        try {
            Files.createDirectories(planDir);

            dispatchBackupHooks(planId);

            ledgerStore.recordStep(planId, "snapshot-jars", InstallationLedgerStore.StepState.PENDING, null);
            snapshotJars(planDir);
            ledgerStore.recordStep(planId, "snapshot-jars", InstallationLedgerStore.StepState.DONE, null);

            ledgerStore.recordStep(planId, "snapshot-core", InstallationLedgerStore.StepState.PENDING, null);
            snapshotCoreProgram(planDir);
            ledgerStore.recordStep(planId, "snapshot-core", InstallationLedgerStore.StepState.DONE, null);

            ledgerStore.recordStep(planId, "snapshot-config", InstallationLedgerStore.StepState.PENDING, null);
            snapshotConfigTree(planDir);
            ledgerStore.recordStep(planId, "snapshot-config", InstallationLedgerStore.StepState.DONE, null);

            ledgerStore.recordStep(planId, "snapshot-manifest", InstallationLedgerStore.StepState.PENDING, null);
            writeManifest(planDir);
            ledgerStore.recordStep(planId, "snapshot-manifest", InstallationLedgerStore.StepState.DONE, null);
        } catch (IOException e) {
            cleanupQuietly(planDir, planId);
            throw new IllegalStateException("升级快照创建失败(未提交): " + planId + " - " + e.getMessage(), e);
        } catch (RuntimeException e) {
            cleanupQuietly(planDir, planId);
            throw e;
        }
        ledgerStore.pruneRetention();
        return planDir;
    }

    /**
     * 程序面恢复(三面全还原):manifest 逐条 sha256 对账(任一篡改→IllegalStateException,
     * 拒绝用坏快照回滚)→ integrations.yml 字节还原(absent 标记=删除 yml)+config_entries/
     * 整树还原+integrations/*.yml 整树还原。备份面恢复不在本方法(走 restoreBackups)。
     */
    public void restoreProgramFace(String planId) throws IOException {
        Path planDir = snapshotRoot.resolve(planId);
        verifyManifest(planDir);
        Set<String> snapshotPaths = manifestPaths(planDir);

        if (snapshotPaths.contains("config/integrations.yml.absent")) {
            Files.deleteIfExists(integrationsYml);
        } else if (snapshotPaths.contains("config/integrations.yml")) {
            restoreBytes(planDir.resolve("config/integrations.yml"), integrationsYml);
        }

        if (hasPrefix(snapshotPaths, "config/config_entries/")) {
            SnapshotFiles.deleteRecursively(configEntriesRoot);
            copyTree(planDir.resolve("config/config_entries"), configEntriesRoot);
        }
        if (hasPrefix(snapshotPaths, "config/integrations/")) {
            SnapshotFiles.deleteRecursively(integrationsItemDir);
            copyTree(planDir.resolve("config/integrations"), integrationsItemDir);
        }
    }

    /**
     * 备份面恢复:账本驱动——只派发给账本中 {@code backup:<坐标>} 步 DONE 的参与者
     * (硬规则:盲广播会让未备份者从空槽恢复=数据事故),restore 结果对称记
     * {@code restore:<坐标>} 步。backup 成功者已从注册表消失(窗口中卸载等异常形态)
     * =显式拒绝不静默跳过——数据恢复面缺参与者必须显形。
     */
    public void restoreBackups(String planId) throws IOException {
        verifyManifest(snapshotRoot.resolve(planId));
        for (InstallationLedgerStore.Step step : doneBackupSteps(planId)) {
            String coordinate = step.getName().substring(BACKUP_STEP_PREFIX.length());
            IntegrationBase participant = integrationRegistry.getIntegration(coordinate);
            if (!(participant instanceof BackupHook)) {
                throw new IllegalStateException("backup 成功参与者不可恢复(注册表无该坐标或其"
                        + "不再实现 BackupHook): " + coordinate + " - " + planId);
            }
            recordHookStep(planId, RESTORE_STEP_PREFIX + coordinate, (BackupHook) participant, false);
        }
    }

    /** 读安装新增文件清单(rollback 据此逐文件删除新增 jar) */
    public List<AddedFile> readAddedFiles(String planId) throws IOException {
        Path file = snapshotRoot.resolve(planId).resolve(ADDED_FILES_JSON);
        if (!Files.isRegularFile(file)) {
            throw new IllegalStateException("快照无 added-files 清单: " + planId);
        }
        List<Map<String, Object>> wire = JSON.parseObject(new String(Files.readAllBytes(file),
                StandardCharsets.UTF_8), new TypeReference<List<Map<String, Object>>>() {});
        List<AddedFile> result = new ArrayList<>();
        for (Map<String, Object> item : wire) {
            result.add(new AddedFile(strOf(item.get("coordinate"), ADDED_FILES_JSON + " coordinate"),
                    strOf(item.get("version"), ADDED_FILES_JSON + " version"),
                    strOf(item.get("path"), ADDED_FILES_JSON + " path"),
                    strOf(item.get("sha256"), ADDED_FILES_JSON + " sha256")));
        }
        return result;
    }

    // ==================== BackupHook 广播 ====================

    /**
     * B2 广播:遍历集成注册表(坐标排序=派发顺序确定性,账本时间线可读),
     * {@code instanceof BackupHook} 参与者逐个同步调用 backup()(带超时);
     * 不实现=明示不需要备份(缺席语义合法)。任一失败/超时=窗口中止
     * (异常上抛由调用方转 FAILED,半快照由 snapshotForUpgrade 清理)。
     */
    private void dispatchBackupHooks(String planId) {
        for (String coordinate : new TreeSet<>(integrationRegistry.getAllCoordinates())) {
            IntegrationBase participant = integrationRegistry.getIntegration(coordinate);
            if (!(participant instanceof BackupHook)) {
                continue;
            }
            recordHookStep(planId, BACKUP_STEP_PREFIX + coordinate, (BackupHook) participant, true);
        }
    }

    /** 单参与者钩子调用+账本步骤记录(PENDING→DONE;失败/超时记 FAILED 后原样上抛) */
    private void recordHookStep(String planId, String stepName, BackupHook hook, boolean backupPhase) {
        ledgerStore.recordStep(planId, stepName, InstallationLedgerStore.StepState.PENDING, null);
        try {
            callHookWithTimeout(hook, backupPhase);
        } catch (RuntimeException e) {
            ledgerStore.recordStep(planId, stepName, InstallationLedgerStore.StepState.FAILED, e.getMessage());
            throw e;
        }
        ledgerStore.recordStep(planId, stepName, InstallationLedgerStore.StepState.DONE, null);
    }

    /**
     * 同步调用带超时:钩子在单线程执行器中跑,本线程 {@code Future.get(超时)} 等待;
     * 超时=cancel(true)(中断钩子线程,静默/解冻类实现的内部等待靠中断退出)+
     * 异常上抛。执行器随调用创建销毁——升级窗是罕见路径,不为它常驻线程。
     */
    private void callHookWithTimeout(BackupHook hook, boolean backupPhase) {
        String phase = backupPhase ? "backup" : "restore";
        ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "upgrade-" + phase + "-hook");
            thread.setDaemon(true);
            return thread;
        });
        try {
            Future<Object> future = executor.submit(() -> {
                if (backupPhase) {
                    hook.backup();
                } else {
                    hook.restore();
                }
                return null;
            });
            future.get(hookTimeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            throw new IllegalStateException("BackupHook " + phase + "() 超时("
                    + hookTimeoutSeconds + "s),升级窗口中止: " + hook.getClass().getName(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("BackupHook " + phase + "() 等待被中断,升级窗口中止", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RuntimeException) {
                throw (RuntimeException) cause;   // 钩子自抛异常原样上抛(不换型不失真)
            }
            if (cause instanceof Error) {
                throw (Error) cause;
            }
            throw new IllegalStateException("BackupHook " + phase + "() 抛非运行时异常: "
                    + cause, cause);
        } finally {
            executor.shutdownNow();
        }
    }

    /** 账本中 {@code backup:<坐标>} 步 DONE 的参与者名单(按账本记录序=派发序) */
    private List<InstallationLedgerStore.Step> doneBackupSteps(String planId) {
        InstallationLedgerStore.Ledger ledger = ledgerStore.read(planId)
                .orElseThrow(() -> new IllegalStateException("无台账记录,备份面恢复不可达: " + planId));
        List<InstallationLedgerStore.Step> done = new ArrayList<>();
        for (InstallationLedgerStore.Step step : ledger.getSteps()) {
            if (step.getName().startsWith(BACKUP_STEP_PREFIX)
                    && step.getState() == InstallationLedgerStore.StepState.DONE) {
                done.add(step);
            }
        }
        return done;
    }

    // ==================== 快照各面 ====================

    /** ①jar 集:integrations.yml 全坐标 → Maven 布局硬链接(跨 FS 回退整拷);缺失=fail-closed */
    private void snapshotJars(Path planDir) throws IOException {
        for (String[] gav : integrationsCoordinates()) {
            String groupId = gav[0];
            String artifactId = gav[1];
            String version = gav[2];
            String jarPath = System.getProperty("user.home") + "/.m2/repository/"
                    + groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/"
                    + artifactId + "-" + version + ".jar";
            Path source = Paths.get(jarPath);
            if (!Files.isRegularFile(source)) {
                throw new IllegalStateException("快照不完整: " + groupId + ":" + artifactId
                        + ":" + version + " 缺失——拒绝进入变更");
            }
            Path target = planDir.resolve("jars").resolve(groupId.replace('.', '/'))
                    .resolve(artifactId).resolve(version).resolve(artifactId + "-" + version + ".jar");
            linkOrCopy(source, target);
        }
    }

    /** ②core 程序文件(测试缝 coreProgramLocation 重定向;surefire 下非 fat jar 形态) */
    private void snapshotCoreProgram(Path planDir) throws IOException {
        Path source = coreProgramLocation();
        Path target = planDir.resolve("core").resolve(source.getFileName().toString());
        linkOrCopy(source, target);
    }

    /** ③配置树:integrations.yml 字节+config_entries/ 整树+integrations/*.yml 整树 */
    private void snapshotConfigTree(Path planDir) throws IOException {
        Path configDir = planDir.resolve("config");
        Files.createDirectories(configDir);
        if (!Files.isRegularFile(integrationsYml)) {
            throw new IllegalStateException("integrations.yml 缺失,升级快照不完整: " + integrationsYml);
        }
        Files.copy(integrationsYml, configDir.resolve("integrations.yml"),
                StandardCopyOption.REPLACE_EXISTING);
        if (Files.isDirectory(configEntriesRoot)) {
            copyTree(configEntriesRoot, configDir.resolve("config_entries"));
        }
        if (Files.isDirectory(integrationsItemDir)) {
            copyTree(integrationsItemDir, configDir.resolve("integrations"));
        }
    }

    /** 硬链接+跨 FS 回退缝:protected 非 final,测试覆写注入链接失败模拟跨文件系统 */
    protected void createHardLink(Path source, Path target) throws IOException {
        Files.createLink(target, source);
    }

    /** core 程序文件定位缝:protected 非 final,测试覆写指向夹具文件(surefire 下为 classes 目录) */
    protected Path coreProgramLocation() {
        try {
            return Paths.get(EcatCore.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        } catch (Exception e) {
            throw new IllegalStateException("core 程序文件定位失败: " + e.getMessage(), e);
        }
    }

    private void linkOrCopy(Path source, Path target) throws IOException {
        Files.createDirectories(target.getParent());
        try {
            createHardLink(source, target);
        } catch (IOException crossFs) {
            // 跨文件系统(或同 FS 链接失败):回退整拷,内容不变量一致
            log.warn("硬链接失败,回退整拷: " + source + " - " + crossFs.getMessage());
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** integrations.yml 全坐标(去重保序);缺 version=manifest 异常显形 */
    private List<String[]> integrationsCoordinates() {
        if (!Files.isRegularFile(integrationsYml)) {
            throw new IllegalStateException("integrations.yml 缺失,升级快照不完整: " + integrationsYml);
        }
        Yaml yaml = new Yaml();
        Map<String, Object> doc;
        try (InputStream in = Files.newInputStream(integrationsYml)) {
            doc = yaml.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("integrations.yml 读取失败: " + integrationsYml
                    + " - " + e.getMessage(), e);
        }
        Object node = doc == null ? null : doc.get("integrations");
        if (!(node instanceof Map)) {
            throw new IllegalStateException("integrations.yml 缺 integrations 节,升级快照中止: " + integrationsYml);
        }
        List<String[]> result = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        Map<String, Map<String, Object>> integrations = (Map<String, Map<String, Object>>) node;
        for (Map.Entry<String, Map<String, Object>> entry : integrations.entrySet()) {
            Map<String, Object> config = entry.getValue();
            String groupId = config.get("groupId") == null ? null : config.get("groupId").toString();
            String artifactId = config.get("artifactId") == null ? null : config.get("artifactId").toString();
            String version = config.get("version") == null ? null : config.get("version").toString();
            if (groupId == null || artifactId == null || version == null) {
                throw new IllegalStateException("integrations.yml 坐标缺 groupId/artifactId/version,"
                        + " 升级快照中止: " + entry.getKey());
            }
            if (seen.add(groupId + ":" + artifactId + ":" + version)) {
                result.add(new String[]{groupId, artifactId, version});
            }
        }
        return result;
    }

    // ==================== manifest(快照成立标志) ====================

    /** manifest 最后写:先写内容后写清单;各条目 sha256 写入时计算 */
    private void writeManifest(Path planDir) throws IOException {
        List<Map<String, Object>> files = new ArrayList<>();
        try (Stream<Path> walked = Files.walk(planDir)) {
            for (Path file : walked.filter(Files::isRegularFile).sorted().toArray(Path[]::new)) {
                String relative = planDir.relativize(file).toString().replace('\\', '/');
                if (relative.equals(MANIFEST_FILE)) {
                    continue;
                }
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("path", relative);
                entry.put("sha256", SnapshotFiles.sha256Hex(file));
                files.add(entry);
            }
        }
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("planId", planDir.getFileName().toString());
        doc.put("files", files);
        Files.write(planDir.resolve(MANIFEST_FILE),
                JSON.toJSONString(doc).getBytes(StandardCharsets.UTF_8));
    }

    /** manifest 逐条 sha256 对账;路径越界/缺清单=快照不成立,显形拒绝 */
    private void verifyManifest(Path planDir) throws IOException {
        Map<String, Object> doc = readManifest(planDir);
        Object files = doc.get("files");
        if (!(files instanceof List)) {
            throw new IllegalStateException("manifest 缺 files 节(快照未成立): " + planDir);
        }
        for (Object itemObj : (List<?>) files) {
            Map<?, ?> item = (Map<?, ?>) itemObj;
            String relative = item.get("path") == null ? null : item.get("path").toString();
            String expected = item.get("sha256") == null ? null : item.get("sha256").toString();
            if (relative == null || expected == null) {
                throw new IllegalStateException("manifest 条目缺 path/sha256: " + planDir);
            }
            requireContainedPath(relative);
            Path snapshotFile = planDir.resolve(relative);
            if (!Files.isRegularFile(snapshotFile)
                    || !expected.equals(SnapshotFiles.sha256Hex(snapshotFile))) {
                throw new IllegalStateException("快照校验失败: " + relative + "(" + planDir + ")");
            }
        }
    }

    private Map<String, Object> readManifest(Path planDir) {
        Path manifestFile = planDir.resolve(MANIFEST_FILE);
        if (!Files.isRegularFile(manifestFile)) {
            throw new IllegalStateException("快照不完整(无 manifest,快照未成立): " + planDir);
        }
        try {
            Map<String, Object> doc = JSON.parseObject(new String(Files.readAllBytes(manifestFile),
                    StandardCharsets.UTF_8), new TypeReference<Map<String, Object>>() {});
            if (doc == null) {
                throw new IllegalStateException("manifest 为空文档(快照未成立): " + planDir);
            }
            return doc;
        } catch (IOException e) {
            throw new IllegalStateException("manifest 读取失败: " + manifestFile + " - " + e.getMessage(), e);
        }
    }

    private Set<String> manifestPaths(Path planDir) throws IOException {
        Map<String, Object> doc = readManifest(planDir);
        Set<String> paths = new HashSet<>();
        for (Object itemObj : (List<?>) doc.get("files")) {
            Map<?, ?> item = (Map<?, ?>) itemObj;
            String relative = item.get("path").toString();
            requireContainedPath(relative);
            paths.add(relative);
        }
        return paths;
    }

    /** manifest 相对路径越界守卫(绝对路径/.. 上跳=篡改形态,拒绝) */
    private void requireContainedPath(String relative) {
        if (relative.startsWith("/") || relative.startsWith("\\")
                || relative.contains("..") || relative.contains(":")) {
            throw new IllegalStateException("manifest 路径非法(疑似篡改): " + relative);
        }
    }

    // ==================== 恢复辅助 ====================

    /** yml 字节还原:tmp+rename 原子替换(跨 FS 降级普通 move)——与安装路径还原同款 */
    private void restoreBytes(Path source, Path target) throws IOException {
        Path parent = target.getParent() != null ? target.getParent() : Paths.get(".");
        Files.createDirectories(parent);
        Path tmp = Files.createTempFile(parent, target.getFileName().toString(), ".restore");
        Files.copy(source, tmp, StandardCopyOption.REPLACE_EXISTING);
        try {
            Files.move(tmp, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void copyTree(Path sourceRoot, Path targetRoot) throws IOException {
        Files.createDirectories(targetRoot);
        try (Stream<Path> walked = Files.walk(sourceRoot)) {
            for (Path source : walked.filter(Files::isRegularFile).sorted().toArray(Path[]::new)) {
                Path target = targetRoot.resolve(sourceRoot.relativize(source));
                Files.createDirectories(target.getParent());
                Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
            }
        }
    }

    private boolean hasPrefix(Set<String> paths, String prefix) {
        for (String path : paths) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    private String strOf(Object value, String what) {
        if (value == null) {
            throw new IllegalStateException("added-files 条目缺 " + what);
        }
        return value.toString();
    }

    private void cleanupQuietly(Path planDir, String planId) {
        try {
            SnapshotFiles.deleteRecursively(planDir);
        } catch (IOException cleanupFailure) {
            log.error("快照清理失败(留有残目录): " + planId + " - " + cleanupFailure.getMessage());
        }
    }
}
