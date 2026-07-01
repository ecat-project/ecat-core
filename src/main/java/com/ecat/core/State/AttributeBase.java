/*
 * Copyright (c) 2026 ECAT Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ecat.core.State;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import lombok.Getter;
import lombok.Setter;

import com.ecat.core.Utils.LogFactory;
import com.ecat.core.Utils.Log;
import com.ecat.core.Bus.BusTopic;
import com.ecat.core.Bus.event.BusEvent;
import com.ecat.core.Bus.event.DeviceDataChangedEvent;
import com.ecat.core.Bus.event.EventContext;
import com.ecat.core.Device.DeviceBase;
import com.ecat.core.Utils.DynamicConfig.ConfigDefinition;
import com.ecat.core.Utils.DynamicConfig.ConstraintValidator;
import com.ecat.core.Utils.DynamicConfig.ConfigItem;
import com.ecat.core.Utils.DynamicConfig.ConfigItemBuilder;
import com.ecat.core.I18n.I18nKeyPath;
import com.ecat.core.I18n.I18nProxy;
import com.ecat.core.I18n.I18nHelper;

/**
 * attribute base class
 *
 * @implNote this class just provide readonly access to outside, outside can't inherit this class directly.
 * @implNote only the derived class in this package can inherit this class.
 *
 * @Author coffee
 */
public abstract class AttributeBase<T> implements AttributeAbility<T>{

    private final Class<T> targetType;

    protected DeviceBase device; // 所属设备

    // 通用属性
    @Getter
    protected String attributeID; // 参数ID，每台设备唯一
    @Getter
    protected AttributeClass attrClass; // 参数类型
    protected AttributeStatus status;
    protected T value; // 参数值，原始信号值
    @Getter
    protected boolean valueChangeable;
    @Getter
    protected boolean isValueUpdated; // 是否更新过，标志位
    @Getter
    protected Instant updateTime; // 数据更新时间（Instant，跨时区），对于实时数据等同于数据产生的时间
    protected Function<AttrChangedCallbackParams<T>, CompletableFuture<Boolean>> onChangedCallback = null; // 设备数据更新回调函数

    // 数据属性
    protected UnitInfo nativeUnit; //业务原始单位（attr.value 业务值对应的单位），允许为null
    protected UnitInfo displayUnit; //显示信号单位，允许为null，显示使用，不存储数据库
    
    protected String displayName; // 用户设置的高优先级显示名称，对外显示displayName优先级>i18n名称
    private String description; // 属性语义描述，帮助 Agent 和用户理解属性含义
    protected int displayPrecision; // 显示精度，小数位，显示使用不存储数据库
    protected boolean unitChangeable;


    protected I18nKeyPath i18nDispNamePath; // 属性名称的i18n显示资源的json地址，目前规则为全部小写，见getI18nPrefixPath
    protected ConfigDefinition valueDef;    // 输入value的验证规则，null代表不需要验证
    protected I18nProxy i18n = I18nHelper.createProxy(this.getClass()); // 默认使用本类的类名作为i18n的namespace，实际场景如何某集成使用了不是自己集成内的属性，则资源寻找不对，需要在setDevice中重新设置

    protected Log log;

    // 持久化支持
    @Getter
    @Setter
    protected boolean persistable = false; // 是否持久化属性状态，默认 false

    @Getter
    @Setter
    protected T defaultValue = null; // 默认值，无历史记录时使用

    // —— 不可变状态三槽（mid 在途 / last 已提交 / previous 上一态），生命周期与调用链详见 state-lifecycle-design.md ——
    // 在途/不稳定态：updateValue/setStatus 的变更都重建这里，反映尚未提交的最新快照。getState() 在它非空时
    // 返回它（同周期内 updateValue 后立即可读——计算属性求值、测试预热断言依赖此特性）。publicState 提交后置 null。
    private volatile AttrState<T> midState;
    // 已提交态：上次 publicState 提交的稳定快照。作为下次发布事件的 old、持久化数据源；getState() 在 midState 为空时返回它。
    private volatile AttrState<T> lastState;
    // 上一态：lastState 的前驱，永远指向真正的上一个 state（即发布事件的 old）。不再被中间态覆盖，语义干净。
    protected volatile AttrState<T> previousState;
    // 事件溯源上下文：谁触发本次变更（设备轮询/用户操作/逻辑重发布/跨集成）。默认设备轮询；
    // 用户/逻辑入口在调用 updateValue 前用 setEventContext 覆盖。
    private volatile EventContext eventContext;
    // 值真正发生变化的时刻（Instant，跨时区）。与 updateTime 区分：updateTime 是数据产生时间（含重复刷新），
    // lastChanged 仅在 value 实际变化时推进——供设备活性判断、告警去重区分"真变化"与"重复刷新"。
    private volatile Instant lastChanged;

    /**
     * 支持I18n的构造函数，属性i18n名称规则见i18nDispNamePath
     * 适合参数固定名称的设备的国际化支持，比如气象仪、监测仪等
     *
     * @param attributeID 属性ID, 推荐使用小写，以便对应i18n的key
     *
     * @see i18nDispNamePath
     */
    protected AttributeBase(String attributeID, AttributeClass attrClass, UnitInfo nativeUnit,
        UnitInfo displayUnit, int displayPrecision, boolean unitChangeable, boolean valueChangeable){
        this(attributeID, attrClass, nativeUnit, displayUnit, displayPrecision, unitChangeable, valueChangeable, null);
    }

    /**
     * 仅 attributeID 的构造函数，用于先创建属性实例再通过 init 方法完成初始化的场景。
     *
     * <p>适用场景：通过反射创建 standalone 逻辑属性时（如 mapping.getAttr()），
     * 需要 attributeID 来正确初始化 i18n 路径，其余字段由 {@code initFromDefinition} 设置。
     *
     * <p><b>不要滥用</b>：常规属性创建应使用完整参数的构造函数，
     * 仅在"先 new 再 init"模式下使用此构造函数。
     *
     * @param attributeID 属性ID，不能为 null（i18n 路径依赖此值）
     */
    protected AttributeBase(String attributeID) {
        this(attributeID, null, null, null, 0, false, false, null);
    }

    /**
     * 支持I18n的构造函数，属性i18n名称规则见i18nDispNamePath
     * 适合参数固定名称的设备的国际化支持，比如气象仪、监测仪等
     * 
     * @param attributeID 属性ID, 推荐使用小写，以便对应i18n的key
     * @see i18nDispNamePath
     */
    protected AttributeBase(String attributeID, AttributeClass attrClass, UnitInfo nativeUnit,
        UnitInfo displayUnit, int displayPrecision, boolean unitChangeable, boolean valueChangeable,
        Function<AttrChangedCallbackParams<T>, CompletableFuture<Boolean>> onChangedCallback){
        this(attributeID, attrClass, nativeUnit, displayUnit, displayPrecision, unitChangeable, valueChangeable,
            false, null, onChangedCallback);
    }

    /**
     * 完整参数构造函数（包含 persistable + defaultValue）
     * 所有其他构造函数最终委托到此构造函数
     */
    protected AttributeBase(String attributeID, AttributeClass attrClass, UnitInfo nativeUnit,
        UnitInfo displayUnit, int displayPrecision, boolean unitChangeable, boolean valueChangeable,
        boolean persistable, T defaultValue,
        Function<AttrChangedCallbackParams<T>, CompletableFuture<Boolean>> onChangedCallback) {

        this.targetType = extractTargetType();

        this.attributeID = attributeID;
        this.attrClass = attrClass;
        this.status = AttributeStatus.EMPTY;
        this.nativeUnit = nativeUnit;
        this.displayUnit = displayUnit;
        this.displayPrecision = displayPrecision;

        this.unitChangeable = unitChangeable;
        this.valueChangeable = valueChangeable;

        this.onChangedCallback = onChangedCallback;
        this.persistable = persistable;
        this.defaultValue = defaultValue;

        this.log = LogFactory.getLogger(getClass());
        this.i18nDispNamePath = getI18nPrefixPath().withLastSegment(attributeID.toLowerCase(Locale.ENGLISH));
        this.valueDef = null;
    }

    /**
     * 同时支持用户设置显示名称displayName和I18n的构造函数
     * 适合参数名称不固定的设备的国际化支持，比如数据采集转换模块的不同通道需要用户设置名称，以用户设置名称的语言文字为高优先级
     */
    protected AttributeBase(String attributeID, String displayName, AttributeClass attrClass, UnitInfo nativeUnit,
        UnitInfo displayUnit, int displayPrecision, boolean unitChangeable, boolean valueChangeable){
        this(attributeID, displayName, attrClass, nativeUnit, displayUnit, displayPrecision, unitChangeable, valueChangeable, null);
    }

    /**
     * 同时支持用户设置显示名称displayName和I18n的构造函数
     * 适合参数名称不固定的设备的国际化支持，比如数据采集转换模块的不同通道需要用户设置名称，以用户语言文字设置的显示名称为高优先级
     */
    protected AttributeBase(String attributeID, String displayName, AttributeClass attrClass, UnitInfo nativeUnit,
        UnitInfo displayUnit, int displayPrecision, boolean unitChangeable, boolean valueChangeable,
        Function<AttrChangedCallbackParams<T>, CompletableFuture<Boolean>> onChangedCallback){
        // 调用新构造函数，保持displayName字段向后兼容
        this(attributeID, attrClass, nativeUnit, displayUnit, displayPrecision, unitChangeable, valueChangeable, onChangedCallback);
        this.displayName = displayName; // 保持向后兼容
    }

    @Override
    public boolean canUnitChange(){
        return unitChangeable;
    }

    @Override
    public boolean canValueChange(){
        return valueChangeable;
    }

    /**
     * 更新属性状态（不改值）。变更写入在途 midState，待 publicState 提交。
     *
     * <p>⚠ <b>不推荐单独使用</b>：值与状态都需变更时应优先 {@link #updateValue(Object, AttributeStatus)}
     * 一次设值+状态。单独 setStatus 会单独重建一次 midState（含 getDisplayValue 单位换算+格式化），
     * 叠加此前的 updateValue 即双重建、且中途产生「值新/状态旧」瞬态撕裂 midState。
     * 仅在「只改状态不改值」时使用——典型场景：异常路径设备故障 setStatus(MALFUNCTION)，本周期无有效值可解析。
     *
     * <p>状态变更即产新 state：device 已附着时无条件重建 midState（不依赖 lastState 是否已存在），
     * 使异常路径的纯状态变更（无 updateValue）也能经 getState 可见、经 publicState 上总线。
     *
     * @param newStatus 新状态
     */
    @Override
    public boolean setStatus(AttributeStatus newStatus){
        synchronized (this) {
            if (this.status == newStatus) {
                return true; // 状态未变，避免无谓刷新与重复发布
            }
            this.status = newStatus;
            // 状态变更即产新 state：device 已附着时无条件重建在途 midState（不再依赖 lastState 已存在），
            // 使异常路径纯状态变更（无 updateValue）也能经 getState 可见、经 publicState 上总线。
            if (this.device != null && this.device.getId() != null) {
                if (this.eventContext == null) {
                    this.eventContext = EventContext.root(EventContext.Source.DEVICE_POLL, null);
                }
                // 先刷新 updateTime=now，再 buildState——midState 才能捕获新鲜时间戳。
                // 顺序颠倒会导致只走 setStatus、从未 updateValue 的纯状态属性（设备 online/offline、
                // 报警翻转等）midState.lastUpdated 为 null，发布的 device.data.update 事件无时间戳，
                // 下游时序落库（如 realdata 时间分区表）失败。与 updateValue 范式保持一致。
                setValueUpdated(true);
                this.midState = buildState();
            }
        }
        return true;
    }

    protected AttributeStatus getStatus(){
        return status;
    }

    @Override
    public boolean changeDisplayUnit(UnitInfo newDisplayUnit){
        // 根据 nativeUnit 和 newUnit 的 unitClasses 判断是否要同class转换还是跨class转换
        // 判断是否允许设置的单位
        if(!unitChangeable || !attrClass.isValidUnit(newDisplayUnit)){
            return false;
        }
        displayUnit = newDisplayUnit;
        return true;
    }

    @Override
    public boolean changeDisplayPrecision(int newPrecision){
        displayPrecision = newPrecision;
        return true;
    }

    @Override
    public  int getDisplayPrecision(){
        return displayPrecision;
    }

    @Override
    public String getDisplayUnitStr(){
        if(displayUnit == null){
            return "";
        }
        return displayUnit.getDisplayName();
    }

    @Override
    public UnitInfo getDisplayUnit(){
        return displayUnit;
    }

    @Override
    public UnitInfo getNativeUnit(){
        return nativeUnit;
    }

    /**
     * 获取原始单位的完整字符串表示
     * @return 完整的单位字符串，如"AirVolumeUnit.PPM"，如果nativeUnit为null则返回空字符串
     */
    public String getNativeUnitFullStr() {
        if (nativeUnit == null) {
            return "";
        }
        return nativeUnit.getFullUnitString();
    }

    /**
     * 获取属性原始值——protected：内部/子类用；外部跨线程消费方用 getState() 拿不可变 AttrState。
     */
	protected T getValue() {
        return value;
	}

    /**
     * 获取属性的显示值或国际化的key，适合数据存储场景使用，不适合用户侧显示
     * @return 如果是文本、数值等属性，返回值就是显示值
     *         如果是Binary、Command、Select等属性，返回值是选项的i18n的key，前端要显示字符串则根据集成、设备和参数id的路径拼接i18nkey获取对应的显示字符串value
     *            例如BinaryAttribute返回"on"或"off"，CommandAttribute返回命令id，SelectAttribute返回选项id
     * @see DeviceBase#getI18nPrefix()
     * @see StringCommandAttribute#getI18nPrefixPath()
     * @see StringCommandAttribute#getDisplayValue(UnitInfo)
     * @see StringSelectAttribute#getDisplayValue(UnitInfo)
     * @see BinaryAttribute#getDisplayValue(UnitInfo)
     */
	public String getI18nValue(UnitInfo toUnit) {
        // 这里的实现仅可满足 Text 和 number 属性的默认实现，因为他们的数值就是显示值，不需要再去映射选项的i18n key
        // 但是 Binary、Command、Select 等属性的 getDisplayValue 有option的i18n转换，需要子类覆盖此方法返回选项的 i18n key
        return getDisplayValue(toUnit);
	}

    /**
     * 以显示值设置value，用于用户侧或着外部调用api设置
     * @param newDisplayValue 新的显示值，对于Binary、Command、Select等类型，传入的值必须是选项值的key
     * @return 是否设置成功
     * @see CommandAttribute#getOptionDict()
     * @see SelectAttribute#getOptionDict()
     * @see BinaryAttribute#getOptionDict()
     */
    @Override
    public CompletableFuture<Boolean> setDisplayValue(String newDisplayValue) {
        return setDisplayValue(newDisplayValue, displayUnit);
    }

    /**
     * 以对应单位的显示值设置value，用于用户侧或着外部调用api设置
     * @param newDisplayValue 新的显示值，对于Command、Select等类型，传入的值必须是选项值的key
     * @param fromUnit newDisplayValue对应的单位，按需进行单位转换
     * @return 是否设置成功
     * @see CommandAttribute#getOptionDict()
     * @see SelectAttribute#getOptionDict()
     * @see BinaryAttribute#getOptionDict()
     */
    @Override
    public CompletableFuture<Boolean> setDisplayValue(String newDisplayValue, UnitInfo fromUnit) {
        if(!valueChangeable){
            return CompletableFuture.completedFuture(false);
        }

        // 创建验证配置
        Map<String, Object> config = new HashMap<>();
        config.put("value", newDisplayValue);

        // 如果有验证定义，进行验证
        ConfigDefinition def = getLazyValueDefinition();
        if (def != null) {
            boolean isValid = def.validateConfig(config);
            if (!isValid) {
                Map<ConfigItem<?>, String> invalidItems = def.getInvalidConfigItems();
                for (Map.Entry<ConfigItem<?>, String> entry : invalidItems.entrySet()) {
                    log.error("Validation failed for key: " + entry.getKey().getKey() + ", error: " + entry.getValue());
                }
                CompletableFuture<Boolean> failedFuture = new CompletableFuture<>();
                failedFuture.completeExceptionally(
                    new IllegalArgumentException("setDisplayValue输入类型不符合要求.")
                );
                return failedFuture;
            }
        }

        // 验证通过，执行原有逻辑
        try {
            T convertedValue = convertStringToType(newDisplayValue);
            return setDisplayValueImp(convertedValue, fromUnit);
        } catch (Exception e) {
            CompletableFuture<Boolean> failedFuture = new CompletableFuture<>();
            failedFuture.completeExceptionally(
                new IllegalArgumentException("setDisplayValue类型转换失败: " + e.getMessage(), e)
            );
            return failedFuture;
        }
    }

    /**
     * 获取属性的显示值，适合用户侧显示，支持i18n
     * @param toUnit 目标显示单位
     * @return 如果是文本、数值等属性，返回值就是显示值
     *        如果是Binary、Command、Select等属性，返回值是选项的i18n的value，前端直接显示使用即可
     *           例如BinaryAttribute返回"开"或"关"，CommandAttribute返回命令文字，SelectAttribute返回选项文字
     */
    @Override
    public abstract String getDisplayValue(UnitInfo toUnit);

    @Override
    public String getDisplayValue() {
        return getDisplayValue(displayUnit);
    }

    public void setValueUpdated(boolean isValueUpdated){
        if(isValueUpdated){
            this.updateTime = Instant.now();
        }
        this.isValueUpdated = isValueUpdated;
    }

    public void setValueUpdated(){
        setValueUpdated(true);
    }

    public DeviceBase getDevice(){
        return device;
    }

    /**
     * 设置属性所属设备
     * 由DeviceBase.setAttribute调用，用户侧不需要调用
     * 绑定属性与设备的关系以及使用device所在集成的i18n资源
     * 设备集成的i18n资源作为属性的i18n代理
     * 这样属性的i18n资源可以和设备集成保持一致，便于集成内统一管理i18n资源
     * @param device 所属设备
     */
    public void setDevice(DeviceBase device){
        this.device = device;
        // 重新设置i18n代理，使用设备的集成类作为i18n的namespace
        if(device != null){
            this.i18n = I18nHelper.createProxy(device.getClass()); // 使用device集成类作为namespace
            if(this.i18n == null){
                // 一般是使用mockDevice会出现这种情况，使用mockDevice通常是单测属性，回退使用本属性类的namespace
                // 匹配逻辑是属于core或某集成内自定义属性所在的集成
                this.i18n = I18nHelper.createProxy(this.getClass());
            }
        }
    }

    /**
     * dulplicate of {@link #getAttributeID()} for backward compatibility
     * @deprecated see {@link #getAttributeID()}
     */
    @Deprecated
    public String getAttrID(){
        return attributeID;
    }

    /**
     * 获取属性值的类型
     * @return 属性值类型枚举
     * @implNote 子类无需重写此方法，类型由泛型参数自动
     * @see #convertStringToType(String)
     * @see AttrValueType
     */
    protected AttrValueType getValueType(){
        return AttrValueType.fromJDKClass(targetType);
    }

    /**
     * 获取属性值的类型名称
     * @apiNote 为序列化等场景提供字符串类型名称
     * @return 属性值类型名称
     */
    protected String getValueTypeName(){
        return getValueType().getValueTypeName();
    }

    /**
     * 提交并发布状态变更（commit 点）。按需发布：仅当自上次提交后有 updateValue/setStatus 变更（isValueUpdated）时。
     *
     * <p>取在途 midState 作 new、上次已提交 lastState 作 old，发布 BusEvent&lt;DeviceDataChangedEvent&gt;（old+new 两个不可变 AttrState，
     * 订阅者拿到绝对自洽的变化事件）；随后移位：previousState←lastState、lastState←midState、midState 置空
     *（previousState 永远指向 lastState 的前驱=发布事件的 old，链连续无幽灵中间态）。
     * 持久化（persistable）在此对已提交的 coherent 态落盘，保证 value+status 自洽——比在 updateValue 内落盘更准
     *（避免值新/状态旧的瞬态撕裂态被持久化）。
     */
    public boolean publicState() {
        if(this.isValueUpdated){
            try {
                if (device == null) {
                    // 属性未注册到设备时（如单元测试中），无法发布总线事件
                    log.warn("Attribute '{}' is not registered to any device, skip publicState", this.getAttributeID());
                    return true;
                }
                AttrState<T> newState = this.midState;
                if (newState == null) {
                    // 无在途变更（device 未附着或从未 updateValue/setStatus），无可发布内容
                    return true;
                }
                // context 取自 eventContext（null 兜底设备轮询——setValueUpdated(true) 公开入口可能绕过 updateValue）
                EventContext ctx = (this.eventContext != null)
                        ? this.eventContext
                        : EventContext.root(EventContext.Source.DEVICE_POLL, null);
                // 持久化（commit 点，独立 try）：此时 getState() 返回在途 midState（本周期 value+status 已 settle 的 coherent 态）。
                // 放在发布前、移位前——无总线或发布失败时仍保证持久化。
                if (persistable && device.getCore() != null) {
                    try {
                        device.getCore().getStateManager().saveState(device, this);
                    } catch (Exception e) {
                        log.error("Failed to persist state for attribute " + attributeID, e);
                    }
                }
                // 发布总线事件：old=lastState（上次提交），new=midState（本次在途）。
                // 失败抛到外层 catch → publicState 返回 false（保留发布失败可感知契约；未移位，下次 publicState 可重试）。
                DeviceDataChangedEvent change = new DeviceDataChangedEvent(
                        device.getId(), attributeID, this.lastState, newState);
                BusEvent<DeviceDataChangedEvent> event = BusEvent.of(
                        BusTopic.DEVICE_DATA_UPDATE.getTopicName(), change, ctx);
                device.getCore().getBusRegistry().publish(event);
                // 发布成功后移位提交：previous←last, last←mid(已提交), mid=null
                this.previousState = this.lastState;
                this.lastState = newState;
                this.midState = null;
            } catch (Exception e) {
                log.error("Failed to publish attribute state " + this.getAttributeID() + " for device " + device.getId(), e);
                return false;
            }
            this.setValueUpdated(false);
        }
        return true;
    }

    /**
     * 获取属性当前可见的状态快照（不可变 AttrState，单次 volatile 读，字段自洽）。
     *
     * <p>返回规则：自上次 publicState 提交后若有 updateValue/setStatus 变更（midState 非空），返回<b>在途的 midState</b>
     *（反映尚未提交的最新变更）；否则返回<b>上次已提交的 lastState</b>。即「读取此刻最新可见态」——同周期内
     * updateValue 后立即调用可读到刚写入的值（计算属性求值、测试预热断言等依赖此特性）。
     *
     * <p>总线订阅者、API 导出、持久化统一用此替代分别读 getValue/getStatus/getUpdateTime——避免多字段分别读取期间的撕裂读。
     * 注意：在途 midState 在属性自身 updateValue 与 setStatus 之间存在极短的「值已新/状态未新」瞬态窗口；
     * 若需值与状态绝对自洽的已提交态，调用前先 publicState。首次 updateValue 前（或未绑定设备）为 null。
     *
     * @return 当前可见状态；属性从未 updateValue 且 device 未附着时为 null
     */
    public AttrState<T> getState() {
        return midState != null ? midState : lastState;
    }

    /**
     * 获取当前事件溯源上下文（谁触发本次变更）。
     *
     * @return 溯源上下文，尚未 updateValue 过时为 null
     */
    public EventContext getEventContext() {
        return eventContext;
    }

    /**
     * 设置事件溯源上下文。用户侧（setDisplayValue 链）、逻辑重发布等入口在调用 updateValue 前
     * 调用此方法标记来源；不设置则 updateValue 默认按设备轮询溯源。
     *
     * @param ctx 溯源上下文，不能为 null
     */
    public void setEventContext(EventContext ctx) {
        if (ctx == null) {
            throw new IllegalArgumentException("eventContext 不能为 null");
        }
        this.eventContext = ctx;
    }

    /**
     * 值实际发生变化的时刻（Instant，跨时区）。与 updateTime（数据时间，含重复刷新）区分，用于判断"真变化"。
     *
     * @return 最近一次 value 变化的时刻，从未变化过为 null
     */
    public Instant getLastChanged() {
        return lastChanged;
    }

    /**
     * 设备自己更新原始数据，不触发onChangedCallback订阅，适于收到设备新数据更新状态值
     * 子类根据需求设置访问范围
     * 不要给用户侧操作使用此函数
     * @param newValue
     * @return
     */
    /**
     * 更新属性业务值（工程值），不改变 status。变更写入在途 midState，待 publicState 提交。
     *
     * <p>⚠ <b>不推荐单独使用</b>：若同周期还需 setStatus，应改用 {@link #updateValue(Object, AttributeStatus)}
     * 一次设值+状态。本方法触发一次 midState 重建（含 getDisplayValue 单位换算+格式化），紧接 setStatus 会再重建一次
     * ——双倍重建开销，且中途产生「值新/状态旧」瞬态撕裂 midState。仅在「只改值不改状态」时使用。
     *
     * @param newValue 新业务值
     * @return true
     */
    protected boolean updateValue(T newValue){
        // 同步块保证"写新值 -> 推进 lastChanged -> 构建在途 midState"原子进行，getState() 单次 volatile 读拿到的状态字段自洽（修撕裂读）。
        synchronized (this) {
            T oldValue = this.value;
            boolean changed = !Objects.equals(oldValue, newValue);
            this.value = newValue;
            this.setValueUpdated();
            if (changed) {
                this.lastChanged = Instant.now();
            }
            // context 默认按设备轮询入口溯源；用户/逻辑入口会先 setEventContext 覆盖。
            if (this.eventContext == null) {
                this.eventContext = EventContext.root(EventContext.Source.DEVICE_POLL, null);
            }
            // 写入在途 midState（不触碰 lastState/previousState——它们由 publicState 移位管理）。
            // 仅在已绑定设备且设备 id 可解析时构建（deviceId 是 AttrState 必填字段）；未绑定或 id 为 null（单测未 stub getId 的 mock 设备）时跳过。
            if (this.device != null && this.device.getId() != null) {
                this.midState = buildState();
            }
        }
        // 持久化已迁到 publicState（commit 点），此处不再落盘——避免值新/状态旧的瞬态 midState 被持久化。
        return true;
    }

    /**
     * 原子更新属性业务值与状态——<b>推荐入口</b>。值与状态一次写入、midState 仅重建一次，无瞬态撕裂、性能最优
     *（相对分别调 updateValue + setStatus 省一次 midState 重建与一次同步块）。适于收到设备新数据时同步更新值与状态。
     * 子类按需设置访问范围；不要给用户侧操作使用（用户侧走 setValue，会触发 onChangedCallback）。
     *
     * @param newValue  新业务值
     * @param newStatus 新状态；null 表示不改变当前 status（保留既有 status 字段，buildState 要求 status 非空）
     * @return true
     */
    @Override
    public boolean updateValue(T newValue, AttributeStatus newStatus){
        // null 表示"不改变当前状态"（保留既有 status 字段），而非置空——buildState 要求 status 非空。
        if (newStatus != null) {
            this.status = newStatus;
        }
        return updateValue(newValue);
    }

    /**
     * 从当前可变字段构建不可变 AttrState 状态。
     * <p>须在 synchronized(this) 内调用（由 updateValue 保证），确保各字段读取自洽。
     * 集合类 value 由 AttrState 自行防御性拷贝；标量与不可变类型零拷贝。
     */
    private AttrState<T> buildState() {
        return AttrState.<T>builder()
            .deviceId(device.getId())
            .attrId(attributeID)
            .valueType(targetType)
            .value(value)
            .status(status)
            .nativeUnit(nativeUnit)
            .displayUnit(displayUnit)
            .displayPrecision(displayPrecision)
            .displayValue(getDisplayValue())
            .lastUpdated(updateTime)
            .lastChanged(lastChanged)
            .context(eventContext)
            .build();
    }


    /**
     * 子类按需实现具体的用户侧业务的属性设置
     * 使用指定单位的数据设置原始值
     * @param newDisplayValue 指定单位的value
     * @param customUnit 指定单位
     * @return
     */
    protected CompletableFuture<Boolean> setDisplayValueImp(T newDisplayValue, UnitInfo customUnit){
        return setValue(newDisplayValue, customUnit);
    }

    /**
     * 子类可按需override此方法实现具体的用户侧业务的属性设置
     * 使用原始单位设置原始数据，会触发参数修改订阅.
     * @param newValue
     * @return
     *        true: 设置成功
     *       false: 设置失败
     */
    protected CompletableFuture<Boolean> setValue(T newValue){
        if(!valueChangeable){
            return CompletableFuture.completedFuture(false);
        }
        if(updateValue(newValue)){
            publicState();  // 值变更成功后自动发布 Bus 事件，确保下游绑定属性感知变化
            // 触发事件订阅
            if(onChangedCallback != null){
                return onChangedCallback.apply(new AttrChangedCallbackParams<T>(this, newValue));
            }
            return CompletableFuture.completedFuture(true);
        }
        else{
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * 子类无需重写此方法，此方法调用setValue(T newValue)方法执行
     * 使用指定单位设置原始数据，会触发参数修改订阅.
     * @param newValue
     * @param fromUnit newValue的单位
     * @return
     *        true: 设置成功
     *       false: 设置失败
     */
    protected CompletableFuture<Boolean> setValue(T newValue, UnitInfo fromUnit){
        if(!valueChangeable){
            return CompletableFuture.completedFuture(false);
        }

        if(fromUnit != null && nativeUnit != null){
            // Both units are not null, perform conversion
            T nativeValue = convertFromUnitImp(newValue, fromUnit);
            return setValue(nativeValue);
        }
        else if(fromUnit == null && nativeUnit == null){
            // Both units are null, handle as needed or just return
            return setValue(newValue);
        }
        else{
            // only one of them is null
            throw new IllegalArgumentException("fromUnit 和 nativeUnit不能某一项有值，另一项为空");
        }
    }

    /**
     * 子类按需实现具体的单位转换逻辑
     * 将指定单位的值转换为原始单位的值
     * @param value
     * @param fromUnit value的单位
     * @return
     */
    protected abstract T convertFromUnitImp(T value, UnitInfo fromUnit);

    /**
     * 将指定单位的值转换为目标单位的对应新值
     * 用于数据库存储值与用户展示值之间的单位转换
     *
     * @param value 要转换的数值
     * @param fromUnit value 当前所在的单位（输入单位）
     * @param toUnit 目标单位（输出单位）
     * @return 转换后的值
     * @throws IllegalArgumentException 如果单位转换不支持
     * @throws NullPointerException 如果 fromUnit 或 toUnit 为 null
     */
    public abstract Double convertValueToUnit(Double value, UnitInfo fromUnit, UnitInfo toUnit);

    /**
     * 字符串到目标类型的转换逻辑（支持常见类型）
     * @param source 输入字符串
     * @return 转换后的目标类型值
     * @throws Exception 转换失败时抛出异常
     */
    /**
     * 将字符串转换为属性的目标类型 T。
     *
     * <p>整数类型（Short/Integer/Long/Byte）使用 {@code Double.parseDouble} + 强转，
     * 而非直接 {@code parseShort}/parseInt 等，原因：
     * {@link #getDisplayValue} 对整数属性输出浮点格式（如 {@code "1.0"}），
     * 直接 {@code Short.parseShort("1.0")} 会抛 NumberFormatException。
     * 使用 {@code (short) Double.parseDouble("1.0")} 既兼容浮点格式也兼容整数格式。
     *
     * @param source 输入字符串
     * @return 转换后的类型 T 值
     * @throws Exception 转换失败时抛出异常
     */
    private T convertStringToType(String source) throws Exception {
        if (targetType == Integer.class) {
            return targetType.cast((int) Double.parseDouble(source));
        }
        if (targetType == Double.class) {
            return targetType.cast(Double.parseDouble(source));
        }
        if (targetType == Boolean.class) {
            return targetType.cast(convertStringToBoolean(source));
        }
        if (targetType == String.class) {
            return targetType.cast(source);
        }
        if (targetType == Float.class) {
            return targetType.cast(Float.parseFloat(source));
        }
        if (targetType == Short.class) {
            return targetType.cast((short) Double.parseDouble(source));
        }
        if (targetType == Long.class) {
            return targetType.cast((long) Double.parseDouble(source));
        }
        if (targetType == Byte.class) {
            return targetType.cast((byte) Double.parseDouble(source));
        }
        // 可继续扩展其他常见类型...
        throw new UnsupportedOperationException(
            "不支持的目标类型: " + targetType.getSimpleName() +
            "，请子类覆盖 setDisplayValue(String) 方法实现自定义转换"
        );
    }

    /**
     * 将字符串转换为布尔值
     * 支持 "true", "false", "1", "0", "on", "off", "yes", "no"（不区分大小写）
     * 如果子类有特殊需求，可以覆盖此方法
     * @param source 输入字符串
     * @return 转换后的布尔值
     * @throws Exception 转换失败时抛出异常
     */
    protected Boolean convertStringToBoolean(String source) throws Exception {
        String trimmed = source.trim().toLowerCase(Locale.ENGLISH);
        if (trimmed.equals("true") || trimmed.equals("1") || trimmed.equals("on") || trimmed.equals("yes")) {
            return true;
        }
        if (trimmed.equals("false") || trimmed.equals("0") || trimmed.equals("off") || trimmed.equals("no")) {
            return false;
        }
        throw new IllegalArgumentException("无法将字符串转换为布尔值: " + source);
    }

    @SuppressWarnings("unchecked")
    private Class<T> extractTargetType() {
        // java8 兼容
        Class<?> currentClass = getClass();
        Type genericSuperclass = currentClass.getGenericSuperclass();

        while (genericSuperclass instanceof Class<?>) {
            currentClass = (Class<?>) genericSuperclass;
            genericSuperclass = currentClass.getGenericSuperclass();
        }
        if (!(genericSuperclass instanceof ParameterizedType)) {
            throw new IllegalStateException(
                "无法自动推导泛型类型参数，请确保子类明确声明具体类型（如 class MyAttr extends AttributeBase<MyType>）"
            );
        }

        Type[] typeArguments = ((ParameterizedType) genericSuperclass).getActualTypeArguments();
        if (typeArguments.length == 0) {
            throw new IllegalStateException("泛型父类未指定具体类型参数");
        }

        Type typeArg = typeArguments[0];
        if (typeArg instanceof Class<?>) {
            return (Class<T>) typeArg;
        } else {
            throw new IllegalStateException(
                "不支持的泛型类型参数: " + typeArg.getTypeName() +
                "，仅支持具体类作为泛型参数"
            );
        }
    }

    /**
     * 从 PersistedState 恢复属性状态（value, status, updateTime）到已提交 lastState。
     * 基类统一处理类型转换，不需要子类重写。恢复后 midState/previousState 置空——首次 updateValue 重建 midState、
     * 首次 publicState 移位生成新 previousState。
     */
    public void restore(PersistedState state) {
        synchronized (this) {
            this.value = convertObjectToTargetType(state.value);
            this.status = AttributeStatus.fromId(state.statusCode);
            if (state.updateTimeEpochMs > 0) {
                this.updateTime = Instant.ofEpochMilli(state.updateTimeEpochMs);
            }
            // 恢复到已提交 lastState（让 getState() 在无在途变更时返回恢复态）；midState/previousState 置空。
            // 必填（deviceId/attrId/status）未就绪时跳过，待首次 updateValue 构建——避免 LogicDevice createAttrs 早期 restore 时字段未全引发 build 校验失败。
            if (device != null && device.getId() != null && attributeID != null && this.status != null) {
                this.lastState = buildState();
                this.midState = null;
                this.previousState = null;
            }
        }
    }

    /**
     * 使用 defaultValue 恢复属性
     */
    public void restoreFromDefault() {
        this.value = defaultValue;
        this.status = AttributeStatus.NORMAL;
        this.updateTime = Instant.now();
    }

    /**
     * 将 Object 转换为目标类型 T
     * 用于从 PersistedState 恢复时 fastjson2 反序列化后的类型窄化
     * fastjson2 可能将数字反序列化为 BigDecimal/Integer/Long 等
     */
    @SuppressWarnings("unchecked")
    private T convertObjectToTargetType(Object rawValue) {
        if (rawValue == null) return null;

        if (targetType == Double.class && rawValue instanceof Number) {
            return (T) (Double) ((Number) rawValue).doubleValue();
        }
        if (targetType == Float.class && rawValue instanceof Number) {
            return (T) (Float) ((Number) rawValue).floatValue();
        }
        if (targetType == Integer.class && rawValue instanceof Number) {
            return (T) (Integer) ((Number) rawValue).intValue();
        }
        if (targetType == Short.class && rawValue instanceof Number) {
            return (T) (Short) ((Number) rawValue).shortValue();
        }
        if (targetType == Long.class && rawValue instanceof Number) {
            return (T) (Long) ((Number) rawValue).longValue();
        }
        if (targetType == Byte.class && rawValue instanceof Number) {
            return (T) (Byte) ((Number) rawValue).byteValue();
        }
        if (targetType == Boolean.class && rawValue instanceof Boolean) {
            return (T) rawValue;
        }
        if (targetType == String.class && rawValue instanceof String) {
            return (T) rawValue;
        }
        if (targetType == Instant.class && rawValue instanceof Number) {
            return (T) Instant.ofEpochMilli(((Number) rawValue).longValue());
        }

        throw new IllegalArgumentException(
            "Cannot convert persisted value type " + rawValue.getClass().getName() +
            " to attribute type " + targetType.getName() +
            " for attribute " + attributeID
        );
    }

    /**
     * 延迟初始化验证定义
     * @return ConfigDefinition 验证定义
     */
    private ConfigDefinition getLazyValueDefinition() {
        if (valueDef == null) {
            valueDef = getValueDefinition();
        }
        return valueDef;
    }

    /**
     * 获取setDisplayValue验证用户输入的展示属性值的验证定义
     * 子类可以重写此方法定义自己的验证规则
     * @return ConfigDefinition 验证定义，null表示不需要验证
     */
    public abstract ConfigDefinition getValueDefinition();

    /**
     * 获取本类i18n统一的默认前缀path，应为小写
     * 子类必须重写此方法定义自己的path前缀
     * 完整路径将自动生成为: prefix + attributeID.toLowerCase()
     *
     * 示例实现：
     * - BinaryAttribute: return new I18nKeyPath("state.binary_attr.", "");
     * - SelectAttribute: return new I18nKeyPath("state.select_attr.", "");
     * - StringSelectAttribute: return new I18nKeyPath("state.string_select_attr.", "");
     *
     * @return I18nKeyPath I18n路径前缀，不能为null
     * @apiNote 在setDevice前生效，setDevice后见getI18nDispNamePath()
     * @see #getI18nDispNamePath()
     */
    protected abstract I18nKeyPath getI18nPrefixPath();

    /**
     * 获取属性的国际化显示名称路径
     * 支持设备分组的新路径结构和向后兼容
     *
     * @return I18nKeyPath 属性的国际化显示名称路径
     */
    protected I18nKeyPath getI18nDispNamePath() {
        if(device != null && device.getI18nPrefix() != null){
            // 约定，设置attr的i18n path在devices下，e.g.: "devices.qc_device.{attrid}"
            return device.getI18nPrefix().withLastSegment(attributeID.toLowerCase(Locale.ENGLISH));
        }else{
            // 未绑定设备，返回默认约定的路径
            return i18nDispNamePath;
        }
    }

    /**
     * 获取国际化显示名称，辅助功能
     * 支持设备分组的新路径结构和向后兼容
     * @return 国际化显示名称
     */
    protected String getI18nDisplayName() {
        String result;
        boolean deviceI18nPrefixExists = (device != null && device.getI18nPrefix() != null);
        String devicePath = "";
        // 优先尝试新的设备分组路径
        if(deviceI18nPrefixExists){
            devicePath = device.getI18nPrefix().getI18nPath() + attributeID.toLowerCase(Locale.ENGLISH);
            result = i18n.t(devicePath);
            // 如果找到了翻译（不是返回原路径），则使用设备分组路径的翻译
            if(!result.equals(devicePath)){
                return result;
            }
        }

        // 回退到原有的路径结构
        if(i18nDispNamePath != null){
            result = i18n.t(i18nDispNamePath.getI18nPath());
            // 如果找到了翻译（不是返回原路径），则使用原有路径的翻译
            if(!result.equals(i18nDispNamePath.getI18nPath())){
                return result;
            }
            else if(deviceI18nPrefixExists){
                // 如果设备分组路径存在，就返回设备路径
                return devicePath;
            }
            else{
                // 否则返回属性默认路径
                return result;
            }
        }else{
            return "";
        }
    }

    /**
     * 获取显示名称，统一对外显示接口
     * 优先使用 displayName 字段，如果不存在则使用 I18n 系统获取
     */
    public String getDisplayName() {
        if(displayName != null){
            return displayName;
        }else{
            return getI18nDisplayName();
        }
    }

    /**
     * 设置属性的语义描述
     * 集成开发者在创建属性时提供，说明该属性的作用、单位含义、正常范围等
     *
     * @param description 属性语义描述
     * @return this（支持链式调用）
     */
    public AttributeBase<T> setDescription(String description) {
        this.description = description;
        return this;
    }

    /**
     * 获取属性的语义描述
     *
     * @return 属性语义描述，未设置时返回 null
     */
    public String getDescription() {
        return description;
    }

    /**
     * 检查是否有验证定义
     * @return 是否有验证定义
     */
    public boolean hasValidation() {
        return getLazyValueDefinition() != null;
    }

    /**
     * 获取验证错误信息
     * @return 验证错误信息，如果没有错误则返回空Map
     */
    public Map<String, String> getValidationErrors() {
        ConfigDefinition def = getLazyValueDefinition();
        if (def == null) {
            return new HashMap<>();
        }
        Map<String, String> errors = new HashMap<>();
        for (Map.Entry<ConfigItem<?>, String> entry : def.getInvalidConfigItems().entrySet()) {
            errors.put(entry.getKey().getKey(), entry.getValue());
        }
        return errors;
    }

    /**
     * 设置验证器
     * @param validator 验证器，如果为null则移除验证
     */
    public void setValidator(ConstraintValidator<T> validator) {
        if (validator == null) {
            this.valueDef = null;
        } else {
            // 创建配置项
            ConfigItem<T> configItem =
                new ConfigItem<>(
                    "value",
                    targetType,
                    true,
                    getValue(),
                    validator
                );

            // 创建配置项构建器
            ConfigItemBuilder builder =
                new ConfigItemBuilder();

            // 添加验证项
            builder.add(configItem);

            // 创建验证定义
            this.valueDef = new ConfigDefinition();
            this.valueDef.define(builder);
        }
    }

}
