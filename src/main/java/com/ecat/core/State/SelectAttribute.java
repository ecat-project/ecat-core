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

    protected ConfigDefinition valueDef;        // 验证定义

    /**
     * 支持I18n的构造函数
     */
    public SelectAttribute(String attributeID, AttributeClass attrClass, boolean valueChangeable, List<T> options) {
        this(attributeID, attrClass, null, null, valueChangeable, options, null);
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
        super(attributeID, attrClass, nativeUnit, displayUnit, 0, false,
                valueChangeable, onChangedCallback);

        this.options = options;
        this.valueDef = getValueDefinition(); // 初始化验证定义
    }

    /**
     * 同时支持用户设置显示名称displayName和I18n的构造函数，displayName优先级高
     */
    public SelectAttribute(String attributeID, String displayName, AttributeClass attrClass, UnitInfo nativeUnit,
            UnitInfo displayUnit, boolean valueChangeable, List<T> options,
            Function<AttrChangedCallbackParams<T>, CompletableFuture<Boolean>> onChangedCallback) {
        super(attributeID, displayName, attrClass, nativeUnit, displayUnit, 0, false,
                valueChangeable, onChangedCallback);

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
     * 获取选项的国际化显示名称
     * @param option 选项值
     * @return 国际化显示名称
     * @apiNote path: getI18nOptionPathPrefix().getFullPath().{optionLowerCase}
     */
    public String getOptionI18nName(T option) {
        return i18n.t(getI18nOptionPathPrefix().getFullPath() + "." + option.toString().toLowerCase(Locale.ENGLISH));
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
