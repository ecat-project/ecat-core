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
 * MDC 上下文传递工具
 * <p>
 * 解决 CompletableFuture 异步执行中的 MDC 上下文丢失问题。
 * 在异步操作前保存上下文，在异步线程中恢复上下文。
 * </p>
 *
 * <h3>使用方式：</h3>
 * <pre>{@code
 * // 方式一：使用 try-with-resources 自动恢复
 * Map<String, String> context = MdcContext.capture();
 * CompletableFuture.supplyAsync(() -> {
 *     try (MdcContext.RestoreContext restore = MdcContext.restore(context)) {
 *         log.info("这条日志会携带正确的上下文");
 *         return doWork();
 *     }
 * }, executor);
 *
 * // 方式二：使用便捷方法
 * Map<String, String> context = MdcContext.capture();
 * CompletableFuture.supplyAsync(task)
 *     .thenApply(MdcContext.withContext(result -> {
 *         log.info("自动恢复上下文");
 *         return process(result);
 *     }));
 *
 * // 方式三：使用 runWithContext
 * Map<String, String> context = MdcContext.capture();
 * executor.execute(() -> {
 *     MdcContext.runWithContext(context, () -> {
 *         log.info("在 Runnable 中自动恢复上下文");
 *     });
 * });
 * }</pre>
 *
 * @author coffee
 */
public final class MdcContext {
    /** MDC 中存储插件坐标的键名 */
    public static final String INTEGRATION_COORDINATE_KEY = "integration.coordinate";

    /**
     * 私有构造函数，防止实例化
     */
    private MdcContext() {
    }

    /**
     * 保存当前线程的 MDC 上下文快照
     *
     * @return MDC 上下文副本，如果没有上下文则返回 null
     */
    public static Map<String, String> capture() {
        return MDC.getCopyOfContextMap();
    }

    /**
     * 恢复指定的 MDC 上下文到当前线程
     * <p>
     * 返回的 RestoreContext 对象实现了 AutoCloseable，
     * 建议使用 try-with-resources 确保上下文正确清理
     * </p>
     *
     * @param capturedContext 之前保存的上下文，可以为 null
     * @return RestoreContext 对象，用于 try-with-resources
     */
    public static RestoreContext restore(Map<String, String> capturedContext) {
        return new RestoreContext(capturedContext);
    }

    /**
     * 恢复上下文（使用之前 capture 的结果）
     *
     * @return RestoreContext 对象
     */
    public static RestoreContext restore() {
        return new RestoreContext(null);
    }

    /**
     * 在回调中恢复上下文的便捷方法（Function 版本）
     * <p>
     * 包装一个 Function，在执行前恢复 MDC 上下文，
     * 执行后恢复原始上下文
     * </p>
     *
     * @param <T> 输入类型
     * @param <R> 返回类型
     * @param action 要执行的函数
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
     * 在回调中恢复上下文的便捷方法（Runnable 版本）
     * <p>
     * 包装一个 Runnable，在执行前恢复 MDC 上下文，
     * 执行后恢复原始上下文
     * </p>
     *
     * @param capturedContext 之前保存的上下文
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
     * 设置当前插件的坐标到 MDC
     * <p>
     * 这是一个便捷方法，等同于：
     * <pre>MDC.put("integration.coordinate", coordinate);</pre>
     * </p>
     *
     * @param coordinate 插件坐标
     */
    public static void setCoordinate(String coordinate) {
        if (coordinate != null) {
            MDC.put(INTEGRATION_COORDINATE_KEY, coordinate);
        }
    }

    /**
     * 从 MDC 中清除插件坐标
     */
    public static void clearCoordinate() {
        MDC.remove(INTEGRATION_COORDINATE_KEY);
    }

    /**
     * 获取当前 MDC 中的插件坐标
     *
     * @return 插件坐标，如果未设置则返回 null
     */
    public static String getCoordinate() {
        return MDC.get(INTEGRATION_COORDINATE_KEY);
    }

    /**
     * RestoreContext 类，实现 AutoCloseable 接口
     * <p>
     * 用于 try-with-resources 语法，确保上下文正确恢复和清理
     * </p>
     */
    public static final class RestoreContext implements AutoCloseable {

        /** 恢复前的原始上下文 */
        private final Map<String, String> previousContext = MDC.getCopyOfContextMap();

        /** 是否需要恢复（是否设置了新上下文） */
        private final boolean shouldRestore;

        /**
         * 私有构造函数
         *
         * @param newContext 要设置的新上下文，可以为 null
         */
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
