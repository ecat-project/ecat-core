# ECAT 设备标识体系（entryId / deviceId / uniqueId / attrId）

> 面向新开发者的**概念入门**。读完能回答："我要唯一认出一台设备 / 关联一条历史数据 / 发一个设备事件，该用哪个 id？"
> 本轮重设计的完整决策链（D1–D9、修改清单）见 `docs/2026-07-17-device-identity-design.md`，本文不重复，只讲清楚"是什么、关系、怎么用"。

---

## 1. 为什么需要四层标识

旧模型里 `entryId`（配置记录的 UUID）身兼数职：既是配置记录主键、又被当成设备主键、又塞进事件载荷。这带来两个硬伤：

1. **网关场景崩**：一个网关（如 MQTT broker、HJ212 连接）背后挂 N 台子设备，但只有 **1 个 ConfigEntry（1 个 entryId）**。若 entryId 当设备主键，N 台设备挤一个 id，注册表、事件、历史数据全无法区分。
2. **重配/重建丢历史**：用户删了设备再加回来，ConfigEntry 重建 → entryId 变 → 以 entryId 为键的历史数据/绑定全部断裂。

借鉴 Home Assistant 的四层标识，把"配置记录""设备""硬件""属性"拆开，各司其职。ECAT 的差异：ecat 一个 entry 可派生 **1:N** 个 device（HA 是 1:N 但 entry 与 device 分离更早），且 `uniqueId` 在 ecat 是**硬件锚点、coordinate 内唯一**（HA 是集成内唯一）。

---

## 2. 四层标识总览

| 标识 | 核心定位 | 生成主体 | 可变性 | 典型格式 | 核心用途 |
|---|---|---|---|---|---|
| **entryId** | 配置记录（ConfigEntry）主键 | ConfigEntryRegistry 创建时铸造 UUID | 删除重建则变 | UUID | 配置条目 CRUD、ConfigFlow 追踪、entry 生命周期事件 |
| **deviceId** | **设备（Device）主键** | `DeviceBase` 构造铸造临时 UUID；`DeviceBase.load()` 调 `DeviceRegistry.resolveStableId` 用 yml 持久化值覆盖（早于 init/state 物化）；`DeviceRegistry` yml 持久化 | **永久不可变**（跨重启/重配/禁启用稳定） | UUID | 设备注册表 key、事件载荷、历史数据 FK、数据/逻辑绑定 |
| **uniqueId** | 硬件锚点（物理设备一对一） | 集成从硬件读（序列号/MAC 等） | 硬件决定，不可改 | 自由（厂家+sn 等） | **coordinate 内**重复发现去重、物理匹配 |
| **attrId** | 属性标识 | 设备定义（每个测点一个） | 设备类型固定 | 字符串（如 `so2_concentration`） | 属性级数据读写、绑定 |

> 口诀：**entryId 管配置、deviceId 管设备、uniqueId 管硬件、attrId 管测点。**

---

## 3. 层级关系图

```
                         ┌─────────────────────────────────────────┐
                         │  ConfigEntry  (entryId = UUID)          │
                         │  用户/ConfigFlow 创建的"配置记录"        │
                         │  例：一个 MQTT broker 连接配置           │
                         └──────────────────┬──────────────────────┘
                                            │ 1 : N
                                            │ （普通设备 1:1；网关 1:N）
                    ┌───────────────────────┼───────────────────────┐
                    ▼                       ▼                       ▼
          ┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
          │  Device A       │     │  Device B       │     │  Device C       │
          │  deviceId=UUID  │     │  deviceId=UUID  │     │  deviceId=UUID  │
          │  uniqueId=sn-A  │     │  uniqueId=sn-B  │     │  uniqueId=sn-C  │
          │  entry=同上 ↑   │     │  entry=同上 ↑   │     │  entry=同上 ↑   │
          │  (back-ref)     │     │  (back-ref)     │     │  (back-ref)     │
          └────────┬────────┘     └────────┬────────┘     └────────┬────────┘
                   │ 1 : M                 │                       │
                   ▼                       ▼                       ▼
            attrId: so2, t, humidity, ...  ...                     ...
```

**关键关系**：
- `Device.getEntry().getEntryId()` 回指所属 ConfigEntry（**back-ref**，1:N 反向枚举的基础）。
- 一个 entryId 下可有 N 个 deviceId（网关）；普通集成是 1:1。
- 一个 deviceId 下有 M 个 attrId。
- `uniqueId` 仅在**同一 coordinate（集成坐标）内唯一**：`(coordinate, uniqueId)` 才全局定位一台硬件。

### 1:N 网关示例（demo-iot-gateway）

```
ConfigEntry "网关 gw-001" (entryId=E1)
   │ createDeviceFromEntry 发现 3 个子传感器
   ├── DemoSensorDevice  deviceId=D1  uniqueId="sensor-01"   (getEntry()=E1)
   ├── DemoSensorDevice  deviceId=D2  uniqueId="sensor-02"   (getEntry()=E1)
   └── DemoSensorDevice  deviceId=D3  uniqueId="sensor-03"   (getEntry()=E1)
```
三台子设备共享 entryId（E1）但各有独立 deviceId（D1/D2/D3）——`findDevicesByEntryId(E1)` 能一次枚举全部，支撑 enable/disable 1:N 级联。

---

## 4. 逐层详解

### entryId —— 配置记录的身份证
- 谁：`ConfigEntryRegistry.createEntry` 铸造；ConfigFlow 提交后落盘到 `.ecat-data/core/config_entries/`。
- 变：删除 entry 再建 → 新 entryId。
- 用：配置管理（REST `/core-api/config-flow/entries/{entryId}`）、ConfigFlow 追踪、`CONFIG_ENTRY_LIFECYCLE` 事件载荷。
- **不要**拿 entryId 当设备主键（旧模型错点，已废弃）。

### deviceId —— 设备的永久主键（本轮新引入的核心）
- 谁：`DeviceBase` 构造时铸造临时 UUID；`DeviceBase.load(core)` 立即调 `DeviceRegistry.resolveStableId`——命中 yml 持久化记录则 `setId` 覆盖为稳定值，**早于 init/updateValue/buildState 物化任何 AttrState**。`getOrCreate` 内部即 `resolveStableId + commit` 两步。
- 为什么 resolve 必须在 load：init 的 `updateValue/buildState` 会把 `device.getId()` 烘进不可变 `AttrState.deviceId`。若 resolve（setId）晚于 buildState（如旧实现放在 getOrCreate 内），id 突变后已物化的 midState 仍带临时 id → ready-gate flush 发布带临时 id 的事件 → 消费侧 `getDeviceByID(临时id)=null` → valueType/type 解析为 null（详见 §10）。
- 未命中（首次创建）：构造 UUID 永不再被 setId 改写，它本身就是稳定 id——所以"命中覆盖"与"未命中保留"两分支都自洽。
- 不变：删除设备再加（同硬件 uniqueId）→ 命中持久化记录 → **同一个 deviceId 复原**；重配（reconfigure 经 `replace`）、禁用/启用同理保活。
- 用：
  - 设备注册表 key（`DeviceRegistry`、`UnifiedDeviceStore`、集成本地 `devices` map 全以 deviceId 为键）
  - **事件载荷**：`DEVICE_LIFECYCLE`、`DeviceDataChangedEvent` 都带 deviceId
  - 历史数据外键（如 hj212 `his_data.stationId`）
  - 跨集成设备绑定（media `MediaDeviceRegistry`、airstation/airdevice 的 `mappings.device_id`）
- 取：`device.getId()`。

### uniqueId —— 硬件锚点（coordinate 内唯一）
- 谁：集成从硬件读（序列号、MAC、host+port 等），写入 `DeviceBase`。
- **语义重定义（重要）**：uniqueId 只在**一个 coordinate 内**保证唯一，不再全局唯一。要定位一台硬件必须带 coordinate：`getDeviceByUniqueId(coordinate, uniqueId)`。
- 用：**重复发现去重**——同 `(coordinate, uniqueId)` 的设备重发现时，`getOrCreate` 复用已有 deviceId，不重复入网。
- 取：`device.getUniqueId()`。
- **不要**脱离 coordinate 用 uniqueId 查设备（旧的单参 `getDeviceByUniqueId(String)` 已删除）。

#### ConfigFlow 约束（设计原则，2026-07-24 全项目统一）
- **字段决定，禁随机**：uniqueId 必须由用户填写的稳定字段决定，**禁止 `UUID.randomUUID()` 兜底**。随机 uniqueId 会致 reconfigure 漂移（重算出不同值 → 身份丢失）、同设备重复入网。
  - 字段选择：SN 类（A/B 集成）用 `sn`；网络设备（摄像头/门禁/分析仪，host 接入）用 `host+port`；serial-tcp 桥用 `serial_port`（连接即身份）；modbus-generic 用编号 `code`。**name 可能含中文/特殊符号，不作 uniqueId**。
- **uniqueId 决定字段在 form 与 device load schema 都必填**（fail-fast）：
  - ConfigFlow 表单（`createDeviceBasicSchema`）+ 设备加载校验 schema（`XxxDeviceConfigSchema`，`createDeviceFromEntry` 在 core 启动时 `validate(entry.getData())` 校验存量 entry）都设 `required=true`。
  - 缺身份字段的畸形 entry：创建时被表单拦、加载时被 schema 拒（明确报错），**不静默放行**。
  - 前提：改 load schema 必填前，确认所有存量 entry 已有该字段（否则像 sn-less legacy entry 一样 load fail，需先重建）。
- **尽早排重**：`context.setEntryUniqueId(generateUniqueId(), isReconfigure)` 在**提交 uniqueId 决定字段的那个 step** 就调（不等 final_confirm），重名冲突早暴露、避免用户白填后续步。`FlowContext.setEntryUniqueId` 会查活跃 flow + 已存 entry 的 uniqueId 冲突。
- **reconfigure 复用旧 uniqueId**：身份不可变。reconfigure 时 `generateUniqueId` 返回被重配 entry 的原 uniqueId（查 `reconfigureEntryId`），不重算——防漂移。配合 reconfigure 时身份字段只读（如 SN readonly）。
- **required 校验已收紧**：`AbstractConfigItem.validate` 的 required 不仅查 `null`，也查**空白串**（`value==null || (required && isBlankString(value))`）——堵住 API/脚本 POST 空 required 字段绕过前端 HTML5 的口子。

### attrId —— 属性（测点）标识
- 谁：设备类定义（如 SO2 分析仪有 `so2`、`status` 等 attr）。
- 用：属性级数据读写、绑定。与 deviceId 组合 `(deviceId, attrId)` 定位一个具体测点。

---

## 5. LogicDevice 与物理设备同机制（无特例）

逻辑设备（LogicDevice，如 airdevice/airstation 的聚合设备）**与物理设备走完全相同的 deviceId 机制**——早期设计（D5，2026-07-17）曾让 `LogicDevice.getId()` 覆盖返回 `getUniqueId()` 作特例，该豁免在 2026-07-18 DeviceRegistry 统一重构中**已撤销**：
- LogicDevice **不** override `getId()`/`setId()`（仅 override `init()`、`replaceAttrWithPlaceholder`），构造铸造 UUID，`load()` 经 `resolveStableId` 覆盖为持久化稳定值——与物理设备一字不差。
- 与物理设备的唯一差异：LogicDevice **不发 `DEVICE_LIFECYCLE` 事件**（其生命周期随物理设备/配置派生，由 `LogicdeviceIntegration` 内部编排 createEntry→init→register→finalizeLogicDevice）。

> 对使用方而言：`getId()` 对物理设备与 LogicDevice 都返回该设备的稳定 deviceId（铸造 UUID，可能已被 resolveStableId 覆盖为持久化值）。消费方无需区分设备种类。

---

## 6. 使用场景指南（该用哪个 id）

| 场景 | 用哪个 | 为什么 |
|---|---|---|
| 设备注册表查询/枚举 | deviceId（`getDeviceByID`） | 主键，最快 |
| 按硬件认一台物理设备（去重/匹配） | `(coordinate, uniqueId)`（`getDeviceByUniqueId`） | 硬件锚点，coordinate 内唯一 |
| 发设备生命周期事件 | deviceId（`DEVICE_LIFECYCLE`） | 设备主键，跨重配稳定 |
| 发属性数据变更事件 | deviceId + attrId（`DeviceDataChangedEvent`） | 定位到具体测点 |
| 历史数据外键 | deviceId | 跨设备重建不丢历史 |
| 跨集成绑定（media/airstation/airdevice） | deviceId | 被绑设备的主键 |
| ConfigFlow 配置管理 | entryId | 配置记录主键 |
| 1:N 网关枚举子设备 | entryId（`findDevicesByEntryId`） | 子设备 back-ref 到网关 entry |
| 重复发现抑制 | `(coordinate, uniqueId)` | R5 去重键 |

---

## 7. 关键 API 速查

```java
// DeviceBase
device.getId();          // deviceId（铸造UUID，load 时可能已被 resolveStableId 覆盖为持久化值；物理/逻辑设备同）
device.getUniqueId();    // uniqueId（硬件锚点）
device.getEntry();       // 所属 ConfigEntry（.getEntryId() = entryId，back-ref）

// 设备查询（IDeviceQuery / UnifiedDeviceStore）
store.getDeviceByID(deviceId);                       // 按设备主键
store.getDeviceByUniqueId(coordinate, uniqueId);     // 按硬件锚点（域化，2 参）
store.findDevicesByEntryId(entryId);                 // 1:N 网关枚举

// 设备注册/移除（DeviceRegistry，Phase4 正交化后的 API）
registry.getOrCreate(device, Action.CREATE);         // resolveStableId（铸/复原稳定 id）+ commit（注册+发 DEVICE_LIFECYCLE）
registry.replace(oldDevice, newDevice);              // reconfigure：解析 new→old 稳定 id + 单 RECONFIGURE 事件
registry.disable(device);                            // 软移除：保 yml+matchIndex+state，发 REMOVE（供 enable 恢复）
registry.remove(device);                             // 逻辑删：保 matchIndex+state，发 REMOVE（供同 uniqueId 重建复原 id）
registry.purge(device);                              // 物理删：彻底清除记录与 state（一般不用）

// 事件
BusTopic.DEVICE_LIFECYCLE  // topic = "device.lifecycle"
DeviceLifecycleEvent{ deviceId, coordinate, entryId, Action{CREATE,RECONFIGURE,REMOVE} }
```

---

## 8. 常见误区（避坑）

1. **把 entryId 当 deviceId 用** —— 旧代码最常见错点。entryId 是配置记录 id，不是设备主键；1:N 下 N 设备共用一个 entryId。
2. **脱离 coordinate 用 uniqueId 查设备** —— uniqueId 只在 coordinate 内唯一。单参 `getDeviceByUniqueId(String)` / `getByUniqueId(String)` 已删除，必须带 coordinate。
3. **单测里 mock DeviceRegistry** —— `createEntry`/`removeEntry`/`disableEntry` 的 1:N 级联依赖 `DeviceRegistry.getOrCreate`/`findDevicesByEntryId` 真实行为，mock 会使级联失效（设备删不掉）。测试用真实 `new DeviceRegistry()`。
4. **DeviceBase getId() 在测试里断言等于 entryId** —— 新模型 getId() 返回铸造的 deviceId（UUID），≠ entryId。entry-backed 设备断言 `getEntry().getEntryId()`；Map-ctor 设备断言 `getId()` 非空。
5. **网关子设备 disable 不级联** —— 子设备经 `findDevicesByEntryId(gatewayEntryId)` 枚举，disable 网关 entry 会软移除全部子设备（保 deviceId+state，enable 复原）。

---

## 9. 实施状态与验证（2026-08-01：稳定 id 前置于 state 物化）

### 9.1 修复的根因（id 突变 + 陈旧 midState）

`realdata.type=null` 撞 DB NOT NULL 的真因**不是**"逻辑设备 registry 域不可解析"（旧 bug-record 的错误结论，已订正），而是 **device id 突变 + 陈旧 midState**：

1. 构造：`this.id = UUID.randomUUID()`（临时）。
2. init：`updateValue → buildState` 把临时 id 烘进不可变 `midState.deviceId`。
3. 注册：`getOrCreate` 内 `setId(稳定id)` 改写 `device.id`，但 **midState 不重建**（不可变，已烘死临时 id）。
4. `markReady` flush：发布带**临时 id** 的陈旧 midState。
5. 消费侧（env-data-handle）：`registry.getDeviceByID(临时id)=null` → valueType=null → `realdata.type=null`。

### 9.2 修复（resolve-in-load，方案 A）

把 `getOrCreate` 拆为 `resolveStableId`（仅 matchIndex→setId，不 commit）+ `commit`；`DeviceBase.load(core)` 在 init 之前调 `resolveStableId`（core 为 null 的未初始化测试、或 registry 为 null 的 mock-core 测试均跳过——生产 EcatCore 的 registry 是 final 字段永不为 null）。id 稳定**先于** buildState 物化，从根上消除 id 突变。reconfigure 经 `replace` 自动复用旧设备稳定 id，同治。完整决策链见聚合根 `docs/2026-08-01-device-id-stable-before-state-design.md`。

### 9.3 验证状态表

| 验证项 | 结果 |
|---|---|
| TDD 红测试 `DeviceIdStableBeforeStateTest` | RED 精确失败（预测的临时 id mismatch）→ GREEN |
| ecat-core 全量单测 | **1326 tests, 0 failures, 0 errors** |
| 运行时残留诊断标记（`[诊断调试]`/`[ADM-DELAY-DIAG]`/`MISMATCH`/`type_null_diag`） | 全 0（已彻底清除临时诊断） |
| `null value in column "type" violates not-null` 错误 | **0**（修前约 129/60 万次发布） |
| realdata 入库成功 | 持续增长，无 NOT NULL 失败 |
| core 启动 cwd | workspace 根（避 `core 读错 .ecat-data` 陷阱） |

### 9.4 端到端逻辑链（两个分支都走一遍）

设构造铸造的临时 UUID 记作 `tmp`，matchIndex 命中的持久化 id 记作 `S`。

**分支 1：matchIndex 命中（重启恢复 / reconfigure 重建）——bug 原发场景，修后正确**

| 步骤 | id 字段 | midState.deviceId | registry key |
|---|---|---|---|
| 构造 | `tmp` | — | — |
| load→resolveStableId（命中→setId(S)） | `S` | — | — |
| init/buildState | `S` | `S` ✓ | — |
| getOrCreate（幂等命中→setId(S) 同值）→ commit | `S` | `S` | `S` |
| markReady→flush→publish | — | newState.deviceId=`S` | `S` |

消费侧 `getDeviceByID(S)` ✓ 命中。

**分支 2：matchIndex 未命中（首次创建全新设备）——本就不踩 bug，修后仍正确**

| 步骤 | id 字段 | midState.deviceId | registry key |
|---|---|---|---|
| 构造 | `tmp` | — | — |
| load→resolveStableId（未命中→保留 tmp） | `tmp` | — | — |
| init/buildState | `tmp` | `tmp` ✓ | — |
| getOrCreate（幂等未命中→保留 tmp）→ commit | `tmp` | `tmp` | `tmp`（此 tmp 写入 device yml，成为该设备持久化稳定 id） |
| markReady→flush→publish | — | newState.deviceId=`tmp` | `tmp` |

消费侧 `getDeviceByID(tmp)` ✓ 命中。关键：这个 `tmp` 自构造起永不再变，所以它就是稳定 id——"随机"不等于"不稳定"，"会被改写"才是 bug。

---

## 10. 参考
- 完整设计决策：`docs/2026-07-17-device-identity-design.md`（D1–D9、§5 生命周期、§11 state 迁移、§12 生命周期事件）
- 落地方案集：`docs/device-identity-impl/`（00-core + 13 受影响集成 + demo-iot-gateway 试点）
- 1:N 网关试点代码：`ecat-integrations/demo-iot-gateway/`（`DemoIotGatewayIntegration` + `DemoSensorDevice`）
