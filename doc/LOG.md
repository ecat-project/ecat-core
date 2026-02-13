# ECAT 日志系统

## 1. 概述

ECAT 日志系统解决插件式架构中的日志分类和上下文传递问题：

- **自动分类**：每个集成的日志自动写入独立的日志文件
- **上下文传递**：异步任务中日志上下文自动传递，无需手动管理
- **实时广播**：支持 SSE 实时日志推送，按时间戳排序

### 核心问题与解决方案

| 问题 | 解决方案 |
|------|---------|
| 异步任务中日志丢失上下文 | Log 类自动从 JAR MANIFEST 获取坐标 |
| 集成日志混入 core 日志文件 | LogManager 按坐标分离存储 |
| SSE 广播历史日志重复 | 订阅时间过滤机制 |
| 并发订阅导致重复广播 | CopyOnWriteArraySet 自动去重 |
| 动态加载 JAR 日志重定向 | RuoyiClassLoaderTurboFilter 拦截 |

---

## 2. 架构设计

```
┌─────────────────────────────────────────────────────────────┐
│                      Integration JAR                        │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  MANIFEST.MF                                         │   │
│  │    Ecat-Artifact-Id: integration-xxx                │   │
│  │    Ecat-Group-Id: com.ecat                          │   │
│  └─────────────────────────────────────────────────────┘   │
│                           │                                  │
│                           ▼                                  │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  IntegrationCoordinateHelper                         │   │
│  │    从 MANIFEST 或 pom.xml 读取坐标                   │   │
│  │    coordinate = "com.ecat:integration-xxx"          │   │
│  └─────────────────────────────────────────────────────┘   │
│                           │                                  │
│                           ▼                                  │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  Log 类                                              │   │
│  │    构造时自动获取坐标，日志输出时广播到 LogManager   │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                            │
                            ▼
┌─────────────────────────────────────────────────────────────┐
│                      LogManager                              │
│  ┌─────────────────────────────────────────────────────┐   │
│  │  根据 coordinate 分发到不同的 LogBuffer              │   │
│  │                                                      │   │
│  │  LogBuffer (每个坐标一个)                            │   │
│  │    - 环形缓冲区存储日志                              │   │
│  │    - 订阅时间过滤，避免历史日志重复                  │   │
│  │    - 按时间戳排序广播                                │   │
│  │                                                      │   │
│  │  logs/integrations/                                  │   │
│  │    com.ecat:integration-xxx/ecat.log                 │   │
│  │    core/ecat.log                                     │   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

---

## 3. 核心组件

### 3.1 IntegrationCoordinateHelper

**文件位置**: `com/ecat/core/Utils/IntegrationCoordinateHelper.java`

**职责**：从 Class 对象自动检测其所在 JAR 的坐标

**设计要点**：
- 支持 JAR 运行模式（从 MANIFEST.MF 读取）
- 支持 IDE 开发模式（从 pom.xml 读取）
- 使用正确的 ClassLoader（每个集成由独立 ClassLoader 加载）
- 缓存机制避免重复读取

**API**：
```java
// 获取完整坐标 (groupId:artifactId)
String coordinate = IntegrationCoordinateHelper.getCoordinate(MyClass.class);
// 结果: "com.ecat:integration-sensecap"

// 获取 artifactId
String artifactId = IntegrationCoordinateHelper.getArtifactId(MyClass.class);
// 结果: "integration-sensecap"

// 获取 groupId
String groupId = IntegrationCoordinateHelper.getGroupId(MyClass.class);
// 结果: "com.ecat"
```

### 3.2 Log 类

**文件位置**: `com/ecat/core/Utils/Log.java`

**职责**：扩展 SLF4J Logger，支持：
- 自动从 JAR MANIFEST 获取坐标
- 多种坐标获取模式（CoordinateMode）
- 日志广播到 LogManager

**坐标模式配置**：

| 模式 | 行为 | 适用场景 |
|------|------|---------|
| `LOG_FIRST` | 优先内置坐标，降级到 MDC | **默认模式**，推荐大多数场景 |
| `MDC_FIRST` | 优先 MDC，降级到内置坐标 | 需要动态覆盖日志分类 |
| `LOG_ONLY` | 只使用内置坐标 | 严格隔离，确保日志始终归类到原始集成 |
| `MDC_ONLY` | 只使用 MDC | 完全由调用方控制日志分类 |

**配置方式**：

```yaml
# .ecat-data/core/config.yml
logging:
  coordinate-mode: LOG_FIRST
```

```bash
# 或系统属性
java -Decat.log.coordinate.mode=LOG_FIRST -jar ecat-core.jar
```

### 3.3 LogBuffer

**文件位置**: `com/ecat/core/Log/LogBuffer.java`

**职责**：日志缓冲区，支持：
- 环形缓冲区存储日志（超出容量自动丢弃旧日志）
- SSE 订阅者实时接收日志
- 按时间戳排序广播
- 订阅时间过滤，避免历史日志重复

**关键设计**：

```java
// 订阅者集合 - 使用 CopyOnWriteArraySet 自动去重
private final CopyOnWriteArraySet<LogSubscriber> subscribers;

// 订阅者订阅时间 - 用于过滤历史日志避免重复
private final ConcurrentHashMap<LogSubscriber, Long> subscriberTimestamps;
```

**订阅时间过滤**：
- 订阅时记录当前时间戳
- 广播时只发送 `entry.timestamp >= subscribeTime` 的日志
- 避免 SSE 连接时历史日志与广播日志重复

### 3.4 LogManager

**文件位置**: `com/ecat/core/Log/LogManager.java`

**职责**：
- 单例模式管理所有 LogBuffer
- 按坐标分发日志到对应缓冲区
- 支持集成的注册/注销

### 3.5 MdcExecutorService / MdcScheduledExecutorService

**文件位置**: `com/ecat/core/Utils/Mdc/`

**职责**：包装线程池，确保异步任务保留 MDC 上下文

**工作原理**：
1. 任务提交时：捕获当前线程的 MDC 上下文
2. 任务执行时：恢复捕获的上下文，执行任务，清理上下文

### 3.6 ClassLoaderCoordinateFilter

**文件位置**: `com/ecat/core/Log/ClassLoaderCoordinateFilter.java`

**职责**：为动态加载的 JAR 自动设置 MDC 坐标

**适用场景**：
- 动态加载第三方 JAR（如 ruoyi.jar、spring-boot fat jar 等）
- 第三方 JAR 使用标准 SLF4J Logger，没有 MDC 上下文
- 第三方 JAR 创建的线程（Tomcat 线程池等）无法继承 MDC

**工作原理**：
1. 在加载动态 JAR 时注册其 ClassLoader 到 Filter
2. Filter 拦截所有日志事件
3. 检查日志调用者的 ClassLoader 是否已注册
4. 自动为已注册 ClassLoader 的日志设置 MDC 坐标

**使用示例**：
```java
// 1. 创建动态 ClassLoader 加载第三方 JAR
URLClassLoader dynamicLoader = new URLClassLoader(jarUrls, parentClassLoader);

// 2. 注册 ClassLoader（在调用 JAR 代码之前）
ClassLoaderCoordinateFilter.registerClassLoader(dynamicLoader, "com.ecat:integration-xxx");

// 3. 调用第三方 JAR 的代码
// 所有来自该 ClassLoader 的日志都会自动设置 MDC 坐标

// 4. 卸载时注销
ClassLoaderCoordinateFilter.unregisterClassLoader(dynamicLoader);
```

**Logback 配置**（已自动配置）：
```xml
<configuration>
    <!-- Filter: 为动态加载的JAR自动设置MDC坐标 -->
    <turboFilter class="com.ecat.core.Log.ClassLoaderCoordinateFilter"/>
    <!-- 其他配置 -->
</configuration>
```

---

## 4. 配置方式

### 方式一：系统属性（优先级最高）

```bash
java -Decat.log.coordinate.mode=LOG_FIRST -jar ecat-core.jar
```

### 方式二：配置文件

```yaml
# .ecat-data/core/config.yml
logging:
  coordinate-mode: LOG_FIRST
```

### 方式三：运行时修改

```java
Log.setCoordinateMode(Log.CoordinateMode.MDC_FIRST);
```

---

## 5. 开发者使用指南

### 5.1 现有代码 - 无需修改

Log 会自动从 JAR MANIFEST 获取坐标：

```java
public class MyDevice extends DeviceBase {
    // 自动获取正确的坐标
    private static final Log log = LogFactory.getLogger(MyDevice.class);

    public void doWork() {
        // 即使在异步任务中，日志也会正确分类
        CompletableFuture.supplyAsync(() -> {
            log.info("处理数据");  // ✅ 日志自动写入正确的集成文件
            return result;
        });
    }
}
```

### 5.2 异步场景 - 推荐使用 thenComposeAsync(executor)

```java
public void asyncProcess() {
    someFuture.thenComposeAsync(result -> {
        log.info("异步处理");  // ✅ 通过 executor 保持上下文
        return process(result);
    }, getScheduledExecutor());  // 使用集成的线程池
}
```

### 5.3 获取坐标供其他用途

```java
// 使用 IntegrationCoordinateHelper 获取坐标
String coordinate = IntegrationCoordinateHelper.getCoordinate(MyClass.class);
// 结果: "com.ecat:integration-sensecap"
```

---

## 6. Maven 配置要求

每个集成的 JAR 必须在 MANIFEST.MF 中配置坐标：

```xml
<plugin>
    <artifactId>maven-assembly-plugin</artifactId>
    <configuration>
        <archive>
            <manifestEntries>
                <!-- 必需：集成标识 -->
                <Ecat-Artifact-Id>${project.artifactId}</Ecat-Artifact-Id>
                <!-- 可选：组标识，默认 com.ecat -->
                <Ecat-Group-Id>${project.groupId}</Ecat-Group-Id>
            </manifestEntries>
        </archive>
    </configuration>
</plugin>
```

---

## 7. 坐标模式行为矩阵

| 模式 | Log 有 coordinate | MDC 有值 | 结果 |
|------|-----------------|---------|------|
| LOG_FIRST | ✅ | ✅ | Log coordinate |
| LOG_FIRST | ❌ | ✅ | MDC |
| LOG_FIRST | ✅ | ❌ | Log coordinate |
| LOG_FIRST | ❌ | ❌ | "core" |
| MDC_FIRST | ✅ | ✅ | MDC |
| MDC_FIRST | ❌ | ✅ | MDC |
| MDC_FIRST | ✅ | ❌ | Log coordinate |
| LOG_ONLY | ✅ | ✅ | Log coordinate |
| LOG_ONLY | ❌ | ✅ | null → "core" |
| MDC_ONLY | ✅ | ✅ | MDC |
| MDC_ONLY | ✅ | ❌ | null → "core" |

---

## 8. 相关文件

| 文件 | 说明 |
|------|------|
| `IntegrationCoordinateHelper.java` | 坐标获取工具类 |
| `Log.java` | 日志类，支持坐标模式和广播 |
| `LogBuffer.java` | 日志缓冲区，支持 SSE 订阅 |
| `LogManager.java` | 日志管理器，按坐标分离存储 |
| `LogEntry.java` | 日志条目数据结构 |
| `LogSubscriber.java` | SSE 订阅者 |
| `MdcContext.java` | MDC 上下文管理 |
| `MdcExecutorService.java` | ExecutorService 包装器 |
| `MdcScheduledExecutorService.java` | ScheduledExecutorService 包装器 |
| `ClassLoaderCoordinateFilter.java` | 动态 JAR 日志重定向过滤器 |

---

## 9. 故障排查

### Q: 日志中显示 `core` 而不是集成坐标？

A: 检查以下项目：
1. JAR 的 MANIFEST.MF 是否包含 `Ecat-Artifact-Id`
2. pom.xml 中的 maven-assembly-plugin 配置是否正确
3. 使用 `jar -xf your.jar META-INF/MANIFEST.MF` 查看内容

### Q: IDE 开发模式下坐标获取不正确？

A: IDE 模式下从 pom.xml 读取坐标，确保：
1. 项目有正确的 pom.xml
2. pom.xml 包含 artifactId 和 maven-assembly-plugin 配置

### Q: SSE 日志出现重复？

A: 已通过以下机制解决：
1. **订阅时间过滤**：LogBuffer 在订阅时记录时间戳，只广播订阅后产生的日志
2. **CopyOnWriteArraySet**：使用 Set 存储订阅者，自动去重避免并发订阅导致重复

### Q: 如何清除缓存？

A: 调用 `IntegrationCoordinateHelper.clearCache()` 清除坐标缓存（主要用于测试）。

### Q: 动态加载的 JAR（如 ruoyi.jar）日志如何重定向？

A: 使用 `ClassLoaderCoordinateFilter`：

```java
// 1. 在加载 JAR 时注册 ClassLoader
URLClassLoader jarLoader = ...; // 动态加载的 ClassLoader
ClassLoaderCoordinateFilter.registerClassLoader(jarLoader, "com.ecat:integration-xxx");

// 2. JAR 内部的所有日志（包括新线程）都会自动重定向到指定坐标

// 3. 卸载时注销
ClassLoaderCoordinateFilter.unregisterClassLoader(jarLoader);
```

这个方案适用于：
- 动态加载第三方 JAR（如 ruoyi.jar、spring-boot fat jar）
- 任何需要将第三方 JAR 日志重定向到特定集成的场景
