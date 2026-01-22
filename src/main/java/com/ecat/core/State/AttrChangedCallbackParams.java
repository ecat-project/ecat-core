package com.ecat.core.State;

public class AttrChangedCallbackParams<T> {
    private AttributeBase<T> attributeBase;
    private T newValue;

    public AttrChangedCallbackParams(AttributeBase<T> attributeBase, T newValue) {
        this.attributeBase = attributeBase;
        this.newValue = newValue;
    }

    public AttributeBase<T> getAttributeBase() {
        return attributeBase;
    }

    public T getNewValue() {
        return newValue;
    }
}