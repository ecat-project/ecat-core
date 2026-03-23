# ConfigEntry 架构设计

## 设计目标

| 目标 | 说明 |
|------|------|
| 统一配置条目管理 | 所有集成的配置条目通过 ConfigEntry 模型统一管理 |
| 分步配置向导 (Config Flow) | 通过 ConfigFlow 机制引导用户完成复杂配置 |
| 集成生命周期集成 | 配置条目的创建、重配置、删除与集成设备生命周期绑定 |
| 时区感知 | 使用 `ZonedDateTime` 统一时间处理，替代旧版 `Date` |
| 状态隔离 | Flow 每次使用反射创建新实例，保证并发安全 |

## 分层架构

```
┌──────────────────────────────────────────────────────────┐
│                    Frontend (Lit 3)                      │
│   entries.html / entries.js / schema-field.js            │
└──────────────┬────────────────────┬─────────────────────┘
               │                    │
┌──────────────▼────────────────────▼─────────────────────┐
│               API Layer (ecat-core-api)                  │
│  ConfigEntryController · ConfigFlowController            │
│  ConfigEntryService  · ConfigFlowService                 │
│  SchemaConversionService · ConfigEntryDto                │
└──────────────┬────────────────────┬─────────────────────┘
               │                    │
┌──────────────▼────────────────────▼─────────────────────┐
│                Core Layer (ecat-core)                    │
│  ConfigEntryRegistry  · ConfigFlowRegistry               │
│  YmlConfigEntryPersistence · DateTimeUtils               │
│  AbstractConfigFlow  · FlowRegistration                  │
│  ConfigSchema  · ConfigItem types                        │
└──────────────┬────────────────────┬─────────────────────┘
               │                    │
┌──────────────▼────────────────────▼─────────────────────┐
│             Integration Layer                            │
│  IntegrationBase (getConfigFlow / createEntry / ...)     │
│  [DemoConfigFlow] [SailheroConfigFlow] [SerialSchema]    │
└──────────────────────────────────────────────────────────┘
```

| 层 | 职责 |
|----|------|
| Frontend | 配置条目列表、配置向导表单、Schema 字段渲染 |
| API Layer | REST 端点、DTO 转换、Flow 实例管理、CREATE_ENTRY 处理 |
| Core Layer | 数据模型、注册表、持久化、Flow 执行引擎、Schema 定义 |
| Integration Layer | 具体业务逻辑、设备管理、Schema 复用 |

## 核心数据模型

### ConfigEntry

配置条目的核心模型，使用 Lombok `@Data`。

| 字段 | 类型 | 说明 |
|------|------|------|
| `entryId` | `String` | 系统生成 UUID |
| `coordinate` | `String` | 集成标识（`groupId:artifactId` 格式） |
| `uniqueId` | `String` | 业务唯一标识（由集成生成，如 `manufacturer_serial`） |
| `title` | `String` | 用户可编辑的配置名称 |
| `data` | `Map<String, Object>` | 核心配置数据 |
| `enabled` | `boolean` | 启用状态 |
| `createTime` | `ZonedDateTime` | 创建时间（时区感知） |
| `updateTime` | `ZonedDateTime` | 更新时间（时区感知） |
| `version` | `int` | 版本号（部分操作自增） |

#### 不可变更新模式

ConfigEntry 通过 Builder 模式和 `withUpdate()` / `withReconfigure()` 方法实现不可变更新：

```java
// Builder 默认值: data=new HashMap<>(), enabled=true, version=1
ConfigEntry entry = new ConfigEntry.Builder()
    .coordinate("com.ecat:integration-serial")
    .title("Serial Device 1")
    .build();

// 更新（版本自增）
ConfigEntry updated = entry.withUpdate(newEntryData);

// 重配置（版本不变）
ConfigEntry reconfigured = entry.withReconfigure(newEntryData);
```

| 方法 | 版本变化 | uniqueId 行为 | 使用场景 |
|------|----------|---------------|----------|
| `withUpdate()` | +1 | 保持不变 | API 配置修改、enable/disable |
| `withReconfigure()` | 不变 | 可从 newData 更新 | UI 重配置流程 |

#### uniqueId 业务唯一性约束

`ConfigEntryRegistry.createEntry()` 会检查 uniqueId 唯一性，重复则抛出 `DuplicateUniqueIdException`。uniqueId 由集成负责生成和保证唯一性（如 `{manufacturer}_{serial_number}`）。

### FlowContext

一次 Flow 执行的共享数据容器。

| 字段 | 类型 | 说明 |
|------|------|------|
| `flowId` | `String` | Flow 实例标识 |
| `coordinate` | `String` | 所属集成标识 |
| `currentStep` | `String` | 当前步骤 |
| `data` | `Map<String, Object>` | 共享数据（步骤间传递） |

数据访问通过 `getData()` 获取直接引用。步骤数据通过 `step_inputs` 嵌套结构存储在 `data` 中。

### SourceType

Flow 来源类型枚举：

| 值 | 说明 |
|----|------|
| `USER` | 用户主动创建 |
| `RECONFIGURE` | 对已有条目重新配置 |
| `DISCOVERY` | 设备自动发现（预留） |

## ConfigFlow 子系统

### AbstractConfigFlow

配置向导的抽象基类，管理步骤注册、执行、数据传递。

#### 入口步骤机制

入口步骤通过函数式注册定义，每种类型每个 Flow 只能注册一个：

```java
// 入口步骤注册（BiFunction 签名，接收 userInput + context）
registerStepUser("user", "配置设备", this::stepUser);
registerStepReconfigure("reconfigure", "重配置设备", this::stepReconfigure);
registerStepDiscovery("discovery", "发现设备", this::stepDiscovery);

// 普通步骤注册（Function 签名，仅接收 userInput）
registerStep("device_config", "设备配置", this::stepDeviceConfig);
```

> **注意**：入口步骤 handler 签名为 `BiFunction<Map<String,Object>, FlowContext, ConfigFlowResult>`，普通步骤为 `Function<Map<String,Object>, ConfigFlowResult>`。

#### 步骤注册与分发

```java
// handleStep 自动行为：
// 1. 从 stepDefinitions 查找 handler
// 2. 自动调用 saveStepData() 保存用户输入
// 3. 更新 currentStep
// 4. 如果结果是 SHOW_FORM，更新 currentStep 为展示的步骤
```

#### createEntry() 自动构建

`createEntry()` 方法从 FlowContext 数据自动构建 ConfigEntry：

- 从 `context.getCoordinate()` 获取 coordinate
- 从 `getData()` 读取 `title` 和 `uniqueId`
- 根据 `sourceType` 区分 create（生成新 entryId）和 reconfigure（保留原 entryId）

#### I18n 约定

- 前缀常量：`config_flow`
- 步骤显示名 key：`config_flow.step_{stepId}.display_name`
- 优先级：i18n 资源 > 注册的 StepInfo > stepId

### ConfigFlowRegistry

Flow 注册中心，管理 Flow 类定义和能力缓存。

#### FlowRegistration

```
FlowRegistration
  coordinate: String                          // 集成标识
  flowClass: Class<? extends AbstractConfigFlow>  // 类定义
  userStepSupported: boolean                  // 缓存的能力标志
  reconfigureStepSupported: boolean
  discoveryStepSupported: boolean
```

存储类定义而非实例，避免状态共享问题。

#### 实例创建（状态隔离）

```java
// createFlow() 使用反射创建新实例
AbstractConfigFlow flow = registry.createFlow(coordinate);
// 等价于: flowClass.getDeclaredConstructor().newInstance()
```

> **关键约束**：具体 Flow 子类**必须提供无参构造函数**，`createFlow()` 使用 `getDeclaredConstructor().newInstance()` 调用。

#### 能力查询（无需创建实例）

| 方法 | 返回类型 | 说明 |
|------|----------|------|
| `hasUserStep(coordinate)` | `boolean` | 是否支持用户入口 |
| `hasReconfigureStep(coordinate)` | `boolean` | 是否支持重配置 |
| `getCoordinatesWithUserStep()` | `List<String>` | 所有支持用户入口的集成 |
| `getCoordinatesWithReconfigureStep()` | `List<String>` | 所有支持重配置的集成 |
| `getFlowsWithUserStep()` | `List<AbstractConfigFlow>` | 创建实例获取所有支持用户入口的 Flow |
| `listAllCoordinates()` | `List<String>` | 所有已注册集成 |

#### Flow 实例缓存（遗留兼容）

`ConfigFlowRegistry` 内部维护 `flowInstances` Map（`@Deprecated`），用于 Flow 复用场景。key 格式为 `className:flowId`。

### FlowContext 自动保存

`handleStep()` 在执行 handler 前自动调用 `saveStepData()` 将用户输入保存到 `context.data["step_inputs"][stepId]`。

> **注意**：入口步骤（executeUserStep / executeReconfigureStep）不经过 handleStep，需要手动调用 saveStepData。

## ConfigEntry 子系统

### ConfigEntryRegistry

基于 ConcurrentHashMap 的内存缓存，提供 CRUD 和通知机制。

| 方法 | 返回类型 | 行为 |
|------|----------|------|
| `createEntry(entry)` | `ConfigEntry` | uniqueId 校验 → 自动生成 entryId → 设置时间 → 持久化 → 缓存 → **通知集成** |
| `updateEntry(entryId, newData)` | `ConfigEntry` | 查找 → withUpdate()（版本+1） → 持久化 → 缓存 |
| `reconfigureEntry(entryId, newData)` | `ConfigEntry` | 查找 → withReconfigure()（版本不变） → 持久化 → 缓存 → **通知集成** |
| `removeEntry(entryId)` | `void` | **先通知集成清理** → 删除缓存 → 删除持久化 |
| `setEnabled(entryId, enabled)` | `ConfigEntry` | 值未变时跳过 → 构建 enabled 条目 → 版本+1 → **通知集成** → 持久化 → 缓存 |

#### 通知顺序

| 操作 | 通知方法 | 失败处理 |
|------|----------|----------|
| create | `integration.createEntry(entry)` | 捕获 UnsupportedOperationException，跳过 |
| reconfigure | `integration.reconfigureEntry(entryId, entry)` | 同上 |
| remove | `integration.removeEntry(entryId)` | 同上 |
| enable | `integration.enableEntry(entry)` | 同上 |
| disable | `integration.disableEntry(entryId)` | 同上 |

#### 异常类型

| 异常 | 触发场景 |
|------|----------|
| `DuplicateUniqueIdException` | uniqueId 已存在 |
| `EntryNotFoundException` | entryId 不存在 |
| `EntryInUseException` | 条目被占用（不可卸载） |

### 持久化

#### ConfigEntryPersistence 接口

```java
public interface ConfigEntryPersistence {
    List<ConfigEntry> loadAll();
    void save(ConfigEntry entry);
    void update(ConfigEntry entry);
    void delete(String entryId);
}
```

#### YmlConfigEntryPersistence

- 存储路径：`.ecat-data/core/config_entries/{groupId}/{artifactId}/{entryId}.yml`
- coordinate 按 `:` 分割为 groupId 和 artifactId 子目录
- YAML 格式：BLOCK 风格，2 空格缩进
- `ZonedDateTime` 深度序列化为 ISO 8601 字符串（递归处理 Map/List 中的时间字段）
- 删除时自动清理空目录
- `update()` 内部委托给 `save()`

### 集成层扩展点

IntegrationBase 提供的 ConfigEntry 生命周期方法：

| 方法 | 默认行为 | 说明 |
|------|----------|------|
| `getConfigFlow()` | 返回 `null` | 子类重写返回 Flow 实例 |
| `createEntry(entry)` | 抛出 `UnsupportedOperationException` | 创建并启动设备 |
| `reconfigureEntry(entryId, entry)` | 抛出 `UnsupportedOperationException` | 更新设备配置 |
| `removeEntry(entryId)` | 调用 `onPreRemove()` → `registry.removeEntry()` | 删除前清理 |
| `enableEntry(entry)` | 委托 `createEntry()` | 启用设备 |
| `disableEntry(entryId)` | 委托 `removeEntry()` | 禁用设备 |
| `mergeEntries(entries)` | 返回 `null` | 版本升级迁移 |
| `onPreRemove(entryId)` | 空实现 | 资源清理钩子 |

> **关键约束**：`getConfigFlow()` **必须每次返回新实例**。Registry 提取类定义和能力信息后，后续 Flow 通过反射创建。

## API 层

### ConfigFlowController

基础路径：`/core-api/config-flows`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/core-api/config-flows/providers` | 获取所有 Flow 提供者 |
| POST | `/core-api/config-flows/start` | 启动配置 Flow |
| GET | `/core-api/config-flows/{flowId}` | 获取 Flow 状态 |
| POST | `/core-api/config-flows/step` | 提交步骤 |
| POST | `/core-api/config-flows/previous` | 返回上一步 |
| DELETE | `/core-api/config-flows/{flowId}` | 取消 Flow |

#### CREATE_ENTRY 处理

`handleSubmitStep` 在检测到 `ResultType.CREATE_ENTRY` 时：

1. 从 result 获取 entry
2. 根据 `flow.getSourceType()` 区分：
   - `RECONFIGURE` → `registry.reconfigureEntry()` → `integration.reconfigureEntry()`
   - 其他 → `registry.createEntry()` → `integration.createEntry()`
3. Registry 内部自动处理持久化和集成通知

### ConfigEntryController

基础路径：`/api/config-flow/entries`

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/config-flow/entries` | 列出所有条目（可选 `?coordinate=` 过滤） |
| GET | `/api/config-flow/entries/{entryId}` | 获取单个条目 |
| DELETE | `/api/config-flow/entries/{entryId}` | 删除条目 |
| POST | `/api/config-flow/entries/enable` | 启用条目 |
| POST | `/api/config-flow/entries/disable` | 禁用条目 |
| POST | `/api/config-flow/entries/reconfigure` | 启动重配置流程 |

响应格式：`{"code": 200, "msg": "success", "data": {...}}`

### SchemaConversionService

ConfigSchema → DTO 转换管线：

1. `convertSchema(schema)` → `ConfigFlowSchemaDto`
2. 遍历 schema.getFields() → `convertField(item)` → `ConfigFlowFieldDto`
3. SchemaConfigItem 特殊处理：
   - `extend` 模式 → `dto.setExtendFields()`
   - 普通嵌套 → `dto.setNestedFields()`
4. i18n 三级回退：Flow 方法 → item 默认值 → raw key

## 核心数据流

### 创建 Entry 流程

```
用户点击"配置"
    │
    ▼
ConfigFlowController.startFlow
    │
    ▼
ConfigFlowService.startFlow
    ├─ flowRegistry.hasUserStep(coordinate)  // 能力查询（无实例创建）
    ├─ flow = registry.createFlow(coordinate) // 反射创建新实例
    ├─ flow.setContext(context)                // 注入 context
    └─ flow.executeUserStep(null)              // 执行入口步骤
         │
         ▼
    [多步配置向导: SHOW_FORM ↔ submitStep 循环]
         │
         ▼
    flow.createEntry()                         // 构建 ConfigEntry
         │
         ▼
ConfigFlowResult(CREATE_ENTRY, entry)
    │
    ▼
ConfigFlowController.handleCreateEntry
    ├─ flow.getSourceType() → CREATE
    ├─ registry.createEntry(entry)             // 持久化 + 缓存 + 通知集成
    └─ integration.createEntry(savedEntry)     // 创建并启动设备
```

### 重新配置 Entry 流程

```
用户点击"重配置"
    │
    ▼
ConfigEntryController.startReconfigure
    │
    ▼
ConfigFlowService.startReconfigureFlow
    ├─ entryRegistry.getByUniqueId(uniqueId)   // 查找已有条目
    ├─ flow = registry.createFlow(coordinate)  // 反射创建新实例
    ├─ flow.setSourceType(RECONFIGURE)         // 设置来源
    ├─ flow.setReconfigureEntryId(entryId)     // 关联原条目
    ├─ context.getData().putAll(entry.data)    // 预填配置数据
    ├─ context.put("title", entry.title)
    ├─ context.put("uniqueId", entry.uniqueId)
    └─ flow.executeReconfigureStep(entryId, null)
         │
         ▼
    [多步重配置向导]
         │
         ▼
    flow.createEntry()                         // 构建 ConfigEntry（保留 entryId）
         │
         ▼
ConfigFlowController.handleCreateEntry
    ├─ flow.getSourceType() → RECONFIGURE
    ├─ registry.reconfigureEntry(entryId, entry)  // 版本不变
    └─ integration.reconfigureEntry(entryId, entry)
```

### 删除 Entry 流程

```
用户点击"删除" → 确认
    │
    ▼
ConfigEntryController.deleteEntry
    │
    ▼
ConfigEntryService.deleteEntry
    ├─ integration.removeEntry(entryId)        // 1. 先通知集成清理设备
    │   └─ onPreRemove(entryId)                //    清理钩子
    └─ registry.removeEntry(entryId)           // 2. 再删除持久化和缓存
```

### 服务重启恢复

```
Server Start
    │
    ▼
ConfigEntryRegistry.loadAllEntries()           // 从 YAML 加载所有条目
    │
    ▼
IntegrationManager.loadEnabledIntegrations()   // 加载并启动集成
    │
    ▼
saveInitialDependencySnapshot()
    │
    ▼
loadExistingConfigEntries()
    │
    ├─ 遍历所有 coordinate
    │   ├─ entries = registry.listByCoordinate(coordinate)
    │   ├─ mergedEntries = integration.mergeEntries(entries)  // 版本升级
    │   ├─ if mergedEntries != null: 持久化合并结果
    │   └─ for each entry (仅 enabled):
    │       └─ integration.createEntry(entry)   // 重建设备
    │
    ▼
Done
```

## ConfigSchema 字段类型

ConfigSchema 是字段定义的集合，仅包含一个 `fields` 列表。嵌套通过 `SchemaConfigItem` 实现。

### 字段类型一览

| 类型 | 泛型 | field_type | 说明 |
|------|------|------------|------|
| `TextConfigItem` | `String` | `"string"` | 文本字段，支持 `.length(min, max)` |
| `NumericConfigItem` | `Double` | `"number"` | 数值字段，支持 `.range(min, max)` |
| `FloatConfigItem` | `Float` | `"number"` | 浮点字段，支持 `.range(min, max)` |
| `ShortConfigItem` | `Short` | `"integer"` | 整数字段，支持 `.range(min, max)` |
| `BooleanConfigItem` | `Boolean` | `"boolean"` | 布尔字段 |
| `EnumConfigItem` | `String` | `"select"` | 静态枚举，编译时固定选项 |
| `DynamicEnumConfigItem` | `String` | `"dynamic_enum"` | 动态枚举，运行时通过 `Supplier` 获取选项 |
| `ArrayConfigItem<T>` | `List<T>` | `"array"` | 数组字段，支持 `.size(min, max)` |
| `SchemaConfigItem` | `Map<String,Object>` | `"schema"` | 嵌套/引用 Schema 字段 |

### getFieldType 映射（后端 → 前端）

| 后端 field_type | 前端组件 |
|-----------------|----------|
| `"string"` | 文本输入 |
| `"number"` | 数值输入 |
| `"integer"` | 整数输入 |
| `"boolean"` | 开关 |
| `"select"` | 下拉选择 |
| `"dynamic_enum"` | 动态下拉 |
| `"array"` | 数组编辑 |
| `"schema"` | 嵌套表单/展开字段 |

### SchemaConfigItem 三种模式

| 模式 | 构造方式 | JSON 结构 | 用例 |
|------|----------|-----------|------|
| 内联嵌套 | `new SchemaConfigItem("key", true, new ConfigSchema()...)` | `{"key": {...}}` | 一次性定义 |
| 引用嵌套 | `new SchemaConfigItem("key", true, ProviderClass.class)` | `{"key": {...}}` | Schema 复用 |
| 引用展开 | `new SchemaConfigItem("key", true, ProviderClass.class).extend()` | 字段提升到父级 | 字段需要与同级混排 |

## 扩展模式

### 新增 ConfigItem 类型

1. 创建 `XxxConfigItem extends AbstractConfigItem<T>`
2. 实现 `getFieldType()` 返回前端组件标识
3. 实现 `validateType()` 类型校验
4. 实现 `addDefaultValue()` 默认值设置
5. 在前端 `FieldRegistry` 注册对应渲染器

### 新增集成流程

**集成类实现要点**：

1. 重写 `getConfigFlow()` 返回新实例
2. 实现 `createEntry(entry)` 创建并启动设备
3. 实现 `reconfigureEntry(entryId, entry)` 更新设备配置
4. 实现 `removeEntry(entryId)` 清理资源（可选重写 `onPreRemove`）
5. 实现 `generateUniqueId()` 生成业务唯一标识

**Flow 类注册模式**：

```java
public class MyConfigFlow extends AbstractConfigFlow {
    public MyConfigFlow() { super(null); }  // 必须有无参构造

    public void initFlow() {
        registerStepUser("user", "配置设备", this::stepUser);
        registerStepReconfigure("reconfigure", "重配置", this::stepReconfigure);
        registerStep("step_xxx", "步骤名", this::stepXxx);
    }
}
```

**Schema 复用模式**：

```java
// 1. 定义可复用 Schema（实现 ConfigSchemaProvider）
public class SerialCommConfigSchema implements ConfigSchemaProvider {
    public ConfigSchema createSchema() { ... }
}

// 2. 在其他集成中引用
new SchemaConfigItem("serial_settings", true, SerialCommConfigSchema.class)

// 3. 或展开到父级
new SchemaConfigItem("serial", true, SerialCommConfigSchema.class).extend()
```

> Schema 无需注册。`SchemaConfigItem` 通过 `ConfigSchemaProvider` 接口直接解析。

### 版本升级

**withUpdate() vs withReconfigure() 版本策略**：

| 操作 | 方法 | 版本变化 |
|------|------|----------|
| 创建新条目 | `createEntry()` | 设为 1 |
| UI 重配置 | `reconfigureEntry()` → `withReconfigure()` | **不变** |
| API 配置修改 | `updateEntry()` → `withUpdate()` | +1 |
| 启用/禁用 | `setEnabled()` → `withUpdate()` | +1 |

**mergeEntries() 预留**：

`IntegrationBase.mergeEntries(List<ConfigEntry>)` 在服务重启加载条目后调用，用于版本升级迁移：

```java
@Override
public List<ConfigEntry> mergeEntries(List<ConfigEntry> entries) {
    List<ConfigEntry> merged = new ArrayList<>();
    for (ConfigEntry entry : entries) {
        if (entry.getVersion() < 2) entry = upgradeV1toV2(entry);
        if (entry.getVersion() < 3) entry = upgradeV2toV3(entry);
        merged.add(entry);
    }
    return merged;  // null 表示无需变更
}
```

## 关键注意事项

1. **`getConfigFlow()` 必须返回新实例** — Registry 提取类定义和能力信息，后续通过反射创建实例
2. **Flow 子类必须有无参构造函数** — `ConfigFlowRegistry.createFlow()` 使用 `getDeclaredConstructor().newInstance()`
3. **uniqueId 由集成生成并保证唯一** — Registry 仅做去重校验
4. **removeEntry 通知顺序：先集成后持久化** — 确保设备先停止清理，再删除配置文件
5. **enable/disable 使用 withUpdate()（版本+1）** — 重配置使用 withReconfigure()（版本不变）
6. **入口步骤 vs 普通步骤 handler 签名不同** — 入口：`BiFunction<Map, FlowContext, Result>`；普通：`Function<Map, Result>`
7. **DynamicEnumConfigItem 不走 i18n 回退链** — 选项标签直接使用 `Supplier` 返回值
8. **ConfigFlowResult.CREATE_ENTRY 用于 create 和 update** — 需通过 `flow.getSourceType()` 区分
9. **coordinate 格式严格为 `groupId:artifactId`** — 持久化路径按 `:` 分割为子目录
10. **`handleStep()` 自动 saveStepData，入口步骤不自动** — `executeUserStep()` 等需手动保存
11. **集成卸载保护** — 有条目的集成无法卸载，需先删除所有条目
12. **重启仅加载 enabled 条目** — disabled 条目跳过 `createEntry()` 调用
