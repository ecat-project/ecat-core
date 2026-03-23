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

import com.ecat.core.Utils.DateTimeUtils;
import com.ecat.core.Utils.Log;
import com.ecat.core.Utils.LogFactory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.introspector.PropertyUtils;

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

    /**
     * 构造函数
     * <p>
     * 初始化 YAML 解析器并创建存储目录。
     */
    public YmlConfigEntryPersistence() {
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        options.setIndent(2);

        // 配置 PropertyUtils 以支持 Lombok 生成的方法
        PropertyUtils propertyUtils = new PropertyUtils();
        propertyUtils.setSkipMissingProperties(true);
        propertyUtils.setAllowReadOnlyProperties(true);

        this.yaml = new Yaml(options);

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

        // 递归遍历所有子目录查找 yml 文件
        loadEntriesFromDirectory(baseDir, allEntries);

        log.info("Loaded {} config entries from {}", allEntries.size(), BASE_DIR);
        return allEntries;
    }

    /**
     * 递归从目录加载所有 ConfigEntry
     *
     * @param directory  目录
     * @param allEntries 所有条目列表
     */
    private void loadEntriesFromDirectory(File directory, List<ConfigEntry> allEntries) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                // 递归遍历子目录
                loadEntriesFromDirectory(file, allEntries);
            } else if (file.getName().endsWith(".yml")) {
                try (InputStream input = new FileInputStream(file)) {
                    Map<String, Object> data = yaml.load(input);
                    if (data != null) {
                        allEntries.add(convertToConfigEntry(data));
                    }
                } catch (Exception e) {
                    log.warn("Failed to load config file: {}", file.getAbsolutePath(), e);
                }
            }
        }
    }

    @Override
    public void save(ConfigEntry entry) {
        File file = getFile(entry.getEntryId(), entry.getCoordinate());
        Map<String, Object> data = convertFromConfigEntry(entry);

        // 确保目录存在
        File parentDir = file.getParentFile();
        if (!parentDir.exists()) {
            parentDir.mkdirs();
        }

        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8")) {
            yaml.dump(data, writer);
            log.debug("Saved config entry: {} to {}", entry.getEntryId(), file.getAbsolutePath());
        } catch (Exception e) {
            throw new RuntimeException("Failed to save config entry: " + entry.getEntryId(), e);
        }
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

        // 处理版本号
        Object versionObj = map.get("version");
        if (versionObj instanceof Integer) {
            builder.version((Integer) versionObj);
        } else if (versionObj instanceof String) {
            builder.version(Integer.parseInt((String) versionObj));
        }

        return builder.build();
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
