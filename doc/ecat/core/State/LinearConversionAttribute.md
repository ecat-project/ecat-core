# LinearConversionAttribute 设计方案

## 需求概述

基于现有的 `NumericAttribute` 类，创建一个新的 `LinearConversionAttribute` 类，为模拟量信号转换提供了一个标准化、可配置的解决方案。主要用于模拟量信号转换场景，如将4-20mA电流信号转换为对应的工程量（如0-10MPa压力值）。通过支持多段线性转换，可以处理复杂的非线性传感器特性，同时保持与现有单段配置的向后兼容性。通过YAML配置驱动的模式，实现了高度的灵活性和易用性。

## 核心设计思路

`LinearConversionAttribute` 将继承自 `NumericAttribute`，扩展自定义线性转换功能，实现：

1. **线性范围转换**：支持输入范围到输出范围的线性映射
2. **多段线性支持**：支持复杂非线性特性的分段线性化处理
3. **单位系统集成**：利用现有的 `UnitInfoFactory` 进行单位管理
4. **双向转换**：支持原始值到工程值的正向转换和工程值到原始值的反向转换
5. **配置驱动**：通过YAML配置文件灵活定义转换关系

## 类结构设计

### 继承关系

```
AttributeBase<Double> (抽象基类)
    ↓
NumericAttribute (数值属性基类)
    ↓
LinearConversionAttribute (线性转换属性)
```

### 核心字段

```java
private final List<LinearSegment> segments;  // 线性段列表（单段或多段）
private final UnitInfo inputUnit;             // 输入单位（通过UnitInfoFactory获取）
private final UnitInfo outputUnit;            // 输出单位（通过UnitInfoFactory获取）

/**
 * 线性段定义（支持多段转换）
 */
public static class LinearSegment {
    private final Double inputMin;
    private final Double inputMax;
    private final Double outputMin;
    private final Double outputMax;

    public LinearSegment(Double inputMin, Double inputMax,
                       Double outputMin, Double outputMax) {
        this.inputMin = inputMin;
        this.inputMax = inputMax;
        this.outputMin = outputMin;
        this.outputMax = outputMax;
    }

    // 判断输入值是否在此段范围内
    public boolean contains(Double inputValue) {
        return inputValue >= inputMin && inputValue <= inputMax;
    }

    // 执行此段的线性转换
    public Double convert(Double inputValue) {
        double ratio = (inputValue - inputMin) / (inputMax - inputMin);
        return outputMin + (outputMax - outputMin) * ratio;
    }

    // 反向转换：工程值转原始值
    public Double convertBack(Double outputValue) {
        double ratio = (outputValue - outputMin) / (outputMax - outputMin);
        return inputMin + (inputMax - inputMin) * ratio;
    }
}
```

### 构造函数设计

参考 `NumericAttribute`，提供多种构造函数：

0.  此属性不需要单位切换，默认unitChangeable为false，因为用户可以设置显示单位和值范围，用户就能计算合理的output_range匹配自己需要的单位，因此不支持unitChangeable

1. **多段转换构造函数**：
   ```java
   public LinearConversionAttribute(String attributeID, AttributeClass attrClass,
                                    List<LinearSegment> segments,
                                    String inputUnitEnumName, String outputUnitEnumName,
                                    int displayPrecision, boolean valueChangeable)
   ```

2. **多段转换带显示名称构造函数**：
   ```java
   public LinearConversionAttribute(String attributeID, String displayName, AttributeClass attrClass,
                                    List<LinearSegment> segments,
                                    String inputUnitEnumName, String outputUnitEnumName,
                                    int displayPrecision, boolean valueChangeable)
   ```

3. **多段转换完整构造函数（支持回调）**：
   ```java
   public LinearConversionAttribute(String attributeID, AttributeClass attrClass,
                                    List<LinearSegment> segments,
                                    String inputUnitEnumName, String outputUnitEnumName,
                                    int displayPrecision, boolean valueChangeable,
                                    Function<AttrChangedCallbackParams<Double>, CompletableFuture<Boolean>> onChangedCallback)
   ```

4. **单段转换便捷构造函数**（向后兼容）：
   ```java
   public LinearConversionAttribute(String attributeID, AttributeClass attrClass,
                                    Double inputMin, Double inputMax,
                                    Double outputMin, Double outputMax,
                                    String inputUnitEnumName, String outputUnitEnumName,
                                    int displayPrecision, boolean valueChangeable)
   ```

5. **单段转换带显示名称构造函数**：
   ```java
   public LinearConversionAttribute(String attributeID, String displayName, AttributeClass attrClass,
                                    Double inputMin, Double inputMax,
                                    Double outputMin, Double outputMax,
                                    String inputUnitEnumName, String outputUnitEnumName,
                                    int displayPrecision, boolean valueChangeable)
   ```

6. **单段转换完整构造函数（支持回调）**：
   ```java
   public LinearConversionAttribute(String attributeID, AttributeClass attrClass,
                                    Double inputMin, Double inputMax,
                                    Double outputMin, Double outputMax,
                                    String inputUnitEnumName, String outputUnitEnumName,
                                    int displayPrecision, boolean valueChangeable,
                                    Function<AttrChangedCallbackParams<Double>, CompletableFuture<Boolean>> onChangedCallback)
   ```

## 核心方法实现

### 1. 多段线性转换核心逻辑

```java
/**
 * 将原始输入值转换为工程值（支持多段转换）
 * @param inputValue 原始输入值（如电流值）
 * @return 转换后的工程值
 */
private Double convertToEngineeringValue(Double inputValue) {
    if (inputValue == null) return null;

    // 查找对应的线性段
    LinearSegment segment = findSegment(inputValue);
    if (segment == null) {
        log.warn("输入值 {} 不在任何定义的段范围内", inputValue);
        return null;
    }

    return segment.convert(inputValue);
}

/**
 * 将工程值反向转换为原始输入值（支持多段转换）
 * @param engineeringValue 工程值
 * @return 原始输入值
 */
private Double convertFromEngineeringValue(Double engineeringValue) {
    if (engineeringValue == null) return null;

    // 在多段情况下，需要找到包含该工程值的段
    for (LinearSegment segment : segments) {
        if (engineeringValue >= segment.outputMin && engineeringValue <= segment.outputMax) {
            return segment.convertBack(engineeringValue);
        }
    }

    log.warn("工程值 {} 不在任何定义的段输出范围内", engineeringValue);
    return null;
}

/**
 * 查找输入值对应的线性段（segments已按inputMin排序）
 * @param inputValue 输入值
 * @return 对应的线性段，如果不在任何段内则返回null
 */
private LinearSegment findSegment(Double inputValue) {
    // 由于已经排序，可以顺序查找
    for (LinearSegment segment : segments) {
        if (segment.contains(inputValue)) {
            return segment;
        }
        // 提前终止：如果当前段的inputMin已经大于输入值，后面的段更大
        if (segment.inputMin > inputValue) {
            break;
        }
    }
    return null;
}

/**
 * 初始化时对段进行排序和验证
 * @param originalSegments 原始段列表
 * @return 排序后的段列表
 */
private List<LinearSegment> sortAndValidateSegments(List<LinearSegment> originalSegments) {
    if (originalSegments.isEmpty()) {
        throw new IllegalArgumentException("至少需要一个线性段");
    }

    // 按 inputMin 从小到大排序
    List<LinearSegment> sorted = new ArrayList<>(originalSegments);
    Collections.sort(sorted, (a, b) -> Double.compare(a.inputMin, b.inputMin));

    // 验证段的连续性和完整性
    validateSegments(sorted);

    return sorted;
}

/**
 * 验证段的连续性和完整性
 * @param segments 已排序的段列表
 */
private void validateSegments(List<LinearSegment> segments) {
    // 检查范围重叠
    for (int i = 0; i < segments.size() - 1; i++) {
        LinearSegment current = segments.get(i);
        LinearSegment next = segments.get(i + 1);

        if (current.inputMax > next.inputMin) {
            log.warn("段{}和段{}存在重叠: [{}, {}] 与 [{}, {}]",
                    i, i+1, current.inputMin, current.inputMax, next.inputMin, next.inputMax);
        }

        // 检查连续性（可选，允许间隙）
        if (!MathUtils.equals(current.inputMax, next.inputMin)) {
            log.info("段{}和段{}之间不连续: {} != {}",
                    i, i+1, current.inputMax, next.inputMin);
        }
    }
}
```

### 2. 显示值转换方法

```java
@Override
public String getDisplayValue(UnitInfo toUnit) {
    if (value == null) return null;

    // 1. 将原始输入值转换为输出工程值
    Double engineeringValue = convertToEngineeringValue(value);

    // 2. 如果指定了显示单位，进行单位转换
    if (toUnit != null && outputUnit != null) {
        if (outputUnit.getClass().equals(toUnit.getClass())) {
            engineeringValue = engineeringValue * outputUnit.convertUnit(toUnit);
        }
    }

    return NumberFormatter.formatValue(engineeringValue, displayPrecision);
}
```

### 3. 反向转换方法

```java
@Override
protected Double convertFromUnitImp(Double displayValue, UnitInfo fromUnit) {
    // 反向转换：将显示值转换为原始输入值
    if (displayValue == null) return null;

    // 1. 如果有单位转换，先转换到标准输出单位
    Double standardOutputValue = displayValue;
    if (fromUnit != null && outputUnit != null) {
        if (outputUnit.getClass().equals(fromUnit.getClass())) {
            standardOutputValue = displayValue * fromUnit.convertUnit(outputUnit);
        }
    }

    // 2. 反向线性转换
    return convertFromEngineeringValue(standardOutputValue);
}
```

### 4. core中默认国际化路径支持

```java
@Override
public I18nKeyPath getI18nPrefixPath() {
    return new I18nKeyPath("state.linear_conversion_attr.", "");
}
```

#### 4.1 国际化strings.json配置
1. **cEcatCore-属性显示名称路径**：`state.linear_conversion_attr.{attributeIDLowerCase}`
2. **cEcatCore-选项显示名称路径**：`state.linear_conversion_attr.{attributeIDLowerCase}_options.{optionLowerCase}`
3. **绑定设备后-属性显示名称路径**: `devices.xxxdevice.{attributeIDLowerCase}`
3. **绑定设备后-选项显示名称路径**: `devices.xxxdevice.{attributeIDLowerCase}_options.{optionLowerCase}`

### 5. 验证定义

```java
@Override
public ConfigDefinition getValueDefinition() {
    // LinearConversionAttribute 验证用户设置的工程值是否在有效范围内
    if (valueDef == null) {
        valueDef = new ConfigDefinition();

        // 计算总的输出范围（所有段的并集）
        Double totalOutputMin = segments.stream()
            .mapToDouble(s -> s.outputMin).min().orElse(0.0);
        Double totalOutputMax = segments.stream()
            .mapToDouble(s -> s.outputMax).max().orElse(0.0);

        // 创建输出范围验证器
        ConfigItemBuilder builder = new ConfigItemBuilder()
            .add(new ConfigItem<>("value", Double.class, true, null,
                new DynamicRangeValidator(totalOutputMin, totalOutputMax)));

        valueDef.define(builder);
    }
    return valueDef;
}
```

## YAML配置结构

### 设备配置示例

#### 多段转换配置

```yaml
version: 1.0.0
update: 2024-12-10 15:30:00
devices:
  - id: multi-segment-sensor-001
    name: 多段温度传感器
    class: default.sensor
    sn: MT-TEMP-001
    vendor: 自研
    model: MT-TEMP
    io_comm_settings:
      port: COM2
      baudRate: 9600
      numDataBit: 8
      numStopBit: 1
      parity: N
      slaveId: 2
      timeout: 2000
    channels:
      channel1:
        enable: true
        name: 多段温度传感器
        input_unit: CurrentUnit.MILLIAMPERE
        output_unit: TemperatureUnit.CELSIUS
        precision: 1
        segments:           # 多段线性转换定义（按inputMin从小到大排序）
          - input_range:    # 第一段：0-5mA → 0-50°C (低温高精度段)
              min: 0.0
              max: 5.0
            output_range:
              min: 0.0
              max: 50.0
          - input_range:    # 第二段：5-15mA → 50-150°C (中温线性段)
              min: 5.0
              max: 15.0
            output_range:
              min: 50.0
              max: 150.0
          - input_range:    # 第三段：15-20mA → 150-200°C (高温低灵敏度段)
              min: 15.0
              max: 20.0
            output_range:
              min: 150.0
              max: 200.0

      channel2:
        enable: true
        name: 多段流量传感器
        input_unit: CurrentUnit.MILLIAMPERE
        output_unit: DistanceUnit.CENTIMETER
        precision: 2
        segments:
          - input_range:    # 小流量段：0-4mA → 0-10cm³/h
              min: 0.0
              max: 4.0
            output_range:
              min: 0.0
              max: 10.0
          - input_range:    # 正常流量段：4-16mA → 10-100cm³/h
              min: 4.0
              max: 16.0
            output_range:
              min: 10.0
              max: 100.0
          - input_range:    # 大流量段：16-20mA → 100-120cm³/h
              min: 16.0
              max: 20.0
            output_range:
              min: 100.0
              max: 120.0

```
## 使用示例

### 基本使用（单段转换）

```java
// 创建4-20mA到0-10MPa的压力转换属性
LinearConversionAttribute pressureAttr = new LinearConversionAttribute(
    "pressure",
    AttributeClass.MEASUREMENT,
    4.0,      // inputMin: 4mA
    20.0,     // inputMax: 20mA
    0.0,      // outputMin: 0MPa
    10.0,     // outputMax: 10MPa
    "CurrentUnit.MILLIAMPERE",    // 输入单位
    "PressureUnit.MPA",           // 输出单位
    2,        // 显示精度
    false     // 是否可修改
);

// 模拟接收到12mA的电流值
pressureAttr.updateValue(12.0);

// 获取转换后的压力值（工程值）
String displayValue = pressureAttr.getDisplayValue(null); // 返回 "5.00"
```

### 多段转换使用

```java
// 创建多段温度传感器属性（模拟非线性特性）
List<LinearSegment> segments = Arrays.asList(
    new LinearSegment(0.0, 5.0,   0.0,   50.0),   // 0-5mA → 0-50°C
    new LinearSegment(5.0, 15.0,  50.0,  150.0),  // 5-15mA → 50-150°C
    new LinearSegment(15.0, 20.0, 150.0, 200.0)   // 15-20mA → 150-200°C
);

LinearConversionAttribute tempSensor = new LinearConversionAttribute(
    "temperature",
    AttributeClass.MEASUREMENT,
    segments,
    "CurrentUnit.MILLIAMPERE",
    "TemperatureUnit.CELSIUS",
    1,        // 显示精度
    false     // 是否可修改
);

// 测试不同段的转换
tempSensor.updateValue(2.5);   // 低温段 → 25°C
tempSensor.updateValue(10.0);  // 中温段 → 100°C
tempSensor.updateValue(17.5);  // 高温段 → 175°C

// 获取转换后的温度值
String temp1 = tempSensor.getDisplayValue(null); // 返回 "25.0"
String temp2 = tempSensor.getDisplayValue(null); // 返回 "100.0"
String temp3 = tempSensor.getDisplayValue(null); // 返回 "175.0"
```

### 高级使用

```java
// 带完整参数和回调的构造
LinearConversionAttribute levelAttr = new LinearConversionAttribute(
    "water_level",
    "液位高度",  // 自定义显示名称
    AttributeClass.MEASUREMENT,
    4.0,        // 4mA
    20.0,       // 20mA
    0.0,        // 0cm
    200.0,      // 200cm
    "CurrentUnit.MILLIAMPERE",
    "DistanceUnit.CENTIMETER",
    1,          // 精度
    false,      // 不可修改
    (params) -> {
        // 数值变更回调
        System.out.println("液位变更为: " + params.getNewValue() + "cm");
        return CompletableFuture.completedFuture(true);
    }
);
```

### 设备集成使用

```java
public class CurrentChannelDevice extends DeviceBase {
    private void createAttributes() {
        Map<String, Object> config = this.getConfig();
        Map<String, Map<String, Object>> channels =
            (Map<String, Map<String, Object>>) config.get("channels");

        for (Map.Entry<String, Map<String, Object>> entry : channels.entrySet()) {
            String channelId = entry.getKey();
            Map<String, Object> channelConfig = entry.getValue();

            if (Boolean.TRUE.equals(channelConfig.get("enable"))) {
                // 统一使用多段转换接口（单段只是多段的特例）
                createLinearConversionAttribute(channelId, channelConfig);
            }
        }
    }

    /**
     * 创建线性转换属性（统一接口，支持单段和多段）
     */
    private void createLinearConversionAttribute(String channelId, Map<String, Object> channelConfig) {
        List<Map<String, Object>> segmentsConfig =
            (List<Map<String, Object>>) channelConfig.get("segments");

        // 统一解析多段转换参数（单段时segments列表只有一个元素）
        List<LinearSegment> segments = new ArrayList<>();
        for (Map<String, Object> segmentConfig : segmentsConfig) {
            Map<String, Object> inputRange = (Map<String, Object>) segmentConfig.get("input_range");
            Map<String, Object> outputRange = (Map<String, Object>) segmentConfig.get("output_range");

            LinearSegment segment = new LinearSegment(
                ((Number) inputRange.get("min")).doubleValue(),
                ((Number) inputRange.get("max")).doubleValue(),
                ((Number) outputRange.get("min")).doubleValue(),
                ((Number) outputRange.get("max")).doubleValue()
            );
            segments.add(segment);
        }

        // 创建线性转换属性
        LinearConversionAttribute attr = new LinearConversionAttribute(
            channelId,
            AttributeClass.MEASUREMENT,
            segments,
            (String) channelConfig.get("input_unit"),
            (String) channelConfig.get("output_unit"),
            ((Number) channelConfig.getOrDefault("precision", 2)).intValue(),
            false
        );

        if (channelConfig.containsKey("name")) {
            attr.setDisplayName((String) channelConfig.get("name"));
        }

        setAttribute(attr);
    }
}
```

## 与现有类的对比

### 与 NumericAttribute 的区别

| 特性 | NumericAttribute | LinearConversionAttribute |
|------|-----------------|--------------------------|
| 单位转换 | 固定比例转换 | 自定义线性范围转换 |
| 值范围 | 原始数值范围 | 工程值范围 |
| 转换方式 | 基于单位比例 | 基于输入输出范围映射 |
| 使用场景 | 简单数值属性 | 模拟量信号转换 |
| 配置复杂度 | 简单 | 需要配置转换参数 |

### 与其他转换方案的优势

1. **配置灵活**：支持任意线性转换关系，不局限于固定单位比例
2. **类型安全**：基于现有属性系统，保证编译时类型检查
3. **单位系统集成**：正确使用 `UnitInfoFactory`，支持现有单位体系
4. **双向转换**：支持原始值↔工程值的双向转换
5. **国际化支持**：继承现有i18n系统

## 实现注意事项

1. **边界检查**：需要验证输入值是否在有效范围内
2. **除零保护**：防止inputMax - inputMin = 0的情况
3. **单位兼容性**：确保输入输出单位类型匹配时才能进行转换
4. **精度处理**：注意浮点数精度问题，特别是对大范围转换
5. **错误处理**：对无效配置和转换失败进行适当处理
6. **配置验证**：在构造时验证输入输出范围的合理性
7. **线程安全**：确保并发访问时的数据一致性

## 扩展性考虑

1. **非线性转换**：可扩展支持多项式、对数等复杂转换
2. **多段转换**：支持分段线性转换（如温度传感器特性曲线）
3. **动态配置**：支持运行时修改转换参数
4. **校准功能**：支持零点和满度校准
5. **诊断信息**：提供转换状态和诊断信息

## 适用场景

### 单段转换场景
- 4-20mA电流信号到工程量的标准转换
- 0-10V电压信号到工程量的转换
- 基本传感器信号的线性化处理
- 模拟量采集设备的数据转换
- 工业自动化领域的信号标准化

### 多段转换场景
- **温度传感器非线性补偿**：PT100、热电偶等传感器的非线性特性分段线性化
- **流量传感器分段标定**：不同流量段具有不同的灵敏度系数
- **压力开关多段设置**：负压、工作压力、高压报警段的分段处理
- **特殊工艺要求**：某些工艺过程在不同信号段有不同的控制要求
- **传感器故障诊断**：通过分段转换识别传感器工作状态

## 实现注意事项

1. **段排序**：初始化时强制对段按inputMin从小到大排序，提高查找效率
2. **连续性验证**：检查段之间的连续性，避免重叠或过大间隙
3. **边界检查**：对输入值进行边界检查，超出范围时给出警告
4. **精度处理**：注意浮点数精度问题，特别是对大范围转换
5. **错误处理**：对无效配置和转换失败进行适当处理
6. **配置验证**：在构造时验证段配置的合理性
7. **性能优化**：排序后的段列表支持更高效的查找算法

## 文档维护人员列表

coffee
