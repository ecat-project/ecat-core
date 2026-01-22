package com.ecat.core.Utils;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * 自定义 logback 转换器，跳过 Log.java 包装器显示真实调用位置
 *
 * @author coffee
 */
public class CallerDataConverter extends ClassicConverter {

    @Override
    public String convert(ILoggingEvent event) {
        StackTraceElement[] callerDataArray = event.getCallerData();
        if (callerDataArray == null || callerDataArray.length == 0) {
            return "Unknown:0";
        }

        // 跳过 Log.java、LogFactory.java 以及 SLF4J/Logback 框架类，找到真实调用者
        for (StackTraceElement element : callerDataArray) {
            String className = element.getClassName();
            if (!className.equals(Log.class.getName()) &&
                !className.equals(LogFactory.class.getName()) &&
                !className.startsWith("org.slf4j.") &&
                !className.startsWith("ch.qos.logback.")) {
                return element.getFileName() + ":" + element.getLineNumber();
            }
        }

        // 如果没找到，返回第一个有效位置
        StackTraceElement first = callerDataArray[0];
        return first.getFileName() + ":" + first.getLineNumber();
    }
}
