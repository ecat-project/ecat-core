# 设备注册表查询 API 使用文档

**包**：`com.ecat.core.Device`（逻辑设备表 `com.ecat.core.LogicDevice` 实现同一查询契约）

设备注册表提供三套按不同 key 查询设备的能力。本文说明各查询方法的用法、入参语义、返回契约，以及何时用统一门面 `UnifiedDeviceStore`、何时用具体注册表。

---

## 一、三种查询 key——务必分清

设备对象有三个互不相同的标识，对应三套查询。**填错 key 是最常见的查询失败原因**：

| key | 来源方法 | 含义 | 稳定性 | 示例 | 对应查询方法 |
|---|---|---|---|---|---|
| **entryId** | `device.getId()` / `entry.getEntryId()` | 系统生成 UUID，注册表内部 map 的 key | 稳定（reconfigure 保留） | `b3f1a2c4-...` | `getDeviceByID(entryId)` |
| **uniqueId** | `device.getUniqueId()` / `entry.getUniqueId()` | 业务标识，由集成生成 | **reconfigure 可变**（如改 SN） | `hikvision_DS-2CD123` | `getDeviceByUniqueId(uniqueId)` |
| **coordinate** | `device.getCoordinate()` / `entry.getCoordinate()` | 集成坐标 `groupId:artifactId` | 稳定（reconfigure 保留） | `com.ecat:integration-hikvision` | `getDevicesByCoordinate(coordinate)` |

> **关键坑**：`getDeviceByID` 内部按 **entryId** 建 key。用 `uniqueId` 调 `getDeviceByID` **必然返回 null**。按业务标识查必须用 `getDeviceByUniqueId`。

---

## 二、接口结构（读写分离，ISP）

```
IDeviceQuery  (只读查询契约)
    getDeviceByID / getAllDevices
    getDeviceByUniqueId / getDevicesByCoordinate

IDeviceRegistry extends IDeviceQuery   (再加写操作 register / unregister)
    └ DeviceRegistry          (物理设备表，com.ecat.core.Device)
    └ LogicDeviceRegistry     (逻辑设备表，com.ecat.core.LogicDevice)

UnifiedDeviceStore implements IDeviceQuery   (只读聚合门面)
```

- `IDeviceQuery` 是所有查询的公共契约：具体注册表与统一门面都实现它，消费方可面向它编程。
- `UnifiedDeviceStore` **只实现只读 `IDeviceQuery`，不实现可写 `IDeviceRegistry`**——它是只读门面，没有 register/unregister 语义。
- `UnifiedDeviceStore.addRegistry(IDeviceRegistry)` 是该门面自有的装配方法（挂载子注册表），无共性，**不在任何接口里**。

---

## 三、查询方法签名

### `IDeviceQuery`（只读，所有查询入口）

| 签名 | 返回 | 说明 |
|---|---|---|
| `getDeviceByID(String entryId)` | `DeviceBase` / `null` | 按系统 entryId 查 |
| `getDeviceByUniqueId(String uniqueId)` | `DeviceBase` / `null` | 按业务 uniqueId 查 |
| `getDevicesByCoordinate(String coordinate)` | `List<DeviceBase>` | 按集成 coordinate 查（返回副本，无匹配为空列表） |
| `getAllDevices()` | `List<DeviceBase>` | 全量（返回副本） |

### `IDeviceRegistry`（继承上面 4 个，再加写操作）

| 签名 | 说明 |
|---|---|
| `register(String deviceID, DeviceBase device)` | deviceID 在注册表内唯一 |
| `unregister(String deviceID)` | — |

---

## 四、行为契约（严格模式，统一适用）

| 情形 | `getDeviceByUniqueId` | `getDevicesByCoordinate` |
|---|---|---|
| 命中 | 返回设备 | 返回包含匹配设备的**副本 List** |
| 无匹配 | 返回 **null** | 返回**空列表**（非 null） |
| 入参 null / 空串 | 抛 `IllegalArgumentException` | 抛 `IllegalArgumentException` |

- 无匹配不是异常，是正常查询结果（设备未注册 / id 不存在）→ 返回 null / 空。
- 入参为 null 或空串属于调用方契约违规 → 抛异常，明确报错。

---

## 五、UnifiedDeviceStore vs 具体 registry——何时用哪个

| 场景 | 用谁 |
|---|---|
| 跨物理+逻辑两表统一查（默认） | **`UnifiedDeviceStore`**（`core.getUnifiedDeviceStore()`） |
| 仅查物理设备 | `DeviceRegistry`（`core.getDeviceRegistry()`） |
| 仅查逻辑设备 | `LogicDeviceRegistry`（`core.getLogicDeviceRegistry()`） |
| 只读消费、不想误触写操作 | 依赖 `IDeviceQuery` 类型 |

`UnifiedDeviceStore` 的查询委托其挂载的各 registry：
- `getDeviceByUniqueId` / `getDeviceByID`：按 registry 添加顺序，**首个命中返回**。
- `getDevicesByCoordinate` / `getAllDevices`：**合并**各 registry 结果。

---

## 六、使用示例

```java
EcatCore core = ...;
UnifiedDeviceStore store = core.getUnifiedDeviceStore();

// 1) 按业务标识查（如厂家_sn）——注意不要用 getDeviceByID
DeviceBase dev = store.getDeviceByUniqueId("hikvision_DS-2CD123");
if (dev == null) {
    // 无此 uniqueId 的设备（非异常）
}

// 2) 拿某集成下的全部设备（物理+逻辑都算）
List<DeviceBase> hikDevices = store.getDevicesByCoordinate("com.ecat:integration-hikvision");

// 3) 按系统 entryId 查（已知 UUID 时）
DeviceBase byEntry = store.getDeviceByID("b3f1a2c4-...");

// 4) 只想查物理设备范围
DeviceBase phy = core.getDeviceRegistry().getDeviceByUniqueId("hikvision_DS-2CD123");

// 5) 面向只读契约编程——可传入具体 registry 或统一门面
void consume(IDeviceQuery query) {
    DeviceBase d = query.getDeviceByUniqueId("...");
}
consume(core.getUnifiedDeviceStore());
consume(core.getDeviceRegistry());
```

---

## 七、实现要点（维护者）

- **扫描实现**：两具体 registry 各自遍历 `registry.values()` 按 `getUniqueId()`/`getCoordinate()` 匹配。O(n)，n=设备数（实际几十~几百）；`uniqueId` 可在 reconfigure 时变化，扫描天然始终正确，免维护额外索引。
- **并发性**：`DeviceRegistry` 用 `HashMap`，对其 `values()` 迭代的弱一致性与既有 `getAllDevices()` 相同；`LogicDeviceRegistry` 用 `ConcurrentHashMap`。
- **REST 层 wiring 不在本 API 内**：`DeviceController` 当前 `{deviceId}` 等价于 entryId，如需前端按 uniqueId 路由是后续独立任务。
