# ConfigFlow 开发指南

ConfigFlow 是 ecat 设备集成的配置向导框架。它通过多步骤表单引导用户完成设备创建、重配置和自动发现。

## 目录

- [核心概念](#核心概念)
- [快速开始：创建一个 ConfigFlow](#快速开始创建一个-configflow)
- [步骤处理器模式](#步骤处理器模式)
- [ConfigSchema 与 ConfigItem](#configschema-与-configitem)
- [ConfigItem 类型速查](#configitem-类型速查)
- [Schema 默认值最佳实践](#schema-默认值最佳实践)
- [ConfigSchemaProvider 与可复用 Schema](#configschemaprovider-与可复用-schema)
- [Builder 模式规范](#builder-模式规范)
- [i18n 翻译](#i18n-翻译)
- [FlowContext 数据管理](#flowcontext-数据管理)
- [重配置与发现](#重配置与发现)
- [ConfigFlowResult 返回值](#configflowresult-返回值)
- [完整示例](#完整示例)

---

## 核心概念

```
ConfigFlowProvider (集成实现)
  └── 提供 AbstractConfigFlow 实例

AbstractConfigFlow (流程控制器)
  ├── 注册步骤 (registerStep / registerStepUser / registerStepReconfigure)
  ├── 每个步骤返回 ConfigFlowResult
  └── FlowContext 维护跨步骤数据

ConfigSchema (一个步骤的表单定义)
  └── List<AbstractConfigItem<?>> (字段列表)

ConfigSchemaProvider (可复用的 Schema 定义)
  └── 如 SerialCommConfigSchema、ModbusTcpCommConfigSchema
```

**执行流程**：

```
用户选择集成 → API 调用 executeUserStep() → handleStep("user", input)
  → step handler 返回 showForm("device_config", schema, errors)
  → API 调用 handleStep("device_config", input)
  → step handler 返回 showForm("comm_config", schema, errors)
  → ...
  → step handler 返回 createEntry() → 根据 sourceType 创建新 ConfigEntry 或更新已有 ConfigEntry
```

> **Discovery 路径**（IMPORT_FLOW/ZEROCONF/MQTT）走同一 `handleStep`，入口换为 `ConfigFlowService.startDiscoveryFlow(coordinate, source, payload)` → `handleStep("$discovery:<SOURCE>", payload)` 跑 discovery handler，之后同样经 `showForm` / `createEntry` 推进。详见 [DISCOVERY.md](DISCOVERY.md)。

---

## 快速开始：创建一个 ConfigFlow

### 1. 实现 ConfigFlowProvider

```java
public class MyIntegration implements ConfigFlowProvider {

    @Override
    public String getDisplayName() {
        return "我的设备集成";
    }

    @Override
    public String getFlowType() {
        return "my_device";
    }

    @Override
    public AbstractConfigFlow createFlow() {
        return new MyConfigFlow();
    }
}
```

### 2. 继承 AbstractConfigFlow

```java
public class MyConfigFlow extends AbstractConfigFlow {

    public MyConfigFlow() {
        super();

        // 注册入口步骤（每种流程只能注册一个）
        registerStepUser("user", "配置我的设备", this::stepUser);
        registerStepReconfigure("reconfigure", "重新配置我的设备", this::stepReconfigure);

        // 注册普通步骤
        registerStep("device_config", this::stepDeviceConfig, "设备配置");
        registerStep("comm_config", this::stepCommConfig, "通讯配置");
        registerStep("final_confirm", this::stepFinalConfirm, "确认配置");
    }
}
```

### 3. 编写步骤处理器

每个步骤处理器遵循相同的模式：

```java
private ConfigFlowResult stepXxx(Map<String, Object> userInput) {
    // 阶段 1：首次进入（userInput 为空）→ 显示表单
    if (userInput == null || userInput.isEmpty()) {
        return showForm("xxx", createXxxSchema(), new HashMap<>());
    }

    // 阶段 2：用户提交数据 → 验证
    ConfigSchema schema = createXxxSchema();
    Map<String, Object> errors = schema.validate(userInput);
    if (!errors.isEmpty()) {
        return showForm("xxx", schema, errors);
    }

    // 阶段 3：验证通过 → 保存数据，进入下一步
    context.getEntryData().put("xxx", userInput);
    return showForm("next_step", createNextSchema(), new HashMap<>());
}
```

---

## 步骤处理器模式

### registerStep vs registerStepUser / registerStepReconfigure

| 方法 | 用途 | handler 签名 | 可注册数量 |
|------|------|-------------|-----------|
| `registerStep()` | 普通步骤 | `Function<Map, ConfigFlowResult>` | 任意数量 |
| `registerStepUser()` | 用户入口步骤 | `BiFunction<Map, FlowContext, ConfigFlowResult>` | 1 个 |
| `registerStepReconfigure()` | 重配置入口步骤 | `BiFunction<Map, FlowContext, ConfigFlowResult>` | 1 个 |
| `registerStepDiscovery()` | 自动发现入口步骤 | `BiFunction<Map, FlowContext, ConfigFlowResult>` | 0-1 个 |

`registerStepUser` 和 `registerStepReconfigure` 的 handler 会额外接收 `FlowContext` 参数，重配置时已包含原有 entryData。

### 首次进入 vs 提交数据

```java
// userInput == null 或 empty 表示用户首次进入此步骤
// 此时需要 showForm 显示空表单

// userInput 非空表示用户点击了"下一步"提交了数据
// 此时需要验证并决定是否前进
```

**注意**：`handleStep()` 在调用 handler 前会自动将 `userInput` 保存到 `context.stepInputs`，所以无需手动保存步骤输入。

---

## ConfigSchema 与 ConfigItem

ConfigSchema 是一组有序字段的集合。每个字段是一个 ConfigItem 子类。

### 创建 Schema

```java
ConfigSchema schema = new ConfigSchema()
    .addField(new TextConfigItem("name", true)
        .displayName("设备名称")
        .placeholder("请输入设备名称")
        .length(1, 50))
    .addField(new EnumConfigItem("model", true, "default_model")
        .displayName("设备型号")
        .addOption("model_a", "型号 A")
        .addOption("model_b", "型号 B")
        .buildValidator())
    .addField(new NumericConfigItem("timeout", true, 500.0)
        .displayName("超时时间(ms)")
        .range(100, 60000));
```

### 验证用户输入

```java
Map<String, Object> errors = schema.validate(userInput);
// errors 为空 → 验证通过
// errors 非空 → key 为字段名，value 为错误消息 (String 或嵌套 Map)
if (!errors.isEmpty()) {
    return showForm("step_id", schema, errors);
}
```

### 链式调用

所有 ConfigItem 的配置方法都支持链式调用：

```java
new TextConfigItem("name", true)
    .displayName("名称")       // 设置显示名
    .description("设备的名称") // 设置描述
    .placeholder("请输入")     // 设置占位符
    .length(1, 50)            // 添加长度验证
    .setDefaultValue("默认")   // 设置默认值
```

---

## ConfigItem 类型速查

### TextConfigItem — 文本输入

```java
new TextConfigItem(key, required)
new TextConfigItem(key, required, defaultValue)
    .displayName(String)
    .placeholder(String)
    .description(String)
    .length(minLength, maxLength)     // 添加 TextLengthValidator
    .setDefaultValue(String)
```

### NumericConfigItem — 数值输入 (Double)

```java
new NumericConfigItem(key, required)
new NumericConfigItem(key, required, 500.0)  // Double 默认值
new NumericConfigItem(key, required, 500.0f) // float 便利构造，自动转 Double
    .range(minValue, maxValue)        // 添加 NumericRangeValidator
```

接受 `Double`, `Integer`, `Long`, `Float`, `BigDecimal`, 可解析的 `String`。

### FloatConfigItem — 浮点数输入 (Float)

```java
new FloatConfigItem(key, required)
new FloatConfigItem(key, required, 3.14f)
    .range(minValue, maxValue)
```

### ShortConfigItem — 短整数输入 (Short)

```java
new ShortConfigItem(key, required)
new ShortConfigItem(key, required, (short)10)
new ShortConfigItem(key, required, 10)  // int 自动转 Short
    .range(minValue, maxValue)
```

字段类型为 `"integer"`（注意与 NumericConfigItem 的 `"number"` 不同）。

### BooleanConfigItem — 布尔开关

```java
new BooleanConfigItem(key, required)
new BooleanConfigItem(key, required, true)
```

自动添加 `BooleanValidator`。

### EnumConfigItem — 单选下拉

```java
new EnumConfigItem(key, required)
new EnumConfigItem(key, required, "default_value")
    .addOption("value1", "显示标签1")
    .addOption("value2", "显示标签2")
    .addOptions(Map.of("v1", "标签1", "v2", "标签2"))
    .buildValidator()                 // 推荐调用：添加 StringEnumValidator
    .displayName("选择项")
```

**注意**：EnumConfigItem 的 `validate()` 方法重写了父类实现，内置了选项合法性检查（空字符串视为 null，不在 validValues 中的值报错并显示友好的 label）。`buildValidator()` 会额外添加 `StringEnumValidator` 到验证器列表，属于防御性编程。

### DynamicEnumConfigItem — 动态选项下拉

```java
new DynamicEnumConfigItem(key, required, () -> {
    // 每次渲染时调用，返回当前可用的选项
    Map<String, String> ports = new LinkedHashMap<>();
    for (SerialPort port : SerialPort.getCommPorts()) {
        ports.put(port.getSystemPortName(), port.getDescriptivePortName());
    }
    return ports;
})
    .caseSensitive(false)             // 选项匹配是否区分大小写
    .displayName("串口")
```

选项在运行时动态生成（如可用串口列表）。验证时也会重新调用 supplier。

### SchemaConfigItem — 嵌套对象 / Schema 引用

```java
// 方式 1：直接传入 ConfigSchema（支持 Builder 定制默认值）
new SchemaConfigItem("comm_settings", true,
    SerialCommConfigSchema.builder()
        .baudrate(BaudRate.BAUD_115200)
        .build()
        .createSchema())
    .displayName("通讯配置")

// 方式 2：引用 Provider 类（反射创建，使用标准默认值）
new SchemaConfigItem("comm_settings", true, SerialCommConfigSchema.class)
    .displayName("通讯配置")

// 方式 3：扁平化扩展（字段提升到父级，不产生嵌套对象）
new SchemaConfigItem("comm_settings", true, SerialCommConfigSchema.class)
    .extend()
```

嵌套 schema 的 i18n 翻译自动从定义方 namespace 获取。

### ArrayConfigItem — 多选列表

```java
new ArrayConfigItem<String>(key, required)
new ArrayConfigItem<String>(key, required, "string")  // 指定元素类型
new ArrayConfigItem<>(key, required, Arrays.asList("a", "b"))
    .addOption("opt1", "选项1")
    .addOption("opt2", "选项2")
    .size(1, 5)                       // 最少选 1 个，最多选 5 个
```

### YamlConfigItem — 只读 YAML 显示

```java
new YamlConfigItem("config_summary")
    .setValue(configMap)               // Map → YAML 字符串，用于最终确认步骤
    .displayName("配置摘要")
```

始终 `required=false`, `readOnly=true`。不参与验证。

---

## Schema 默认值最佳实践

### 原则 1：区分 schema 定义的默认值与业务默认值

| 类型 | 谁设置 | 怎么设置 | 示例 |
|------|--------|----------|------|
| **Schema 默认值** | ConfigSchemaProvider | 构造函数 / Builder | 波特率 9600, 超时 500ms |
| **业务默认值** | ConfigFlow | `setDefaultValue()` | 设备名称 "我的设备", 从站 ID 1 |

Schema 默认值是**协议层面**的通用默认值，所有使用该 schema 的集成共享。
业务默认值是**特定集成**的默认值，可能因设备型号而异。

```java
// ✅ 正确：Schema 默认值在 ConfigSchemaProvider 中定义
// SerialCommConfigSchema 中：
.addField(new EnumConfigItem("baudrate", true, "9600")  // 协议通用默认值
    .addOptions(BaudRate.toMap())
    .buildValidator())

// ✅ 正确：业务默认值在 ConfigFlow 中设置
// GassensorConfigFlow 中：
.addField(new TextConfigItem("name", true)
    .setDefaultValue("气体传感器")  // 特定集成的默认设备名
    .length(1, 50))
```

### 原则 2：使用枚举类管理选项和默认值

当多个地方引用同一组选项时（如 CommConfigSchema 和单元测试），使用枚举类统一管理：

```java
// 1. 定义枚举（value = option key, label = 回退显示文本）
public enum BaudRate {
    BAUD_9600("9600", "9600"),
    BAUD_115200("115200", "115200");

    private final String value;
    private final String label;

    BaudRate(String value, String label) { this.value = value; this.label = label; }
    public String getValue() { return value; }
    public String getLabel() { return label; }

    public static Map<String, String> toMap() {
        Map<String, String> map = new LinkedHashMap<>();
        for (BaudRate br : values()) { map.put(br.value, br.label); }
        return map;
    }
}

// 2. 在 schema 中使用
.addField(new EnumConfigItem("baudrate", true, BaudRate.BAUD_9600.getValue())
    .addOptions(BaudRate.toMap())
    .buildValidator())

// 3. 在 ConfigFlow 中引用默认值
SerialCommConfigSchema.builder()
    .baudrate(BaudRate.BAUD_115200)  // 类型安全
    .build()
    .createSchema()
```

### 原则 3：无参构造 vs Builder

| 场景 | 使用方式 | 原因 |
|------|----------|------|
| 标准默认值 | `new XxxSchema().createSchema()` | 简单，向后兼容 |
| 自定义默认值 | `XxxSchema.builder().xxx().build().createSchema()` | 需要特定配置 |

```java
// Pattern A：直接使用（ConfigFlow 步骤中）
return showForm("comm_config",
    new SerialCommConfigSchema().createSchema(), errors);

// Pattern A + Builder：ConfigFlow 需要非标准默认值
return showForm("comm_config",
    SerialCommConfigSchema.builder()
        .baudrate(BaudRate.BAUD_115200)
        .timeout(1000)
        .build()
        .createSchema(), errors);

// Pattern B：通过 SchemaConfigItem 引用（其他 schema 中嵌套使用）
.addField(new SchemaConfigItem("serial_settings", true,
    SerialCommConfigSchema.class))  // 反射创建，无参构造

// Pattern B + Builder：嵌套但需要定制
.addField(new SchemaConfigItem("serial_settings", true,
    SerialCommConfigSchema.builder()
        .baudrate(BaudRate.BAUD_57600)
        .build()
        .createSchema()))
```

### 原则 4：DynamicEnumConfigItem 的默认值

动态选项（如可用串口列表）不能硬编码默认值，因为选项在运行时才确定：

```java
// DynamicEnumConfigItem 的 defaultValue 应为 null 或已知存在的值
new DynamicEnumConfigItem("serial_port", true, null, () -> getAvailablePorts())
    .displayName("串口")

// 如果需要默认选中第一个可用项，在 showForm 之前处理：
Map<String, Object> prefilled = new HashMap<>();
Map<String, String> ports = getAvailablePorts();
if (!ports.isEmpty()) {
    prefilled.put("serial_port", ports.keySet().iterator().next());
}
```

---

## ConfigSchemaProvider 与可复用 Schema

`ConfigSchemaProvider` 是定义可复用 Schema 的接口。当一个 Schema 被多个集成共用时（如 SerialCommConfigSchema 被 14+ 集成使用），应实现此接口。

### 实现 ConfigSchemaProvider

```java
public class SerialCommConfigSchema implements ConfigSchemaProvider {

    // 实例级默认值字段
    private final BaudRate defaultBaudrate;
    private final int defaultTimeout;

    // 无参构造 → 标准默认值（向后兼容，被 14+ 集成通过 SchemaConfigItem 引用）
    public SerialCommConfigSchema() {
        this.defaultBaudrate = BaudRate.BAUD_9600;
        this.defaultTimeout = 500;
    }

    // Builder 构造 → 自定义默认值
    private SerialCommConfigSchema(Builder builder) {
        this.defaultBaudrate = builder.baudrate != null ? builder.baudrate : BaudRate.BAUD_9600;
        this.defaultTimeout = builder.timeout > 0 ? builder.timeout : 500;
    }

    @Override
    public String getI18nKeyPrefix() {
        return "config_schemas.serial_comm";
    }

    @Override
    public ConfigSchema createSchema() {
        ConfigSchema schema = new ConfigSchema()
            .addField(new EnumConfigItem("baudrate", true, defaultBaudrate.getValue())
                .displayName("波特率")
                .addOptions(BaudRate.toMap())
                .buildValidator())
            .addField(new NumericConfigItem("timeout", true, (double) defaultTimeout)
                .displayName("超时(ms)")
                .range(100, 60000));
            // ... 其余字段
        schema.initI18n(this);  // ← 必须在末尾调用
        return schema;
    }

    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private BaudRate baudrate;
        private int timeout;

        public Builder baudrate(BaudRate baudrate) { this.baudrate = baudrate; return this; }
        public Builder timeout(int timeout) { this.timeout = timeout; return this; }
        public SerialCommConfigSchema build() { return new SerialCommConfigSchema(this); }
    }
}
```

### 关键要求

1. **`createSchema()` 末尾必须调用 `schema.initI18n(this)`** — 初始化 i18n 解析能力
2. **`getI18nKeyPrefix()` 返回翻译前缀** — 如 `"config_schemas.serial_comm"`
3. **无参构造保持向后兼容** — SchemaConfigItem 反射创建时使用无参构造
4. **pom.xml 配置 MANIFEST 条目** — coordinate 自动检测依赖此条目

```xml
<!-- pom.xml maven-assembly-plugin 配置 -->
<archive>
  <manifestEntries>
    <Ecat-Artifact-Id>${project.artifactId}</Ecat-Artifact-Id>
  </manifestEntries>
</archive>
```

---

## Builder 模式规范

当 ConfigSchemaProvider 需要支持自定义默认值时，使用内部 Builder 类。

### Builder 编写规则

```java
public static class Builder {
    // 字段初始值为 null / 0（表示未设置）
    private BaudRate baudrate;
    private int timeout;

    // setter 方法：枚举类型用枚举，数值用原始类型
    public Builder baudrate(BaudRate baudrate) { this.baudrate = baudrate; return this; }
    public Builder timeout(int timeout) { this.timeout = timeout; return this; }

    // build() 方法：未设置的字段使用标准默认值
    public SerialCommConfigSchema build() {
        return new SerialCommConfigSchema(this);
    }
}
```

### 私有构造函数处理未设置的字段

```java
private SerialCommConfigSchema(Builder builder) {
    // 未设置 → 标准默认值；已设置 → 使用 builder 值
    this.defaultBaudrate = builder.baudrate != null ? builder.baudrate : BaudRate.BAUD_9600;
    this.defaultTimeout = builder.timeout > 0 ? builder.timeout : 500;
}
```

### 已有 CommConfigSchema

| Schema | 定义模块 | Builder 支持 | 枚举类 |
|--------|----------|-------------|--------|
| SerialCommConfigSchema | serial | 有 | BaudRate, DataBits, StopBits, Parity, FlowControl |
| ModbusTcpCommConfigSchema | modbus | 有 | ModbusProtocol |
| ModbusRtuCommConfigSchema | modbus | 无 | 包装 serial + slave_id |
| TcpClientCommConfigSchema | tcp | 无 | 无枚举 |

---

## i18n 翻译

ConfigFlow 的 i18n 采用三层 Fallback 策略：

```
优先级 1: flow namespace（使用方覆盖）
  → AbstractConfigFlow.getFieldDisplayName() / getOptionDisplayName() / getFieldDescription()
  → key: config_flow.step_{stepId}.items.{fieldKey}.display_name
  → 场景：特定集成覆盖通用字段名

优先级 2: schema 自解析（定义方翻译）
  → ConfigSchema.resolveDisplayName() / resolveOptionLabel() / resolveDescription()
  → key: config_schemas.{schema_name}.{fieldKey}.display_name
  → 场景：SerialCommConfigSchema 提供 "Baud Rate" 等通用翻译
  → 所有使用方自动获得，无需重复定义

优先级 3: Java 硬编码（最终回退）
  → item.getDisplayName() / 枚举 label
  → 场景：无 strings.json 时的保底
```

### 翻译文件位置

| 层级 | 文件位置 | 前缀 |
|------|----------|------|
| flow namespace | `{集成}/src/main/resources/strings.json` | `config_flow.step_{stepId}.*` |
| schema namespace | `{定义方集成}/src/main/resources/strings.json` | `config_schemas.{schema_name}.*` |
| locale 翻译 | `{集成}/src/main/resources/i18n/zh_CN.json` | 同上 |

### flow namespace strings.json 结构

```json
{
  "config_flow": {
    "step_device_config": {
      "display_name": "设备配置",
      "items": {
        "name": { "display_name": "设备名称", "placeholder": "请输入设备名称" },
        "model": {
          "display_name": "设备型号",
          "options": { "model_a": "型号 A", "model_b": "型号 B" }
        }
      }
    }
  }
}
```

### schema namespace strings.json 结构

```json
{
  "config_schemas": {
    "serial_comm": {
      "baudrate": {
        "display_name": "Baud Rate",
        "description": "Communication speed in bits per second",
        "options": { "9600": "9600 bps", "115200": "115.2K bps" }
      }
    }
  }
}
```

### 翻译规划原则

- **步骤标题**、**业务特有字段名**、**placeholder** → flow namespace（使用方集成）
- **通用通讯字段名**、**技术选项标签**、**字段描述** → schema namespace（定义方集成）
- 优先依赖 schema 层翻译，只在需要不同措辞时才在 flow 层覆盖
- 详细规划指南参见 `.claude/skills/maintain-vertical-skill/skills/config-flow/SKILL.md`

---

## FlowContext 数据管理

`FlowContext` 是跨步骤的单一数据源。

### entryData vs stepInputs

| 数据 | 用途 | 存取方式 |
|------|------|----------|
| `entryData` | 业务数据（写入 ConfigEntry） | `context.getEntryData().put(key, value)` |
| `stepInputs` | 步骤表单输入（用于回退导航） | 由 `handleStep()` 自动管理 |
| `entryTitle` | ConfigEntry 标题 | `context.setEntryTitle(title)` |
| `entryUniqueId` | 业务唯一标识 | `context.setEntryUniqueId(id)` |

### 数据保存模式

```java
// 在步骤 handler 中保存业务数据
private ConfigFlowResult stepCommConfig(Map<String, Object> userInput) {
    // ... 验证通过后
    context.getEntryData().put("comm_settings", userInput);
    // ...
}
```

### 生成唯一 ID

```java
// setEntryUniqueId 会自动检查唯一性（不与现有 ConfigEntry 冲突）
String uniqueId = vendor + "_" + model + "_" + serialNumber;
context.setEntryUniqueId(uniqueId);

// 重配置模式下跳过唯一性检查
context.setEntryUniqueId(uniqueId, true);
```

### 最终步骤模式

```java
private ConfigFlowResult stepFinalConfirm(Map<String, Object> userInput) {
    if (userInput == null || userInput.isEmpty()) {
        // 构建确认摘要
        Map<String, Object> summary = new LinkedHashMap<>(context.getEntryData());
        return showForm("final_confirm",
            new ConfigSchema()
                .addField(new YamlConfigItem("config_summary").setValue(summary))
                .addField(new BooleanConfigItem("confirmed", true)
                    .displayName("确认创建配置")),
            new HashMap<>());
    }

    // 确认后：设置标题和唯一 ID
    String name = (String) context.getEntryData().get("name");
    context.setEntryTitle(name);
    context.setEntryUniqueId(generateUniqueId());

    return createEntry();  // 创建 ConfigEntry
}
```

---

## 重配置与发现

### 重配置步骤

重配置时 `FlowContext` 已包含原有 ConfigEntry 的数据：

```java
registerStepReconfigure("reconfigure", "重新配置设备", (userInput, ctx) -> {
    // ctx.getEntryData() 包含原有配置
    // 用户可以修改部分字段，其余保持不变
    return stepReconfigure(userInput);
});
```

**注意**：重配置时不要改变设备类型（如从串口改为 Modbus），这会导致数据结构不兼容。

### Discovery 多源接入（IMPORT_FLOW / ZEROCONF / MQTT）

除 USER（人工向导）/ RECONFIGURE 外，ConfigFlow 支持**多源设备发现**——外部（同进程 SDK / 协议 broker）触发 flow，跳过型号选择等前置 step、直达连接配置。三源共用 core **一个入口** `ConfigFlowService.startDiscoveryFlow(coordinate, source, payload)`（`source` 参数区分类型）：

| Source | 触发方 | payload（core 不透明） | 典型场景 |
|--------|--------|------------------------|----------|
| `IMPORT_FLOW` | 同进程 SDK | `ImportFlowPayload{coordinate, version, data}` | 外部已知设备身份，程序化接入 |
| `ZEROCONF` | integration-zeroconf broker（mDNS） | `ZeroconfDiscoveryPayload`（集成侧定义） | 网络自动发现 |
| `MQTT` | integration-mqtt broker | `MqttDiscoveryPayload`（集成侧定义） | MQTT 发现 |

注册：`registerStepDiscovery(SourceType, DiscoveryHandler<P>)`（每发现源最多 1 个）；core 对 payload 完全不透明，data 格式由各集成自定（如 saimosen v1 = `class|model|sn|name`）。

> discovery 机制详情（统一入口用法、执行统一 `drive`/`applyResult`、重复发现去重 clean ABORT + `AbortReason`、peer broker 模型、HA 对齐理念、参考实现）见同目录 **[DISCOVERY.md](DISCOVERY.md)**。

### 保留额外字段

如果 ConfigEntry 有手动添加的字段（如 register_mappings），重配置时需要保留：

```java
private List<Map<String, Object>> preservedMappings;

private ConfigFlowResult stepReconfigure(Map<String, Object> userInput) {
    // 进入重配置前保存
    Object mappings = context.getEntryData().get("register_mappings");
    if (mappings instanceof List) {
        preservedMappings = (List<Map<String, Object>>) mappings;
    }
    // ...
}

private ConfigFlowResult stepFinalConfirm(Map<String, Object> userInput) {
    // final_confirm 阶段恢复
    if (preservedMappings != null) {
        context.getEntryData().put("register_mappings", preservedMappings);
    }
    return createEntry();
}
```

---

## ConfigFlowResult 返回值

每个步骤 handler 返回 `ConfigFlowResult`，由 `AbstractConfigFlow` 提供的工厂方法创建：

### AbstractConfigFlow 的工厂方法（步骤 handler 使用）

| 方法 | 用途 | 使用时机 |
|------|------|----------|
| `showForm(stepId, schema, errors)` | 显示表单 | 需要用户输入时 |
| `createEntry()` | 创建/更新 ConfigEntry | 最后一步确认后。内部根据 sourceType 决定调用 `ConfigFlowResult.createEntry()` (USER) 或 `ConfigFlowResult.updateEntry()` (RECONFIGURE) |
| `abort(reason)` | 中止流程 | 发生不可恢复错误时 |

### ConfigFlowResult 静态工厂方法（框架内部使用）

| 方法 | 返回的 ResultType | 描述 |
|------|-------------------|------|
| `showForm(stepId, schema, errors, context)` | `SHOW_FORM` | 显示表单 |
| `createEntry(context)` | `CREATE_ENTRY` | 创建新 ConfigEntry |
| `createEntry(entry, context)` | `CREATE_ENTRY` | 带已有 ConfigEntry 创建 |
| `updateEntry(entry, context)` | `CREATE_ENTRY` | 更新已有 ConfigEntry（重配置） |
| `abort(reason)` | `ABORT` | 中止流程 |
| `removeEntry(context)` | `REMOVE_ENTRY` | 删除 ConfigEntry |

步骤 handler **不要**直接调用 `ConfigFlowResult` 的静态方法，始终使用 `showForm()` / `createEntry()` / `abort()`。

> **去重 ABORT 的 reason 词表**：框架 R5 去重命中时用 `AbortReason.ALREADY_CONFIGURED`（entry 已存在）/ `ALREADY_IN_PROGRESS`（同 uniqueId 活跃 flow）——由 `ConfigFlowService.drive` 内部产生，集成无需关心；集成自身 `abort(reason)` 用自定义 reason 串。

---

## 完整示例

以下是一个完整的 4 步 ConfigFlow（用户入口 → 设备配置 → 通讯配置 → 最终确认）：

```java
public class MyConfigFlow extends AbstractConfigFlow {

    public MyConfigFlow() {
        super();
        registerStepUser("user", "配置我的设备", this::stepUser);
        registerStepReconfigure("reconfigure", "重新配置", this::stepReconfigure);
        registerStep("device_config", this::stepDeviceConfig, "设备配置");
        registerStep("comm_config", this::stepCommConfig, "通讯配置");
        registerStep("final_confirm", this::stepFinalConfirm, "确认配置");
    }

    // ========== 步骤处理器 ==========

    private ConfigFlowResult stepUser(Map<String, Object> userInput, FlowContext ctx) {
        if (userInput == null || userInput.isEmpty()) {
            return showForm("user", new ConfigSchema()
                .addField(new TextConfigItem("welcome", false)
                    .displayName("欢迎配置向导")),
                new HashMap<>());
        }
        return showForm("device_config", createDeviceSchema(), new HashMap<>());
    }

    private ConfigFlowResult stepReconfigure(Map<String, Object> userInput, FlowContext ctx) {
        // 重配置：从原有数据预填表单
        return showForm("device_config", createDeviceSchema(), new HashMap<>());
    }

    private ConfigFlowResult stepDeviceConfig(Map<String, Object> userInput) {
        if (userInput == null || userInput.isEmpty()) {
            return showForm("device_config", createDeviceSchema(), new HashMap<>());
        }
        ConfigSchema schema = createDeviceSchema();
        Map<String, Object> errors = schema.validate(userInput);
        if (!errors.isEmpty()) {
            return showForm("device_config", schema, errors);
        }
        context.getEntryData().putAll(userInput);
        return showForm("comm_config",
            new SerialCommConfigSchema().createSchema(), new HashMap<>());
    }

    private ConfigFlowResult stepCommConfig(Map<String, Object> userInput) {
        if (userInput == null || userInput.isEmpty()) {
            return showForm("comm_config",
                new SerialCommConfigSchema().createSchema(), new HashMap<>());
        }
        ConfigSchema schema = new SerialCommConfigSchema().createSchema();
        Map<String, Object> errors = schema.validate(userInput);
        if (!errors.isEmpty()) {
            return showForm("comm_config", schema, errors);
        }
        context.getEntryData().put("comm_settings", userInput);
        return showForm("final_confirm", createFinalSchema(), new HashMap<>());
    }

    private ConfigFlowResult stepFinalConfirm(Map<String, Object> userInput) {
        if (userInput == null || userInput.isEmpty()) {
            return showForm("final_confirm", createFinalSchema(), new HashMap<>());
        }
        Boolean confirmed = (Boolean) userInput.get("confirmed");
        if (confirmed == null || !confirmed) {
            Map<String, Object> errors = new HashMap<>();
            errors.put("confirmed", "请确认创建配置");
            return showForm("final_confirm", createFinalSchema(), errors);
        }

        String name = (String) context.getEntryData().get("name");
        context.setEntryTitle(name);
        context.setEntryUniqueId("my_device_" + System.currentTimeMillis());
        return createEntry();
    }

    // ========== Schema 工厂方法 ==========

    private ConfigSchema createDeviceSchema() {
        return new ConfigSchema()
            .addField(new TextConfigItem("name", true)
                .displayName("设备名称")
                .placeholder("请输入设备名称")
                .length(1, 50)
                .setDefaultValue("我的设备"))
            .addField(new TextConfigItem("sn", false)
                .displayName("序列号"));
    }

    private ConfigSchema createFinalSchema() {
        return new ConfigSchema()
            .addField(new YamlConfigItem("summary")
                .setValue(context.getEntryData())
                .displayName("配置摘要"))
            .addField(new BooleanConfigItem("confirmed", true)
                .displayName("确认创建配置"));
    }
}
```

---

## 相关文档

| 文档 | 位置 |
|------|------|
| ConfigFlow 垂直领域维护指南 | `.claude/skills/maintain-vertical-skill/skills/config-flow/SKILL.md` |
| ConfigFlow 升级指南 | `.claude/skills/upgrade-config-flow/SKILL.md` |
| 多源设备发现（discovery 机制 / 执行统一 drive·applyResult / 去重 AbortReason） | 同目录 [DISCOVERY.md](DISCOVERY.md) |
