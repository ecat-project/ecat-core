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

package com.ecat.core.Log;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

/**
 * LogBuffer 单元测试
 * 
 * @author coffee
 */
public class LogBufferTest {

    private LogBuffer buffer;

    @Before
    public void setUp() {
        buffer = new LogBuffer(10);
    }

    @After
    public void tearDown() {
        if (buffer != null) {
            buffer.close();
        }
    }

    @Test
    public void testPutAndGetAll() {
        LogEntry entry1 = createEntry(1, "msg1");
        LogEntry entry2 = createEntry(2, "msg2");

        buffer.put(entry1);
        buffer.put(entry2);

        List<LogEntry> all = buffer.getAll();
        assertEquals(2, all.size());
        assertEquals("msg1", all.get(0).getMessage());
        assertEquals("msg2", all.get(1).getMessage());
    }

    @Test
    public void testCapacityLimit() {
        // 添加15条日志到容量为10的缓冲区
        for (int i = 1; i <= 15; i++) {
            buffer.put(createEntry(i, "msg" + i));
        }

        // 应该只保留最新的10条
        assertEquals(10, buffer.size());

        List<LogEntry> all = buffer.getAll();
        assertEquals("msg6", all.get(0).getMessage());  // 最旧的是 msg6
        assertEquals("msg15", all.get(9).getMessage()); // 最新的是 msg15
    }

    @Test
    public void testGetRecent() {
        for (int i = 1; i <= 10; i++) {
            buffer.put(createEntry(i, "msg" + i));
        }

        List<LogEntry> recent = buffer.getRecent(5);
        assertEquals(5, recent.size());
        assertEquals("msg6", recent.get(0).getMessage());
        assertEquals("msg10", recent.get(4).getMessage());

        // limit 超过实际数量时返回全部
        List<LogEntry> all = buffer.getRecent(20);
        assertEquals(10, all.size());
    }

    @Test
    public void testClear() {
        buffer.put(createEntry(1, "msg1"));
        buffer.put(createEntry(2, "msg2"));

        assertEquals(2, buffer.size());
        buffer.clear();
        assertEquals(0, buffer.size());
    }

    @Test
    public void testSubscribeUnsubscribe() {
        assertEquals(0, buffer.getSubscriberCount());

        TestSubscriber sub1 = new TestSubscriber();
        TestSubscriber sub2 = new TestSubscriber();

        buffer.subscribe(sub1);
        assertEquals(1, buffer.getSubscriberCount());

        buffer.subscribe(sub2);
        assertEquals(2, buffer.getSubscriberCount());

        // 重复订阅不增加计数
        buffer.subscribe(sub1);
        assertEquals(2, buffer.getSubscriberCount());

        buffer.unsubscribe(sub1);
        assertEquals(1, buffer.getSubscriberCount());

        buffer.unsubscribe(sub2);
        assertEquals(0, buffer.getSubscriberCount());
    }

    @Test
    public void testClose() {
        buffer.put(createEntry(1, "msg1"));
        buffer.close();

        // 关闭后再 put 应该无效
        buffer.put(createEntry(2, "msg2"));
        assertEquals(1, buffer.size());
    }

    @Test
    public void testSubscribeNull() {
        buffer.subscribe(null);
        assertEquals(0, buffer.getSubscriberCount());
    }

    // ========== 内存泄漏相关测试 ==========

    @Test
    public void testNoMemoryLeakWithoutSubscribers() {
        // 模拟 env-data-handle 场景：无 SSE 订阅者，持续写入大量日志
        // 验证 buffer 大小始终不超过容量，不会无限增长
        for (int i = 1; i <= 10000; i++) {
            buffer.put(createEntry(i, "data-point-" + i + " value:0.123 unit:ug/m3"));
        }
        assertEquals("buffer 应严格限制在容量内", 10, buffer.size());

        List<LogEntry> all = buffer.getAll();
        assertEquals("data-point-9991 value:0.123 unit:ug/m3", all.get(0).getMessage());
        assertEquals("data-point-10000 value:0.123 unit:ug/m3", all.get(all.size() - 1).getMessage());
    }

    @Test
    public void testNoMemoryLeakWithSubscriber() {
        // 有订阅者时同样不应泄漏
        TestSubscriber sub = new TestSubscriber();
        buffer.subscribe(sub);

        long now = System.currentTimeMillis();
        for (int i = 1; i <= 10000; i++) {
            buffer.put(createEntry(now + i, "data-point-" + i));
        }

        assertEquals("buffer 仍应限制在容量内", 10, buffer.size());
        assertTrue("订阅者应收到日志推送", sub.receivedCount >= 10);

        buffer.unsubscribe(sub);
    }

    @Test
    public void testEvictedEntriesAreGcEligible() {
        // 验证被淘汰的 LogEntry 可以被 GC 回收（单引用保证）
        List<WeakReference<LogEntry>> weakRefs = new ArrayList<>();

        // 填满 buffer，保留弱引用
        for (int i = 1; i <= 10; i++) {
            LogEntry entry = createEntry(i, "msg" + i);
            weakRefs.add(new WeakReference<>(entry));
            buffer.put(entry);
        }

        // 再写入 10 条，前 10 条被淘汰
        for (int i = 11; i <= 20; i++) {
            buffer.put(createEntry(i, "msg" + i));
        }

        // 手动触发 GC 并等待
        System.gc();
        System.runFinalization();
        try { Thread.sleep(100); } catch (InterruptedException ignored) {}

        // 被淘汰的前 10 条应该可被 GC（弱引用应被清除）
        // 注意：WeakReference 清除不保证立即发生，但大多数 JVM 会立即清除
        int gcCleared = 0;
        for (int i = 0; i < 10; i++) {
            if (weakRefs.get(i).get() == null) {
                gcCleared++;
            }
        }
        assertTrue("被淘汰的条目应可被 GC（清除 " + gcCleared + "/10）", gcCleared > 7);
    }

    @Test
    public void testNoBroadcastQueueField() {
        // 验证重构后不存在 broadcastQueue 字段（双引用已消除）
        try {
            java.lang.reflect.Field broadcastQueue = LogBuffer.class.getDeclaredField("broadcastQueue");
            fail("broadcastQueue 字段应已被删除");
        } catch (NoSuchFieldException e) {
            // 预期行为：字段不存在
        }

        try {
            java.lang.reflect.Field broadcastScheduler = LogBuffer.class.getDeclaredField("broadcastScheduler");
            fail("broadcastScheduler 字段应已被删除");
        } catch (NoSuchFieldException e) {
            // 预期行为：字段不存在
        }
    }

    @Test
    public void testDefaultBufferSizeIs200() {
        // 验证 LogManager 默认缓冲区大小为 200
        assertEquals(200, com.ecat.core.Log.LogManager.getInstance().getBufferSize());
    }

    @Test
    public void testMemoryStaysBoundedUnderSustainedLoad() {
        // 模拟真实场景：每秒 120 条日志（env-data-handle 实际速率）
        // 持续写入 5000 条（约 40 秒的数据量），验证内存始终有界
        int capacity = 200;
        LogBuffer bigBuffer = new LogBuffer(capacity);

        try {
            for (int i = 1; i <= 5000; i++) {
                LogEntry entry = new LogEntry(
                    System.currentTimeMillis(), "trace-" + i,
                    "com.ecat:integration-env-data-handle", "DEBUG",
                    "DataHandleConsumer", "pool-1-thread-1",
                    "Event received: DEVICE_DATA_UPDATE Device:PM-001 attrDisplatName:PM2.5 attrClass:数值, DisplayValue:35.2, DisplayUnit: ug/m3",
                    null
                );
                bigBuffer.put(entry);
            }

            assertEquals("持续高负载后 buffer 大小应为 200", 200, bigBuffer.size());
            assertEquals("getRecent(50) 应返回 50 条", 50, bigBuffer.getRecent(50).size());
            assertEquals("getRecent(300) 应截断为 200", 200, bigBuffer.getRecent(300).size());
        } finally {
            bigBuffer.close();
        }
    }

    private LogEntry createEntry(long timestamp, String message) {
        return new LogEntry(timestamp, "trace1", "test", "DEBUG", "TestLogger", "main", message, null);
    }

    /**
     * 测试用订阅者（支持统计接收数量）
     */
    private static class TestSubscriber extends LogSubscriber {
        private int receivedCount = 0;

        TestSubscriber() {
            super(new ByteArrayOutputStream());
        }

        @Override
        public void send(LogEntry entry) throws java.io.IOException {
            receivedCount++;
            super.send(entry);
        }

        int getReceivedCount() {
            return receivedCount;
        }
    }
}
