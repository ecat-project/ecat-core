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

import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.*;

/**
 * boot/shutdown 作用域追踪（arch-review 25 号杠杆④）契约：
 * beginBoot 写 main 线程 MDC + static；多轮 init 各自成代；停机段独立 id 且结束恢复。
 *
 * @author coffee
 */
public class BootTraceContextTest {

    @Test
    public void beginBoot_setsMdcAndStaticBootId() {
        Map<String, String> previous = TraceContext.capture();
        try {
            String bootId = BootTraceContext.beginBoot();
            assertEquals(26, bootId.length());
            assertEquals("boot id 应写入当前线程 MDC（main 线程启动序列共用）",
                    bootId, TraceContext.getTraceId());
            assertEquals("static 留存供端点读取", bootId, BootTraceContext.getBootId());
        } finally {
            TraceContext.restore(previous);
        }
    }

    @Test
    public void beginBoot_eachCallNewGeneration() {
        String first = BootTraceContext.beginBoot();
        String second = BootTraceContext.beginBoot();
        assertNotEquals("多轮 init 各自成代", first, second);
        assertEquals(second, BootTraceContext.getBootId());
        TraceContext.clearTraceId();
    }

    @Test
    public void runWithShutdownTrace_setsDistinctIdAndRestores() {
        Map<String, String> previous = TraceContext.capture();
        try {
            String bootId = BootTraceContext.beginBoot();
            final String[] inScope = new String[1];
            BootTraceContext.runWithShutdownTrace(() -> {
                inScope[0] = TraceContext.getTraceId();
            });
            assertNotNull("停机编排段内应有 traceId", inScope[0]);
            assertNotEquals("shutdown id 与 boot id 分离（代际可区分）", bootId, inScope[0]);
            assertEquals("结束后恢复进入前 MDC（boot id 回到当前线程）",
                    bootId, TraceContext.getTraceId());
        } finally {
            TraceContext.restore(previous);
        }
    }
}
