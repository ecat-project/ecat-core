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
| **deviceId** | **设备（Device）主键** | core 在设备首次注册时铸造 UUID，`DeviceRegistry` yml 持久化 | **永久不可变**（跨重启/重配/禁启用稳定） | UUID | 设备注册表 key、事件载荷、历史数据 FK、数据/逻辑绑定 |
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
- 谁：`DeviceBase` 构造时铸造 UUID；`DeviceRegistry.getOrCreate` 用 yml 持久化的值覆盖构造值，保证跨重启稳定。
- 不变：删除设备再加（同硬件 uniqueId）→ `getOrCreate` 命中持久化记录 → **同一个 deviceId 复原**；重配（reconfigure）、禁用/启用同理保活。
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

## 5. LogicDevice 豁免（特例）

逻辑设备（LogicDevice，如 airdevice/airstation 的聚合设备）**不走 deviceId 铸造**：
- `LogicDevice.getId()` 覆盖返回 `getUniqueId()`（确定性，不铸 UUID）。
- 不发 `DEVICE_LIFECYCLE` 事件（其生命周期随物理设备/配置）。
- 原因：LogicDevice 是确定性派生设备，用 uniqueId 作 id 足够稳定，且避免与物理 deviceId 体系混淆。

> 对使用方而言：物理设备用 `getId()`=deviceId；LogicDevice 用 `getId()`=uniqueId。**消费方一般不需要区分**——getId() 返回的就是该设备的稳定主键。

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
device.getId();          // deviceId（物理=铸造UUID；LogicDevice=uniqueId）
device.getUniqueId();    // uniqueId（硬件锚点）
device.getEntry();       // 所属 ConfigEntry（.getEntryId() = entryId，back-ref）

// 设备查询（IDeviceQuery / UnifiedDeviceStore）
store.getDeviceByID(deviceId);                       // 按设备主键
store.getDeviceByUniqueId(coordinate, uniqueId);     // 按硬件锚点（域化，2 参）
store.findDevicesByEntryId(entryId);                 // 1:N 网关枚举

// 设备注册（DeviceRegistry）
registry.getOrCreate(device, Action.CREATE);         // 铸/复原稳定 deviceId + 发 DEVICE_LIFECYCLE
registry.unregister(device, deleteRecord);           // deleteRecord=false 软移除（保记录，供 enable 恢复）

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

## 9. 参考
- 完整设计决策：`docs/2026-07-17-device-identity-design.md`（D1–D9、§5 生命周期、§11 state 迁移、§12 生命周期事件）
- 落地方案集：`docs/device-identity-impl/`（00-core + 13 受影响集成 + demo-iot-gateway 试点）
- 1:N 网关试点代码：`ecat-integrations/demo-iot-gateway/`（`DemoIotGatewayIntegration` + `DemoSensorDevice`）
