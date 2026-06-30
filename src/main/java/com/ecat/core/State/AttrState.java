package com.ecat.core.State;

import com.ecat.core.Utils.NumberFormatter;
import com.ecat.core.Bus.event.EventContext;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

/**
 * 属性状态——不可变的强类型状态对象（≡ Home Assistant 的 State）。
 *
 * <p>泛型化 {@code AttrState<T>}：value 强类型 T。持有具体子类引用的消费者（场景①，如设备内部
 * {@code ModbusLinearConversionAttribute ai1; ai1.getState():AttrState<Double>}）零 cast 拿强类型；
 * 总线/通配消费者（场景②，拿 {@code AttrState<?>}）getValue() 退回 Object，靠
 * {@link #getValueType()} 返回的 {@code Class<T>} 安全判断。
 *
 * <p>{@code valueType:Class<T>} = attr.targetType 运行时类型令牌（不经枚举中间层，支持
 * byte[]/BigDecimal 等任意值类型零成本扩展）。
 *
 * <p>不可变纪律：全字段 final、构造期一次性赋值、集合类 value 防御性拷贝。这样总线传递与跨线程消费
 * 都不会出现"发布者发布后又改了同一对象"的撕裂读——发布者持 live AttributeBase，发布的是 AttrState
 * 状态对象（经 {@link com.ecat.core.Bus.event.DeviceDataChangedEvent} 承载 old+new），二者解耦。
 *
 * <p>双时间戳（Instant，跨时区）：lastUpdated 任意更新都刷新；lastChanged 仅当 value 实际变化才刷新。
 * 让设备活性判断、告警去重能区分"真变化"与"重复刷新"。
 *
 * <p><b>State 与 bus 解耦</b>：AttrState 不实现 BusPayload——State 是内容，
 * {@link com.ecat.core.Bus.event.DeviceDataChangedEvent}（信封）才是 BusPayload。
 *
 * @author coffee
 */
public final class AttrState<T> {

    private final String deviceId;
    private final String attrId;
    private final T value;
    private final Class<T> valueType;
    private final AttributeStatus status;
    private final UnitInfo nativeUnit;
    private final UnitInfo displayUnit;
    private final int displayPrecision;
    private final String displayValue;
    private final Instant lastUpdated;
    private final Instant lastChanged;
    private final EventContext context;

    private AttrState(Builder<T> b) {
        if (b.deviceId == null || b.attrId == null || b.status == null || b.context == null) {
            throw new IllegalArgumentException("deviceId/attrId/status/context must not be null");
        }
        this.deviceId = b.deviceId;
        this.attrId = b.attrId;
        this.value = defensiveCopy(b.value);
        this.valueType = b.valueType;
        this.status = b.status;
        this.nativeUnit = b.nativeUnit;
        this.displayUnit = b.displayUnit;
        this.displayPrecision = b.displayPrecision;
        this.displayValue = b.displayValue;
        this.lastUpdated = b.lastUpdated;
        this.lastChanged = b.lastChanged;
        this.context = b.context;
    }

    /** 集合类 value 防御性拷贝，避免外部改动污染状态；标量与不可变类型原样持有。 */
    @SuppressWarnings("unchecked")
    private static <T> T defensiveCopy(T v) {
        if (v instanceof Collection<?>) {
            return (T) Collections.unmodifiableList(new ArrayList<Object>((Collection<?>) v));
        }
        return v;
    }

    public static <T> Builder<T> builder() {
        return new Builder<>();
    }

    public String getDeviceId() { return deviceId; }
    public String getAttrId() { return attrId; }
    public T getValue() { return value; }
    public Class<T> getValueType() { return valueType; }
    public AttributeStatus getStatus() { return status; }
    public UnitInfo getNativeUnit() { return nativeUnit; }
    public UnitInfo getDisplayUnit() { return displayUnit; }
    public int getDisplayPrecision() { return displayPrecision; }
    public String getDisplayValue() { return displayValue; }
    public Instant getLastUpdated() { return lastUpdated; }
    public Instant getLastChanged() { return lastChanged; }
    public EventContext getContext() { return context; }

    /** state 的业务单位（= nativeUnit，业务原始单位）。 */
    public UnitInfo getUnit() { return nativeUnit; }

    /**
     * 按指定单位换算显示值——纯单位换算，零 attr 依赖。
     * <p>value 已是工程值（业务值），单位换算靠 {@link UnitInfo#convertUnit}。
     * <p>非数值型 value、无 nativeUnit、或 toUnit 与 nativeUnit 不同单位类时，
     * 返回冻结的 {@link #displayValue}（toUnit 对非数值型本就无意义）。
     *
     * @param toUnit 目标显示单位，null 则返回默认 displayValue
     */
    public String getDisplayValue(UnitInfo toUnit) {
        if (!(value instanceof Number) || nativeUnit == null) {
            return displayValue;
        }
        if (toUnit == null || toUnit.getClass() != nativeUnit.getClass()) {
            return displayValue;
        }
        double magnitude = ((Number) value).doubleValue() * nativeUnit.convertUnit(toUnit);
        return NumberFormatter.formatValue(magnitude, displayPrecision);
    }

    /** AttrState 构造器——字段多，用 builder 保证可读性与不可变性。 */
    public static final class Builder<T> {
        private String deviceId;
        private String attrId;
        private T value;
        private Class<T> valueType;
        private AttributeStatus status;
        private UnitInfo nativeUnit;
        private UnitInfo displayUnit;
        private int displayPrecision;
        private String displayValue;
        private Instant lastUpdated;
        private Instant lastChanged;
        private EventContext context;

        public Builder<T> deviceId(String v) { this.deviceId = v; return this; }
        public Builder<T> attrId(String v) { this.attrId = v; return this; }
        public Builder<T> value(T v) { this.value = v; return this; }
        public Builder<T> valueType(Class<T> v) { this.valueType = v; return this; }
        public Builder<T> status(AttributeStatus v) { this.status = v; return this; }
        public Builder<T> nativeUnit(UnitInfo v) { this.nativeUnit = v; return this; }
        public Builder<T> displayUnit(UnitInfo v) { this.displayUnit = v; return this; }
        public Builder<T> displayPrecision(int v) { this.displayPrecision = v; return this; }
        public Builder<T> displayValue(String v) { this.displayValue = v; return this; }
        public Builder<T> lastUpdated(Instant v) { this.lastUpdated = v; return this; }
        public Builder<T> lastChanged(Instant v) { this.lastChanged = v; return this; }
        public Builder<T> context(EventContext v) { this.context = v; return this; }

        public AttrState<T> build() {
            return new AttrState<>(this);
        }
    }
}
