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
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * 追踪上下文，管理 Trace ID 和 MDC 上下文的传播
 *
 * <p>提供跨线程、跨组件的追踪能力，用于日志链路追踪。
 *
 * <p>核心功能：
 * <ul>
 *   <li>生成和管理 Trace ID（8字符短格式）</li>
 *   <li>捕获和恢复 MDC 上下文</li>
 *   <li>包装 Runnable/Callable 以自动传播上下文</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>
 * // 在业务入口生成 Trace ID
 * TraceContext.getOrCreateTraceId();
 *
 * // 在异步任务中包装
 * executor.submit(TraceContext.wrapRunnable(() -> doWork()));
 *
 * // 获取当前 Trace ID
 * String traceId = TraceContext.getTraceId();
 * </pre>
 *
 * @author coffee
 */
public final class TraceContext {
    /**
     * MDC 中 Trace ID 的键名
     */
    public static final String TRACE_ID_KEY = "traceId";

    /**
     * Trace ID 长度（8字符）
     */
    private static final int TRACE_ID_LENGTH = 8;

    private TraceContext() {
    }

    /**
     * 生成新的 Trace ID（8字符短格式）
     *
     * <p>使用 UUID 的前8个字符作为 Trace ID，便于日志阅读和搜索。
     *
     * @return 新的 Trace ID
     */
    public static String generateTraceId() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, TRACE_ID_LENGTH);
    }

    /**
     * 获取当前 Trace ID
     *
     * @return Trace ID，如果不存在返回 null
     */
    public static String getTraceId() {
        return MDC.get(TRACE_ID_KEY);
    }

    /**
     * 获取当前 Trace ID，如果不存在则生成一个新的
     *
     * <p>通常在业务入口调用，确保整个调用链有统一的 Trace ID。
     *
     * @return Trace ID
     */
    public static String getOrCreateTraceId() {
        String traceId = MDC.get(TRACE_ID_KEY);
        if (traceId == null || traceId.isEmpty()) {
            traceId = generateTraceId();
            MDC.put(TRACE_ID_KEY, traceId);
        }
        return traceId;
    }

    /**
     * 设置 Trace ID
     *
     * @param traceId Trace ID
     */
    public static void setTraceId(String traceId) {
        if (traceId != null && !traceId.isEmpty()) {
            MDC.put(TRACE_ID_KEY, traceId);
        }
    }

    /**
     * 清除 Trace ID
     */
    public static void clearTraceId() {
        MDC.remove(TRACE_ID_KEY);
    }

    /**
     * 捕获当前 MDC 上下文（包括 Trace ID 和其他所有 MDC 值）
     *
     * @return MDC 上下文副本
     */
    public static Map<String, String> capture() {
        return MDC.getCopyOfContextMap();
    }

    /**
     * 恢复 MDC 上下文
     *
     * @param context 要恢复的上下文
     */
    public static void restore(Map<String, String> context) {
        MDC.clear();
        if (context != null) {
            context.forEach(MDC::put);
        }
    }

    /**
     * 包装 Runnable，使其在执行时使用捕获的上下文
     *
     * <p>上下文包括 Trace ID、coordinate 等所有 MDC 值。
     * 如果当前没有 Trace ID，会自动生成一个。
     *
     * @param task 原始任务
     * @return 包装后的任务
     */
    public static Runnable wrapRunnable(Runnable task) {
        Map<String, String> context = capture();
        // 确保有 Trace ID
        if (context == null || !context.containsKey(TRACE_ID_KEY)) {
            String traceId = generateTraceId();
            if (context != null) {
                context.put(TRACE_ID_KEY, traceId);
            }
        }
        return wrapRunnable(task, context);
    }

    /**
     * 包装 Runnable，使其在执行时使用指定的上下文
     *
     * <p>如果上下文中没有 Trace ID，会自动生成一个。
     *
     * @param task 原始任务
     * @param context MDC 上下文
     * @return 包装后的任务
     */
    public static Runnable wrapRunnable(Runnable task, Map<String, String> context) {
        return () -> {
            Map<String, String> previousContext = capture();
            try {
                restore(context);
                // 确保 Trace ID 存在，如果不存在则生成一个
                getOrCreateTraceId();
                task.run();
            } finally {
                restore(previousContext);
            }
        };
    }

    /**
     * 包装 Callable，使其在执行时使用捕获的上下文
     *
     * <p>上下文包括 Trace ID、coordinate 等所有 MDC 值。
     * 如果当前没有 Trace ID，会自动生成一个。
     *
     * @param task 原始任务
     * @return 包装后的任务
     */
    public static <V> Callable<V> wrapCallable(Callable<V> task) {
        Map<String, String> context = capture();
        // 确保有 Trace ID
        if (context == null || !context.containsKey(TRACE_ID_KEY)) {
            String traceId = generateTraceId();
            if (context != null) {
                context.put(TRACE_ID_KEY, traceId);
            }
        }
        return wrapCallable(task, context);
    }

    /**
     * 包装 Callable，使其在执行时使用指定的上下文
     *
     * <p>如果上下文中没有 Trace ID，会自动生成一个。
     *
     * @param task 原始任务
     * @param context MDC 上下文
     * @return 包装后的任务
     */
    public static <V> Callable<V> wrapCallable(Callable<V> task, Map<String, String> context) {
        return () -> {
            Map<String, String> previousContext = capture();
            try {
                restore(context);
                // 确保 Trace ID 存在，如果不存在则生成一个
                getOrCreateTraceId();
                return task.call();
            } finally {
                restore(previousContext);
            }
        };
    }

    /**
     * 在新 Trace ID 上下文中执行任务
     *
     * <p>适用于需要独立追踪的业务操作。
     *
     * @param task 要执行的任务
     */
    public static void runWithNewTraceId(Runnable task) {
        Map<String, String> previousContext = capture();
        try {
            String newTraceId = generateTraceId();
            MDC.put(TRACE_ID_KEY, newTraceId);
            task.run();
        } finally {
            restore(previousContext);
        }
    }

    /**
     * 在指定 Trace ID 上下文中执行任务
     *
     * @param traceId Trace ID
     * @param task 要执行的任务
     */
    public static void runWithTraceId(String traceId, Runnable task) {
        Map<String, String> previousContext = capture();
        try {
            MDC.put(TRACE_ID_KEY, traceId);
            task.run();
        } finally {
            restore(previousContext);
        }
    }
}
