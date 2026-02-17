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

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * LogEntry 单元测试
 *
 * @author coffee
 */
public class LogEntryTest {

    @Test
    public void testDefaultConstructor() {
        LogEntry entry = new LogEntry();
        assertEquals(0, entry.getTimestamp());
        assertNull(entry.getTraceId());
        assertNull(entry.getCoordinate());
        assertNull(entry.getLevel());
        assertNull(entry.getLogger());
        assertNull(entry.getThread());
        assertNull(entry.getMessage());
        assertNull(entry.getThrowable());
    }

    @Test
    public void testConstructorWithTraceId() {
        long timestamp = System.currentTimeMillis();
        LogEntry entry = new LogEntry(timestamp, "abc123", "test-coord", "DEBUG", "TestLogger", "main", "Test message", null);

        assertEquals(timestamp, entry.getTimestamp());
        assertEquals("abc123", entry.getTraceId());
        assertEquals("test-coord", entry.getCoordinate());
        assertEquals("DEBUG", entry.getLevel());
        assertEquals("TestLogger", entry.getLogger());
        assertEquals("main", entry.getThread());
        assertEquals("Test message", entry.getMessage());
        assertNull(entry.getThrowable());
    }

    @Test
    public void testConstructorWithThrowable() {
        long timestamp = System.currentTimeMillis();
        LogEntry entry = new LogEntry(timestamp, "def456", "core", "ERROR", "ErrorLogger", "thread-1", "Error occurred", "Stack trace here");

        assertEquals(timestamp, entry.getTimestamp());
        assertEquals("def456", entry.getTraceId());
        assertEquals("core", entry.getCoordinate());
        assertEquals("ERROR", entry.getLevel());
        assertEquals("ErrorLogger", entry.getLogger());
        assertEquals("thread-1", entry.getThread());
        assertEquals("Error occurred", entry.getMessage());
        assertEquals("Stack trace here", entry.getThrowable());
    }

    @Test
    public void testSetters() {
        LogEntry entry = new LogEntry();

        entry.setTimestamp(12345L);
        entry.setTraceId("xyz789");
        entry.setCoordinate("integration-1");
        entry.setLevel("INFO");
        entry.setLogger("MyLogger");
        entry.setThread("worker");
        entry.setMessage("Processing data");
        entry.setThrowable("NullPointerException");

        assertEquals(12345L, entry.getTimestamp());
        assertEquals("xyz789", entry.getTraceId());
        assertEquals("integration-1", entry.getCoordinate());
        assertEquals("INFO", entry.getLevel());
        assertEquals("MyLogger", entry.getLogger());
        assertEquals("worker", entry.getThread());
        assertEquals("Processing data", entry.getMessage());
        assertEquals("NullPointerException", entry.getThrowable());
    }

    @Test
    public void testToString() {
        LogEntry entry = new LogEntry(1000L, "trace1", "coord", "DEBUG", "logger", "thread", "msg", "err");
        String str = entry.toString();

        assertTrue(str.contains("timestamp=1000"));
        assertTrue(str.contains("traceId='trace1'"));
        assertTrue(str.contains("coordinate='coord'"));
        assertTrue(str.contains("level='DEBUG'"));
        assertTrue(str.contains("logger='logger'"));
        assertTrue(str.contains("thread='thread'"));
        assertTrue(str.contains("message='msg'"));
        assertTrue(str.contains("throwable='err'"));
    }
}
