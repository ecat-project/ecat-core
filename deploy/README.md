# deploy/ · 部署形态与启动脚本

ECAT 三种部署形态的 JVM 配方、启动脚本与 Win2003 32 位服务化方案。
数字依据：部署形态资源评估（workspace `arch-review-20260815/16-deployment-profiles-and-win2003-32bit.md`，含修订四内存预算——2026-08-16 用户拍板：部署形态 = Edge+ADM 采集单机 2.5GB 方案）。

## 1 · 形态矩阵

| | Edge（纯采集） | Edge+ADM 采集单机 | Full（全量） |
|---|---|---|---|
| 定位 | 2c/1G 采集机 | 一台机同时采集 + ADM 落库 | 开发机/服务器全量 |
| 组成 | core + 通信（serial/modbus/tcp/http…）+ 设备集成 + logicdevice + ecat-core-api | 左者 + **ruoyi（ADM 硬依赖，不装 ruoyi 则 ADM 采集链整体失效）** + env-air-device-manager + 本地 PG + Redis | 全部集成（含 media / env-* 等） |
| 不装 | ruoyi、ADM、media、env-*；无本地数据库/Redis 依赖 | media、env-*（除 ADM） | — |
| 启动脚本 | `start-edge.sh` | `start-edge-adm.sh`（Win2003 服务化：`win2003/nssm-install.bat`） | 无脚本约束（开发机自然形态） |
| JVM 参数 | `-Xms512m -Xmx512m -XX:+UseG1GC -XX:MaxMetaspaceSize=96m -XX:ReservedCodeCacheSize=64m -XX:MaxDirectMemorySize=32m -Xss512k` | `-Xms768m -Xmx768m -XX:+UseG1GC -XX:MaxMetaspaceSize=128m -XX:ReservedCodeCacheSize=96m -XX:MaxDirectMemorySize=64m -Xss512k` | 堆 ≤1024m（32 位含 UI 上限），非堆配额同 Edge+ADM |
| 内存预算（32 位口径） | 整机 1GB 目标（堆 512m + 下调非堆配额 + OS） | OS 250 + PG 350 + Redis 80 + 堆 768 + 非堆 ~430 ≈ **1.88GB** → **2.5GB 可行 / 3GB 稳 / 2GB 不可行**（2GB 的正解是 UI 分机） | ≥3GB（32 位含 UI 单机的推荐最小值） |

> 预算表口径为修订四原文（瘦身 = 关 swagger + lazy init + Druid 收缩 + 线程省 ~100 后）；
> 若 ruoyi 侧数据库与 ADM 的 PG 分开部署，按评估文档 §2.1 另计 MySQL 最小配置 ~300MB。
> UI 分机方案（更优解）：Edge 采集机（本方案主角）+ 任意规格 UI 机装 ruoyi/ADM/media，两机经
> 既有 9999 core-api/SSE 通信，架构零改动——采集侧满足 1GB 目标的同时 UI 侧不受旧机约束。

## 2 · 怎么选形态

- **现场只有一台旧机且要 ADM 本地落库** → Edge+ADM（2.5GB 起，3GB 稳）。
- **纯采集 / 已有 UI 机或云侧** → Edge（1GB），UI 走分机方案。
- **内存只有 2GB** → 不要压参数硬塞（评估结论：必进 swap 拖死），改 UI 分机方案。
- **32 位 Win2003 目标机** → Edge+ADM 配方 + `win2003/`（三条实机门禁过后才算兼容成立）。

## 3 · 启动脚本怎么用

两脚本结构相同（差异只在 JVM 配方与形态注释）：

```
<部署根>/
├── ecat-core-3.0.0.jar
├── deploy/                  # 本目录（脚本随仓库分发）
├── logs/                    # 日志（LOG_DIR 默认，可覆盖）
└── .ecat-data/              # core 运行数据（首次启动生成）
```

```bash
./deploy/start-edge-adm.sh                       # 前台参数全默认
ECAT_JAR=/path/to/ecat-core-3.0.0.jar ./deploy/start-edge.sh   # jar 路径覆盖
JAVA_BIN=/opt/jre/bin/java LOG_DIR=/data/logs ./deploy/start-edge.sh
```

- 可配项（环境变量）：`JAVA_BIN`（默认 `java`）、`ECAT_JAR`（默认部署根下 jar）、`LOG_DIR`（logback 经 `-DLOG_DIR` 识别）、`PID_FILE`。
- **工作目录硬约束**：`.ecat-data` 是相对路径常量，core 按 JVM cwd 解析；脚本已 `cd` 部署根（脚本位于 `<部署根>/deploy/`，根 = 上一级）。手敲 `java -jar` 时同理，必须在部署根执行。
- 幂等：已有活实例（PID 文件命中）拒绝二次启动；残留 PID 文件自动清理。
- **停机**：`kill -15 $(cat ecat-core.pid)`——SIGTERM → shutdown hook → 状态持久化 + 任务收尾；勿 `kill -9`（丢状态）。编排层（systemd 等）直接发 SIGTERM 即可，其等待窗须 ≥ 停机编排总预算 100s（`CoreShutdown.TOTAL_BUDGET_MS`）+ ~10s JVM 退出余量，即 ≥110s（systemd `TimeoutStopSec`；Win2003 形态见 `win2003/nssm-install.bat` 的 `AppStopMethodConsole 110000`）——低于此值会在病态停机（多阶段同时卡死）时截断编排丢尾批。

## 4 · 安装集怎么改（integrations.yml）

安装集 = `<部署根>/.ecat-data/core/integrations.yml`，每集成一条 `groupId:artifactId` 记录：

```yaml
integrations:
  com.ecat:integration-ecat-common:
    enabled: true
    groupId: com.ecat
    artifactId: integration-ecat-common
    version: 3.0.0
    state: RUNNING
```

- **收安装集（做 Edge）**：把 ruoyi / media / env-* 条目删除或 `enabled: false`，对应 jar 从 `.ecat-data/integrations/` 移除；改完重启 core 生效。
- **注意 ADM 的连带关系**：装 env-air-device-manager 必装 ruoyi（ADM 启动期 fail-fast 硬依赖），且需要本地 PG + Redis。
- media 集成带 JavaCPP/FFmpeg native，32 位/小内存机一律不装。
- 集成的新增/升级走 core 的集成安装流程（UI/API），比手编 yml 可靠；yml 手改适用于裁剪。

## 5 · 日志目录结构（五通道 + 兜底）

`LOG_DIR`（默认 `<部署根>/logs`）下，logback 五通道预算（合计 cap 650MB，1GB 兜底）：

| 文件 | 内容 | 保留 / cap |
|---|---|---|
| `lifecycle.log` | 生命周期通道（marker=LIFECYCLE，一行一事，grep 设备即得全史） | 180 天 zip / 100MB |
| `comm-health.log` | 通讯健康通道（marker=COMM，连接状态转移） | 90 天 / 150MB |
| `error.log` | 错误通道（WARN+，同签名 3s 窗口限频去重） | 90 天 / 100MB |
| `app.log` | 应用主通道（INFO+ 常规输出） | 30 天 / 300MB |
| *（诊断 DEBUG）* | 默认不落盘；临时排障 `-Decat.log.level=debug` 进 app.log，用完即关 | — |
| `core-api.log` | **启动脚本重定向的 stdout/stderr**：承接 logback 初始化前的启动输出、System.out 与 OOM 栈（五通道看不到的兜底；追加模式，无自动轮转，必要时人工清） | — |

## 6 · 32 位注意（Win2003 目标，详见 `win2003/README.md`）

- **堆 ≤768m 的地址空间理由**：32 位进程用户地址空间默认 2GB，堆 + Metaspace + CodeCache +
  线程栈 + Direct + native 全挤在内，安全天花板 ~1.2-1.4GB。Edge+ADM 配方 768+128+96+64 + 栈/native
  之外已无多少余量；物理内存层面 32 位 Windows 也只见 ~3.3-3.5GB，整机投入 4GB 封顶。
- **线程 ≤100 纪律（生死项，非优化项）**：每线程栈 reserved 512k-1M 同样吃地址空间——
  32 位下硬上限 **100**、Edge 目标 **≤60**。现状参照：全量开发机实测 基线 482 → A 阶段后 328；
  B 阶段已落地的收敛项（调度 v2 表轮引擎万级任务零线程增长、serial 超时合并表轮 25→0、
  无名/池线程归因减半）尚未出整机复测快照——**Win2003/Edge 上线前以目标安装集的
  system_health 实测线程数 ≤100（Edge ≤60）为准**，超了先查安装集是否混入 media/env-*。
- G1 在 32 位 client VM 的可用性属实机门禁项（T1.2），过门禁前不视为已验证。
