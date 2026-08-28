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

package com.ecat.core.Task;

/**
 * 轮询事务因「源锁忙」被立即放弃的信号（L2 传输 SDK 层共用词汇——17 号 §1：modbus→serial
 * 的 L2-L2 横向共用件批 0 下沉 core；S4 归位 Task 词汇包——纯异常词汇，与调度引擎无关，
 * 不随 engine/execution 消亡）。
 *
 * <p><b>语义</b>：只由<b>轮询入口</b>（executePolling 的 {@code tryAcquire}）产生——锁忙时
 * 本周期立即跳过、下周期再试，调用线程零 park。周期任务的 whenComplete 消费方应把本异常
 * 识别为「本轮跳过」而非设备错误（不计入失败率/不触发告警）；写命令路径
 * （MANUAL_COMMAND 经闸）不使用本异常，保留有限等待语义。
 *
 * <p><b>消费面</b>：modbus 仓直接消费；serial 仓的
 * {@code com.ecat.integration.SerialIntegration.LockBusySkippedException} 为过渡壳
 * （继承本类 + {@code isLockBusySkip} 静态委托，46 个设备仓 import 零破坏）。
 *
 * @author coffee
 */
public class LockBusySkippedException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public LockBusySkippedException(String message) {
        super(message);
    }

    /**
     * 判定给定的消费侧异常（whenComplete / exceptionally 收到的 t，或同步阻塞 {@code get()}
     * 抛出的 {@link java.util.concurrent.ExecutionException}）是否为本「本轮跳过」信号。
     * 逐层剥 CompletionException / ExecutionException 包装后做 instanceof 判定，
     * 供各集成轮询调用点统一做降级分支（静默/debug 返回，不进设备错误处理链）。
     */
    public static boolean isLockBusySkip(Throwable t) {
        Throwable cur = t;
        while (cur != null) {
            if (cur instanceof LockBusySkippedException) {
                return true;
            }
            cur = (cur instanceof java.util.concurrent.CompletionException
                    || cur instanceof java.util.concurrent.ExecutionException) && cur.getCause() != null
                    ? cur.getCause() : null;
        }
        return false;
    }
}
