# ConfigFlow 多源设备发现（Discovery）

> 本文档介绍 ConfigFlow 的**多源设备发现**机制——与 USER（人工向导）/ RECONFIGURE（重配置）并列的第三类入口。discovery 是核心能力，单独成篇；ConfigFlow 通用用法（Schema / ConfigItem / 步骤处理器 / i18n）见同目录 [README.md](README.md)。

## 1. 概述

discovery 让**外部程序**（同进程 SDK / 协议 broker）提供设备的**部分识别信息**触发 ConfigFlow，**跳过型号选择等前置 step**、直达连接配置 step，再经 `submitStep` 推进入网。适用于：

- 已知设备身份、需程序化批量建设备（IMPORT_FLOW）；
- 设备在网络上自动广播、需自动接入（ZEROCONF / MQTT）。

产物与人工向导一致（ConfigEntry + 设备实例），只是入口和起点不同。

## 2. 三类发现源（SourceType）

`SourceType` 共 7 值（见 `04-config-entry.md` §SourceType），其中 **3 个是携带 discovery payload 的发现源**（仅这 3 个可注册 `DiscoveryHandler`，`isPayloadDiscoverySource()` 为 true）：

| Source | 触发方 | payload（core 不透明） | broker / owner 位置 | 典型场景 |
|--------|--------|------------------------|---------------------|----------|
| `IMPORT_FLOW` | 同进程 SDK | `ImportFlowPayload{coordinate, version, data}` | 无独立 owner——SDK 直调统一入口 | 外部已知设备身份，程序化接入（如 test-discovery） |
| `ZEROCONF` | mDNS 广播订阅 | `ZeroconfDiscoveryPayload`（集成侧定义） | `integration-zeroconf`（jmdns broker） | 网络自动发现 |
| `MQTT` | MQTT 订阅事件 | `MqttDiscoveryPayload`（集成侧定义） | `integration-mqtt`（broker） | HA MQTT discovery 规范 |

> `USER` / `RECONFIGURE` 是既有入口（不携带 payload）；`IGNORE` / `UNIGNORE` 是控制源（抑制/召回发现，复用 ConfigEntry）。对非发现源注册 `DiscoveryHandler` 抛 `IllegalStateException`（严格模式）。

## 3. 统一入口

**所有 discovery 源只有一个对外入口**——`ConfigFlowService.startDiscoveryFlow`，`source` 参数区分类型（不再有 per-source 的独立入口/Manager）：

```java
import com.ecat.core.ConfigEntry.SourceType;
import com.ecat.core.ConfigFlow.ConfigFlowService;
import com.ecat.core.ConfigFlow.ImportFlowPayload;

ImportFlowPayload payload = new ImportFlowPayload(
        "com.ecat:integration-saimosen",            // coordinate（目标集成）
        1,                                           // version（由目标集成解析）
        "air.monitor.so2|SMS8200|SO2-SN001|测试SO2"); // data 串（由目标集成自解析）
ConfigFlowService service = core.getConfigFlowService();
ConfigFlowService.ConfigFlowInstance inst = service.startDiscoveryFlow(
        "com.ecat:integration-saimosen", SourceType.IMPORT_FLOW, payload);
// inst 落地连接配置 step（SHOW_FORM）；后续用 service.submitStep(flowId, stepId, input) 推进至 CREATE_ENTRY
```

- ZEROCONF / MQTT 的 broker 在各自协议集成命中匹配后，**调同一个** `startDiscoveryFlow`；IMPORT_FLOW 由同进程 SDK 直调。
- core 对 payload **完全不透明**（当 `Object` 透传，不解析）；`data` 串格式 + `version` 语义由**目标集成自定**（如 saimosen v1 = `class|model|sn|name`，详见各集成 README）。

## 4. 注册 discovery handler

设备集成在 flow 构造时注册（与 `registerStepUser` 平行），每个发现源最多 1 个：

```java
registerStepDiscovery(SourceType.IMPORT_FLOW, this::stepDiscoveryImportFlow);

private ConfigFlowResult stepDiscoveryImportFlow(ImportFlowPayload payload, FlowContext ctx) {
    // core 不解析 payload；本集成按 version 自解析 data + 自校验
    // 预填 entryData（跳过型号选择等前置步）→ 直达连接配置 step
    return showForm("protocol_select", schema, errors);
}
```

- handler 签名 = `DiscoveryHandler<P>`：`(P payload, FlowContext ctx) -> ConfigFlowResult`（比 user/reconfigure 的 handler 多一个强类型 payload）。
- `registerStepDiscovery` 内部把 discovery 步同步进 `stepDefinitions`（派生 stepId = `$discovery:<SOURCE>`），使 `handleStep` 能统一执行——discovery 不再自成一套执行路径。

## 5. 执行统一（drive / applyResult）

4 个入口（`startFlow` / `startReconfigureFlow` / `startDiscoveryFlow`）+ `submitStep` **全部收口**到 `ConfigFlowService.drive(flow, stepId, input)` + `applyResult`：

```
startDiscoveryFlow(coordinate, source, payload)
   │  guard（core 就绪 / 集成支持该 source / Layer2 去重）+ create + setup（stash payload + setSourceType）
   ▼
 drive(flow, "$discovery:<SOURCE>", null)
   │  registerIfAbsent（幂等注册）+ flow.handleStep(stepId, payload) + 统一异常策略
   ▼
 applyResult(flow, result)
   │  CREATE/REMOVE → finish；ABORT → abort；+ 持久化（applyTerminalResult）
   ▼
 ConfigFlowInstance（SHOW_FORM / CREATE_ENTRY / ABORT）
```

- **统一异常策略（唯一一处）**：`DuplicateUniqueIdException`（R5 去重）→ clean ABORT；`ConfigFlowException` → 透传；其它 `RuntimeException` → log + 透传。
- **统一终态处理（唯一一处）**：CREATE/REMOVE→`finishActiveFlow`，ABORT→`abortActiveFlow`，+ 持久化。杜绝多入口终态漂移（历史 bug：discovery 入口曾不清理 ABORT，留孤儿 flow）。

## 6. 去重（重复发现 = 正常路径）

已添加设备被重复发现是**正常路径**，不抛异常、不建重复 entry、不留孤儿 flow。两层去重：

| 层 | 机制 | 触发 | 结果 |
|----|------|------|------|
| Layer2（R12） | `hasActiveFlowWithDiscoveryPayload(coordinate, source, payload)` | 同 (coordinate, source, payload) 已有活跃 flow | 拒绝创建（入口 guard 抛 `ConfigFlowException`） |
| R5 | handler 内 `setEntryUniqueId(id, false)` 命中已存在 entry / 活跃 flow → 抛 `DuplicateUniqueIdException` | entry 已存在（或同 uniqueId 活跃 flow） | `drive` catch → **clean `ABORT`** |

**AbortReason 词表**（R5 命中时 `drive` 产生，集成无需关心）：
- `already_configured` — 同 uniqueId 的 entry 已持久化存在；
- `already_in_progress` — 同 uniqueId 已有活跃 flow 在跑。

**broker 日志分级**：broker（如 `ZeroconfDiscoveryIntegration`）检查 `startDiscoveryFlow` 返回——ABORT → DEBUG（去重 = 正常）；非 ABORT → INFO；异常 → DEBUG/WARN。**去重不产生 WARN 噪声。**

**周期清理（兜底）**：`ConfigFlowService` 构造时借 `core.getTaskManager().getMdcScheduledExecutorService()` 起 `scheduleAtFixedRate(cleanupExpiredFlows, 30min)`，清理任何未达终态的 flow（理论无孤儿，此为 defense-in-depth）。

> **jmdns 分阶段 TXT resolve 实证**：mDNS 把基本信息与 TXT 分两次回调 `serviceResolved`（4ms 内两帧：空 TXT → 满 TXT）。第一帧 handler 校验 `TXT 缺 model/sn` 自身 `ABORT`（等重解）；第二帧命中已存在 entry → R5 clean ABORT。两帧各自 register → abort，同 flowId 配对，**零孤儿**。

## 7. 严格校验（不建设备的情况）

以下情况不建设备，避免脏数据：
- core 未就绪 → 抛 `ConfigFlowException`（IMPORT_FLOW 同步源由调用方重试）；
- 目标集成未注册该 source 的 handler → 抛 `ConfigFlowException`；
- Layer2（R12）命中 → 抛 `ConfigFlowException`；
- R5（entry 已存在 / 同 uniqueId 活跃 flow）→ clean `ABORT`（`already_configured` / `already_in_progress`）；
- handler 自身校验失败（如 data 格式错、型号不支持）→ handler 返回 `ABORT(reason)`。

## 8. peer 模型（broker / owner 归属）

core 是**哑引擎**：只提供统一入口 + 机制（去重 / 终态 / 限流），不实现任何协议监听。监听 + 匹配归各协议集成（peer）：

- **ZEROCONF / MQTT**：broker 在 `integration-zeroconf` / `integration-mqtt`——拥有全部机制（payload 类型 / 订阅 registry / 匹配 matcher / 协议监听），是真正的 cohesive owner。命中匹配后调 core 统一入口。
- **IMPORT_FLOW**：无协议监听器，由同进程 SDK 直调统一入口（不独占 owner——曾有过 `ImportFlowDiscoveryManager` 一行委托 wrapper，因无独占逻辑已删除）。

## 9. 设计理念（对齐 Home Assistant）

- **flow = 一张 DAG**；不同入口 = 不同 start 节点（user / reconfigure / discovery:$SOURCE）。
- **入口之后的执行 = 一套机制**（drive + applyResult）；入口区分只限"选 start 节点 + 初始 setup + 前置 guard"。
- **重复发现 = 正常路径** → clean ABORT（非异常）；终态 flow 立即清理（不依赖懒过期）。
- core 对 payload 不透明（当 Object 透传）；身份仅由 `uniqueId` + `(coordinate, Source)` 表达（无 discovery-id）。

## 10. 参考实现

| 角色 | 实现 | 说明 |
|------|------|------|
| IMPORT_FLOW 设备集成 | `saimosen` `SaimosenConfigFlow.stepDiscoveryImportFlow` | data v1 = `class\|model\|sn\|name`；跳过型号选择直达 `protocol_select` / `qc_config` / `tube_config` |
| IMPORT_FLOW 同进程 SDK 触发 | `test-discovery` `ImportFlowTestDriver` | 事件驱动 E2E 桩（`INTEGRATIONS_ALL_LOADED` 后触发） |
| ZEROCONF broker | `integration-zeroconf` `ZeroconfDiscoveryIntegration` | jmdns 监听 + `ZeroconfMatcher` 匹配订阅表 → `startDiscoveryFlow(ZEROCONF, payload)` |
| ZEROCONF 闭环 E2E | `test-discovery` `ZeroconfTestLoop` + `ZeroconfServiceBroadcaster` | 广播 → 发现 → probe（HTTP 探活）→ confirm → 入网；常驻周期重广播压测去重鲁棒性 |

## 11. 相关文档

- ConfigFlow 通用用法（Schema / ConfigItem / 步骤 / i18n）：同目录 [README.md](README.md)
- SourceType 7 值定义：`require/ecat-core/04-config-entry.md` §SourceType
- ConfigFlow 引擎需求：`require/ecat-core/05-config-flow.md`
- 多源发现需求（R / D / AC）：`require/ecat-core/25-discovery-flow.md`
