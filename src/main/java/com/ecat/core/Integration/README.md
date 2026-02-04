# Integration 模块文档

## 目录

- [ecat-config.yml 配置规则](#ecat-configyml-配置规则)
- [依赖管理](#依赖管理)
- [配置示例](#配置示例)
- [向后兼容性](#向后兼容性)
- [开发指南](#开发指南)

---

## ecat-config.yml 与 pom.xml的区别
- pom.xml 面向java编译的版本约束，确保最低版本依赖的可编译，从保持低版本兼容考虑应尽量使用准确的版本描述；
- ecat-config.yml 面向ecat系统运行时的版本兼容控制，一般使用范围描述，以便ecat使用最新版本运行提升性能和安全修复；

### 举例：
1. A集成编译代码依赖core 1.0.0 版本，则它的：
- pom.xml 中明确对 core 的1.0.0编译依赖，确保其能通过编译和自测试，明确本集成最低的编译依赖。如果不需要更高版本特性，可以一直保持对1.0.0版本编译依赖。一旦使用了高版本接口则一定要更新编译依赖版本，否则编译无法通过；
- ecat-config.yml 中明确对 core ^1.0.0 版本兼容性描述，保障ecat运行时确定版本可运行的最新兼容性。

2. 如果core版本升级到 1.0.1 版本，但是A修复代码没有用到core的 1.0.1 版本接口，则A：
- pom.xml 中明确对core 1.0.0 版本，保持最低兼容性
- ecat-config.yml 保持 core ^1.0.0 版本兼容性描述

3. 如果A集成升级代码用了core 1.0.1 版本，则A的：
- pom.xml 中更新对core 1.0.1 版本引用
- ecat-config.yml 中更新对core ^1.0.1 版本兼容性描述


### 过渡期方案
- 为了解决最新依赖集成的快速测试，目前集成pom.xml都使用范围依赖如 [1.0.1,2.0.0), ecat-config.yml 也同步更新为 ^1.0.1，最小版本为代码编译依赖的最低版本（要通过删除本地maven repo后重新构建才能确认使用的最新依赖，否则会找到依赖的历史版本）
- 后面完成所有自动化多版本兼容性测试环境后再优化改为上面的准确编译依赖版本。


## ecat-config.yml 配置规则

### 概述

`ecat-config.yml` 是每个集成模块 JAR 包中的配置文件，位于 JAR 的 `src/main/resources/ecat-config.yml`。它定义了集成的元数据信息，包括对 ECAT Core 的版本要求、依赖关系、Web 平台支持等。

### 配置文件格式

```yaml
# 此文件为 ecat 集成的配置文件，无此文件则下面均为默认值。

# 对 ECAT Core 主程序的版本要求。可选项，默认 ^1.0.0
requires_core: ">=1.5.0,<2.0.0"

dependencies: # 依赖的其他 ecat 集成的信息。可选项，默认无
  - artifactId: integration-modbus
    groupId: com.ecat           # 可选，默认为 com.ecat
    version: "^1.0.0"            # 可选，版本约束

web-platform: # Web 平台支持配置。可选项，默认不支持
  ui: false                      # 是否提供 UI 界面
  api: false                     # 是否提供 API 接口
```

### 字段说明

#### requires_core（可选）

对 ECAT Core 主程序的版本要求。

| 字段 | 类型 | 必需 | 默认值 | 说明 |
|------|------|------|--------|------|
| `requires_core` | String | ❌ 否 | `^1.0.0` | 对 ECAT Core 的版本约束 |

**支持的版本约束语法**：
- `^1.0.0` - 兼容主版本（`>=1.0.0,<2.0.0`）
- `~1.0.0` - 兼容次版本（`>=1.0.0,<1.1.0`）
- `>=1.0.0` - 大于等于
- `>=1.0.0,<2.0.0` - 范围组合

#### dependencies（可选）

依赖的其他 ecat 集成列表。每个依赖项包含：

| 字段 | 类型 | 必需 | 默认值 | 说明 |
|------|------|------|--------|------|
| `artifactId` | String | ✅ 是 | - | 依赖的集成 artifactId |
| `groupId` | String | ❌ 否 | `com.ecat` | 依赖的集成 groupId |
| `version` | String | ❌ 否 | `*` | 版本约束（如 `^1.0.0`, `>=1.2.0`），`*` 表示任意版本 |

#### web-platform（可选）

Web 平台支持配置：

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `ui` | Boolean | `false` | 是否提供 UI 界面 |
| `api` | Boolean | `false` | 是否提供 API 接口 |

---

## 依赖管理

### 依赖格式

#### 1. 官方集成（省略 groupId）

```yaml
dependencies:
  # 旧格式（向后兼容）
  - artifactId: integration-ecat-common

  # 新格式（推荐）
  - artifactId: integration-modbus
    version: "^1.0.0"        # 兼容 1.x 版本
```

**解析结果**：
- `groupId`: `com.ecat`（默认）
- `artifactId`: `integration-modbus`
- `version`: `^1.0.0`

#### 2. 第三方集成（指定 groupId）

```yaml
dependencies:
  - artifactId: modbus4j
    groupId: com.github.tusky2015
    version: "^3.0.0"
```

**解析结果**：
- `groupId`: `com.github.tusky2015`
- `artifactId`: `modbus4j`
- `version`: `^3.0.0`

### 版本约束语法

支持的版本约束语法（基于 Semantic Versioning）：

| 语法 | 含义 | 示例 | 匹配版本 |
|------|------|------|----------|
| `^1.2.0` | 兼容主版本 | `^1.2.0` | `>=1.2.0,<2.0.0` |
| `~1.2.0` | 兼容次版本 | `~1.2.0` | `>=1.2.0,<1.3.0` |
| `>=1.2.0` | 大于等于 | `>=1.2.0` | `1.2.0, 1.3.0, 2.0.0...` |
| `>1.2.0` | 大于 | `>1.2.0` | `1.2.1, 1.3.0...` |
| `<=1.2.0` | 小于等于 | `<=1.2.0` | `...1.1.0, 1.2.0` |
| `<1.2.0` | 小于 | `<1.2.0` | `...1.1.0` |
| `1.2.0` | 精确版本 | `1.2.0` | `1.2.0` |
| `*` | 任意版本 | `*` | 所有版本 |
| `>=1.0.0,<2.0.0` | 范围组合 | `>=1.0.0,<2.0.0` | `>=1.0.0,<=2.0.0` |

**版本约束省略**：如果省略 `version` 字段，默认值为 `*`（接受任何版本，最宽松的约束）。

---

## 配置示例

### 示例 1：最小化配置（旧格式兼容）

```yaml
# modbus/src/main/resources/ecat-config.yml

dependencies: # 依赖的其他 ecat 集成的信息。可选项，默认无
  - artifactId: integration-ecat-common
```

### 示例 2：官方集成带版本约束

```yaml
# dypsensor/src/main/resources/ecat-config.yml

dependencies: # 依赖的其他 ecat 集成的信息。可选项，默认无
  - artifactId: integration-modbus
    version: "^1.0.0"        # 兼容 1.x 版本
```

### 示例 3：第三方集成

```yaml
# my-custom-integration/src/main/resources/ecat-config.yml

dependencies:
  - artifactId: modbus4j
    groupId: com.github.tusky2015
    version: "^3.0.0"
  - artifactId: integration-serial
    version: ">=1.2.0"
```

### 示例 4：完整配置（带 requires_core）

```yaml
# advanced-integration/src/main/resources/ecat-config.yml

# 对 ECAT Core 主程序的版本要求
requires_core: ">=1.5.0,<2.0.0"

dependencies:
  - artifactId: integration-common
    version: "^1.0.0"
  - artifactId: integration-modbus
    groupId: com.ecat
    version: ">=1.2.0"

web-platform:
  ui: true
  api: true
```

### 示例 5：指定 Core 版本 + 多依赖

```yaml
# device-app-integration/src/main/resources/ecat-config.yml

# 此集成需要 ECAT Core 1.5 或更高版本，但低于 2.0
requires_core: ">=1.5.0,<2.0.0"

dependencies:
  - artifactId: integration-core
    version: ">=1.0.0,<2.0.0"  # 必须兼容 Core 1.x
  - artifactId: integration-serial
    version: "^2.0.0"           # 需要 serial 2.x
  - artifactId: integration-common
    version: ">=1.2.0"           # 至少 1.2.0
```

---

## 向后兼容性

### 旧格式支持

**旧格式（没有 ecat-config.yml 文件 或只有 dependencies）**：
```yaml
dependencies:
  - artifactId: integration-ecat-common
```

**解析为**：
```java
DependencyInfo {
    groupId: "com.ecat",      // 默认值
    artifactId: "integration-ecat-common",
    version: "*"              // 任意版本
}

// requires_core 默认为 "^1.0.0"
```

### 字段省略规则

| 字段 | 省略时的默认值 |
|------|--------------|
| `requires_core` | `^1.0.0` |
| `groupId` | `com.ecat` |
| `artifactId` | **必需**，无默认值 |
| `version` | `*`（任意版本） |
| `web-platform.ui` | `false` |
| `web-platform.api` | `false` |

---

## 开发指南

### 添加新集成

1. **创建 ecat-config.yml**

在集成的 `src/main/resources/` 目录下创建 `ecat-config.yml`：

```yaml
dependencies:
  - artifactId: integration-common
    version: "^1.0.0"
```

2. **依赖 groupId 选择**

- **官方集成**（groupId 为 `com.ecat`）：省略 `groupId` 字段
- **第三方集成**：明确指定 `groupId`

3. **版本约束建议**

- **主要依赖**：使用 `^` 约束主版本
- **次要依赖**：使用 `~` 约束次版本
- **不稳定依赖**：使用精确版本号
- **无特殊要求**：省略 `version` 字段

### Java 代码中使用

#### 创建 DependencyInfo

```java
// 官方集成（默认 groupId）
DependencyInfo dep1 = new DependencyInfo("integration-modbus");

// 官方集成（带版本约束）
DependencyInfo dep2 = new DependencyInfo("integration-modbus", "^1.0.0");

// 第三方集成
DependencyInfo dep3 = new DependencyInfo(
    "com.github.tusky2015",  // groupId
    "modbus4j",               // artifactId
    "^3.0.0"                  // version
);
```

#### 从 IntegrationInfo 获取依赖

```java
// 获取所有依赖信息
List<DependencyInfo> dependencies = info.getDependencyInfoList();

// 根据 artifactId 查找依赖
DependencyInfo dep = info.getDependencyInfo("integration-modbus");

// 根据 groupId:artifactId 查找依赖
DependencyInfo dep = info.getDependencyInfo("com.ecat", "integration-modbus");

// 检查版本约束
if (dep.hasVersionConstraint()) {
    String version = dep.getVersion();
    // 使用 VersionRange 检查版本兼容性
}

// 获取完整坐标
String coordinate = dep.getCoordinate();  // "com.ecat:integration-modbus"
```

#### 向后兼容

旧代码仍然可以使用 `getDependencies()` 获取纯 artifactId 列表：

```java
// 旧方法（已废弃，但仍可用）
List<String> deps = info.getDependencies();  // ["integration-modbus", "integration-common"]

// 推荐的新方法
List<DependencyInfo> depInfos = info.getDependencyInfoList();
```

---

## 常见问题

### Q1：何时指定 groupId？

**A**：
- **官方集成**（`com.ecat`）：省略 `groupId`，使用默认值
- **第三方集成**：必须指定 `groupId`

### Q2：版本约束是必需的吗？

**A**：不是。省略 `version` 表示接受任何版本（最宽松的约束）。

### Q3：如何处理同名但不同 groupId 的集成？

**A**：使用完整的 `groupId:artifactId` 来标识集成。例如：
- `com.ecat:integration-modbus`
- `com.github.tusky2015:modbus4j`

### Q4：旧格式的 ecat-config.yml 还能使用吗？

**A**：能。系统向后兼容只有 `artifactId` 的旧格式，会自动使用默认值。

---

## 版本历史

| 版本 | 日期 | 变更说明 |
|------|------|----------|
| 1.0.0 | 2026-01-07 | 初始版本，支持依赖管理、版本约束、第三方集成 |
