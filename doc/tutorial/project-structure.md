# 项目结构与核心文件

相关文档：[设备开发规范](device-development.md) | [I18n 国际化系统](i18n.md) | [Maven 配置规范](maven-config.md)

## 项目结构

```
ecat-integrations/
├── {integration-name}/                # 集成模块
│   ├── pom.xml                        # Maven配置文件
│   ├── src/main/java/
│   │   └── com/ecat/integration/{IntegrationName}Integration/
│   │       ├── {DeviceName}Device.java     # 设备类
│   │       ├── {AttributeName}Attribute.java # 属性类
│   │       └── {IntegrationName}DeviceBase.java # 基类
│   ├── src/main/resources/
│   │   ├── strings.json                   # 国际化资源文件
│   │   ├── ecat-config.yml                # 集成配置文件
│   │   └── vue-modules/                   # 前端组件
│   ├── src/main/assembly/
│   │   └── fatjar.xml                     # Assembly配置文件
│   └── src/test/java/
│       └── com/ecat/integration/{IntegrationName}Integration/
│           └── {DeviceName}DeviceTest.java  # 单元测试
```

### 文件关系

```
┌─────────────────────────────────────────────────────┐
│                  IntegrationBase                     │
│  onLoad() → onInit() → onStart() → onPause()       │
│  getConfigFlow() → AbstractConfigFlow (可选)         │
└──────────────────────┬──────────────────────────────┘
                       │ 1:N
          ┌────────────┼────────────────┐
          ▼            ▼                ▼
   ┌────────────┐ ┌──────────┐  ┌──────────┐
   │ DeviceBase │ │ ConfigFlow│  │ Service  │
   │ init/start │ │ (可选)    │  │ (可选)    │
   └─────┬──────┘ └──────────┘  └──────────┘
         │ 1:N
         ▼
   ┌────────────┐     ┌──────────────┐
   │ Attribute  │     │ strings.json │
   │ (属性)      │←───│ (国际化资源)  │
   └────────────┘     └──────────────┘
```

- **IntegrationBase**: 集成入口，管理生命周期
- **DeviceBase**: 设备基类，包含属性（Attribute）
- **Attribute**: 设备的具体属性（数值、命令、状态等）
- **strings.json**: 国际化资源，通过 I18nProxy 按 key 路径查找翻译
- **ConfigFlow**: 配置向导（可选），仅在需要引导用户配置时实现

---

## 核心文件说明

### 设备类文件

设备类继承自基类，负责具体的设备逻辑实现。注意基类并不是必须的，需要根据实际情况定义。

**示例：CODevice.java**
```java
public class CODevice extends SaimosenDeviceBase {
    private GasDeviceCommandAttribute commandAttr;

    @Override
    public void init() {
        super.init();
        createAttributes();
    }

    private void createAttributes() {
        commandAttr = new GasDeviceCommandAttribute(
            "gas_device_command",           // attributeID
            AttributeClass.DISPATCH_COMMAND // AttributeClass
        );
        setAttribute(commandAttr);

        commandAttr.registerCommand("ZERO_START",
            new GasDeviceCommandAttribute.CommandConfig(
                "零点校准开始",           // 显示名称（在strings.json中定义）
                0x0001,                  // 寄存器地址
                (short) 1                // 寄存器值
            )
        );
    }
}
```

### 属性类文件

属性类定义设备的具体属性和行为。

**StringCommandAttribute示例：**
```java
public class GasDeviceCommandAttribute extends StringCommandAttribute {
    public static class CommandConfig {
        private final String displayName;
        private final int registerAddress;
        private final short registerValue;

        public CommandConfig(String displayName, int registerAddress, short registerValue) {
            this.displayName = displayName;
            this.registerAddress = registerAddress;
            this.registerValue = registerValue;
        }
    }

    @Override
    protected String getI18nPrefix() {
        return "devices.codevice.gas_device_command";
    }
}
```

### 基类文件

基类提供通用功能和设备管理。

**SaimosenDeviceBase.java**
```java
public abstract class SaimosenDeviceBase extends DeviceBase {
    protected ModbusSource modbusSource;

    @Override
    public void init() {
        super.init();
        // 通用初始化逻辑
    }
}
```

### 国际化文件

**strings.json** 是集成国际化的核心文件，采用设备分组结构。

**标准结构：**
```json
{
  "devices": {
    "codevice": {
      "co": "CO浓度",
      "no2": "NO2浓度",
      "so2": "SO2浓度",
      "gas_device_command": "气体设备命令",
      "gas_device_command_commands": {
        "zero_start": "零点校准开始",
        "zero_check_start": "零点检查开始"
      }
    },
    "o3device": {
      // O3设备属性
    }
  }
}
```

### Maven配置文件

ECAT集成项目需要特定的Maven配置文件，这些文件对构建和I18n系统至关重要。

**关键配置元素**:
- **Ecat-Artifact-Id**: 写入MANIFEST.MF，I18n系统通过此值识别集成并加载对应资源
- **assembly配置**: 指定fatjar.xml文件位置
- **压缩设置**: 必须设为false以符合ECAT系统要求

**ecat-config.yml**:
```yaml
# 此文件为ecat集成的配置文件，无此文件则下面均为无。
dependencies: # 依赖的其他ecat集成的信息。可选项，默认无
  - artifactId: integration-modbus
```

**重要性说明**:
- Maven配置直接影响I18n系统的资源加载能力
- Ecat-Artifact-Id是I18n Proxy识别集成空间的关键
- 配置错误会导致国际化资源无法正确加载
