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
 * 测试用可变时钟：手动推进时间驱动表轮/熔断/看门狗，零真实等待（测试纪律：禁 sleep 同步）。
 *
 * @author coffee
 */
public final class MutableClock implements SchedulerClock {

    private long now = 1_000_000_000L;

    @Override
    public long nanoTime() {
        return now;
    }

    /** 推进 millis 毫秒。 */
    public void advanceMillis(long millis) {
        now += millis * 1_000_000L;
    }

    public long currentMillis() {
        return now / 1_000_000L;
    }
}
