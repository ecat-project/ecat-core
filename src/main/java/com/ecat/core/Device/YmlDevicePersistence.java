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

package com.ecat.core.Device;

import com.ecat.core.Utils.DateTimeUtils;
import com.ecat.core.Utils.Log;
import com.ecat.core.Utils.LogFactory;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.introspector.PropertyUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * YAML 格式的 DeviceRecord 持久化实现，镜像 {@code YmlConfigEntryPersistence}。
 * <p>
 * 存储路径: {@code {baseDir}/{groupId}/{artifactId}/{id}.yml}，按 coordinate 分目录。
 * baseDir 由构造函数注入（生产传 {@code .ecat-data/core/devices}，测试传临时目录）。
 *
 * @author coffee
 */
public class YmlDevicePersistence implements DevicePersistence {

    private static final Log log = LogFactory.getLogger(YmlDevicePersistence.class);

    private final String baseDir;
    private final Yaml yaml;

    public YmlDevicePersistence(String baseDir) {
        this.baseDir = baseDir;
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
            Files.createDirectories(Paths.get(baseDir));
        } catch (IOException e) {
            throw new RuntimeException("Failed to create devices directory: " + baseDir, e);
        }
    }

    @Override
    public List<DeviceRecord> loadAll() {
        List<DeviceRecord> all = new ArrayList<>();
        File dir = new File(baseDir);
        if (!dir.exists()) {
            log.debug("Devices directory does not exist: {}", baseDir);
            return all;
        }
        loadFromDirectory(dir, all);
        log.info("Loaded {} device records from {}", all.size(), baseDir);
        return all;
    }

    private void loadFromDirectory(File directory, List<DeviceRecord> all) {
        File[] files = directory.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                loadFromDirectory(file, all);
            } else if (file.getName().endsWith(".yml")) {
                try (InputStream input = new FileInputStream(file)) {
                    Map<String, Object> data = yaml.load(input);
                    if (data != null) {
                        all.add(convertToDeviceRecord(data));
                    }
                } catch (Exception e) {
                    log.warn("Failed to load device file: {}", file.getAbsolutePath(), e);
                }
            }
        }
    }

    @Override
    public void save(DeviceRecord record) {
        File file = getFile(record.getId(), record.getCoordinate());
        Map<String, Object> data = convertFromDeviceRecord(record);
        File parentDir = file.getParentFile();
        if (!parentDir.exists()) {
            parentDir.mkdirs();
        }
        try (Writer writer = new OutputStreamWriter(new FileOutputStream(file), "UTF-8")) {
            yaml.dump(data, writer);
            log.debug("Saved device record: {} to {}", record.getId(), file.getAbsolutePath());
        } catch (Exception e) {
            throw new RuntimeException("Failed to save device record: " + record.getId(), e);
        }
    }

    @Override
    public void update(DeviceRecord record) {
        save(record);
    }

    @Override
    public void delete(String id) {
        File dir = new File(baseDir);
        File found = findFileRecursively(dir, id);
        if (found != null && found.exists()) {
            if (!found.delete()) {
                log.warn("Failed to delete device file: {}", found.getAbsolutePath());
            } else {
                log.debug("Deleted device record: {}", id);
                cleanupEmptyDirectories(found.getParentFile());
            }
        } else {
            log.warn("Device record file not found: {}", id);
        }
    }

    private File findFileRecursively(File directory, String id) {
        if (!directory.exists() || !directory.isDirectory()) {
            return null;
        }
        File[] files = directory.listFiles();
        if (files == null) {
            return null;
        }
        for (File file : files) {
            if (file.isDirectory()) {
                File found = findFileRecursively(file, id);
                if (found != null) {
                    return found;
                }
            } else if (file.getName().equals(id + ".yml")) {
                return file;
            }
        }
        return null;
    }

    private void cleanupEmptyDirectories(File directory) {
        if (directory == null || !directory.exists()) {
            return;
        }
        File base = new File(baseDir);
        if (!directory.getAbsolutePath().startsWith(base.getAbsolutePath())) {
            return;
        }
        if (directory.isDirectory() && !directory.equals(base)) {
            String[] children = directory.list();
            if (children == null || children.length == 0) {
                if (directory.delete()) {
                    log.debug("Cleaned up empty directory: {}", directory.getAbsolutePath());
                    cleanupEmptyDirectories(directory.getParentFile());
                }
            }
        }
    }

    /**
     * 路径格式: {baseDir}/{groupId}/{artifactId}/{id}.yml（coordinate = groupId:artifactId）。
     */
    private File getFile(String id, String coordinate) {
        String[] parts = coordinate == null ? new String[0] : coordinate.split(":");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Invalid coordinate format: " + coordinate + ", expected groupId:artifactId");
        }
        File dir = new File(baseDir, parts[0] + "/" + parts[1]);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, id + ".yml");
    }

    private DeviceRecord convertToDeviceRecord(Map<String, Object> map) {
        return DeviceRecord.builder()
                .id((String) map.get("id"))
                .coordinate((String) map.get("coordinate"))
                .uniqueId((String) map.get("uniqueId"))
                .entryId((String) map.get("entryId"))
                .name((String) map.get("name"))
                .vendor((String) map.get("vendor"))
                .model((String) map.get("model"))
                .createTime(parseTime(map.get("createTime")))
                .updateTime(parseTime(map.get("updateTime")))
                .build();
    }

    private Map<String, Object> convertFromDeviceRecord(DeviceRecord r) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", r.getId());
        map.put("coordinate", r.getCoordinate());
        map.put("uniqueId", r.getUniqueId());
        map.put("entryId", r.getEntryId());
        map.put("name", r.getName());
        map.put("vendor", r.getVendor());
        map.put("model", r.getModel());
        map.put("createTime", formatTime(r.getCreateTime()));
        map.put("updateTime", formatTime(r.getUpdateTime()));
        return map;
    }

    private String formatTime(ZonedDateTime time) {
        return time != null ? DateTimeUtils.formatIso(time) : null;
    }

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
