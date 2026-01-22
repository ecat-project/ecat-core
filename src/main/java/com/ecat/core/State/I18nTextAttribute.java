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
 * 支持有限值集合的国际化文本属性
 * 继承自TextAttribute，增加了选项限制和国际化支持
 * 适用于设备状态文本等具有固定选项的文本属性
 *
 * @apiNote displayName i18n supported, path: state.i18n_text_attr.{attributeID}
 * @apiNote options i18n supported, path: state.i18n_text_attr.{attributeID}_options.{option}
 *
 * @author coffee
 */
public class I18nTextAttribute extends TextAttribute {

    @Getter
    protected List<String> options;              // 选项列表（构造时确定，不可修改）
    @Getter
    protected boolean caseSensitive = false;      // 是否区分大小写
    protected Map<String, String> optionCache;    // 选项缓存
    protected ConfigDefinition valueDef;          // 验证定义

    /**
     * 支持I18n的构造函数
     */
    public I18nTextAttribute(String attributeID, AttributeClass attrClass, boolean valueChangeable, List<String> options) {
        this(attributeID, attrClass, null, null, valueChangeable, options, null);
    }

    /**
     * 同时支持用户设置显示名称displayName和I18n的构造函数，displayName优先级高
     */
    public I18nTextAttribute(String attributeID, String displayName, AttributeClass attrClass, boolean valueChangeable, List<String> options) {
        this(attributeID, displayName, attrClass, null, null, valueChangeable, options, null);
    }

    /**
     * 支持I18n的构造函数
     */
    public I18nTextAttribute(String attributeID, AttributeClass attrClass, UnitInfo nativeUnit,
            UnitInfo displayUnit, boolean valueChangeable, List<String> options,
            Function<AttrChangedCallbackParams<String>, CompletableFuture<Boolean>> onChangedCallback) {
        super(attributeID, attrClass, nativeUnit, displayUnit, valueChangeable, onChangedCallback);

        this.options = options;
        this.optionCache = new HashMap<>();
        this.caseSensitive = false;
    }

    /**
     * 同时支持用户设置显示名称displayName和I18n的构造函数，displayName优先级高
     */
    public I18nTextAttribute(String attributeID, String displayName, AttributeClass attrClass, UnitInfo nativeUnit,
            UnitInfo displayUnit, boolean valueChangeable, List<String> options,
            Function<AttrChangedCallbackParams<String>, CompletableFuture<Boolean>> onChangedCallback) {
        super(attributeID, displayName, attrClass, nativeUnit, displayUnit, valueChangeable, onChangedCallback);

        this.options = options;
        this.optionCache = new HashMap<>();
        this.caseSensitive = false;
    }

    /**
     * 支持I18n的构造函数（新增大小写敏感参数）
     */
    public I18nTextAttribute(String attributeID, AttributeClass attrClass, UnitInfo nativeUnit,
            UnitInfo displayUnit, boolean valueChangeable, List<String> options,
            Function<AttrChangedCallbackParams<String>, CompletableFuture<Boolean>> onChangedCallback,
            boolean caseSensitive) {
        super(attributeID, attrClass, nativeUnit, displayUnit, valueChangeable, onChangedCallback);

        this.options = options;
        this.optionCache = new HashMap<>();
        this.caseSensitive = caseSensitive;
    }

    /**
     * 同时支持用户设置显示名称displayName和I18n的构造函数，displayName优先级高
     */
    public I18nTextAttribute(String attributeID, String displayName, AttributeClass attrClass, UnitInfo nativeUnit,
            UnitInfo displayUnit, boolean valueChangeable, List<String> options,
            Function<AttrChangedCallbackParams<String>, CompletableFuture<Boolean>> onChangedCallback,
            boolean caseSensitive) {
        super(attributeID, displayName, attrClass, nativeUnit, displayUnit, valueChangeable, onChangedCallback);

        this.options = options;
        this.optionCache = new HashMap<>();
        this.caseSensitive = caseSensitive;
    }

    @Override
    public String getDisplayValue(UnitInfo toUnit) {
        if (value == null) {
            return null;
        }
        return getOptionI18nName(value);
    }

    @Override
    public String getI18nValue(UnitInfo toUnit) {
        return value != null ? value.toLowerCase(Locale.ENGLISH) : "";
    }

    @Override
    public I18nKeyPath getI18nPrefixPath() {
        return new I18nKeyPath("state.i18n_text_attr.", "");
    }

    /**
     * 获取选项的国际化路径前缀
     * 约定使用后缀"_options"
     */
    public I18nKeyPath getI18nOptionPathPrefix() {
        return getI18nDispNamePath().withSuffix("_options");
    }

    /**
     * 获取选项的国际化显示名称
     * @param option 选项值
     * @return 国际化显示名称
     */
    public String getOptionI18nName(String option) {
        return i18n.t(getI18nOptionPathPrefix().getFullPath() + "." + option.toLowerCase(Locale.ENGLISH));
    }

    /**
     * 获取选项字典（k-v 结构）
     * k 为选项值，v 为国际化显示名称
     * @return 选项字典
     */
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

    /**
     * 获取所有选项的显示名称列表（按选项排序）
     * @return 排序后的显示名称列表
     */
    public List<String> getSortedOptionDisplayNames() {
        List<String> displayNames = new ArrayList<>(getOptionDict().values());
        Collections.sort(displayNames);
        return displayNames;
    }

    /**
     * 根据显示名称查找选项值（支持大小写不敏感）
     * @param displayName 显示名称
     * @return 选项值，如果找不到则返回 null
     */
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
     * 验证选项值是否有效
     * @param option 选项值
     * @return 是否有效
     */
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

    @Override
    public boolean updateValue(String newValue) {
        if (newValue != null && !isValidOption(newValue)) {
            log.warn("值 {} 不在有效选项列表中: {}", newValue, options);
            return false;
        }
        return super.updateValue(newValue);
    }

    @Override
    public boolean updateValue(String newValue, AttributeStatus newStatus) {
        if (newValue != null && !isValidOption(newValue)) {
            log.warn("值 {} 不在有效选项列表中: {}", newValue, options);
            return false;
        }
        return super.updateValue(newValue, newStatus);
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
}
