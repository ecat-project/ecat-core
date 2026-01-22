package com.ecat.core.Utils.DynamicConfig.Validator;

import com.ecat.core.Utils.DynamicConfig.ConstraintValidator;

/**
 * 浮点数范围验证器
 */
public class FloatRangeValidator implements ConstraintValidator<Float> {

    private final float minValue;
    private final float maxValue;

    public FloatRangeValidator(float minValue, float maxValue) {
        this.minValue = minValue;
        this.maxValue = maxValue;
    }

    @Override
    public boolean validate(Float value) {
        if (value == null) {
            return false;
        }
        return value >= minValue && value <= maxValue;
    }

    @Override
    public String getErrorMessage() {
        return "值必须在 " + minValue + " 和 " + maxValue + " 之间";
    }

    public float getMinValue() {
        return minValue;
    }

    public float getMaxValue() {
        return maxValue;
    }
}