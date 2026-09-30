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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.alibaba.fastjson2.JSON;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

import com.ecat.core.Device.DeviceBase;
import com.ecat.core.Task.NamedThreadFactory;
import com.ecat.core.Utils.LogFactory;
import com.ecat.core.Utils.Log;

/**
 * 属性状态持久化管理器（单库单 map 形态）。
 *
 * <p>存储布局：全部设备共用一个 MapDB 库 {@code {baseDir}/states.db}（单文件 + 事务 WAL），
 * 两个命名 map：
 * <ul>
 *   <li>"meta"（STRING→STRING）：storeSchemaVersion（单轴库级 schema 版本，当前
 *       {@link #STORE_SCHEMA_VERSION}）、storeCreatedEpochMs、coreVersion（诊断标注）</li>
 *   <li>"states"（STRING→STRING）：key = {@code deviceId + SEP + attributeId}（复合键，
 *       SEP 为 NUL，见 {@link #SEP}），value = PersistedState 的 JSON</li>
 * </ul>
 *
 * <p>写入节拍：持久化在 commit 点（publicState → {@link #saveState}）写入 states map——
 * 此时数据在 MapDB 事务 WAL 中、未刷盘；自有单线程计时器（ecat-state-commit）每秒
 * {@link #commitAll()}，以 dirty 标记做脏跳过：CAS 清零成功才真正 commit，空闲期（无新写）
 * 零磁盘写（旧 per-device 多库形态即使空闲也每库固定刷 ~1MB/s，单库+脏跳过后稳态归零）。
 * commit 失败恢复 dirty 下一拍重试；{@link #shutdown()} 无条件最终 commit 后关库。
 *
 * <p>单轴 schema 版本策略（打开库时按 meta.storeSchemaVersion 判代，全程持锁单线程）：
 * 无标记且 states 空 = 新库，写 meta 三键；版本相符直接用；低于当前版本按迁移链
 * （{@link StoreMigration}）逐步 rewrite + 推进版本 + commit（每步一个事务原子）；
 * 高于当前版本（程序降级运行）响亮告警后整库改名旁置（states.db.incompatible-时间戳，
 * 原数据保留供检查）重开新库——状态是可重建缓存，不因版本冲突阻塞运行。
 *
 * <p>崩溃恢复语义：事务 WAL 保证已 commit 数据 ACID 完整，未 commit 的 WAL 数据在重新
 * 打开时按事务边界恢复（硬崩 Runtime.halt 后重开已探针实证，数字见设计文档）。
 *
 * <p>落盘层整体设计（存储布局图 / 版本策略表 / 探针实测数据 / 维护操作）见同目录
 * state-persistence-design.md；内存三槽模型与提交链路见 state-lifecycle-design.md。
 */
public class StateManager {

    /** 单状态库文件名（相对 baseDir，baseDir 由构造传入） */
    private static final String STORE_FILE_NAME = "states.db";
    private static final String META_MAP = "meta";
    private static final String STATES_MAP = "states";
    private static final String META_KEY_SCHEMA_VERSION = "storeSchemaVersion";
    private static final String META_KEY_CREATED_EPOCH_MS = "storeCreatedEpochMs";
    private static final String META_KEY_CORE_VERSION = "coreVersion";

    /**
     * 当前单轴 schema 版本。凡改持久化结构（PersistedState 字段 / 复合键格式 / map 拓扑 /
     * 存储引擎）一律 +1 并按旧版本补 {@link StoreMigration} 实现，不设免升例外。
     */
    static final int STORE_SCHEMA_VERSION = 1;

    /**
     * core 版本诊断标注（仅写 meta 供排查，不参与任何逻辑）。构建 manifest 未注入
     * Implementation-Version（assembly 插件仅配 mainClass），亦无现成版本常量可取，
     * 按约定写 "dev"，不为此引入新构建配置实体。
     */
    private static final String CORE_VERSION_TAG = "dev";

    private static final DateTimeFormatter ASIDE_TIMESTAMP =
        DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /**
     * 复合键分隔符（NUL）。业务 ID 结构上不可能含 NUL（deviceId 为 core 铸造的 UUID、
     * attributeId 为集成配置的属性名），前缀边界与拆键（indexOf(SEP) 首次出现）均无歧义——
     * removeDevice 的前缀清除必须拼上本分隔符，裸 deviceId 前缀会误中 id 以其为前缀的
     * 其他设备（dev1 误中 dev10）。
     */
    static final char SEP = '\u0000';

    private final String baseDir;
    /** 有序迁移链，打开库时按 fromVersion 匹配逐步推进；生产默认空链（库文件不存在即新建当前版本），
     * 测试经包私有构造注入演练迁移机制。 */
    private final List<StoreMigration> migrations;
    private final Object storeLock = new Object();
    /** 已打开的单状态库；null = 未打开（懒开：首次 save/load 触发，commitAll 不建库） */
    private volatile DB storeDb;
    private volatile ConcurrentMap<String, String> statesMap;
    /** 脏标记：saveState put 成功后置位；commitAll CAS 清零成功才真正 commit——
     * 空闲期跳过 commit 使磁盘写归零（此前的无条件每秒 commit 与数据量无关固定刷盘） */
    private final AtomicBoolean dirty = new AtomicBoolean(false);

    /** 自有每秒 commit 计时器（仅生产构造创建；注入形态/无持久化为 null），shutdown 时先停。
     * 包内可见 = 生命周期测试缝：所有实例计时线程同名 ecat-state-commit-0，测试须锚定
     * 本实例调度器断言终止，不能扫全 JVM 线程表（bug-record-20260831-115342）。 */
    final ScheduledExecutorService selfCommitScheduler;
    private final Log log = LogFactory.getLogger(getClass());

    /**
     * 默认构造函数（EcatCore.init 使用，不启用持久化）
     */
    public StateManager() {
        this.baseDir = null;
        this.migrations = Collections.emptyList();
        this.selfCommitScheduler = null;
    }

    /**
     * <b>生产入口</b>（EcatCore.init 使用）：baseDir 非空即启用持久化，并自持单线程 daemon
     * （ecat-state-commit）每秒批量 commit。自持而非借 core 调度设施——MapDB commit 是
     * 文件 IO（事务 WAL 刷盘，可阻塞在磁盘），按业务池边界 IO 禁入（2 线程小池会被
     * 持久化刷盘饿死全部业务计时），也不占设备调度引擎车道（那是设备 IO 治理域）；
     * core 持久化 tick 与两侧故障域物理隔离。
     *
     * <p>双构造形态收窄：生产一律走本构造（含 null baseDir 的无持久化形态见
     * {@link #StateManager()}）；注入形态（scheduler 参数）为包私有——仅 core 测试
     * 同包镜像使用，集成侧误用注入形态=持久化静默丢失（无自持 commit 计时器）。
     *
     * @param baseDir 持久化根目录（如 ".ecat-data/core/states/"），单状态库文件为
     *                {baseDir}/states.db
     */
    public StateManager(String baseDir) {
        this.baseDir = baseDir;
        this.migrations = Collections.emptyList();
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
     * 注入形态（<b>仅测试可见</b>，包私有）：scheduler 由调用方拥有并关停，
     * 本类只挂周期任务——core 测试同包镜像以此确定性驱动 commit 时序（不依赖真实秒拍）。
     * 生产代码一律用 {@link #StateManager(String)}（自持 commit 计时器；误用注入形态=
     * 持久化静默丢失）。集成仓测试需要持久化时也走生产构造（自持 daemon 1s commit，
     * 断言前手动 {@code commitAll()} 拍平）。
     *
     * @param baseDir  持久化根目录；null = 不启用持久化
     * @param scheduler 定时任务执行器，用于批量 commit；null = 不挂周期任务
     */
    StateManager(String baseDir, ScheduledExecutorService scheduler) {
        this(baseDir, scheduler, Collections.<StoreMigration>emptyList());
    }

    /**
     * 注入形态 + 迁移链注入（<b>仅测试可见</b>，包私有）：迁移链机制本身需要可演练——
     * 生产默认空链无存量迁移，注入测试迁移是唯一能确定性地覆盖「rewrite + 版本推进 +
     * 事务 commit」全路径的手段（手造旧版本库 + 注入迁移，禁真实秒拍等待）。
     *
     * @param baseDir  持久化根目录；null = 不启用持久化
     * @param scheduler 定时任务执行器；null = 不挂周期任务
     * @param migrations 有序迁移链，打开旧版本库时按 fromVersion 匹配执行
     */
    StateManager(String baseDir, ScheduledExecutorService scheduler, List<StoreMigration> migrations) {
        this.baseDir = baseDir;
        this.migrations = migrations;
        if (baseDir != null) {
            new File(baseDir).mkdirs();
        }
        this.selfCommitScheduler = null;

        if (scheduler != null) {
            scheduler.scheduleAtFixedRate(this::commitAll, 1, 1, TimeUnit.SECONDS);
        }
    }

    /**
     * 保存属性状态到单状态库（写入 states map，数据进事务 WAL，不立即 commit）
     *
     * @param device 属性所属设备
     * @param attr 需要持久化的属性
     */
    public void saveState(DeviceBase device, AttributeBase<?> attr) {
        if (baseDir == null) return;

        try {
            AttrState<?> s = attr.getState();
            if (s == null) return;  // 未 updateValue 过，无可持久化的 state
            ConcurrentMap<String, String> map = ensureOpen();
            // 围绕 state 持久化：从不可变 AttrState 精简映射，不戳 attr 内部字段
            map.put(stateKey(device.getId(), attr.getAttributeID()),
                JSON.toJSONString(PersistedState.from(s)));
            // put 成功后再置脏：与 commitAll 的 CAS 配合无丢失窗口——
            // 置位前 commitAll 至多跳过本拍（下一拍看到脏标记照常 commit），不会漏数据
            dirty.set(true);
        } catch (Exception e) {
            log.error("Failed to save state for attr " + attr.getAttributeID() +
                " device " + device.getId(), e);
        }
    }

    /**
     * 从单状态库加载单个属性状态
     *
     * @param device 属性所属设备
     * @param attrId 属性ID
     * @return 持久化状态，无数据时返回 null
     */
    public PersistedState loadState(DeviceBase device, String attrId) {
        if (baseDir == null) return null;

        try {
            ConcurrentMap<String, String> map = ensureOpen();
            String json = map.get(stateKey(device.getId(), attrId));
            if (json == null) return null;
            return JSON.parseObject(json, PersistedState.class);
        } catch (Exception e) {
            log.error("Failed to load state for attr " + attrId +
                " device " + device.getId(), e);
            return null;
        }
    }

    /**
     * 恢复属性状态（包含默认值兜底）
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
     * 手动触发状态库 commit（脏跳过：无脏数据时不产生磁盘写）
     */
    public void commitAll() {
        DB db = storeDb;
        if (db == null) return;  // 库未开（无任何 save/load 发生过）：no-op，不因计时器空拍建库
        if (!dirty.compareAndSet(true, false)) return;  // 空闲（无新写）：跳过刷盘
        try {
            db.commit();
        } catch (Exception e) {
            dirty.set(true);  // commit 失败恢复脏标记，下一拍重试
            log.error("Failed to commit state store", e);
        }
    }

    /**
     * 删除指定设备的全部持久化状态（硬删除语义；单库形态下 = 复合键前缀清除）。
     *
     * <p>调用方：serial-tcp-server 的 removeEntry（硬删除路径——entry 删除连同其持久化状态，
     * 与框架 removeEntry/disableEntry 的逻辑删保留 state 相反）。保留本 API 的原因：MapDB 3.1
     * 无 compaction API，单库条目只增不减，硬删除是唯一的空间回收手段。
     *
     * <p>前缀必须拼分隔符（deviceId + {@link #SEP}）：裸 deviceId 前缀会误中 id 以其为
     * 前缀的其他设备（dev1 误中 dev10），拼上 SEP 后边界无歧义。
     *
     * <p>清除后无条件即时 commit（不依赖 1s 拍与脏标记）：iterator.remove 的删除先进事务
     * WAL，未 commit 前崩溃会被回放丢弃——硬删除若留在崩溃窗口内，已删条目会复活，违背
     * 硬删除语义。commit 成功后清脏（本次 commit 同时刷掉了期间积攒的 save）；commit 失败
     * 恢复脏标记，下一拍 commitAll 重试兜底。
     *
     * @param device 设备实例（以其 getId() 为前缀清除）
     */
    public void removeDevice(DeviceBase device) {
        if (baseDir == null) return;

        String prefix = device.getId() + SEP;
        try {
            ConcurrentMap<String, String> map = ensureOpen();
            for (Iterator<Map.Entry<String, String>> it = map.entrySet().iterator(); it.hasNext(); ) {
                if (it.next().getKey().startsWith(prefix)) {
                    it.remove();
                }
            }
            DB db = storeDb;
            if (db != null) {
                // null = 并发 shutdown 先行（其无条件最终 commit 已兜底刷盘），无需再 commit
                db.commit();
                dirty.set(false);
            }
        } catch (Exception e) {
            // 删除可能已部分进 WAL：恢复脏标记让下一拍 commit 兜底，不留下没人管的未提交删除
            dirty.set(true);
            log.error("Failed to remove persisted state for device " + device.getId(), e);
        }
    }

    /**
     * 关闭单状态库：先停自有 commit 计时器（graceful shutdown：停发起新一轮 commitAll，
     * 不中断在飞轮次），再无条件最终 commit（兜底 WAL 内未刷数据，不依赖脏标记）+ close。
     * 在飞 commitAll 轮次与本方法 close 的残余竞态由双侧 catch 兜住：对已 close 的库再
     * commit 会抛，仅记错误日志不外抛。库从未打开（无持久化/无任何读写）时无事可做。
     */
    public void shutdown() {
        if (selfCommitScheduler != null) {
            selfCommitScheduler.shutdown();
        }
        DB db = storeDb;
        if (db == null) return;
        synchronized (storeLock) {
            try {
                db.commit();
                db.close();
            } catch (Exception e) {
                log.error("Failed to shutdown state store", e);
            }
            storeDb = null;
            statesMap = null;
        }
    }

    // ========== 内部方法 ==========

    /**
     * 懒开单状态库并返回 states map。打开 + schema 判代 + 迁移全程持 storeLock 单线程；
     * map 本身并发安全，打开完成后的读写不再持锁。双重检查使热路径无锁。
     */
    private ConcurrentMap<String, String> ensureOpen() {
        ConcurrentMap<String, String> map = statesMap;
        if (map != null) {
            return map;
        }
        synchronized (storeLock) {
            if (statesMap == null) {
                openStore();
            }
            return statesMap;
        }
    }

    /**
     * 打开单状态库并按单轴版本策略处理。不可用库（降级/未知来源/缺迁移）走
     * 「改名旁置 → 重开新库」路径，只允许一次旁置重开（旁置后仍不可用=文件系统级异常，
     * 抛明确异常响亮失败，不无限重试）。
     */
    private void openStore() {
        File dbFile = new File(baseDir, STORE_FILE_NAME);
        File parent = dbFile.getParentFile();
        if (parent != null) {
            parent.mkdirs();
        }
        DB db = openDb(dbFile);
        for (int attempt = 0; ; attempt++) {
            boolean usable;
            try {
                usable = classifyAndPrepare(db, dbFile);
            } catch (RuntimeException e) {
                closeQuietly(db);   // 失败路径释放句柄，避免半开库占文件锁
                throw e;
            }
            if (usable) {
                this.storeDb = db;
                this.statesMap = openMap(db, STATES_MAP);
                return;
            }
            try {
                db.close();
            } catch (RuntimeException e) {
                log.warn("Failed to close incompatible state store " + dbFile, e);
            }
            renameAsideIncompatible(dbFile);
            if (attempt > 0) {
                throw new IllegalStateException("State store still unusable after rename-aside: " + dbFile);
            }
            db = openDb(dbFile);   // 旁置后重开：文件不存在 → 必走新库初始化
        }
    }

    /**
     * 打开库：事务模式、无 mmap。去 mmap 是写放大治理的关键——mmap 页缓存让每次 commit
     * 产生与数据量无关的固定大刷写（隔离探针 /proc io 差分实测 ~1.06MB/次），FileChannel
     * 直写下脏 commit 仅 ~76KB/次；进程异常退出后的 stale file lock 风险同源于 mmap 映射，
     * 一并消除。
     */
    private static DB openDb(File dbFile) {
        return DBMaker.fileDB(dbFile)
            .transactionEnable()
            .make();
    }

    /**
     * 按单轴 schema 版本策略分类处理：新库初始化 / 版本相符 / 迁移链逐步推进 /
     * 不可用库返回 false（由调用方旁置重建）。每步迁移 rewrite + meta 版本推进 + commit
     * 为一个事务原子——中途崩溃整步回滚，重开后重跑同一步。
     */
    private boolean classifyAndPrepare(DB db, File dbFile) {
        ConcurrentMap<String, String> meta = openMap(db, META_MAP);
        ConcurrentMap<String, String> states = openMap(db, STATES_MAP);
        String raw = meta.get(META_KEY_SCHEMA_VERSION);

        if (raw == null && states.isEmpty()) {
            meta.put(META_KEY_SCHEMA_VERSION, Integer.toString(STORE_SCHEMA_VERSION));
            meta.put(META_KEY_CREATED_EPOCH_MS, Long.toString(System.currentTimeMillis()));
            meta.put(META_KEY_CORE_VERSION, CORE_VERSION_TAG);
            db.commit();
            return true;
        }
        if (raw == null) {
            // states 非空却无版本标记，来源不明：继续读=把未知格式误读为当代数据（静默损坏）
            log.error("State store {} has no {} marker but is not empty (unknown origin); renaming aside",
                dbFile, META_KEY_SCHEMA_VERSION);
            return false;
        }
        Integer storeVersion = parseSchemaVersion(raw);
        if (storeVersion == null) {
            log.error("State store {} has unparseable {}='{}'; renaming aside",
                dbFile, META_KEY_SCHEMA_VERSION, raw);
            return false;
        }
        if (storeVersion > STORE_SCHEMA_VERSION) {
            log.warn("State store {} schema version {} is newer than supported {} "
                + "(running an older build); renaming aside and starting fresh",
                dbFile, storeVersion, STORE_SCHEMA_VERSION);
            return false;
        }
        while (storeVersion < STORE_SCHEMA_VERSION) {
            StoreMigration step = findMigration(storeVersion);
            if (step == null) {
                // 缺步=无法安全升代：继续读会误读旧格式，旁置重建（响亮记录，不静默跳代）
                log.error("State store {} at schema version {} has no registered migration to {}; "
                    + "renaming aside and starting fresh", dbFile, storeVersion, storeVersion + 1);
                return false;
            }
            step.rewrite(states);
            meta.put(META_KEY_SCHEMA_VERSION, Integer.toString(step.toVersion()));
            db.commit();
            storeVersion = step.toVersion();
        }
        return true;
    }

    /** 不可用库改名旁置（states.db.incompatible-时间戳），原数据保留供人工检查后处置。
     * 伴生 WAL（states.db.wal.0，硬崩残留）一并改名，避免残留 WAL 与新库同名冲突。 */
    private void renameAsideIncompatible(File dbFile) {
        String ts = ASIDE_TIMESTAMP.format(LocalDateTime.now());
        File aside = new File(dbFile.getParentFile(), dbFile.getName() + ".incompatible-" + ts);
        if (!dbFile.renameTo(aside)) {
            throw new IllegalStateException(
                "Failed to rename aside incompatible state store: " + dbFile + " -> " + aside);
        }
        log.warn("Incompatible state store renamed aside: {} -> {}", dbFile, aside);
        File wal = new File(dbFile.getParentFile(), dbFile.getName() + ".wal.0");
        if (wal.exists()) {
            File walAside = new File(dbFile.getParentFile(), aside.getName() + ".wal.0");
            if (!wal.renameTo(walAside)) {
                throw new IllegalStateException("Failed to rename aside WAL of incompatible state store: " + wal);
            }
        }
    }

    private StoreMigration findMigration(int fromVersion) {
        for (StoreMigration m : migrations) {
            if (m.fromVersion() == fromVersion) {
                return m;
            }
        }
        return null;
    }

    private static Integer parseSchemaVersion(String raw) {
        try {
            return Integer.valueOf(raw.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static ConcurrentMap<String, String> openMap(DB db, String name) {
        return db.hashMap(name)
            .keySerializer(Serializer.STRING)
            .valueSerializer(Serializer.STRING)
            .createOrOpen();
    }

    /** 复合键：单库单 map 容纳全部设备，deviceId（UUID）+ SEP + attributeId 唯一定位一条记录 */
    private static String stateKey(String deviceId, String attributeId) {
        return deviceId + SEP + attributeId;
    }

    private void closeQuietly(DB db) {
        try {
            db.close();
        } catch (RuntimeException e) {
            log.warn("Failed to close state store during error unwind", e);
        }
    }
}
