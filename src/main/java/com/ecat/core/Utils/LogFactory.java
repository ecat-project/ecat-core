package com.ecat.core.Utils;

public class LogFactory {
    private static final LogFactory INSTANCE = new LogFactory();

    private LogFactory() {
        // 私有构造函数，防止外部实例化
    }

    public static LogFactory getInstance() {
        return INSTANCE;
    }

    public static Log getLogger(Class<?> clazz) {
        return new Log(clazz.getName());
    }

    public static Log getLogger(String name) {
        return new Log(name);
    }
}