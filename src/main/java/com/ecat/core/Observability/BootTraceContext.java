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

package com.ecat.core.Observability;

import com.ecat.core.Utils.Mdc.TraceContext;

import java.util.Map;

/**
 * boot/shutdown 作用域追踪（arch-review 25 号杠杆④）。
 *
 * <p>动机：main 线程承载启动 entry 恢复序列与停机编排日志，此前 traceId 槽为空——
 * 多代启动共用一个日志文件时只能靠时间戳手工切段归因。本类在 {@code EcatCore.init}
 * 起点生成 boot ULID 写入 main 线程 MDC（整段启动序列共用），停机编排段同理用独立
 * shutdown ULID 标注；startup-report 嵌入 boot id（{@link StartupReportHolder}），
 * 按 boot id 一键分离代际。
 *
 * <p>每次 {@code init()} 调用产生新 id（单测/嵌入式多轮 init 各自成代）；id 存 static
 * 供 core-api 启动报告端点读取。
 *
 * @author coffee
 */
public final class BootTraceContext {

    /** 最近一次 boot 的 ULID；null = 本 JVM 尚未经过 EcatCore.init。 */
    private static volatile String bootId;

    private BootTraceContext() {
    }

    /**
     * 开始一代 boot：生成 boot ULID，写入当前线程（main）MDC 并留存 static。
     *
     * @return 本代 boot id
     */
    public static String beginBoot() {
        String id = TraceContext.generateTraceId();
        bootId = id;
        TraceContext.setTraceId(id);
        return id;
    }

    /** 最近一次 boot 的 ULID（本 JVM 生命周期内不变，init 后非空）。 */
    public static String getBootId() {
        return bootId;
    }

    /**
     * 在独立的 shutdown ULID 上下文中执行停机编排：编排段日志全部带该 id，
     * 与启动段（boot id）天然分离；结束后恢复进入前的 MDC。
     *
     * @param shutdown 停机编排动作
     */
    public static void runWithShutdownTrace(Runnable shutdown) {
        Map<String, String> previous = TraceContext.capture();
        try {
            TraceContext.setTraceId(TraceContext.generateTraceId());
            shutdown.run();
        } finally {
            TraceContext.restore(previous);
        }
    }
}
