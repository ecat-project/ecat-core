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

package com.ecat.core.CommTrace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.time.Instant;

import org.junit.Test;

/**
 * renderIsoTime 输出契约（UI 设计稿 §3.2/§7 硬前置）：合法 ISO-8601、固定毫秒三位、
 * UTC Z 后缀、Z 之后零字符——前端 {@code new Date} 直接解析。
 *
 * <p>锁死的历史缺陷形态：微秒尾直接拼在 Z 后（{@code ...057Z437}，非法 ISO-8601，
 * new Date 得 Invalid）；以及 Instant.toString 毫秒为零时掉小数段导致追加位错位。
 */
public class CommTraceEventTest {

    private static CommTraceEvent event(long tsMicros) {
        return new CommTraceEvent(1L, tsMicros, CommTraceTransport.SERIAL, "/dev/ttyUSB0",
                null, null, null, CommTraceDirection.TX, new byte[]{1}, 1, null, null);
    }

    @Test
    public void renderIsoTimeIsLegalIsoWithFoldedMillis() {
        // 1_760_000_000_123_437µs = 2025-10-09T08:53:20.123437Z：微秒 437 丢弃（折叠进毫秒）
        String iso = event(1_760_000_000_123_437L).renderIsoTime();
        assertEquals("固定毫秒三位 + Z 后缀", "2025-10-09T08:53:20.123Z", iso);
        assertEquals("Z 之后不得有任何字符", "Z", iso.substring(iso.length() - 1));
        // 标准 ISO 解析必须成功（前端 new Date 同解析器的 Java 侧等价断言）
        Instant parsed = Instant.parse(iso);
        assertEquals(1_760_000_000_123L, parsed.toEpochMilli());
    }

    @Test
    public void renderIsoTimeKeepsThreeDigitMillisWhenZero() {
        // 毫秒为 0、微秒非 0：小数段固定三位 000（不得随 Instant.toString 掉段变 "...T20Z999" 形态）
        String iso = event(1_760_000_000_000_999L).renderIsoTime();
        assertEquals("毫秒零也保留三位小数段", "2025-10-09T08:53:20.000Z", iso);
        assertTrue("合法 ISO（Instant.parse 不抛即合法）", Instant.parse(iso) != null);
    }
}
