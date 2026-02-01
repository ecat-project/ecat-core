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
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import lombok.Getter;

import com.ecat.core.Utils.LogFactory;
import com.ecat.core.Utils.Log;
import com.ecat.core.Bus.BusTopic;
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
    protected java.time.LocalDateTime updateTime; // 数据更新时间，对于实时数据等同于数据产生的时间
    protected Function<AttrChangedCallbackParams<T>, CompletableFuture<Boolean>> onChangedCallback = null; // 设备数据更新回调函数

    // 数据属性
    protected UnitInfo nativeUnit; //原始信号单位，允许为null
    protected UnitInfo displayUnit; //显示信号单位，允许为null，显示使用，不存储数据库
    
    protected String displayName; // 用户设置的高优先级显示名称，对外显示displayName优先级>i18n名称
    protected int displayPrecision; // 显示精度，小数位，显示使用不存储数据库
    protected boolean unitChangeable;


    protected I18nKeyPath i18nDispNamePath; // 属性名称的i18n显示资源的json地址，目前规则为全部小写，见getI18nPrefixPath
    protected ConfigDefinition valueDef;    // 输入value的验证规则，null代表不需要验证
    protected I18nProxy i18n = I18nHelper.createProxy(this.getClass()); // 默认使用本类的类名作为i18n的namespace，实际场景如何某集成使用了不是自己集成内的属性，则资源寻找不对，需要在setDevice中重新设置

    protected Log log;

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
     * 支持I18n的构造函数，属性i18n名称规则见i18nDispNamePath
     * 适合参数固定名称的设备的国际化支持，比如气象仪、监测仪等
     * 
     * @param attributeID 属性ID, 推荐使用小写，以便对应i18n的key
     * @see i18nDispNamePath
     */
    protected AttributeBase(String attributeID, AttributeClass attrClass, UnitInfo nativeUnit,
        UnitInfo displayUnit, int displayPrecision, boolean unitChangeable, boolean valueChangeable,
        Function<AttrChangedCallbackParams<T>, CompletableFuture<Boolean>> onChangedCallback){

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

        // 初始化日志对象为子类的类名
        this.log = LogFactory.getLogger(getClass());

        // 初始化I18n路径 - 使用子类提供的路径前缀
        this.i18nDispNamePath = getI18nPrefixPath().withLastSegment(attributeID.toLowerCase(Locale.ENGLISH));

        // 延迟初始化验证定义，避免在构造函数中调用抽象方法
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

    @Override
    public boolean setStatus(AttributeStatus newStatus){
        status = newStatus;
        return true;
    }

    @Override
    public AttributeStatus getStatus(){
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
     * 获取属性原始值
     */
    @Override
	public T getValue() {
		// Provide implementation
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
            this.updateTime = java.time.LocalDateTime.now();
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
    public AttrValueType getValueType(){
        return AttrValueType.fromJDKClass(targetType);
    }

    /**
     * 获取属性值的类型名称
     * @apiNote 为序列化等场景提供字符串类型名称
     * @return 属性值类型名称
     */
    public String getValueTypeName(){
        return getValueType().getValueTypeName();
    }

    /**
     * 发布属性状态，提交给bus
     * 按需更新，只有属性值发生变化时才更新
     */
    public boolean publicState() {
        if(this.isValueUpdated){
            try {
                device.getCore().getBusRegistry().publish(BusTopic.DEVICE_DATA_UPDATE.getTopicName(), this);
            } catch (Exception e) {
                log.error("Failed to update attribute " + this.getAttributeID() + " for device " + device.getId());
                log.error(e.getStackTrace().toString());
                return false;
            }
            this.setValueUpdated(false);
        }
        return true;
    }

    /**
     * 设备自己更新原始数据，不触发onChangedCallback订阅，适于收到设备新数据更新状态值
     * 子类根据需求设置访问范围
     * 不要给用户侧操作使用此函数
     * @param newValue
     * @return
     */
    protected boolean updateValue(T newValue){
        this.value = newValue;
        this.setValueUpdated();
        return true;
    }

    /**
     * 设备自己更新原始数据，不触发onChangedCallback订阅，适于收到设备新数据更新状态值
     * 子类根据需求设置访问范围
     * 不要给用户侧操作使用此函数
     * @param newValue
     * @param newStatus
     * @return
     */
    protected boolean updateValue(T newValue, AttributeStatus newStatus){
        this.status = newStatus;
        return updateValue(newValue);
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
    private T convertStringToType(String source) throws Exception {
        if (targetType == Integer.class) {
            return targetType.cast(Integer.parseInt(source));
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
            return targetType.cast(Short.parseShort(source));
        }
        if (targetType == Long.class) {
            return targetType.cast(Long.parseLong(source));
        }
        if (targetType == Byte.class) {
            return targetType.cast(Byte.parseByte(source));
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
