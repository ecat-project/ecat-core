package com.ecat.core.Utils.DynamicConfig.Validator;

import com.ecat.core.Utils.DynamicConfig.ConstraintValidator;

/**
 * 数值范围验证器
 */
public class NumericRangeValidator implements ConstraintValidator<Double> {

    private final double minValue;
    private final double maxValue;

    public NumericRangeValidator(double minValue, double maxValue) {
        this.minValue = minValue;
        this.maxValue = maxValue;
    }

    @Override
    public boolean validate(Double value) {
        if (value == null) {
            return false;
        }
        return value >= minValue && value <= maxValue;
    }

    @Override
    public String getErrorMessage() {
        return "值必须在 " + minValue + " 和 " + maxValue + " 之间";
    }

    public double getMinValue() {
        return minValue;
    }

    public double getMaxValue() {
        return maxValue;
    }
}