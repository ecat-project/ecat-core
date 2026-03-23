# 幂等配置加载

ECAT 的 ConfigEntry 机制支持集成配置和设备配置的持久化。在系统启动时，框架会从持久化存储中重新加载所有已保存的配置条目。由于集成配置和设备配置在同一平面加载，加载顺序不可控，需要一个机制让集成在所有配置加载完毕后完成初始化。

相关文档：[Config Flow 配置向导](config-flow.md) | [Config Schema 配置定义](config-schema.md) | [设备开发规范](device-development.md)

## 概述

系统启动时，`IntegrationManager` 会遍历每个集成，依次执行：

1. 调用 `mergeEntries()` 进行版本升级
2. 对每个启用的 entry 调用 `createEntry()`
3. 调用 `onAllExistEntriesLoaded(entries)` 通知集成所有配置加载完毕

问题在于：`createEntry()` 被逐个调用，设备可能在集成级配置尚未全部加载时就尝试读取配置，导致获取到不完整或不一致的状态。

`ready` 字段和 `onAllExistEntriesLoaded` 钩子正是为了解决这个问题。

---

## ready 字段

`IntegrationBase` 提供了一个 `volatile boolean ready` 字段：

```java
/** 集成初始化就绪标志。所有已持久化 ConfigEntry 加载完成后由框架设为 true。 */
@Getter
private volatile boolean ready = false;
```

| 特性 | 说明 |
|------|------|
| 默认值 | `false` |
| 设为 true 的时机 | `onAllExistEntriesLoaded()` 执行后 |
| 线程安全性 | `volatile` 保证跨线程可见性 |
| 查询方式 | `isReady()`（Lombok @Getter 生成） |

设备或其他线程可以通过 `isReady()` 判断集成是否已完成初始化：

```java
if (!isReady()) {
    log.warn("Integration not ready yet, skipping device activation");
    return;
}
```

---

## onAllExistEntriesLoaded 钩子

在所有 `createEntry()` 调用完毕后，框架触发此钩子：

```java
/**
 * 在集成启动时，所有已持久化 ConfigEntry 的 createEntry() 调用完毕后触发。
 * 默认实现将 ready 设为 true。
 *
 * @param entries 该集成的所有已加载 ConfigEntry（包括 disabled 的）
 */
public void onAllExistEntriesLoaded(List<ConfigEntry> entries) {
    this.ready = true;
}
```

**要点：**

- 默认实现只做一件事：将 `ready` 设为 `true`
- 子类可以覆盖此方法添加自定义逻辑（如一致性检查）
- 如果覆盖，应调用 `super.onAllExistEntriesLoaded(entries)` 保留默认行为
- `entries` 参数包含该集成的**所有**已加载条目（包括 `enabled=false` 的）

---

## 使用方式一：直接使用 ready

最简单的场景——不需要覆盖钩子，直接依赖框架的默认行为：

```java
public class MyDevice extends DeviceBase {

    @Override
    public void onStart() {
        if (!isReady()) {
            log.warn("Integration not ready, will activate later");
            return;
        }
        doActivate();
    }

    private void doActivate() {
        // 正常激活逻辑
    }
}
```

框架在所有 entry 加载完毕后自动将 `ready` 设为 `true`，设备在下次检查时即可通过。

---

## 使用方式二：覆盖钩子做一致性检查

需要额外的一致性校验时，覆盖钩子并调用 `super`：

```java
public class MyIntegration extends IntegrationBase {

    private Map<String, Object> globalConfig;

    @Override
    public void onAllExistEntriesLoaded(List<ConfigEntry> entries) {
        // 自定义逻辑：从集成级配置中提取全局设置
        for (ConfigEntry entry : entries) {
            if ("global-settings".equals(entry.getUniqueId())) {
                globalConfig = (Map<String, Object>) entry.getData().get("config");
                break;
            }
        }

        // 一致性检查
        if (globalConfig == null) {
            log.warn("No global settings found, using defaults");
            globalConfig = getDefaultConfig();
        }

        // 必须调用 super 保留默认行为
        super.onAllExistEntriesLoaded(entries);
    }
}
```

**执行顺序：**

```
自定义逻辑（一致性检查）
        │
        ▼
super.onAllExistEntriesLoaded()  →  ready = true
        │
        ▼
其他线程通过 isReady() 感知就绪
```

---

## 模式一：缓存待激活（Pending Cache）

**适用场景：** 设备必须在集成配置就绪后才能工作。在配置加载期间缓存认活请求，等 ready 后批量处理。

```java
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.ecat.core.ConfigEntry.ConfigEntry;
import com.ecat.core.Device.DeviceBase;
import com.ecat.core.Integration.IntegrationBase;

public class SmartGatewayIntegration extends IntegrationBase {

    /** 集成级配置 */
    private volatile Map<String, Object> gatewayConfig;

    /** 待激活设备缓存：deviceId → 设备实例 */
    private final ConcurrentHashMap<String, DeviceBase> pendingDevices = new ConcurrentHashMap<String, DeviceBase>();

    @Override
    public ConfigEntry createEntry(ConfigEntry entry) {
        String uniqueId = entry.getUniqueId();

        if ("gateway-settings".equals(uniqueId)) {
            // 集成级配置
            gatewayConfig = (Map<String, Object>) entry.getData().get("config");
            return entry;
        }

        // 设备配置 —— 如果集成配置还没就绪，先缓存
        DeviceBase device = buildDevice(entry);
        if (gatewayConfig != null) {
            activateDevice(device, gatewayConfig);
        } else {
            pendingDevices.put(device.getDeviceId(), device);
            log.info("Device {} queued, waiting for gateway config", device.getDeviceId());
        }

        return entry;
    }

    @Override
    public void onAllExistEntriesLoaded(List<ConfigEntry> entries) {
        // 从全量列表中提取集成级配置
        for (ConfigEntry entry : entries) {
            if ("gateway-settings".equals(entry.getUniqueId())) {
                gatewayConfig = (Map<String, Object>) entry.getData().get("config");
            }
        }

        // 激活所有待处理设备
        List<String> activated = new ArrayList<String>();
        for (Map.Entry<String, DeviceBase> pending : pendingDevices.entrySet()) {
            activateDevice(pending.getValue(), gatewayConfig);
            activated.add(pending.getKey());
        }
        for (String id : activated) {
            pendingDevices.remove(id);
        }
        log.info("Activated {} pending devices after all entries loaded", activated.size());

        super.onAllExistEntriesLoaded(entries);
    }

    private void activateDevice(DeviceBase device, Map<String, Object> config) {
        device.applyGatewayConfig(config);
        device.onStart();
    }

    private DeviceBase buildDevice(ConfigEntry entry) {
        // 根据配置创建设备实例
        return new DeviceBase();
    }
}
```

**流程图：**

```
createEntry(gateway-settings) → 保存配置
createEntry(device-1)          → 配置已就绪，直接激活
createEntry(device-2)          → 配置未就绪，放入 pendingDevices
createEntry(device-3)          → 配置未就绪，放入 pendingDevices
        │
        ▼
onAllExistEntriesLoaded()
  ├── 提取集成配置
  ├── 遍历 pendingDevices 批量激活
  └── super → ready = true
```

---

## 模式二：标志位 + 周期检查（Flag + Periodic Check）

**适用场景：** 设备可独立运行，但部分功能依赖集成配置。设备定期检查就绪状态，就绪后自动开启高级功能。

```java
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.ecat.core.ConfigEntry.ConfigEntry;
import com.ecat.core.Device.DeviceBase;
import com.ecat.core.Integration.IntegrationBase;

public class SensorHubIntegration extends IntegrationBase {

    /** 集成级报警阈值配置 */
    private volatile Map<String, Double> alarmThresholds;

    /** 已注册的传感器 */
    private final Map<String, SensorDevice> sensors = new ConcurrentHashMap<String, SensorDevice>();

    @Override
    public ConfigEntry createEntry(ConfigEntry entry) {
        String uniqueId = entry.getUniqueId();

        if ("alarm-config".equals(uniqueId)) {
            // 集成级报警配置
            Map<String, Object> data = entry.getData();
            alarmThresholds = (Map<String, Double>) data.get("thresholds");
            return entry;
        }

        // 创建传感器设备，独立运行
        SensorDevice sensor = new SensorDevice(uniqueId, entry);
        sensor.onStart();
        sensors.put(uniqueId, sensor);

        return entry;
    }

    @Override
    public void onAllExistEntriesLoaded(List<ConfigEntry> entries) {
        // 提取报警阈值
        for (ConfigEntry entry : entries) {
            if ("alarm-config".equals(entry.getUniqueId())) {
                alarmThresholds = (Map<String, Double>) entry.getData().get("thresholds");
            }
        }

        // 通知所有传感器进入增强模式
        if (alarmThresholds != null) {
            for (SensorDevice sensor : sensors.values()) {
                sensor.enableAlarmMode(alarmThresholds);
            }
        }

        super.onAllExistEntriesLoaded(entries);
    }

    // ========== 内部设备类 ==========

    private class SensorDevice extends DeviceBase {

        private final String sensorId;
        private boolean alarmEnabled = false;
        private Map<String, Double> thresholds;

        SensorDevice(String sensorId, ConfigEntry entry) {
            super();
            this.sensorId = sensorId;
        }

        @Override
        public void onStart() {
            // 设备立即启动基础功能
            log.info("Sensor {} started in basic mode", sensorId);
            startBasicCollection();
        }

        /**
         * 开启报警模式（由 onAllExistEntriesLoaded 调用）
         */
        void enableAlarmMode(Map<String, Double> thresholds) {
            this.thresholds = thresholds;
            this.alarmEnabled = true;
            log.info("Sensor {} upgraded to alarm mode", sensorId);
        }

        /**
         * 传感器数据采集循环，自动检查就绪状态
         */
        void startBasicCollection() {
            ScheduledExecutorService scheduler = getScheduler();
            scheduler.scheduleAtFixedRate(new Runnable() {
                @Override
                public void run() {
                    collectData();

                    // 周期性检查是否可以升级
                    if (!alarmEnabled && isReady()) {
                        Map<String, Double> t = SensorHubIntegration.this.alarmThresholds;
                        if (t != null) {
                            enableAlarmMode(t);
                        }
                    }
                }
            }, 0, 5, TimeUnit.SECONDS);
        }

        private void collectData() {
            // 基础数据采集逻辑
        }
    }
}
```

**流程图：**

```
createEntry(alarm-config)      → 保存阈值
createEntry(sensor-1)          → 启动基础采集
createEntry(sensor-2)          → 启动基础采集
        │
        ▼
onAllExistEntriesLoaded()
  ├── 提取阈值
  ├── 遍历已注册传感器，启用报警模式
  └── super → ready = true

        或者（如果设备在 ready 后才创建）：

设备采集循环
  ├── collectData()（基础功能）
  └── if (!alarmEnabled && isReady()) → 自动升级
```

---

## 两种模式对比

| 特性 | 缓存待激活 | 标志位 + 周期检查 |
|------|----------------------|---------------------------|
| **适用场景** | 设备必须依赖集成配置才能工作 | 设备可独立运行，部分功能依赖配置 |
| **设备激活时机** | 配置就绪后批量激活 | 立即激活，就绪后升级功能 |
| **实时性** | 高（就绪后立即处理） | 取决于检查周期 |
| **复杂度** | 中等（需维护缓存） | 低（只需标志位检查） |
| **资源消耗** | 集中处理，一次性 | 周期轮询，持续消耗 |
| **推荐场景** | 网关、控制器等核心设备 | 传感器、采集器等边缘设备 |

---

## 注意事项

1. **异常不阻止其他集成** — 如果某个集成的 `onAllExistEntriesLoaded()` 抛出异常，`IntegrationManager` 会捕获并记录日志，不影响其他集成的加载。

2. **只在启动时调用** — `onAllExistEntriesLoaded()` 仅在系统启动阶段的 `loadExistingConfigEntries()` 流程中调用。运行时通过 Config Flow 新创建的 entry 不会触发此钩子。

3. **传入全量列表** — `entries` 参数包含该集成的**所有**已持久化条目，包括 `enabled=false` 的（已禁用的）条目。如果只需要启用的条目，需自行过滤：

   ```java
   List<ConfigEntry> enabledEntries = new ArrayList<ConfigEntry>();
   for (ConfigEntry e : entries) {
       if (e.isEnabled()) {
           enabledEntries.add(e);
       }
   }
   ```

4. **必须调用 super** — 覆盖 `onAllExistEntriesLoaded()` 时，务必在方法末尾调用 `super.onAllExistEntriesLoaded(entries)`，否则 `ready` 将不会被设为 `true`。

5. **volatile 保证可见性** — `ready` 字段使用 `volatile` 修饰，保证在一个线程中的修改对其他线程立即可见。设备线程可以通过 `isReady()` 安全地检查就绪状态。
