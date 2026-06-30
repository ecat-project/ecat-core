# ECAT Core

> **工业数据自动化控制系统 - 核心框架**

ECAT Core 是 ECAT 项目的核心框架，为工业监测行业提供开箱即用的自动化控制基础。通过插件化架构，支持快速集成多厂商设备，实现从硬件数据采集、数据清洗、统计分析到自动化流程控制和 AI 决策的全流程能力。

---

## 项目概述

### 什么是 ECAT Project？

ECAT (EcoAutomation) 是一个专注于监测行业数据自动化流程控制的工业级系统。它通过插件化开发模式，让用户能够快速匹配自己的数据自动化控制任务，支持以下核心能力：

- **硬件数据采集** - 支持多协议、多厂商设备的统一接入
- **数据清洗处理** - 内置数据验证、转换和清洗机制
- **统计分析** - 提供丰富的数据统计和分析工具
- **自动化流程控制** - 灵活的规则引擎和任务调度
- **AI 决策支持** - 可扩展的智能算法集成能力

### ECAT Core 的定位

ECAT Core 是整个 ECAT 项目的基础框架，提供：

| 能力 | 描述 |
|------|------|
| **设备管理** | 统一的设备抽象层，支持设备注册、生命周期管理和状态监控 |
| **状态管理** | 强类型不可变状态 `AttrState<T>` 承载工程值、状态、业务单位与 `Class<T>` 值类型——总线/API/持久化的唯一状态真相源 |
| **集成框架** | 插件化架构，支持动态加载和热插拔 |
| **事件总线** | 发布-订阅模式的事件系统，实现模块间松耦合通信 |
| **国际化** | 完整的 i18n 支持，支持多语言切换 |
| **任务调度** | 内置任务管理器，支持定时任务和异步执行 |
| **依赖解析** | 智能的版本兼容性检查和依赖解析 |

### 核心特性

- **工业级控制系统** - 专为环境监测行业设计的开箱即用解决方案
- **开源生态系统** - 通过共建开源生态，快速集成多厂商设备
- **信息合规与安全审计** - 内置合规性检查，满足安全审计要求
- **持续智能化演进** - 不断提升智能和数据驱动能力

---

## 核心架构

### 系统组成

```
┌─────────────────────────────────────────────────────────────┐
│                        ECAT Core                            │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │ Integration │  │   Device    │  │    State    │         │
│  │   Manager   │  │  Registry   │  │   Manager   │         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │     Bus     │  │     Task    │  │     I18N    │         │
│  │  Registry   │  │   Manager   │  │  Registry   │         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
├─────────────────────────────────────────────────────────────┤
│                    Integration Plugins                      │
│  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐      │
│  │  Modbus  │ │ Serial   │ │   HTTP   │ │  Custom  │ ...  │
│  └──────────┘ └──────────┘ └──────────┘ └──────────┘      │
└─────────────────────────────────────────────────────────────┘
```

### 核心模块

| 模块 | 说明 |
|------|------|
| `IntegrationManager` | 集成模块管理器，负责插件的加载、初始化和生命周期管理 |
| `DeviceRegistry` | 设备注册表，管理所有设备实例 |
| `StateManager` | 状态管理器，管理所有属性和状态变化 |
| `BusRegistry` | 事件总线注册表，管理发布-订阅事件 |
| `TaskManager` | 任务管理器，提供定时任务和异步执行能力 |
| `I18nRegistry` | 国际化注册表，支持多语言资源管理 |

---

## 快速开始

### 环境要求

- **Java**: JDK 8 或更高版本
- **Maven**: 3.6 或更高版本
- **操作系统**: Linux / Windows / macOS

### 构建项目

```bash
# 克隆仓库
git clone https://github.com/ecat-project/ecat-core.git
cd ecat-core

# 构建
mvn clean package

# 运行
java -jar target/ecat-core-1.0.0.jar
```

### Maven 依赖

在你的插件项目中添加 ECAT Core 依赖：

```xml
<dependency>
    <groupId>com.ecat</groupId>
    <artifactId>ecat-core</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
```

---

## 开发插件

详细的插件开发指南请参考：[**ECAT 集成开发教程**](./doc/tutorial/README.md)

### 开发文档

- [ECAT 集成开发教程](./doc/tutorial/README.md) - 完整的开发文档，包括：
  - 项目结构和核心文件说明
  - 设备开发规范和命名规则
  - I18n 国际化系统详解
  - 单元测试要求
  - Maven 配置规范
  - 常见问题解答

---

## 核心概念

### 设备 (Device)

设备是 ECAT 中数据采集和控制的基本单元。每个设备：

- 继承自 `DeviceBase`
- 拥有唯一的 `id` 和 `name`
- 包含多个属性 (`Attribute`)
- 支持生命周期管理 (`init`, `start`, `stop`, `release`)

### 属性 (Attribute)

属性是设备的数据点，可以是：

| 类型 | 说明 |
|------|------|
| `NumericAttribute` | 数值型属性（温度、压力等） |
| `TextAttribute` | 文本型属性 |
| `BinaryAttribute` | 布尔型属性 |
| `CommandAttribute` | 命令型属性（可执行的操作） |
| `SelectAttribute` | 枚举型属性 |

#### 值模型与不可变状态

每个属性内部持有可变的工程值，对外**只通过**不可变强类型快照暴露：

- **`getState(): AttrState<T>`** —— 当前状态的唯一对外入口。`AttrState<T>` 不可变（全字段 `final`、集合类 value 防御性拷贝），总线订阅者与 API 读到的是字段自洽的快照，无撕裂读。
- **`AttrState<T>`** 承载工程值（`T value`）、`valueType`（`Class<T>` = 属性的 targetType，非枚举）、`status`、业务单位（`nativeUnit`）、派生的 `displayValue`、以及 `Instant` 类型的 `lastUpdated`/`lastChanged`。`getDisplayValue(toUnit)` 是基于已是工程值的 value 做纯单位换算，零 live 属性依赖。
- **线性换算属性**（如 4-20 mA → 工程量）将原始信号保留为内部 `rawValue`（单位 `inputUnit`），不进 state、不持久化。设备经 `updateRawValue(信号)` 灌入原始信号；逻辑设备/API 经 `updateValue(工程值)` 设业务值——两个 public 入口职责分离，语义不同不可混用。
- **原始信号字段为 `protected`**：`value`、`status`、`getValueType()`、`getUpdateTime()` 不在对外面上。跨模块、跨线程消费方一律读 `getState()`——这是项目铁律：总线与 API 不读属性脏数据。
- **持久化围绕 state**：`StateManager.saveState` 经 `PersistedState.from(state)` 序列化当前 `AttrState`；`restore` 据此重建 `lastState`。`rawValue` 不恢复（重启重新采集）。

### 集成 (Integration)

集成是功能模块的载体：

- `IntegrationBase` - 无设备管理的基础类
- `IntegrationDeviceBase` - 带设备管理的基础类

### 事件总线 (Bus)

发布-订阅模式的事件系统，用于模块间通信：

```java
// 发布事件
core.getBusRegistry().publish("my-topic", eventData);

// 订阅事件
core.getBusRegistry().subscribe("my-topic", new EventSubscriber() {
    @Override
    public void onEvent(String topic, Object data) {
        // 处理事件
    }
});
```

---

## 更多资源

- **示例插件**: [ecat-integrations](https://github.com/ecat-integrations)
- **开发文档**: [ECAT Core 开发指南](https://github.com/ecat-project/ecat-core/wiki)
- **问题反馈**: [GitHub Issues](https://github.com/ecat-project/ecat-core/issues)

---

## 协议声明

1. 核心框架：ECAT Core 采用 **Apache License 2.0** 开源协议
2. 插件开发：基于 ECAT Core 开发的插件需遵守 Apache 2.0 协议规则
3. 版权保留：若复用 ECAT Core 代码片段，需保留原版权声明

### 许可证获取

- ECAT Core 完整许可证：[LICENSE](./LICENSE)
- Apache 2.0 协议文本：https://www.apache.org/licenses/LICENSE-2.0

### 致谢

- Home Assistant 提供的设计参考
- Spring Boot 提供的jar机制


