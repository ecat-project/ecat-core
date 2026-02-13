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

/**
 * 日志条目实体类
 *
 * <p>表示单条日志记录，包含时间戳、坐标、级别、日志器、线程、消息和异常信息。
 * 
 * @author coffee
 */
public class LogEntry {
    private long timestamp;
    private String coordinate;
    private String level;
    private String logger;
    private String thread;
    private String message;
    private String throwable;

    public LogEntry() {
    }

    public LogEntry(long timestamp, String coordinate, String level, String logger, String thread, String message) {
        this.timestamp = timestamp;
        this.coordinate = coordinate;
        this.level = level;
        this.logger = logger;
        this.thread = thread;
        this.message = message;
    }

    public LogEntry(long timestamp, String coordinate, String level, String logger, String thread, String message, String throwable) {
        this.timestamp = timestamp;
        this.coordinate = coordinate;
        this.level = level;
        this.logger = logger;
        this.thread = thread;
        this.message = message;
        this.throwable = throwable;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getCoordinate() {
        return coordinate;
    }

    public void setCoordinate(String coordinate) {
        this.coordinate = coordinate;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public String getLogger() {
        return logger;
    }

    public void setLogger(String logger) {
        this.logger = logger;
    }

    public String getThread() {
        return thread;
    }

    public void setThread(String thread) {
        this.thread = thread;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getThrowable() {
        return throwable;
    }

    public void setThrowable(String throwable) {
        this.throwable = throwable;
    }

    @Override
    public String toString() {
        return "LogEntry{" +
                "timestamp=" + timestamp +
                ", coordinate='" + coordinate + '\'' +
                ", level='" + level + '\'' +
                ", logger='" + logger + '\'' +
                ", thread='" + thread + '\'' +
                ", message='" + message + '\'' +
                ", throwable='" + throwable + '\'' +
                '}';
    }
}
