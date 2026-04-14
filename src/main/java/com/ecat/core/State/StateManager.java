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

package com.ecat.core.State;

import java.io.File;
import java.time.ZoneId;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.alibaba.fastjson2.JSON;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;

import com.ecat.core.Device.DeviceBase;
import com.ecat.core.Utils.LogFactory;
import com.ecat.core.Utils.Log;
import com.ecat.core.Utils.platform.OsUtils;

/**
 * 属性状态持久化管理器
 *
 * 使用 MapDB 管理每个设备的属性状态持久化。
 * 每个设备一个 DB 文件，路径格式: {baseDir}/{groupId}/{integrationId}/{deviceId}.db
 *
 * 写入策略: 每次 updateValue 写入 MapDB WAL，定时 1 秒批量 commit。
 * 恢复策略: setAttribute 时逐个恢复，包含单位校验和默认值兜底。
 */
public class StateManager {

    private final String baseDir;
    private final Map<String, DB> dbCache = new ConcurrentHashMap<>();
    private final Log log = LogFactory.getLogger(getClass());

    /**
     * 默认构造函数（EcatCore.init 使用，不启用持久化）
     */
    public StateManager() {
        this.baseDir = null;
    }

    /**
     * 完整构造函数
     * @param baseDir 持久化根目录，如 ".ecat-data/core/states/"
     * @param scheduler 定时任务执行器，用于批量 commit
     */
    public StateManager(String baseDir, ScheduledExecutorService scheduler) {
        this.baseDir = baseDir;
        if (baseDir != null) {
            new File(baseDir).mkdirs();
        }

        if (scheduler != null) {
            scheduler.scheduleAtFixedRate(this::commitAll, 1, 1, TimeUnit.SECONDS);
        }
    }

    /**
     * 保存属性状态到 MapDB（仅写入 WAL 缓存，不立即 commit）
     *
     * @param device 属性所属设备
     * @param attr 需要持久化的属性
     */
    public void saveState(DeviceBase device, AttributeBase<?> attr) {
        if (baseDir == null) return;

        try {
            ConcurrentMap<String, String> map = getOrCreateMap(device);

            PersistedState state = new PersistedState();
            state.value = attr.getValue();
            state.statusCode = attr.getStatus().getId();
            state.updateTimeEpochMs = attr.getUpdateTime() != null
                ? attr.getUpdateTime().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                : 0L;
            state.nativeUnitStr = attr.getNativeUnit() != null
                ? attr.getNativeUnit().getFullUnitString() : null;

            map.put(attr.getAttributeID(), JSON.toJSONString(state));
        } catch (Exception e) {
            log.error("Failed to save state for attr " + attr.getAttributeID() +
                " device " + device.getId(), e);
        }
    }

    /**
     * 从 MapDB 加载单个属性状态
     *
     * @param device 属性所属设备
     * @param attrId 属性ID
     * @return 持久化状态，无数据时返回 null
     */
    public PersistedState loadState(DeviceBase device, String attrId) {
        if (baseDir == null) return null;

        try {
            ConcurrentMap<String, String> map = getOrCreateMap(device);
            String json = map.get(attrId);
            if (json == null) return null;
            return JSON.parseObject(json, PersistedState.class);
        } catch (Exception e) {
            log.error("Failed to load state for attr " + attrId +
                " device " + device.getId(), e);
            return null;
        }
    }

    /**
     * 恢复属性状态（包含单位校验和默认值兜底）
     * 在 DeviceBase.setAttribute() 中调用
     *
     * @param device 属性所属设备
     * @param attr 需要恢复的属性
     */
    public void restoreAttributeState(DeviceBase device, AttributeBase<?> attr) {
        if (baseDir == null) return;

        try {
            PersistedState state = loadState(device, attr.getAttributeID());
            if (state != null) {
                String currentUnit = attr.getNativeUnit() != null
                    ? attr.getNativeUnit().getFullUnitString() : null;
                if (Objects.equals(currentUnit, state.nativeUnitStr)) {
                    attr.restore(state);
                } else {
                    log.warn("Unit mismatch for attr {}, skip restore (persisted={}, current={})",
                        attr.getAttributeID(), state.nativeUnitStr, currentUnit);
                }
            } else if (attr.getDefaultValue() != null) {
                attr.restoreFromDefault();
            }
        } catch (Exception e) {
            log.error("Failed to restore state for attr " + attr.getAttributeID() +
                " device " + device.getId(), e);
        }
    }

    /**
     * 手动触发所有 DB 的 commit
     */
    public void commitAll() {
        for (Map.Entry<String, DB> entry : dbCache.entrySet()) {
            try {
                entry.getValue().commit();
            } catch (Exception e) {
                log.error("Failed to commit DB for device " + entry.getKey(), e);
            }
        }
    }

    /**
     * 关闭设备的 DB（commit + close），保留文件
     *
     * @param deviceId 设备ID
     */
    public void closeDevice(String deviceId) {
        DB db = dbCache.remove(deviceId);
        if (db != null) {
            try {
                db.commit();
                db.close();
            } catch (Exception e) {
                log.error("Failed to close DB for device " + deviceId, e);
            }
        }
    }

    /**
     * 关闭集成下所有设备的 DB
     *
     * @param deviceIds 设备ID集合
     */
    public void closeIntegrationDevices(Collection<String> deviceIds) {
        for (String deviceId : deviceIds) {
            closeDevice(deviceId);
        }
    }

    /**
     * 删除设备的 DB 文件（close + delete file）
     *
     * @param device 设备实例
     */
    public void removeDevice(DeviceBase device) {
        String deviceId = device.getId();

        // 先从缓存获取 DB，通过 MapDB 官方 API 获取所有关联文件列表
        DB db = dbCache.get(deviceId);
        Iterable<String> allFiles = null;
        if (db != null) {
            try {
                allFiles = db.getStore().getAllFiles();
            } catch (Exception e) {
                log.warn("Failed to get DB file list for device " + deviceId, e);
            }
        }

        // 关闭 DB
        closeDevice(deviceId);

        // 删除文件：优先使用 MapDB API 获取的文件列表，兜底使用路径猜测
        if (allFiles != null) {
            for (String filePath : allFiles) {
                File f = new File(filePath);
                if (f.exists()) {
                    f.delete();
                }
            }
        } else {
            // 兜底：DB 已关闭或获取文件列表失败，基于主路径删除
            String dbPath = buildDbPath(device);
            File dbDir = new File(dbPath).getParentFile();
            String dbFileName = new File(dbPath).getName();
            if (dbDir != null && dbDir.isDirectory()) {
                File[] candidates = dbDir.listFiles();
                if (candidates != null) {
                    for (File f : candidates) {
                        if (f.getName().startsWith(dbFileName)) {
                            f.delete();
                        }
                    }
                }
            }
        }
    }

    /**
     * 关闭所有 DB，最终 commit（shutdown 时调用）
     */
    public void shutdown() {
        for (Map.Entry<String, DB> entry : dbCache.entrySet()) {
            try {
                entry.getValue().commit();
                entry.getValue().close();
            } catch (Exception e) {
                log.error("Failed to shutdown DB for device " + entry.getKey(), e);
            }
        }
        dbCache.clear();
    }

    // ========== 内部方法 ==========

    private ConcurrentMap<String, String> getOrCreateMap(DeviceBase device) {
        DB db = getOrCreateDb(device);
        HTreeMap<String, String> map = (HTreeMap<String, String>) db.hashMap("states")
            .keySerializer(Serializer.STRING)
            .valueSerializer(Serializer.STRING)
            .createOrOpen();
        return map;
    }

    private DB getOrCreateDb(DeviceBase device) {
        String deviceId = device.getId();
        return dbCache.computeIfAbsent(deviceId, id -> {
            String dbPath = buildDbPath(device);
            new File(dbPath).getParentFile().mkdirs();
            DBMaker.Maker maker = DBMaker.fileDB(dbPath)
                .transactionEnable();
            // Windows 下不启用 mmap，避免文件被锁定无法动态删除
            if (!OsUtils.isWindows()) {
                maker.fileMmapEnable();
            }
            return maker.make();
        });
    }

    private String buildDbPath(DeviceBase device) {
        // coordinate format: "com.ecat:integration-sailhero"
        String coordinate = device.getEntry().getCoordinate();
        String[] parts = coordinate.split(":");
        String groupId = parts[0];
        String integrationId = parts[1];
        String deviceId = device.getId();
        return baseDir + groupId + "/" + integrationId + "/" + deviceId + ".db";
    }
}
