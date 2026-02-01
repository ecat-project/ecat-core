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

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;
import java.util.Map;
import java.util.HashMap;
import com.ecat.core.I18n.I18nKeyPath;
import com.ecat.core.Utils.DynamicConfig.ConfigDefinition;
import com.ecat.core.Utils.DynamicConfig.Validator.DynamicStringDictValidator;
import com.ecat.core.Utils.DynamicConfig.ConfigItemBuilder;
import com.ecat.core.Utils.DynamicConfig.ConfigItem;

/**
 * BinaryAttribute is a class that represents a binary attribute (ON/OFF) of a device.
 * It extends the AttributeBase class and provides methods to manage the binary state of the attribute.
 * 
 * This class is suitable for attributes that represent binary states such as
 * switches, lights, alarms, etc.
 * 
 * @apiNote displayName i18n supported, path: state.binary_attr.{attributeID}
 * @apiNote options i18n supported, path: state.binary_attr.options.{option}
 * 
 * @author coffee
 */
public class BinaryAttribute extends AttributeBase<Boolean> {

    protected ConfigDefinition valueDef;        // 验证定义
    protected Map<String, String> optionCache;  // 选项缓存
    protected final String ON = "on";   // 开启状态标识
    protected final String OFF = "off"; // 关闭状态标识

    /**
     * 支持I18n的构造函数
     *
     * @param attributeID
     * @param attrClass
     * @param valueChangeable
     * @param onChangedCallback
     */
    public BinaryAttribute(String attributeID, AttributeClass attrClass, boolean valueChangeable,
            Function<AttrChangedCallbackParams<Boolean>, CompletableFuture<Boolean>> onChangedCallback) {
        super(attributeID, attrClass, null, null, 0, false,
                valueChangeable, onChangedCallback);
        this.optionCache = new HashMap<>();
        this.valueDef = getValueDefinition(); // 初始化验证定义
    }

    /**
     * 支持I18n的构造函数
     */
    public BinaryAttribute(String attributeID, AttributeClass attrClass, boolean valueChangeable) {
        this(attributeID, attrClass, valueChangeable, null);
    }

    /**
     * 同时支持用户设置显示名称displayName和I18n的构造函数，displayName优先级高
     */
    public BinaryAttribute(String attributeID, String displayName, AttributeClass attrClass, boolean valueChangeable) {
        this(attributeID, displayName, attrClass, valueChangeable, null);
    }

    /**
     * 同时支持用户设置显示名称displayName和I18n的构造函数，displayName优先级高
     */
    public BinaryAttribute(String attributeID, String displayName, AttributeClass attrClass, boolean valueChangeable,
            Function<AttrChangedCallbackParams<Boolean>, CompletableFuture<Boolean>> onChangedCallback) {
        super(attributeID, displayName, attrClass, null, null, 0, false,
                valueChangeable, onChangedCallback);
        this.optionCache = new HashMap<>();
        this.valueDef = getValueDefinition(); // 初始化验证定义
    }

    // @Override
    // public boolean updateValue(Boolean value) {
    //     return super.updateValue(value);
    // }

	// @Override
	// public String getValue() {
	// 	// Provide implementation
    //     return value.toString();
	// }
    
    @Override
    protected CompletableFuture<Boolean> setDisplayValueImp(Boolean displayValue, UnitInfo fromUnit){
        CompletableFuture<Boolean>  rest;
        if(displayValue){
            rest = this.asyncTurnOn();
        }
        else{
            rest = this.asyncTurnOff();
        }
        return rest;
    }

    @Override
    protected Boolean convertFromUnitImp(Boolean value, UnitInfo fromUnit) {
        return value; // BinaryAttribute does not require unit conversion
    }

    @Override
    public Double convertValueToUnit(Double value, UnitInfo fromUnit, UnitInfo toUnit) {
        throw new UnsupportedOperationException("BinaryAttribute does not support unit conversion");
    }

    @Override
    public String getDisplayValue(UnitInfo toUnit){
        if (value == null) {
            return null;
        }
        return value ? getOnDisplayText() : getOffDisplayText();
    }

    @Override
    public String getI18nValue(UnitInfo toUnit){
        if (value == null) {
            return null;
        }
        return value ? ON : OFF;
    }

    /**
     * 设备侧TurnOn方法，给设备侧功能使用，记录为开启状态，无其他联动操作
     * @return
     */
    public boolean turnOn(){
        return updateValue(true);
    }

    /**
     * 设备侧TurnOff方法，给设备侧功能使用，记录为关闭状态，无其他联动操作
     * @return
     */
    public boolean turnOff(){
        return updateValue(false);
    }

    /**
     * 异步asyncTurnOn方法具体实现，比如给设备提交命令
     * @return
     */
    protected CompletableFuture<Boolean> asyncTurnOnImpl() {
        return CompletableFuture.completedFuture(true);
    }

    /**
     * 异步asyncTurnOff方法具体实现，比如给设备提交命令
     * @return
     */
    protected CompletableFuture<Boolean> asyncTurnOffImpl() {
        return CompletableFuture.completedFuture(true);
    }

    /**
     * 异步TurnOn方法，给用户侧功能使用
     * 
     * @return
     */
    public CompletableFuture<Boolean> asyncTurnOn(){
        return asyncTurnOnImpl().thenCompose(result -> {
            if (result) {
                return setValue(true).thenApply(success -> {
                    if (success) {
                        log.info("Device " + getDevice().getId() + " - Turn On Successed: " + getOnDisplayText());
                    }
                    return success;
                });
            }
            return CompletableFuture.completedFuture(false);
        }).exceptionally(e -> {
            log.error("Device " + getDevice().getId() + " - Turn On Failed: " + e.getMessage());
            return false;
        });
    }

    /**
     * 异步TurnOff方法，给用户侧功能使用
     *
     * @return
     */
    public CompletableFuture<Boolean> asyncTurnOff(){
        return asyncTurnOffImpl().thenCompose(result -> {
            if (result) {
                return setValue(false).thenApply(success -> {
                    if (success) {
                        log.info("Device " + getDevice().getId() + " - Turn Off Successed: " + getOffDisplayText());
                    }
                    return success;
                });
            }
            return CompletableFuture.completedFuture(false);
        }).exceptionally(e -> {
            log.error("Device " + getDevice().getId() + " - Turn Off Failed: " + e.getMessage());
            return false;
        });
    }

    /**
     * 获取选项的国际化路径前缀
     * 约定使用后缀"_options"，例如：devices.qc_device.device_status_options
     *
     * @return I18nKeyPath 选项的国际化路径前缀
     */
    public I18nKeyPath getI18nOptionPathPrefix() {
        return getI18nDispNamePath().withSuffix("_options");
    }

    @Override
    public I18nKeyPath getI18nPrefixPath() {
        return new I18nKeyPath("state.binary_attr.", "");
    }

    @Override
    public ConfigDefinition getValueDefinition() {
        if (valueDef == null) {
            valueDef = new ConfigDefinition();

            // 创建动态字典验证器
            // 创建动态字符串字典验证器（参考 00-validator-classes-reference.md）
            DynamicStringDictValidator valueValidator =
                new DynamicStringDictValidator(this::getOptionDict);

            ConfigItemBuilder builder =
                new ConfigItemBuilder()
                    .add(new ConfigItem<>("value", String.class, true, null, valueValidator));

            valueDef.define(builder);
        }
        return valueDef;
    }

    /**
     * 获取二值选项字典（k-v 结构）
     * key 为 ON/OFF 字符串，value 为显示名称
     * @return 二值选项字典
     */
    public Map<String, String> getOptionDict() {
        // 使用缓存提高性能
        if (optionCache.isEmpty()) {
            synchronized (optionCache) {
                if (optionCache.isEmpty()) {
                    optionCache.put(ON, getOnDisplayText());
                    optionCache.put(OFF, getOffDisplayText());
                }
            }
        }
        return new HashMap<>(optionCache);
    }

    /**
     * 获取开启状态的显示文本
     * @return 开启状态显示文本
     */
    protected String getOnDisplayText() {
        return i18n.t(getI18nOptionPathPrefix().addLastSegment(ON).getI18nPath());
    }

    /**
     * 获取关闭状态的显示文本
     * @return 关闭状态显示文本
     */
    protected String getOffDisplayText() {
        return i18n.t(getI18nOptionPathPrefix().addLastSegment(OFF).getI18nPath());
    }

    /**
     * 切换状态
     * @return 切换结果
     */
    public CompletableFuture<Boolean> toggle() {
        Boolean currentValue = getValue();
        return currentValue != null ?
            (currentValue ? asyncTurnOff() : asyncTurnOn()) :
            CompletableFuture.completedFuture(false);
    }

    /**
     * 获取是否为开启状态
     * @return 是否为开启状态
     */
    public boolean isOn() {
        Boolean currentValue = getValue();
        return currentValue != null && currentValue;
    }

    /**
     * 获取是否为关闭状态
     * @return 是否为关闭状态
     */
    public boolean isOff() {
        Boolean currentValue = getValue();
        return currentValue != null && !currentValue;
    }

    /**
     * 验证布尔值是否有效
     * @param value 布尔值
     * @return 是否有效
     */
    public boolean isValidValue(Boolean value) {
        return value != null;
    }

    @Override
    public AttributeType getAttributeType() {
        return AttributeType.BINARY;
    }

}
