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

import com.ecat.core.Integration.IntegrationInfo;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * 日志管理器单例
 *
 * <p>管理所有集成的日志缓冲区，提供日志广播和历史查询功能。
 *
 * <p>功能：
 * <ul>
 *   <li>注册/注销集成的日志缓冲区</li>
 *   <li>广播日志条目到对应的缓冲区</li>
 *   <li>查询历史日志</li>
 * </ul>
 *
 * @author coffee
 */
public class LogManager {
    private static final LogManager INSTANCE = new LogManager();
    private final ConcurrentMap<String, LogBuffer> buffers = new ConcurrentHashMap<>();
    private static final int DEFAULT_BUFFER_SIZE = 1000;
    private final int bufferSize;

    private LogManager() {
        this.bufferSize = DEFAULT_BUFFER_SIZE;
    }

    public static LogManager getInstance() {
        return INSTANCE;
    }

    /**
     * 注册集成日志缓冲区
     *
     * @param coordinate 集成坐标
     * @param info 集成信息（可空）
     */
    public void registerIntegration(String coordinate, IntegrationInfo info) {
        if (coordinate == null || coordinate.isEmpty()) {
            return;
        }
        buffers.computeIfAbsent(coordinate, k -> new LogBuffer(bufferSize));
    }

    /**
     * 注销集成日志缓冲区
     *
     * @param coordinate 集成坐标
     */
    public void unregisterIntegration(String coordinate) {
        if (coordinate == null || coordinate.isEmpty()) {
            return;
        }
        LogBuffer buffer = buffers.remove(coordinate);
        if (buffer != null) {
            buffer.close();
        }
    }

    /**
     * 获取指定集成的日志缓冲区
     *
     * @param coordinate 集成坐标
     * @return 日志缓冲区，不存在则返回 null
     */
    public LogBuffer getBuffer(String coordinate) {
        return buffers.get(coordinate);
    }

    /**
     * 检查指定集成是否有日志缓冲区
     *
     * @param coordinate 集成坐标
     * @return 是否存在缓冲区
     */
    public boolean hasBuffer(String coordinate) {
        return buffers.containsKey(coordinate);
    }

    /**
     * 广播日志条目到对应的缓冲区
     *
     * @param entry 日志条目
     */
    public void broadcast(LogEntry entry) {
        if (entry == null || entry.getCoordinate() == null) {
            return;
        }
        LogBuffer buffer = buffers.get(entry.getCoordinate());
        if (buffer != null) {
            buffer.put(entry);
        }
    }

    /**
     * 获取指定集成的历史日志
     *
     * @param coordinate 集成坐标
     * @param limit 最大数量
     * @return 日志列表
     */
    public List<LogEntry> getHistory(String coordinate, int limit) {
        LogBuffer buffer = buffers.get(coordinate);
        if (buffer == null) {
            return Collections.emptyList();
        }
        return buffer.getRecent(limit);
    }

    /**
     * 获取所有已注册的集成坐标
     *
     * @return 坐标集合
     */
    public Set<String> getRegisteredCoordinates() {
        return Collections.unmodifiableSet(buffers.keySet());
    }

    /**
     * 获取缓冲区大小
     *
     * @return 缓冲区大小
     */
    public int getBufferSize() {
        return bufferSize;
    }

    /**
     * 清除所有缓冲区（仅用于测试）
     */
    public void clearAllForTest() {
        for (LogBuffer buffer : buffers.values()) {
            buffer.close();
        }
        buffers.clear();
    }

    /**
     * 获取总日志数量
     *
     * @return 总数量
     */
    public int getTotalLogCount() {
        int total = 0;
        for (LogBuffer buffer : buffers.values()) {
            total += buffer.size();
        }
        return total;
    }
}
