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

/**
 * 到拍决策（{@link RoundSchedule#onFire()} 返回值）：正常执行轮体（{@link #run()}），
 * 或过期即弃（{@link #drop(long)}——锚点已由策略推进到首个未来网格点，本轮不触碰轮体，
 * 按决策携带的延迟重排下一拍）。两态在返回值层面互斥，杜绝「布尔判定+二次取延迟」
 * 两步式缝的推进/读延迟竞态。
 */
public final class FireDecision {

    private static final FireDecision RUN = new FireDecision(true, -1L);

    private final boolean run;
    private final long rearmDelayMillis;

    private FireDecision(boolean run, long rearmDelayMillis) {
        this.run = run;
        this.rearmDelayMillis = rearmDelayMillis;
    }

    /** 正常执行轮体（结算后经 {@link RoundSchedule#onSettleRearmMillis()} 取下一拍）。 */
    public static FireDecision run() {
        return RUN;
    }

    /**
     * 过期即弃本轮：重排延迟由策略在推进锚点后给出。
     *
     * @throws IllegalArgumentException 延迟为负（锚点未推进到未来即返回，策略实现缺陷）
     */
    public static FireDecision drop(long rearmDelayMillis) {
        if (rearmDelayMillis < 0L) {
            throw new IllegalArgumentException("drop 重排延迟不能为负: " + rearmDelayMillis);
        }
        return new FireDecision(false, rearmDelayMillis);
    }

    /** true=执行轮体；false=过期即弃（读 {@link #rearmDelayMillis()} 取重排延迟）。 */
    public boolean shouldRun() {
        return run;
    }

    /** 弃拍重排延迟（毫秒）。 */
    public long rearmDelayMillis() {
        if (run) {
            throw new IllegalStateException("run 决策无重排延迟（结算路径才取下一拍）");
        }
        return rearmDelayMillis;
    }
}
