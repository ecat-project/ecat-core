# IO 资源归属登记与通讯追踪权威归因

> 状态：v0.7 定稿（2026-09-12；同日两度扩界裁定：serial/modbus 集成迁移与旧签名删除并入，再定 5 库全量（原 Phase 1+2 合并，§6）。实施期勘误：§4 DEVICE 层派生改按四层身份模型——entryId=`getEntry().getEntryId()`、deviceId=`getId()`，两值不同（2026-07-19 已拆"entryId 兼当 deviceId"折叠，原设计引了过时事实）。v0.7 §9-10 查询契约全库强制·精准语义：指定身份一对一查注册 Info（get* 只读族）+ 消费方一步得源 `registerForDevice(身份, owner, host)`（身份/生命周期两维度正交，与 host 收口一视同仁），不做旗下罗列，`ResourceQuery<I, S>` 泛型接口锁签名；D1' owner getter/MDC 来源升级与多设备同口事务锁判官落 §4。v0.6 §9-8 收官：四库 host 收口全线裁定 A。v0.5 捕获策略裁定：payload 500B / HTTP_SERVER 元数据+凭据红线 / 环默认 2_000，见 §5.1。v0.4 被动侧埋点并入（§9-7）。v0.3 归属模型重构（OwnerLevel/Usage 双枚举、借用带主、分层查询）+ httpserver 裁定：URI 级账本 + RemovalHost 登记即绑定宿主，见 §4/§5.5）

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

**实施期全仓普查修正（2026-09-12，54 仓矩阵）**：实际 7 形态，最多数是上表未列的**裸 `getId()`**（20 仓——无任何集成区分）；另有 `getSimpleName()+"-"+getId()`（tianhong/zhaorong）、`"mgd-coord:"+moduleId`（modbus-generic-device 协调器）、`"modbus-verify-"+UUID`（flow 验证）。上表保留作设计期快照。

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
// 归属 core；5 库已依赖 core，不引入新依赖方向。包归属已裁定 com.ecat.core.CommTrace（§9-3）。
// 身份层级与使用方式是两个正交维度：层级显式枚举（不靠字段有无猜），用法二值。
public enum OwnerLevel { INTEGRATION, ENTRY, DEVICE, LEGACY }
public enum Usage { DIRECT, ADAPTER }

public final class ResourceOwner {
    private final OwnerLevel level;    // 哪一层的使用者（显式标注）
    private final String coordinate;   // 哪个集成（域归属；LEGACY 兼容占位时为 null）
    private final String entryId;      // ENTRY/DEVICE 层必填，INTEGRATION 层为 null
    private final String deviceId;     // DEVICE 层必填，上两层为 null（勘误 2026-09-12 实施期：四层身份模型下 getId()=core
                                       // 铸造稳定 UUID≠entryId——派生定案 entryId=getEntry().getEntryId() 真实主键、
                                       // deviceId=getId()；网关子设备 N 台共网关 entry 折叠正确；查询契约按真实键命中）
    private final Usage usage;         // DIRECT 直接使用 / ADAPTER 经他库转手落到本资源
    private final String rawIdentity;  // 仅 LEGACY：旧签名自由字符串原样保留；其余层为 null
    private final RemovalHost host;    // 派生宿主活引用（of(host) 构造时留存）：仅展示元数据 getter 用——
                                       // 不入身份等值/ownerKey/构造校验；账本条目随宿主 onRemove 摘除，引用寿命天然有界；
                                       // 字段构造路径（带主转发/未来消费方显式身份）可 null，相应展示元数据如实缺省

    // mdcEntries()：展示上下文按 level 派发——同一对象既喂归因权威参数又喂 MDC 注入，
    // id/name 同刻同源永不撕裂；DEVICE 层 name 从 host 现取（改名实时反映，非注册期快照）
    // DEVICE→设备三键 / ENTRY→entry 键+coordinate / INTEGRATION→coordinate / LEGACY→rawIdentity 键
    // 键名沿用 MdcContext 既有键族；实施期补齐（2026-09-12 裁定）：entry.id / owner.rawIdentity
    // 两新键按实体.属性命名法入 MdcContext 键族真相源
}
// 构造期校验层与字段一致（严格模式 fail-fast，不猜）：
// INTEGRATION → entryId/deviceId 必 null；ENTRY → entryId 必非空、deviceId 必 null；
// DEVICE → entryId/deviceId 必非空；LEGACY → coordinate/entryId/deviceId 必 null、rawIdentity 必非空
// 实施期补充（2026-09-12 裁定）显式字段构造工厂族，与 of(host)/legacy/asAdapter 并列：
// integration(coordinate) / entry(coordinate, entryId) / device(coordinate, entryId, deviceId[, host])
// ——registerForDevice 契约的「身份=字段构造 owner（非宿主派生）」与 ENTRY 层唯一构造路径所需；
// 全部工厂过同一校验通道收口，杜绝层-字段错位组合
// 同日裁定：ownerKey/equals/hashCode 组分含 rawIdentity——LEGACY 层结构字段全 null，
// rawIdentity 是唯一区分度（多集成同口多条 LEGACY 条目为迁移过渡期常态，缺则账本撞键互踩）；
// 非 LEGACY 层 rawIdentity 恒 null 无影响；host 引用仍然除外
```

**借用带主（身份穿透）**：经他库转手落到资源上的注册，登记的是**最终使用者的完整身份**，ADAPTER 只标注"路径经过转手"，不把中间库登记为使用者。例：sailhero 设备经 modbus-RTU 用串口，serial 账本记 `(DEVICE, com.ecat:integration-sailhero, entryId, deviceId, ADAPTER)`。收益：① 追踪归因一跳到位——RTU 帧直接归属设备，无需再去 modbus 账本二次换算；② 资源账本见真人——"这个口谁在用"直接列设备清单。账目形态：同一条 RTU 串口上 N 个设备 = 串口视图使用者集合 N 条 ADAPTER 条目（摘一条少一条，摘空拆视图，引用计数天然对上）；跨集成共线（aogan/saimosen 同总线）集合里多家各一条，自然成立。机制代价：modbus 的 register 须把调用者 owner 原样转发给 serial（库间 API 语义，Phase 1 落实）。

两级归属落地载体：

| 层级 | 载体 | 登记时机 | 回答 |
|---|---|---|---|
| 常住人口 | 各库资源账本（serial `connectedSources` 富化；modbus `registeredIntegrations` Set→Map；tcp TcpResourceKeys/新弱账本；httpserver serverPool 值富化；mqtt clients 值富化） | `register()` / 构造 | 这个口/连接/句柄上注册了哪些使用方 |
| 当前住户 | A 类事务锁状态（serial：SerialSourcePort；modbus：ModbusSource——两者均已记 `lockAcquireTime/lockAcquireThread`，ModbusSource.java:512-513，顺位加 `lockAcquireOwner`） | `acquire()` / `tryAcquire()` | 当前在飞事务是谁的（release/幽灵锁收割时清除） |

锁纪律（同口至多一笔在飞）正是现行 RX 启发式的成立前提——把 owner 挂到锁上，A 类归因从"用时序猜"变为"直接读"，机制自洽。归因收益按形态分层：**A 类最大**（RX 归因权威化，替代时间窗回填）；B 类 TX/RX 归因 owner 优先于 MDC；C 类入站帧归因 = owner 直读（§9-7 并入埋点后被动侧流量可见且可归属）。

**分层查询（不同层不同函数，签名即层级契约；查询面留在各库账本读面，core 不建统一注册中心，§3-4）**：

```java
// ResourceRef：定位一笔资源的自描述标识（反向查询参数 + 账本自描述）
public final class ResourceRef {
    private final ResourceKind kind;  // SERIAL_PORT / MODBUS_CONNECTION / TCP_CLIENT / TCP_SERVER / HTTP_CLIENT / HTTP_SERVER / MQTT_CLIENT
    private final String key;         // 各库账本键原样：portName / ip:port / clientId …
}

// 5 库账本统一查询契约（core 只放接口与数据类型，实现留各库——接口≠注册中心，§3-4）。
// I = 各库资源信息类型（SerialInfo / ModbusInfo / tcp 连接配置…），S = 各库 source 视图类型（SerialSource / ModbusSource / …）。
// 命名规范：只读族 get 前缀（对齐 DeviceRegistry.getByUniqueId 纯读先例）；动作方法 register
// 动词——有副作用勿用 get 假只读承诺（register/unregister 是 5 库既有习语）；勿用 acquire（撞事务锁术语）。
public interface ResourceQuery<I, S> {
    // 指定身份 → 该身份自己占用的 IO 资源信息（一对一精准只读；未注册如实 null；
    // 同一身份名下多笔属异常形态，明确异常不猜——严格模式）
    I getIntegrationInfo(String coordinate);                             // 集成本体自持资源（如 env-push 推送连接）；不含旗下设备
    I getEntryInfo(String coordinate, String entryId);
    I getDeviceInfo(String coordinate, String entryId, String deviceId); // 设备的通道信息（含 ADAPTER 条目——RTU 设备在 serial 账本亦可得其串口参数）

    // 消费方一步到位：为设备注册（解析 + 追加登记 + 得 source 视图）。与既有
    // register(Info, host) 同形同返回，只是解析键从 Info 换成设备身份。身份与生命周期
    // 两个维度显式正交——身份=字段构造 owner（非派生），生命周期=宿主锚点 host
    // （onRemove 摘账 + 终态守卫，与 host 收口一视同仁）；未注册如实 null，不凭 Info
    // 另开资源（杜绝查注间隙设备已摘导致的半死挂靠）
    S registerForDevice(String coordinate, String entryId, String deviceId, ResourceOwner owner, RemovalHost host);

    // 资源 → 使用者：按层折叠（运维诊断/冲突核查读面）
    List<ResourceOwner> getDeviceOwners(ResourceRef ref);      // 全量（ADAPTER 逐设备一条）
    List<ResourceOwner> getEntryOwners(ResourceRef ref);       // device 折叠到 entry
    List<ResourceOwner> getIntegrationOwners(ResourceRef ref); // 折叠到集成去重
}
```

例：sailhero 两设备共用一条 RTU 口——`getDeviceOwners(口)` 返回两条 `(DEVICE,…,ADAPTER)`；`getIntegrationOwners(口)` 折叠为 `[sailhero]`，"这口是谁家的"一眼答。

**查询契约全库强制（2026-09-12 裁定，§9-10；同日修正为精准语义）**：每个 IO registry 必须提供**指定身份的一对一精准查询**——按 integration / entryId / deviceId 精确匹配账本条目，返回该身份自己占用的 IO 资源信息（注册时的 Info：串口参数、连接地址、句柄标识），未注册如实 null；**不做"某集成名下全部设备"的罗列查询**（消费方 ADM 是指定设备的精准复用，无旗下罗列需求）。core 以泛型接口 `ResourceQuery<I, S>` 锁签名（I=各库 Info 类型、S=各库 source 视图类型），5 库 registry 全部 implements（Phase 1 serial/modbus，Phase 2 其余三库），漏实现编译期暴露；反向三折叠为运维诊断读面一并锁定。**命名规范**：只读族 get 前缀（`getIntegrationInfo/getEntryInfo/getDeviceInfo` + `getDeviceOwners/getEntryOwners/getIntegrationOwners`，对齐 `DeviceRegistry.getByUniqueId` 纯读先例）；动作方法 `registerForDevice`——register/unregister 是 5 库既有习语，有副作用勿用 get 假只读承诺、勿用 acquire 撞事务锁术语。实现侧零新增留存成本：账本本就持有注册 Info（SerialSourcePort.serialInfo:79、ModbusSource.modbusInfo:102），且 RECONFIGURE 原位替换（SerialSourcePort:503-517）——查询返回的是**活配置**，正是 ADM 应查账本而非用自己配置副本的根据。ADM 用法一步到位（2026-09-12 裁定）：`registerForDevice(coordinate, entryId, deviceId, owner, host)`——库内原子完成解析+追加登记+返回 source 视图（serial `SerialSource` / modbus `ModbusSource`，SerialIntegration.java:86、ModbusIntegration.java:134 形态），与既有 `register(Info, host)` 同形同返回、只是解析键从 Info 换成设备身份。**身份与生命周期两维度显式正交（一视同仁）**：身份=字段构造 owner（非宿主派生），生命周期=宿主锚点 host——`host.onRemove(摘账)` 构造期绑定 + 宿主已终态拒绝并就地回收（EasyHttpClient 同型守卫），与 host 收口同权；ADM 任务对象实现 RemovalHost（单方法接口）即得自动摘账，任务收尾自触发回调（任务宿主不在引擎生命周期树内、非 core LIFO 遍历，但库侧绑定/守卫语义与设备宿主完全一致）。同键挂上同一共享资源（常住人口 +1，"复用设备 IO 通道"语义所在，非新开资源），回补事务走视图既有事务 API，与设备轮询共享同一事务锁（FIFO 串行 + `lockAcquireOwner` 帧归属）。`get*Info` 三函数为只读诊断面（展示/核对活配置，不产生登记）；裸 `register(Info, ResourceOwner)`（无宿主）保留给库间带主转发——生命周期由转发方自管，是该形态唯一正当的无宿主场景。

**跨库聚合（§9-9 裁定）**：消费方要"deviceId → 全部资源"时**逐库拼查**（依次调各库 `getDeviceInfo`），不建聚合读端点、不建统一注册中心；当前无消费方，暂不实现。

**MDC 来源升级（owner 单一真相源，2026-09-12 裁定）**：MDC 注入源从「起链时手里的宿主对象」（`scopeOf(host)`）改为「资源上登记的 owner」——注入端读 `owner.mdcEntries()` 按 level 派发，CommTraceBuffer 读 MDC 的机制原样不动（**升级发生在注入端，不在消费端**）。收益：① 设备维度 id/name 同源同刻，无跨来源拼装；② 集成层链路（env-push、com-hj212 等推送/上报）今天 `scopeOf` 非设备宿主 no-op，升级后获得 coordinate 上下文；③ 日志 enrichment 与帧归因同一真相源。**多设备同口的注入归属**：从本链设备自己登记的那条 owner 取（咽喉 `associateOwner(view, owner)` 关联的配对），不从视图 owner 集合取（N 条歧义）。

**多设备同口的帧归属判官 = 事务锁状态机（当前住户，2026-09-12 代码实证）**：TX 在持锁临界区内捕获（doSendWrite 写路径唯一出口）、RX 在**端口共享读线程**捕获（handleIncomingData 读路径唯一入口，向全部 connectedSources 扇出，永无任务 MDC——任何线程上下文方案在此失效）——两捕获点同读 `lockAcquireOwner`；「同口至多一笔在飞事务」的锁纪律使 RX=持锁者的应答**结构性成立**；强拆/迟到/unsolicited 字节锁已清 → 既有 30s 回填 → null 如实。锁告警已带 currentKey（"lock currently held by: …"），identity 由 owner 派生后共享组件日志自动点名设备。

**登记即绑定宿主（RemovalHost 收口）**：注册 API 以宿主为参数，一个 host 在 create 时完成三件事——owner 从 host 类型派生（DeviceBase → DEVICE 层，IntegrationBase → INTEGRATION 层，不靠调用者传字符串）、销毁动作构造期注册进宿主生命周期（`host.onRemove(dispose)`——作者不接触生命周期概念，忘了绑定在签名层面不可能，RemovalHost.java:34-45）、宿主停止由 core LIFO 移除机制统一收割。仓内先例：EasyHttpClient（构造期 `host.onRemove(this::shutdown)`，宿主已终态则就地回收再上抛，EasyHttpClient.java:112-121）。httpserver 服务端（§5.5）、serial/modbus register（§5.2/§5.3）、tcp client `.on(host)`（§5.4）、mqtt registerClient（§5.6）已按此定型——**5 库注册 API 全线 host 收口（§9-8）**。

## 5. 各侧改动

### 5.1 core（ecat-core）

- 新增 `ResourceOwner` + `OwnerLevel` / `Usage` 枚举（§4）。
- `CommTraceBuffer` 增加 owner 权威入口：`tx(transport, portId, payload, txnId, ResourceOwner)` / `rx(...)` 重载。归因合成次序：参数 owner（权威）> 线程 MDC > 既有时间窗回填 > null。既有无 owner 重载原样保留（MDC 路径），未迁移调用方（LEGACY）行为不变。
- `CommTraceTransport` 扩展被动侧三值：`TCP_SERVER` / `HTTP_SERVER` / `MQTT`（沿用"按捕获组件划分"的既有原则，§9-7 裁定并入本工单）。
- **MDC 来源升级（§4）**：`DeviceMdcContext` 增加 `scopeOf(ResourceOwner)`（读 `owner.mdcEntries()`）；`scopeOf(RemovalHost)` 保留并内部转 owner 路径——DeviceBase→设备三键、IntegrationBase→coordinate（现状 no-op 的升级收益）、其余宿主（测试假宿主）维持 no-op 容忍（MDC 路径不适用账本级严格失败）。buffer 的 MDC 读取与回填机制不动。
- **捕获策略（2026-09-12 裁定）**：
  - payload 截断上限 256B → **500B**：Modbus 全帧 256/260B、HJ212 典型 300–600B 覆盖，"装下一个完整协议帧"原则；截断保留 originalLength 现状不变。
  - HTTP_SERVER 只记元数据不记 body：方法 + 路径 + 状态 + 耗时 + 访问者 IP + 选定头（白名单制，如 Content-Type/User-Agent）；**Authorization / Cookie / Set-Cookie 凭据类头逐字捕获禁止**（凭据不入可查询环）。HTTP_CLIENT 是设备协议流量，payload 照截——协议排障价值在帧内容。
  - 流式端点（SSE/长流）只记连接建立/断开，不逐帧入环（防高频流刷环挤占其他传输帧）。
  - 环默认容量 50_000 → **2_000**：全传输共享环，按 100–200 帧/分搅动保 ~10 分钟事故现场，~1.6MB（800B/条）；50k×800B≈40MB 给调试环过重、500 条仅 2.5 分钟窗口过薄。`ecat.commtrace.capacity` 可调现状保留。

### 5.2 serial（integration-serial）——A 类样板

- **`register(SerialInfo, RemovalHost host)` host 收口（§9-8 裁定 A，2026-09-12）**：register 调用点全在设备类（sailhero MyDeviceBase.java:122，thermofisher/saimosen/fronttech 同形态），设备传 `this`——库在注册时一体完成：owner 从 host 派生（`ownerOf(host)`，DeviceBase→DEVICE 层）、`host.onRemove(摘账)` 构造期绑定销毁、core LIFO 统一收割（§4 登记即绑定宿主）。设备 stop 手工 closePort（sailhero :136）随之删除；LIFO 顺序天然正确——register 绑定早于 SerialPolling 轮询收尾绑定（sailhero :127 注释自证后者存在），后绑定先执行 → 先停轮询后关口。`register(SerialInfo, ResourceOwner)` 重载保留给库间带主转发（modbus→serial 的 ADAPTER 条目无宿主可传）与测试假宿主；消费方直取源走 `registerForDevice(身份, owner, host)`（§4 ResourceQuery）——解析+登记+宿主绑定一体（终态守卫同型），实现复用 register 机制。视图携带 owner 集合（借用带主下同一视图可挂多条 ADAPTER 设备条目，§4）；`SerialSourcePort.connectedSources` 记账富化；identity 串由 owner 派生（日志/引用计数键不变量保持）。
- **轮询 SDK 咽喉（Phase 1 过渡机制，迁移完删除）**：`SerialPolling` 起链处现调 `DeviceMdcContext.scopeOf(host)`（SerialPolling.java:309）——Phase 1 在同位置以 `of(host)` 构造 owner（设备宿主 → DEVICE 层），一处完成两件事：MDC 注入源升级 `scopeOf(owner)`（读 mdcEntries，§5.1；输出与今天等价，真相源换为 owner）+ `associateOwner(view, owner)` 视图关联。过渡期未迁移集成（LEGACY register）不改一行代码即获得权威归属；集成迁移完成后（已并入本工单，§6）注册即有权威 owner，咽喉的 associateOwner 成冗余随 LEGACY 签名同批退场（§9-4）；`scopeOf(owner)` 注入长期保留（日志本职）。
- 锁：`acquire/tryAcquire` 增加 owner 传入（视图已持 owner 时自动注入，调用方无感）；`currentKey` 状态机加 `lockAcquireOwner`，release 与幽灵锁收割（SerialSourcePort.java:389-409）路径清除。
- 捕获点：TX（SerialSourcePort.java:900，doSendWrite 写路径唯一出口，持锁临界区内）与 RX（:744，handleIncomingData 读路径唯一入口，端口共享读线程永无任务 MDC，:757 向全部 connectedSources 扇出）——同读 `lockAcquireOwner` 作权威参数（多设备同口判官=事务锁，§4）；强拆/迟到字节落既有 30s 回填 → null 如实。两捕获点与锁状态同对象，无跨对象竞争窗口之外的新增复杂度。
- 反向索引：`SerialIntegration` 维护 deviceId → portName(+视图) 查询面（读 API，供未来消费方）。

### 5.3 modbus（integration-modbus）——A 类样板

- **`register(ModbusInfo, RemovalHost host)` host 收口（§9-8 裁定 A）**：同 serial 三合一——thermofisher AirModbusTcpDeviceBase.java:51 现手拼 `类名+"-"+getId()` 即 `ownerOf(host)` 派生内容的类型化替代，:63 手工 closeModbus 随之删除；`DeviceSpecificModbusSource` 携带 owner；共享 `ModbusSource.registeredIntegrations` Set → `Map<ownerKey, ResourceOwner>`；`closeModbus` 记账键随 owner 化。`register(ModbusInfo, ResourceOwner)` 重载保留给带主转发与测试假宿主。
- 锁：`DeviceSpecificModbusSource` 的 acquire/tryAcquire 全部委托共享源（DeviceSpecificModbusSource.java:52-96）——包装器自动注入自身 owner，**集成事务代码零改动**。共享源锁状态加 `lockAcquireOwner`（同 serial）。
- 捕获点：TX/RX（ModbusSource.java:189,193）读当前持锁 owner；modbus 事务恒在锁内（executeWithLambda/executePolling），权威值总可得。`PendingSend` 现有的 mdcContext 快照（:306-309,385）继续服务日志链路，设备归属不再依赖它。
- RTU 子注册带主注册：modbus 经 serial 底层注册的 `"modbus-"+port`（ModbusIntegration.java:172-179）按 §4 借用带主规则登记最终设备身份——每个设备注册时向串口视图使用者集合附加一条 `(DEVICE, 调用者coordinate, entryId, deviceId, ADAPTER)`，注销摘除、摘空拆视图；modbus register 把调用者 owner 原样转发给 serial（不再自造 "modbus-端口" 中间商身份）。同口 N 设备 = N 条 ADAPTER 条目；跨集成共线自然成立。
- 反向索引：`ModbusIntegration` 维护 deviceId → connectionIdentity 查询面。
- 同连接多 slave（原生形态：一共享源 + 每设备包装器逐请求注入 slaveId，DeviceSpecificModbusSource.java:100-152）下，账本按包装器登记每设备 owner，与锁的"当前住户"配合完成归因。

### 5.4 tcp（integration-tcp）

- **客户端（B 类）——`TcpConnection.on(RemovalHost)` host 收口（§9-8 裁定 A，2026-09-12）**：`TcpConnection.on(this).connect(host, port, config)`，与 EasyHttpClient `.on(host)` 同式（同为主动出站连接，API 语言统一；`connect(String host, int port)` 的 host=远端主机，RemovalHost 前置消解参数撞名）。`on()` 记宿主，connect 时三合一：owner 派生（设备类调用点 thermofisher AirDeviceBase.java:71 / wmade / hikvision → DEVICE 层；集成构造的协议客户端 com-hj212-*、env-push-demo/neimenggu → INTEGRATION 层）+ `host.onRemove(释放)` 销毁绑定（thermofisher `release()` 手工 `connection.release()` :100-108 随之删除；同时消灭"宿主已死、后台重连线程成僵尸"——活着时永不放弃重试的既有语义不动，§7-4）+ 弱账本登记。LIFO 顺序同 serial 天然正确：连接绑定早于 TcpPolling 轮询绑定 → 先停轮询后关连接；`release()` 级联取消轮询与 TcpPolling 自身 onRemove 双跑须幂等（实现时核一处）。`ReconnectTcpClient` 自持 owner；四个 TCP_CLIENT 捕获点（ReconnectTcpClient.java:119,426,502,537）归因从 MDC 升级为 owner 权威。`TcpPolling` 咽喉已有 `scopeOf`（TcpPolling.java:121）——过渡期未迁移集成零改动获得关联，迁移完随 LEGACY 签名同批退场（同 §5.2）。
- **客户端进程清单**：现 connect 门面每次 new 一个 client、零登记零账本——进程内"有哪些 host:port 连接、属谁"完全无法回答。`.on(host)` 收口时一体登记弱账本 `Map<key, WeakReference<client>>`：弱引用不钉扎 GC（账本不强行留住对象），销毁经宿主 onRemove 主动触发——两者正交；只做清单查询，不做共享/计数（client 本就私有，勿增实体）。
- **第二入口 `TcpIntegration.createTcpClient(host, port)` 同样收口（2026-09-12 复核补全）**：tcp 库有**两个公共入口**——该入口的两消费方 env-push-demo（DemoPushTask.java:177）/ env-push-neimenggu（NeimengguPushTask.java:270）的推送长连接经此创建，同式 `tcpIntegration.on(host).createTcpClient(host, port)`（宿主=集成 → INTEGRATION 层），否则两个推送长连接游离账本外。env-push 各自的裸 `java.net.Socket`（DemoPushTask:142 / NeimengguPushTask:242）仅是连通性测试探针——即开即关不持有通讯资源，不入账本属正确划分。
- **服务端（C 类）**：独占注册已拒撞键（TcpIntegration.java:126-139），归属数据已有（`TcpServerImpl.integrationId`）只是未入账本——`TcpResourceKeys`（tcp 仓唯一静态登记簿，键无主，TcpResourceKeys.java:19，注释自证"此处只是记账侧"）owner 化为天然挂点；与 core 侧 ExecutionRequest 绑定的关系落地时核实。改造成本全库最低。
- **被动侧埋点（§9-7 并入）**：TcpServerConnection 入站/出站捕获，transport=`TCP_SERVER`；归因 = server owner（C 类账本直读，无 TX/MDC 上下文，走"owner 直读"即终）；portId = 监听口 + remote 对端。

### 5.5 httpserver（integration-httpserver）

- **服务端（C 类，现行最高风险点）——登记即绑定宿主**：serverPool 共享池无引用计数、`unregisterServer` 无条件 stop（HttpServerIntegration.java:139-149）。新签名 `registerServer(String ip, int port, RemovalHost host)`，host 在 create 时一体完成：① owner 从 host 派生（DeviceBase → DEVICE 层；IntegrationBase → INTEGRATION 层，:147）；② `host.onRemove(() -> dispose(serverKey, owner))` 构造期注册销毁（不依赖调用者自觉成对调用）；③ 宿主停止由 core LIFO 统一收割（§4 登记即绑定宿主）。宿主已终态则拒绝并就地回收（EasyHttpClient 同型守卫）。
- **两层账本（URI 级为主体）**：路由是真正使用单位（handlerMap url→method→handler，现状无归属信息）——`(url, method) → {handler, owner}` 按 URI 计数；server 层 owner 集合定生命周期（不挂瞬时路由数：创建瞬间零路由 + agent-bridge 动态挂摘先例 McpServer.java:82-106）。registerServer(host) 返回绑 owner 的注册视图，controller 仍调 registerUrl 同名方法、owner 内部盖章。`dispose(owner)` = 摘光该 owner 名下全部路由 + 出集合；集合空 → stop + 出池——共享口上先走者路由变 404，不僵尸、不殃及他人。
- **宿主生死矩阵（显式拆卸的适用面）**：设备宿主下 reconfigure = 先 stop 再 start new（RECONFIGURE 流程保 entryId、uniqueId 不可变，ConfigFlowService.java:680-687；reconfigureStep 集成自定义）——旧租约随旧设备 onRemove 自动摘、新租约随新设备生（含换端口），**无需显式 API**。集成宿主下 entry 级变化宿主不死、onRemove 不触发 → 显式 `unregisterServer(ip, port, host)`（与 onRemove 绑定幂等互斥：先显式摘过，宿主死时回调空转）；集成 disable/remove 由 onRemove 兜底。**多租约可选形态**：共享 server 上每台设备各挂一份租约到设备宿主（"server 为这批设备服务"的推送接收类语义）——entry 级全自动、摘空即停；代价是唯一在用设备 reconfigure 时存在租约归零窗口，端口未变也会 stop/start 抖一次。两种形态按使用方语义选，机制都支持；ecat-core-api/media-api/env-calibration-composer-api 常设服务口属集成宿主型。
- **多宿主共享一口**：各家各自 registerServer(host) 各得一份租约+路由，各宿主死各摘各的，最后一个宿主走才 stop。
- 旧签名 `registerServer(ip, port)` 兼容保留 → LEGACY owner（无宿主即无自动销毁，维持手工注销现状）。
- **客户端（B 类）**：EasyHttpClient 已是宿主绑定先例（`on(host)` + 构造期 `onRemove(this::shutdown)`），本工单只补 owner 派生（host → ResourceOwner 同上映射）供 HttpCommTrace 权威归因（HttpCommTrace.java:62-94），API 零新增参数。
- **被动侧埋点（§9-7 并入）**：入站请求/响应捕获，transport=`HTTP_SERVER`；归因 = URI 账本 route owner 一跳（先账本后埋点的顺序收益在此兑现）。**流式端点（SSE/长流）只记连接建立/断开，不逐帧入环**——高频流会刷满有界环、挤掉其他传输的帧，追踪整体可用性优先。
- EndpointLanes 实例内锁不跨实例为既有语义，不动（§7）。

### 5.6 mqtt（integration-mqtt）

- **客户端句柄（B 类）——`registerClient(String clientId, RemovalHost host)` host 收口（§9-8 裁定 A，2026-09-12）**：与 serial/modbus 同式直传（clientId 无撞名，不需 `.on()` 前置）。全仓唯一消费方 vision-analysis 在集成类调用（VisionAnalysisIntegration.java:1439，宿主=集成 → INTEGRATION 层 owner），`:389` 手工 unregisterClient 随 onRemove 绑定删除；clientId 仍单传（broker 会话标识，协议层身份与 ResourceOwner 正交）。`MqttClientManager.clients` 值富化 owner（MqttClientManager.java:39）；撞 clientId 抛 ISE 的独占语义原样。`registerClient(clientId, ResourceOwner)` 重载保留给测试假宿主。
- 嵌入式 Broker 为进程级基础设施，不属消费方资源，不入账本。
- **全链埋点（§9-7 并入）**：transport=`MQTT`——broker 侧消息（topic + payload 截断）与 client 句柄收发双面捕获；归因 = handle owner（B 类账本直读）。

## 6. 分期

机制（ResourceOwner 类型 + 归因优先级 + 各库 API 形态）**一次定稿覆盖 5 库**，避免二次 API 变更；原按库分期锚定 A 类样板先行，2026-09-12 用户两度扩界后 **5 库全量落地、全部消费集成迁移与旧签名删除并入本工单一次完成**（实施顺序仍 serial/modbus 先行定型，三库复用同套机制与作业流）。

| 阶段 | 范围 | 交付判据 |
|---|---|---|
| **本工单（原 Phase 1+2，2026-09-12 两度扩界全量）** | core 类型 + CommTrace owner 入口 + 捕获策略 + 被动侧三枚举 + **5 库全落地**（账本 owner 化 + 锁 owner + 捕获点权威归因 + 被动侧埋点 + 凭据红线）+ **全部 5 库消费集成迁移 host 收口 + 全部旧无宿主签名物理删除**（warn-once 与咽喉 associateOwner 同批退场；`scopeOf(owner)` MDC 注入保留） | serial/modbus 轮询 TX/RX 的 deviceId 权威化；手动命令 TX 有归属；被动侧流量（tcp server / http server 入站、mqtt 链路）进追踪视图；5 库 `ResourceQuery` 精准查询（`get*Info`）与反向折叠（`get*Owners`）可用；旧签名全仓零引用并删除，终态 @Deprecated 零残留 |
| Phase 3 | 消费方（ADM 回补 resolve、追踪 UI owner 展示、冲突诊断报错点名、资源运维清单） | 各消费方仓自行验收 |

原 Phase 2（tcp/httpserver/mqtt 三库落地与其集成迁移）已并入本工单（2026-09-12 二次扩界裁定）；Phase 3 消费方不变。

## 7. 不解决的边界（如实）

1. **锁外字节无权威归属**：被动帧、迟到帧、不走锁的手动路径（如 teledyne sendLine 不持源锁的存量形态）——Phase 1 后这些路径的归属**仍是现状行为**（MDC/回填/null）。机制的收益是把多数流量权威化 + 把无归属流量显式暴露，不是消灭 null。
2. **多住户共享口的被动收帧**：人口账本只能缩圈（本口 owners 集合），定夺仍靠"当前住户"（锁）；无锁时序可考的帧，归属不优于现状启发式。
3. **入站帧归因只有 owner 一层**：被动侧（tcp/http server 入站、mqtt）无 TX、无线程 MDC，归因走"owner 直读"即终——remote 对端是谁只能如实呈现（portId 携带对端地址），不反推。这是如实语义不是缺口。
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
| C 类共享记账与宿主绑定 | 宿主停止 LIFO 自动摘名下路由/租约；宿主已终态注册被拒且不留半活资源；共享口先走者路由变 404、末主才 stop；显式拆卸与 onRemove 绑定幂等互斥；tcp server 键 owner 入账 | httpserver Phase 2 生命周期测试；RemovalHost 单方法接口可用 lambda 假宿主断言 |
| B 类弱账本 | tcp client 弱引用账本不阻止回收、清单查询如实 | Phase 2 新增 |
| 被动侧归因 | 入站帧 owner 直读（http = URI 账本一跳 / tcp server = server owner / mqtt = handle owner）；SSE 等流式端点只记连接起止不逐帧；高频流不挤占其他传输帧（环内共存验证） | Phase 2 各库捕获点测试 + CommTraceBuffer 环层测试 |
| owner 展示元数据与 MDC 派发 | mdcEntries 按 level 派发矩阵；DEVICE 层 name 从 host 现取（改名后新帧新名）；host 引用寿命随账本条目 onRemove 摘除释放（记账法锁泄漏） | W1 单测（假宿主）+ Phase 1 集成验证 |

时间敏感断言一律测试缝时钟注入（沿用 CommTraceBuffer 测试的 AtomicLong clock 范式），禁真实等待。

## 9. 决策记录（10 点全部已裁决，2026-09-12）

1. ~~ResourceUsage 枚举粒度~~ **已裁决（2026-09-12）**：身份层级与使用方式两维度正交——层级用 `OwnerLevel{INTEGRATION, ENTRY, DEVICE, LEGACY}` 显式枚举标注（不靠字段有无猜，构造期校验层与字段一致），用法用 `Usage{DIRECT, ADAPTER}` 二值；POLLING/COMMAND/BACKFILL 不入账（注册时刻不可知/无现时消费者）。模型见 §4。
2. ~~deviceId=null 合法边界~~ **已裁决（2026-09-12）**：nullability 由 OwnerLevel 构造校验机定（INTEGRATION→entryId/deviceId 必 null；ENTRY→deviceId 必 null；DEVICE→双必非空；LEGACY→结构字段全 null + rawIdentity 必非空），不再单列。
3. ~~ResourceOwner 包归属~~ **已裁决（2026-09-12）**：`com.ecat.core.CommTrace`——归属数据的第一消费方是通讯追踪归因，与 CommTraceEvent/CommTraceBuffer 同包，最小暴露面，勿增实体。
4. ~~LEGACY 注册方迁移提醒~~ **已裁决（2026-09-12）**：旧签名标 `@Deprecated`（javadoc 指向新签名）+ 注册时 warn 一次（带原串，boot 后 grep 即知剩余未迁移数，迁移完日志消失即验收信号）；不建周期汇总机制。迁移完成后删除旧签名——终态无 @Deprecated 残留（DoD 第 4 条）。
5. ~~httpserver serverPool 计数化语义变更~~ **已裁决（2026-09-12）**：采纳并两度升级——① URI 级 owner 账本为主体（`(url,method)→{handler,owner}`，路由是真正使用单位）+ server 级 owner 集合定生命周期（末主才 stop）；② 登记 API 收口为 RemovalHost（身份派生 + onRemove 销毁绑定 + core LIFO 统一收割一体，不依赖调用者自觉成对调用）。见 §5.5。
6. ~~tcp client 弱账本 vs 零账本~~ **已裁决（2026-09-12）**：弱账本——connect 收口登记 `Map<key, WeakReference<client>>`，弱引用不干预消费方生命周期、不做共享计数；落地面是 §4 分层查询契约（`get*Info` 对 tcp client 可用）+ 进程连接清单。登记动作形态已随 §9-8-A 定：`.on(host)` 收口时一体登记（弱引用防钉扎 GC 与 onRemove 主动销毁正交）。
7. ~~被动侧埋点是否另立工单~~ **已裁决（2026-09-12）**：并入本工单，与三库账本同批落地——调用者（owner 账本）就绪、同属通讯追踪消费、Phase 2 本就开同样三个仓（同批摩擦最小）；URI 账本直接喂 http 入站归因，先账本后埋点顺序自然成立。枚举扩展 TCP_SERVER/HTTP_SERVER/MQTT；流式端点（SSE/长流）只记连接建立/断开不逐帧入环（防高频流刷环挤占其他传输帧）；捕获参数同批裁定（payload 上限 500B、HTTP_SERVER 只记元数据且凭据类头禁入环、环默认容量 50_000→2_000，见 §5.1 捕获策略）。各库捕获点见 §5.4/§5.5/§5.6。
8. ~~其余 IO 库 host 收口推广~~ **已裁决（2026-09-12，A 全线）**：四库全部采 host 三合一——serial/modbus `register(SerialInfo/ModbusInfo, RemovalHost host)`（设备传 this）；tcp `TcpConnection.on(RemovalHost)` fluent 前置（消解 connect 的 host=远端主机撞名，与 EasyHttpClient 同式）+ 弱账本一体登记；mqtt `registerClient(clientId, RemovalHost host)`（全仓唯一消费方 vision-analysis，集成宿主）。共同效果：owner 从宿主派生不靠手拼串、`host.onRemove` 构造期绑销毁（手工 closePort / closeModbus / connection.release / unregisterClient 全删）、core LIFO 统一收割；僵尸 tcp 重连线程消灭（活着时永不放弃语义不动，§7-4）；owner 重载保留给库间带主转发（modbus→serial ADAPTER）与测试假宿主；SDK 咽喉 associateOwner 降级为 Phase 1 过渡机制，迁移完成随 LEGACY 签名同批删除（§9-4）。§5.2/§5.3/§5.4/§5.6 已按此改写。**至此 8 个决策点全部裁决，设计定稿。**
9. ~~跨库聚合查询形态~~ **已裁决（2026-09-12）**：消费方要"deviceId → 全部资源"时**逐库拼查**（依次调各库 `getDeviceInfo`），不建聚合读端点、不建统一注册中心；当前无消费方，暂不实现。见 §4。
10. ~~查询接口形态~~ **已裁决（2026-09-12，同日按 ADM 实际消费形态三度修正）**：每个 IO registry 必须提供按 integration/entryId/deviceId 的**指定身份一对一精准查询**——返回该身份自己占用的 IO 资源信息（注册 Info），未注册如实 null；**不提供"按集成罗列旗下全部设备资源"的列表查询**（ADM 是指定设备的精准复用，无罗列需求）。core 泛型接口锁签名 + `ResourceRef`/`ResourceKind`，5 库 registry 全部 implements，漏实现编译期暴露；实现留各库账本读面（接口是契约不是注册中心，§3-4 不破）。反向三折叠（ref→owners）保留为运维诊断读面。**再修正三处**：① 契约升级 `ResourceQuery<I, S>`，新增 `registerForDevice(身份三元组, owner, host)` 一步"解析+追加登记+得 source"（库内原子，杜绝查注间隙设备已摘导致凭旧 Info 另开资源的半死挂靠），`get*Info` 为只读诊断面；② **身份与生命周期两维度显式正交（一视同仁）**——owner 显式构造 + host 宿主锚点绑生命周期（`onRemove(摘账)` + 终态守卫，与 host 收口同权），裸 `register(Info, owner)` 无宿主形态仅存于库间带主转发（转发方自管生命周期）；③ **命名规范**——只读族 get 前缀（`get*Info`/`get*Owners`，对齐 `DeviceRegistry.getByUniqueId` 纯读先例），动作方法 `registerForDevice`（register 习语；有副作用勿用 get 假只读承诺；勿用 acquire 撞事务锁术语）。见 §4。

## 关联

- `CommTraceEvent` / `CommTraceBuffer` / `CommTraceTransport` / `CommTraceRxAttributionTest`（com.ecat.core.CommTrace）
- `DeviceMdcContext`（com.ecat.core.Utils.Mdc，工单 G 注入机制——本设计中 MDC 降级为归因第二优先级）
- serial：`SerialIntegration` / `SerialSource` / `SerialSourcePort` / `SerialPolling`
- modbus：`ModbusIntegration` / `ModbusSource` / `DeviceSpecificModbusSource` / `Sdk/ModbusPolling`
- tcp：`TcpIntegration` / `TcpResourceKeys` / `TcpServerImpl` / `sdk/TcpConnection` / `ReconnectTcpClient` / `TcpPolling`
- httpserver：`HttpServerIntegration` / `EasyHttpServer` / `EasyHttpClient` / `EndpointLanes` / `HttpCommTrace`
- mqtt：`MqttIntegration` / `MqttClientManager` / `MqttClientHandle`
