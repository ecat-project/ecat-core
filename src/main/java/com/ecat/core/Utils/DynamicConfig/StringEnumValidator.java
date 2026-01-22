package com.ecat.core.Utils.DynamicConfig;

import java.util.Set;

public class StringEnumValidator implements ConstraintValidator<String> {
    private final Set<String> validValues;

    public StringEnumValidator(Set<String> validValues) {
        this.validValues = validValues;
    }

    @Override
    public boolean validate(String value) {
        return validValues.contains(value);
    }

    @Override
    public String getErrorMessage() {
        return "字符串值必须是 " + validValues + " 中的一个";
    }

    public Set<String> getValidValues() {
        return validValues;
    }
}
