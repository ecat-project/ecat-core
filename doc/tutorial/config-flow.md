# Config Flow 配置向导

Config Flow 是 ECAT 的分步配置向导框架，用于引导用户完成设备配置。每个集成可定义多步骤向导，支持条件分支、校验和国际化。

相关文档：[Config Schema 配置定义](config-schema.md) | [I18n 国际化系统](i18n.md) | [设备开发规范](device-development.md)

## 核心概念

### FlowContext

`FlowContext` 是一次 Flow 执行的共享数据容器，贯穿所有步骤：

| 字段 | 说明 |
|------|------|
| `flowId` | 唯一实例标识 |
| `coordinate` | 集成标识（groupId:artifactId） |
| `currentStep` | 当前步骤 |
| `entryData` | 业务数据（将写入 ConfigEntry.data） |
| `stepInputs` | 各步骤用户输入 (stepId → userInput) |
| `entryTitle` | Entry 标题（createEntry 时使用） |
| `entryUniqueId` | Entry 唯一标识（设置时自动校验重复） |

### 三种结果类型

每个步骤处理器返回 `ConfigFlowResult`：

| ResultType | 静态方法 | 用途 |
|-----------|---------|------|
| `SHOW_FORM` | `showForm(stepId, schema, errors)` | 向用户展示表单 |
| `CREATE_ENTRY` | `createEntry()` | 创建/更新配置条目 |
| `ABORT` | `abort(reason)` | 中止流程 |

> **说明：** 上表列出的是 `AbstractConfigFlow` 中的 protected 实例方法。`createEntry()` 框架自动从 `context` 构建条目，`entry.data` 仅包含业务数据。

### 步骤数据持久化

框架通过 `handleStep()` 自动保存步骤输入数据到 `context.stepInputs[stepId]`。每个步骤可通过 `getStepData(stepId)` 获取前序步骤数据。

注意：handleStep() 会在分发到步骤处理器之前自动调用 saveStepData()，
因此步骤处理器通常不需要手动调用 saveStepData()。
但入口步骤（通过 registerStepUser 注册的）需要手动调用。

### UniqueId 前置校验

调用 `context.setEntryUniqueId(uniqueId)` 时，框架自动进行双重校验：
1. 检查其他运行中的 flow 是否已使用该 uniqueId（内存）
2. 检查已持久化的 ConfigEntry 是否已使用该 uniqueId（持久化）

如果重复，抛出 `ConfigEntryRegistry.DuplicateUniqueIdException`。通过校验 = 隐式占位（flow 存在于 ConfigFlowRegistry.trackedFlows 中）。

**RECONFIGURE 模式**下使用 `context.setEntryUniqueId(uniqueId, true)` 跳过校验，因为 uniqueId 属于正在修改的 entry，校验无意义。

### Flow 生命周期管理

`ConfigFlowRegistry` 是运行中 flow 的唯一管理者。Flow 实例通过 `TrackedFlow` 包装，自动追踪最后更新时间。`getActiveFlow()` 每次调用自动 touch 更新活跃时间，外部无需管理。

Flow 结束时（正常完成、用户取消、过期清理），Registry 自动触发 `AbstractConfigFlow.onRelease()` 回调。

| 调用场景 | Registry 方法 |
|----------|--------------|
| 正常完成 (CREATE_ENTRY) | `finishActiveFlow()` |
| 用户取消 (DELETE 接口) | `abortActiveFlow()` |
| 过期清理 (30min timeout) | `cleanupExpiredFlows()` |

---

### 步骤流转示意

```
用户请求 → executeUserStep()
                │
                ▼
        ┌──────────────┐
        │  入口步骤      │  registerStepUser() 注册
        │  (USER/RECONF)│
        └──────┬───────┘
               │ showForm() 或 createEntry()
               ▼
        ┌──────────────┐     ┌──────────────┐
        │  步骤 A       │────→│  步骤 B       │
        │  registerStep │     │  registerStep │
        └──────┬───────┘     └──────┬───────┘
               │                    │
               ▼                    ▼
        ┌──────────────┐     ┌──────────────┐
        │  条件分支      │     │  最终确认      │
        │  TCP → 网络    │     │  createEntry()│
        │  Serial → 串口 │     └──────────────┘
        └──────────────┘
```

---

## 入口步骤类型

入口步骤是 Flow 的起点，每种类型只能注册一个：

| 入口类型 | 注册方法 | SourceType | 使用场景 |
|---------|---------|-----------|---------|
| **USER** | `registerStepUser()` | `SourceType.USER` | 用户手动创建新配置 |
| **RECONFIGURE** | `registerStepReconfigure()` | `SourceType.RECONFIGURE` | 重新配置已有条目 |
| **DISCOVERY** | `registerStepDiscovery()` | `SourceType.DISCOVERY` | 设备自动发现（预留） |

入口步骤的签名与普通步骤不同，接收 `FlowContext` 参数：

```java
registerStepUser("user", "用户配置", this::stepUserEntry);

private ConfigFlowResult stepUserEntry(Map<String, Object> userInput, FlowContext context) {
    return handleStep("user", userInput);
}
```

RECONFIGURE 入口会自动将已有条目的数据预填充到 context 中。

---

## 注册 Flow

### 在集成类中注册

`getConfigFlow()` 必须每次返回新实例，保证状态隔离：

```java
public class DemoConfigFlowIntegration extends IntegrationBase {
    @Override
    public AbstractConfigFlow getConfigFlow() {
        return new DemoConfigFlowNew();
    }
}
```

### 在 Flow 构造函数中注册步骤

```java
public class DemoConfigFlowNew extends AbstractConfigFlow {
    public DemoConfigFlowNew() {
        super(null);

        // 注册入口步骤
        registerStepUser("user", "用户配置", this::stepUserEntry);
        registerStepReconfigure("reconfigure", "重新配置", this::stepReconfigureEntry);

        // 注册普通步骤
        registerStep("device_config", this::stepDeviceConfig, "设备基本配置");
        registerStep("comm_config", this::stepCommConfig, "通讯配置");
        registerStep("final_confirm", this::stepFinalConfirm, "确认配置");
    }

    private ConfigFlowResult stepDeviceConfig(Map<String, Object> userInput) {
        // 获取前序步骤数据
        Map<String, Object> userData = getStepData("user");

        ConfigSchema schema = new ConfigSchema()
            .addField(new TextConfigItem("device_name", true)
                .displayName(getFieldDisplayName("device_config", "device_name"))
                .length(1, 100));

        return showForm("device_config", schema, new HashMap<>());
    }

    private ConfigFlowResult stepFinalConfirm(Map<String, Object> userInput) {
        // 所有步骤数据已自动保存到 context
        return createEntry();  // 从 context.getData() 构建条目
    }
}
```

---

## 条件分支

根据用户在前面步骤中的选择，跳转到不同的后续步骤：

```java
private ConfigFlowResult stepProtocolSelect(Map<String, Object> userInput) {
    Map<String, Object> data = getStepData("protocol_select");
    String protocol = (String) data.getOrDefault("protocol", "TCP");

    if ("TCP".equals(protocol) || "UDP".equals(protocol)) {
        return showForm("network_config", createNetworkSchema(), new HashMap<>());
    } else {
        return showForm("serial_config", createSerialSchema(), new HashMap<>());
    }
}
```

---

## 创建配置条目

最终步骤调用 `createEntry()`，框架从 `context` 构建干净的 `ConfigEntry`：

```java
private ConfigFlowResult stepFinalConfirm(Map<String, Object> userInput) {
    getContextData().put("flow_type", "demo-device-config");
    getContextData().put("created_at", DateTimeUtils.now());
    setEntryUniqueId(generateUniqueId());  // 设置时自动校验重复
    setEntryTitle("Demo Device");
    return createEntry();
}
```

条目持久化为 YAML 文件：`.ecat-data/core/config_entries/{groupId}/{artifactId}/{entryId}.yml`

---

## 参考实现

| 集成 | 步骤数 | 特点 |
|------|--------|------|
| **demo-config-flow** | 18 步 | 展示所有 ConfigItem 类型、条件分支、动态通道配置 |
| **sailhero** | 4 步 | 复用 `SerialCommConfigSchema`，简单实用 |

详细框架设计参见：
- [ConfigFlow 开发指南](../ecat/core/Integration/ConfigFlow-development.md)
- [ConfigFlow 使用指南](../ecat/core/Integration/ConfigFlow-usage.md)
