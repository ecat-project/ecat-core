# Config Schema 配置定义

Config Schema 定义配置向导中表单的字段、类型、校验规则和默认值。可内嵌在 Flow 步骤中，也可定义为可复用的 `ConfigSchemaProvider` 供其他集成引用。

相关文档：[Config Flow 配置向导](config-flow.md) | [I18n 国际化系统](i18n.md)

---

## ConfigSchema 与 ConfigSchemaProvider

```java
// 内嵌方式：在步骤处理器中直接创建
ConfigSchema schema = new ConfigSchema()
    .addField(new TextConfigItem("device_name", true))
    .addField(new NumericConfigItem("timeout", false, 30.0));

// 可复用方式：实现 ConfigSchemaProvider 接口
// 注：ConfigSchemaProvider 不继承 AbstractConfigFlow，无法使用 getFieldDisplayName()，
//     因此这里的 displayName 是回退默认值，可在 Flow 中通过 getFieldDisplayName() 覆盖
public class SailheroDeviceConfigSchema implements ConfigSchemaProvider {
    @Override
    public ConfigSchema createSchema() {
        return new ConfigSchema()
            .addField(new TextConfigItem("id", true).displayName("设备ID").length(1, 50))
            .addField(new TextConfigItem("name", true).displayName("设备名称").length(1, 50));
    }
}
```

---

## 字段类型

| 类 | getFieldType() | 说明 | 关键方法 |
|---|---|---|---|
| `TextConfigItem` | `"string"` | 文本输入 | `.length(min, max)` |
| `NumericConfigItem` | `"number"` | 数值（double） | `.range(min, max)` |
| `FloatConfigItem` | `"number"` | 浮点数 | `.range(min, max)` |
| `ShortConfigItem` | `"integer"` | 短整数 | `.range(min, max)` |
| `BooleanConfigItem` | `"boolean"` | 布尔开关 | (none - auto-validates via BooleanValidator) |
| `EnumConfigItem` | `"select"` | 下拉选择 | `.addOptions(map).buildValidator()` |
| `DynamicEnumConfigItem` | `"dynamic_enum"` | 动态下拉 | 构造函数传入 `Supplier<Map<String, String>>` |
| `ArrayConfigItem` | `"array"` | 数组/多选 | `.size(min, max)` |
| `SchemaConfigItem` | `"schema"` | 嵌套/引用 Schema | 见下方 |

### 构造函数

```java
new TextConfigItem("device_name", true)                    // key, required
new TextConfigItem("device_name", true, "default_name")   // key, required, defaultValue
new NumericConfigItem("timeout", false, 30.0)              // key, required, defaultValue (double)
new BooleanConfigItem("enabled", true, true)               // key, required, defaultValue
```

### ConfigItemBuilder 工厂方法

```java
// ConfigItemBuilder 提供静态工厂方法简化创建
import static com.ecat.core.ConfigFlow.ConfigItem.ConfigItemBuilder.*;

text("device_name", true)
numeric("timeout", false, 30.0)
floatItem("temperature", true, 25.0f)
shortItem("priority", true, (short) 5)
booleanItem("enabled", true, true)
enumItem("protocol", true, "TCP")
array("channels", false, "string")
stringArray("features", false, Arrays.asList("a", "b"))
```

### EnumConfigItem

Enum 类型必须调用 `buildValidator()` 构建校验器：

```java
String stepId = "protocol_config";
new EnumConfigItem("protocol", true, "TCP")
    .displayName(getFieldDisplayName(stepId, "protocol"))
    .addOptions(mapOf(
        "TCP", "TCP",
        "UDP", "UDP",
        "Serial", "串口"
    ))
    .buildValidator();
```

### DynamicEnumConfigItem

选项在运行时动态获取（如系统可用串口）。构造函数直接接收 `Supplier<Map<String, String>>`：

```java
new DynamicEnumConfigItem("serial_port", true, new Supplier<Map<String, String>>() {
    @Override
    public Map<String, String> get() {
        Map<String, String> ports = new LinkedHashMap<>();
        ports.put("COM1", "COM1");
        ports.put("COM2", "COM2");
        return ports;
    }
});
```

每次调用 `get()` 时会重新获取选项列表，适合需要动态刷新的场景（如重新扫描串口）。

---

## SchemaConfigItem — 嵌套与引用

### 内嵌模式

```java
ConfigSchema commSchema = new ConfigSchema()
    .addField(new EnumConfigItem("baudrate", true, "9600").buildValidator());

new SchemaConfigItem("comm_settings", true, commSchema)
    .displayName("通讯配置");
```

### 引用模式（跨集成复用）

```java
// 引用串口集成的 Schema
new SchemaConfigItem("serial_config", true, SerialCommConfigSchema.class)
    .displayName("串口配置");
```

### 展开模式（扁平化）

将嵌套 Schema 的字段展开到父级：

```java
new SchemaConfigItem("serial_config", true, SerialCommConfigSchema.class)
    .displayName("串口配置")
    .extend();  // 子字段直接出现在父表单中
```

校验时递归验证所有嵌套字段。

---

## 校验

框架自动执行两种校验：

1. **required 检查** — 必填字段不能为空/null
2. **类型检查** — 值类型必须匹配字段类型

额外校验通过 `validators` 添加：

```java
new TextConfigItem("device_name", true)
    .length(1, 100)  // 添加 TextLengthValidator

new NumericConfigItem("timeout", true, 30.0)
    .range(1.0, 300.0)  // 添加范围校验
```

手动校验：

```java
schema.validate(data);  // 返回 Map<String, String>（key → 错误信息），空 Map 表示校验通过
```

---

## 国际化

### 约定式 Key（自动）

通过 `AbstractConfigFlow` 的方法自动构建 Key：

| 内容 | Key 模式 | 方法 |
|------|---------|------|
| 步骤名称 | `config_flow.step_{stepId}.display_name` | `getStepDisplayName(stepId)` |
| 字段名称 | `config_flow.step_{stepId}.items.{key}.display_name` | `getFieldDisplayName(stepId, key)` |
| 字段占位符 | `config_flow.step_{stepId}.items.{key}.placeholder` | `getFieldPlaceholder(stepId, key)` |
| 字段描述 | `config_flow.step_{stepId}.items.{key}.description` | `getFieldDescription(stepId, key)` |
| 选项标签 | `config_flow.step_{stepId}.items.{key}.options.{value}` | `getOptionDisplayName(stepId, key, value)` |

### 常量式 Key（自定义消息）

```java
public final class DemoConfigFlowI18n {
    public static final String CUSTOM = "config_flow.custom";
    public static final String CHANNEL_TEST_SUCCESS = CUSTOM + ".channel_test_success";
}
```

使用：`t(DemoConfigFlowI18n.CHANNEL_TEST_SUCCESS)`

### strings.json 结构

```json
{
  "config_flow": {
    "step_user": {
      "display_name": "用户配置",
      "items": {
        "device_name": {
          "display_name": "设备名称",
          "placeholder": "请输入设备名称",
          "description": "设备显示名称"
        },
        "protocol": {
          "display_name": "通讯协议",
          "options": {
            "TCP": "TCP/IP",
            "Serial": "串口通讯"
          }
        }
      }
    },
    "custom": {
      "channel_test_success": "通道 {0} 测试成功"
    }
  }
}
```

---

## 完整示例

### sailhero 集成

**Flow 类**（4 步向导）：

```java
public class SailheroConfigFlow extends AbstractConfigFlow {
    public SailheroConfigFlow() {
        super(null);
        registerStepUser("user", "配置设备", this::stepUserEntry);
        registerStep("device_config", this::stepDeviceConfig, "设备配置");
        registerStep("comm_config", this::stepCommConfig, "通讯配置");
        registerStep("final_confirm", this::stepFinalConfirm, "确认");
    }

    private ConfigFlowResult stepDeviceConfig(Map<String, Object> userInput) {
        // 复用 SailheroDeviceConfigSchema（内部引用了 SerialCommConfigSchema）
        ConfigSchema schema = new SailheroDeviceConfigSchema().createSchema();
        // 为所有字段设置 i18n 显示名
        schema.getFields().forEach(f -> f.displayName(getFieldDisplayName("device_config", f.getKey())));
        return showForm("device_config", schema, new HashMap<>());
    }

    private ConfigFlowResult stepCommConfig(Map<String, Object> userInput) {
        // 复用串口集成的 Schema
        ConfigSchema schema = new SerialCommConfigSchema().createSchema();
        return showForm("comm_config", schema, new HashMap<>());
    }

    private ConfigFlowResult stepFinalConfirm(Map<String, Object> userInput) {
        getData().put("title", getData().getOrDefault("device_name", "Sailhero"));
        return createEntry();
    }
}
```

**可复用 Schema**（引用串口集成）：

```java
public class SailheroDeviceConfigSchema implements ConfigSchemaProvider {
    @Override
    public ConfigSchema createSchema() {
        return new ConfigSchema()
            .addField(new TextConfigItem("id", true).displayName("设备ID").length(1, 50))
            .addField(new TextConfigItem("name", true).displayName("设备名称").length(1, 50))
            .addField(new EnumConfigItem("class", false, "air.monitor.pm")
                .displayName("设备型号").addOptions(mapOf(
                    "air.monitor.pm", "PM100",
                    "air.monitor.mh", "MH100"
                )).buildValidator())
            // 引用串口集成的通讯配置 Schema
            .addField(new SchemaConfigItem("comm_settings", true, SerialCommConfigSchema.class)
                .displayName("通讯配置"));
    }
}
```

### ecat-config.yml 声明依赖

```yaml
requires_core: ^1.0.0
dependencies:
  - artifactId: integration-serial
    groupId: com.ecat
    version: ^1.0.0
```

---

## 参考实现

| 集成 | 文件 | 特点 |
|------|------|------|
| **demo-config-flow** | `DemoConfigFlowNew.java` | 展示全部字段类型 |
| **sailhero** | `SailheroConfigFlow.java` + `SailheroDeviceConfigSchema.java` | Schema 复用 |
| **serial** | `SerialCommConfigSchema.java` | DynamicEnum + 跨集成复用 |
