package com.ecat.core.Utils.DynamicConfig.Validator;

import com.ecat.core.Utils.DynamicConfig.ConstraintValidator;

/**
 * 布尔值验证器
 */
public class BooleanValidator implements ConstraintValidator<Boolean> {

    @Override
    public boolean validate(Boolean value) {
        return value != null; // 布尔值只要不是null都有效
    }

    @Override
    public String getErrorMessage() {
        return "值必须为布尔类型";
    }
}