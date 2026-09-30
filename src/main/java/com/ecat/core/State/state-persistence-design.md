# State 持久化落盘层设计：单库单 map + 单轴 schema 版本

本文档是 `com.ecat.core.State` 包持久化落盘层（`StateManager`）的设计文档，说清单状态库的
存储布局、版本策略、写入节拍与崩溃恢复语义。读 `StateManager` 代码前先读此文档。

> 本文档随源码版本（.md 放在 src/main/java 下，仅源码树可见，不打入 jar）。代码注释是单点说明，
> 本文档是整体关系与设计动机的汇总。

---

## 1. 目标与收益

**本设计解决什么**：旧形态 StateManager 每设备一个 MapDB 库文件，`ecat-state-commit` 线程
每秒对全部打开库无条件 commitAll。隔离探针（/proc io 差分，MapDB 3.1.0）实测：事务+mmap
配置下每次 commit 固定写 ~1.06MB、与数据量无关——47 库 × 1 次/秒 ≈ 48-60MB/s 的持续磁盘写，
**健康系统同样在烧**，与业务负载完全脱钩。

**受益者**：

- **运维**：磁盘写速从 48-60MB/s 降到稳态 <0.1MB/s（空闲归零）；df 波动消失；磁盘寿命
  与告警噪音（持续高 IO）同步消失。磁盘足迹从 47+ 个库文件的 138MB 量级收敛到单库 ~2MB。
- **未来写升级/维护脚本的人**：版本判代从「逐记录猜 version 字段」收敛为「读库级 meta 的
  storeSchemaVersion」——升级脚本面对的是一个可枚举的版本轴和一条明确的迁移链，不再需要
  处理「同库内新旧记录混存」的组合爆炸；降级场景有确定的 rename-aside 语义可依赖。

**可验证收益**（探针实测，验证方法见 §6）：

| 指标 | 旧形态（per-device 多库） | 新形态（单库） |
|---|---|---|
| 每次 commit 磁盘写 | ~1.06MB 固定（与数据量无关） | 脏 commit ~76KB / 干净 ~72KB |
| 空闲期写速 | ~48-60MB/s（47 库×1 次/s） | 0（脏跳过后 commit 不发生） |
| 稳态写速 | 持续烧盘 | <0.1MB/s |
| 磁盘足迹 | 138MB 量级（47 库） | ~2MB（单库） |
| 崩溃恢复 | ACID（事务） | ACID（事务，硬崩探针实证，重开 ~261ms） |

---

## 2. 存储布局

全部设备的属性状态落**一个** MapDB 库 `{baseDir}/states.db`（生产 baseDir =
`.ecat-data/core/states/`，由 EcatCore 传入）。库内两个命名 map：

```
{baseDir}/
  states.db                        ← 单库文件（MapDB 事务模式，FileChannel 直写，无 mmap）
  states.db.wal.0                  ← 事务 WAL（仅在写后未 commit / 硬崩残留时存在，见 §6）
  states.db.incompatible-<ts>      ← 不可用库改名旁置（仅降级/未知来源时出现，见 §5）

states.db 内部：
  "meta"   (STRING → STRING)       库级元数据
     ├─ storeSchemaVersion = "1"     单轴 schema 版本（判代唯一依据）
     ├─ storeCreatedEpochMs          建库时刻（诊断）
     └─ coreVersion                  写库时的 core 版本标注（诊断）
  "states" (STRING → STRING)       全部设备的属性状态
     ├─ key   = deviceId + SEP(NUL) + attributeId   （复合键，SEP 说明见下）
     └─ value = PersistedState JSON
```

**为什么复合键用 NUL 分隔**：deviceId 是 core 铸造的 UUID、attributeId 是集成配置的属性名，
两者在业务上都不可能含 NUL——前缀边界与拆键（indexOf(SEP) 首次出现）因此无歧义。前缀类操作
（removeDevice 清除）**必须拼上分隔符**：裸 deviceId 前缀会误中 id 以其为前缀的其他设备
（dev1 误中 dev10）。单 map 免去 per-device map 的打开/遍历/关闭管理。

**为什么无 mmap**：mmap 页缓存让每次 commit 产生与数据量无关的固定大刷写（探针实测
~1.06MB/次）；FileChannel 直写下脏 commit 仅 ~76KB/次。进程异常退出后的 stale file lock
风险同源于 mmap 映射，去 mmap 一并消除。

**为什么记录（PersistedState）不带版本字段**：版本判代收敛到库级 storeSchemaVersion 单点。
记录级版本意味着「同库内多代记录混存」——每处读取都要判代，迁移等于逐条改写还无法原子；
库级版本让迁移成为打开库时的一次性、事务原子的整 map 改写。旧形态的记录级 version<2
丢弃分支已随本次重构删除：新库记录永远与当前代码同代。

---

## 3. 单轴 schema 版本策略

打开库时读 `meta.storeSchemaVersion`，四种情形（全程持锁单线程）：

| meta 版本 | states map | 判定 | 动作 |
|---|---|---|---|
| 无标记 | 空 | 新库 | 写 meta 三键（版本=当前/建库时刻/core 版本）+ commit |
| = 当前 | 任意 | 相符 | 直接使用 |
| < 当前 | 任意 | 旧代库 | 沿迁移链逐步：rewrite → meta 版本推进 → commit（每步一个事务原子）；**缺步则旁置重建**（响亮 error，继续读会误读旧格式） |
| > 当前（或不可解析） | 任意 | 降级/损坏 | 响亮告警 → 整库改名旁置 `states.db.incompatible-<yyyyMMddHHmmss>` → 重开新库 |

**治理纪律**：凡改持久化结构——PersistedState 字段、复合键格式、map 拓扑、存储引擎——
一律升 storeSchemaVersion 并按旧版本号补 `StoreMigration` 实现，**不设免升例外**。
理由：库级单轴版本是打开时判代的唯一依据，任何「小改动不必升版」都会在未来的某次升级
中变成「无法区分的新旧混存」，那时修复成本是 data archaeology，而升版本的成本是一行常量。

### 迁移链机制

```java
interface StoreMigration {
    int fromVersion();   // 迁移起始库版本
    int toVersion();     // 目标版本（= from + 1，链式逐步）
    void rewrite(ConcurrentMap<String,String> states);  // 原地改写 states map
}
```

- StateManager 持有有序迁移列表；打开旧库时从当前版本起按 `fromVersion` 匹配逐步执行，
  每步 rewrite + meta 版本推进 + commit 构成**一个事务原子**——中途崩溃整步回滚，
  重开后重跑同一步，不会出现「改了一半」的库。
- rewrite 只依赖 states map 自身内容，不改 meta（版本推进由 StateManager 统一执行）。
- **v1 空链**：当前版本 1，迁移列表为空——新库直接建在当前版本，无存量迁移。
  机制先行：下次改持久化结构时升版本、补实现、（如需要）测试构造注入演练即可，
  打开/判代/推进/原子的全部路径已有测试锁（`StateManagerTest.testMigrationChain_*`）。

---

## 4. 生命周期

```
构造（baseDir 非空）      mkdirs + 自持 ecat-state-commit 单线程（每秒 commitAll）；库不打开
publicState（commit 点）  → saveState：懒开库（首次）→ put(复合键, JSON) → dirty=true
                          （数据进 WAL，未刷盘）
每秒 commitAll            库未开 → no-op（空拍不建库）
                          dirty CAS(true→false) 失败 → 空闲，跳过（零磁盘写）
                          CAS 成功 → db.commit()（WAL 刷盘）
                          commit 抛异常 → dirty 恢复 true，下一拍重试（log error）
restoreAttributeState     loadState 读复合键 → attr.restore / 默认值兜底
onPause / 设备收尾        不再关库（单库为全部设备共用，任何一方无权关闭）；数据由 1s
                          commitAll 照常落盘
removeDevice（硬删除）    复合键前缀清除（deviceId+SEP）→ 无条件即时 commit + 清脏
                          （语义与约束见下节）
shutdown                  停计时器（graceful）→ 无条件最终 commit（不依赖脏标记，兜底 WAL
                          尾数据）→ close。这是全系统唯一关库点
```

### 4.1 设备状态硬删除（removeDevice）与空间口径

- **调用方**：serial-tcp-server 的 removeEntry（硬删除路径——entry 删除连同其持久化状态）。
  框架的 removeEntry/disableEntry 是逻辑删（保留 state，跨重建/enable 复活），不经过本 API。
- **机制**：遍历 states map，`startsWith(deviceId + SEP)` 精确前缀（拼分隔符防 dev1 误中
  dev10），iterator.remove；随后**无条件即时 commit** 并清脏——删除先进事务 WAL，未 commit
  前崩溃会被回放丢弃（已删条目复活），硬删除语义要求即时持久，不能等 1s 拍。
- **保留原因与空间口径**：MapDB 3.1 无 compaction API，单库条目只增不减——没有本 API，
  硬删 entry 的状态空间永不回收。硬删除即回收（条目从 map 消失，空间随后续写复用）；
  逻辑删（软删化身）的条目按 KB 级累积，可接受；未来若需整体回收（长期运行碎片/软删条目
  过多），手段是**重建库维护操作**：停写 → 新建库按当前内存态重写 → 换名启用。

懒开 + 脏跳过是写速收益的两个支点：懒开保证「从未持久化的进程零库文件」；脏跳过保证
「有库但空闲的进程零磁盘写」。二者叠加后，磁盘写与真实业务写入量成正比，而不是与库数量×
节拍频率成正比。

---

## 5. 降级 rename-aside 语义

meta 版本高于当前实现（程序降级运行）、states 非空却无版本标记（来源不明）、迁移链缺步
（无法安全升代）三种情形统一走**改名旁置**：

1. `states.db` → `states.db.incompatible-<yyyyMMddHHmmss>`（伴生 `states.db.wal.0` 若存在
   一并改名——残留 WAL 与新库同名会冲突；MapDB close 后才可 move）
2. 重开 `states.db` 作为新库（写当前版本 meta）
3. 旁置文件原样保留，日志响亮记录（降级=warn，来源不明/缺步=error）

**为什么旁置而不是抛异常阻断运行**：状态是**可重建缓存**——设备轮询下一周期即回填当前值，
丢失的只是历史状态快照，不是业务数据。为缓存可用性阻断整个采集进程，代价收益不成比例。
**为什么不是丢弃删除**：旁置成本为零而保留了「降级前到底有什么」的取证能力；删除不可逆，
误判（例如实际是升级产物被误标降级）后无法回滚。

---

## 6. 崩溃恢复证据与验证方法

隔离探针（独立 JVM、/proc io 差分、MapDB 3.1.0）实测：

- **事务+mmap（旧配置）**：每次 commit 固定写 ~1.06MB，与库内数据量无关。
- **事务+FileChannel（现配置）**：脏 commit ~76KB/次，干净 commit ~72KB/次。
- **脏跳过**：加 dirty CAS 后，空闲期 commit 不发生，磁盘写归零。
- **WAL**：按需增长（数百字节量级），commit 后收拢；硬崩（Runtime.halt）后残留
  `states.db.wal.0`，重新打开时按事务边界回放——已 commit 数据完整、未 commit 数据丢弃，
  ACID 完好，重开耗时 ~261ms。
- **干净 close 后**目录仅剩 `states.db` 一个文件。

**维护操作口径**：

- **旁置文件处置**：`states.db.incompatible-*` 是降级/异常时点的状态快照，确认不需要取证
  后可直接删除（内容=可重建缓存）；若需回溯，可用当前版本 MapDB 直接打开读取（旁置库的
  格式停留在其 meta 版本所标识的代）。
- **旧目录树清理口径**：单库化之前每个设备一个库（`{baseDir}/{groupId}/{integrationId}/
  {deviceId}.db`）。升级后这棵旧树不再被读取（新库文件名不同），设备轮询会自然回填新库。
  旧树整树可删（同样=可重建缓存）；不删也无害，只是占磁盘。判定标准：新库
  `states.db` 存在且 meta 版本相符后，旧树即可进入清理候选。
- **重建库（空间回收）**：长期运行后若需回收软删条目/碎片（无 compaction 的代价），执行
  重建库维护操作：停写 → 以当前内存态新建库写当前版本 meta → 换名启用（详见 §4.1）。

---

## 7. 与 state-lifecycle-design.md 的关系

- **state-lifecycle-design.md**（同目录）：管**内存侧**——attr/state 分离、三槽状态模型、
  updateValue→publicState 的提交与发布链路、持久化数据源为何是 commit 点的 midState。
- **本文档**：管**落盘侧**——数据离开 attr 之后怎么进库、何时刷盘、版本如何判代、
  崩溃后如何恢复、异常库如何处置。

两份文档的接缝在 `publicState` 的持久化步骤（lifecycle 文档 §3.2「持久化：saveState」）：
那边解释「为什么此时落盘的值是自洽的」，这边解释「落下去之后发生了什么」。
