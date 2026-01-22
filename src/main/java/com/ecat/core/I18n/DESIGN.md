
# ECAT Core I18n 系统设计文档

## 1. 概述
ECAT Core I18n 系统是一个基于 JSON 资源文件的国际化解决方案，专为 ECAT Core 插件架构设计。系统支持动态 locale 切换、ICU Message Format、资源隔离和缓存等高级功能。

## 2. 系统架构
### 2.1 核心组件
#### 2.1.1 I18nConfig
- 类型: 单例配置类  
- 职责: 管理全局 locale 设置  
- 关键特性:  
  - 线程安全的单例模式  
  - 动态 locale 切换  
  - 默认 locale 为英语  

```java
public class I18nConfig {
    private static I18nConfig instance;
    private Locale currentLocale;
    public static synchronized I18nConfig getInstance() {
        if (instance == null) {
            instance = new I18nConfig();
        }
        return instance;
    }
}
```

#### 2.1.2 I18nRegistry
- 类型: 中央注册表  
- 职责: 管理 I18nProxy 实例和全局翻译资源  
- 关键特性:  
  - 单例模式，全局唯一  
  - 管理多个 namespace 的资源  
  - 实现回退机制（integration → core → 默认）  
  - 支持资源更新和清理  

```java
public class I18nRegistry {
    private final Map<String, I18nProxy> proxyMap = new HashMap<>();
    private final Map<String, Map<String, Object>> allResources = new HashMap<>();
    public Object getTranslation(String namespace, String key) {
        // 实现回退机制
    }
}
```

#### 2.1.3 I18nProxy
- 类型: 代理类  
- 职责: 提供集成特定资源隔离和缓存  
- 关键特性:  
  - 每个 integration 有独立的 proxy 实例  
  - 支持缓存机制  
  - ICU Message Format 支持  
  - 自动注册到 I18nRegistry  

```java
public class I18nProxy {
    private final String artifactId;
    private final String namespace;
    private final ClassLoader classLoader;
    private final Map<Locale, Map<String, Object>> cache = new HashMap<>();
    public String t(String key, Object... params) {
        // 支持参数化翻译
    }
}
```

#### 2.1.4 I18nHelper
- 类型: 工具类  
- 职责: 提供 I18nProxy 创建和管理的便捷方法  
- 关键特性:  
  - 自动检测 artifactId（从 MANIFEST.MF）  
  - 缓存机制，避免重复创建  
  - 全局翻译方法  

```java
public class I18nHelper {
    private static final Map<String, I18nProxy> proxyCache = new ConcurrentHashMap<>();
    public static I18nProxy createProxy(Class<?> clazz) {
        // 自动检测 artifactId 并创建 proxy
    }
}
```

#### 2.1.5 ResourceLoader
- 类型: 资源加载器  
- 职责: JSON 资源文件加载，支持 ClassLoader 隔离  
- 关键特性:  
  - 简单 JSON 解析器  
  - 支持嵌套 key 访问  
  - 自动合并默认和 locale 特定资源  

```java
public class ResourceLoader {
    public Map<String, Object> loadResources(Locale locale) {
        // 加载 strings.json 和 i18n/{locale}.json
    }
}
```

### 2.2 命名空间规则
- Core: `ecat-core`  
- Integrations: `integration.{artifactId}`

## 3. 资源文件格式
### 3.1 JSON 格式
系统使用 JSON 格式存储翻译资源，支持嵌套结构：
```json
{
  "common": {
    "action": {
      "close": "Close",
      "connect": "Connect",
      "disconnect": "Disconnect"
    },
    "device": {
      "name": "Device",
      "type": "Sensor"
    }
  },
  "message": {
    "device_connected": "Device {0} connected",
    "error_occurred": "Error occurred: {0}"
  }
}
```

### 3.2 文件结构
```
src/main/resources/
├── strings.json                 # 默认资源（英语）
└── i18n/
    ├── zh-CN.json               # 简体中文
    ├── en-US.json               # 美式英语
    └── ...                      # 其他语言
```

## 4. ICU Message Format 支持
### 4.1 参数化翻译
```java
String message = proxy.t("message.device_connected", "Temperature Sensor");
// 输出: "Device Temperature Sensor connected"
```

### 4.2 复数形式
```json
"device_count": "{0, plural, one{# device} other{# devices}}"
```
```java
String singular = proxy.t("device_count", 1);   // "1 device"
String plural = proxy.t("device_count", 2);     // "2 devices"
```

### 4.3 选择格式
```json
"gender_welcome": "{0, select, male{Welcome Mr. {1}} female{Welcome Ms. {1}} other{Welcome {1}}}"
```

## 5. 集成方式
### 5.1 Core 模块使用
```java
public class CoreService {
    private final I18nProxy i18n = I18nHelper.createCoreProxy();
    public void doSomething() {
        String message = i18n.t("common.action.save");
        // 使用翻译
    }
}
```

### 5.2 Integration 模块使用
```java
public class SenseCAPDeviceBase extends DeviceBase {
    protected final I18nProxy i18n = I18nHelper.createProxy(this.getClass());
    public void showMessage() {
        String message = i18n.t("device.status.connected");
        // 使用翻译
    }
}
```

### 5.3 全局方法
```java
// 设置全局 locale
I18nHelper.setLocale("zh-CN");

// 全局翻译方法
String message = I18nHelper.t("common.action.save");
```

## 6. 配置要求
### 6.1 Maven 配置
需要在 `pom.xml` 中配置 `MANIFEST.MF` 条目：
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-assembly-plugin</artifactId>
    <version>3.3.0</version>
    <configuration>
        <archive>
            <manifestEntries>
                <Ecat-Artifact-Id>${project.artifactId}</Ecat-Artifact-Id>
            </manifestEntries>
        </archive>
    </configuration>
</plugin>
```

### 6.2 依赖关系
Integrations 模块需要依赖 `ecat-core`：
```xml
<dependency>
    <groupId>com.ecat</groupId>
    <artifactId>ecat-core</artifactId>
    <version>1.0.0</version>
    <scope>provided</scope>
</dependency>
```

## 7. 回退机制
系统实现了三层回退机制：
1. Integration 层: 首先查找 integration 特定资源  
2. Core 层: 如果 integration 层未找到，查找 core 资源  
3. 默认层: 如果都未找到，返回 key 本身  

## 8. 缓存策略
### 8.1 I18nProxy 缓存
- 每个 I18nProxy 实例维护自己的 locale 资源缓存  
- 缓存键为 Locale，值为资源 Map  
- 当 locale 切换时自动更新缓存  

### 8.2 I18nHelper 缓存
- 缓存已创建的 I18nProxy 实例  
- 基于 artifactId 进行缓存  
- 避免重复创建相同 proxy  

## 9. 线程安全性
- 所有单例类使用线程安全的实现方式  
- 缓存使用 ConcurrentHashMap 保证线程安全  
- I18nConfig 使用 synchronized 保证线程安全  

## 10. 使用示例
### 10.1 基本翻译
```java
I18nProxy proxy = I18nHelper.createCoreProxy();
String text = proxy.t("common.action.save");
```

### 10.2 参数化翻译
```java
String message = proxy.t("message.welcome", "John");
```

### 10.3 Locale 切换
```java
I18nHelper.setLocale("zh-CN");
String chineseText = proxy.t("common.action.save");
```

### 10.4 复数形式
```java
String text = proxy.t("device.count", 5);
```

## 11. 测试策略
### 11.1 单元测试
- 测试核心组件的基本功能  
- 测试 locale 切换  
- 测试缓存行为  
- 测试回退机制  

### 11.2 集成测试
- 测试 integration 模块的 I18n 集成  
- 测试资源文件加载  
- 测试 MANIFEST.MF 读取  

## 12. 注意事项
1. JSON 格式: 必须使用有效的 JSON 格式  
2. 编码: 资源文件使用 UTF-8 编码  
3. Key 命名: 使用点分隔的嵌套命名（如 `"common.action.save"`）  
4. 性能: 大量翻译时考虑缓存策略  
5. 内存: 注意资源文件的内存占用  

## 12. i18n对各集成模块的单元测试能力

### 12.1 概述

ECAT Core I18n 系统新增了单元测试控制能力，允许集成模块在测试过程中精确控制国际化资源的加载行为。通过 `ResourceLoader.setLoadI18nResources(false)` 方法，可以临时禁用 i18n 目录资源加载，确保单元测试只使用基础的 strings.json 资源，保障测试逻辑稳定从而验证属性的 displayname 功能是否正确返回有意义的值。

这个功能主要解决了以下测试需求：
- 验证 `attr.getDisplayName()` 方法能正确返回 strings.json 中的有意义值
- 避免动态生成的 i18n 翻译干扰单元测试
- 确保集成模块的国际化功能正常工作
- 提供可重现的测试环境

### 12.2 基本用法

#### 12.2.1 控制方法

```java
// 禁用 i18n 目录资源，只使用 strings.json
ResourceLoader.setLoadI18nResources(false);

// 启用 i18n 目录资源（默认状态）
ResourceLoader.setLoadI18nResources(true);

// 检查当前状态
boolean isEnabled = ResourceLoader.isLoadI18nResources();
```

#### 12.2.2 测试模式对比

**启用 i18n 资源（默认）**：
- 系统会同时加载 strings.json 和 i18n/{locale}.json
- 翻译结果会包含动态语言切换后的值
- 适合测试完整的国际化功能

**禁用 i18n 资源（测试模式）**：
- 系统只加载 strings.json
- 翻译结果始终为基础资源中的值
- 适合验证基础功能和 displayname 属性

### 12.3 验证测试的示例

以 saimosen 集成的 `CalibratorGasSelectAttribute` 测试为例,
验证 `attr.getDisplayName()` 方法能正确返回 strings.json 中定义的有意义值：

```java
import com.ecat.core.I18n.ResourceLoader;
import com.ecat.integration.SaimosenIntegration.CalibratorGasSelectAttribute;
import org.junit.Before;
import org.junit.Test;
import org.junit.After;
import static org.junit.Assert.*;

public class CalibratorGasSelectAttributeTest {

    @Before
    public void setUp() {
        // 禁用 i18n 目录资源，确保测试只使用 strings.json
        ResourceLoader.setLoadI18nResources(false);
    }

    @After
    public void tearDown() {
        // 恢复 i18n 功能
        ResourceLoader.setLoadI18nResources(true);
    }

    @Test
    public void testDisplayNameReturnsMeaningfulValue() {
        // 创建属性实例
        CalibratorGasSelectAttribute attr = new CalibratorGasSelectAttribute(
            "gas",
            mockAttrClass,
            true,
            options,
            mockModbusSource,
            (short) 0x46
        );

        // 属性没有绑定设备前，getDisplayName返回的是属性默认path规则，见各类属性定义
        assertEquals("state.select_attr.calibrator_gas_select", attr.getDisplayName());

        // 属性绑定设备后，会自动应用规则，path为本集成内的设备+path规则
        // 如 devices.xxx_device.calibrator_gas_select
        attr.setDevice(...);

        // 验证 getDisplayName() 返回 strings.json 中的有意义的值
        // 这里应该返回 "Calibrator Gas Selection" 而不是 i18n key
        assertEquals("Calibrator Gas Selection", attr.getDisplayName());
    }
}
```

### 12.4 最佳实践

#### 12.4.1 测试组织建议

1. **测试类结构**：
   ```java
   public class MyAttributeTest {

       @Before
       public void setUp() {
           // 默认禁用 i18n 资源
           ResourceLoader.setLoadI18nResources(false);
       }

       @After
       public void tearDown() {
           // 恢复默认状态
           ResourceLoader.setLoadI18nResources(true);
       }

       @Test
       public void testBasicDisplayName() {
           // 基础功能测试
       }
   }
   ```

2. **测试数据准备**：
   - 确保 strings.json 中包含所有必要的键值对
   - 为不同测试场景准备相应的断言值
   - 使用常量管理预期的显示名称

#### 12.4.2 注意事项

1. **状态管理**：
   - 始终在 `@After` 方法中恢复 i18n 状态
   - 避免测试间的状态污染
   - 考虑使用测试规则（Test Rule）管理状态

2. **测试覆盖**：
   - 同时测试启用和禁用 i18n 的场景
   - 验证边界情况和错误处理
   - 确保测试的可重复性

3. **性能考虑**：
   - 大量测试时考虑批量操作
   - 避免频繁切换 i18n 状态
   - 合理组织测试执行顺序

#### 12.4.3 集成指导

1. **新集成开发**：
   - 在开发初期就加入 i18n 控制测试
   - 建立 strings.json 值的验证机制
   - 确保所有属性都有正确的 displayname 配置

2. **现有集成改造**：
   - 逐步添加 i18n 控制测试
   - 验证现有功能的兼容性
   - 更新测试文档和示例

通过这些新的单元测试能力，集成开发者可以更有效地验证其国际化功能的正确性，确保用户界面在不同语言环境下都能提供有意义的显示名称。

## 13. 扩展性
系统设计考虑了未来扩展：
- 支持自定义资源加载器  
- 支持插件式 Message Format 实现  
- 支持分布式缓存  
- 支持动态资源更新  

## 14. 最佳实践
1. 只对要展示给用户的string进行翻译，不需要对log等日志信息翻译
2. 抛出异常是否翻译需要由后续异常处理时是否需要将信息提示给用户决定，能确定返回用户的使用翻译，存疑（可能返回用户）或不返回的一律都不翻译
3. 尽量遵循core或其他已有的集成的strings.json结构，比如多级key命名习惯，降低学习成本，比如core/State下用到的key-value你放到State下的分类，core/Device下用到key-value放到Device下，与代码路径贴切，人类好找，尽量不要交叉。

## 15. strings.json
|第一级key|含义/用途|常见使用场景|
|-|-|-|
|`common`|通用文本（跨场景复用）|全局通用的操作（如“打开”“关闭”）、时间（如星期几）等。|
|`device`|实体相关文本|设备的信息（如型号、厂家）、类型描述（如“水泵”）、用途描述等。|
|`state`|属性和状态相关文本（如state.class为属性分类）|属性的名称（如传感器“温度”）、状态描述（如“低电量”）、单位（如“°C”）等；特定状态的文本（如“充电中”“待机”）。|
|`service`|服务相关文本|服务名称（如“校准”）、服务描述、服务参数（如“偏移量”）等。|
|`config_flow`|配置流程相关文本，比如设备配置、工作流配置|配置步骤标题、输入字段提示（如“IP地址”）、配置错误（如“无效设备ID”）、配置中断提示、配置任务工作流的进度和错误信息等。|
|`device_automation`|设备自动化相关文本|自动化触发条件（如“设备开启时”）、动作类型（如“关闭设备”）、条件描述等。|
|`dialog`|弹窗/对话框相关文本|弹窗标题、按钮文本（如“确认删除？”）、提示信息等。|
|`error`|系统错误相关文本|重要错误提示信息等。|


## 16. 遗留问题
1. 目前只有BinaryAttribute支持值的i18n，比如 getDisplayValue()，其他属性类型不支持
2. 如果要Command、Select支持 getDisplayValue()，setDisplayValue() 目前问题是不支持动态切换，比如中文时在流程actions中设置attr的输入值为中文名了，切换到英文后如果Command切换到英文，则流程值与当前attr要求不匹配，流程无法执行，目前还没有好的解决办法。如果一种语言一直使用不切换则没有问题
