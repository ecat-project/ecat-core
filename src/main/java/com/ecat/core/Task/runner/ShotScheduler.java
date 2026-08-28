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

package com.ecat.core.Task.runner;

import java.util.concurrent.ScheduledFuture;

/**
 * 单发提交缝：到点执行一次（命令已完成 MDC 包装），返回可取消句柄。
 *
 * <p>29 号 v2 库级工具契约：PeriodicRunner 不自带线程池、不注册任何治理——执行器由
 * 消费方 SDK 传入并拥有。本缝窄于 {@link java.util.concurrent.ScheduledExecutorService}：
 * 周期链自排（每拍一单发），不使用执行器原生周期形态，缝面即实际消费面。消费方两种喂法：
 * 真执行器经 {@link PeriodicRunner#on(java.util.concurrent.ScheduledExecutorService)} 适配；
 * 测试替身（捕获型 fake + 可取消桩）直接实现本接口——http 域 S0 穿刺的测试缝三件套形态。
 */
public interface ShotScheduler {

    /**
     * @param command      已包装命令（MDC 语义在包装层完成）
     * @param delayMillis  延迟毫秒（0=立即）
     * @return 可取消句柄（链路 cancel 撤销待发拍）
     */
    ScheduledFuture<?> fireAfter(Runnable command, long delayMillis);
}
