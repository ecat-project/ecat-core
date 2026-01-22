package com.ecat.core.State;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import com.ecat.core.State.Unit.UnitInfoFactory;
import com.ecat.core.Utils.NumberFormatter;
import com.ecat.core.I18n.I18nKeyPath;
import com.ecat.core.Utils.DynamicConfig.ConfigDefinition;
import com.ecat.core.Utils.DynamicConfig.ConfigItem;
import com.ecat.core.Utils.DynamicConfig.ConfigItemBuilder;
import com.ecat.core.Utils.DynamicConfig.Validator.NumericRangeValidator;

import lombok.extern.slf4j.Slf4j;

/**
 * 线性转换属性类，支持多段线性转换
 * 继承自 NumericAttribute，提供输入范围到输出范围的线性映射功能
 *
 * 主要用于模拟量信号转换场景，如将4-20mA电流信号转换为对应的工程量（如0-10MPa压力值）
 * 通过支持多段线性转换，可以处理复杂的非线性传感器特性
 *
 * @apiNote displayName i18n supported, default path: state.linear_conversion_attr.{attributeID}
 *
 * @author coffee
 */
@Slf4j
public class LinearConversionAttribute extends NumericAttribute {

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

        public Double getInputMin() { return inputMin; }
        public Double getInputMax() { return inputMax; }
        public Double getOutputMin() { return outputMin; }
        public Double getOutputMax() { return outputMax; }
    }

    private final List<LinearSegment> segments;  // 线性段列表（单段或多段）
    private final UnitInfo inputUnit;             // 输入单位（通过UnitInfoFactory获取）
    private final UnitInfo outputUnit;            // 输出单位（通过UnitInfoFactory获取）
    private ConfigDefinition valueDef;            // 验证定义

    /**
     * 多段转换构造函数
     */
    public LinearConversionAttribute(String attributeID, AttributeClass attrClass,
                                   List<LinearSegment> segments,
                                   String inputUnitEnumName, String outputUnitEnumName,
                                   int displayPrecision, boolean valueChangeable) {
        this(attributeID, attrClass, segments, inputUnitEnumName, outputUnitEnumName,
             displayPrecision, valueChangeable, null);
    }

    /**
     * 多段转换带显示名称构造函数
     */
    public LinearConversionAttribute(String attributeID, String displayName, AttributeClass attrClass,
                                   List<LinearSegment> segments,
                                   String inputUnitEnumName, String outputUnitEnumName,
                                   int displayPrecision, boolean valueChangeable) {
        this(attributeID, displayName, attrClass, segments, inputUnitEnumName, outputUnitEnumName,
             displayPrecision, valueChangeable, null);
    }

    /**
     * 多段转换完整构造函数（支持回调）
     */
    public LinearConversionAttribute(String attributeID, AttributeClass attrClass,
                                   List<LinearSegment> segments,
                                   String inputUnitEnumName, String outputUnitEnumName,
                                   int displayPrecision, boolean valueChangeable,
                                   Function<AttrChangedCallbackParams<Double>, CompletableFuture<Boolean>> onChangedCallback) {
        super(attributeID, attrClass,
              UnitInfoFactory.getEnum(inputUnitEnumName),
              UnitInfoFactory.getEnum(outputUnitEnumName),
              displayPrecision, false, valueChangeable, onChangedCallback);

        this.segments = sortAndValidateSegments(segments);
        this.inputUnit = UnitInfoFactory.getEnum(inputUnitEnumName);
        this.outputUnit = UnitInfoFactory.getEnum(outputUnitEnumName);
    }

    /**
     * 多段转换完整构造函数（支持回调）
     */
    public LinearConversionAttribute(String attributeID, String displayName, AttributeClass attrClass,
                                   List<LinearSegment> segments,
                                   String inputUnitEnumName, String outputUnitEnumName,
                                   int displayPrecision, boolean valueChangeable,
                                   Function<AttrChangedCallbackParams<Double>, CompletableFuture<Boolean>> onChangedCallback) {
        super(attributeID, displayName, attrClass,
              UnitInfoFactory.getEnum(inputUnitEnumName),
              UnitInfoFactory.getEnum(outputUnitEnumName),
              displayPrecision, false, valueChangeable, onChangedCallback);

        this.segments = sortAndValidateSegments(segments);
        this.inputUnit = UnitInfoFactory.getEnum(inputUnitEnumName);
        this.outputUnit = UnitInfoFactory.getEnum(outputUnitEnumName);
    }

    /**
     * 单段转换便捷构造函数（向后兼容）
     */
    public LinearConversionAttribute(String attributeID, AttributeClass attrClass,
                                   Double inputMin, Double inputMax,
                                   Double outputMin, Double outputMax,
                                   String inputUnitEnumName, String outputUnitEnumName,
                                   int displayPrecision, boolean valueChangeable) {
        this(attributeID, attrClass, inputMin, inputMax, outputMin, outputMax,
             inputUnitEnumName, outputUnitEnumName, displayPrecision, valueChangeable, null);
    }

    /**
     * 单段转换带显示名称构造函数
     */
    public LinearConversionAttribute(String attributeID, String displayName, AttributeClass attrClass,
                                   Double inputMin, Double inputMax,
                                   Double outputMin, Double outputMax,
                                   String inputUnitEnumName, String outputUnitEnumName,
                                   int displayPrecision, boolean valueChangeable) {
        this(attributeID, displayName, attrClass, inputMin, inputMax, outputMin, outputMax,
             inputUnitEnumName, outputUnitEnumName, displayPrecision, valueChangeable, null);
    }

    /**
     * 单段转换完整构造函数（支持回调）
     */
    public LinearConversionAttribute(String attributeID, AttributeClass attrClass,
                                   Double inputMin, Double inputMax,
                                   Double outputMin, Double outputMax,
                                   String inputUnitEnumName, String outputUnitEnumName,
                                   int displayPrecision, boolean valueChangeable,
                                   Function<AttrChangedCallbackParams<Double>, CompletableFuture<Boolean>> onChangedCallback) {
        this(attributeID, attrClass,
             Collections.singletonList(new LinearSegment(inputMin, inputMax, outputMin, outputMax)),
             inputUnitEnumName, outputUnitEnumName, displayPrecision, valueChangeable, onChangedCallback);
    }

    /**
     * 单段转换完整构造函数（支持回调）
     */
    public LinearConversionAttribute(String attributeID, String displayName, AttributeClass attrClass,
                                   Double inputMin, Double inputMax,
                                   Double outputMin, Double outputMax,
                                   String inputUnitEnumName, String outputUnitEnumName,
                                   int displayPrecision, boolean valueChangeable,
                                   Function<AttrChangedCallbackParams<Double>, CompletableFuture<Boolean>> onChangedCallback) {
        this(attributeID, displayName, attrClass,
             Collections.singletonList(new LinearSegment(inputMin, inputMax, outputMin, outputMax)),
             inputUnitEnumName, outputUnitEnumName, displayPrecision, valueChangeable, onChangedCallback);
    }

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
            if (segment.getInputMin() > inputValue) {
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
        Collections.sort(sorted, (a, b) -> Double.compare(a.getInputMin(), b.getInputMin()));

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

            if (current.getInputMax() > next.getInputMin()) {
                log.warn("段{}和段{}存在重叠: [{}, {}] 与 [{}, {}]",
                        i, i+1, current.getInputMin(), current.getInputMax(),
                        next.getInputMin(), next.getInputMax());
            }

            // 检查连续性（可选，允许间隙）
            if (Math.abs(current.getInputMax() - next.getInputMin()) > 1e-10) {
                log.info("段{}和段{}之间不连续: {} != {}",
                        i, i+1, current.getInputMax(), next.getInputMin());
            }
        }
    }

    @Override
    public String getDisplayValue(UnitInfo toUnit) {
        if (value == null) return null;

        // 1. 将原始输入值转换为输出工程值
        Double engineeringValue = convertToEngineeringValue(value);

        // 2. 如果指定了显示单位，进行单位转换
        if (toUnit != null && outputUnit != null) {
            if (outputUnit.getClass().equals(toUnit.getClass())) {
                engineeringValue = engineeringValue != null ? engineeringValue * outputUnit.convertUnit(toUnit): null;;
            }
        }

        return engineeringValue != null ? NumberFormatter.formatValue(engineeringValue, displayPrecision) : null;
    }

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

    @Override
    public I18nKeyPath getI18nPrefixPath() {
        return new I18nKeyPath("state.linear_conversion_attr.", "");
    }

    @Override
    public ConfigDefinition getValueDefinition() {
        // LinearConversionAttribute 验证用户设置的工程值是否在有效范围内
        if (valueDef == null) {
            valueDef = new ConfigDefinition();

            // 计算总的输出范围（所有段的并集）
            Double totalOutputMin = segments.stream()
                .mapToDouble(s -> s.getOutputMin()).min().orElse(0.0);
            Double totalOutputMax = segments.stream()
                .mapToDouble(s -> s.getOutputMax()).max().orElse(0.0);

            // 创建输出范围验证器
            ConfigItemBuilder builder = new ConfigItemBuilder()
                .add(new ConfigItem<>("value", Double.class, true, null,
                    new NumericRangeValidator(totalOutputMin, totalOutputMax)));

            valueDef.define(builder);
        }
        return valueDef;
    }

    // Getter 方法
    public List<LinearSegment> getSegments() {
        return new ArrayList<>(segments);
    }

    public UnitInfo getInputUnit() {
        return inputUnit;
    }

    public UnitInfo getOutputUnit() {
        return outputUnit;
    }

    @Override
    public AttributeType getAttributeType() {
        return AttributeType.NUMERIC;
    }
}
