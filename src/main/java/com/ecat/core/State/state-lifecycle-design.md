# State 模块设计：state 与 attr 的关系及调用链

本文档是 `com.ecat.core.State` 包的关键关系设计文档，说清属性（attr）与状态（state）的分离、
三槽状态模型、更新/提交/发布的完整调用链，以及最容易踩坑的易混淆点。读代码前先读此文档。

> 本文档随源码版本（.md 放在 src/main/java 下，仅源码树可见，不打入 jar）。代码注释是单点说明，
> 本文档是整体关系与设计动机的汇总。

---

## 1. 核心概念：attr 与 state 为何分离

- **attr（`AttributeBase<T>`）**：属性的可变字段载体，是业务值的**真相源**。内部持有可变字段
  `value`（业务工程值）、`status`、`updateTime`、`lastChanged`、单位、显示精度等。设备轮询/用户操作
  通过 `updateValue`/`setStatus` 改这些字段。
- **state（`AttrState<T>`）**：不可变的状态快照。某个时刻对 attr 字段的**一次性自洽拷贝**，对外只读。

**为何要分离**（三个独立诉求，都不能靠直接读 attr 字段满足）：
1. **撕裂读**：分别读 `getValue()/getStatus()/getUpdateTime()` 三个字段，期间若发生并发更新，会读到
   「值来自本次更新、状态来自上次」的撕裂组合。不可变 `AttrState` 一次拷贝、单次 volatile 读，字段绝对自洽。
2. **发布**：总线事件 `DeviceDataChangedEvent` 需要携带 old/new 两个不可变快照，让订阅者拿到绝对自洽的变化。
3. **持久化**：落盘的必须是某个时刻自洽的字段组合，不能是分散读的撕裂态。

因此 `getValue()/getStatus()/getUpdateTime()` 已降为 `protected`，**外部统一经 `getState()` 读不可变快照**。
（`getUpdateTime()` 当前仍由 Lombok `@Getter` 暴露为 public，属历史遗留，外部新代码应改用 `getState().getLastUpdated()`。）

---

## 2. 三槽状态模型

attr 内部用三个不可变快照槽协同管理状态生命周期（字段均为 `private volatile AttrState<T>`）：

| 槽 | 语义 | 何时变化 |
|---|---|---|
| `midState`（在途/不稳定态） | `updateValue`/`setStatus` 的变更都重建这里，反映**尚未提交**的最新快照。 | 每次 `updateValue`/`setStatus`（device 已附着时）；`publicState` 提交后置 null |
| `lastState`（已提交态） | 上次 `publicState` 提交的稳定快照。持久化数据源、下次发布事件的 old。 | 仅 `publicState` 移位时 ← midState |
| `previousState`（上一态） | `lastState` 的前驱，**永远指向真正的上一个 state**（即发布事件的 old）。 | 仅 `publicState` 移位时 ← lastState |

### `getState()` 返回规则

```
getState() = midState != null ? midState : lastState
```

即「读取此刻最新可见态」：有在途变更返回在途 `midState`，否则返回已提交 `lastState`。
**这是刻意设计**——同周期内 `updateValue` 后立即 `getState()` 能读到刚写入的值（计算属性求值、测试预热断言依赖此特性）。

---

## 3. 调用链

### 3.1 流水线阶段（设备一个轮询周期内）

```
设备 readData → parse → 对各属性 updateValue(value) / updateValue(value,status) / setStatus(status)
                      ↓ 每次都重建
                   midState（在途，累积本周期所有变更；lastState/previousState 不动）
                      ↓
                  publicAttrsState()（DeviceBase 遍历所有属性）
```

字段状态（恢复场景：上周期 MALFUNCTION，本周期 NORMAL）：

```
初始:        midState=null,  lastState=S0(v0,MALFUNCTION), previousState=S-1
updateValue(v1):  midState=M1(v1,MALFUNCTION-stale)   ← 只写 mid
setStatus(NORMAL): midState=M2(v1,NORMAL)              ← 继续写 mid
getState() = midState = M2   ← 同周期可读
```

### 3.2 提交阶段（`publicState()`，commit 点）

```
publicState():
  if isValueUpdated && midState != null:
     持久化: saveState（此时 getState()=midState，已 coherent）   ← 独立 try，发布失败也持久化
     发布: BusEvent<DeviceDataChangedEvent>(old=lastState, new=midState)   ← 失败则 return false（可重试）
     移位: previousState←lastState, lastState←midState, midState=null   ← 仅发布成功后
     isValueUpdated=false
```

提交后：`previousState=S0, lastState=M2(v1,NORMAL), midState=null`。

**关键不变量**：发布事件 old=`lastState`（上次提交=消费者上次所见），new=`midState`（本次在途）。
链永远连续，不存在「幽灵中间态」污染 old。

### 3.3 异常路径（设备故障，本周期无有效值）

```
catch: forEach(attr -> attr.setStatus(MALFUNCTION))
  → midState = (value, MALFUNCTION)          ← 无条件建（不依赖 lastState 是否已存在）
publicAttrsState → publicState: 发布(old=lastState, new=midState=MALFUNCTION); 持久化; 移位
→ getState() 返回 MALFUNCTION（可见）+ 总线发 MALFUNCTION 事件（可观测）
```

这就是「设备故障对外暴露 MALFUNCTION」的完整通路：`setStatus` 无条件建 `midState` + 设备的
`publicAttrsState()` 把它提交上总线。**前提：设备的轮询路径必须调 `publicAttrsState()`**（见 §5.4）。

---

## 4. API 选择指南

| API | 推荐度 | 说明 |
|---|---|---|
| `updateValue(value, status)` | ⭐ 推荐 | 原子设值+状态，`midState` 仅重建一次，无瞬态撕裂、性能最优 |
| `updateValue(value)` 单参 | ⚠ 不推荐单独用 | 仅改值不改状态；若紧接 `setStatus` 会双重建 + 中途产生「值新/状态旧」瞬态撕裂 `midState` |
| `setStatus(status)` | ⚠ 不推荐单独用 | 仅改状态不改值；典型用于异常路径 `setStatus(MALFUNCTION)`。叠加 `updateValue` 即双重建 |

**性能依据**：`buildState()`（含 `getDisplayValue` 的单位换算 + `NumberFormat` 格式化）是非平凡开销。
`updateValue(v)` + `setStatus(s)` 分开调 → `midState` 重建两次、进两次同步块；`updateValue(v, status)` → 一次。
设备 20 属性 × 每 5s 轮询，分开调每周期多 20 次 `buildState`，热路径可感。

**结论**：值与状态都变 → `updateValue(value, status)`；只改状态（故障标记）→ `setStatus`；只改值（极少）→ 单参 `updateValue`。

---

## 5. 易混淆点（重点）

### 5.1 `getState()` 返回在途 `midState`，不是已提交 `lastState`

很多人以为 `getState()` 返回「已提交的稳定态」。**不是**——它优先返回在途 `midState`（若 `updateValue`/`setStatus`
后未提交）。这是为了支持「同周期内 updateValue 后立即读到刚写入的值」（计算属性求值、测试预热断言）。
若需绝对自洽的已提交态，调用前先 `publicState()`。

### 5.2 `previousState` 永远指向上一个真正的 state，永不被中间态覆盖

旧设计里 `previousState` 被当作「瞬态捕获缓冲」，在 `updateValue` 里捕获、被同周期后续 `setStatus` 覆盖，
导致发布事件 old 变成从未发布过的「幽灵中间态」，链断裂。三槽模型把在途（`midState`）与已提交（`lastState`）、
上一态（`previousState`）显式分离：`previousState` 仅在 `publicState` 移位时 ← `lastState`，语义干净可推。

### 5.3 「值新/状态旧」瞬态撕裂窗口存在但安全

属性自身的 `updateValue(v)` 与 `setStatus(s)` 之间，`midState` 短暂处于「值已新、状态未新」的撕裂态。
**这是安全的**：没有代码在「某属性自己 updateValue 之后、setStatus 之前」读它的 `getState()`——
跨属性读的都是已 settle 的值（计算属性读别的属性），读自己中途态的用例不存在。

### 5.4 持久化在 commit 点（`publicState`），不在 `updateValue`

`saveState` 从 `updateValue` 迁到 `publicState`：在 commit 时对 `midState`（此时 value+status 已 settle 的
coherent 态）落盘，避免在 `updateValue` 内落盘存到「值新/状态旧」的瞬态撕裂态。
**前提：设备轮询路径必须调 `publicAttrsState()`**——否则该设备的属性 state 永不提交（既不上总线也不持久化）。
极少数集成历史上不调 `publicAttrsState`（靠 `getState` 立即返回让数据对外可见），这些集成的数据属性默认非持久化，
不受影响；但若要让它们的属性上总线/持久化，需补 `publicAttrsState()`。

### 5.5 `device==null` 时不建 state（Placeholder 边界）

`midState` 仅在 `device != null && device.getId() != null` 时构建（`buildState` 需要 `deviceId` 必填字段）。
Placeholder 属性在工厂 `createAlarm/createBlank` 内 `setStatus` 时 device 尚未附着（`LogicDevice.setAttribute`
才 `setDevice`），故 Placeholder 天然不触发 state 构建，`getState()` 返回 null——其对外语义由
`PlaceholderLogicAttribute.Kind` 表达，无冲突。

### 5.6 `publicState` 发布失败返回 false（可重试）

发布失败（如总线瞬时不可用）时 `publicState` 返回 false 且**不移位**（`midState` 保留），下次 `publicState` 可重试。
但持久化在发布前已独立完成（独立 try），所以即使发布失败，本周期 state 仍已落盘。

---

## 6. 不变量

1. `previousState` 恒为 `lastState` 的前驱（发布事件的 old）。
2. `publicState` 成功后 `midState=null`；`lastState`=刚提交态。
3. `getState()` 永不返回「撕裂的已提交态」——已提交态（`lastState`）构建于字段 settle 时刻，自洽。
4. `midState` 非空 ⇔ 自上次 `publicState` 后有 `updateValue`/`setStatus` 变更（`isValueUpdated=true`）。
5. 持久化内容 = commit 时的 `midState`（coherent），与发布解耦。

---

## 附：字段与方法索引（AttributeBase）

- 字段：`midState` / `lastState` / `previousState`（均 `private volatile AttrState<T>`）、`eventContext`、`lastChanged`
- 读：`getState()`（= midState ?: lastState）
- 写在途：`updateValue(T)` / `updateValue(T, AttributeStatus)` / `setStatus(AttributeStatus)` → 重建 `midState`
- 提交：`publicState()`（持久化 + 发布 + 移位）；`DeviceBase.publicAttrsState()` 遍历所有属性提交
- 构建：`buildState()`（private，从当前可变字段建不可变快照，须在 synchronized 内）
- 恢复：`restore(PersistedState)` → 重建 `lastState`，`midState`/`previousState` 置 null
