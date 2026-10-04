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

import com.ecat.core.Config.EcatConfig;
import com.ecat.core.Utils.Log;
import com.ecat.core.Utils.LogFactory;
import com.ecat.core.Utils.YamlAtomicFileWriter;
import lombok.Data;
import lombok.Value;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * 安装/升级状态台账({@code .ecat-data/installations/{planId}/ledger.yml})唯一读写口。
 *
 * <p>台账记录安装/升级两族流程的执行史(plan/版本/步骤/时间戳/结果),REST status
 * 响应=本结构投影,rollback 以「无台账即明确异常」为准入条件。与 boot 窗 state.yml
 * 双轨:本台账记流程执行史,state.yml 记 boot 窗口推进态,读写方与生命周期均不同。</p>
 *
 * <p>写规则:每次状态变化=整文件重写(共用件 YamlAtomicFileWriter:tmp→dump→回读
 * 验证→原子 rename 六步);步骤记录追加式+幂等重写(同名步骤重复记录更新原条目,
 * 不产生重复行)。写失败抛 IllegalStateException(带原因链,不吞不兜底)。</p>
 *
 * <p>时间源:构造注入 {@link Clock}(生产=系统 UTC;单测注入受控时钟,使
 * startedAt&lt;finishedAt 可确定性断言,不依赖墙钟精度)。</p>
 *
 * @author coffee
 */
public class InstallationLedgerStore {

    private static final Log log = LogFactory.getLogger(InstallationLedgerStore.class);

    static final int SCHEMA_VERSION = 1;

    /** 流程族:INSTALL=当场生效安装;UPGRADE=重启生效队列升级 */
    public enum PlanType { INSTALL, UPGRADE }

    /** 计划终态词汇(与状态机大写词汇族一致,不发明第二套;CANCELLED=停队作废留台账) */
    public enum PlanResult { RUNNING, SUCCESS, ROLLED_BACK, FAILED, CANCELLED }

    /** 步骤状态词汇(与状态机大写词汇族一致) */
    public enum StepState { PENDING, DONE, FAILED }

    /** 逐坐标版本轨迹 */
    @Value
    public static class CoordinateTrack {
        String coordinate;
        String fromVersion;
        String toVersion;
    }

    /** 降级记录(降级原因串承载:台账为权威载体,结构化日志为伴面) */
    @Value
    public static class Degradation {
        String coordinate;
        String reason;
        /** 降级产生的队列计划 id(安装台账 → 队列计划的交叉引用链) */
        String queuePlanId;
    }

    /** 步骤时间线条目 */
    @Value
    public static class Step {
        String name;
        StepState state;
        Instant startedAt;
        Instant finishedAt;
        String error;
    }

    /** 台账值对象(读写均为整文件口径;字段=ledger.yml schema 键位) */
    @Data
    public static class Ledger {
        private int schemaVersion = SCHEMA_VERSION;
        private String planId;
        private PlanType type;
        private PlanResult result;
        private Instant createdAt;
        private Instant finishedAt;
        private String snapshotPath;
        private List<CoordinateTrack> coordinates = new ArrayList<>();
        private List<Step> steps = new ArrayList<>();
        private List<String> retentionPruned = new ArrayList<>();
        private List<Degradation> degradations = new ArrayList<>();

        public boolean isTerminal() {
            return result != PlanResult.RUNNING;
        }
    }

    private final Path installationsRoot;
    private final Path backupsRoot;
    private final Clock clock;
    private final YamlAtomicFileWriter yamlWriter = new YamlAtomicFileWriter();

    public InstallationLedgerStore(Path installationsRoot, Path backupsRoot, Clock clock) {
        this.installationsRoot = installationsRoot;
        this.backupsRoot = backupsRoot;
        this.clock = clock;
    }

    /**
     * 创建 RUNNING 台账。已存在同 planId 台账=调用方时序缺陷,显式拒绝。
     */
    public void create(String planId, PlanType type, String snapshotPath,
                       List<CoordinateTrack> coordinates, List<Degradation> degradations) {
        if (read(planId).isPresent()) {
            throw new IllegalStateException("台账已存在,拒绝重复创建: " + planId);
        }
        Ledger ledger = new Ledger();
        ledger.setPlanId(planId);
        ledger.setType(type);
        ledger.setResult(PlanResult.RUNNING);
        ledger.setCreatedAt(Instant.now(clock));
        ledger.setSnapshotPath(snapshotPath);
        if (coordinates != null) {
            ledger.getCoordinates().addAll(coordinates);
        }
        if (degradations != null) {
            ledger.getDegradations().addAll(degradations);
        }
        write(ledger);
    }

    /**
     * 记录步骤:首记=建条目(startedAt=now);重复记录=更新原条目(幂等,不产生重复行),
     * 进入 DONE/FAILED 时补 finishedAt。已 DONE/FAILED 的步骤重复记录同态=无操作。
     */
    public void recordStep(String planId, String name, StepState state, String error) {
        Ledger ledger = readOrThrow(planId);
        Step existing = findStep(ledger, name);
        if (existing == null) {
            Instant now = Instant.now(clock);
            Instant finishedAt = state == StepState.DONE || state == StepState.FAILED ? now : null;
            ledger.getSteps().add(new Step(name, state, now, finishedAt, error));
        } else {
            if (existing.getState() == state
                    && (state == StepState.DONE || state == StepState.FAILED)) {
                return; // 幂等:终态重复记录同态无操作
            }
            Instant finishedAt = existing.getFinishedAt();
            if ((state == StepState.DONE || state == StepState.FAILED) && finishedAt == null) {
                finishedAt = Instant.now(clock);
            }
            ledger.getSteps().set(ledger.getSteps().indexOf(existing),
                    new Step(name, state, existing.getStartedAt(), finishedAt, error));
        }
        write(ledger);
    }

    /** 写终态+finishedAt。已终态再 finish=状态机违例,显式拒绝。 */
    public void finish(String planId, PlanResult result) {
        if (result == PlanResult.RUNNING) {
            throw new IllegalArgumentException("finish 不接受 RUNNING(非终态): " + planId);
        }
        Ledger ledger = readOrThrow(planId);
        if (ledger.isTerminal()) {
            throw new IllegalStateException("台账已终态(" + ledger.getResult() + "),拒绝再写终态: " + planId);
        }
        ledger.setResult(result);
        ledger.setFinishedAt(Instant.now(clock));
        write(ledger);
    }

    /** 读台账;无台账=empty(消费方对 empty 的处置是语义的一部分:rollback 抛明确异常) */
    public Optional<Ledger> read(String planId) {
        Path file = ledgerFile(planId);
        if (!Files.isRegularFile(file)) {
            return Optional.empty();
        }
        Yaml yaml = new Yaml();
        Map<String, Object> doc;
        try (InputStream in = Files.newInputStream(file)) {
            doc = yaml.load(in);
        } catch (IOException e) {
            throw new IllegalStateException("台账读取失败: " + file + " - " + e.getMessage(), e);
        }
        if (doc == null) {
            throw new IllegalStateException("台账文件为空文档: " + file);
        }
        return Optional.of(fromMap(doc, file));
    }

    /**
     * 快照保留清理:按 backups/ 下各 planId 的台账 createdAt 降序保留前 N
     * ({@code ecat.upgrade.backup.retention},默认 3);被剪者目录树删除,剪除名单
     * 追加记入最新台账 retentionPruned。进行中 plan(RUNNING)恒保留;无法读到台账
     * 的快照目录不参与排序、不清理(无法定位年龄的目录不剪,保守不丢数据)。
     */
    public void pruneRetention() {
        int retention = EcatConfig.upgradeBackupRetention();
        List<PlanIdAge> aged = new ArrayList<>();
        if (!Files.isDirectory(backupsRoot)) {
            return;
        }
        try (Stream<Path> walked = Files.list(backupsRoot)) {
            for (Path dir : walked.filter(Files::isDirectory).toArray(Path[]::new)) {
                String planId = dir.getFileName().toString();
                Optional<Ledger> ledger = read(planId);
                if (!ledger.isPresent()) {
                    log.warn("快照目录无台账,保留不剪(无法定位年龄): " + planId);
                    continue;
                }
                if (ledger.get().getResult() == PlanResult.RUNNING) {
                    continue; // 进行中 plan 恒保留
                }
                aged.add(new PlanIdAge(planId, ledger.get().getCreatedAt()));
            }
        } catch (IOException e) {
            throw new IllegalStateException("快照目录遍历失败: " + backupsRoot + " - " + e.getMessage(), e);
        }
        aged.sort(Comparator.comparing(PlanIdAge::getCreatedAt).reversed());
        if (aged.size() <= retention) {
            return;
        }
        List<String> prunedIds = new ArrayList<>();
        for (PlanIdAge candidate : aged.subList(retention, aged.size())) {
            Path snapshotDir = backupsRoot.resolve(candidate.getPlanId());
            try {
                DbDumpExecutor.deleteRecursively(snapshotDir);
            } catch (IOException e) {
                throw new IllegalStateException("清理时目录删除失败: " + snapshotDir
                        + " - " + e.getMessage(), e);
            }
            prunedIds.add(candidate.getPlanId());
        }
        // 剪除名单记入最新台账(kept[0]=createdAt 最新);无最新台账不追加(名单仍已剪除)
        Ledger newest = read(aged.get(0).getPlanId()).orElse(null);
        if (newest != null) {
            for (String prunedId : prunedIds) {
                if (!newest.getRetentionPruned().contains(prunedId)) {
                    newest.getRetentionPruned().add(prunedId);
                }
            }
            write(newest);
        }
    }

    /** 排序中间载体(planId+台账 createdAt) */
    @Value
    private static class PlanIdAge {
        String planId;
        Instant createdAt;
    }

    // ==================== 内部:序列化与定位 ====================

    private void write(Ledger ledger) {
        try {
            yamlWriter.write(toMap(ledger), ledgerFile(ledger.getPlanId()).toFile());
        } catch (IOException e) {
            throw new IllegalStateException("台账写入失败: " + ledger.getPlanId() + " - " + e.getMessage(), e);
        }
    }

    private Ledger readOrThrow(String planId) {
        return read(planId).orElseThrow(() ->
                new IllegalStateException("无台账记录: " + planId));
    }

    private Path ledgerFile(String planId) {
        return installationsRoot.resolve(planId).resolve("ledger.yml");
    }

    private Step findStep(Ledger ledger, String name) {
        for (Step step : ledger.getSteps()) {
            if (step.getName().equals(name)) {
                return step;
            }
        }
        return null;
    }

    private Map<String, Object> toMap(Ledger ledger) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("schemaVersion", ledger.getSchemaVersion());
        doc.put("planId", ledger.getPlanId());
        doc.put("type", ledger.getType() == null ? null : ledger.getType().name());
        doc.put("result", ledger.getResult() == null ? null : ledger.getResult().name());
        doc.put("createdAt", ledger.getCreatedAt() == null ? null : ledger.getCreatedAt().toString());
        doc.put("finishedAt", ledger.getFinishedAt() == null ? null : ledger.getFinishedAt().toString());
        doc.put("snapshotPath", ledger.getSnapshotPath());
        List<Map<String, Object>> coordinates = new ArrayList<>();
        for (CoordinateTrack track : ledger.getCoordinates()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("coordinate", track.getCoordinate());
            item.put("fromVersion", track.getFromVersion());
            item.put("toVersion", track.getToVersion());
            coordinates.add(item);
        }
        doc.put("coordinates", coordinates);
        List<Map<String, Object>> steps = new ArrayList<>();
        for (Step step : ledger.getSteps()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("name", step.getName());
            item.put("state", step.getState() == null ? null : step.getState().name());
            item.put("startedAt", step.getStartedAt() == null ? null : step.getStartedAt().toString());
            item.put("finishedAt", step.getFinishedAt() == null ? null : step.getFinishedAt().toString());
            item.put("error", step.getError());
            steps.add(item);
        }
        doc.put("steps", steps);
        doc.put("retentionPruned", new ArrayList<>(ledger.getRetentionPruned()));
        List<Map<String, Object>> degradations = new ArrayList<>();
        for (Degradation degradation : ledger.getDegradations()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("coordinate", degradation.getCoordinate());
            item.put("reason", degradation.getReason());
            item.put("queuePlanId", degradation.getQueuePlanId());
            degradations.add(item);
        }
        doc.put("degradations", degradations);
        return doc;
    }

    @SuppressWarnings("unchecked")
    private Ledger fromMap(Map<String, Object> doc, Path file) {
        Object schemaVersion = doc.get("schemaVersion");
        if (!Integer.valueOf(SCHEMA_VERSION).equals(((Number) schemaVersion).intValue())) {
            throw new IllegalStateException("台账 schema 版本不支持: " + schemaVersion + "(" + file + ")");
        }
        Ledger ledger = new Ledger();
        ledger.setPlanId((String) doc.get("planId"));
        ledger.setType(PlanType.valueOf((String) doc.get("type")));
        ledger.setResult(PlanResult.valueOf((String) doc.get("result")));
        ledger.setCreatedAt(parseInstant((String) doc.get("createdAt"), file));
        Object finishedAt = doc.get("finishedAt");
        ledger.setFinishedAt(finishedAt == null ? null : parseInstant((String) finishedAt, file));
        ledger.setSnapshotPath((String) doc.get("snapshotPath"));
        List<Map<String, Object>> coordinates = (List<Map<String, Object>>) doc.getOrDefault("coordinates",
                new ArrayList<>());
        for (Map<String, Object> item : coordinates) {
            ledger.getCoordinates().add(new CoordinateTrack((String) item.get("coordinate"),
                    (String) item.get("fromVersion"), (String) item.get("toVersion")));
        }
        List<Map<String, Object>> steps = (List<Map<String, Object>>) doc.getOrDefault("steps",
                new ArrayList<>());
        for (Map<String, Object> item : steps) {
            ledger.getSteps().add(new Step((String) item.get("name"),
                    StepState.valueOf((String) item.get("state")),
                    parseInstant((String) item.get("startedAt"), file),
                    item.get("finishedAt") == null ? null : parseInstant((String) item.get("finishedAt"), file),
                    (String) item.get("error")));
        }
        Object pruned = doc.get("retentionPruned");
        if (pruned instanceof List) {
            ledger.getRetentionPruned().addAll((List<String>) pruned);
        }
        List<Map<String, Object>> degradations = (List<Map<String, Object>>) doc.getOrDefault("degradations",
                new ArrayList<>());
        for (Map<String, Object> item : degradations) {
            ledger.getDegradations().add(new Degradation((String) item.get("coordinate"),
                    (String) item.get("reason"), (String) item.get("queuePlanId")));
        }
        return ledger;
    }

    private Instant parseInstant(String value, Path file) {
        try {
            return Instant.parse(value);
        } catch (Exception e) {
            throw new IllegalStateException("台账时间戳非法(" + file + "): " + value, e);
        }
    }
}
