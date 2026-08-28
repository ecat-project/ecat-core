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
 * 周期策略：把「下一拍在哪」全部封进策略——时钟、名义锚点、网格/相位族差异（fixedDelay
 * 的完成点+period、fixedRate 的名义网格+在飞跨拍跳过、墙钟相位族的逐轮重对齐）由消费域
 * 自行实现，core 只按链事件取延迟（29 号 v2：定时需求共性≠执行机制共性，网格知识归域）。
 *
 * <p><b>时钟归策略</b>：三个方法读策略自持的时钟（域内注入单调钟/墙钟/测试假钟），
 * PeriodicRunner 本身不感知时间——http 域相位族用墙钟、轮询网格用单调钟即各自成策略。
 *
 * <p><b>三事件契约</b>（单飞链上串行调用，无跨线程竞态）：
 * <ol>
 *   <li>起链 {@link #firstDelayMillis()}：锚定首拍（轮询域惯例 0=首发即发；相位族=到
 *       下一个未来网格点）；</li>
 *   <li>到拍 {@link #onFire()}：过期即弃判定——滞后超阈值时把锚点推进到首个未来网格点
 *       并返回 {@link FireDecision#drop(long)}（公式：skips = lag / period + 1），
 *       未过期返回 {@link FireDecision#run()}；</li>
 *   <li>结算 {@link #onSettleRearmMillis()}：轮事务终态后推进锚点到下一拍并返回延迟
 *       （fixedDelay=结算点+period；fixedRate=网格+period 且跳过在飞期间跨过的拍，
 *       不做滞后补跑——补跑=同任务重入，正是要消灭的形态）。</li>
 * </ol>
 */
public interface RoundSchedule {

    /** 起链：到首拍的延迟（毫秒，&gt;=0）。 */
    long firstDelayMillis();

    /** 到拍决策：执行本轮，或过期即弃（锚点已推进到首个未来网格点）。 */
    FireDecision onFire();

    /** 结算点：推进锚点到下一拍，返回重排延迟（毫秒，&gt;=0）。 */
    long onSettleRearmMillis();
}
