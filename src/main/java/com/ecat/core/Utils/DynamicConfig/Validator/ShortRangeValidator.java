package com.ecat.core.Utils.DynamicConfig.Validator;

import com.ecat.core.Utils.DynamicConfig.ConstraintValidator;

/**
 * 短整型范围验证器
 */
public class ShortRangeValidator implements ConstraintValidator<Short> {

    private final short minValue;
    private final short maxValue;

    public ShortRangeValidator(short minValue, short maxValue) {
        this.minValue = minValue;
        this.maxValue = maxValue;
    }

    @Override
    public boolean validate(Short value) {
        if (value == null) {
            return false;
        }
        return value >= minValue && value <= maxValue;
    }

    @Override
    public String getErrorMessage() {
        return "值必须在 " + minValue + " 和 " + maxValue + " 之间";
    }

    public short getMinValue() {
        return minValue;
    }

    public short getMaxValue() {
        return maxValue;
    }
}