# IO 资源归属登记与通讯追踪权威归因

> 状态：v0.3 草案（2026-09-12；v0.2 全 IO 库扩展 + 归属模型重构：OwnerLevel/Usage 双枚举、借用带主、分层查询，见 §4）

## 1. 目标与收益

**给全部 IO 库资源（serial / modbus / tcp / httpserver / mqtt）建立"使用者"的权威记录，使通讯追踪归因从时序启发式升级为直接读取。**

| 受益者 | 收益（可验证） |
|---|---|
| 通讯追踪（CommTrace / core-api 查询面） | `deviceId` 过滤权威化：手动命令路径、回补类事务的 TX/RX 帧不再无归属；RX 归因不再依赖 30s 时间窗猜"最近带键 TX" |
| 资源运维诊断 | 能回答"谁在用这个口/连接"（常住人口）与"谁的事务在飞"（当前住户）；httpserver 共享 server 池任一消费方 `unregisterServer` 无条件停服殃及池鱼的现行风险显性化（§2.2） |
| 上层编排（如 ADM 设备回补，本文只作动机不作设计） | deviceId → 资源反查成为账本读面，无需解析各设备集成 entry 形态 |
| 集成作者 | 迁移后 register 传类型化 owner，替代现状混用的自由字符串 identity / 约定俗成 clientId |

## 2. 背景

### 2.1 缘起

ADM（env-air-device-manager，ruoyi 侧业务编排层）规划设备侧历史数据回补：不经设备集成的轮询/解析，直接以独立注册方身份复用设备的 IO 通道发回补指令。该需求暴露出 infra 层缺口：**IO 库不知道资源被谁使用**——deviceId 反查无门、新增注册方的流量在通讯追踪中无归属。经第一性分析确认：这不是 ADM 一家的需求，而是 IO 资源溯源机制的普遍缺口；机制一次定稿覆盖全部 5 个 IO 库，落地按库分期（§6），ADM 为未来消费方之一。

### 2.2 现状证据画像（5 库全景）

| 库 | 资源（键） | 账本 | 引用计数 | 互斥/串行化原语 | CommTrace | 归属信息现状 |
|---|---|---|---|---|---|---|
| serial | 共享 SerialSourcePort（portName）；每注册方一个视图 | `serialPorts` Map + 每端口 `connectedSources`（SerialSourcePort.java:93） | ✓ 末源拆港 | 端口事务锁（FIFO 等待 / tryAcquire / 幽灵锁收割 / 事务代数闸门） | SERIAL 双向（:744,:900） | identity 串三种形态混用（下表） |
| modbus | 共享 ModbusSource（ip:port 或 portName）+ 每设备 DeviceSpecificModbusSource 包装 | `tcpSources`/`serialSources` Map（ModbusIntegration.java:33-34）+ `registeredIntegrations` Set（ModbusSource.java:481-487） | ✓ 空集关连接 | ReentrantLock+Condition 等待队列（maxWaiters 上限、ghost 回收） | MODBUS_TCP 双向（:189,:193） | identity 串两种形态混用 |
| tcp | client 每调用方自建（无键）；server 独占 ip:port | serverRegistry Map 撞键拒绝（TcpIntegration.java:126-139）；**TcpResourceKeys 仅键无主**（TcpResourceKeys.java:19）；`TcpServerImpl.integrationId` 在对象上未入账本（:34-35） | ✗ | client：writeQueue CLQ + 每连接 InboundLane 单飞；server：连接级回调 | **仅 client** TCP_CLIENT 四点（ReconnectTcpClient.java:119,426,502,537）；server 入站零埋点 | client 完全游离；server 有数据未账本化 |
| httpserver | server 池化共享 ip:port；client 每消费方自建 | `serverPool` Map（computeIfAbsent，HttpServerIntegration.java:110-124） | ✗ **unregisterServer 无条件 stop**（:139-149） | server：lifecycleLock + EndpointLanes 每端点公平锁（实例内，上限 32）；client：内部 ioPool + inFlight 集合 | **仅 client** HTTP（SDK 层统一埋点 HttpCommTrace.java:62-94，MDC 归因）；server 入站零埋点 | client 经 RemovalHost 隐式绑宿主（EasyHttpClient.java:114-127，**最强生命周期绑定设计**）但无登记；server 池 6 消费方共享无记账（benmai / ecat-core-api / env-calibration-composer-api / hikvision / media-api / zkteco） |
| mqtt | 嵌入式 Broker（进程级单例）+ client 句柄（clientId 独占） | `clients` Map，撞 clientId 抛 ISE（MqttClientManager.java:39,58-59） | ✗ removeClient 无条件 stop | 每句柄会话队列 synchronized 单飞 drain（容量 256） | **零接入**（CommTraceTransport 无 MQTT 枚举值） | clientId 约定俗成弱归属（如 "vision-analysis-events"） |

**范围判定依据**：以 ecat-config.yml 依赖图（145 条边）穷举全部被依赖服务仓——zeroconf（mDNS 发现，其注册表天然按消费方 coordinate 键控）、media（设备键控垂直域服务）不是 IO 传输库；serial-tcp-server 是**桥（消费方）不是库**（依赖 serial+tcp 复用）；usr/ebyte/wit-motion 等全是 modbus 消费方。IO 级传输库封闭集合 = 上述 5 个。

**反差证据（归属知识停在上层，IO 层无感）**：serial-tcp-server 桥持有全仓唯一按 entryId 记账的完整归属账本（`entryResourcesMap`，SerialTcpServerIntegration.java:55,397-408）——但它底层的 serial/tcp 库只见到 identity 字符串或一无所知。归属信息只在桥内可用，跨桥不可见、追踪不可见，正是机制缺口的活标本。

**serial/modbus identity 传参三种形态混用，无结构无契约**（全仓 grep 实测）：

| 集成 | register 传参 |
|---|---|
| fronttech、saimosen(modbus)、thermofisher(modbus)、vaisala | `类名-entryId` |
| saimosen(serial)、thermofisher(serial)、teledyne | `类名`（无实例区分） |
| sailhero | `类名-objectId` |

**通讯追踪已为归属缺失打过两个补丁**：

1. **工单 G MDC 注入**：轮询链起链处 `DeviceMdcContext.scopeOf(host)` 把 `device.id/name/integration.coordinate` 写入线程 MDC（DeviceMdcContext.java:64-88；注入点：SerialPolling.java:309、ModbusPolling.java:251、TcpPolling.java:121），TX 帧在捕获点继承。已知洞：**手动命令路径常无键**——测试注释自证"无键 TX（如手动命令路径）"（CommTraceRxAttributionTest.java:117）。
2. **RX 时间窗启发式回填**：读线程（sweeper/selector）无 MDC，按「本口最近一次带设备键 TX + 30s 新鲜度窗」猜归属。测试注释自证成立前提：**"串口源锁纪律下同口至多一笔在飞事务，回填=归属成立"**（CommTraceRxAttributionTest.java:40-43）——多注册方（本机制落地前的 ADM 回补即是一例）会稀释该前提。

**展示契约已就绪**：`CommTraceEvent` 已有 `deviceId/deviceName/coordinate` 字段与按 device 过滤（matchesQuery，CommTraceEvent.java:41-43,139-151）——缺的只是权威数据源。

## 3. 要求

1. **双向溯源**：资源 → 使用者（常住人口 + 当前在飞事务持有者）；使用者（deviceId）→ 资源。
2. **权威归因优先级**：资源登记的当前持有者 > 线程 MDC > 时间窗回填 > 如实 null；不猜测填充（沿用 CommTraceEvent 既有契约）。
3. **兼容**：既有 `register(info, String identity)` / `registerClient(clientId)` 签名与全部既有调用方（19+ 集成、6 个 server 消费方、mqtt 消费方）零改动可继续工作（包成 LEGACY owner，如实标注，不伪造 deviceId）。
4. **分层治理**：core 只新增纯数据类型与缓冲入口（依赖方向 = 库→core，既有边，pom 实证 5 库全依赖 ecat-core）；账本实现留在各库，不收编、不建统一 ResourceRegistry。
5. **不破坏既有语义**：serial/modbus 引用计数、锁 FIFO/tryAcquire/幽灵锁收割/事务代数闸门、modbus 同连接帧格式拒绝、RTU 经 serial 底层的子注册、tcp server 独占注册撞键拒绝、mqtt clientId 独占——语义原样，只富化记账维度（httpserver serverPool 计数化是唯一提议的语义变更，单独决策见 §9-5）。

## 4. 归属模型

核心主张：**归属信息的权威载体是资源的使用登记**——共享事务资源上再叠加"当前住户"（事务锁）。资源按「持有方数 × 收发方向」第一性分为三类，归属模型在三类上形态不同：

| 资源形态 | 判据 | 实例 | 常住人口（注册账本） | 当前住户（在飞事务） |
|---|---|---|---|---|
| **A 共享事务资源** | 多注册方 + 事务互斥 | serial port、modbus 连接 | register 账本（多 owner 集合） | 事务锁 owner（acquire/tryAcquire 登记，release/收割清除） |
| **B 私有主动资源** | 单持有方主动收发 | tcp client、http client、mqtt client 句柄 | 构造/注册时的唯一 owner | = 常住人口（无共享无竞争，恒等） |
| **C 共享被动资源** | 多注册方被动收 | tcp server（listener）、http server（ip:port 池） | 注册方 owner 集合 | 不适用（无事务互斥） |

```java
// 归属 core；5 库已依赖 core，不引入新依赖方向。包归属见 §9 待决策点 3。
// 身份层级与使用方式是两个正交维度：层级显式枚举（不靠字段有无猜），用法二值。
public enum OwnerLevel { INTEGRATION, ENTRY, DEVICE, LEGACY }
public enum Usage { DIRECT, ADAPTER }

public final class ResourceOwner {
    private final OwnerLevel level;    // 哪一层的使用者（显式标注）
    private final String coordinate;   // 哪个集成（域归属；LEGACY 兼容占位时为 null）
    private final String entryId;      // ENTRY/DEVICE 层必填，INTEGRATION 层为 null
    private final String deviceId;     // DEVICE 层必填，上两层为 null（device↔entry 1:1、DeviceBase.getId()=entryId，DEVICE 层两字段同值一并记录，保持层级前缀一致）
    private final Usage usage;         // DIRECT 直接使用 / ADAPTER 经他库转手落到本资源
    private final String rawIdentity;  // 仅 LEGACY：旧签名自由字符串原样保留；其余层为 null
}
// 构造期校验层与字段一致（严格模式 fail-fast，不猜）：
// INTEGRATION → entryId/deviceId 必 null；ENTRY → entryId 必非空、deviceId 必 null；
// DEVICE → entryId/deviceId 必非空；LEGACY → coordinate/entryId/deviceId 必 null、rawIdentity 必非空
```

**借用带主（身份穿透）**：经他库转手落到资源上的注册，登记的是**最终使用者的完整身份**，ADAPTER 只标注"路径经过转手"，不把中间库登记为使用者。例：sailhero 设备经 modbus-RTU 用串口，serial 账本记 `(DEVICE, com.ecat:integration-sailhero, entryId, deviceId, ADAPTER)`。收益：① 追踪归因一跳到位——RTU 帧直接归属设备，无需再去 modbus 账本二次换算；② 资源账本见真人——"这个口谁在用"直接列设备清单。账目形态：同一条 RTU 串口上 N 个设备 = 串口视图使用者集合 N 条 ADAPTER 条目（摘一条少一条，摘空拆视图，引用计数天然对上）；跨集成共线（aogan/saimosen 同总线）集合里多家各一条，自然成立。机制代价：modbus 的 register 须把调用者 owner 原样转发给 serial（库间 API 语义，Phase 1 落实）。

两级归属落地载体：

| 层级 | 载体 | 登记时机 | 回答 |
|---|---|---|---|
| 常住人口 | 各库资源账本（serial `connectedSources` 富化；modbus `registeredIntegrations` Set→Map；tcp TcpResourceKeys/新弱账本；httpserver serverPool 值富化；mqtt clients 值富化） | `register()` / 构造 | 这个口/连接/句柄上注册了哪些使用方 |
| 当前住户 | A 类事务锁状态（serial：SerialSourcePort；modbus：ModbusSource——两者均已记 `lockAcquireTime/lockAcquireThread`，ModbusSource.java:512-513，顺位加 `lockAcquireOwner`） | `acquire()` / `tryAcquire()` | 当前在飞事务是谁的（release/幽灵锁收割时清除） |

锁纪律（同口至多一笔在飞）正是现行 RX 启发式的成立前提——把 owner 挂到锁上，A 类归因从"用时序猜"变为"直接读"，机制自洽。归因收益按形态分层：**A 类最大**（RX 归因权威化，替代时间窗回填）；B 类 TX/RX 归因 owner 优先于 MDC；C 类 owner 先服务于资源运维诊断（入站流量可见性是独立缺口，§7）。

**分层查询（不同层不同函数，签名即层级契约；查询面留在各库账本读面，core 不建统一注册中心，§3-4）**：

```java
// 使用者 → 资源：按层收窄
List<ResourceRef> resourcesOfIntegration(String coordinate);                    // 该集成名下全部（含其 entry/device）
List<ResourceRef> resourcesOfEntry(String coordinate, String entryId);
List<ResourceRef> resourcesOfDevice(String coordinate, String entryId, String deviceId);

// 资源 → 使用者：按层折叠
List<ResourceOwner> deviceOwnersOf(ResourceRef ref);      // 全量（ADAPTER 逐设备一条）
List<ResourceOwner> entryOwnersOf(ResourceRef ref);       // device 折叠到 entry
List<ResourceOwner> integrationOwnersOf(ResourceRef ref); // 折叠到集成去重
```

例：sailhero 两设备共用一条 RTU 口——`deviceOwnersOf(口)` 返回两条 `(DEVICE,…,ADAPTER)`；`integrationOwnersOf(口)` 折叠为 `[sailhero]`，"这口是谁家的"一眼答。

## 5. 各侧改动

### 5.1 core（ecat-core）

- 新增 `ResourceOwner` / `ResourceUsage` 类型。
- `CommTraceBuffer` 增加 owner 权威入口：`tx(transport, portId, payload, txnId, ResourceOwner)` / `rx(...)` 重载。归因合成次序：参数 owner（权威）> 线程 MDC > 既有时间窗回填 > null。既有无 owner 重载原样保留（MDC 路径），未落地库（Phase 1 仅 serial/modbus）行为不变。

### 5.2 serial（integration-serial）——A 类样板

- `SerialIntegration.register(SerialInfo, ResourceOwner)` 重载：视图携带 owner 集合（借用带主下同一视图可挂多条 ADAPTER 设备条目，§4）；`SerialSourcePort.connectedSources` 记账富化；identity 串由 owner 派生（日志/引用计数键不变量保持）。
- **轮询 SDK 咽喉（Phase 1 零集成改动的关键）**：`SerialPolling` 起链处已调用 `DeviceMdcContext.scopeOf(host)`（SerialPolling.java:309）——在同一位置把 `host`（DeviceBase）的 `(coordinate, deviceId)` 与 source 视图关联登记（`associateOwner(view, owner)`）。凡走轮询 SDK 的集成（sailhero/saimosen/fronttech/thermofisher/teledyne 全部在列）**不改一行代码**即获得权威归属。
- 锁：`acquire/tryAcquire` 增加 owner 传入（视图已持 owner 时自动注入，调用方无感）；`currentKey` 状态机加 `lockAcquireOwner`，release 与幽灵锁收割（SerialSourcePort.java:389-409）路径清除。
- 捕获点：TX（SerialSourcePort.java:900，doSendWrite 路径）与 RX（:744，handleIncomingData）读当前持锁 owner 作为权威参数传给 CommTraceBuffer。两捕获点与锁状态同对象，无跨对象竞争窗口之外的新增复杂度。
- 反向索引：`SerialIntegration` 维护 deviceId → portName(+视图) 查询面（读 API，供未来消费方）。

### 5.3 modbus（integration-modbus）——A 类样板

- `ModbusIntegration.register(ModbusInfo, ResourceOwner)` 重载：`DeviceSpecificModbusSource` 携带 owner；共享 `ModbusSource.registeredIntegrations` Set → `Map<ownerKey, ResourceOwner>`；`closeModbus` 记账键随 owner 化。
- 锁：`DeviceSpecificModbusSource` 的 acquire/tryAcquire 全部委托共享源（DeviceSpecificModbusSource.java:52-96）——包装器自动注入自身 owner，**集成事务代码零改动**。共享源锁状态加 `lockAcquireOwner`（同 serial）。
- 捕获点：TX/RX（ModbusSource.java:189,193）读当前持锁 owner；modbus 事务恒在锁内（executeWithLambda/executePolling），权威值总可得。`PendingSend` 现有的 mdcContext 快照（:306-309,385）继续服务日志链路，设备归属不再依赖它。
- RTU 子注册带主注册：modbus 经 serial 底层注册的 `"modbus-"+port`（ModbusIntegration.java:172-179）按 §4 借用带主规则登记最终设备身份——每个设备注册时向串口视图使用者集合附加一条 `(DEVICE, 调用者coordinate, entryId, deviceId, ADAPTER)`，注销摘除、摘空拆视图；modbus register 把调用者 owner 原样转发给 serial（不再自造 "modbus-端口" 中间商身份）。同口 N 设备 = N 条 ADAPTER 条目；跨集成共线自然成立。
- 反向索引：`ModbusIntegration` 维护 deviceId → connectionIdentity 查询面。
- 同连接多 slave（原生形态：一共享源 + 每设备包装器逐请求注入 slaveId，DeviceSpecificModbusSource.java:100-152）下，账本按包装器登记每设备 owner，与锁的"当前住户"配合完成归因。

### 5.4 tcp（integration-tcp）

- **客户端（B 类）**：`TcpConnection.connect(host, port, ResourceOwner)` 重载，`ReconnectTcpClient` 自持 owner；四个 TCP_CLIENT 捕获点（ReconnectTcpClient.java:119,426,502,537）归因从 MDC 升级为 owner 权威。`TcpPolling` 咽喉已有 `scopeOf`（TcpPolling.java:121）——轮询路径零集成改动获得关联；轮询外手动连接经 owner 参数显式传入。
- **客户端进程清单**：现 connect 门面每次 new 一个 client、零登记零账本——进程内"有哪些 host:port 连接、属谁"完全无法回答。tcp 侧收口登记（弱引用 value，不干预消费方生命周期），只做清单查询，不做共享/计数（client 本就私有，勿增实体）。
- **服务端（C 类）**：独占注册已拒撞键（TcpIntegration.java:126-139），归属数据已有（`TcpServerImpl.integrationId`）只是未入账本——`TcpResourceKeys`（tcp 仓唯一静态登记簿，键无主，TcpResourceKeys.java:19，注释自证"此处只是记账侧"）owner 化为天然挂点；与 core 侧 ExecutionRequest 绑定的关系 Phase 2 落地前核实。改造成本全库最低。

### 5.5 httpserver（integration-httpserver）

- **服务端（C 类，现行最高风险点）**：serverPool 共享池无引用计数、`unregisterServer` 无条件 stop（HttpServerIntegration.java:139-149）——6 个现役消费方任一拔线殃及池鱼。改动：`registerServer` 记 owner 集合（serverPool 值富化为 `{server, owners}`）；`unregisterServer` 只摘调用方 owner。**末主才 stop 的计数化是语义变更，单独决策**（§9-5；现役消费方全部成对调用，行为对它们透明）。
- **客户端（B 类）**：`EasyHttpClient` 构造必填 RemovalHost（EasyHttpClient.java:114-127）——宿主绑定已是最强生命周期设计，owner 从 host 派生（DeviceBase → coordinate+deviceId；非设备宿主 → coordinate+null），**零新增构造参数**。HttpCommTrace 捕获（HttpCommTrace.java:62-94）归因 owner 优先、MDC 降第二层。
- EndpointLanes 实例内锁不跨实例（两个 client 打同一端点互不互斥）为既有语义，不动（§7）。

### 5.6 mqtt（integration-mqtt）

- **客户端句柄（B 类）**：`registerClient(clientId, ResourceOwner)` 重载；`MqttClientManager.clients` 值富化 owner（MqttClientManager.java:39）。现状 clientId 约定俗成弱归属 → 类型化；撞 clientId 抛 ISE 的独占语义原样。
- 嵌入式 Broker 为进程级基础设施，不属消费方资源，不入账本。
- mqtt 全链零 CommTrace 接入（CommTraceTransport 无 MQTT 枚举值）为既有观测缺口，与归属登记正交（§7、§9-7）。

## 6. 分期

机制（ResourceOwner 类型 + 归因优先级 + 各库 API 形态）**一次定稿覆盖 5 库**，避免二次 API 变更；落地按库分期，Phase 1 锚定 A 类样板（既有账本+锁最完整，改造成本最低、归因收益直接）。

| 阶段 | 范围 | 交付判据 |
|---|---|---|
| **Phase 1（本工单）** | core 类型 + CommTrace owner 入口 + **5 库 API 形态定稿** + serial/modbus 全落地（账本 owner 化 + 锁 owner + 捕获点权威归因 + 轮询 SDK 咽喉关联） | 走轮询 SDK 的集成不改码，serial/modbus 轮询 TX/RX 的 deviceId 权威化；`findByOwner(deviceId)` 双向查询面可用；旧 register 签名全兼容 |
| Phase 2 | tcp / httpserver / mqtt 三库落地（§5.4-5.6）+ 19 集成 register 传参 owner 化（机械，一行改调用；手动命令路径归属依赖此步） | 5 库资源全登记；手动命令 TX 有归属；进程级资源清单（tcp client、server 池、mqtt 句柄）可查 |
| Phase 3 | 消费方（ADM 回补 resolve、追踪 UI owner 展示、冲突诊断报错点名、资源运维清单） | 各消费方仓自行验收 |

## 7. 不解决的边界（如实）

1. **锁外字节无权威归属**：被动帧、迟到帧、不走锁的手动路径（如 teledyne sendLine 不持源锁的存量形态）——Phase 1 后这些路径的归属**仍是现状行为**（MDC/回填/null）。机制的收益是把多数流量权威化 + 把无归属流量显式暴露，不是消灭 null。
2. **多住户共享口的被动收帧**：人口账本只能缩圈（本口 owners 集合），定夺仍靠"当前住户"（锁）；无锁时序可考的帧，归属不优于现状启发式。
3. **C 类入站流量零 CommTrace 埋点**（tcp server 入站、http server 入站）与 **mqtt 全链零接入**（枚举无 MQTT 值）——既有观测缺口，与归属登记正交，本工单不做（是否另立工单见 §9-7）。
4. **既有生命周期语义原样**：EndpointLanes 跨实例不互斥、tcp client 首连失败后台永不放弃重试、mqtt 会话队列 QoS0 丢弃记账——归属登记不触碰。
5. 不建跨库统一注册中心、不迁移任何集成（兼容策略承担）。

## 8. 测试策略（风险锁点，不铺样板）

| 风险 | 测试要点 | 范式 |
|---|---|---|
| 锁 owner 生命周期 | acquire→release 清除；幽灵锁收割后 owner 不残留；同线程嵌套 fail-fast 路径 owner 不误挂 | 既有锁状态机测试扩展 |
| 归因合成次序 | 权威 > MDC > 回填 > null 四层优先级；无键/无锁/窗口过期各自如实 | 扩展 CommTraceRxAttributionTest（其 rxDirectMdcWinsOverBackfill 前插权威层） |
| 并发归因正确性 | 多口并发 TX/RX 归属不串台 | 沿用 concurrentMultiPortAppendEachPortAttributionCorrect（CountDownLatch 确定性同步） |
| 引用计数 owner 化 | 同口多 owner 注册/注销，最后注销才毁资源；LEGACY 与类型化 owner 混存 | serial/modbus 既有生命周期测试扩展 |
| RTU 带主注册 | 每设备一条 ADAPTER 条目挂串口视图；注销摘除、摘空拆视图；跨集成共线多家各一条 | modbus RTU 既有测试扩展 |
| C 类共享记账 | httpserver serverPool 多 owner 注册/注销、末主前不毁（若 §9-5 采纳计数化）；tcp server 键 owner 入账 | Phase 2 各库生命周期测试 |
| B 类弱账本 | tcp client 弱引用账本不阻止回收、清单查询如实 | Phase 2 新增 |

时间敏感断言一律测试缝时钟注入（沿用 CommTraceBuffer 测试的 AtomicLong clock 范式），禁真实等待。

## 9. 待决策点

1. ~~ResourceUsage 枚举粒度~~ **已裁决（2026-09-12）**：身份层级与使用方式两维度正交——层级用 `OwnerLevel{INTEGRATION, ENTRY, DEVICE, LEGACY}` 显式枚举标注（不靠字段有无猜，构造期校验层与字段一致），用法用 `Usage{DIRECT, ADAPTER}` 二值；POLLING/COMMAND/BACKFILL 不入账（注册时刻不可知/无现时消费者）。模型见 §4。
2. ~~deviceId=null 合法边界~~ **已裁决（2026-09-12）**：nullability 由 OwnerLevel 构造校验机定（INTEGRATION→entryId/deviceId 必 null；ENTRY→deviceId 必 null；DEVICE→双必非空；LEGACY→结构字段全 null + rawIdentity 必非空），不再单列。
3. **`ResourceOwner` 包归属**：`com.ecat.core.CommTrace`（归属的主消费方，最小暴露面）vs 新建 `com.ecat.core.Resource`（中性，为未来资源治理留位）。倾向前者，勿增实体。
4. **旧签名 LEGACY owner 的语义**：coordinate/deviceId 如实 null + 原字符串另存（rawIdentity），追踪展示为"未迁移注册方"。是否在日志定期提醒迁移（可观测推动 Phase 2）待定。
5. **httpserver serverPool 计数化语义变更**：`unregisterServer` 从无条件 stop → 摘自身 owner、末主才 stop。这是本设计唯一提议的行为变更（§3-5 例外项）。倾向采纳并入 Phase 2：归属账本天然要求计数，且 6 现役消费方全部成对调用、行为透明。
6. **tcp client 弱账本 vs 零账本**：倾向弱账本（WeakReference value）——进程清单"有哪些连接属谁"是"资源→使用者"要求在 B 类上的唯一落点；不干预生命周期、不做共享计数。
7. **被动侧 CommTrace 埋点 + MQTT 枚举**：是否另立工单补 tcp server/http server 入站埋点与 CommTraceTransport.MQTT。倾向另立（正交，本工单聚焦归属），本设计只保证 owner 数据就绪，埋点工单直接消费。

## 关联

- `CommTraceEvent` / `CommTraceBuffer` / `CommTraceTransport` / `CommTraceRxAttributionTest`（com.ecat.core.CommTrace）
- `DeviceMdcContext`（com.ecat.core.Utils.Mdc，工单 G 注入机制——本设计中 MDC 降级为归因第二优先级）
- serial：`SerialIntegration` / `SerialSource` / `SerialSourcePort` / `SerialPolling`
- modbus：`ModbusIntegration` / `ModbusSource` / `DeviceSpecificModbusSource` / `Sdk/ModbusPolling`
- tcp：`TcpIntegration` / `TcpResourceKeys` / `TcpServerImpl` / `sdk/TcpConnection` / `ReconnectTcpClient` / `TcpPolling`
- httpserver：`HttpServerIntegration` / `EasyHttpServer` / `EasyHttpClient` / `EndpointLanes` / `HttpCommTrace`
- mqtt：`MqttIntegration` / `MqttClientManager` / `MqttClientHandle`
