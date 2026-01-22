package com.ecat.core.State;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import com.ecat.core.I18n.I18nKeyPath;
import com.ecat.core.Utils.DynamicConfig.ConfigDefinition;

/**
 * TextAttribute class represents an attribute that holds a text value.
 * It extends the AttributeBase class and provides methods to manage the text attribute.
 * 
 * This class is suitable for attributes that represent text-based states such as
 * switches, visitors, alarms, etc.
 * 
 * It provides methods to set and get the text value, as well as to handle
 * callbacks when the attribute value changes.
 * 
 * @apiNote displayName i18n supported, path: state.text_attr.{attributeID}
 * 
 * @author coffee
 */
public class TextAttribute extends AttributeBase<String> {

    /**
     * 支持I18n的构造函数
     */
	public TextAttribute(String attributeID, AttributeClass attrClass, UnitInfo nativeUnit,
            UnitInfo displayUnit, boolean valueChangeable) {
        this(attributeID, attrClass, nativeUnit, displayUnit, valueChangeable, null);
    }

    /**
     * 同时支持用户设置显示名称displayName和I18n的构造函数，displayName优先级高
     */
    public TextAttribute(String attributeID, String displayName, AttributeClass attrClass, UnitInfo nativeUnit,
            UnitInfo displayUnit, boolean valueChangeable) {
        this(attributeID, displayName, attrClass, nativeUnit, displayUnit, valueChangeable, null);
    }

    /**
     * 支持I18n的构造函数
     */
    public TextAttribute(String attributeID, AttributeClass attrClass, UnitInfo nativeUnit,
            UnitInfo displayUnit, boolean valueChangeable,
            Function<AttrChangedCallbackParams<String>, CompletableFuture<Boolean>> onChangedCallback) {
        super(attributeID, attrClass, nativeUnit, displayUnit, 0, false,
                valueChangeable, onChangedCallback);
    }

    /**
     * 同时支持用户设置显示名称displayName和I18n的构造函数，displayName优先级高
     */
    public TextAttribute(String attributeID,  String displayName, AttributeClass attrClass, UnitInfo nativeUnit,
            UnitInfo displayUnit, boolean valueChangeable,
            Function<AttrChangedCallbackParams<String>, CompletableFuture<Boolean>> onChangedCallback) {
        super(attributeID, displayName, attrClass, nativeUnit, displayUnit, 0, false,
                valueChangeable, onChangedCallback);
    }

    @Override
    public boolean updateValue(String value) {
        return super.updateValue(value);
    }

    @Override
    public boolean updateValue(String value, AttributeStatus newStatus) {
        return super.updateValue(value, newStatus);
    }

	@Override
	public String getValue() {
		// Provide implementation
        return value;
	}

    @Override
    public String getDisplayValue(UnitInfo toUnit) {
        return value;
    }

    @Override
    protected String convertFromUnitImp(String value, UnitInfo toUnit) {
        return value; // TextAttribute does not require unit conversion
    }

    @Override
    public ConfigDefinition getValueDefinition() {
        // Text attributes typically don't need validation by default
        // Subclasses can override this to add specific validation rules
        return null;
    }

    @Override
    public I18nKeyPath getI18nPrefixPath() {
        return new I18nKeyPath("state.text_attr.", "");
    }

    @Override
    public AttributeType getAttributeType() {
        return AttributeType.TEXT;
    }

}
