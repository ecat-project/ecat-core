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
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.alibaba.fastjson2.JSON;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;

import com.ecat.core.Device.DeviceBase;
import com.ecat.core.Task.NamedThreadFactory;
import com.ecat.core.Utils.LogFactory;
import com.ecat.core.Utils.Log;
import com.ecat.core.Utils.platform.PlatformInfo;

/**
 * 属性状态持久化管理器
 *
 * 使用 MapDB 管理每个设备的属性状态持久化。
 * 每个设备一个 DB 文件，路径格式: {baseDir}/{groupId}/{integrationId}/{deviceId}.db
 *
 * 写入策略: 每次 updateValue 写入 MapDB WAL，自有单线程计时器（ecat-state-commit）定时 1 秒批量 commit。
 * 恢复策略: setAttribute 时逐个恢复，包含单位校验和默认值兜底。
 */
public class StateManager {

    private final String baseDir;
    private final Map<String, DB> dbCache = new ConcurrentHashMap<>();
    /** 自有每秒 commit 计时器（仅生产构造创建；注入形态/无持久化为 null），shutdown 时先停。 */
    private final ScheduledExecutorService selfCommitScheduler;
    private final Log log = LogFactory.getLogger(getClass());

    /**
     * 默认构造函数（EcatCore.init 使用，不启用持久化）
     */
    public StateManager() {
        this.baseDir = null;
        this.selfCommitScheduler = null;
    }

    /**
     * <b>生产入口</b>（EcatCore.init 使用）：baseDir 非空即启用持久化，并自持单线程 daemon
     * （ecat-state-commit）每秒批量 commit。自持而非借 core 调度设施——MapDB commit 是
     * 文件 IO（事务 WAL 刷盘，可阻塞在磁盘），按业务池边界 IO 禁入（2 线程小池会被
     * 持久化刷盘饿死全部业务计时），也不占设备调度引擎车道（那是设备 IO 治理域）；
     * core 持久化 tick 与两侧故障域物理隔离。
     *
     * <p>双构造形态收窄（E4-7）：生产一律走本构造（含 null baseDir 的无持久化形态见
     * {@link #StateManager()}）；注入形态（scheduler 参数）已收窄为包私有——仅 core 测试
     * 同包镜像使用，集成侧误用注入形态=持久化静默丢失（无自持 commit 计时器）。
     *
     * @param baseDir 持久化根目录，如 ".ecat-data/core/states/"
     */
    public StateManager(String baseDir) {
        this.baseDir = baseDir;
        if (baseDir != null) {
            new File(baseDir).mkdirs();
            selfCommitScheduler = Executors.newSingleThreadScheduledExecutor(
                new NamedThreadFactory("ecat-state-commit", true));
            selfCommitScheduler.scheduleAtFixedRate(this::commitAll, 1, 1, TimeUnit.SECONDS);
        } else {
            selfCommitScheduler = null;
        }
    }

    /**
     * 注入形态（<b>仅测试可见</b>，包私有——E4-7 收窄）：scheduler 由调用方拥有并关停，
     * 本类只挂周期任务——core 测试同包镜像以此确定性驱动 commit 时序（不依赖真实秒拍）。
     * 生产代码一律用 {@link #StateManager(String)}（自持 commit 计时器；误用注入形态=
     * 持久化静默丢失）。集成仓测试需要持久化时也走生产构造（自持 daemon 1s commit，
     * 断言前手动 {@code commitAll()} 拍平）。
     *
     * @param baseDir  持久化根目录；null = 不启用持久化
     * @param scheduler 定时任务执行器，用于批量 commit；null = 不挂周期任务
     */
    StateManager(String baseDir, ScheduledExecutorService scheduler) {
        this.baseDir = baseDir;
        if (baseDir != null) {
            new File(baseDir).mkdirs();
        }
        this.selfCommitScheduler = null;

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
            AttrState<?> s = attr.getState();
            if (s == null) return;  // 未 updateValue 过，无可持久化的 state
            ConcurrentMap<String, String> map = getOrCreateMap(device);
            // 围绕 state 持久化：从不可变 AttrState 精简映射，不戳 attr 内部字段
            map.put(attr.getAttributeID(), JSON.toJSONString(PersistedState.from(s)));
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
                if (state.version < 2) {
                    // 围绕 state 重设计前的旧结构数据废弃，不恢复，走默认值（不迁移历史缓存）
                    log.warn("Obsolete persisted state (version={}) for attr {}, skip restore",
                        state.version, attr.getAttributeID());
                    if (attr.getDefaultValue() != null) {
                        attr.restoreFromDefault();
                    }
                    return;
                }
                // state 内容恢复 attr（restore 内重建 lastState）；state 自带一致单位，无需单位校验
                attr.restore(state);
                log.debug("Restored state for attr '{}' value={} device={}",
                    attr.getAttributeID(), state.value, device.getId());
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
     * 关闭所有 DB，最终 commit（shutdown 时调用）。
     * 先停自有 commit 计时器（graceful shutdown：停发起新一轮 commitAll，不中断在飞轮次），
     * 再做最终 commit+close。在飞 commitAll 轮次与本方法 close 的残余竞态由双侧 per-op
     * catch 兜住（commitAll 的逐 entry catch 与本方法的逐 entry catch）：MapDB 对已 close
     * 的 DB 再 commit 会抛，仅记错误日志不外抛。
     */
    public void shutdown() {
        if (selfCommitScheduler != null) {
            selfCommitScheduler.shutdown();
        }
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
            // 使用 fileMmapEnableIfSupported() 而非 fileMmapEnable()，
            // 避免进程异常退出后遗留 stale file lock 导致重启时 FileLocked 异常
            if (!PlatformInfo.getInstance().isWindows()) {
                maker.fileMmapEnableIfSupported();
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
