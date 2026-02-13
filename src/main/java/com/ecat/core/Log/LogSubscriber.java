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

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * SSE 日志订阅者
 *
 * <p>将日志条目格式化为 SSE 消息并发送到客户端。
 *
 * <p>消息格式：
 * <pre>
 * event: log
 * data: {"timestamp":1234567890,"coordinate":"core",...}
 *
 * </pre>
 * 
 * @author coffee
 */
public class LogSubscriber implements AutoCloseable {
    protected final OutputStream outputStream;
    protected final AtomicBoolean closed = new AtomicBoolean(false);

    public LogSubscriber(OutputStream outputStream) {
        this.outputStream = outputStream;
    }

    /**
     * 发送日志条目到客户端
     *
     * @param entry 日志条目
     * @throws IOException 发送失败时抛出
     */
    public void send(LogEntry entry) throws IOException {
        if (closed.get() || outputStream == null) {
            return;
        }
        String sseMessage = formatSseMessage(entry);
        outputStream.write(sseMessage.getBytes(StandardCharsets.UTF_8));
        outputStream.flush();
    }

    /**
     * 格式化 SSE 消息
     *
     * @param entry 日志条目
     * @return SSE 消息字符串
     */
    protected String formatSseMessage(LogEntry entry) {
        StringBuilder sb = new StringBuilder();
        sb.append("event: log\n");
        sb.append("data: {");
        sb.append("\"timestamp\":").append(entry.getTimestamp()).append(",");
        sb.append("\"coordinate\":\"").append(escapeJson(entry.getCoordinate())).append("\",");
        sb.append("\"level\":\"").append(escapeJson(entry.getLevel())).append("\",");
        sb.append("\"logger\":\"").append(escapeJson(entry.getLogger())).append("\",");
        sb.append("\"thread\":\"").append(escapeJson(entry.getThread())).append("\",");
        sb.append("\"message\":\"").append(escapeJson(entry.getMessage())).append("\"");
        if (entry.getThrowable() != null) {
            sb.append(",\"throwable\":\"").append(escapeJson(entry.getThrowable())).append("\"");
        }
        sb.append("}\n\n");
        return sb.toString();
    }

    /**
     * 转义 JSON 字符串中的特殊字符
     *
     * @param value 原始字符串
     * @return 转义后的字符串
     */
    protected String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    /**
     * 检查是否已关闭
     *
     * @return 是否已关闭
     */
    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true) && outputStream != null) {
            try {
                outputStream.flush();
            } catch (IOException e) {
                // ignore
            }
        }
    }
}
