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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;

import org.junit.Test;

/**
 * ULID 因果链原语测试（14 号架构 §5.4-3）：格式合法、单线程单调（同毫秒严格递增）、
 * 单线程与多线程下全局唯一。字典序单调是「日志按 id 排序即按发生顺序排列」的依据。
 */
public class TraceContextUlidTest {

    private static final String CROCKFORD = "0123456789ABCDEFGHJKMNPQRSTVWXYZ";

    @Test
    public void ulidFormatIs26CharCrockford() {
        for (int i = 0; i < 100; i++) {
            String ulid = TraceContext.generateUlid();
            assertThat("ULID 长度恒 26", ulid.length(), is(26));
            for (char c : ulid.toCharArray()) {
                assertTrue("字符必须在 Crockford Base32 集内: " + ulid,
                        CROCKFORD.indexOf(c) >= 0);
            }
        }
    }

    @Test
    public void ulidIsMonotonicSingleThread() {
        String prev = TraceContext.generateUlid();
        for (int i = 0; i < 10_000; i++) {
            String next = TraceContext.generateUlid();
            assertTrue("字典序必须单调不减（prev=" + prev + " next=" + next + "）",
                    next.compareTo(prev) > 0);
            prev = next;
        }
    }

    @Test
    public void ulidIsUniqueUnderConcurrency() throws InterruptedException {
        int threads = 8;
        int perThread = 5_000;
        Set<String> all = new HashSet<>(threads * perThread * 2);
        CountDownLatch done = new CountDownLatch(threads);
        Object lock = new Object();
        for (int t = 0; t < threads; t++) {
            new Thread(() -> {
                try {
                    for (int i = 0; i < perThread; i++) {
                        String ulid = TraceContext.generateUlid();
                        synchronized (lock) {
                            all.add(ulid);
                        }
                    }
                } finally {
                    done.countDown();
                }
            }).start();
        }
        assertTrue("并发生成应全部完成", done.await(30, java.util.concurrent.TimeUnit.SECONDS));
        assertThat("并发生成的 ULID 必须全局唯一", all.size(), is(threads * perThread));
    }

    @Test
    public void generateTraceIdReturnsUlid() {
        String traceId = TraceContext.generateTraceId();
        assertThat(traceId, notNullValue());
        assertThat(traceId.length(), is(26));
    }
}
