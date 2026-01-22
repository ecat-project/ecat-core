# ECAT 集成开发指南

## 目录

1. [项目概述](#项目概述)
2. [项目结构](#项目结构)
3. [核心文件说明](#核心文件说明)
   - [3.1 设备类文件](#31-设备类文件)
   - [3.2 属性类文件](#32-属性类文件)
   - [3.3 基类文件](#33-基类文件)
   - [3.4 国际化文件](#34-国际化文件)
   - [3.5 Maven配置文件](#35-maven配置文件)
4. [设备开发规范](#设备开发规范)
   - [4.1 设备类命名规则](#41-设备类命名规则)
   - [4.2 属性创建标准](#42-属性创建标准)
   - [4.3 构造函数使用规范](#43-构造函数使用规范)
5. [I18n国际化系统](#i18n国际化系统)
   - [5.1 I18n Proxy架构](#51-i18n-proxy架构)
   - [5.2 资源文件管理](#52-资源文件管理)
   - [5.3 设备分组国际化](#53-设备分组国际化)
   - [5.4 命令和选项国际化](#54-命令和选项国际化)
   - [5.5 ICU Message Format支持](#55-icu-message-format支持)
6. [I18nKeyPath路径管理](#i18nkeypath路径管理)
   - [6.1 路径构造和管理](#61-路径构造和管理)
   - [6.2 路径操作方法](#62-路径操作方法)
7. [单元测试要求](#单元测试要求)
   - [7.1 测试文件命名](#71-测试文件命名)
   - [7.2 测试覆盖要求](#72-测试覆盖要求)
   - [7.3 TestTools使用](#73-testtools使用)
   - [7.4 国际化测试](#74-国际化测试)
8. [开发工作流程](#开发工作流程)
   - [8.1 集成项目创建](#81-集成项目创建)
   - [8.2 设备开发步骤](#82-设备开发步骤)
   - [8.3 国际化实施流程](#83-国际化实施流程)
   - [8.4 测试验证流程](#84-测试验证流程)
9. [Maven配置规范](#maven配置规范)
   - [9.1 Maven Assembly Plugin配置](#91-maven-assembly-plugin配置)
   - [9.2 ecat-config.yml配置](#92-ecat-configyml配置)
   - [9.3 Assembly配置文件](#93-assembly配置文件)
10. [最佳实践](#最佳实践)
   - [10.1 代码规范](#101-代码规范)
   - [10.2 性能优化](#102-性能优化)
   - [10.3 错误处理](#103-错误处理)
11. [常见问题](#常见问题)

---

## 项目概述

ECAT (EcoAutomation) 是一个用于生态环境监测的工业数据自动化控制系统。系统采用Java开发，基于模块化插件架构，支持多种环境监测设备和数据处理。

**技术栈**：
- 后端：Java 8, Spring Boot 2.5.15, PostgreSQL 16+, Redis 7+
- 前端：Vue 2.x/3.x, Node.js 18+
- 构建工具：Maven 3.x, npm

---

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

---

## 核心文件说明

### 3.1 设备类文件

1. 设备类继承自基类，负责具体的设备逻辑实现。
2. 注意基类并不是必须要，需要根据实际情况定义

**示例：CODevice.java**
```java
public class CODevice extends SaimosenDeviceBase {
    // 设备特定属性
    private GasDeviceCommandAttribute commandAttr;

    @Override
    public void init() {
        super.init();
        createAttributes();
    }

    private void createAttributes() {
        // 使用i18n友好的构造函数
        commandAttr = new GasDeviceCommandAttribute(
            "gas_device_command",           // attributeID
            AttributeClass.DISPATCH_COMMAND // AttributeClass
        );
        setAttribute(commandAttr);

        // 注册命令
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

### 3.2 属性类文件

属性类定义设备的具体属性和行为。

**StringCommandAttribute示例：**
```java
public class GasDeviceCommandAttribute extends StringCommandAttribute {
    public static class CommandConfig {
        private final String displayName;    // 命令显示名称
        private final int registerAddress;    // 寄存器地址
        private final short registerValue;    // 寄存器值

        public CommandConfig(String displayName, int registerAddress, short registerValue) {
            this.displayName = displayName;
            this.registerAddress = registerAddress;
            this.registerValue = registerValue;
        }
    }

    // 国际化路径重写（可选）
    @Override
    protected String getI18nPrefix() {
        return "devices.codevice.gas_device_command";
    }
}
```

### 3.3 基类文件

基类提供通用功能和设备管理。

**SaimosenDeviceBase.java**
```java
public abstract class SaimosenDeviceBase extends DeviceBase {
    // 通用设备功能实现
    protected ModbusSource modbusSource;

    @Override
    public void init() {
        super.init();
        // 通用初始化逻辑
    }
}
```

### 3.4 国际化文件

**strings.json** 是集成国际化的核心文件，采用设备分组结构。

**标准结构：**
```json
{
  "devices": {
    "codevice": { // 设备属性名称规范见下
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

### 3.5 Maven配置文件

ECAT集成项目需要特定的Maven配置文件，这些文件对构建和I18n系统至关重要。

**pom.xml配置**:
每个集成都必须配置maven-assembly-plugin，这是构建集成JAR并写入I18n系统识别信息的关键组件。

**关键配置元素**:
- **Ecat-Artifact-Id**: 写入MANIFEST.MF，I18n系统通过此值识别集成并加载对应资源
- **assembly配置**: 指定fatjar.xml文件位置
- **压缩设置**: 必须设为false以符合ECAT系统要求

**ecat-config.yml配置**:
位于`src/main/resources/`目录，定义集成依赖关系：
```yaml
# 此文件为ecat集成的配置文件，无此文件则下面均为无。

dependencies: # 依赖的其他ecat集成的信息。可选项，默认无
  - artifactId: integration-modbus
```

**fatjar.xml配置**:
位于`src/main/assembly/`目录，定义JAR包组装结构，新项目可直接复制现有集成的配置文件。

**重要性说明**:
- Maven配置直接影响I18n系统的资源加载能力
- Ecat-Artifact-Id是I18n Proxy识别集成空间的关键
- 配置错误会导致国际化资源无法正确加载

---

## 设备开发规范

### 4.1 设备类命名规则

- **Java类名**: 使用PascalCase，如`CODevice`、`O3Device`
- **国际化key**: 使用snake_case，如`codevice`、`o3device`
- **转换规则**:
  - `CODevice` → `codevice`
  - `O3Device` → `o3device`
  - `SmartPowerStabilizer` → `smart_power_stabilizer`

### 4.2 属性创建标准

所有属性创建必须使用i18n友好的构造函数：

```java
// ✅ 正确：使用无displayName参数的构造函数
new NumericAttribute(
    "co",                     // attributeID
    AttributeClass.CO,        // AttributeClass
    AirVolumeUnit.PPM,        // Unit
    AirVolumeUnit.PPM,        // DisplayUnit
    2,                        // 精度
    true,                     // 可写
    false,                    // 历史记录
    molecularWeight           // 扩展参数
);

// ❌ 错误：使用null作为displayName参数
new NumericAttribute(
    "co",
    null,                     // 不应使用null displayName
    AttributeClass.CO,
    // ...
);

// 尽量避免硬编码，除非遇到通讯模块类属性是抽象的，需要用户定义名称才有意义的厂家，比如通道1属性没有意义，用户设置通道1属性为电压值则有意义
new NumericAttribute(
    "co",
    "CO浓度",                 // 这里建议使用用户定义的名称变量
    AttributeClass.CO,
    // ...
);
```

### 4.3 构造函数使用规范

根据属性类型选择合适的构造函数：

**NumericAttribute**:
```java
// 通用格式
new NumericAttribute(attributeID, AttributeClass, Unit, DisplayUnit, precision, writable, historic, ...);
```

**BinaryAttribute**:
```java
// 通用格式
new BinaryAttribute(attributeID, AttributeClass, initialValue);
```

**StringCommandAttribute**:
```java
// 通用格式
new StringCommandAttribute(attributeID, AttributeClass);
```

**ModbusScalableFloatSRAttribute** (状态属性):
```java
// 状态属性可以使用null单位
new ModbusScalableFloatSRAttribute(
    "device_status",
    AttributeClass.STATUS,
    null,  // 设备状态属性确实不需要单位
    null,
    0, false, false, ...
);
```

---

## I18n国际化系统

### 5.1 I18n Proxy架构

ECAT使用基于Proxy的国际化系统，通过ClassLoader实现资源隔离：

**核心组件**:
- **I18nProxy**: 国际化代理，每个集成拥有独立的代理实例
- **I18nHelper**: 创建代理的工厂类
- **I18nRegistry**: 全局国际化资源注册表
- **ClassLoader隔离**: 每个集成使用自己的ClassLoader加载资源

**使用示例**:
```java
public class WeatherSensor extends SenseCAPDeviceBase {
    // 父类已定义i18n代理，子类直接使用
    // protected final I18nProxy i18n;

    public void initAttributes() {
        setAttribute(new ModbusScalableFloatDRAttribute(
            "CONNECT",
            i18n.t("common.state.connected"),  // 使用模块化代理
            AttributeClass.WINDSPEED,
            // ...其他参数
        ));
    }
}
```

**代理创建**:
```java
// 在基类中初始化
public SenseCAPDeviceBase() {
    // this.getClass() 返回子类的Class，实现动态资源定位
    this.i18n = I18nHelper.createProxy(this.getClass());
}
```

### 5.2 资源文件管理

**资源文件结构**:
```
src/main/resources/
├── strings.json              # 原字符模版（英文key）
└── i18n/
    ├── zh_CN.json            # 中文翻译
    ├── en_US.json            # 英文翻译
    └── ja_JP.json            # 日文翻译
```

**Artifact识别**:
- Maven构建时将artifactId写入MANIFEST.MF
- 格式: `Ecat-Artifact-Id: integration-saimosen`
- I18nProxy通过JAR的MANIFEST.MF识别集成

**资源加载优先级**:
1. 集成空间: `integration.{artifactId}`
2. Core空间: `ecat` (fallback)
3. 避免不同模块间的键名冲突

### 5.3 设备分组国际化

**设备基类增强**:
```java
public abstract class SaimosenDeviceBase extends DeviceBase {
    @Override
    public String getTypeName() {
        return "saimosen_device";  // 设备类型名称
    }

    @Override
    public I18nKeyPath getI18nPrefix() {
        if(getTypeName() != null){
            return new I18nKeyPath("devices." + getTypeName() + ".", "");
        } else {
            return null;
        }
    }
}
```

**属性路径自动生成**:
```java
// AttributeBase中的路径生成逻辑
public I18nKeyPath getI18nDispNamePath(){
    if(device != null && device.getI18nPrefix() != null){
        // 设备分组路径: "devices.saimosen_device.{attribute_id}"
        return device.getI18nPrefix().withLastSegment(attributeID.toLowerCase(Locale.ENGLISH));
    } else {
        // 默认路径: "state.text_attr.{attribute_id}"
        return getI18nPrefixPath().withLastSegment(attributeID.toLowerCase(Locale.ENGLISH));
    }
}
```

### 5.4 命令和选项国际化

**命令属性后缀约定**:
- 命令选项: `{attribute_id}_commands`
- 选择选项: `{attribute_id}_options`
- 二值选项: `{attribute_id}_options`

**StringCommandAttribute实现**:
```java
public class GasDeviceCommandAttribute extends StringCommandAttribute {
    @Override
    protected I18nKeyPath getI18nCommandPathPrefix(){
        // 路径: "devices.saimosen_device.gas_device_command_commands"
        return getI18nDispNamePath().addLastSegment("commands");
    }

    public String getCommandI18nName(String commandKey) {
        I18nKeyPath commandPath = getI18nCommandPathPrefix().withLastSegment(commandKey);
        return i18n.t(commandPath.getFullPath());
    }
}
```

**StringSelectAttribute实现**:
```java
public class CalibratorGasSelectAttribute extends StringSelectAttribute {
    @Override
    protected I18nKeyPath getI18nOptionPathPrefix(){
        // 路径: "devices.saimosen_device.calibrator_gas_select_options"
        return getI18nDispNamePath().addLastSegment("options");
    }

    public String getOptionI18nName(String optionKey) {
        I18nKeyPath optionPath = getI18nOptionPathPrefix().withLastSegment(optionKey);
        return i18n.t(optionPath.getFullPath());
    }
}
```

### 5.5 ICU Message Format支持

**复数处理**:
```json
// strings.json
{
  "devices": {
    "qc_device": {
      "item_count": "Found {0, plural, one{# item} other{# items}}"
    }
  }
}
```

**选择处理**:
```json
{
  "devices": {
    "qc_device": {
      "greeting": "{0, select, MALE{Mr.} FEMALE{Ms.} other{Dear}}"
    }
  }
}
```

**数字格式化**:
```java
// Java代码
String message = i18n.t("devices.qc_device.temperature", 25.5);
// 结果: "当前温度：25.5°C"
```

---

## 单元测试要求

### 6.1 测试文件命名

测试文件必须与被测试的设备类对应：

- `CODevice.java` → `CODeviceTest.java`
- `SaimosenIntegration.java` → `SaimosenI18nTest.java`

### 6.2 测试覆盖要求

每个设备类至少需要一个测试用例验证国际化功能：

```java
@Test
public void testDeviceAttributeI18n() {
    // 测试设备初始化
    device.init();

    // 测试属性显示名称与strings.json对应
    AttributeBase<?> attr = device.getAttrs().get("co");
    assertEquals("CO浓度", attr.getDisplayName());

    // 测试命令属性国际化
    StringCommandAttribute commandAttr = (StringCommandAttribute) device.getAttrs().get("gas_device_command");
    assertEquals("气体设备命令", commandAttr.getDisplayName());
}
```

### 6.3 TestTools使用

使用TestTools工具类简化反射操作：

```java
import com.ecat.core.Utils.TestTools;

public class CODeviceTest {
    private CODevice device;

    @Before
    public void setUp() {
        device = new CODevice(config);
        device.init();
    }

    @Test
    public void testPrivateMethod() throws Exception {
        // 使用TestTools调用私有方法
        Object result = TestTools.invokePrivateMethod(device, "updateValue",
            new Object[]{123}, new Class[]{int.class});
        assertNotNull(result);
    }

    @Test
    public void testPrivateField() throws Exception {
        // 使用TestTools操作私有字段
        TestTools.setPrivateField(device, "modbusSource", mockSource);
        Object value = TestTools.getPrivateField(device, "modbusSource");
        assertEquals(mockSource, value);
    }
}
```

### 6.4 国际化测试

**测试用例模板**:
```java
@Test
public void testI18nPathWithDeviceSet() throws Exception {
    // 设置设备到集成
    TestTools.setPrivateField(integration, "device", device);

    // 测试带设备前缀的国际化路径
    String displayName = attr.getDisplayName();
    assertEquals("预期显示名称", displayName);
}

@Test
public void testI18nPathWithoutDeviceSet() throws Exception {
    // 不设置设备，测试默认国际化路径
    String displayName = attr.getDisplayName();
    assertEquals("默认显示名称", displayName);
}
```

---

## 常用工具类

### 7.1 TestTools工具类

TestTools是ECAT核心提供的反射操作工具类，封装了常用的测试反射方法：

**核心方法**:
```java
public class TestTools {
    /**
     * 设置对象的私有字段值
     */
    public static void setPrivateField(Object target, String fieldName, Object value) throws Exception

    /**
     * 获取对象的私有字段值
     */
    public static Object getPrivateField(Object target, String fieldName) throws Exception

    /**
     * 递归查找类及父类中的字段
     */
    public static Field findField(Class<?> clazz, String fieldName) throws NoSuchFieldException

    /**
     * 反射调用对象的私有方法（自动推断参数类型）
     */
    public static Object invokePrivateMethod(Object target, String methodName, Object... args) throws Exception

    /**
     * 反射调用对象的私有方法（指定参数类型）
     */
    public static Object invokePrivateMethodByClass(Object target, String methodName, Class<?>[] parameterTypes, Object... args) throws Exception

    /**
     * 递归查找类及父类中的方法
     */
    public static Method findMethod(Class<?> clazz, String methodName, Class<?>... parameterTypes) throws NoSuchMethodException
}
```

**参数类型处理**:
```java
// 自动推断参数类型的实现
for (int i = 0; i < args.length; i++) {
    if (args[i] instanceof Short) {
        parameterTypes[i] = short.class;
    } else if (args[i] instanceof Integer) {
        parameterTypes[i] = int.class;
    } else if (args[i] instanceof AttributeStatus) {
        parameterTypes[i] = AttributeStatus.class;
    } else {
        parameterTypes[i] = args[i].getClass();
    }
}
```

**使用示例**:
```java
@Test
public void testPrivateMethod() throws Exception {
    // 自动推断参数类型
    Object result = TestTools.invokePrivateMethod(device, "updateValue", 123);

    // 指定参数类型
    Object result2 = TestTools.invokePrivateMethodByClass(
        device,
        "updateValue",
        new Class<?>[]{int.class},
        123
    );

    // 操作私有字段
    TestTools.setPrivateField(device, "modbusSource", mockSource);
    Object value = TestTools.getPrivateField(device, "modbusSource");
}
```

### 7.2 I18nKeyPath工具类

I18nKeyPath是国际化路径管理类，支持路径前缀和最后段的分离操作：

**核心特性**:
```java
public class I18nKeyPath {
    private final String pathPrefix;    // 路径前缀，如 "devices.saimosen_device."
    private final String lastSegment;  // 最后段，如 "gas_device_command"
    private final String fullPath;     // 完整路径

    // 构造函数
    public I18nKeyPath(String pathPrefix, String lastSegment)

    // 从完整路径创建
    public static I18nKeyPath fromFullPath(String fullPath)

    // 获取方法
    public String getPathPrefix() { return pathPrefix; }
    public String getLastSegment() { return lastSegment; }
    public String getFullPath() { return fullPath; }
    public String getI18nPath() { return fullPath; }
}
```

**路径操作方法**:
```java
// 修改最后段
public I18nKeyPath withLastSegment(String newLastSegment)
// 示例: "devices.saimosen_device." with "device_status"
//      -> "devices.saimosen_device.device_status"

// 添加最后段（路径扩展）
public I18nKeyPath addLastSegment(String newLastSegment)
// 示例: "devices.saimosen_device.gas_device_command" + "commands"
//      -> "devices.saimosen_device.gas_device_command.commands"

// 添加后缀
public I18nKeyPath withSuffix(String suffix)
// 示例: "devices.saimosen_device.device_status" + "_active"
//      -> "devices.saimosen_device.device_status_active"

// 修改路径前缀
public I18nKeyPath withPathPrefix(String newPathPrefix)
```

**实际使用**:
```java
// 设备基础路径
I18nKeyPath devicePrefix = new I18nKeyPath("devices.saimosen_device.", "");

// 构建属性路径
I18nKeyPath attrPath = devicePrefix.withLastSegment("gas_device_command");

// 构建命令路径
I18nKeyPath commandPath = attrPath.addLastSegment("commands");
// -> "devices.saimosen_device.gas_device_command_commands"

// 具体命令
I18nKeyPath zeroStartPath = commandPath.withLastSegment("zero_start");
// -> "devices.saimosen_device.gas_device_command_commands.zero_start"
```

---

## 开发工作流程

### 8.1 集成项目创建

创建新的ECAT集成项目需要完成以下关键步骤，其中Maven配置对I18n系统至关重要。

**步骤1: 创建项目结构**
```
{integration-name}/
├── pom.xml                              # Maven配置文件
├── src/main/java/
│   └── com/ecat/integration/{IntegrationName}Integration/
│       ├── {DeviceName}Device.java
│       └── {IntegrationName}DeviceBase.java
├── src/main/resources/
│   ├── strings.json                   # 国际化资源文件
│   └── ecat-config.yml                 # 集成配置文件
├── src/main/assembly/
│   └── fatjar.xml                       # Assembly配置文件(复制现有集成)
└── src/test/java/
    └── com/ecat/integration/{IntegrationName}Integration/
        └── {DeviceName}DeviceTest.java
```

**步骤2: 配置pom.xml**
```xml
<project xmlns="http://maven.apache.org/POM/4.0.0">
    <modelVersion>4.0.0</modelVersion>

    <!-- ecat-core -->
    <parent>
        <groupId>com.ecat</groupId>
        <artifactId>integrations</artifactId>
        <version>1.0.0</version>
        <relativePath>../pom.xml</relativePath>
    </parent>

    <artifactId>integration-{name}</artifactId>
    <version>1.0.0</version>
    <name>integration-{name}</name>

    <dependencies>
        <!-- 根据实际需要添加依赖 -->
        <dependency>
            <groupId>com.ecat</groupId>
            <artifactId>integration-modbus</artifactId>
            <version>1.0.0</version>
            <scope>provided</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <!-- 关键配置：maven-assembly-plugin -->
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-assembly-plugin</artifactId>
                <version>3.6.0</version>
                <configuration>
                    <descriptors>
                        <descriptor>src/main/assembly/fatjar.xml</descriptor>
                    </descriptors>
                    <appendAssemblyId>false</appendAssemblyId>
                    <archiverConfig>
                        <compress>false</compress>
                    </archiverConfig>
                    <archive>
                        <manifestEntries>
                            <!-- 关键配置：I18n系统识别集成的重要标识 -->
                            <Ecat-Artifact-Id>${project.artifactId}</Ecat-Artifact-Id>
                        </manifestEntries>
                    </archive>
                </configuration>
                <executions>
                    <execution>
                        <phase>package</phase>
                        <goals><goal>single</goal></goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
```

**步骤3: 创建配置文件**

**ecat-config.yml** (src/main/resources/):
```yaml
# 此文件为ecat集成的配置文件，无此文件则下面均为无。

dependencies: # 根据实际依赖配置
  - artifactId: integration-modbus
```

**fatjar.xml** (src/main/assembly/ - 从现有集成复制): 从saimosen或其他集成复制此文件即可

**步骤4: 验证构建配置**
```bash
# 构建集成
mvn clean package

# 检查生成的JAR结构
jar tf target/integration-{name}-1.0.0.jar

# 验证MANIFEST.MF包含Ecat-Artifact-Id
jar xf target/integration-{name}-1.0.0.jar META-INF/MANIFEST.MF
grep "Ecat-Artifact-Id" META-INF/MANIFEST.MF
```

### 8.2 设备类开发

基于task-i18n-integration的实际开发经验，标准设备开发流程如下：

**1. 设备基类实现**
```java
public class CODevice extends SaimosenDeviceBase {
    @Override
    public String getTypeName() {
        return "saimosen_device";  // 设备分组名称
    }

    @Override
    public void init() {
        super.init();
        createAttributes();
    }

    private void createAttributes() {
        // 使用i18n友好的构造函数
        setAttribute(new NumericAttribute(
            "co",                     // attributeID
            AttributeClass.CO,        // AttributeClass
            AirVolumeUnit.PPM,        // Unit
            AirVolumeUnit.PPM,        // DisplayUnit
            2,                        // 精度
            true,                     // 可写
            false,                    // 历史记录
            molecularWeight           // 扩展参数
        ));

        // 命令属性
        GasDeviceCommandAttribute commandAttr = new GasDeviceCommandAttribute(
            "gas_device_command",
            AttributeClass.DISPATCH_COMMAND
        );
        setAttribute(commandAttr);

        // 注册命令（使用英文key）
        commandAttr.registerCommand("ZERO_START",
            new GasDeviceCommandAttribute.CommandConfig("零点校准开始", 0x0001, (short) 1)
        );
    }
}
```

**2. 特殊属性类继承**
```java
// GasDeviceCommandAttribute继承自StringCommandAttribute
public class GasDeviceCommandAttribute extends StringCommandAttribute {
    // 直接使用父类的i18n路径处理方法
    // 父类已实现设备分组路径支持
    // 父类已实现_commands后缀的命令路径处理
    // 无需额外重写i18n相关方法
}

// CalibratorGasSelectAttribute继承自StringSelectAttribute
public class CalibratorGasSelectAttribute extends StringSelectAttribute {
    // 直接使用父类的i18n路径处理方法
    // 父类已实现设备分组路径支持
    // 父类已实现_options后缀的选择路径处理
}
```

### 8.3 国际化实施流程

**1. strings.json结构设计**
```json
{
  "devices": {
    "saimosen_device": {
      // 普通属性
      "co": "CO浓度",
      "no2": "NO2浓度",
      "so2": "SO2浓度",
      "o3": "O3浓度",

      // 命令属性
      "gas_device_command": "气体设备命令",
      "gas_device_command_commands": {
        "zero_start": "零点校准开始",
        "zero_check_start": "零点检查开始",
        "span_calibration_start": "跨度校准开始"
      },

      // 选择属性
      "calibrator_gas_select": "校准器气体选择",
      "calibrator_gas_select_options": {
        "choose": "选择",
        "SO2": "二氧化硫",
        "no": "一氧化氮",
        "co": "一氧化碳",
        "M_GPTNO": "零气"
      }
    }
  }
}
```

**2. 设备命名转换规则**
```java
// Java类名 -> 国际化key
CODevice        -> codevice
O3Device        -> o3device
SmartPowerStabilizer -> smart_power_stabilizer
SampleTube      -> sample_tube
ParticulateZeroChecker -> particulate_zero_checker
```

**3. 命令和选项约定**
- 命令选项: `{attribute_id}_commands`
- 选择选项: `{attribute_id}_options`
- 二值选项: `{attribute_id}_options`
- 键名规范: 小写snake_case

### 8.4 测试验证流程

**1. 测试文件创建**
```java
// CODevice.java -> CODeviceTest.java
// SaimosenIntegration.java -> SaimosenI18nTest.java
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
```java
@Test
public void testDeviceAttributeI18n() {
    // 测试属性显示名称与strings.json对应
    AttributeBase<?> attr = device.getAttrs().get("co");
    assertEquals("CO浓度", attr.getDisplayName());

    // 测试命令属性国际化
    StringCommandAttribute commandAttr = (StringCommandAttribute) device.getAttrs().get("gas_device_command");
    assertEquals("气体设备命令", commandAttr.getDisplayName());
}

@Test
public void testCommandI18nWithDeviceSet() throws Exception {
    // 测试设备绑定时的i18n路径
    I18nKeyPath expectedPrefix = new I18nKeyPath("devices.saimosen_device.gas_device_command_commands", "");

    // 使用TestTools验证私有字段
    I18nKeyPath actualPrefix = (I18nKeyPath) TestTools.getPrivateField(commandAttr, "i18nCommandPathPrefix");
    assertEquals(expectedPrefix.getFullPath(), actualPrefix.getFullPath());
}

@Test
public void testOptionI18nName() throws Exception {
    // 测试选项国际化名称解析
    CalibratorGasSelectAttribute attr = (CalibratorGasSelectAttribute) device.getAttrs().get("calibrator_gas_select");

    // 使用TestTools调用私有方法
    String optionName = (String) TestTools.invokePrivateMethod(attr, "getOptionI18nName", "SO2");
    assertEquals("二氧化硫", optionName);
}
```

**3. 设备分组路径测试**
```java
@Test
public void testI18nPathWithDeviceGrouping() throws Exception {
    // 验证设备分组路径正确生成
    I18nKeyPath devicePrefix = (I18nKeyPath) TestTools.getPrivateField(device, "i18nPrefix");
    assertEquals("devices.saimosen_device.", devicePrefix.getPathPrefix());

    // 验证属性路径使用设备分组
    AttributeBase<?> attr = device.getAttrs().get("co");
    String displayName = attr.getDisplayName();
    assertNotNull(displayName);
    assertEquals("CO浓度", displayName);
}
```

---

## Maven配置规范

### 9.1 Maven Assembly Plugin配置

maven-assembly-plugin是ECAT集成构建的核心组件，负责将集成打包成正确的结构并写入I18n系统识别信息。

**完整配置说明**:
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-assembly-plugin</artifactId>
    <version>3.6.0</version>
    <configuration>
        <!-- 指定assembly配置文件位置 -->
        <descriptors>
            <descriptor>src/main/assembly/fatjar.xml</descriptor>
        </descriptors>

        <!-- 避免在JAR文件名后添加assembly id -->
        <appendAssemblyId>false</appendAssemblyId>

        <!-- 关键：ECAT系统要求关闭压缩 -->
        <archiverConfig>
            <compress>false</compress>
        </archiverConfig>

        <!-- 关键：写入I18n系统识别信息 -->
        <archive>
            <manifestEntries>
                <Ecat-Artifact-Id>${project.artifactId}</Ecat-Artifact-Id>
            </manifestEntries>
        </archive>
    </configuration>
    <executions>
        <execution>
            <phase>package</phase>
            <goals><goal>single</goal></goals>
        </execution>
    </executions>
</plugin>
```

### 9.2 ecat-config.yml配置

**ecat-config.yml**定义了集成间的依赖关系，影响系统加载顺序和I18n资源fallback机制。

**配置格式**:
```yaml
# 此文件为ecat集成的配置文件，无此文件则下面均为无。

dependencies: # 依赖的其他ecat集成的信息。可选项，默认无
  - artifactId: integration-modbus
```

**配置示例**:
```yaml
# Modbus依赖集成
dependencies:
  - artifactId: integration-modbus

# Serial依赖集成
dependencies:
  - artifactId: integration-serial

# 多依赖集成
dependencies:
  - artifactId: integration-modbus
  - artifactId: integration-serial

# 无依赖集成
dependencies: []
```

**作用说明**:
- 系统会确保依赖集成先于当前集成加载
- 影响I18n资源的fallback查找顺序
- 定义集成的加载顺序和依赖关系

### 9.3 Assembly配置文件

**fatjar.xml**定义了JAR包的组装结构，新集成项目可以直接复制现有集成的配置文件。

**标准配置内容**:
```xml
<assembly xmlns="http://maven.apache.org/ASSEMBLY/2.1.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/ASSEMBLY/2.1.0 http://maven.apache.org/xsd/assembly-2.1.0.xsd">
  <id>fatjar</id>
  <formats>
    <format>jar</format>
  </formats>
  <includeBaseDirectory>false</includeBaseDirectory>
  <fileSets>
    <fileSet>
      <directory>${project.build.outputDirectory}</directory>
      <outputDirectory>/</outputDirectory>
    </fileSet>
  </fileSets>
  <dependencySets>
    <dependencySet>
      <outputDirectory>/lib</outputDirectory>
      <useProjectArtifact>false</useProjectArtifact>
      <unpack>false</unpack>
    </dependencySet>
  </dependencySets>
</assembly>
```

**配置说明**:
- **formats**: 打包格式为JAR
- **includeBaseDirectory=false**: 不包含基础目录
- **fileSets**: 包含项目编译输出到根目录
- **dependencySets**: 依赖JAR包放在/lib目录下

**使用方法**:
新集成项目直接从saimosen、environnement-sa等现有集成复制`src/main/assembly/fatjar.xml`文件即可，无需修改。

---

## 最佳实践

### 10.1 代码规范

1. **导入规范**: 使用长类名导入，提高代码可读性
   ```java
   import java.util.Map;
   import java.util.HashMap;
   import java.lang.reflect.Method;
   ```

2. **构造函数选择**: 优先使用i18n友好的构造函数

3. **命名一致性**: 保持代码命名与国际化key的一致性

### 10.2 性能优化

1. **避免重复初始化**: 在`init()`方法中避免重复创建对象

2. **使用懒加载**: 对于不常用的属性，考虑使用懒加载

3. **资源管理**: 及时释放Modbus连接等资源

### 10.3 错误处理

1. **异常处理**: 在关键操作中添加异常处理
   ```java
   try {
       modbusResult = modbusSource.readHoldingRegisters(address, count);
   } catch (Exception e) {
       log.error("读取寄存器失败: {}", e.getMessage());
       attr.setStatus(AttributeStatus.MALFUNCTION);
   }
   ```

2. **状态管理**: 使用`AttributeStatus`正确标识属性状态

---

## 11. 常见问题

**Q1: 为什么不能使用带displayName参数的构造函数？**
A1: 因为国际化要求显示名称从strings.json中获取，使用硬编码的displayName会破坏国际化机制。

**Q2: ModbusScalableFloatSRAttribute的单位参数可以为null吗？**
A2: 可以为null。对于设备状态等属性，业务上确实不需要单位参数，这是合理的设计。

**Q3: 命令的key为什么要使用英文而不是中文？**
A3: 使用英文key符合国际化最佳实践，便于维护和扩展，中文显示名称应该在strings.json中定义。

**Q4: 如何使用I18nKeyPath构建正确的国际化路径？**
A4: I18nKeyPath提供了多种路径操作方法：
```java
// 设备前缀
I18nKeyPath devicePrefix = new I18nKeyPath("devices.saimosen_device.", "");

// 属性路径
I18nKeyPath attrPath = devicePrefix.withLastSegment("gas_device_command");

// 命令路径
I18nKeyPath commandPath = attrPath.addLastSegment("commands");
```

**Q5: 如何测试国际化路径是否正确？**
A5: 可以使用TestTools检查国际化路径：
```java
// 检查设备前缀
I18nKeyPath devicePrefix = (I18nKeyPath) TestTools.getPrivateField(device, "i18nPrefix");
assertEquals("devices.saimosen_device.", devicePrefix.getPathPrefix());

// 检查命令路径前缀
I18nKeyPath commandPrefix = (I18nKeyPath) TestTools.getPrivateField(commandAttr, "i18nCommandPathPrefix");
assertEquals("devices.saimosen_device.gas_device_command_commands", commandPrefix.getFullPath());
```

---

## 开发流程

1. **设备开发**:
   - 创建设备类和属性类
   - 使用i18n友好的构造函数
   - 实现设备特定逻辑

2. **国际化配置**:
   - 创建或更新strings.json
   - 使用正确的设备分组结构
   - 配置所有属性和命令的显示名称

3. **单元测试**:
   - 创建测试类
   - 使用TestTools进行测试
   - 验证国际化功能

4. **验证和构建**:
   - 运行测试套件
   - 执行Maven构建
   - 验证功能完整性

---

**注意事项**:
- 遵循"如非必要勿增实体"原则
- 保持与现有代码风格一致
- 及时更新文档和测试
- 确保向后兼容性
