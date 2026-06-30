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

package com.ecat.core.Bus.event;

import java.time.Instant;

/**
 * 异步操作执行事件（不可变快照）—— async.execution.* topic 的载荷。
 *
 * <p>在 Bus 事件中传递异步操作的状态信息。
 * 与 ecat-core-api 的 AsyncExecution 类解耦，
 * 仅包含事件消费者需要的字段。
 *
 * <p>ecat-core-api 的 AsyncExecution 提供 toInfo() 方法转换为此类（方法名保留，仅返回类型改为本类）。
 *
 * @author coffee
 */
public class AsyncExecutionEvent implements BusPayload {

    /** 异步操作唯一标识，格式 "async-{序号}"，如 "async-1" */
    private final String asyncExecutionId;

    /**
     * 操作类型，当前定义值：
     * <ul>
     *   <li>"device.attribute.write" — 物理设备属性写入</li>
     *   <li>"logic_device.attribute.write" — 逻辑设备属性写入</li>
     * </ul>
     */
    private final String operationType;

    /**
     * 操作目标路径，为原始 HTTP 请求路径，如：
     * "/devices/{deviceId}/attributes/{attrId}/value"
     */
    private final String targetPath;

    /** 发起者标识（客户端自定义，可为 null） */
    private final String clientId;

    /** 操作状态 */
    private final Status status;

    /** 错误信息（仅 FAILED/TIMEOUT 时有值，SUCCESS 时为 null） */
    private final String errorMessage;

    /** 操作创建时间（UTC） */
    private final Instant createdAt;

    /** 操作完成时间（UTC，未完成时为 null） */
    private final Instant completedAt;

    /**
     * 异步操作状态枚举
     *
     * <p>状态机流转：
     * <pre>
     *   PENDING ──→ RUNNING ──→ SUCCESS
     *                     ├──→ FAILED
     *                     ├──→ TIMEOUT
     *                     └──→ CANCELLED
     *
     *   PENDING ──→ AWAITING_CONFIRM ──→ RUNNING (确认后)
     *                              └──→ CANCELLED (拒绝)
     * </pre>
     */
    public enum Status {
        /** 已提交，等待执行 */
        PENDING,
        /** 等待人工确认（高风险操作） */
        AWAITING_CONFIRM,
        /** 正在执行 */
        RUNNING,
        /** 执行成功 */
        SUCCESS,
        /** 执行失败 */
        FAILED,
        /** 执行超时 */
        TIMEOUT,
        /** 已取消 */
        CANCELLED
    }

    /**
     * 构造异步操作执行信息
     *
     * @param asyncExecutionId 异步操作唯一标识
     * @param operationType 操作类型
     * @param targetPath 操作目标路径
     * @param clientId 发起者标识（可为 null）
     * @param status 操作状态
     * @param errorMessage 错误信息（可为 null）
     * @param createdAt 创建时间（UTC）
     * @param completedAt 完成时间（UTC，可为 null）
     * @throws IllegalArgumentException 如果 asyncExecutionId、operationType、targetPath、status、createdAt 为 null
     */
    public AsyncExecutionEvent(String asyncExecutionId, String operationType, String targetPath,
                              String clientId, Status status,
                              String errorMessage, Instant createdAt, Instant completedAt) {
        if (asyncExecutionId == null || operationType == null
            || targetPath == null || status == null || createdAt == null) {
            throw new IllegalArgumentException(
                "asyncExecutionId, operationType, targetPath, status, createdAt must not be null");
        }
        this.asyncExecutionId = asyncExecutionId;
        this.operationType = operationType;
        this.targetPath = targetPath;
        this.clientId = clientId;
        this.status = status;
        this.errorMessage = errorMessage;
        this.createdAt = createdAt;
        this.completedAt = completedAt;
    }

    /**
     * 获取异步操作唯一标识
     *
     * @return 异步操作 ID，格式 "async-{序号}"
     */
    public String getAsyncExecutionId() { return asyncExecutionId; }

    /**
     * 获取操作类型
     *
     * @return 操作类型，如 "device.attribute.write"
     */
    public String getOperationType() { return operationType; }

    /**
     * 获取操作目标路径
     *
     * @return 目标路径，如 "/devices/{id}/attributes/{attrId}/value"
     */
    public String getTargetPath() { return targetPath; }

    /**
     * 获取请求者标识
     *
     * @return 发起者 ID，可为 null
     */
    public String getClientId() { return clientId; }

    /**
     * 获取操作状态
     *
     * @return 操作状态枚举
     */
    public Status getStatus() { return status; }

    /**
     * 获取错误信息
     *
     * @return 错误信息字符串，仅 FAILED/TIMEOUT 时有值
     */
    public String getErrorMessage() { return errorMessage; }

    /**
     * 获取操作创建时间
     *
     * @return 创建时间（UTC）
     */
    public Instant getCreatedAt() { return createdAt; }

    /**
     * 获取操作完成时间
     *
     * @return 完成时间（UTC），未完成时为 null
     */
    public Instant getCompletedAt() { return completedAt; }

    @Override
    public String toString() {
        return "AsyncExecutionEvent{id=" + asyncExecutionId + ", status=" + status +
               ", operationType=" + operationType + "}";
    }
}
