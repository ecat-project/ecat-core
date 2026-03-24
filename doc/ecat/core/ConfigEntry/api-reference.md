# ConfigEntry & ConfigFlow API 参考

## ecat-core 公共 API

### ConfigEntry

**包**：`com.ecat.core.ConfigEntry`
**注解**：`@Data` (Lombok)

#### Builder 字段

| 方法 | 类型 | 默认值 |
|------|------|--------|
| `entryId(String)` | `String` | `null` |
| `coordinate(String)` | `String` | `null` |
| `uniqueId(String)` | `String` | `null` |
| `title(String)` | `String` | `null` |
| `data(Map<String, Object>)` | `Map<String, Object>` | `new HashMap<>()` |
| `enabled(boolean)` | `boolean` | `true` |
| `createTime(ZonedDateTime)` | `ZonedDateTime` | `null` |
| `updateTime(ZonedDateTime)` | `ZonedDateTime` | `null` |
| `version(int)` | `int` | `1` |
| `build()` | `ConfigEntry` | — |

> `build()` 内部复制 data map（`new HashMap<>(data)`），防止共享可变状态。

#### 实例方法

| 签名 | 返回类型 | 说明 |
|------|----------|------|
| `withUpdate(ConfigEntry newData)` | `ConfigEntry` | 保留 entryId/coordinate/uniqueId；title/data/enabled 从 newData 取（null 则保持）；**版本+1**；updateTime=now() |
| `withReconfigure(ConfigEntry newData)` | `ConfigEntry` | 同 withUpdate 但**版本不变**；uniqueId 可从 newData 更新 |

---

### ConfigEntryRegistry

**包**：`com.ecat.core.ConfigEntry`

#### 构造函数

| 签名 | 说明 |
|------|------|
| `ConfigEntryRegistry(EcatCore core, ConfigEntryPersistence persistence)` | 主构造函数；自动调用 `loadAllEntries()` |
| `ConfigEntryRegistry(ConfigEntryPersistence persistence)` | `@Deprecated` — core 为 null |

#### CRUD 方法

| 签名 | 返回类型 | 说明 |
|------|----------|------|
| `createEntry(ConfigEntry entry)` | `ConfigEntry` | uniqueId 去重 → 自动生成 entryId → 设置时间 → version=0 时设为 1 → 持久化 → 缓存 → 通知集成 |
| `updateEntry(String entryId, ConfigEntry newEntryData)` | `ConfigEntry` | 查找 → `withUpdate()` → 持久化 → 缓存 |
| `reconfigureEntry(String entryId, ConfigEntry newEntryData)` | `ConfigEntry` | 查找 → `withReconfigure()` → 持久化 → 缓存 → 通知集成 |
| `removeEntry(String entryId)` | `void` | **先通知集成** → 删除缓存 → 删除持久化 |
| `setEnabled(String entryId, boolean enabled)` | `ConfigEntry` | 版本+1 → 持久化 → 缓存 → 通知集成（值未变时跳过） |

#### 查询方法

| 签名 | 返回类型 | 说明 |
|------|----------|------|
| `getByEntryId(String entryId)` | `ConfigEntry` (nullable) | 直接缓存查找 |
| `getByUniqueId(String uniqueId)` | `ConfigEntry` (nullable) | 线性扫描 |
| `listByCoordinate(String coordinate)` | `List<ConfigEntry>` | 按 coordinate 过滤 |
| `hasEntries(String coordinate)` | `boolean` | 是否有条目 |
| `listAll()` | `List<ConfigEntry>` | 所有条目 |

#### 异常类型

| 异常 | 说明 |
|------|------|
| `DuplicateUniqueIdException(String uniqueId)` | uniqueId 重复 |
| `EntryNotFoundException(String entryId)` | entryId 不存在 |
| `EntryInUseException(String message)` | 条目被占用 |

---

### ConfigFlowRegistry

**包**：`com.ecat.core.ConfigFlow`

#### 注册管理

| 签名 | 返回类型 | 说明 |
|------|----------|------|
| `registerFlow(String coordinate, AbstractConfigFlow flowInstance)` | `void` | 从实例提取类定义 + 能力信息，存入 registrations |
| `unregisterFlow(String coordinate)` | `void` | 移除注册 |

#### 实例创建

| 签名 | 返回类型 | 说明 |
|------|----------|------|
| `createFlow(String coordinate)` | `AbstractConfigFlow` (nullable) | 反射调用无参构造函数创建新实例 |
| `getRegistration(String coordinate)` | `FlowRegistration` (nullable) | 获取注册信息 |

#### 能力查询

| 签名 | 返回类型 | 说明 |
|------|----------|------|
| `hasUserStep(String coordinate)` | `boolean` | |
| `hasReconfigureStep(String coordinate)` | `boolean` | |
| `getCoordinatesWithUserStep()` | `List<String>` | |
| `getCoordinatesWithReconfigureStep()` | `List<String>` | |
| `getFlowsWithUserStep()` | `List<AbstractConfigFlow>` | 创建实例获取 userStep |
| `listAllCoordinates()` | `List<String>` | |

#### 遗留兼容 (`@Deprecated`)

| 签名 | 说明 |
|------|------|
| `getFlow(Supplier<T> creator, FlowContext sharedContext)` | 缓存 Flow 实例 |
| `clearFlow(String flowId)` | 清除缓存 |
| `clearAll()` | 清除所有缓存 |

---

### AbstractConfigFlow

**包**：`com.ecat.core.ConfigFlow`

#### 构造函数

| 签名 | 说明 |
|------|------|
| `protected AbstractConfigFlow(String flowId)` | 子类调用；创建 I18nProxy |

> **注意**：`ConfigFlowRegistry.createFlow()` 使用无参构造函数，子类必须提供。

#### 入口注册

| 签名 | 说明 |
|------|------|
| `registerStepUser(String stepId, String displayName, BiFunction<Map<String, Object>, FlowContext, ConfigFlowResult> handler)` | 注册用户入口（每个 Flow 仅一个） |
| `registerStepReconfigure(String stepId, String displayName, BiFunction handler)` | 注册重配置入口 |
| `registerStepDiscovery(String stepId, String displayName, BiFunction handler)` | 注册发现入口 |

> 入口步骤同时注册到 `stepDefinitions`（可通过 `handleStep` 调用）。

#### 普通注册

| 签名 | 说明 |
|------|------|
| `registerStep(String stepId, Function<Map<String, Object>, ConfigFlowResult> handler)` | 无显示名 |
| `registerStep(String stepId, Function handler, String displayName)` | 带 StepInfo |
| `registerStep(String stepId, Function handler, StepInfo stepInfo)` | 完整 StepInfo |

#### 数据访问 (protected)

| 签名 | 返回类型 | 说明 |
|------|----------|------|
| `getData()` | `Map<String, Object>` | 委托 `context.getData()` |
| `getStepData(String stepId)` | `Map<String, Object>` | 获取步骤输入（`data["step_inputs"][stepId]`） |
| `saveStepData(String stepId, Map<String, Object> userInput)` | `void` | 保存步骤输入 |
| `getCurrentStepData()` | `Map<String, Object>` | 当前步骤的输入 |
| `getFlowData(String key)` | `Object` | 遗留兼容（ThreadLocal） |
| `setFlowData(String key, Object value)` | `void` | 遗留兼容 |

#### I18n (protected)

| 签名 | 返回类型 | 说明 |
|------|----------|------|
| `getFieldDisplayName(String stepId, String fieldKey)` | `String` (nullable) | 字段显示名（三级回退） |
| `getFieldPlaceholder(String stepId, String fieldKey)` | `String` (nullable) | 字段占位符 |
| `getFieldDescription(String stepId, String fieldKey)` | `String` (nullable) | 字段描述 |
| `getOptionDisplayName(String stepId, String fieldKey, String optionValue)` | `String` | 选项显示名 |
| `t(String i18nKey)` | `String` | 通用 i18n |
| `t(String i18nKey, Object... args)` | `String` | 带参数 i18n |

#### 流程控制 (protected)

| 签名 | 返回类型 | 说明 |
|------|----------|------|
| `showForm(String stepId, ConfigSchema schema, Map<String, Object> errors)` | `ConfigFlowResult` | 返回 SHOW_FORM |
| `createEntry()` | `ConfigFlowResult` | 从 context 数据构建条目 |
| `abort(String reason)` | `ConfigFlowResult` | 终止流程（`final`） |

#### 入口执行

| 签名 | 返回类型 | 说明 |
|------|----------|------|
| `executeUserStep(Map<String, Object> userInput)` | `ConfigFlowResult` | 未注册时抛 IllegalStateException |
| `executeReconfigureStep(String entryId, Map<String, Object> userInput)` | `ConfigFlowResult` | 自动设置 sourceType=RECONFIGURE |

#### 步骤管理

| 签名 | 返回类型 | 说明 |
|------|----------|------|
| `handleStep(String stepId, Map<String, Object> userInput)` | `ConfigFlowResult` | 自动 saveStepData + 更新 currentStep |
| `getStepInfos()` | `Map<String, StepInfo>` | 从 stepDefinitions 提取 |
| `getStepDisplayName(String stepId)` | `String` | i18n > StepInfo > stepId |
| `getPreviousStep()` | `String` (nullable) | 上一步 |
| `goToPreviousStep()` | `void` | 回退（已是第一步时抛异常） |
| `getStepHistory()` | `List<String>` | 步骤历史副本 |

#### Context / Registry

| 签名 | 返回类型 | 说明 |
|------|----------|------|
| `setContext(FlowContext)` | `void` | |
| `getContext()` | `FlowContext` | |
| `setRegistry(ConfigFlowRegistry)` | `void` | |
| `getFlowId()` | `String` | |

#### SourceType

| 签名 | 返回类型 | 说明 |
|------|----------|------|
| `setSourceType(SourceType type)` | `void` | |
| `getSourceType()` | `SourceType` | |
| `setReconfigureEntryId(String entryId)` | `void` | |

#### 能力查询

| 签名 | 返回类型 | 说明 |
|------|----------|------|
| `hasUserStep()` | `boolean` | |
| `hasReconfigureStep()` | `boolean` | |
| `hasDiscoveryStep()` | `boolean` | |
| `getUserStep()` | `EntryStepDefinition` | |
| `getReconfigureStep()` | `EntryStepDefinition` | |
| `getDiscoveryStep()` | `EntryStepDefinition` | |

---

### ConfigSchema

**包**：`com.ecat.core.ConfigFlow`

| 签名 | 返回类型 | 说明 |
|------|----------|------|
| `addField(AbstractConfigItem<?> field)` | `ConfigSchema` | 链式调用 |
| `getFields()` | `List<AbstractConfigItem<?>>` | 不可修改列表 |
| `validate(Map<String, Object> input)` | `Map<String, String>` | 错误 key=字段名, value=错误信息 |
| `addDefaults(Map<String, Object> config)` | `void` | 为缺失字段填充默认值 |

---

### ConfigItem 类型一览

#### 基类：AbstractConfigItem\<T\>

**包**：`com.ecat.core.ConfigFlow.ConfigItem`

所有 ConfigItem 共有的链式方法：

| 方法 | 说明 |
|------|------|
| `displayName(String)` | 显示名称 |
| `description(String)` | 描述 |
| `placeholder(String)` | 占位符 |
| `required(boolean)` | 是否必填 |
| `addValidator(ConstraintValidator<? super T>)` | 添加校验器 |
| `setDefaultValue(T)` | 设置默认值 |

#### TextConfigItem

泛型 `AbstractConfigItem<String>`，fieldType = `"string"`

```java
new TextConfigItem("name", true)              // 必填
new TextConfigItem("name", true, "default")   // 必填 + 默认值
    .length(1, 100)                           // 长度约束
```

#### NumericConfigItem

泛型 `AbstractConfigItem<Double>`，fieldType = `"number"`

```java
new NumericConfigItem("value", true)
new NumericConfigItem("value", true, 3.14)
    .range(0.0, 100.0)                        // 范围约束
```

> 自动类型转换：BigDecimal / Integer / Long / Float / String → Double

#### FloatConfigItem

泛型 `AbstractConfigItem<Float>`，fieldType = `"number"`

```java
new FloatConfigItem("value", true)
new FloatConfigItem("value", true, 3.14f)
    .range(0.0f, 100.0f)
```

#### ShortConfigItem

泛型 `AbstractConfigItem<Short>`，fieldType = `"integer"`

```java
new ShortConfigItem("count", true)
new ShortConfigItem("count", true, (short) 10)
new ShortConfigItem("count", true, 10)        // int 参数（自动检查溢出）
    .range(1, 100)
```

#### BooleanConfigItem

泛型 `AbstractConfigItem<Boolean>`，fieldType = `"boolean"`

```java
new BooleanConfigItem("enabled", true)
new BooleanConfigItem("enabled", true, true)   // 自动添加 BooleanValidator
```

#### EnumConfigItem

泛型 `AbstractConfigItem<String>`，fieldType = `"select"`

```java
new EnumConfigItem("type", true, "sensor")     // 默认值
    .addOption("sensor", "传感器")
    .addOption("actuator", "执行器")
    .addOption("manual")                       // 无 label
```

#### DynamicEnumConfigItem

泛型 `AbstractConfigItem<String>`，fieldType = `"dynamic_enum"`

```java
new DynamicEnumConfigItem("device", true, () -> deviceService.getDevices())
    .caseSensitive(false)                      // 大小写不敏感匹配
```

> 选项通过 `Supplier<Map<String, String>>` 运行时获取。

#### ArrayConfigItem\<T\>

泛型 `AbstractConfigItem<List<T>>`，fieldType = `"array"`

```java
new ArrayConfigItem<>("tags", true, "string")  // 指定元素类型
new ArrayConfigItem<>("ids", true, Arrays.asList(1, 2, 3))
    .size(1, 10)                               // 长度约束
    .addOption("a", "选项A")                    // 可选：限制可选值
```

#### SchemaConfigItem

泛型 `AbstractConfigItem<Map<String, Object>>`，fieldType = `"schema"`

```java
// 内联嵌套
new SchemaConfigItem("network", true,
    new ConfigSchema()
        .addField(new TextConfigItem("host", true))
        .addField(new ShortConfigItem("port", true, 8080)))

// 引用外部 Schema（通过 ConfigSchemaProvider）
new SchemaConfigItem("serial", true, SerialCommConfigSchema.class)

// 引用 + 展开（字段提升到父级）
new SchemaConfigItem("serial", true, SerialCommConfigSchema.class).extend()
```

---

### FlowRegistration

**包**：`com.ecat.core.ConfigFlow`
**注解**：`@Getter`, `@AllArgsConstructor`

| 字段 | 类型 | 说明 |
|------|------|------|
| `coordinate` | `String` | 集成标识 |
| `flowClass` | `Class<? extends AbstractConfigFlow>` | Flow 类定义 |
| `userStepSupported` | `boolean` | |
| `reconfigureStepSupported` | `boolean` | |
| `discoveryStepSupported` | `boolean` | |

别名方法：`hasUserStep()` / `hasReconfigureStep()` / `hasDiscoveryStep()`

### EntryStepDefinition

**包**：`com.ecat.core.ConfigFlow`
**注解**：`@Data`, `@AllArgsConstructor`

| 字段 | 类型 | 说明 |
|------|------|------|
| `stepId` | `String` | 步骤标识 |
| `displayName` | `String` | 显示名称 |
| `handler` | `BiFunction<Map<String, Object>, FlowContext, ConfigFlowResult>` | 处理函数 |

### StepInfo

**包**：`com.ecat.core.ConfigFlow`
**注解**：`@Data`, `@NoArgsConstructor`

| 字段 | 类型 | 说明 |
|------|------|------|
| `displayName` | `String` | 显示名称 |
| `description` | `String` | 描述 |
| `icon` | `String` | 图标 |

构造：`StepInfo.of(String displayName)` / `StepInfo.of(String displayName, String description)`

### SourceType

**包**：`com.ecat.core.ConfigEntry`

| 值 | 说明 |
|----|------|
| `USER` | 用户主动创建 |
| `RECONFIGURE` | 重配置 |
| `DISCOVERY` | 自动发现（预留） |

---

## ecat-core-api REST 端点

### ConfigFlow 端点

**基础路径**：`/core-api/config-flows`

| 方法 | 路径 | 请求体 | 说明 |
|------|------|--------|------|
| GET | `/core-api/config-flows/providers` | — | 获取所有 Flow 提供者 |
| POST | `/core-api/config-flows/start` | `ConfigFlowStartDto` | 启动配置 Flow |
| GET | `/core-api/config-flows/{flowId}` | — | 获取 Flow 当前状态 |
| POST | `/core-api/config-flows/step` | `ConfigFlowStepDto` | 提交步骤数据 |
| POST | `/core-api/config-flows/previous` | `{"flowId":"..."}` | 返回上一步 |
| DELETE | `/core-api/config-flows/{flowId}` | — | 取消 Flow |

### ConfigEntry 端点

**基础路径**：`/core-api/config-flow/entries`

| 方法 | 路径 | 请求体 | 说明 |
|------|------|--------|------|
| GET | `/core-api/config-flow/entries` | — | 列出所有条目（可选 `?coordinate=` 过滤） |
| GET | `/core-api/config-flow/entries/{entryId}` | — | 获取单个条目 |
| DELETE | `/core-api/config-flow/entries/{entryId}` | — | 删除条目 |
| POST | `/core-api/config-flow/entries/enable` | `{"entryId":"..."}` | 启用条目 |
| POST | `/core-api/config-flow/entries/disable` | `{"entryId":"..."}` | 禁用条目 |
| POST | `/core-api/config-flow/entries/reconfigure` | `{"entryId":"..."}` | 启动重配置 Flow |

### 统一响应格式

```json
{
    "code": 200,
    "msg": "success",
    "data": { ... }
}
```

错误响应：`{"code": 500, "msg": "错误信息", "data": null}`

---

## DTO 结构

### ConfigEntryDto

**包**：`com.ecat.integration.EcatCoreApiIntegration.ConfigEntry.dto`
**注解**：`@Data`

| 字段 | 类型 | 说明 |
|------|------|------|
| `entryId` | `String` | |
| `coordinate` | `String` | |
| `uniqueId` | `String` | |
| `title` | `String` | |
| `data` | `Map<String, Object>` | |
| `enabled` | `boolean` | |
| `createTime` | `String` | ISO 8601 格式 |
| `updateTime` | `String` | ISO 8601 格式 |
| `version` | `int` | |
| `integrationName` | `String` | 集成名称（从 IntegrationRegistry 丰富） |
| `integrationStatus` | `String` | 集成状态（从 IntegrationRegistry 丰富） |

转换方法：
- `fromEntity(ConfigEntry entry)` → ConfigEntryDto — 时间格式化为 ISO 8601
- `toEntity()` → ConfigEntry — ISO 8601 解析为 ZonedDateTime

### ConfigFlowSchemaDto

由 `SchemaConversionService.convertSchema()` 生成，包含：

| 字段 | 类型 | 说明 |
|------|------|------|
| `stepId` | `String` | 步骤标识 |
| `displayName` | `String` | 步骤显示名 |
| `fields` | `List<ConfigFlowFieldDto>` | 字段列表 |
| `errors` | `Map<String, Object>` | 校验错误 |

### ConfigFlowFieldDto

| 字段 | 类型 | 说明 |
|------|------|------|
| `key` | `String` | 字段 key |
| `displayName` | `String` | 显示名 |
| `fieldType` | `String` | 字段类型 |
| `required` | `boolean` | 是否必填 |
| `defaultValue` | `Object` | 默认值 |
| `nestedFields` | `List<ConfigFlowFieldDto>` | 嵌套字段（SchemaConfigItem 非 extend 模式） |
| `extendFields` | `List<ConfigFlowFieldDto>` | 展开字段（SchemaConfigItem extend 模式） |
| `options` | `List<Map<String, String>>` | 选项列表（EnumConfigItem） |
| `validValues` | `Set<String>` | 有效值（DynamicEnumConfigItem 运行时填充） |

---

## 相关文档

- [ConfigFlow 开发指南](../Integration/ConfigFlow-development.md)
- [ConfigFlow 使用指南](../Integration/ConfigFlow-usage.md)
