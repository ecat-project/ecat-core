package com.ecat.core.Utils.DynamicConfig;

import java.util.List;

public class ListValidator<T> implements ConstraintValidator<List<T>> {
    private final ConstraintValidator<T> elementValidator;

    public ListValidator(ConstraintValidator<T> elementValidator) {
        this.elementValidator = elementValidator;
    }

    @Override
    public boolean validate(List<T> value) {
        if (value == null) {
            return true;
        }
        for (T element : value) {
            if (!elementValidator.validate(element)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String getErrorMessage() {
        return "列表中的元素不满足验证条件: " + elementValidator.getErrorMessage();
    }

    public ConstraintValidator<T> getElementValidator() {
        return elementValidator;
    }
}
