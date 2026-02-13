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

package com.ecat.core.Utils.Mdc;

import org.slf4j.MDC;

import java.util.Map;
import java.util.function.Function;

/**
 * MDC 上下文管理工具
 *
 * <p>提供 MDC 上下文的捕获、恢复和执行功能，用于在异步任务中传递上下文。
 * 
 * @author coffee
 */
public final class MdcContext {
    public static final String INTEGRATION_COORDINATE_KEY = "integration.coordinate";

    private MdcContext() {
    }

    /**
     * 捕获当前 MDC 上下文
     *
     * @return MDC 上下文副本
     */
    public static Map<String, String> capture() {
        return MDC.getCopyOfContextMap();
    }

    /**
     * 创建上下文恢复器
     *
     * @param capturedContext 要恢复的上下文
     * @return 上下文恢复器（AutoCloseable）
     */
    public static RestoreContext restore(Map<String, String> capturedContext) {
        return new RestoreContext(capturedContext);
    }

    /**
     * 创建空上下文恢复器
     *
     * @return 上下文恢复器（AutoCloseable）
     */
    public static RestoreContext restore() {
        return new RestoreContext(null);
    }

    /**
     * 包装 Function 使其在执行时使用捕获的上下文
     *
     * @param action 要包装的 Function
     * @return 包装后的 Function
     */
    public static <T, R> Function<T, R> withContext(Function<T, R> action) {
        Map<String, String> context = capture();
        return input -> {
            Map<String, String> originalContext = MDC.getCopyOfContextMap();
            try {
                if (context != null) {
                    for (Map.Entry<String, String> entry : context.entrySet()) {
                        MDC.put(entry.getKey(), entry.getValue());
                    }
                }
                return action.apply(input);
            } finally {
                MDC.clear();
                if (originalContext != null) {
                    for (Map.Entry<String, String> entry : originalContext.entrySet()) {
                        MDC.put(entry.getKey(), entry.getValue());
                    }
                }
            }
        };
    }

    /**
     * 在指定上下文中执行 Runnable
     *
     * @param capturedContext 上下文
     * @param runnable 要执行的任务
     */
    public static void runWithContext(Map<String, String> capturedContext, Runnable runnable) {
        Map<String, String> originalContext = MDC.getCopyOfContextMap();
        try {
            if (capturedContext != null) {
                for (Map.Entry<String, String> entry : capturedContext.entrySet()) {
                    MDC.put(entry.getKey(), entry.getValue());
                }
            }
            runnable.run();
        } finally {
            MDC.clear();
            if (originalContext != null) {
                for (Map.Entry<String, String> entry : originalContext.entrySet()) {
                    MDC.put(entry.getKey(), entry.getValue());
                }
            }
        }
    }

    /**
     * 设置集成坐标
     *
     * @param coordinate 坐标
     */
    public static void setCoordinate(String coordinate) {
        if (coordinate != null) {
            MDC.put(INTEGRATION_COORDINATE_KEY, coordinate);
        }
    }

    /**
     * 清除集成坐标
     */
    public static void clearCoordinate() {
        MDC.remove(INTEGRATION_COORDINATE_KEY);
    }

    /**
     * 获取当前集成坐标
     *
     * @return 坐标
     */
    public static String getCoordinate() {
        return MDC.get(INTEGRATION_COORDINATE_KEY);
    }

    /**
     * 上下文恢复器（AutoCloseable）
     */
    public static final class RestoreContext implements AutoCloseable {
        private final Map<String, String> previousContext = MDC.getCopyOfContextMap();
        private final boolean shouldRestore;

        private RestoreContext(Map<String, String> newContext) {
            shouldRestore = newContext != null;
            if (shouldRestore && newContext != null) {
                for (Map.Entry<String, String> entry : newContext.entrySet()) {
                    MDC.put(entry.getKey(), entry.getValue());
                }
            }
        }

        @Override
        public void close() {
            if (shouldRestore) {
                MDC.clear();
            }
            if (previousContext != null) {
                for (Map.Entry<String, String> entry : previousContext.entrySet()) {
                    MDC.put(entry.getKey(), entry.getValue());
                }
            }
        }
    }
}
