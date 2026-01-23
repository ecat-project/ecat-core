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
 * LogFactory is a factory class responsible for creating Log instances.
 * It implements the Singleton pattern to ensure only one instance of LogFactory exists.
 * 
 * @author coffee
 */
public class LogFactory {
    /**
     * The single instance of LogFactory, created eagerly.
     * This is a static final field, ensuring it's thread-safe and immutable.
     */
    private static final LogFactory INSTANCE = new LogFactory();

    /**
     * Private constructor to prevent external instantiation of this class.
     * This ensures the Singleton pattern is maintained by controlling object creation.
     */
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
