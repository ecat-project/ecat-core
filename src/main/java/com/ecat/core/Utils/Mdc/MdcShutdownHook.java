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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * MDC 关闭钩子工具
 *
 * <p>注册 JVM 关闭钩子，确保在关闭时使用正确的 MDC 上下文。
 * 
 * @author coffee
 */
public final class MdcShutdownHook {
    private static final AtomicBoolean registered = new AtomicBoolean(false);

    private MdcShutdownHook() {
    }

    /**
     * 注册关闭钩子
     *
     * @param runnable 关闭时执行的任务
     * @param context MDC 上下文
     * @return 是否注册成功（已注册时返回 false）
     */
    public static boolean register(Runnable runnable, Map<String, String> context) {
        if (runnable == null) {
            return false;
        }
        if (!registered.compareAndSet(false, true)) {
            return false;
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            Map<String, String> previousContext = MDC.getCopyOfContextMap();
            try {
                if (context != null) {
                    for (Map.Entry<String, String> entry : context.entrySet()) {
                        MDC.put(entry.getKey(), entry.getValue());
                    }
                }
                runnable.run();
            } finally {
                MDC.clear();
                if (previousContext != null) {
                    for (Map.Entry<String, String> entry : previousContext.entrySet()) {
                        MDC.put(entry.getKey(), entry.getValue());
                    }
                }
            }
        }, "MdcShutdownHook"));
        return true;
    }

    /**
     * 使用当前 MDC 上下文注册关闭钩子
     *
     * @param runnable 关闭时执行的任务
     * @return 是否注册成功
     */
    public static boolean registerWithCurrentContext(Runnable runnable) {
        return register(runnable, MDC.getCopyOfContextMap());
    }

    /**
     * 检查是否已注册
     *
     * @return 是否已注册
     */
    public static boolean isRegistered() {
        return registered.get();
    }

    /**
     * 重置注册状态（仅用于测试）
     */
    public static void resetForTest() {
        registered.set(false);
    }
}
