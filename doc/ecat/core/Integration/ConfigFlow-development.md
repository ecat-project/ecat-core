# Config Flow 开发指南

## 目录

1. [概述](#概述)
2. [架构设计](#架构设计)
3. [核心组件](#核心组件)
4. [快速开始](#快速开始)
5. [ConfigItem 类型系统](#configitem-类型系统)
6. [I18n 国际化](#i18n-国际化)
7. [API 层集成](#api-层集成)
8. [前端集成](#前端集成)
9. [完整示例](#完整示例)
10. [最佳实践](#最佳实践)
11. [常见问题](#常见问题)

---

## 概述

Config Flow 是 ECAT 的配置向导系统，用于引导用户完成设备的配置流程。采用分步表单的方式，支持多种字段类型和动态流程控制。

### 设计目标

- **模块化**: 支持多种设备类型的配置流程
- **可扩展**: 轻松添加新的字段类型和验证规则
- **国际化**: 完整的 i18n 支持
- **类型安全**: 强类型的 ConfigItem 系统

### 技术栈

| 层级 | 技术 |
|------|------|
| 后端核心 | Java 8+, Lombok, FastJSON2 |
| API 层 | EasyHttpServer |
| 前端 | Lit 3 (Web Components) |
| 构建 | Maven |
| 核心依赖 | ecat-core 1.0.1+ |

---

## 架构设计

### 分层架构

```
┌─────────────────────────────────────────────────────────┐
│                     前端 (Lit 3)                         │
│  flow-form.js → FieldRegistry → FieldRenderer           │
└─────────────────────────────┬───────────────────────────┘
                              │ HTTP API
┌─────────────────────────────▼───────────────────────────┐
│                   API 层 (ecat-core-api)                 │
│  ConfigFlowController → ConfigFlowService               │
│                      → SchemaConversionService           │
└─────────────────────────────┬───────────────────────────┘
                              │
┌─────────────────────────────▼───────────────────────────┐
│                   核心层 (ecat-core)                     │
│  AbstractConfigFlow → ConfigItemBuilder → ConfigItem    │
│                      → I18nProxy                        │
└─────────────────────────────────────────────────────────┘
                              │
┌─────────────────────────────▼───────────────────────────┐
│                   集成层 (Integration)                   │
│  XxxConfigFlow extends AbstractConfigFlow               │
└─────────────────────────────────────────────────────────┘
```

### 职责划分

| 层级 | 模块 | 职责 |
|------|------|------|
| **核心层** | ecat-core | 流程框架、类型系统、I18n 基础设施 |
| **API 层** | ecat-core-api | REST API、DTO 转换、通用 i18n 常量 |
| **集成层** | integration | 具体配置流程实现、业务 i18n 资源 |

---

## 核心组件

### 3.1 AbstractConfigFlow

配置流程的抽象基类，提供：
- 步骤隔离和导航
- 反射发现步骤方法
- 表单显示和验证
- I18n 约定方法

```java
public abstract class AbstractConfigFlow {
    // 核心字段
    protected final String flowId;
    protected final I18nProxy i18n;
    protected String currentStep;

    // I18n 约定方法
    public String getStepDisplayName(String stepId);
    public String getFieldDisplayName(String stepId, String fieldKey);
    public String getFieldPlaceholder(String stepId, String fieldKey);
    public String getFieldDescription(String stepId, String fieldKey);
    public String getOptionDisplayName(String stepId, String fieldKey, String optionValue);

    // 流程控制
    protected ConfigFlowResult show_form(String stepId, DynamicFormSchema schema, Map<String, Object> errors);
    protected ConfigFlowResult create_entry(Map<String, Object> data);
    protected ConfigFlowResult abort(String reason);
}
```

### 3.2 ConfigFlowResult

流程执行结果，包含三种类型：

| 类型 | 说明 | 使用场景 |
|------|------|---------|
| `SHOW_FORM` | 显示表单 | 需要用户输入 |
| `CREATE_ENTRY` | 创建配置条目 | 流程完成 |
| `ABORT` | 中止流程 | 发生错误 |

### 3.3 ConfigItem 类型系统

8 种 ConfigItem 类型与验证器对应：

| ConfigItem | fieldType | 前端 Renderer | 验证器 |
|------------|-----------|---------------|--------|
| `TextConfigItem` | `text` | `TextFieldRenderer` | `TextLengthValidator` |
| `NumericConfigItem` | `numeric` | `NumericFieldRenderer` | `NumericRangeValidator` |
| `FloatConfigItem` | `float` | `FloatFieldRenderer` | `FloatRangeValidator` |
| `ShortConfigItem` | `short` | `ShortFieldRenderer` | `ShortRangeValidator` |
| `BooleanConfigItem` | `boolean` | `BooleanFieldRenderer` | `BooleanValidator` |
| `EnumConfigItem` | `enum` | `EnumFieldRenderer` | `StringEnumValidator` |
| `ArrayConfigItem` | `array` | `ArrayFieldRenderer` | `ListSizeValidator` |
| `ObjectConfigItem` | `object` | `ObjectFieldRenderer` | 递归验证 |

---

## 快速开始

### 4.1 创建配置流程类

```java
package com.ecat.integration.MyIntegration.flow;

import com.ecat.core.ConfigFlow.AbstractConfigFlow;
import com.ecat.core.ConfigFlow.ConfigFlowResult;
import com.ecat.core.ConfigFlow.DynamicFormSchema;
import com.ecat.core.ConfigFlow.FormContext;
import com.ecat.core.Utils.DynamicConfig.ConfigDefinition;

import java.util.*;

import static com.ecat.core.ConfigFlow.ConfigItem.ConfigItemBuilder.*;

/**
 * My Integration 配置流程
 *
 * @author your-name
 */
public class MyConfigFlow extends AbstractConfigFlow {

    public static final String PROVIDER_COORDINATE = "com.ecat.integration:my-integration";

    public MyConfigFlow(String flowId) {
        super(flowId);
    }

    private ConfigFlowResult handleUserStep(Map<String, Object> userInput) {
        if (userInput == null || userInput.isEmpty()) {
            return show_form("user", this::generateUserSchema, new HashMap<>());
        }

        // 验证输入...

        // 进入下一步或完成
        return show_form("network", this::generateNetworkSchema, new HashMap<>());
    }

    private ConfigDefinition generateUserSchema(FormContext context) {
        String stepId = "user";
        ConfigDefinition configDef = new ConfigDefinition();

        com.ecat.core.ConfigFlow.ConfigItem.ConfigItemBuilder builder =
            new com.ecat.core.ConfigFlow.ConfigItem.ConfigItemBuilder();

        builder.add(text("username", true)
            .displayName(getFieldDisplayName(stepId, "username"))
            .placeholder(getFieldPlaceholder(stepId, "username"))
            .length(1, 50));

        builder.add(numeric("port", true, 502.0)
            .displayName(getFieldDisplayName(stepId, "port"))
            .range(1, 65535));

        return configDef.defineFlowItems(builder);
    }
}
```

### 4.2 创建 strings.json

```json
{
  "config_flow": {
    "step_user": {
      "display_name": "用户配置",
      "items": {
        "username": {
          "display_name": "用户名",
          "placeholder": "请输入用户名"
        },
        "port": {
          "display_name": "端口号"
        }
      }
    },
    "step_network": {
      "display_name": "网络配置"
    }
  }
}
```

---

## ConfigItem 类型系统

### 5.1 文本字段 (TextConfigItem)

```java
text("username", true)                    // 必填
    .displayName("用户名")
    .placeholder("请输入用户名")
    .description("用于登录的用户名")
    .length(1, 100);                       // 长度约束

text("description", false)                // 可选
    .displayName("描述");
```

### 5.2 数值字段 (NumericConfigItem)

```java
numeric("port", true, 502.0)              // 带默认值
    .displayName("端口号")
    .range(1, 65535);                      // 范围约束

numeric("timeout", false, 5000.0)
    .displayName("超时时间")
    .range(100, 60000);
```

### 5.3 浮点数字段 (FloatConfigItem)

```java
floatItem("temperature", true, 25.0f)
    .displayName("温度")
    .range(-40.0f, 100.0f);
```

### 5.4 短整数字段 (ShortConfigItem)

```java
shortItem("priority", true, (short) 5)
    .displayName("优先级")
    .range((short) 1, (short) 10);
```

### 5.5 布尔字段 (BooleanConfigItem)

```java
booleanItem("enabled", false, true)       // 可选，默认 true
    .displayName("启用");

booleanItem("confirmed", true, false)     // 必填，默认 false
    .displayName("确认配置");
```

### 5.6 枚举字段 (EnumConfigItem)

```java
enumItem("protocol", true, "TCP")         // 带默认值
    .displayName("协议类型")
    .addOption("TCP", "TCP - 网络协议")
    .addOption("UDP", "UDP - 网络协议")
    .addOption("Serial", "Serial - 串口协议")
    .buildValidator();                     // 必须调用以添加验证器

// 使用 Map 批量添加选项（i18n 由 SchemaConversionService 自动处理）
enumItem("device_type", true, "PLC")
    .displayName(getFieldDisplayName(stepId, "device_type"))
    .addOptions(mapOf(
        "PLC", "PLC控制器",
        "SENSOR", "传感器",
        "METER", "仪表",
        "GATEWAY", "网关"
    ))
    .buildValidator();
```

### 5.7 数组字段 (ArrayConfigItem)

```java
array("channels", false, Arrays.asList("ch1", "ch2"), "string")
    .displayName("通道列表");
```

### 5.8 对象字段 (ObjectConfigItem)

```java
object("config", false)
    .displayName("配置对象")
    .addItem(text("host", true).displayName("主机"))
    .addItem(numeric("port", true, 8080.0).displayName("端口"));
```

---

## I18n 国际化

### 6.1 设计原则

**约定大于配置** - 有固定模式的自动处理，无固定模式的定义常量。

| 场景 | 处理方式 | 示例 |
|------|---------|------|
| **有固定模式** | 约定自动拼接 key | step name, field name, options |
| **无固定模式** | 定义常量引用 | 特殊错误、特殊提示 |

### 6.2 Key 约定格式

| 内容 | Key 格式 |
|------|---------|
| 步骤名称 | `config_flow.step_{stepId}.display_name` |
| 字段名称 | `config_flow.step_{stepId}.items.{fieldKey}.display_name` |
| 字段占位符 | `config_flow.step_{stepId}.items.{fieldKey}.placeholder` |
| 字段描述 | `config_flow.step_{stepId}.items.{fieldKey}.description` |
| 选项标签 | `config_flow.step_{stepId}.items.{fieldKey}.options.{optionValue}` |
| 特殊消息 | `config_flow.custom.{customKey}` |

### 6.3 资源文件结构

#### API 层 (ecat-core-api)
```json
{
  "config_flow": {
    "action": {
      "next": "下一步",
      "previous": "上一步",
      "submit": "提交",
      "cancel": "取消",
      "test_connection": "测试连接"
    },
    "error": {
      "required": "{field} 是必需的",
      "invalid_format": "{field} 格式无效",
      "connection_failed": "连接失败",
      "test_failed": "测试失败: {reason}"
    },
    "message": {
      "test_success": "测试成功",
      "loading": "处理中..."
    }
  }
}
```

#### 集成层 (integration)
```json
{
  "config_flow": {
    "step_user": {
      "display_name": "用户配置",
      "items": {
        "username": {
          "display_name": "用户名",
          "placeholder": "请输入用户名",
          "description": "用于登录的用户名",
          "options": {
            "admin": "管理员",
            "user": "普通用户"
          }
        }
      }
    },
    "custom": {
      "channel_test_success": "通道 {0} 测试成功",
      "channel_test_failed": "通道 {0} 测试失败: {1}"
    }
  }
}
```

### 6.4 常量类定义

#### API 层常量 (ConfigFlowI18n.java)
```java
package com.ecat.integration.EcatCoreApiIntegration.ConfigFlow;

public final class ConfigFlowI18n {
    private ConfigFlowI18n() {}

    // Key 前缀
    public static final String PREFIX = "config_flow";
    public static final String ACTION = PREFIX + ".action";
    public static final String ERROR = PREFIX + ".error";
    public static final String MESSAGE = PREFIX + ".message";

    // Action
    public static final String ACTION_NEXT = ACTION + ".next";
    public static final String ACTION_SUBMIT = ACTION + ".submit";

    // Error
    public static final String ERROR_REQUIRED = ERROR + ".required";
    public static final String ERROR_CONNECTION_FAILED = ERROR + ".connection_failed";

    // Message
    public static final String MSG_TEST_SUCCESS = MESSAGE + ".test_success";
}
```

#### 集成层常量 (XxxConfigFlowI18n.java)
```java
package com.ecat.integration.XxxIntegration;

public final class XxxConfigFlowI18n {
    private XxxConfigFlowI18n() {}

    public static final String PREFIX = "config_flow";
    public static final String CUSTOM = PREFIX + ".custom";

    // 集成特有消息
    public static final String CHANNEL_TEST_SUCCESS = CUSTOM + ".channel_test_success";
    public static final String CHANNEL_TEST_FAILED = CUSTOM + ".channel_test_failed";
}
```

### 6.5 在流程中使用 i18n

```java
// 约定方法 - 自动拼接 key
builder.add(text("username", true)
    .displayName(getFieldDisplayName(stepId, "username"))
    .placeholder(getFieldPlaceholder(stepId, "username")));

builder.add(enumItem("protocol", true, "TCP")
    .displayName(getFieldDisplayName(stepId, "protocol"))
    .addOptions(mapOf(
        "TCP", "TCP协议",
        "UDP", "UDP协议"
    ))
    .buildValidator());

// 常量引用 - 特殊消息
String message = t(XxxConfigFlowI18n.CHANNEL_TEST_SUCCESS, channelNum);
String error = t(XxxConfigFlowI18n.CHANNEL_TEST_FAILED, channelNum, "连接超时");
```

---

## API 层集成

### 7.1 SchemaConversionService

负责将 ConfigDefinition 转换为前端所需的 Schema DTO，自动应用 i18n 翻译：

```java
public static ConfigFlowSchemaDto convert(
    ConfigDefinition configDef,
    String stepId,
    AbstractConfigFlow flow
) {
    // 获取步骤显示名称（使用 i18n）
    String stepDisplayName = flow.getStepDisplayName(stepId);

    // 转换字段
    for (AbstractConfigItem<?> item : configDef.getFlowConfigItems().values()) {
        ConfigFlowFieldDto field = convertConfigItem(item, stepId, flow);
        fields.add(field);
    }

    return schema;
}

private static ConfigFlowFieldDto convertConfigItem(
    AbstractConfigItem<?> item,
    String stepId,
    AbstractConfigFlow flow
) {
    // displayName: 优先使用 i18n 翻译
    String displayName = flow.getFieldDisplayName(stepId, item.getKey());

    // placeholder: 优先使用 i18n 翻译
    String placeholder = flow.getFieldPlaceholder(stepId, item.getKey());

    // description: 优先使用 i18n 翻译
    String description = flow.getFieldDescription(stepId, item.getKey());

    // enum options: 使用翻译后的选项
    if (item instanceof EnumConfigItem) {
        for (Map.Entry<String, String> entry : optionLabels.entrySet()) {
            String optionLabel = flow.getOptionDisplayName(stepId, fieldKey, entry.getKey());
            options.add(new FieldOptionDto(entry.getKey(), optionLabel));
        }
    }
}
```

### 7.2 ConfigFlowFieldDto

字段 DTO 结构：

```java
@Data
public class ConfigFlowFieldDto {
    private String key;
    private String displayName;
    private String fieldType;        // text, numeric, float, short, boolean, enum, array, object
    private String placeholder;
    private String description;
    private Object defaultValue;
    private boolean required;
    private Integer minLength;
    private Integer maxLength;
    private Object min;
    private Object max;
    private Object step;
    private List<FieldOptionDto> options;      // enum 选项
    private List<ConfigFlowFieldDto> nestedFields;  // object 嵌套字段
}
```

---

## 前端集成

### 8.1 FieldRegistry

字段类型注册表，管理字段类型与渲染器的映射：

```javascript
// fields/FieldRegistry.js
class FieldRegistry {
    constructor() {
        this.renderers = new Map();
        this.registerDefaultRenderers();
    }

    registerDefaultRenderers() {
        this.register('text', TextFieldRenderer);
        this.register('numeric', NumericFieldRenderer);
        this.register('boolean', BooleanFieldRenderer);
        this.register('enum', EnumFieldRenderer);
        // ...
    }

    register(fieldType, rendererClass) {
        this.renderers.set(fieldType, rendererClass);
    }

    getRenderer(fieldType) {
        return this.renderers.get(fieldType) || TextFieldRenderer;
    }
}
```

### 8.2 使用 Registry 渲染

```javascript
// flow-form.js
renderField(field) {
    const rendererClass = this.registry.getRenderer(field.fieldType);
    const renderer = new rendererClass(field, this);
    return renderer.render();
}
```

**禁止在 flow-form.js 中使用 if/switch 判断字段类型！**

---

## 完整示例

### 10.1 完整的配置流程类

```java
package com.ecat.integration.DemoConfigFlowIntegration.flow;

import com.ecat.core.ConfigFlow.AbstractConfigFlow;
import com.ecat.core.ConfigFlow.ConfigFlowResult;
import com.ecat.core.ConfigFlow.DynamicFormSchema;
import com.ecat.core.ConfigFlow.FormContext;
import com.ecat.core.Utils.DynamicConfig.ConfigDefinition;

import java.util.*;

import static com.ecat.core.ConfigFlow.ConfigItem.ConfigItemBuilder.*;

/**
 * Demo 设备配置流程
 *
 * @author coffee
 */
public class DemoConfigFlow extends AbstractConfigFlow {

    public static final String PROVIDER_COORDINATE = "com.ecat.integration:demo-config-flow";

    public DemoConfigFlow(String flowId) {
        super(flowId);
    }

    // ========== 步骤 1: 用户配置 ==========

    private ConfigFlowResult handleUserStep(Map<String, Object> userInput) {
        if (userInput == null || userInput.isEmpty()) {
            return show_form("user", this::generateUserSchema, new HashMap<>());
        }

        Map<String, Object> errors = validateUserInput(userInput);
        if (!errors.isEmpty()) {
            return show_form("user", this::generateUserSchema, errors);
        }

        return show_form("device_config", this::generateDeviceConfigSchema, new HashMap<>());
    }

    private ConfigDefinition generateUserSchema(FormContext context) {
        ConfigDefinition configDef = new ConfigDefinition();
        String stepId = "user";

        com.ecat.core.ConfigFlow.ConfigItem.ConfigItemBuilder builder =
            new com.ecat.core.ConfigFlow.ConfigItem.ConfigItemBuilder();

        builder.add(text("username", true)
            .displayName(getFieldDisplayName(stepId, "username"))
            .placeholder(getFieldPlaceholder(stepId, "username"))
            .length(1, 50));

        builder.add(text("password", true)
            .displayName(getFieldDisplayName(stepId, "password"))
            .placeholder(getFieldPlaceholder(stepId, "password"))
            .length(1, 100));

        return configDef.defineFlowItems(builder);
    }

    // ========== 步骤 2: 设备配置 ==========

    protected ConfigFlowResult step_device_config(Map<String, Object> userInput) {
        if (userInput == null || userInput.isEmpty()) {
            return show_form("device_config", this::generateDeviceConfigSchema, new HashMap<>());
        }

        return show_form("final_confirm", this::generateFinalConfirmSchema, new HashMap<>());
    }

    private ConfigDefinition generateDeviceConfigSchema(FormContext context) {
        ConfigDefinition configDef = new ConfigDefinition();
        String stepId = "device_config";

        com.ecat.core.ConfigFlow.ConfigItem.ConfigItemBuilder builder =
            new com.ecat.core.ConfigFlow.ConfigItem.ConfigItemBuilder();

        builder.add(text("device_name", true)
            .displayName(getFieldDisplayName(stepId, "device_name"))
            .length(1, 100));

        builder.add(enumItem("device_type", true, "PLC")
            .displayName(getFieldDisplayName(stepId, "device_type"))
            .addOptions(mapOf(
                "PLC", "PLC控制器",
                "SENSOR", "传感器",
                "METER", "仪表"
            ))
            .buildValidator());

        return configDef.defineFlowItems(builder);
    }

    // ========== 最终确认 ==========

    protected ConfigFlowResult step_final_confirm(Map<String, Object> userInput) {
        if (userInput == null || userInput.isEmpty()) {
            return show_form("final_confirm", this::generateFinalConfirmSchema, new HashMap<>());
        }

        Map<String, Object> resultData = new HashMap<>();
        resultData.put("provider_coordinate", PROVIDER_COORDINATE);
        resultData.put("created_at", new Date());

        return create_entry(resultData);
    }

    private ConfigDefinition generateFinalConfirmSchema(FormContext context) {
        ConfigDefinition configDef = new ConfigDefinition();
        String stepId = "final_confirm";

        com.ecat.core.ConfigFlow.ConfigItem.ConfigItemBuilder builder =
            new com.ecat.core.ConfigFlow.ConfigItem.ConfigItemBuilder();

        builder.add(booleanItem("confirmed", true, false)
            .displayName(getFieldDisplayName(stepId, "confirmed")));

        return configDef.defineFlowItems(builder);
    }

    // ========== 辅助方法 ==========

    /**
     * 创建 Map 的辅助方法（兼容 Java 8）
     */
    @SuppressWarnings("unchecked")
    private static <K, V> Map<K, V> mapOf(Object... entries) {
        Map<K, V> map = new LinkedHashMap<>();
        for (int i = 0; i < entries.length; i += 2) {
            map.put((K) entries[i], (V) entries[i + 1]);
        }
        return map;
    }
}
```

---

## 最佳实践

### 11.1 命名规范

| 类型 | 规范 | 示例 |
|------|------|------|
| 步骤方法 | `step{StepId}` | `stepNetworkConfig`, `stepDeviceConfig` |
| 步骤 ID | snake_case | `device_config`, `network_config` |
| 字段 Key | snake_case | `device_name`, `serial_port` |
| 选项值 | 大写或 snake_case | `TCP`, `persistent`, `dev_ttyUSB0` |

### 11.2 步骤设计

1. **单一职责**: 每个步骤只处理一类配置
2. **合理的步骤数量**: 建议 5-15 个步骤
3. **可选的回退**: 支持用户修改之前的配置
4. **清晰的验证**: 即时反馈输入错误

### 11.3 I18n 最佳实践

1. **使用约定方法**: 不要硬编码 displayName
2. **选项翻译**: `addOptions()` 中直接传入默认标签，`SchemaConversionService` 会自动通过 i18n 查找翻译，未命中时回退到传入的标签
3. **特殊消息**: 定义常量类引用
4. **参数化消息**: 使用 ICU Message Format

### 11.4 代码组织

```
integration/
├── flow/
│   └── XxxConfigFlow.java        # 配置流程类
├── XxxConfigFlowI18n.java         # i18n 常量类
└── resources/
    └── strings.json               # i18n 资源
```

---

## 常见问题

### Q1: 如何添加新的 ConfigItem 类型？

1. 在 `ecat-core/ConfigFlow/ConfigItem/` 创建新的 ConfigItem 类
2. 继承 `AbstractConfigItem<T>`
3. 实现 `getFieldType()`, `validateType()`, `addDefaultValue()`, `getDefaultValue()`
4. 在 `ConfigItemBuilder` 添加工厂方法
5. 在前端添加对应的 FieldRenderer

### Q2: 如何实现条件性步骤？

在步骤处理方法中根据用户输入决定下一步：

```java
protected ConfigFlowResult step_protocol_config(Map<String, Object> userInput) {
    String protocol = (String) userInput.get("protocol");

    if ("TCP".equals(protocol)) {
        return show_form("network_config", this::generateNetworkSchema, new HashMap<>());
    } else if ("Serial".equals(protocol)) {
        return show_form("serial_config", this::generateSerialSchema, new HashMap<>());
    }

    return show_form("protocol_config", this::generateProtocolSchema, errors);
}
```

### Q3: 如何跳过某个步骤？

直接返回下一步的结果：

```java
protected ConfigFlowResult step_optional(Map<String, Object> userInput) {
    // 检查是否需要此步骤
    if (shouldSkip()) {
        return show_form("next_step", this::generateNextSchema, new HashMap<>());
    }
    // 正常处理...
}
```

### Q4: 如何在运行时动态添加字段？

在 Schema 生成函数中根据上下文动态构建：

```java
private ConfigDefinition generateSchema(FormContext context) {
    String deviceType = getStepData("device_config").get("device_type");

    if ("SENSOR".equals(deviceType)) {
        builder.add(numeric("sensor_range_min", false)
            .displayName(getFieldDisplayName(stepId, "sensor_range_min")));
    }
}
```

### Q5: 如何处理多语言切换？

I18n 系统会自动根据全局 Locale 加载对应的翻译资源：
- 默认资源: `strings.json`
- 中文翻译: `i18n/zh_CN.json`
- 英文翻译: `i18n/en_US.json`

---

## 参考资源

- [I18n 系统文档](../I18n/README.md)
- [集成开发教程](../../../tutorial/README.md)
- [Config Flow i18n 设计方案](../../../plan/config-flow-i18n.md)
