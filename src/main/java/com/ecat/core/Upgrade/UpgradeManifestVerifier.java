/*
 * Copyright (c) 2026 ECAT Team
 */

package com.ecat.core.Upgrade;

import com.ecat.core.Version.CoreVersions;
import com.ecat.core.Version.Version;
import com.ecat.core.Version.VersionRange;
import lombok.Value;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 升级清单本地复验门:四门全量复验,产出失败清单(不首错即断,一次报全)。
 *
 * <p>防御纵深定位:云端计划(或本地入队壳)在执行前于客户端复验——服务端缺陷
 * 不得变全舰队砖。boot 侧 B1(复验不过=PREREQ_FAILED)与在线侧入队前预验
 * 双消费方;纯读盘+纯计算,零写零网络。db 约定块复验门(T-5-6 规则 2~6)随
 * db: 块退役而退役——旧清单残留的 db_conventions 键由反序列化自然忽略,不再校验。</p>
 *
 * <p>类不变量:把一切异常形态翻译为失败清单而非上抛——复验器的职责就是让
 * 坏 manifest 显形为可读失败。唯一例外:{@link CoreVersions#current()} 的
 * IllegalStateException(core 自身版本不可得)属装配缺陷而非 manifest 缺陷,
 * 上抛显形,不折叠成 manifest 失败掩盖环境灾难。</p>
 *
 * <p>复验失败分类码词汇表(B1 PREREQ_FAILED 原因与队列页面共用):
 * MANIFEST_MALFORMED(缺必填字段/产源值域外/sha256 词形非法)、
 * FILE_MISSING(文件不在本地仓预期路径)、SHA256_MISMATCH(在位但哈希不符)、
 * REQUIRES_CORE_UNSATISFIED(core 约束缺失/不可解析/不满足)、
 * DEPENDENCY_INCOMPLETE(依赖无 resolution_map 终态或终态 jar 不在位)。</p>
 *
 * <p>产源值域 {"resolve","local"}(rev-T-1-7-T-1-6 lead 裁定):resolve=云端
 * resolve 产源;local=T-1-3 壳写侧本地产源(安装降级/本地升级入队)。local 是
 * 真实产源语义,非复验豁免——其余各门对两产源同一执法。本地产源形态无
 * resolution_map/resolved_dependencies 面,门4 按字段在位性自然空转。</p>
 *
 * @author coffee
 */
public class UpgradeManifestVerifier {

    /** 产源:云端 resolve 计算 */
    public static final String SOURCE_RESOLVE = "resolve";

    /** 产源:本地壳写侧(T-1-3 安装降级/本地升级入队) */
    public static final String SOURCE_LOCAL = "local";

    private static final Pattern SHA256_HEX = Pattern.compile("^[0-9a-f]{64}$");

    private static final Set<String> LEGAL_SOURCES =
            new HashSet<>(Arrays.asList(SOURCE_RESOLVE, SOURCE_LOCAL));

    /**
     * 四门复验:门间不短路,门内全量收集;失败恒进清单不上抛
     * (除 core 版本不可得的装配缺陷,见类注释)。
     */
    public VerifyResult verify(UpgradeManifest manifest) {
        List<VerifyFailure> failures = new ArrayList<>();
        verifyIntegrity(manifest, failures);
        if (manifest.getItems() != null) {
            for (UpgradeManifest.UpgradeItem item : manifest.getItems()) {
                verifyFiles(item, failures);
                verifyRequiresCore(item, failures);
                verifyDependencies(item, manifest, failures);
            }
        }
        return new VerifyResult(failures.isEmpty(), failures);
    }

    // ==================== 门1 完整性 ====================

    /**
     * 壳字段非空(planId/createdAt/coreVersion/source)+产源值域+items 非空+
     * 逐条 target_version/files 非空+sha256 词形。逐项独立报出(全量清单精神)。
     */
    private void verifyIntegrity(UpgradeManifest manifest, List<VerifyFailure> failures) {
        requireNonEmpty(manifest.getPlanId(), "planId", failures);
        requireNonEmpty(manifest.getCreatedAt(), "createdAt", failures);
        requireNonEmpty(manifest.getCoreVersion(), "coreVersion", failures);
        if (manifest.getSource() == null || manifest.getSource().isEmpty()) {
            failures.add(new VerifyFailure("MANIFEST_MALFORMED", "壳字段缺失: source"));
        } else if (!LEGAL_SOURCES.contains(manifest.getSource())) {
            failures.add(new VerifyFailure("MANIFEST_MALFORMED",
                    "source 不在合法产源值域: " + manifest.getSource()
                            + "(合法={resolve,local},防手编 manifest 绕过产源链)"));
        }
        if (manifest.getItems() == null || manifest.getItems().isEmpty()) {
            failures.add(new VerifyFailure("MANIFEST_MALFORMED",
                    "items 缺失或为空(空计划不入队,manifest 恒非空)"));
            return;
        }
        for (UpgradeManifest.UpgradeItem item : manifest.getItems()) {
            String coordinate = coordinateOf(item);
            if (item.getTargetVersion() == null || item.getTargetVersion().isEmpty()) {
                failures.add(new VerifyFailure("MANIFEST_MALFORMED",
                        coordinate + " 缺 target_version"));
            }
            if (item.getFiles() == null || item.getFiles().isEmpty()) {
                failures.add(new VerifyFailure("MANIFEST_MALFORMED",
                        coordinate + " files 缺失或为空(升级原子单元至少含发布产物一件)"));
                continue;
            }
            for (UpgradeManifest.FileEntry file : item.getFiles()) {
                if (file.getSha256() == null || !SHA256_HEX.matcher(file.getSha256()).matches()) {
                    failures.add(new VerifyFailure("MANIFEST_MALFORMED",
                            coordinate + " 文件条目 sha256 词形非法(须 64 位小写十六进制): "
                                    + file.getFilename() + " -> " + file.getSha256()));
                }
            }
        }
    }

    private void requireNonEmpty(String value, String field, List<VerifyFailure> failures) {
        if (value == null || value.isEmpty()) {
            failures.add(new VerifyFailure("MANIFEST_MALFORMED", "壳字段缺失: " + field));
        }
    }

    // ==================== 门2 文件在位+哈希 ====================

    /**
     * 逐文件按本地仓路径规则推导(~/.m2/repository/{groupId 点转斜杠}/artifactId/version/
     * filename),不存在或非常规文件=FILE_MISSING(detail 含实际形态);在位则流式重算
     * sha256(8KB 缓冲,与下载侧同缓冲口径)比对,不等=SHA256_MISMATCH(明细含期望/实际)。
     */
    private void verifyFiles(UpgradeManifest.UpgradeItem item, List<VerifyFailure> failures) {
        if (item.getFiles() == null) {
            return;   // 门1 已报 files 缺失,本门无文件可复验
        }
        String coordinate = coordinateOf(item);
        for (UpgradeManifest.FileEntry file : item.getFiles()) {
            Path local = m2Path(file.getGroupId(), file.getArtifactId(),
                    file.getVersion(), file.getFilename());
            if (!Files.isRegularFile(local)) {
                failures.add(new VerifyFailure("FILE_MISSING",
                        coordinate + " 落盘文件缺失或形态畸形: " + local
                                + "(实际形态=" + actualForm(local) + ")"));
                continue;
            }
            String actual;
            try {
                actual = SnapshotFiles.sha256Hex(local);
            } catch (RuntimeException e) {
                failures.add(new VerifyFailure("FILE_MISSING",
                        coordinate + " 落盘文件不可读: " + local + " - " + e.getMessage()));
                continue;
            }
            if (!actual.equals(file.getSha256())) {
                failures.add(new VerifyFailure("SHA256_MISMATCH",
                        coordinate + " 落盘文件 sha256 不符: " + file.getFilename()
                                + "(期望 " + file.getSha256() + " 实测 " + actual + ")"));
            }
        }
    }

    /** 实际形态人话(门2 detail 用):常规文件=大小,目录=directory,不存在=absent,余=other */
    private String actualForm(Path path) {
        if (!Files.exists(path)) {
            return "absent";
        }
        if (Files.isDirectory(path)) {
            return "directory";
        }
        if (Files.isRegularFile(path)) {
            try {
                return "regular-file(" + Files.size(path) + "B)";
            } catch (Exception e) {
                return "regular-file(size 不可读)";
            }
        }
        return "other";
    }

    // ==================== 门3 requires_core ====================

    /**
     * 三形态同判(与 T-3-2 版本门同语义):缺失=报/解析失败=报/不满足=报;
     * actual=CoreVersions.current()(ISE 上抛=装配缺陷显形,见类注释)。
     */
    private void verifyRequiresCore(UpgradeManifest.UpgradeItem item, List<VerifyFailure> failures) {
        String coordinate = coordinateOf(item);
        String constraint = item.getRequiresCore();
        if (constraint == null || constraint.isEmpty()) {
            failures.add(new VerifyFailure("REQUIRES_CORE_UNSATISFIED",
                    coordinate + " 未声明 requires_core(缺失)——版本契约 fail-closed,"
                            + "与运行期加载门同语义拒绝"));
            return;
        }
        try {
            VersionRange range = VersionRange.parse(constraint);
            if (!range.satisfies(Version.parse(CoreVersions.current()))) {
                failures.add(new VerifyFailure("REQUIRES_CORE_UNSATISFIED",
                        coordinate + " requires_core(" + constraint + ") 与当前 core 版本("
                                + CoreVersions.current() + ")不满足"));
            }
        } catch (IllegalArgumentException e) {
            failures.add(new VerifyFailure("REQUIRES_CORE_UNSATISFIED",
                    coordinate + " requires_core 无法解析: " + constraint + " - " + e.getMessage()));
        }
    }

    // ==================== 门4 依赖齐全 ====================

    /**
     * 逐 resolved_dependencies:(coordinate,version) 必在 resolution_map 有同坐标同版本
     * 终态条目,且该终态坐标的 jar 按本地仓路径规则推导后在位——缺一即半个。
     * 本地产源形态无 resolved_dependencies/resolution_map 面(空列表),本门自然空转;
     * 其依赖在位性由 B1 读目标 jar 声明式复验承担(T-1-5 §门5 双形态分派)。
     */
    private void verifyDependencies(UpgradeManifest.UpgradeItem item, UpgradeManifest manifest,
                                    List<VerifyFailure> failures) {
        if (item.getResolvedDependencies() == null) {
            return;
        }
        String coordinate = coordinateOf(item);
        for (UpgradeManifest.ResolvedDependency dep : item.getResolvedDependencies()) {
            String depCoordinate = dep.getGroupId() + ":" + dep.getArtifactId();
            UpgradeManifest.ResolutionEntry terminal = terminalOf(manifest, dep);
            if (terminal == null) {
                failures.add(new VerifyFailure("DEPENDENCY_INCOMPLETE",
                        coordinate + " 依赖 " + depCoordinate + "@" + dep.getVersion()
                                + " 无 resolution_map 终态条目(依赖闭包不全,缺一即半个)"));
                continue;
            }
            Path jar = m2Path(terminal.getGroupId(), terminal.getArtifactId(),
                    terminal.getVersion(), terminal.getArtifactId() + "-" + terminal.getVersion() + ".jar");
            if (!Files.isRegularFile(jar)) {
                failures.add(new VerifyFailure("DEPENDENCY_INCOMPLETE",
                        coordinate + " 依赖终态件不在位: " + depCoordinate + "@"
                                + terminal.getVersion() + " -> " + jar));
            }
        }
    }

    /** resolution_map 同坐标同版本终态查找;无 map 面(本地产源)返回 null */
    private UpgradeManifest.ResolutionEntry terminalOf(UpgradeManifest manifest,
                                                       UpgradeManifest.ResolvedDependency dep) {
        if (manifest.getResolutionMap() == null) {
            return null;
        }
        for (UpgradeManifest.ResolutionEntry entry : manifest.getResolutionMap()) {
            if (dep.getGroupId().equals(entry.getGroupId())
                    && dep.getArtifactId().equals(entry.getArtifactId())
                    && dep.getVersion().equals(entry.getVersion())) {
                return entry;
            }
        }
        return null;
    }

    // ==================== 公共件 ====================

    private static String coordinateOf(UpgradeManifest.UpgradeItem item) {
        return item.getGroupId() + ":" + item.getArtifactId();
    }

    /**
     * 本地仓路径推导规则:~/.m2/repository/{groupId 点转斜杠}/{artifactId}/{version}/{filename}
     * (boot 拼路径同款规则,UpgradeOrchestrator.m2Path 同形;文件条目→本地路径的唯一推导式)。
     */
    private static Path m2Path(String groupId, String artifactId, String version, String filename) {
        String repo = System.getProperty("user.home") + "/.m2/repository";
        return Paths.get(repo, groupId.replace('.', '/'), artifactId, version, filename);
    }

    /**
     * 复验结果:passed=失败清单为空;failures=全量失败清单(不首错即断)。
     */
    @Value
    public static class VerifyResult {
        boolean passed;
        List<VerifyFailure> failures;
    }
}
