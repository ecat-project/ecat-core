# ECAT 日志路由开发指引

> 基于代码实现的第一性原理分析
> 更新时间: 2026-02-16

## 1. 核心设计原则

### 1.1 设计目标
- 将不同集成的日志自动路由到各自的日志文件
- 支持日志的集中查看和独立查看
- 提供 Trace ID 实现跨线程日志追踪
- **实时广播**：支持 SSE 实时日志推送，按时间戳排序

### 1.2 核心组件

| 组件 | 文件 | 作用 |
|------|------|------|
| ClassLoaderCoordinateFilter | ecat-core/.../Log/ClassLoaderCoordinateFilter.java | TurboFilter，根据 logger.getName() 自动设置 MDC 坐标 |
| IntegrationBase | ecat-core/.../Integration/IntegrationBase.java | 集成基类，在 onLoad 时自动注册包名前缀 |
| SiftingAppender | logback.xml | 根据 MDC 坐标将日志分流到不同文件 |
| CoordinateConverter | ecat-core/.../Utils/CoordinateConverter.java | 从 MDC 获取坐标，格式化输出 |
| TraceIdConverter | ecat-core/.../Utils/Mdc/TraceIdConverter.java | 从 MDC 获取 Trace ID |
| TraceContext | ecat-core/.../Utils/Mdc/TraceContext.java | 内部使用：提供线程间 MDC 上下文传播 |
| MdcExecutorService | ecat-core/.../Utils/Mdc/MdcExecutorService.java | 带 MDC 支持的线程池包装器 |
| NamedThreadFactory | ecat-core/.../Task/NamedThreadFactory.java | 线程工厂，为线程池设置有意义的名称 |
| MdcScheduledExecutorService | ecat-core/.../Utils/Mdc/MdcScheduledExecutorService.java | 带 MDC 支持的定时任务执行器 |
| Log | ecat-core/.../Utils/Log.java | 增强版日志包装器，支持坐标模式和日志广播 |
| LogFactory | ecat-core/.../Utils/LogFactory.java | 日志工厂，创建 Log 实例 |
| LogManager | ecat-core/.../Log/LogManager.java | 日志管理器，按坐标分发日志到不同缓冲区 |
| LogBuffer | ecat-core/.../Log/LogBuffer.java | 环形缓冲区，支持 SSE 订阅和广播 |
| LogEntry | ecat-core/.../Log/LogEntry.java | 日志条目数据结构 |
| IntegrationCoordinateHelper | ecat-core/.../Utils/IntegrationCoordinateHelper.java | 从 JAR MANIFEST 或 pom.xml 获取集成坐标 |
| MdcCoordinateConverter | ecat-core/.../Utils/Mdc/MdcCoordinateConverter.java | MDC 坐标读写工具类 |
| LogSubscriber | ecat-core/.../Log/LogSubscriber.java | SSE 订阅者，支持实时日志推送 |

### 1.3 路由流程

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           日志路由完整流程                                   │
├─────────────────────────────────────────────────────────────────────────────┤
│                                                                             │
│  方式一：集成自身日志（由 IntegrationBase 处理）                            │
│                                                                             │
│  1. IntegrationBase.onLoad()                                               │
│     → IntegrationBase.registerPackagePrefix(packagePrefix, coordinate)     │
│     → LogManager.registerIntegration(coordinate)                           │
│     → Log.setIntegrationContext(coordinate)                                │
│                                                                             │
│  2. 业务代码调用 log.info()                                                │
│     → Log 实例持有 coordinate（构造时检测）                                │
│     → 调用底层 SLF4J Logger                                                │
│                                                                             │
│  3. ClassLoaderCoordinateFilter.decide() (TurboFilter)                    │
│     → logger.getName() 获取业务类全限定名                                  │
│     → 匹配已注册的包名前缀（最长匹配原则）                                 │
│     → MDC.put("integration.coordinate", coordinate)                       │
│                                                                             │
│  方式二：第三方 JAR 日志（如 ruoyi-admin.jar）                             │
│                                                                             │
│  1. EcatCoreRuoyiIntegration.onStart()                                     │
│     → ClassLoaderCoordinateFilter.registerPackagePrefix()                 │
│        注册: com.ruoyi → com.ecat:integration-ecat-core-ruoyi            │
│        注册: org.springframework → ruoyi 坐标                             │
│                                                                             │
│  2. 第三方代码调用 log.info()                                              │
│     → ClassLoaderCoordinateFilter.decide() 拦截                           │
│     → 根据 logger.getName() 匹配前缀                                       │
│     → MDC.put("integration.coordinate", coordinate)                       │
│                                                                             │
│  3. SiftingAppender 根据 MDC 坐标分流                                      │
│     → logs/integrations/{coordinate}/ecat.log                            │
│                                                                             │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 1.4 关键设计特点

| 特性 | 说明 |
|------|------|
| 自动路由 | 业务代码无需感知日志路由，TurboFilter 自动处理 |
| 动态注册 | 集成在 onLoad() 时注册包名前缀，onRelease() 时注销 |
| 最长匹配 | 支持包名层级，如 `com.ecat.integration.SailheroIntegration` 优先于 `com.ecat` |
| MDC 传播 | TaskManager.createMdcExecutorService() 自动传播 MDC 上下文（外部集成）<br>TraceContext.wrapRunnable() 仅供 ecat-core 内部使用 |
| 广播支持 | LogBroadcastAppender 支持日志实时推送到 SSE |
| 多模式坐标 | Log 类支持 4 种坐标模式：LOG_FIRST、MDC_FIRST、LOG_ONLY、MDC_ONLY |

---

### 1.5 实时广播机制（SSE）

LogBuffer 支持 SSE（Server-Sent Events）实时日志推送，按时间戳排序。

**核心问题与解决方案**：

| 问题 | 解决方案 |
|------|---------|
| SSE 广播历史日志重复 | 订阅时间过滤机制 |
| 并发订阅导致重复广播 | CopyOnWriteArraySet 自动去重 |

**LogBuffer 关键设计**：

```java
// 订阅者集合 - 使用 CopyOnWriteArraySet 自动去重
private final CopyOnWriteArraySet<LogSubscriber> subscribers;

// 订阅者订阅时间 - 用于过滤历史日志避免重复
private final ConcurrentHashMap<LogSubscriber, Long> subscriberTimestamps;
```

**订阅时间过滤机制**：
1. 订阅时记录当前时间戳
2. 广播时只发送 `entry.timestamp >= subscribeTime` 的日志
3. 避免 SSE 连接时历史日志与广播日志重复

---

## 2. 日志坐标检测机制

### 2.1 IntegrationCoordinateHelper

从 JAR MANIFEST 或 pom.xml 获取集成坐标：

```java
// JAR 运行模式：从 MANIFEST.MF 读取
// Ecat-Artifact-Id: integration-xxx
// Ecat-Group-Id: com.ecat

// IDE 开发模式：从 pom.xml 读取
// 结果: "com.ecat:integration-xxx"
String coordinate = IntegrationCoordinateHelper.getCoordinate(MyClass.class);
```

### 2.2 Log 类的坐标模式

Log 类支持 4 种坐标模式（可配置）：

| 模式 | 行为 | 适用场景 |
|------|------|---------|
| `LOG_FIRST` | 优先使用 Log 实例坐标，其次 MDC | **默认模式**，推荐大多数场景 |
| `MDC_FIRST` | 优先使用 MDC 坐标，其次 Log 实例 | 需要动态覆盖日志分类 |
| `LOG_ONLY` | 仅使用 Log 实例坐标 | 严格隔离，确保日志始终归类到原始集成 |
| `MDC_ONLY` | 仅使用 MDC 坐标 | 完全由调用方控制日志分类 |

### 2.3 配置方式

```yaml
# .ecat-data/core/config.yml
logging:
  coordinate-mode: LOG_FIRST
```

```bash
# 或系统属性
java -Decat.log.coordinate.mode=LOG_FIRST -jar ecat-core.jar
```

```java
// 运行时修改
Log.setCoordinateMode(Log.CoordinateMode.MDC_FIRST);
```

### 2.4 坐标模式行为矩阵

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

## 3. 新集成开发要求

### 3.1 基础要求

所有集成必须继承 `IntegrationBase` 或 `IntegrationDeviceBase`，日志路由自动生效。

```java
public class MyIntegration extends IntegrationBase {
    @Override
    public void onInit() {
        // 日志已自动初始化，直接使用
        log.info("MyIntegration initialized");
    }

    @Override
    public void onStart() {
        log.info("MyIntegration started");
    }
}
```

**自动完成的工作（IntegrationBase.onLoad()）**：

```java
public void onLoad(EcatCore core, IntegrationLoadOption loadOption) {
    // 1. 设置日志上下文
    String coordinate = loadOption.getIntegrationInfo().getCoordinate();
    Log.setIntegrationContext(coordinate);

    // 2. 注册包名前缀与坐标的映射
    String packagePrefix = this.getClass().getPackage().getName();
    // 例如: "com.ecat.integration.MyIntegration" → "com.ecat:integration-my-integration"
    ClassLoaderCoordinateFilter.registerPackagePrefix(packagePrefix, coordinate);

    // 3. 注册到 LogManager
    LogManager.getInstance().registerIntegration(coordinate, loadOption.getIntegrationInfo());
}
```

### 3.2 嵌入式框架集成（如 ruoyi）

如果集成加载了第三方 JAR（如 ruoyi-admin.jar），需要注册第三方包的名前缀：

```java
@Override
public void onStart() {
    // 注册集成自身的包名前缀（由 IntegrationBase 自动处理）

    // 注册第三方框架的包名前缀
    String coordinate = "com.ecat:integration-ecat-core-ruoyi";
    ClassLoaderCoordinateFilter.registerPackagePrefix("com.ruoyi", coordinate);
    ClassLoaderCoordinateFilter.registerPackagePrefix("org.springframework", coordinate);
    ClassLoaderCoordinateFilter.registerPackagePrefix("org.mybatis", coordinate);
    ClassLoaderCoordinateFilter.registerPackagePrefix("com.alibaba.druid", coordinate);
}
```

### 3.3 日志输出规范

```java
// 使用 SLF4J 风格
log.info("Message with {}", variable);
log.error("Error message: {}", error, exception);
log.warn("Warning: {}", warning);
log.debug("Debug info: {}", info);

// 推荐：为关键操作添加上下文信息
log.info("Device {} data updated: {}", deviceId, data);

// 异常日志必须包含堆栈信息
log.error("Failed to process data", exception);
```

### 3.1 异步任务（推荐方式）

如果集成需要使用线程池执行异步任务，推荐使用 `TaskManager` 提供的 MDC 封装执行器：

```java
// ✅ 推荐：使用 TaskManager 创建 MDC 封装的线程池
ExecutorService executor = core.getTaskManager().createMdcExecutorService(4, "MyIntegration-pool");
executor.submit(() -> {
    log.info("Async task executed");  // 自动继承 MDC 上下文
});
// 任务完成后关闭
executor.shutdown();

// ✅ 推荐：定时任务使用 getMdcScheduledExecutorService()
ScheduledExecutorService scheduler = core.getTaskManager().getMdcScheduledExecutorService();
scheduler.scheduleAtFixedRate(() -> {
    log.info("Periodic task");  // 自动继承 MDC 上下文
}, 0, 1, TimeUnit.MINUTES);
```

**为什么推荐这种方式？**
- `TaskManager.createMdcExecutorService()` 返回的Executor 自动传播 MDC 上下文
- `TaskManager.getMdcScheduledExecutorService()` 已内置 MDC 支持，无需额外包装
- 开发者无需关心 MDC 传播细节，像使用普通线程池一样简单

**MdcExecutorService 工作原理**：
1. 任务提交时：捕获当前线程的 MDC 上下文
2. 任务执行时：恢复捕获的上下文，执行任务，清理上下文

**线程命名最佳实践**：
推荐使用 `NamedThreadFactory` 为线程池设置有意义的名称，便于日志追踪和问题排查：

```java
// ✅ 推荐：使用 NamedThreadFactory 命名线程
ExecutorService executor = MdcExecutorService.wrap(
    Executors.newFixedThreadPool(4, new NamedThreadFactory("integration-myfeature"))
);
// 线程名将显示为: integration-myfeature-0, integration-myfeature-1, ...

// ✅ 推荐：定时任务同样需要命名
ScheduledExecutorService scheduler = MdcScheduledExecutorService.wrap(
    Executors.newScheduledThreadPool(2, new NamedThreadFactory("integration-scheduler"))
);
```

**常见线程池命名规范**：
| 线程池用途 | 推荐命名 |
|-----------|---------|
| 集成管理 | `integration-manager` |
| 事件总线 | `integration-bus` |
| 串口异步 | `integration-serial` |
| 定时任务 | `integration-{feature}-scheduler` |

---

## 4. 新集成开发应遵循的原则

### 原则 1：使用独特的包名前缀

```java
// ✅ 正确：独特的前缀
package com.ecat.integration.MyNewIntegration;

// ❌ 错误：与其他集成共用前缀
package com.ecat.integration.EnvMyIntegration;  // 会被 Env* 规则捕获（如果有）
```

### 原则 2：在 IntegrationBase.onLoad() 中注册

集成继承 `IntegrationBase` 后，父类会自动完成注册：

```java
@Override
public void onLoad(EcatCore core, IntegrationLoadOption loadOption) {
    super.onLoad(core, loadOption);  // 父类会自动注册：
                                    // 1. registerPackagePrefix("com.ecat.integration.MyNewIntegration", coordinate)
}
```

### 原则 3：包名前缀必须覆盖所有业务类

如果你的集成有多个子包，确保都以前缀开头：

```java
com.ecat.integration.MyNewIntegration.service.XxxService   // ✅ 匹配
com.ecat.integration.MyNewIntegration.mapper.XxxMapper    // ✅ 匹配
com.ecat.integration.MyNewIntegration.controller.XxxController  // ✅ 匹配
// 这些都会匹配 "com.ecat.integration.MyNewIntegration" 前缀
```

### 原则 4：嵌入式框架需要额外注册第三方包前缀

如果你的集成加载了第三方 JAR（如 ruoyi-admin.jar），需要在 `onStart()` 中注册第三方包的名前缀：

```java
@Override
public void onStart() {
    String coordinate = loadOption.getIntegrationInfo().getCoordinate();

    // 注册第三方框架的包名前缀
    ClassLoaderCoordinateFilter.registerPackagePrefix("com.ruoyi", coordinate);
    ClassLoaderCoordinateFilter.registerPackagePrefix("org.springframework", coordinate);
    ClassLoaderCoordinateFilter.registerPackagePrefix("org.mybatis", coordinate);
    ClassLoaderCoordinateFilter.registerPackagePrefix("com.alibaba.druid", coordinate);
}
```

### 原则 5：使用 TaskManager 创建异步任务执行器

**✅ 推荐方式：**
```java
// 使用 TaskManager 创建 MDC 封装的线程池
ExecutorService executor = core.getTaskManager().createMdcExecutorService(4, "MyIntegration-pool");

// 定时任务使用 getMdcScheduledExecutorService()
ScheduledExecutorService scheduler = core.getTaskManager().getMdcScheduledExecutorService();
```

**❌ 错误方式：**
```java
// 直接使用普通线程池，MDC 会丢失
ExecutorService executor = Executors.newFixedThreadPool(4);
```

### 原则 6：使用 TaskManager 的定时任务执行器

```java
// ✅ 正确：使用 getMdcScheduledExecutorService()
ScheduledExecutorService scheduler = core.getTaskManager().getMdcScheduledExecutorService();

// ❌ 错误：使用普通 ScheduledExecutorService
ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
```

---

## 5. 示例代码

### 5.1 简单业务集成

```java
package com.ecat.integration.MyNewIntegration;

import com.ecat.core.Integration.IntegrationBase;

public class MyNewIntegration extends IntegrationBase {

    @Override
    public void onInit() {
        log.info("MyNewIntegration initializing...");
        // 初始化逻辑
        log.info("MyNewIntegration initialized with {} devices", getAllDevices().size());
    }

    @Override
    public void onStart() {
        log.info("MyNewIntegration started");
    }

    @Override
    public void onPause() {
        log.info("MyNewIntegration paused");
    }

    @Override
    public void onRelease() {
        log.info("MyNewIntegration released");
    }
}
```

### 5.2 设备集成

```java
package com.ecat.integration.MyDeviceIntegration;

import com.ecat.core.Integration.IntegrationDeviceBase;
import com.ecat.core.Device.DeviceBase;

public class MyDeviceIntegration extends IntegrationDeviceBase {

    @Override
    public void onInit() {
        log.info("MyDeviceIntegration initializing...");
        // 创建设备
        createDevice(config);
        log.info("MyDeviceIntegration initialized with {} devices", getAllDevices().size());
    }

    @Override
    public boolean createDevice(Map<String, Object> config) {
        MyDevice device = new MyDevice(config);
        device.load(core);
        device.init();
        addDevice(device);
        log.info("Created device: {}", config.get("id"));
        return true;
    }

    @Override
    public void onStart() {
        log.info("Starting all devices");
        for (DeviceBase device : getAllDevices()) {
            device.start();
        }
        log.info("MyDeviceIntegration started");
    }

    @Override
    public void onPause() {
        log.info("Stopping all devices");
        for (DeviceBase device : getAllDevices()) {
            device.stop();
        }
    }
}
```

### 5.3 加载第三方 JAR 的集成

```java
package com.ecat.integration.MyEmbeddedIntegration;

import com.ecat.core.Integration.IntegrationBase;
import com.ecat.core.Log.ClassLoaderCoordinateFilter;

public class MyEmbeddedIntegration extends IntegrationBase {

    @Override
    public void onStart() {
        log.info("MyEmbeddedIntegration starting...");

        // 注册第三方 JAR 的包名前缀
        // 这样第三方代码的日志也会路由到本集成
        String coordinate = loadOption.getIntegrationInfo().getCoordinate();
        ClassLoaderCoordinateFilter.registerPackagePrefix("com.thirdparty", coordinate);
        ClassLoaderCoordinateFilter.registerPackagePrefix("org.thirdparty.framework", coordinate);

        // 加载第三方 JAR
        loadThirdPartyJar();

        log.info("MyEmbeddedIntegration started");
    }

    @Override
    public void onRelease() {
        // 注销包名前缀（IntegrationBase 已自动处理）
        super.onRelease();
    }
}
```

---

## 6. 当前运行的集成日志情况

### 6.1 集成列表

| 坐标 | artifactId | 版本 | 日志情况 |
|------|-----------|------|----------|
| com.ecat:integration-ecat-common | integration-ecat-common | 1.0.0 | 正常 |
| com.ecat:integration-serial | integration-serial | 1.1.0 | 正常 |
| com.ecat:integration-ecat-core-ruoyi | integration-ecat-core-ruoyi | 1.0.0 | 正常（注册了 ruoyi/spring/mybatis/druid 前缀） |
| com.ecat:integration-httpserver | integration-httpserver | 1.0.0 | 正常 |
| com.ecat:integration-ecat-core-api | integration-ecat-core-api | 1.0.0 | 正常 |
| com.ecat:integration-sailhero | integration-sailhero | 1.0.0 | 正常（PMDevice 有详细日志） |
| com.ecat:integration-env-data-manager | integration-env-data-manager | 1.0.1 | 正常（insertRealdata SQL 日志） |
| com.ecat:integration-env-device-manager | integration-env-device-manager | 1.0.1 | 正常 |
| com.ecat:integration-virtual-devices | integration-virtual-devices | 1.0.1 | 日志较少（设计上只在初始化时输出） |
| com.ecat:integration-env-compare-manager | integration-env-compare-manager | 1.0.1 | 正常 |
| com.ecat:integration-env-data-handle | integration-env-data-handle | 1.0.1 | 正常（大量数据处理日志） |
| com.ecat:integration-aticloud | integration-aticloud | 1.0.1 | 正常 |

### 6.2 日志格式

日志输出格式（logback.xml 配置）：

```
%d{HH:mm:ss.SSS} [%traceId] [%coordinate] [%thread] %-5level %logger{36} - %msg%n
```

示例：
```
16:17:13.768 [pool-1-thread-1] [com.ecat:integration-env-data-manager] [INFO] c.e.i.E.EnvDataHandleIntegration - Insert realdata success:normal-device-01:pm10_value:0.0:2026-02-15T16:17:13.777
```

### 6.3 日志文件位置

| 日志类型 | 位置 |
|----------|------|
| 集成独立日志 | logs/integrations/{coordinate}/ecat.log |
| 汇总日志 | logs/ecat-all.log |
| 错误日志 | logs/ecat-error.log |
| Debug 日志 | logs/ecat-debug.log |

---

## 7. TraceId 说明

### 7.1 TraceId 作用

Trace ID 用于追踪跨线程、跨请求的日志关联。在 SSE 日志查看器和调试时特别有用。

### 7.2 TraceId 格式

- 格式：UUID 前 8 位字符（如 `a0fd3622`）
- 默认值：当无 Trace ID 时显示 `-`

### 7.3 TraceId 自动生成

TraceContext 会自动为每个线程生成 Trace ID（如果不存在）。

### 7.4 在异步任务中传播

Trace ID 随 MDC 上下文一起传播。详见 **第 3.1 节** 的推荐方式：使用 TaskManager 创建的 MDC 封装执行器会自动处理 Trace ID 传播。

> **注意**：`TraceContext.wrapRunnable()` 仅供 ecat-core 内部使用，不建议外部集成直接调用。

### 7.5 TraceId 在日志中的显示

```
[traceId]      → [a0fd3622]
[coordinate]   → [com.ecat:integration-env-data-manager]
[thread]       → [pool-1-thread-1]
```

---

## 8. Maven 配置要求

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

## 9. 常见问题

### Q1: 新集成的日志没有路由到正确文件？

检查：
1. 集成是否继承 `IntegrationBase`
2. onLoad() 是否正常执行（无异常）
3. 包名前缀是否正确注册

### Q2: 第三方 JAR 的日志没有路由到正确文件？

需要在 onStart() 中手动注册第三方包的名前缀：
```java
ClassLoaderCoordinateFilter.registerPackagePrefix("com.thirdparty", coordinate);
```

### Q3: 异步任务的日志丢失了坐标？

使用 TaskManager 创建 MDC 封装的执行器：
```java
ExecutorService executor = core.getTaskManager().createMdcExecutorService(4, "MyIntegration-pool");
```
详见 **第 3.1 节**。

### Q4: 如何调试日志路由？

启动服务时添加系统属性：
```bash
java -jar ecat-core.jar -Decat.log.turbo.debug=true -Decat.log.turbo.debug.loggers=MyClass
```

### Q5: 日志中显示 `core` 而不是集成坐标？

检查以下项目：
1. JAR 的 MANIFEST.MF 是否包含 `Ecat-Artifact-Id`
2. pom.xml 中的 maven-assembly-plugin 配置是否正确
3. 使用 `jar -xf your.jar META-INF/MANIFEST.MF` 查看内容

### Q6: SSE 日志出现重复？

已通过以下机制解决：
1. **订阅时间过滤**：LogBuffer 在订阅时记录时间戳，只广播订阅后产生的日志
2. **CopyOnWriteArraySet**：使用 Set 存储订阅者，自动去重避免并发订阅导致重复

---

## 10. 快速参考卡片

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                           日志路由快速参考                                  │
├─────────────────────────────────────────────────────────────────────────────┤
│ 匹配优先级：                                                               │
│   1. 注册的包名前缀（最长匹配）                                           │
│   2. 默认 core                                                            │
│                                                                            │
│ 新集成检查清单：                                                           │
│   □ 包名前缀独特（不与其他集成冲突）                                       │
│   □ 包名前缀覆盖所有业务类                                                 │
│   □ 集成继承 IntegrationBase                                             │
│   □ 异步执行器使用 TaskManager.createMdcExecutorService()               │
│   □ 定时任务使用 TaskManager.getMdcScheduledExecutorService()                       │
│   □ 嵌入式框架额外注册第三方包前缀                                         │
│                                                                            │
│ 核心 API（外部集成使用）：                                                 │
│   TaskManager.createMdcExecutorService(poolSize, prefix)                 │
│   TaskManager.getMdcScheduledExecutorService()                                       │
│   Log.setCoordinateMode(mode)                                              │
│                                                                            │
│ 调试：                                                                    │
│   -Decat.log.turbo.debug=true                                            │
│   -Decat.log.turbo.debug.loggers=MyClass                                  │
│   -Decat.log.coordinate.mode=LOG_FIRST                                    │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 11. 相关文件索引

| 文件 | 路径 |
|------|------|
| ClassLoaderCoordinateFilter | ecat-core/src/main/java/com/ecat/core/Log/ClassLoaderCoordinateFilter.java |
| IntegrationBase | ecat-core/src/main/java/com/ecat/core/Integration/IntegrationBase.java |
| IntegrationDeviceBase | ecat-core/src/main/java/com/ecat/core/Integration/IntegrationDeviceBase.java |
| logback.xml | ecat-core/src/main/resources/logback.xml |
| TraceContext | ecat-core/src/main/java/com/ecat/core/Utils/Mdc/TraceContext.java |
| MdcExecutorService | ecat-core/src/main/java/com/ecat/core/Utils/Mdc/MdcExecutorService.java |
| MdcScheduledExecutorService | ecat-core/src/main/java/com/ecat/core/Utils/Mdc/MdcScheduledExecutorService.java |
| MdcCoordinateConverter | ecat-core/src/main/java/com/ecat/core/Utils/Mdc/MdcCoordinateConverter.java |
| CoordinateConverter | ecat-core/src/main/java/com/ecat/core/Utils/CoordinateConverter.java |
| TraceIdConverter | ecat-core/src/main/java/com/ecat/core/Utils/TraceIdConverter.java |
| LogFactory | ecat-core/src/main/java/com/ecat/core/Utils/LogFactory.java |
| Log | ecat-core/src/main/java/com/ecat/core/Utils/Log.java |
| LogManager | ecat-core/src/main/java/com/ecat/core/Log/LogManager.java |
| LogBuffer | ecat-core/src/main/java/com/ecat/core/Log/LogBuffer.java |
| LogEntry | ecat-core/src/main/java/com/ecat/core/Log/LogEntry.java |
| IntegrationCoordinateHelper | ecat-core/src/main/java/com/ecat/core/Utils/IntegrationCoordinateHelper.java |
| EcatCoreRuoyiIntegration | ecat-integrations/ecat-core-ruoyi/src/main/java/com/ecat/integration/EcatCoreRuoyiIntegration/EcatCoreRuoyiIntegration.java |

---

## 12. 架构澄清

> 以下是对一些不准确描述的修正

### 12.1 关于"跳过的类"

**当前实现中没有框架类跳过逻辑**。日志路由完全依赖于包名前缀匹配：
- 如果某个第三方框架的日志需要路由到特定集成，需要**主动注册**该框架的包名前缀
- 例如：ruoyi 集成在 `onStart()` 中注册了 `com.ruoyi`、`org.springframework` 等前缀

### 12.2 关于"特殊规则"

**当前实现中没有 Env* 特殊规则**。所有路由都基于：
- 集成的包名前缀（由 IntegrationBase 自动注册）
- 嵌入式框架的包名前缀（需要手动注册）

### 12.3 关于 ClassLoader 映射机制

**当前实现已移除 ClassLoader 映射机制**，只使用包名前缀匹配：
- 旧版本：同时支持 ClassLoader 映射和包名前缀匹配
- 新版本：只使用包名前缀匹配（更稳定、可预测）

### 12.4 关于 registerClassLoader 方法

**当前实现中没有 registerClassLoader 方法**，只有 `registerPackagePrefix` 方法：
- 文档中的 `registerClassLoader` 已过时
- 当前使用纯 logger 名称匹配方案

---

## 13. 验证测试方法

### 13.1 验证日志路由正确性

1. 启动服务后，访问 http://localhost:9999/core-api/static/logs.html
2. 选择不同的集成，查看日志是否只显示对应集成的日志
3. 特别验证：env-data-manager 的 `insertRealdata` 日志不应出现在 aticloud

### 13.2 验证 TraceId 传播

1. 查看日志中是否有 `[traceId]` 字段
2. 异步任务的日志应与调用方有相同的 traceId
