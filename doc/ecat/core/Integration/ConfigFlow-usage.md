# Config Flow 使用指南

本文档介绍集成开发者如何使用 Config Flow 框架为设备创建配置向导。

## 目录

1. [概述](#概述)
2. [核心概念](#核心概念)
3. [快速开始](#快速开始)
4. [步骤定义约定](#步骤定义约定)
5. [字段类型](#字段类型)
6. [I18n 国际化约定](#i18n-国际化约定)
7. [流程控制](#流程控制)
8. [验证与错误处理](#验证与错误处理)
9. [完整示例](#完整示例)
10. [检查清单](#检查清单)

---

## 概述

### 什么是 Config Flow？

Config Flow 是一个分步配置向导框架，帮助用户完成复杂设备的配置。每个集成可以通过继承 `AbstractConfigFlow` 实现自己的配置流程。

### 使用 Config Flow 的好处

- **标准化的用户体验** - 统一的分步配置界面
- **内置 I18n 支持** - 自动翻译步骤、字段和选项
- **类型安全** - 强类型的字段定义和验证
- **灵活的流程控制** - 支持条件分支和动态步骤

---

## 核心概念

### 职责划分

| 职责 | 所在模块 | 说明 |
|------|---------|------|
| **流程框架** | ecat-core | AbstractConfigFlow, ConfigItem 类型系统 |
| **通用文案** | ecat-core-api | action/error/message 常量和翻译 |
| **业务实现** | 集成模块 | 具体流程步骤、字段定义、业务翻译 |

### 关键类

| 类 | 用途 |
|----|------|
| `AbstractConfigFlow` | 配置流程基类，继承它实现配置流程 |
| `ConfigFlowResult` | 步骤执行结果（显示表单/完成/中止） |
| `ConfigItemBuilder` | 字段构建器，提供静态工厂方法 |
| `I18nProxy` | 国际化代理，自动获取翻译 |

---

## 快速开始

### 3.1 创建配置流程类

```java
package com.ecat.integration.MyIntegration.flow;

import com.ecat.core.ConfigFlow.*;
import com.ecat.core.Utils.DynamicConfig.ConfigDefinition;
import java.util.*;

import static com.ecat.core.ConfigFlow.ConfigItem.ConfigItemBuilder.*;

public class MyConfigFlow extends AbstractConfigFlow {

    // 集成标识（必须定义）
    public static final String PROVIDER_COORDINATE = "com.ecat.integration:my-integration";

    public MyConfigFlow(String flowId) {
        super(flowId);
    }

    // 入口步骤（必须实现，方法名固定为 step_user）
    @Override
    protected ConfigFlowResult step_user(Map<String, Object> userInput) {
        if (userInput == null || userInput.isEmpty()) {
            // 首次进入，显示表单
            return show_form("user", this::generateUserSchema, new HashMap<>());
        }

        // 处理用户输入，进入下一步
        return show_form("network_config", this::generateNetworkSchema, new HashMap<>());
    }

    // 其他步骤（方法名必须以 step_ 开头）
    protected ConfigFlowResult step_network_config(Map<String, Object> userInput) {
        // ...
    }

    // 最终步骤返回 create_entry
    protected ConfigFlowResult step_final(Map<String, Object> userInput) {
        Map<String, Object> data = new HashMap<>();
        data.put("provider_coordinate", PROVIDER_COORDINATE);
        return create_entry(data);
    }
}
```

### 3.2 定义 Schema 生成方法

```java
private ConfigDefinition generateUserSchema(FormContext context) {
    ConfigDefinition configDef = new ConfigDefinition();
    String stepId = "user";  // 当前步骤 ID

    ConfigItemBuilder builder = new ConfigItemBuilder();

    // 使用 i18n 方法获取翻译
    builder.add(text("device_name", true)
        .displayName(getFieldDisplayName(stepId, "device_name"))
        .placeholder(getFieldPlaceholder(stepId, "device_name"))
        .length(1, 100));

    builder.add(numeric("port", true, 502.0)
        .displayName(getFieldDisplayName(stepId, "port"))
        .range(1, 65535));

    return configDef.defineFlowItems(builder);
}
```

### 3.3 创建 strings.json

在 `src/main/resources/strings.json` 中定义翻译：

```json
{
  "config_flow": {
    "step_user": {
      "display_name": "用户配置",
      "items": {
        "device_name": {
          "display_name": "设备名称",
          "placeholder": "请输入设备名称"
        },
        "port": {
          "display_name": "端口号"
        }
      }
    },
    "step_network_config": {
      "display_name": "网络配置"
    }
  }
}
```

---

## 步骤定义约定

### 4.1 方法命名约定

| 约定 | 说明 |
|------|------|
| 方法名格式 | `step_{stepId}` |
| 入口步骤 | 必须是 `step_user` |
| 参数类型 | `Map<String, Object> userInput` |
| 返回类型 | `ConfigFlowResult` |

**示例：**
```java
// ✅ 正确
protected ConfigFlowResult step_user(Map<String, Object> userInput) { }
protected ConfigFlowResult step_network_config(Map<String, Object> userInput) { }
protected ConfigFlowResult step_serial_config(Map<String, Object> userInput) { }

// ❌ 错误 - 方法名不以 step_ 开头
protected ConfigFlowResult userConfig(Map<String, Object> userInput) { }
```

### 4.2 步骤 ID 命名约定

| 场景 | 命名规范 | 示例 |
|------|---------|------|
| 用户/登录 | `user` | `step_user` |
| 设备配置 | `device_config` | `step_device_config` |
| 网络配置 | `network_config` | `step_network_config` |
| 串口配置 | `serial_config` | `step_serial_config` |
| 测试步骤 | `{name}_test` | `step_network_test` |
| 通道配置 | `channel_{n}_config` | `step_channel_1_config` |
| 最终确认 | `final_confirm` | `step_final_confirm` |

### 4.3 步骤处理模板

```java
protected ConfigFlowResult step_xxx(Map<String, Object> userInput) {
    // 1. 首次进入检查
    if (userInput == null || userInput.isEmpty()) {
        return show_form("xxx", this::generateXxxSchema, new HashMap<>());
    }

    // 2. 验证输入（可选）
    Map<String, Object> errors = validateInput(userInput);
    if (!errors.isEmpty()) {
        return show_form("xxx", this::generateXxxSchema, errors);
    }

    // 3. 处理业务逻辑（可选）
    // ...

    // 4. 决定下一步
    if (shouldGoToNextStep()) {
        return show_form("next_step", this::generateNextSchema, new HashMap<>());
    } else {
        // 测试失败，停留在当前步骤
        return show_form("xxx", this::generateXxxSchema, errors);
    }
}
```

---

## 字段类型

### 5.1 可用字段类型

| 类型 | 方法 | 适用场景 |
|------|------|---------|
| 文本 | `text(key, required)` | 名称、描述、主机地址 |
| 数值 | `numeric(key, required, default)` | 端口、超时、数量 |
| 浮点 | `floatItem(key, required, default)` | 温度、电压 |
| 短整数 | `shortItem(key, required, default)` | 优先级、ID |
| 布尔 | `booleanItem(key, required, default)` | 开关、确认 |
| 枚举 | `enumItem(key, required, default)` | 协议类型、设备类型 |
| 数组 | `array(key, required, default, elementType)` | 列表 |
| 对象 | `object(key, required)` | 嵌套配置 |

### 5.2 字段定义约定

```java
// ✅ 推荐：使用 i18n 方法
builder.add(text("host", true)
    .displayName(getFieldDisplayName(stepId, "host"))     // i18n
    .placeholder(getFieldPlaceholder(stepId, "host"))     // i18n
    .length(1, 100));

// ❌ 不推荐：硬编码中文
builder.add(text("host", true)
    .displayName("主机地址")  // 硬编码
    .placeholder("请输入主机地址"));
```

### 5.3 枚举字段约定

```java
// 使用 getTranslatedOptions 批量翻译选项
builder.add(enumItem("protocol", true, "TCP")
    .displayName(getFieldDisplayName(stepId, "protocol"))
    .addOptions(getTranslatedOptions(stepId, "protocol", mapOf(
        "TCP", "TCP",           // value -> label（会被 i18n 覆盖）
        "UDP", "UDP",
        "Serial", "Serial"
    )))
    .buildValidator());         // 必须调用！
```

### 5.4 字段约束约定

| 字段类型 | 常用约束 |
|---------|---------|
| `text` | `.length(min, max)` |
| `numeric` | `.range(min, max)` |
| `floatItem` | `.range(min, max)` |
| `shortItem` | `.range(min, max)` |
| `enumItem` | `.buildValidator()` （必须） |

---

## I18n 国际化约定

### 6.1 Key 命名约定（重点关注）

| 内容 | Key 格式 | 自动拼接 |
|------|---------|---------|
| 步骤名称 | `config_flow.step_{stepId}.display_name` | ✅ |
| 字段名称 | `config_flow.step_{stepId}.items.{fieldKey}.display_name` | ✅ |
| 字段占位符 | `config_flow.step_{stepId}.items.{fieldKey}.placeholder` | ✅ |
| 字段描述 | `config_flow.step_{stepId}.items.{fieldKey}.description` | ✅ |
| 选项标签 | `config_flow.step_{stepId}.items.{fieldKey}.options.{value}` | ✅ |
| 特殊消息 | `config_flow.custom.{customKey}` | ❌ 需常量 |

### 6.2 strings.json 结构约定

```json
{
  "config_flow": {
    "step_{stepId}": {
      "display_name": "步骤显示名称",
      "items": {
        "{fieldKey}": {
          "display_name": "字段显示名称",
          "placeholder": "输入提示（可选）",
          "description": "字段描述（可选）",
          "options": {
            "value1": "选项1显示名称",
            "value2": "选项2显示名称"
          }
        }
      }
    },
    "custom": {
      "test_success": "测试成功",
      "test_failed": "测试失败: {0}"
    }
  }
}
```

### 6.3 使用约定方法（重点关注）

**不要硬编码 displayName！** 使用约定方法自动获取翻译：

```java
String stepId = "user";

// ✅ 正确：使用约定方法
builder.add(text("username", true)
    .displayName(getFieldDisplayName(stepId, "username"))
    .placeholder(getFieldPlaceholder(stepId, "username"))
    .description(getFieldDescription(stepId, "username")));

// 枚举选项
builder.add(enumItem("role", true, "user")
    .displayName(getFieldDisplayName(stepId, "role"))
    .addOptions(getTranslatedOptions(stepId, "role", mapOf(
        "admin", "admin",
        "user", "user"
    )))
    .buildValidator());
```

### 6.4 特殊消息使用常量

对于无固定模式的特殊消息，定义常量类：

```java
// XxxConfigFlowI18n.java
public final class XxxConfigFlowI18n {
    public static final String CUSTOM = "config_flow.custom";
    public static final String CHANNEL_TEST_SUCCESS = CUSTOM + ".channel_test_success";
    public static final String CHANNEL_TEST_FAILED = CUSTOM + ".channel_test_failed";
}

// 在流程中使用
String message = t(XxxConfigFlowI18n.CHANNEL_TEST_SUCCESS, channelNum);
String error = t(XxxConfigFlowI18n.CHANNEL_TEST_FAILED, channelNum, "超时");
```

### 6.5 I18n 方法速查表

| 方法 | 用途 | 返回值 |
|------|------|-------|
| `getFieldDisplayName(stepId, fieldKey)` | 字段显示名称 | 翻译或 fieldKey |
| `getFieldPlaceholder(stepId, fieldKey)` | 字段占位符 | 翻译或 null |
| `getFieldDescription(stepId, fieldKey)` | 字段描述 | 翻译或 null |
| `getOptionDisplayName(stepId, fieldKey, value)` | 选项显示名称 | 翻译或 value |
| `getTranslatedOptions(stepId, fieldKey, map)` | 翻译整个选项 Map | 翻译后的 Map |
| `getStepDisplayName(stepId)` | 步骤显示名称 | 翻译或 stepId |
| `t(key)` | 特殊消息 | 翻译或 key |
| `t(key, args...)` | 带参数的特殊消息 | 翻译后替换参数 |

---

## 流程控制

### 7.1 三种结果类型

| 方法 | 用途 | 场景 |
|------|------|------|
| `show_form(stepId, schema, errors)` | 显示表单 | 需要用户输入 |
| `create_entry(data)` | 完成流程 | 配置完成，创建条目 |
| `abort(reason)` | 中止流程 | 发生不可恢复错误 |

### 7.2 条件分支

根据用户输入决定下一步：

```java
protected ConfigFlowResult step_protocol_config(Map<String, Object> userInput) {
    if (userInput == null || userInput.isEmpty()) {
        return show_form("protocol_config", this::generateProtocolSchema, new HashMap<>());
    }

    String protocol = (String) userInput.get("protocol");

    // 根据协议类型跳转到不同配置
    if ("TCP".equals(protocol) || "UDP".equals(protocol)) {
        return show_form("network_config", this::generateNetworkSchema, new HashMap<>());
    } else if ("Serial".equals(protocol)) {
        return show_form("serial_config", this::generateSerialSchema, new HashMap<>());
    }

    // 默认停留在当前步骤
    return show_form("protocol_config", this::generateProtocolSchema, new HashMap<>());
}
```

### 7.3 获取之前步骤的数据

```java
// 获取特定步骤的数据
Map<String, Object> userData = getStepData("user");
String username = (String) userData.get("username");

// 获取当前步骤数据
Map<String, Object> currentData = getCurrentStepData();

// 获取所有流程数据
Map<String, Object> allData = getData();
```

### 7.4 动态 Schema

根据上下文动态生成字段：

```java
private ConfigDefinition generateDeviceDetailSchema(FormContext context) {
    String stepId = "device_detail";
    ConfigItemBuilder builder = new ConfigItemBuilder();

    // 基础字段
    builder.add(text("manufacturer", false)
        .displayName(getFieldDisplayName(stepId, "manufacturer")));

    // 根据设备类型动态添加字段
    String deviceType = (String) getStepData("device_config").get("device_type");

    if ("SENSOR".equals(deviceType)) {
        builder.add(numeric("sensor_range_min", false, 0.0)
            .displayName(getFieldDisplayName(stepId, "sensor_range_min")));
        builder.add(numeric("sensor_range_max", false, 100.0)
            .displayName(getFieldDisplayName(stepId, "sensor_range_max")));
    } else if ("METER".equals(deviceType)) {
        builder.add(text("meter_accuracy", false, "0.5")
            .displayName(getFieldDisplayName(stepId, "meter_accuracy")));
    }

    ConfigDefinition configDef = new ConfigDefinition();
    return configDef.defineFlowItems(builder);
}
```

---

## 验证与错误处理

### 8.1 内置验证

字段约束会自动验证：

```java
builder.add(text("username", true)     // 必填验证
    .length(1, 50));                   // 长度验证

builder.add(numeric("port", true, 502.0)
    .range(1, 65535));                 // 范围验证

builder.add(enumItem("protocol", true, "TCP")
    .addOption("TCP", "TCP")
    .addOption("UDP", "UDP")
    .buildValidator());                // 枚举值验证
```

### 8.2 自定义验证

在步骤方法中添加自定义验证：

```java
protected ConfigFlowResult step_network(Map<String, Object> userInput) {
    if (userInput == null || userInput.isEmpty()) {
        return show_form("network", this::generateNetworkSchema, new HashMap<>());
    }

    Map<String, Object> errors = new HashMap<>();

    // 自定义验证
    String host = (String) userInput.get("host");
    if (host != null && !host.matches("^\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}$")) {
        errors.put("host", "请输入有效的 IP 地址");
    }

    Object portObj = userInput.get("port");
    if (portObj != null) {
        int port = Integer.parseInt(portObj.toString());
        if (port < 1 || port > 65535) {
            errors.put("port", "端口号必须在 1-65535 范围内");
        }
    }

    // 有错误则返回当前表单
    if (!errors.isEmpty()) {
        return show_form("network", this::generateNetworkSchema, errors);
    }

    // 验证通过，进入下一步
    return show_form("next_step", this::generateNextSchema, new HashMap<>());
}
```

### 8.3 测试步骤错误处理

```java
protected ConfigFlowResult step_network_test(Map<String, Object> userInput) {
    if (userInput == null || userInput.isEmpty()) {
        return show_form("network_test", this::generateTestSchema, new HashMap<>());
    }

    Boolean testPassed = (Boolean) userInput.get("test_passed");

    if (!Boolean.TRUE.equals(testPassed)) {
        String action = (String) userInput.get("action");

        if ("reconfig".equals(action)) {
            // 用户选择重新配置
            return show_form("network", this::generateNetworkSchema, new HashMap<>());
        }

        // 停留在测试步骤
        Map<String, Object> errors = new HashMap<>();
        errors.put("test_result", "连接测试失败，请检查配置");
        return show_form("network_test", this::generateTestSchema, errors);
    }

    // 测试通过
    return show_form("channel_config", this::generateChannelSchema, new HashMap<>());
}
```

---

## 完整示例

### 10.1 最小化配置流程

```java
public class SimpleConfigFlow extends AbstractConfigFlow {

    public static final String PROVIDER_COORDINATE = "com.ecat.integration:simple";

    public SimpleConfigFlow(String flowId) {
        super(flowId);
    }

    @Override
    protected ConfigFlowResult step_user(Map<String, Object> userInput) {
        if (userInput == null || userInput.isEmpty()) {
            return show_form("user", this::generateUserSchema, new HashMap<>());
        }

        Map<String, Object> data = new HashMap<>();
        data.put("provider_coordinate", PROVIDER_COORDINATE);
        data.put("config", userInput);
        return create_entry(data);
    }

    private ConfigDefinition generateUserSchema(FormContext context) {
        String stepId = "user";
        ConfigItemBuilder builder = new ConfigItemBuilder();

        builder.add(text("name", true)
            .displayName(getFieldDisplayName(stepId, "name"))
            .length(1, 100));

        ConfigDefinition configDef = new ConfigDefinition();
        return configDef.defineFlowItems(builder);
    }
}
```

### 10.2 完整的多步骤流程

参见 `demo-config-flow` 集成中的 `DemoConfigFlow.java`，包含 18 个步骤的完整实现。

---

## 检查清单

### 开发前

- [ ] 确定需要的步骤数量和名称
- [ ] 设计每个步骤的字段
- [ ] 规划条件分支逻辑

### 开发中

- [ ] 继承 `AbstractConfigFlow`
- [ ] 定义 `PROVIDER_COORDINATE` 常量
- [ ] 实现 `step_user` 入口方法
- [ ] 其他步骤方法以 `step_` 开头
- [ ] Schema 生成方法使用 `getFieldDisplayName` 等 i18n 方法
- [ ] 枚举字段调用 `buildValidator()`
- [ ] 最终步骤调用 `create_entry()`

### I18n 资源

- [ ] 创建 `strings.json`，包含 `config_flow` 结构
- [ ] 每个步骤有 `display_name`
- [ ] 每个字段有 `display_name`
- [ ] 枚举字段有 `options`
- [ ] 特殊消息定义常量类

### 测试

- [ ] 测试完整流程可走通
- [ ] 测试验证错误正确显示
- [ ] 测试条件分支正确
- [ ] 测试中英文翻译正确

---

## 参考资源

- [Config Flow 开发指南](ConfigFlow-development.md) - 框架开发文档
- [集成开发指南](../../INTEGRATION-DEVELOPMENT.md) - 通用集成开发
- Demo 示例: `ecat-integrations/demo-config-flow`
