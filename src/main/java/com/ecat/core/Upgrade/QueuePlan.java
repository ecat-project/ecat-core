/*
 * Copyright (c) 2026 ECAT Team
 */

package com.ecat.core.Upgrade;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.ecat.core.Utils.YamlAtomicFileWriter;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.Value;

/**
 * 队列计划类型化载体:manifest.json+state.yml 的编排器自读面与 state 独占写面。
 *
 * <p>读侧契约=T-1-3 写侧(core-api UpgradeQueueStore):manifest=fastjson2、
 * state.yml=snakeyaml;两文件缺一或解析失败=UpgradeStateException(残缺请求按
 * 崩溃恢复矩阵保守路径处置,绝不猜续推)。写面仅 state(状态迁移编排器独占),
 * 走共用件 {@link YamlAtomicFileWriter}(tmp→dump→回读验证→原子 rename,与
 * T-2-3 契约同件同栈)。</p>
 *
 * <p>manifest 模型为 core 侧中性类型,不引 core-api wire 类(跨 jar 裁定);
 * 键位与队列写入器 wire 契约逐字段对齐(group_id/artifact_id 五件套)。
 * db_conventions 键已随 db: 块退役:解析只取声明键,旧清单里残留的
 * db_conventions 键自然忽略(过渡期旧清单仍在盘,禁因其报错)。</p>
 *
 * @author coffee
 */
public class QueuePlan {

    /** 状态机全词表(十六态;wire 值=name,经 state.yml 进 T-1-9 渲染,不发明第二套) */
    public enum PlanState {
        PENDING, VERIFYING, PREREQ_FAILED, BACKING_UP, BACKED_UP,
        PROGRAM_UPGRADING, PROGRAM_UPGRADED, DB_UPGRADING, LOADING,
        COMPLETE, LOADING_FAILED, ROLLING_BACK, ROLLED_BACK, ROLLBACK_FAILED,
        CANCELLED, FAILED;

        static PlanState fromWire(String wire) {
            if (wire == null) {
                throw new UpgradeStateException("state.yml state 键缺失");
            }
            try {
                return PlanState.valueOf(wire);
            } catch (IllegalArgumentException e) {
                throw new UpgradeStateException("state.yml 状态词非法(不在十六态词表): " + wire);
            }
        }
    }

    /** manifest.json 类型化视图(装载后不可变;损坏修复形态下为 null) */
    @Value
    public static class Manifest {
        int schemaVersion;
        String planId;
        String type;
        String createdAt;
        List<Item> items;
    }

    /** manifest 单坐标条目;installedVersion=null=新装降级形态(安装降级判定字段) */
    @Value
    public static class Item {
        String groupId;
        String artifactId;
        String installedVersion;
        String targetVersion;
        String requiresCore;
        List<ManifestFile> files;

        public String getCoordinate() {
            return groupId + ":" + artifactId;
        }
    }

    /** files[] 五件套(下载期已验证值,B1 复验重算比对) */
    @Value
    public static class ManifestFile {
        String kind;
        String groupId;
        String artifactId;
        String version;
        String filename;
        String sha256;
    }

    private final String planId;
    private final Manifest manifest;
    private final Path planDir;
    private final Path stateFile;

    /** state.yml 现值(整文档口径;每次迁移整体重建+原子替换) */
    private final Map<String, Object> stateDoc;

    private final YamlAtomicFileWriter stateWriter = new YamlAtomicFileWriter();

    private QueuePlan(String planId, Manifest manifest, Path planDir, Map<String, Object> stateDoc) {
        this.planId = planId;
        this.manifest = manifest;
        this.planDir = planDir;
        this.stateFile = planDir.resolve("state.yml");
        this.stateDoc = stateDoc;
    }

    // ==================== 工厂 ====================

    /**
     * 装载队列计划目录(manifest.json+state.yml 成对,缺一/解析失败/键缺失=UpgradeStateException)。
     * planId 三方一致校验(目录名=manifest=state),不一致=状态乱序损坏显形。
     */
    public static QueuePlan load(Path planDir) {
        Path manifestFile = planDir.resolve("manifest.json");
        if (!Files.isRegularFile(manifestFile)) {
            throw new UpgradeStateException("manifest.json 缺失: " + planDir);
        }
        Path stateFile = planDir.resolve("state.yml");
        if (!Files.isRegularFile(stateFile)) {
            throw new UpgradeStateException("state.yml 缺失: " + planDir);
        }
        Manifest manifest = parseManifest(manifestFile);
        Map<String, Object> stateDoc = parseState(stateFile);
        String dirName = planDir.getFileName().toString();
        if (!dirName.equals(manifest.getPlanId()) || !dirName.equals(stateDoc.get("planId"))) {
            throw new UpgradeStateException("planId 三方不一致(目录=" + dirName
                    + " manifest=" + manifest.getPlanId() + " state=" + stateDoc.get("planId") + "): " + planDir);
        }
        return new QueuePlan(dirName, manifest, planDir, stateDoc);
    }

    private static Manifest parseManifest(Path manifestFile) {
        JSONObject doc = parseJsonFile(manifestFile);
        String planId = requireString(doc, "planId", manifestFile, "");
        String type = requireString(doc, "type", manifestFile, "");
        if (!"UPGRADE".equals(type)) {
            throw new UpgradeStateException("manifest type 非 UPGRADE(升级队列不收他型): " + type);
        }
        JSONArray rawItems = doc.getJSONArray("items");
        if (rawItems == null || rawItems.isEmpty()) {
            throw new UpgradeStateException("manifest items 缺失或为空: " + manifestFile);
        }
        List<Item> items = new ArrayList<>();
        for (int i = 0; i < rawItems.size(); i++) {
            items.add(parseItem(rawItems.getJSONObject(i), manifestFile, i));
        }
        return new Manifest(doc.getIntValue("schemaVersion"), planId, type,
                requireString(doc, "createdAt", manifestFile, ""), items);
    }

    private static Item parseItem(JSONObject raw, Path manifestFile, int index) {
        String prefix = "items[" + index + "]";
        String groupId = requireString(raw, "group_id", manifestFile, prefix);
        String artifactId = requireString(raw, "artifact_id", manifestFile, prefix);
        String targetVersion = requireString(raw, "target_version", manifestFile, prefix);
        List<ManifestFile> files = new ArrayList<>();
        JSONArray rawFiles = raw.getJSONArray("files");
        if (rawFiles == null) {
            throw new UpgradeStateException(prefix + " files 缺失: " + manifestFile);
        }
        for (int i = 0; i < rawFiles.size(); i++) {
            String filePrefix = prefix + ".files[" + i + "]";
            JSONObject f = rawFiles.getJSONObject(i);
            files.add(new ManifestFile(
                    requireString(f, "kind", manifestFile, filePrefix),
                    requireString(f, "group_id", manifestFile, filePrefix),
                    requireString(f, "artifact_id", manifestFile, filePrefix),
                    requireString(f, "version", manifestFile, filePrefix),
                    requireString(f, "filename", manifestFile, filePrefix),
                    requireString(f, "sha256", manifestFile, filePrefix)));
        }
        // db_conventions 键已退役:只取声明键=对旧清单残留键天然容忍(未知键忽略,禁报错)——
        // 过渡期旧清单仍在盘,复验/装载都不得因其失败
        return new Item(groupId, artifactId, optionalString(raw, "installed_version"), targetVersion,
                optionalString(raw, "requires_core"), files);
    }

    private static Map<String, Object> parseState(Path stateFile) {
        Map<String, Object> doc;
        try (InputStream in = Files.newInputStream(stateFile)) {
            doc = new Yaml().load(in);
        } catch (Exception e) {
            throw new UpgradeStateException("state.yml 解析失败: " + stateFile + " - " + e.getMessage(), e);
        }
        if (doc == null) {
            throw new UpgradeStateException("state.yml 空文档: " + stateFile);
        }
        PlanState.fromWire((String) doc.get("state"));   // 词表校验前移到装载(损坏即显形)
        requireString(new JSONObject(doc), "enqueuedAt", stateFile, "");   // 排序依据,缺失=残缺
        return doc;
    }

    private static JSONObject parseJsonFile(Path file) {
        try {
            JSONObject doc = JSON.parseObject(Files.readAllBytes(file));
            if (doc == null) {
                throw new UpgradeStateException("空 JSON 文档: " + file);
            }
            return doc;
        } catch (UpgradeStateException e) {
            throw e;
        } catch (Exception e) {
            throw new UpgradeStateException("JSON 解析失败: " + file + " - " + e.getMessage(), e);
        }
    }

    private static String requireString(JSONObject doc, String key, Path file, String prefix) {
        String value = doc.getString(key);
        if (value == null || value.isEmpty()) {
            throw new UpgradeStateException((prefix.isEmpty() ? "" : prefix + " ")
                    + "必填键缺失(" + key + "): " + file);
        }
        return value;
    }

    private static String optionalString(JSONObject doc, String key) {
        String value = doc.getString(key);
        return value == null || value.isEmpty() ? null : value;
    }

    // ==================== 只读面 ====================

    public String getPlanId() {
        return planId;
    }

    /** 损坏修复形态(rewriteMinimal)下 manifest=null,调用方不得再消费 */
    public Manifest getManifest() {
        return manifest;
    }

    public Path getPlanDir() {
        return planDir;
    }

    public PlanState getState() {
        return PlanState.fromWire((String) stateDoc.get("state"));
    }

    public int getAttempts() {
        return ((Number) stateDoc.getOrDefault("attempts", 0)).intValue();
    }

    public String getError() {
        return (String) stateDoc.get("error");
    }

    public String getEnqueuedAt() {
        return (String) stateDoc.get("enqueuedAt");
    }

    /**
     * db.{domain} 子标记只读视图(历史计划渲染容差):写入面已随 db: 块退役,
     * 本读取只为过渡期在盘旧 state.yml(前代 core 写过域级子标记)的投影不撕裂,
     * 新计划不再产生标记(无标记=空表)。
     */
    public Map<String, String> getDbMarkers() {
        Map<String, Object> db = (Map<String, Object>) stateDoc.get("db");
        LinkedHashMap<String, String> result = new LinkedHashMap<>();
        if (db != null) {
            for (Map.Entry<String, Object> entry : db.entrySet()) {
                result.put(entry.getKey(), String.valueOf(entry.getValue()));
            }
        }
        return result;
    }

    // ==================== state 写面(编排器独占;共用件原子写) ====================

    /** 状态迁移:追加 history 轨迹+updatedAt,整文档原子替换落盘;from==to=乱序显形 */
    public void stateTransition(PlanState next) {
        PlanState from = getState();
        if (from == next) {
            throw new UpgradeStateException("状态迁移 from==to(乱序): " + from + " - " + planId);
        }
        stateDoc.put("state", next.name());
        stateDoc.put("updatedAt", Instant.now().toString());
        List<Map<String, Object>> history = historyList();
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("from", from.name());
        entry.put("to", next.name());
        entry.put("at", Instant.now().toString());
        history.add(entry);
        persistState();
    }

    /** 最近失败原因(人读,进 T-1-9);非状态迁移,不追加 history */
    public void recordError(String error) {
        stateDoc.put("error", error);
        stateDoc.put("updatedAt", Instant.now().toString());
        persistState();
    }

    /** LOADING 重试计数(D24 重试可观察,无硬上限) */
    public void incrementAttempts() {
        stateDoc.put("attempts", getAttempts() + 1);
        stateDoc.put("updatedAt", Instant.now().toString());
        persistState();
    }

    private List<Map<String, Object>> historyList() {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> history = (List<Map<String, Object>>) stateDoc.get("history");
        if (history == null) {
            history = new ArrayList<>();
            stateDoc.put("history", history);
        }
        return history;
    }

    /**
     * 整文档重建(键序=队列写入器初值序,新增键追加其后)→共用件原子替换。
     * error/history/db 空形不写键(与初值形态无缝往返);IO 失败上抛=状态机推进受阻显形,
     * 由编排器顶层按当前段位转 FAILED/保守回滚,不静默吞。
     */
    private void persistState() {
        LinkedHashMap<String, Object> doc = new LinkedHashMap<>();
        doc.put("schemaVersion", ((Number) stateDoc.getOrDefault("schemaVersion", 1)).intValue());
        doc.put("planId", planId);
        doc.put("state", getState().name());
        doc.put("enqueuedAt", stateDoc.get("enqueuedAt"));
        doc.put("attempts", getAttempts());
        if (stateDoc.get("error") != null) {
            doc.put("error", stateDoc.get("error"));
        }
        if (stateDoc.get("updatedAt") != null) {
            doc.put("updatedAt", stateDoc.get("updatedAt"));
        }
        List<Map<String, Object>> history = historyList();
        if (!history.isEmpty()) {
            doc.put("history", history);
        }
        Map<String, Object> db = (Map<String, Object>) stateDoc.get("db");
        if (db != null && !db.isEmpty()) {
            doc.put("db", db);
        }
        try {
            stateWriter.write(doc, stateFile.toFile());
        } catch (IOException e) {
            throw new UpgradeStateException("state.yml 写入失败: " + stateFile + " - " + e.getMessage(), e);
        }
    }

    // ==================== 损坏修复写(崩溃恢复矩阵保守路径专用) ====================

    /**
     * state.yml 损坏后的最小重写:按给定态重建整个文档(丢历史与原键——损坏面不可信,
     * 只保状态语义;error 固定携带损坏原因供 T-1-9 显形)。manifest 也损坏时 planId
     * 以目录名为准(manifest 为 B1 复验依据,不可信即回滚——本工厂只服务回滚/作废
     * 两条已决路径,装载后不复验、不续推)。
     */
    public static QueuePlan rewriteMinimal(Path planDir, String planId, PlanState state, String error) {
        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("schemaVersion", 1);
        doc.put("planId", planId);
        doc.put("state", state.name());
        doc.put("enqueuedAt", Instant.now().toString());
        doc.put("attempts", 0);
        doc.put("error", error);
        doc.put("updatedAt", Instant.now().toString());
        try {
            new YamlAtomicFileWriter().write(doc, planDir.resolve("state.yml").toFile());
        } catch (IOException e) {
            throw new UpgradeStateException("state.yml 修复写失败: " + planDir + " - " + e.getMessage(), e);
        }
        return new QueuePlan(planId, null, planDir, doc);
    }

    // ==================== 快照面记号(与 BackupService 同包记号契约) ====================

    /** 快照 manifest.json 在=快照成立标志(BACKED_UP 直进 B3/保守回滚分路判据) */
    public boolean isSnapshotEstablished(Path snapshotRoot) {
        return Files.isRegularFile(snapshotRoot.resolve(planId).resolve("manifest.json"));
    }

    /** manifest items 平面枚举(损坏修复形态下为空,调用方不得再消费 manifest) */
    public List<Item> itemsOrEmpty() {
        return manifest == null ? new ArrayList<>() : manifest.getItems();
    }

    @Override
    public String toString() {
        return "QueuePlan{" + planId + " state=" + getState() + " attempts=" + getAttempts()
                + " items=" + itemsOrEmpty().size() + "}";
    }
}
