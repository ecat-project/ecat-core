/*
 * Copyright (c) 2026 ECAT Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
