package com.ecat.core.Device;

public enum DeviceAbility {
    
    // gas.switch
    // gas.zero.generate
    // gas.span.generate
    GAS_SWITCH("gas.switch"),
    GAS_ZERO_GENERATE("gas.zero.generate"),
    GAS_SPAN_GENERATE("gas.span.generate"),

    DEFAULT_NULL("default.null"); // 默认的没有任何控制能力
    
    private final String className;   // 参数名称，英文
    DeviceAbility(String className) {
        this.className = className;
    }
    public String getClassName() {
        return className;
    }

    // 根据输入的name获取enum
    public static DeviceAbility getEnum(String className) {
        if (className == null) {
            return DEFAULT_NULL;
        }
        for (DeviceAbility deviceClass : DeviceAbility.values()) {
            if (deviceClass.getClassName().equals(className)) {
                return deviceClass;
            }
        }
        throw new IllegalArgumentException("DeviceAbility not found: " + className);
    }

}
