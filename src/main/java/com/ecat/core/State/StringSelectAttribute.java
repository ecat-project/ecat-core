package com.ecat.core.State;

import java.util.ArrayList;
import java.util.Collections;
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
 * 
 * @apiNote displayName i18n supported, path: state.string_select_attr.{attributeID}
 * @apiNote options i18n supported, path: state.string_select_attr.{attributeID}_options.{option}
 *
 * @author coffee
 */
public class StringSelectAttribute extends SelectAttribute<String> {

    @Getter
    protected boolean caseSensitive = false; // 是否区分大小写
    protected Map<String, String> optionCache = new HashMap<>(); // 选项缓存

    /**
     * 支持I18n的构造函数
     */
    public StringSelectAttribute(String attributeID, AttributeClass attrClass, boolean valueChangeable, List<String> options) {
        this(attributeID, attrClass, null, null, valueChangeable, options, null);
    }

    /**
     * 同时支持用户设置显示名称displayName和I18n的构造函数，displayName优先级高
     */
    public StringSelectAttribute(String attributeID, String displayName, AttributeClass attrClass, boolean valueChangeable, List<String> options) {
        this(attributeID, displayName, attrClass, null, null, valueChangeable, options, null);
    }

    /**
     * 支持I18n的构造函数
     */
    public StringSelectAttribute(String attributeID, AttributeClass attrClass, UnitInfo nativeUnit,
            UnitInfo displayUnit, boolean valueChangeable, List<String> options,
            Function<AttrChangedCallbackParams<String>, CompletableFuture<Boolean>> onChangedCallback) {
        super(attributeID, attrClass, nativeUnit, displayUnit, valueChangeable, options, onChangedCallback);

        this.caseSensitive = true;
        this.optionCache = new HashMap<>();
        // i18nOptionPathPrefix 由父类自动生成
    }

    /**
     * 同时支持用户设置显示名称displayName和I18n的构造函数，displayName优先级高
     */
    public StringSelectAttribute(String attributeID, String displayName, AttributeClass attrClass, UnitInfo nativeUnit,
            UnitInfo displayUnit, boolean valueChangeable, List<String> options,
            Function<AttrChangedCallbackParams<String>, CompletableFuture<Boolean>> onChangedCallback) {
        super(attributeID, displayName, attrClass, nativeUnit, displayUnit, valueChangeable, options, onChangedCallback);

        this.caseSensitive = true;
        this.optionCache = new HashMap<>();
        // i18nOptionPathPrefix 由父类自动生成
    }

    /**
     * 支持I18n的构造函数（新增大小写敏感参数）
     */
    public StringSelectAttribute(String attributeID, AttributeClass attrClass, UnitInfo nativeUnit,
            UnitInfo displayUnit, boolean valueChangeable, List<String> options,
            Function<AttrChangedCallbackParams<String>, CompletableFuture<Boolean>> onChangedCallback,
            boolean caseSensitive) {
        super(attributeID, attrClass, nativeUnit, displayUnit, valueChangeable, options, onChangedCallback);

        this.caseSensitive = caseSensitive;
        this.optionCache = new HashMap<>();
        // i18nOptionPathPrefix 由父类自动生成
    }

    /**
     * 同时支持用户设置显示名称displayName和I18n的构造函数，displayName优先级高
     */
    public StringSelectAttribute(String attributeID, String displayName, AttributeClass attrClass, UnitInfo nativeUnit,
            UnitInfo displayUnit, boolean valueChangeable, List<String> options,
            Function<AttrChangedCallbackParams<String>, CompletableFuture<Boolean>> onChangedCallback,
            boolean caseSensitive) {
        super(attributeID, displayName, attrClass, nativeUnit, displayUnit, valueChangeable, options, onChangedCallback);

        this.caseSensitive = caseSensitive;
        this.optionCache = new HashMap<>();
        // i18nOptionPathPrefix 由父类自动生成
    }

    @Override
    public String getDisplayValue(UnitInfo toUnit) {
        if (value == null) {
            return null;
        }
        return getOptionI18nName(value);
    }

    @Override
    public String getI18nValue(UnitInfo toUnit){
        return value.toLowerCase(Locale.ENGLISH);
    }

    @Override
    public I18nKeyPath getI18nPrefixPath() {
        return new I18nKeyPath("state.string_select_attr.", "");
    }

    @Override
    public Map<String, String> getOptionDict() {
        // 使用缓存提高性能
        if (optionCache.isEmpty() && options != null) {
            synchronized (optionCache) {
                if (optionCache.isEmpty()) {
                    for (String option : options) {
                        String i18nName = getOptionI18nName(option);
                        optionCache.put(option, i18nName);
                    }
                }
            }
        }
        return new HashMap<>(optionCache);
    }

    @Override
    public ConfigDefinition getValueDefinition() {
        if (valueDef == null) {
            valueDef = new ConfigDefinition();

            // 创建字符串特定的动态字典验证器
            DynamicStringDictValidator valueValidator =
                new DynamicStringDictValidator(
                    this::getOptionDict, caseSensitive);

            ConfigItemBuilder builder =
                new ConfigItemBuilder()
                    .add(new ConfigItem<>("value", String.class, true, null, valueValidator));

            valueDef.define(builder);
        }
        return valueDef;
    }

    /**
     * 子类必须实现，完成具体选项切换逻辑
     */
    @Override
    protected CompletableFuture<Boolean> selectOptionImp(String option) {
        // 默认实现：直接调用回调函数
        if (onChangedCallback != null) {
            return onChangedCallback.apply(new AttrChangedCallbackParams<String>(this, option));
        }
        return CompletableFuture.completedFuture(true);
    }

    /**
     * 根据选项值查找选项（支持大小写不敏感）
     * @param option 选项值
     * @return 找到的选项值，如果找不到则返回 null
     */
    public String findOption(String option) {
        if (option == null) {
            return null;
        }

        if (caseSensitive) {
            return options.contains(option) ? option : null;
        } else {
            for (String validOption : options) {
                if (validOption.equalsIgnoreCase(option)) {
                    return validOption;
                }
            }
            return null;
        }
    }

    /**
     * 根据显示名称查找选项值（支持大小写不敏感）
     * @param displayName 显示名称
     * @return 选项值，如果找不到则返回 null
     */
    @Override
    public String getOptionByDisplayName(String displayName) {
        if (displayName == null) {
            return null;
        }

        Map<String, String> optionDict = getOptionDict();

        if (caseSensitive) {
            for (Map.Entry<String, String> entry : optionDict.entrySet()) {
                if (entry.getValue().equals(displayName)) {
                    return entry.getKey();
                }
            }
        } else {
            for (Map.Entry<String, String> entry : optionDict.entrySet()) {
                if (entry.getValue().equalsIgnoreCase(displayName)) {
                    return entry.getKey();
                }
            }
        }

        return null;
    }

    /**
     * 验证选项值是否有效
     * @param option 选项值
     * @return 是否有效
     */
    @Override
    public boolean isValidOption(String option) {
        if (option == null) {
            return false;
        }

        return findOption(option) != null;
    }

    /**
     * 验证选项值并返回规范化值
     * @param option 选项值
     * @return 规范化的选项值，如果无效则返回 null
     */
    public String normalizeOption(String option) {
        return findOption(option);
    }

    /**
     * 获取所有选项的显示名称列表（按选项排序）
     * @return 排序后的显示名称列表
     */
    public List<String> getSortedOptionDisplayNames() {
        List<String> displayNames = new ArrayList<>(getOptionDict().values());
        Collections.sort(displayNames);
        return displayNames;
    }

    @Override
    public AttributeType getAttributeType() {
        return AttributeType.STRING_SELECT;
    }

}
