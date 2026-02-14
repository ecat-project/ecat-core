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

    private LogEntry createEntry(long timestamp, String message) {
        return new LogEntry(timestamp, "test", "DEBUG", "TestLogger", "main", message);
    }

    /**
     * 测试用订阅者
     */
    private static class TestSubscriber extends LogSubscriber {
        TestSubscriber() {
            super(new ByteArrayOutputStream());
        }
    }
}
