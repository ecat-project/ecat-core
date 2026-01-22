package com.ecat.core.Utils.DynamicConfig;

public class StringLengthValidator implements ConstraintValidator<String> {
    private final int minLength;
    private final int maxLength;

    public StringLengthValidator(int minLength, int maxLength) {
        this.minLength = minLength;
        this.maxLength = maxLength;
    }

    @Override
    public boolean validate(String value) {
        return value.length() >= minLength && value.length() <= maxLength;
    }

    @Override
    public String getErrorMessage() {
        return "字符串长度必须在 " + minLength + " 到 " + maxLength + " 之间";
    }
}