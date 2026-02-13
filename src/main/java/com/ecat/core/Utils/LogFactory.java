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

/**
 * Log 工厂类
 *
 * <p>创建 Log 实例，支持自动坐标检测。
 *
 * <p>使用示例：
 * <pre>
 * Log log = LogFactory.getLogger(MyClass.class);
 * log.info("Hello, World!");
 * </pre>
 */
public class LogFactory {
    private static final LogFactory INSTANCE = new LogFactory();

    private LogFactory() {
    }

    /**
     * 获取工厂实例
     *
     * @return 工厂实例
     */
    public static LogFactory getInstance() {
        return INSTANCE;
    }

    /**
     * 获取日志器（按类）
     *
     * <p>自动检测类的坐标。
     *
     * @param clazz 类
     * @return 日志器
     */
    public static Log getLogger(Class<?> clazz) {
        return new Log(clazz);
    }

    /**
     * 获取日志器（按名称）
     *
     * @param name 日志器名称
     * @return 日志器
     */
    public static Log getLogger(String name) {
        return new Log(name);
    }

    /**
     * 获取日志器（按名称+类）
     *
     * <p>使用指定的名称，但坐标检测使用类。
     *
     * @param name 日志器名称
     * @param clazz 类（用于坐标检测）
     * @return 日志器
     */
    public static Log getLogger(String name, Class<?> clazz) {
        return new Log(name, clazz);
    }
}
