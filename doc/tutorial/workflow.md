# 开发工作流程

相关文档：[设备开发规范](device-development.md) | [I18n 国际化系统](i18n.md) | [单元测试](testing.md) | [Maven 配置规范](maven-config.md)

## 集成项目创建

**步骤1: 创建项目结构**
```
{integration-name}/
├── pom.xml
├── src/main/java/
│   └── com/ecat/integration/{IntegrationName}Integration/
│       ├── {DeviceName}Device.java
│       └── {IntegrationName}DeviceBase.java
├── src/main/resources/
│   ├── strings.json
│   └── ecat-config.yml
├── src/main/assembly/
│   └── fatjar.xml                       # 从现有集成复制
└── src/test/java/
    └── com/ecat/integration/{IntegrationName}Integration/
        └── {DeviceName}DeviceTest.java
```

**步骤2: 配置pom.xml**

详见 [Maven 配置规范](maven-config.md)。

**步骤3: 创建配置文件**

ecat-config.yml (src/main/resources/):
```yaml
dependencies:
  - artifactId: integration-modbus
```

fatjar.xml 从现有集成复制。

**步骤4: 验证构建**
```bash
mvn clean package
jar tf target/integration-{name}-1.0.0.jar
jar xf target/integration-{name}-1.0.0.jar META-INF/MANIFEST.MF
grep "Ecat-Artifact-Id" META-INF/MANIFEST.MF
```

---

## 设备开发

**1. 设备基类实现**
```java
public class CODevice extends SaimosenDeviceBase {
    @Override
    public String getTypeName() {
        return "saimosen_device";
    }

    @Override
    public void init() {
        super.init();
        createAttributes();
    }

    private void createAttributes() {
        setAttribute(new NumericAttribute(
            "co", AttributeClass.CO, AirVolumeUnit.PPM, AirVolumeUnit.PPM,
            2, true, false, molecularWeight
        ));

        GasDeviceCommandAttribute commandAttr = new GasDeviceCommandAttribute(
            "gas_device_command", AttributeClass.DISPATCH_COMMAND
        );
        setAttribute(commandAttr);

        commandAttr.registerCommand("ZERO_START",
            new GasDeviceCommandAttribute.CommandConfig("零点校准开始", 0x0001, (short) 1)
        );
    }
}
```

**2. 特殊属性类继承**
```java
// 继承 StringCommandAttribute
public class GasDeviceCommandAttribute extends StringCommandAttribute {
    // 父类已实现设备分组路径和 _commands 后缀支持
}

// 继承 StringSelectAttribute
public class CalibratorGasSelectAttribute extends StringSelectAttribute {
    // 父类已实现设备分组路径和 _options 后缀支持
}
```

---

## 国际化实施

**1. strings.json 结构设计**
```json
{
  "devices": {
    "saimosen_device": {
      "co": "CO浓度",
      "no2": "NO2浓度",
      "gas_device_command": "气体设备命令",
      "gas_device_command_commands": {
        "zero_start": "零点校准开始",
        "zero_check_start": "零点检查开始"
      },
      "calibrator_gas_select": "校准器气体选择",
      "calibrator_gas_select_options": {
        "SO2": "二氧化硫",
        "co": "一氧化碳"
      }
    }
  }
}
```

**2. 命名转换规则**
```java
CODevice               → codevice
O3Device               → o3device
SmartPowerStabilizer   → smart_power_stabilizer
```

**3. 约定**
- 命令选项: `{attribute_id}_commands`
- 选择选项: `{attribute_id}_options`
- 键名规范: 小写 snake_case

---

## 测试验证

**1. 测试文件创建**
```java
public class CODeviceTest {
    private CODevice device;
    private AutoCloseable mockitoCloseable;

    @Before
    public void setUp() throws Exception {
        mockitoCloseable = MockitoAnnotations.openMocks(this);
        Map<String, Object> config = new HashMap<>();
        device = new CODevice(config);
        device.init();
    }

    @After
    public void tearDown() throws Exception {
        mockitoCloseable.close();
    }
}
```

**2. 国际化功能测试**

详见 [单元测试](testing.md)。

---

## 注意事项

- 遵循"如非必要勿增实体"原则
- 保持与现有代码风格一致
- 及时更新文档和测试
- 确保向后兼容性
