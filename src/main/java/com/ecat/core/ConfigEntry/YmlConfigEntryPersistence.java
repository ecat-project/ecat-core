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

package com.ecat.core.ConfigEntry;

import com.ecat.core.ConfigFormatException;
import com.ecat.core.FormatVersion;
import com.ecat.core.Utils.DateTimeUtils;
import com.ecat.core.Utils.Log;
import com.ecat.core.Utils.LogFactory;
import com.ecat.core.Utils.YamlAtomicFileWriter;
import org.yaml.snakeyaml.Yaml;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.*;

/**
 * YAML 格式的 ConfigEntry 持久化实现
 * <p>
 * 存储路径: `./ecat-data/core/config_entries/{groupId}/{artifactId}/{entryId}.yml`
 * <p>
 * 按照 coordinate (groupId:artifactId) 进行目录分组存储。
 *
 * @author coffee
 */
public class YmlConfigEntryPersistence implements ConfigEntryPersistence {

    private static final Log log = LogFactory.getLogger(YmlConfigEntryPersistence.class);
    private static final String BASE_DIR = ".ecat-data/core/config_entries";

    private final Yaml yaml;

    /** entry yml 写盘共用件:save 经此获得 tmp+回读验证+原子 rename 写强度(裸截断写退役)。 */
    private final YamlAtomicFileWriter yamlAtomicFileWriter = new YamlAtomicFileWriter();

    /**
     * 构造函数
     * <p>
     * 初始化 YAML 解析器并创建存储目录。
     */
    public YmlConfigEntryPersistence() {
        // 本类 Yaml 仅剩 loadAll 解析用途(dump 已委托 YamlAtomicFileWriter);原 DumperOptions/
        // PropertyUtils 配置块随 dump 外迁退役——PropertyUtils 两项只影响 JavaBean dump 反射,对 Map 解析无作用
        this.yaml = new Yaml();

        try {
            Files.createDirectories(Paths.get(BASE_DIR));
        } catch (IOException e) {
            throw new RuntimeException("Failed to create config directory: " + BASE_DIR, e);
        }
    }

    @Override
    public List<ConfigEntry> loadAll() {
        List<ConfigEntry> allEntries = new ArrayList<>();
        File baseDir = new File(BASE_DIR);

        if (!baseDir.exists()) {
            log.debug("Config entries directory does not exist: {}", BASE_DIR);
            return allEntries;
        }

        // 被跳过的 entry 文件（解析失败/空文件）：启动汇总点名用
        List<SkippedEntryFile> skipped = new ArrayList<>();
        // 格式版本非法的文件专项收集：walk 结束后聚合 fail-closed 抛出（不与 skipped 混同——
        // 版本非法必须中止启动而非静默缺额）
        List<VersionOffence> versionOffences = new ArrayList<>();
        // 递归遍历所有子目录查找 yml 文件
        loadEntriesFromDirectory(baseDir, allEntries, skipped, versionOffences);

        // 格式版本非法聚合抛出：一次点名全部 offender（重刻遗漏一次显形，不给
        // 「修一个跑一次」的循环）。本方法在 core 启动期执行，异常上浮即中止启动。
        if (!versionOffences.isEmpty()) {
            throw buildVersionOffenceException(versionOffences);
        }

        log.info("Loaded {} config entries from {}", allEntries.size(), BASE_DIR);
        if (!skipped.isEmpty()) {
            // 静默丢弃改显式（操作员可见）：entry 文件损坏被跳过 = 配置条目凭空缺额（设备不加载、
            // 数量对不上文件数），必须有一行 ERROR 汇总点名，不能只在「Loaded N」计数里隐没。
            // 坐标从存储布局（BASE_DIR/{groupId}/{artifactId}/{entryId}.yml）推导——解析失败的
            // 文件读不出 coordinate 字段，目录结构是唯一可靠的定位信息。
            log.error("{} 个 entry 文件解析失败被跳过（配置条目因此缺额）: {}",
                skipped.size(), describeSkipped(skipped));
        }
        return allEntries;
    }

    /** 格式版本非法的文件（loadAll 专项收集，walk 结束后聚合抛出）。 */
    private static final class VersionOffence {
        final File file;
        final ConfigFormatException cause;

        VersionOffence(File file, ConfigFormatException cause) {
            this.file = file;
            this.cause = cause;
        }
    }

    /**
     * 聚合格式版本非法清单为单个异常：location=存储根目录，expected=当前声明格式，
     * actual=逐文件清单（绝对路径 + 原 location + offender 实际值）。
     */
    private static ConfigFormatException buildVersionOffenceException(List<VersionOffence> offences) {
        StringBuilder actual = new StringBuilder("共 " + offences.size() + " 个文件格式版本非法:\n");
        for (VersionOffence offence : offences) {
            actual.append("  ").append(offence.file.getAbsolutePath())
                    .append(" → 位置=").append(offence.cause.getLocation())
                    .append(",实际=").append(offence.cause.getActual()).append('\n');
        }
        return new ConfigFormatException(BASE_DIR, "4.0", actual.toString().trim());
    }

    /**
     * 递归从目录加载所有 ConfigEntry
     *
     * @param directory       目录
     * @param allEntries      所有条目列表
     * @param skipped         被跳过的损坏文件收集（解析失败/空文件），loadAll 汇总点名用
     * @param versionOffences 格式版本非法文件收集（不跳过、不中断 walk），loadAll 聚合抛出用
     */
    private void loadEntriesFromDirectory(File directory, List<ConfigEntry> allEntries,
                                          List<SkippedEntryFile> skipped,
                                          List<VersionOffence> versionOffences) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                // 递归遍历子目录
                loadEntriesFromDirectory(file, allEntries, skipped, versionOffences);
            } else if (file.getName().endsWith(".yml")) {
                try (InputStream input = new FileInputStream(file)) {
                    Map<String, Object> data = yaml.load(input);
                    if (data != null) {
                        allEntries.add(convertToConfigEntry(data));
                    } else {
                        // 空文件（yaml.load 返回 null）：此前完全静默，现显式记录
                        log.warn("跳过空 entry 文件（无 entry 内容）: {}", file.getAbsolutePath());
                        skipped.add(new SkippedEntryFile(file));
                    }
                } catch (ConfigFormatException e) {
                    // 格式版本非法：专项收集（不落 skipped、不 return）——walk 继续收集其余 offender，
                    // 结束后聚合抛出中止启动。若落进下方通用 catch 会吞成静默跳过，与 fail-closed 目标相反
                    versionOffences.add(new VersionOffence(file, e));
                } catch (Exception e) {
                    // 一行 WARN 带异常摘要（不带全栈：启动期多个坏文件时全栈刷屏；计数与清单由汇总行负责）
                    log.warn("跳过无法解析的 entry 文件: {} 原因: {}",
                        file.getAbsolutePath(), summarizeException(e));
                    skipped.add(new SkippedEntryFile(file));
                }
            }
        }
    }

    /** 被跳过的 entry 文件（损坏/空），供 loadAll 汇总。 */
    private static final class SkippedEntryFile {
        final File file;

        SkippedEntryFile(File file) {
            this.file = file;
        }
    }

    /**
     * 跳过清单的可读描述：按存储布局还原 {@code groupId:artifactId(entryId)}；
     * 布局外的游离文件（不在 groupId/artifactId 目录下）退回相对路径。
     */
    private static String describeSkipped(List<SkippedEntryFile> skipped) {
        List<String> items = new ArrayList<>();
        for (SkippedEntryFile s : skipped) {
            File artifactDir = s.file.getParentFile();
            File groupDir = artifactDir != null ? artifactDir.getParentFile() : null;
            String name = s.file.getName();
            String entryId = name.endsWith(".yml")
                ? name.substring(0, name.length() - ".yml".length()) : name;
            if (groupDir != null) {
                items.add(groupDir.getName() + ":" + artifactDir.getName() + "(" + entryId + ")");
            } else {
                items.add(s.file.getPath());
            }
        }
        return String.join("; ", items);
    }

    /** 异常摘要：简单类名 + 消息首行（多行消息截到首行，避免一行 WARN 展开成多行刷屏）。 */
    private static String summarizeException(Exception e) {
        String message = e.getMessage();
        if (message == null) {
            return e.getClass().getSimpleName();
        }
        int lineBreak = message.indexOf('\n');
        String firstLine = lineBreak >= 0 ? message.substring(0, lineBreak) : message;
        return e.getClass().getSimpleName() + ": " + firstLine;
    }

    @Override
    public void save(ConfigEntry entry) {
        File file = getFile(entry.getEntryId(), entry.getCoordinate());
        Map<String, Object> data = convertFromConfigEntry(entry);

        try {
            // 委托共用件:tmp+回读验证+原子 rename(共用件内含父目录创建,原手工 mkdirs 退役)。
            // 写强度从「FileOutputStream 打开即截断」升级为原子替换;失败仍抛 RuntimeException,
            // Registry 各写点(createEntry/updateEntry/reconfigureEntry/setEnabled)既有上浮路径零改动。
            yamlAtomicFileWriter.write(data, file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save config entry: " + entry.getEntryId(), e);
        }
        log.debug("Saved config entry: {} to {}", entry.getEntryId(), file.getAbsolutePath());
    }

    @Override
    public void update(ConfigEntry entry) {
        // 更新和保存使用相同的实现
        save(entry);
    }

    @Override
    public void delete(String entryId) {
        // 在子目录中查找文件
        File baseDir = new File(BASE_DIR);
        File foundFile = findFileRecursively(baseDir, entryId);

        if (foundFile != null && foundFile.exists()) {
            if (!foundFile.delete()) {
                log.warn("Failed to delete config file: {}", foundFile.getAbsolutePath());
            } else {
                log.debug("Deleted config entry: {}", entryId);
                // 清理空目录
                cleanupEmptyDirectories(foundFile.getParentFile());
            }
        } else {
            log.warn("Config entry file not found: {}", entryId);
        }
    }

    /**
     * 递归查找配置文件
     *
     * @param directory 目录
     * @param entryId   配置条目 ID
     * @return 找到的文件，或 null
     */
    private File findFileRecursively(File directory, String entryId) {
        if (!directory.exists() || !directory.isDirectory()) {
            return null;
        }

        File[] files = directory.listFiles();
        if (files == null) {
            return null;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                File found = findFileRecursively(file, entryId);
                if (found != null) {
                    return found;
                }
            } else if (file.getName().equals(entryId + ".yml")) {
                return file;
            }
        }

        return null;
    }

    /**
     * 清理空目录
     *
     * @param directory 目录
     */
    private void cleanupEmptyDirectories(File directory) {
        if (directory == null || !directory.exists()) {
            return;
        }

        // 只清理 BASE_DIR 下的空目录
        File baseDir = new File(BASE_DIR);
        if (!directory.getAbsolutePath().startsWith(baseDir.getAbsolutePath())) {
            return;
        }

        // 如果是空目录且不是 BASE_DIR 本身，则删除
        if (directory.isDirectory() && !directory.equals(baseDir)) {
            String[] children = directory.list();
            if (children == null || children.length == 0) {
                if (directory.delete()) {
                    log.debug("Cleaned up empty directory: {}", directory.getAbsolutePath());
                    // 递归清理父目录
                    cleanupEmptyDirectories(directory.getParentFile());
                }
            }
        }
    }

    /**
     * 获取配置文件
     * <p>
     * 路径格式: {BASE_DIR}/{groupId}/{artifactId}/{entryId}.yml
     *
     * @param entryId   配置条目 ID
     * @param coordinate 集成标识 (groupId:artifactId)
     * @return 配置文件
     */
    private File getFile(String entryId, String coordinate) {
        // 解析 coordinate: groupId:artifactId
        String[] parts = coordinate.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid coordinate format: " + coordinate + ", expected groupId:artifactId");
        }

        String groupId = parts[0];
        String artifactId = parts[1];

        // 创建分组目录
        File dir = new File(BASE_DIR, groupId + "/" + artifactId);
        if (!dir.exists()) {
            dir.mkdirs();
        }

        return new File(dir, entryId + ".yml");
    }

    /**
     * 将 Map 转换为 ConfigEntry
     *
     * @param map YAML 加载的 Map
     * @return ConfigEntry
     */
    @SuppressWarnings("unchecked")
    private ConfigEntry convertToConfigEntry(Map<String, Object> map) {
        ConfigEntry.Builder builder = new ConfigEntry.Builder();

        builder.entryId((String) map.get("entryId"));
        builder.coordinate((String) map.get("coordinate"));
        builder.uniqueId((String) map.get("uniqueId"));
        builder.title((String) map.get("title"));

        // 处理 data 字段
        Object dataObj = map.get("data");
        if (dataObj instanceof Map) {
            builder.data((Map<String, Object>) dataObj);
        }

        // 处理 stepInputs 字段
        Object stepInputsObj = map.get("stepInputs");
        if (stepInputsObj instanceof Map) {
            builder.stepInputs((Map<String, Object>) stepInputsObj);
        }

        // 处理布尔字段
        Object enabledObj = map.get("enabled");
        if (enabledObj instanceof Boolean) {
            builder.enabled((Boolean) enabledObj);
        } else if (enabledObj instanceof String) {
            builder.enabled(Boolean.parseBoolean((String) enabledObj));
        }

        // 处理时间字段
        builder.createTime(parseTime(map.get("createTime")));
        builder.updateTime(parseTime(map.get("updateTime")));

        // 处理格式版本（"major.minor" 字符串单路解析：缺失/非字符串/格式坏一律异常）
        builder.version(parseFormatVersion(map.get("version"), describeLocation(map)));

        // 处理 source 字段（向后兼容：旧 yml 无 source → Builder 默认 USER）
        Object sourceObj = map.get("source");
        if (sourceObj instanceof String && !((String) sourceObj).isEmpty()) {
            try {
                builder.source(SourceType.valueOf((String) sourceObj));
            } catch (IllegalArgumentException e) {
                // 持久化数据中的 source 值损坏——记错误并回退 USER（不阻断 core 启动）
                log.error("持久化 entry 的 source 值非法，回退 USER: {} (entryId={})",
                        sourceObj, map.get("entryId"));
                builder.source(SourceType.USER);
            }
        }

        return builder.build();
    }

    /**
     * yml version 值单路解析：String 且 well-formed 才放行；其余一律异常
     * （含旧计数器整数残留与未加引号被 snakeyaml 解析为数值的形态）。
     * 「旧计数器残留」只是成因提示，不是放行理由——不设 legacy-int 特判分支。
     *
     * @param raw      yml 根级 version 键的原始值
     * @param location 人读定位（coordinate+entryId）
     * @return well-formed 的格式版本字符串
     */
    private static String parseFormatVersion(Object raw, String location) {
        if (raw == null) {
            throw new ConfigFormatException(location, "major.minor 字符串(如 \"4.0\")", "缺失");
        }
        if (!(raw instanceof String)) {
            // 非 String 统一异常：旧计数器整数（yaml `version: 1`→Integer）与
            // 未加引号小数（`version: 4.0`→Double）都落在这里，actual 携带类型:值
            throw new ConfigFormatException(location, "major.minor 字符串(如 \"4.0\")",
                    raw.getClass().getSimpleName() + ":" + raw + "(疑似旧计数器残留，或未加引号被解析为数值)");
        }
        FormatVersion.requireWellFormed((String) raw, location);
        return (String) raw;
    }

    /**
     * 从 yml map 取可读定位：coordinate+entryId 均可读时返回 "coordinate(entryId=...)"；
     * 否则返回占位——具体文件路径由 loadAll 的聚合清单携带。
     */
    private static String describeLocation(Map<String, Object> map) {
        Object coordinate = map.get("coordinate");
        Object entryId = map.get("entryId");
        if (coordinate instanceof String && !((String) coordinate).isEmpty()
                && entryId instanceof String && !((String) entryId).isEmpty()) {
            return coordinate + "(entryId=" + entryId + ")";
        }
        return "未知(以文件路径定位)";
    }

    /**
     * 将 ConfigEntry 转换为 Map
     *
     * @param entry ConfigEntry
     * @return Map
     */
    private Map<String, Object> convertFromConfigEntry(ConfigEntry entry) {
        Map<String, Object> map = new LinkedHashMap<>();

        map.put("entryId", entry.getEntryId());
        map.put("coordinate", entry.getCoordinate());
        map.put("uniqueId", entry.getUniqueId());
        map.put("title", entry.getTitle());
        map.put("data", deepSerialize(entry.getData()));
        map.put("stepInputs", deepSerialize(entry.getStepInputs()));
        map.put("enabled", entry.isEnabled());
        map.put("createTime", formatTime(entry.getCreateTime()));
        map.put("updateTime", formatTime(entry.getUpdateTime()));
        map.put("version", entry.getVersion());
        // source（来源类型，序列化为 enum name；旧 entry 无此字段，加载时默认 USER——向后兼容）
        map.put("source", entry.getSource() != null ? entry.getSource().name() : SourceType.USER.name());

        return map;
    }

    /**
     * 深度序列化数据，将 ZonedDateTime 转换为 ISO 字符串
     *
     * @param data 原始数据
     * @return 序列化后的数据
     */
    @SuppressWarnings("unchecked")
    private Object deepSerialize(Object data) {
        if (data == null) {
            return null;
        }

        if (data instanceof ZonedDateTime) {
            return DateTimeUtils.formatIso((ZonedDateTime) data);
        }

        if (data instanceof Map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : ((Map<String, Object>) data).entrySet()) {
                result.put(entry.getKey(), deepSerialize(entry.getValue()));
            }
            return result;
        }

        if (data instanceof List) {
            List<Object> result = new ArrayList<>();
            for (Object item : (List<?>) data) {
                result.add(deepSerialize(item));
            }
            return result;
        }

        return data;
    }

    /**
     * 格式化时间为字符串
     *
     * @param time 时间对象
     * @return ISO 格式字符串
     */
    private String formatTime(ZonedDateTime time) {
        return time != null ? DateTimeUtils.formatIso(time) : null;
    }

    /**
     * 解析时间字符串
     *
     * @param time 时间字符串
     * @return 时间对象
     */
    private ZonedDateTime parseTime(Object time) {
        if (time == null) {
            return null;
        }
        if (time instanceof ZonedDateTime) {
            return (ZonedDateTime) time;
        }
        return DateTimeUtils.parseIso(time.toString());
    }
}
