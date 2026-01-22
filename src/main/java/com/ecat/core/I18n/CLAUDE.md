
I18n实现思路
在EcatCore（插件式架构）中开发I18n能力，实现下面特性

需求要求
1. EcatCore运行时初始化I18n模块，完成Core的基础国际化资源注册，I18n维持一个完整的国际化资源，比如map结构，map的key为国际化空间集成名称，core的空间集成key为ecat，其他集成的集成空间key集中在integration下，如integration.{artifactId}，value为该空间下的具体资源信息，数据结构还是map。
2. Core使用classloader加载每个集成Integration，每个Integration模块有自己的资源文件，使用自己的ClassLoader加载资源完成隔离，在国际化时优先使用自己空间内的i18n的proxy资源信息，如果找不到可fallback到core的common空间查找国际化资源，每个proxy要记录被代理的集成对象的信息，不要使用其他集成的国际化资源，避免了不同模块间的键名冲突
3. 支持ICU Message Format特性，使用icu4j
4. 支持动态加载，Core可以通过控制I18n的locale全局控制集成完成语言的在线切换，I18n应能拿到所有I18nProxy并控制完成翻译的切换和资源重新加载。
5. 支持缓存技术，避免同一个集成不同类重复加载同一个集成国际化资源
6. 支持集成移除指定自己的I18n资源，因集成可以被卸载，需要移除对应资源
7. 【这条如何实现可以商讨】最好能在代码编译检查每一条国际化资源是否存在，不存在就给出错误提示，避免运行时报错

本次任务要求
1、完成ECatCore的i18n模块开发，i18n模块的单元测试，单元测试中包含使用方法演示
2、完成对ecat-integrations/sensecap集成的改造，补充单元测试
3、完成对ecat-integrations/env-device-calibration/src/main/java/com/ecat/integration/EnvDeviceCalibrationIntegration/AccuracyExecutor.java具体文件的改造

架构设计

设计目标
1. 全局统一管理：采用全局本地化配置（非线程级），Core模块统一管控本地化机制，所有功能共享同一Locale设置。
2. 低侵入集成：各integration集成无需关心本地化实现细节，通过Core提供的简单API即可完成翻译。
3. 支持复杂场景：本地化文件需支持单复数、多词义（同一词汇在不同上下文的不同翻译）。
4. 模块化扩展：Core与各integration的本地化资源隔离，支持动态添加集成而不影响核心逻辑。

核心原则
- 分层设计：Core模块提供本地化核心能力（资源加载、翻译查询、Locale管理），集成模块仅调用API使用能力。
- 资源隔离：Core与集成的本地化文件独立存放，集成资源可覆盖Core的默认翻译（针对特殊术语）。
- 全局Locale：通过Core的配置类管理全局唯一Locale，所有翻译均基于此Locale生效，支持动态切换。




┌─────────────────────────────────────────────────────────┐
│                     应用层                              │
│  （业务逻辑、API接口等，调用I18n API获取翻译）           │
└─────────────────────────────┬───────────────────────────┘
                              ↓
┌─────────────────────────────────────────────────────────┐
│                     Core I18n模块                        │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐     │
│  │  I18nProxy   │ │ I18nRegistry│  │ 全局配置    │     │
│  │  （具体工具）│ │（统一管理）  │ │（Locale管理）│     │
│  │             │  │             │  │             │     │
│  └─────────────┘  └─────────────┘  └─────────────┘     │
└───┬───────────────────────┬───────────────────────┬─────┘
    ↓                       ↓                       ↓
┌─────────────┐   ┌─────────────┐   ┌─────────────┐   ...
│ Core本地化  │   │ Integration A│   │ Integration B│
│  资源文件   │   │ 本地化资源文件 │   │ 本地化资源文件 │
└─────────────┘   └─────────────┘   └─────────────┘   ...

单独类实现思路

public class WeatherSensor{
    
    // 导入的I18n代理实例
    private final I18nProxy i18n = I18nHelper.createProxy(this.getClass());
    
    // 在属性创建中使用模块化i18n
    setAttribute(new ModbusScalableFloatDRAttribute(
        "CONNECT",
        i18n.t("common.state.connected"),  // 使用模块化代理而不是全局I18n.t()
        AttributeClass.WINDSPEED,
        // ...
    ));
}
继承类实现思路

// 父类：SenseCAPDeviceBase.java
public class SenseCAPDeviceBase {
    // 父类中定义i18n，子类可直接使用
    protected final I18nProxy i18n;
    
    // 父类构造器中初始化，利用this.getClass()动态获取子类的Class
    public SenseCAPDeviceBase() {
        // 当子类实例化时，this指向子类实例，this.getClass()返回子类的Class
        this.i18n = I18nHelper.createProxy(this.getClass());
    }
}

// 子类：WeatherSensor.java（无需重复定义i18n）
public class WeatherSensor extends SenseCAPDeviceBase {
    public void initAttributes() {
        setAttribute(new ModbusScalableFloatDRAttribute(
            "CONNECT",
            i18n.t("common.state.connected"),  // 先搜索自己集成空间下integration.integration-sensecap，找不到再搜索ecat空间下
            AttributeClass.WINDSPEED
            // ...其他参数
        ));
    }
}

// 其他包的子类也可以直接复用
package com.other.package;
public class TemperatureSensor extends SenseCAPDeviceBase {
    public void initAttributes() {
        setAttribute(new SomeAttribute(
            "CONNECT",
            i18n.t("common.state.connected"),  // 自动使用TemperatureSensor.class对应的资源
            // ...
        ));
    }
}


国际化资源文件格式介绍
1. 原字符模版，是json结构的key为全英文的dict，路径：约定每个 JAR 的原字符模版资源文件统一放在 resources/ 目录下，命名格式为 strings.json
2. 翻译资源为根据原字符模版路径对不同语言的翻译后文件，路径：约定每个 JAR 的翻译资源文件统一放在 resources/i18n/ 目录下，命名格式为 {language}_{country}.json（例如 en_US.json）
3. 为了让每个 JAR 能被识别出自己的artifactId，需要在构建 JAR 时，通过 Maven 将artifactId写入META-INF/MANIFEST.MF，<Ecat-Artifact-Id>${project.artifactId}</Ecat-Artifact-Id>，构建后，JAR 包的META-INF/MANIFEST.MF中会包含如：Ecat-Artifact-Id: integration-sensecap
4. 从Class对象找到其所在的 JAR 包 → 读取该 JAR 的资源对应locale的翻译资源路径 → 刷新I18nProxy的数据
5. 翻译资源文件不需要LLM生成，是有翻译脚本批量完成

原字符模版结构（strings.json），以Core举例，不一定对
{
  "common": {
    "action": {
      "close": "Close",
      "connect": "Connect",
      "disable": "Disable",
      "disconnect": "Disconnect",
      "enable": "Enable"
    },
    "device_automation": {
      "action_type": {
        "toggle": "Toggle {entity_name}",
        "turn_off": "Turn off {entity_name}",
        "turn_on": "Turn on {entity_name}"
      },
      "condition_type": {
        "is_off": "{entity_name} is off",
        "is_on": "{entity_name} is on"
      }
    },
    "generic": {
      "temp": "Current temperature: {0, number, #.#°C}",
      "error": "Found {0, plural, one{# error} other{# errors}}"
    },
    "state": {
      "active": "Active",
      "auto": "Auto",
      "charging": "Charging",
      "closed": "Closed",
      "closing": "Closing",
      "connected": "Connected",
      "disabled": "Disabled",
      "discharging": "Discharging"
    },
    "time": {
      "monday": "Monday",
      "tuesday": "Tuesday"
    }
  }
}

中文翻译 (zh_CN.json):
{
  "common": {
    "action": {
      "close": "关闭",
      "connect": "连接",
      "disable": "禁用",
      "disconnect": "断开",
      "enable": "启用"
    },
    "device_automation": {
      "action_type": {
        "toggle": "切换 {entity_name}",
        "turn_off": "关闭 {entity_name}",
        "turn_on": "打开 {entity_name}"
      },
      "condition_type": {
        "is_off": "{entity_name} 已关闭",
        "is_on": "{entity_name} 已开启"
      }
    },
    "generic": {
      "temp": "当前温度：{0, number, #.#°C}",
      "error": "发现 {0, plural, one{# 个错误} other{# 个错误}}"
    },
    "state": {
      "active": "活跃",
      "auto": "自动",
      "charging": "充电中",
      "closed": "已关闭",
      "closing": "正在关闭",
      "connected": "已连接",
      "disabled": "已禁用",
      "discharging": "放电中"
    },
    "time": {
      "monday": "周一",
      "tuesday": "周二"
    }
  }
}

I18nRegistry设计
1. 单例模式，被Core初始化
2. 设置统一的locale并重载新的所有资源
3. 管理每一个I18nProxy实例，每一个artifactId对应一个I18nProxy实例
4. 卸载指定I18nProxy实例，释放资源
5. 管理一个完整的map结构的翻译资源

I18nProxy设计
1. 关联集成必要的信息比如artifactId，对应classloader等必要的信息
2. 关联I18nRegistry
3. 自己完成创建后添加到I18nRegistry
4. 根据I18nRegistry的locale重新加载集成的翻译资源并提交给I18nRegistry
5. 提供获取翻译资源接口函数，根据指定路径访问I18nRegistry拿到信息，并根据输入参数完成转换后返回String结果
6. 具备缓存能力，同一个artifactId仅加载一次，其他类再次加载此artifactId直接返回已经加载好的I18nProxy对象

ICU Message Format支持
- 复数处理: {0, plural, one{# item} other{# items}}
- 选择处理: {0, select, MALE{Mr.} FEMALE{Ms.} other{Dear}}
- 数字格式化: {0, number, currency}
- 日期格式化: {0, date, long}


使用示例


public class MyIntegration {
    private final I18nProxy i18n = I18nHelper.createProxy(this.getClass());
    
    public void showMessage() {
        // 基础翻译
        String message = i18n.t("welcome.message");
        
        // 带参数翻译
        String greeting = i18n.t("greeting.user", "John");
        
        // ICU复数处理
        String items = i18n.pluralize("cart.items", 5);
        
        // ICU数字格式化
        String price = i18n.formatNumber("product.price", 99.99);
    }
}

Strings.json约定

第一级的约定
|第一级key|含义/用途|常见使用场景|
|-|-|-|
|`common`|通用文本（跨场景复用）|全局通用的操作（如“打开”“关闭”）、状态（如“开”“关”）、时间（如星期几）等。|
|`device`|实体相关文本|设备的信息（如型号、厂家）、类型描述（如“水泵”）、用途描述等。|
|`attr`|属性和状态相关文本（部分集成会单独用，core中常包含在`common.attr`中）|属性的名称（如传感器“温度”）、状态描述（如“低电量”）、单位（如“°C”）等；特定状态的文本（如“充电中”“待机”）。|
|`service`|服务相关文本|服务名称（如“校准”）、服务描述、服务参数（如“偏移量”）等。|
|`config_flow`|配置流程相关文本，比如设备配置、工作流配置|配置步骤标题、输入字段提示（如“IP地址”）、配置错误（如“无效设备ID”）、配置中断提示、配置任务工作流的进度和错误信息等。|
|`device_automation`|设备自动化相关文本|自动化触发条件（如“设备开启时”）、动作类型（如“关闭设备”）、条件描述等。|
|`dialog`|弹窗/对话框相关文本|弹窗标题、按钮文本（如“确认删除？”）、提示信息等。|
说明：
1. 第二级key你可以查看项目推荐一个约定。
2. 约定不等于固定，各自集成可以定义自己的结构，只是推荐遵循。

