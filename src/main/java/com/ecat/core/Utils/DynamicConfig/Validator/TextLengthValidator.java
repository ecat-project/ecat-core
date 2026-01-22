package com.ecat.core.Utils.DynamicConfig.Validator;

import com.ecat.core.Utils.DynamicConfig.ConstraintValidator;

/**
 * 文本长度验证器
 */
public class TextLengthValidator implements ConstraintValidator<String> {

    private final int minLength;
    private final int maxLength;

    public TextLengthValidator(int minLength, int maxLength) {
        this.minLength = minLength;
        this.maxLength = maxLength;
    }

    @Override
    public boolean validate(String value) {
        if (value == null) {
            return minLength == 0; // 如果允许空字符串
        }
        int length = value.length();
        return length >= minLength && length <= maxLength;
    }

    @Override
    public String getErrorMessage() {
        return "文本长度必须在 " + minLength + " 和 " + maxLength + " 个字符之间";
    }

    public int getMinLength() {
        return minLength;
    }

    public int getMaxLength() {
        return maxLength;
    }
}