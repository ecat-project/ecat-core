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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import lombok.Getter;
import com.ecat.core.I18n.I18nKeyPath;
import com.ecat.core.Utils.DynamicConfig.ConfigDefinition;
import com.ecat.core.Utils.DynamicConfig.ConfigItem;
import com.ecat.core.Utils.DynamicConfig.ConfigItemBuilder;
import com.ecat.core.Utils.DynamicConfig.Validator.DynamicStringDictValidator;

/**
 * 适用于模式选择等需要从列表切换选项的属性
 * 如风量高低档，工作状态
 * 与CommandAttribute区分为SelectAttribute具有设备对应的实体属性值，CommandAttribute没有实体属性值
 * 
 * @apiNote displayName i18n supported, path: state.select_attr.{attributeID}
 * @apiNote options i18n supported, path: state.select_attr.options.{option}
 * 
 * @implSpec subclass must implement selectOptionImp to complete the specific option switching logic
 * @implNote this class just provide readonly access to outside, outside can't inherit this class directly.
 * @implNote only the derived class in this package can inherit this class.
 * 
 * @see StringSelectAttribute
 * 
 * @author coffee
 */
public abstract class SelectAttribute<T> extends AttributeBase<T> {

    /**
     * 候选值限定列表
     * 要求内容为非空且不重复，需要使用能够标识性的值，比如英文字符串
     * 作为selectOption的输入参数
     */
    @Getter
    protected List<T> options;
    /**
     * 选项显示标签(整批,key=选项 value=显示文本),由集成一次性灌入(setOptionsDisplayText)。
     * 用于运行时动态选项(如 modbus-generic 用户自定义枚举值)的中文/可读显示。
     * 显示优先级:getOptionI18nName → ①有意义 i18n 翻译 > ②optionsDisplayText > ③整条 i18n key(开发诊断:提示此 option 缺①+②)。
     */
    protected Map<T, String> optionsDisplayText;

    /** 一次性设置所有选项的显示标签(整批)。key=选项,value=显示文本。 */
    public void setOptionsDisplayText(Map<T, String> optionsDisplayText) {
        this.optionsDisplayText = optionsDisplayText;
    }

    protected ConfigDefinition valueDef;        // 验证定义

    /**
     * 支持I18n的构造函数
     */
    public SelectAttribute(String attributeID, AttributeClass attrClass, boolean valueChangeable, List<T> options) {
        this(attributeID, attrClass, null, null, valueChangeable, options, null);
    }

    /**
     * 仅 attributeID 的构造函数，用于 "先 new 再 init" 模式。
     * 不要滥用，仅在此模式下使用。
     */
    protected SelectAttribute(String attributeID) {
        super(attributeID);
    }

    /**
     * 同时支持用户设置显示名称displayName和I18n的构造函数，displayName优先级高
     */
    public SelectAttribute(String attributeID, String displayName, AttributeClass attrClass, boolean valueChangeable, List<T> options) {
        this(attributeID, displayName, attrClass, null, null, valueChangeable, options, null);
    }

    /**
     * 支持I18n的构造函数
     */
    public SelectAttribute(String attributeID, AttributeClass attrClass, UnitInfo nativeUnit,
            UnitInfo displayUnit, boolean valueChangeable, List<T> options,
            Function<AttrChangedCallbackParams<T>, CompletableFuture<Boolean>> onChangedCallback) {
        this(attributeID, attrClass, nativeUnit, displayUnit, valueChangeable, options, false, null, onChangedCallback);
    }

    /**
     * 同时支持用户设置显示名称displayName和I18n的构造函数，displayName优先级高
     */
    public SelectAttribute(String attributeID, String displayName, AttributeClass attrClass, UnitInfo nativeUnit,
            UnitInfo displayUnit, boolean valueChangeable, List<T> options,
            Function<AttrChangedCallbackParams<T>, CompletableFuture<Boolean>> onChangedCallback) {
        this(attributeID, displayName, attrClass, nativeUnit, displayUnit, valueChangeable, options, false, null, onChangedCallback);
    }

    /**
     * 完整参数构造函数（包含 persistable + defaultValue）
     * 用于支持属性持久化场景
     */
    public SelectAttribute(String attributeID, AttributeClass attrClass, UnitInfo nativeUnit,
            UnitInfo displayUnit, boolean valueChangeable, List<T> options,
            boolean persistable, T defaultValue,
            Function<AttrChangedCallbackParams<T>, CompletableFuture<Boolean>> onChangedCallback) {
        super(attributeID, attrClass, nativeUnit, displayUnit, 0, false,
                valueChangeable, persistable, defaultValue, onChangedCallback);

        this.options = options;
        this.valueDef = getValueDefinition(); // 初始化验证定义
    }

    /**
     * 完整参数构造函数（包含 displayName + persistable + defaultValue）
     * 用于支持属性持久化场景
     */
    public SelectAttribute(String attributeID, String displayName, AttributeClass attrClass, UnitInfo nativeUnit,
            UnitInfo displayUnit, boolean valueChangeable, List<T> options,
            boolean persistable, T defaultValue,
            Function<AttrChangedCallbackParams<T>, CompletableFuture<Boolean>> onChangedCallback) {
        super(attributeID, attrClass, nativeUnit, displayUnit, 0, false,
                valueChangeable, persistable, defaultValue, onChangedCallback);
        this.displayName = displayName;

        this.options = options;
        this.valueDef = getValueDefinition(); // 初始化验证定义
    }

    /**
     * just for automation using, not for user uesd to select option
     */
    @Override
    protected CompletableFuture<Boolean> setDisplayValueImp(T displayValue, UnitInfo fromUnit) {
        if(options.contains(displayValue)){
            return selectOption(displayValue);
        }
        return CompletableFuture.completedFuture(false);
    }

    @Override
    protected T convertFromUnitImp(T value, UnitInfo toUnit) {
        // SelectAttribute does not require unit conversion
        return value;
    }

    @Override
    public Double convertValueToUnit(Double value, UnitInfo fromUnit, UnitInfo toUnit) {
        // SelectAttribute does not support unit conversion by default
        // Subclasses can override if they need numeric unit conversion
        throw new UnsupportedOperationException("SelectAttribute does not support unit conversion");
    }

    /**
     * just for device data update, not for user used to select option
     */
    @Override
    public boolean updateValue(T newValue, AttributeStatus status) {
        if (options.contains(newValue)) {
            return super.updateValue(newValue, status);
        } else {
            log.error("Attempted to update SelectAttribute with invalid option: {}", newValue);
            return false;
        }
    }

    /**
     * just for device data update, not for user used to select option
     */
    @Override
    public boolean updateValue(T newValue) {
        if (options.contains(newValue)) {
            return super.updateValue(newValue);
        } else {
            log.error("Attempted to update SelectAttribute with invalid option: {}", newValue);
            return false;
        }
    }

    // value is current select option
    public T getCurrentOption() {
        return value;
    }

    /**
     * for user to select option, not used in device data update
     * 封装完整流程，子类只需实现selectOptionImp
     * @param option 选项值，必须在options列表中
     * @return 选项切换是否成功
     */
    public final CompletableFuture<Boolean> selectOption(T option) {
        return this.selectOption(option, true);
    }

    /**
     * for user to select option, not used in device data update
     * 封装完整流程，子类只需实现selectOptionImp
     * @param option 选项值，必须在options列表中
     * @param publicState 是否发布状态变更, true表示发布，false表示不发布
     * @return 选项切换是否成功
     */
    public final CompletableFuture<Boolean> selectOption(T option, boolean publicState) {
        if (!valueChangeable) {
            return CompletableFuture.completedFuture(false);
        }
        if (option.equals(value)) {
            return CompletableFuture.completedFuture(true);
        }

        // 验证选项是否在有效列表中
        if (!options.contains(option)) {
            log.error("选项 {} 不在有效选项列表中: {}", option, options);
            return CompletableFuture.completedFuture(false);
        }

        return selectOptionImp(option).thenApply(result -> {
            if (result) {
                setValue(option);
                if(publicState){
                    publicState();
                }
                log.info("Device " + getDevice().getId() + " - Select Option Successed: " + getValue() +
                        " (" + getCurrentOptionI18nName() + ")");
            }
            return result;
        }).exceptionally(e -> {
            log.error("Device " + getDevice().getId() + " - Select Option Failed: " + e.getMessage());
            return false;
        });
    }

    /**
     * 获取选项字典（k-v 结构）
     * k 为选项值，v 为国际化显示名称
     * @return 选项字典
     */
    public abstract Map<T, String> getOptionDict();

    /**
     * 子类必须实现，完成具体选项切换逻辑
     */
    protected abstract CompletableFuture<Boolean> selectOptionImp(T option);

    @Override
    public ConfigDefinition getValueDefinition() {
        if (valueDef == null) {
            valueDef = new ConfigDefinition();

            // 创建动态字典验证器
            // 创建动态字符串字典验证器（参考 00-validator-classes-reference.md）
            DynamicStringDictValidator valueValidator =
                new DynamicStringDictValidator(() -> {
                    Map<T, String> originalDict = getOptionDict();
                    Map<String, String> stringDict = new HashMap<>();
                    if (originalDict != null) {
                        for (Map.Entry<T, String> entry : originalDict.entrySet()) {
                            stringDict.put(entry.getKey().toString(), entry.getValue());
                        }
                    }
                    return stringDict;
                });

            ConfigItemBuilder builder =
                new ConfigItemBuilder()
                    .add(new ConfigItem<>("value", String.class, true, null, valueValidator));

            valueDef.define(builder);
        }
        return valueDef;
    }

    /**
     * 获取选项的国际化路径前缀
     * 约定使用后缀"_options"，例如：devices.qc_device.start_option_options
     *
     * @return I18nKeyPath 选项的国际化路径前缀
     */
    public I18nKeyPath getI18nOptionPathPrefix() {
        return getI18nDispNamePath().withSuffix("_options");
    }

    @Override
    public I18nKeyPath getI18nPrefixPath() {
        return new I18nKeyPath("state.select_attr.", "");
    }

    /**
     * 默认的选项字典实现
     * 子类可以重写此方法提供自定义实现
     */
    protected Map<T, String> getDefaultOptionDict() {
        Map<T, String> dict = new HashMap<>();
        if (options != null) {
            for (T option : options) {
                dict.put(option, getOptionI18nName(option));
            }
        }
        return dict;
    }

    /**
     * 获取选项的显示名称(三级优先级)。
     * <ol>
     *   <li>① 有意义 i18n 翻译(i18n.t 命中,translated != key)→ 用翻译。</li>
     *   <li>② 集成灌入的 optionsDisplayText(此 option 有非空标签)→ 用它。
     *       覆盖动态/自定义选项无 strings.json 的场景(如 modbus-generic 用户填中文枚举值)。</li>
     *   <li>③ 整条 i18n key——开发诊断信号:提示此 option 既无 i18n 翻译、集成也未灌 displayText,需补。
     *       正常用户不应看到③(集成有责任为每个 option 灌②)。</li>
     * </ol>
     * @param option 选项值
     * @return 显示名称
     * @apiNote 运行时(属性绑设备后)i18n path 实为 devices.{deviceTypeName}.{attributeID}_options.{option};
     *         未绑设备兜底 state.select_attr.{attributeID}_options.{option}。详见 SelectAttribute 类注释订正。
     */
    public String getOptionI18nName(T option) {
        String key = getI18nOptionPathPrefix().getFullPath() + "." + option.toString().toLowerCase(Locale.ENGLISH);
        String translated = i18n.t(key);
        if (!translated.equals(key)) {
            return translated;                                          // ① 有意义 i18n
        }
        if (optionsDisplayText != null) {
            String lbl = optionsDisplayText.get(option);
            if (lbl != null && !lbl.isEmpty()) {
                return lbl;                                             // ② 集成 displayText
            }
        }
        return key;                                                     // ③ 整条 i18n key(开发诊断)
    }

    /**
     * 获取当前选项的国际化显示名称
     * @return 当前选项的国际化显示名称
     */
    public String getCurrentOptionI18nName() {
        T currentOption = getCurrentOption();
        return currentOption != null ? getOptionI18nName(currentOption) : "";
    }

    /**
     * 获取所有选项的显示名称列表
     * @return 显示名称列表
     */
    public List<String> getOptionDisplayNames() {
        List<String> displayNames = new ArrayList<>();
        if (options != null) {
            for (T option : options) {
                displayNames.add(getOptionI18nName(option));
            }
        }
        return displayNames;
    }

    /**
     * 根据显示名称查找选项值
     * @param displayName 显示名称
     * @return 选项值，如果找不到则返回 null
     */
    public T getOptionByDisplayName(String displayName) {
        Map<T, String> optionDict = getOptionDict();
        if (optionDict != null) {
            for (Map.Entry<T, String> entry : optionDict.entrySet()) {
                if (entry.getValue().equals(displayName)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    /**
     * 检查选项是否有效
     * @param option 选项值
     * @return 是否有效
     */
    public boolean isValidOption(T option) {
        return options != null && options.contains(option);
    }

}
