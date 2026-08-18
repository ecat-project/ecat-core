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

package com.ecat.core.Task.engine;

/**
 * 调度引擎时钟抽象。
 *
 * <p>为什么不用 {@link System#nanoTime()} 直连：表轮到期判定、熔断冷却、看门狗超时全都建立在
 * 「当前时刻」上，若引擎直接读系统时钟，单测只能靠真实等待或 Thread.sleep 驱动时间前进，
 * 既慢又 flaky。注入时钟后测试用可变时钟手动推进时间（测试纪律：禁 sleep 同步），
 * 引擎本身零改动。
 *
 * <p>实现约定：与 System.nanoTime 同语义——单调不减、仅用于差值、可跨线程读。
 *
 * @author coffee
 */
public interface SchedulerClock {

    /** 当前单调时刻（纳秒）。 */
    long nanoTime();

    /** 生产时钟：直接转发 System.nanoTime。 */
    SchedulerClock SYSTEM = System::nanoTime;
}
