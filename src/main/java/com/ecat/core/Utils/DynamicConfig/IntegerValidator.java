package com.ecat.core.Utils.DynamicConfig;

/**
 * IntegerValidator 用于验证整数值是否在指定范围内
 * 
 * <p>
 * 主要功能：
 * <ul>
 * <li>验证整数值是否在指定的最小值和最大值之间</li>
 * <li>提供错误消息以便于调试</li>
 * </ul>
 * 
 * @author coffee
 */
public class IntegerValidator  implements ConstraintValidator<Integer> {
    private final int minValue;
    private final int maxValue;

    public IntegerValidator(int minValue, int maxValue) {
        this.minValue = minValue;
        this.maxValue = maxValue;
    }

    @Override
    public boolean validate(Integer value) {
        return value != null && value >= minValue && value <= maxValue;
    }

    @Override
    public String getErrorMessage() {
        return "整数值必须在 " + minValue + " 到 " + maxValue + " 之间";
    }
    
}
