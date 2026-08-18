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

package com.ecat.core.Bus;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;
import java.util.regex.Pattern;

import com.ecat.core.Utils.Log;
import com.ecat.core.Utils.LogFactory;
import com.ecat.core.Bus.event.BusEvent;

/**
 * BusRegistry —— 事件总线注册表，管理主题订阅与发布。支持通配符模式匹配主题（key 含 {@code *}）；
 * 精确 key（不含 {@code *}）走 Map equals 直配快路径，见 {@link #dispatchToMatching}。
 *
 * <p><b>流控模型</b>：发布即同步扇出——{@link #publish} 在调用线程内依次同步调用所有匹配订阅者，
 * <b>不持有共享线程池</b>。"是否异步/离线处理"下放到消费者自身：重活的订阅者用
 * {@link com.ecat.core.Bus.consumer.AbstractBusConsumer} 自带独占线程 + 有界队列 + 丢最旧反压，
 * 其 onEvent 只入队纳秒级返回，绝不阻塞发布线程。这样生产者（设备轮询线程）投递再多也不会因某个
 * 慢消费者把共享池占满而卡死整个系统——每个消费者各自背压，互不影响。
 *
 * <p><b>非阻塞契约（无例外铁律）</b>：总线是关键基础设施，<b>所有订阅者无论 topic——含 load/lifecycle
 * 等同步语义 topic——handleEvent 都必须在 {@link #SLOW_SUBSCRIBER_MS} 阈值内返回，没有任何例外</b>。
 * 即使某 topic 的处理"需要时间"（如收到 INTEGRATIONS_ALL_LOADED 后创建一批逻辑设备），也必须由该订阅者
 * <b>用自己的执行线程</b>（AbstractBusConsumer）处理，绝不在总线发布线程里干重活——否则发布线程被卡，
 * 拖慢/阻塞所有其他 topic 的投递。本类用<b>慢订阅者观测</b>守约：单次 handleEvent 超阈值即记 error 告警，
 * <b>告警即真违规</b>，须把该订阅者的重活移到它自己的线程。
 *
 * <p><b>类型化入口</b>：唯一发布方法是 {@link #publish(BusEvent)}——topic 从 {@code event.getType()} 取，
 * 溯源从 {@code event.getContext()} 取，载荷从 {@code event.getPayload()} 取。订阅者经
 * {@link EventSubscriber#handleEvent(BusEvent)} 收强类型信封。无 Object 入口、无冗余 context 参数。
 *
 * <p>订阅者表分两路：精确 topic（生产 100% 流量）Map 直配；通配 topic（含 {@code *}）预编译 Pattern
 * 匹配。两路订阅者列表都用 CopyOnWriteArrayList：发布期遍历的是数组快照，订阅/取消订阅期间发布不会抛
 * ConcurrentModificationException；写时复制开销可忽略（每 topic 订阅者通常个位数）。
 *
 * @author coffee
 */
public class BusRegistry {

    private static final Log log = LogFactory.getLogger(BusRegistry.class);

    /** 慢订阅者阈值（毫秒）——任何 topic 的订阅者 handleEvent 超此即 error 告警（无例外；
     *  超时即真违规，须把重活移到订阅者自有执行线程）。可用 -Decat.bus.slowSubscriberMs= 覆盖。 */
    private static final long SLOW_SUBSCRIBER_MS_DEFAULT = 100L;
    private static final long SLOW_SUBSCRIBER_MS = Long.parseLong(
            System.getProperty("ecat.bus.slowSubscriberMs", String.valueOf(SLOW_SUBSCRIBER_MS_DEFAULT)));

    // 精确 topic（订阅 key 不含 '*'）-> 订阅者列表。publish 热路径经 Map equals/hash 直配 O(1)。
    // CopyOnWriteArrayList：发布遍历快照，订阅/取消订阅安全无并发修改异常。
    private final Map<String, List<EventSubscriber>> exactSubscribers = new ConcurrentHashMap<>();

    // 通配 topic（订阅 key 含 '*'，显式支持的通配语义）-> 预编译 Pattern + 订阅者列表。
    // Pattern 在 subscribe 期编译一次（冷路径），publish 只做 matcher().matches()，绝不重复 compile。
    // 实证（2026-08-16 全仓 grep src/main 的 subscribe 实参）：生产订阅 100% 为 BusTopic 枚举精确名
    // （device.data.update 等 9 个），零通配订阅；唯一通配用法在测试 BusRegistrySyncPublishTest。
    // 即通配分支覆盖的是显式契约而非现存流量——热路径 isEmpty() 短路下精确流量完全跳过该分支。
    private final Map<String, WildcardTopicSubscribers> wildcardSubscribers = new ConcurrentHashMap<>();

    /**
     * 累计发布事件数（含无订阅者的事件）。LongAdder 原子自增是发布热路径上唯一的可观测成本——
     * 零格式化零字符串；速率由 system_health 端点读时差分计算（B2 自观测）。
     */
    private final LongAdder publishedTotal = new LongAdder();

    /**
     * 通配订阅条目——一个含 {@code *} 的订阅 key 及其订阅者。
     * <p>Pattern 语义与历史逐位一致：{@code key.replace("*", ".*")} 的正则全匹配
     * （{@code Pattern.matches} 等价于 {@code compile(regex).matcher(input).matches()}，
     * 此处只是把 compile 从每次 publish 挪到 subscribe 一次）。
     */
    private static final class WildcardTopicSubscribers {
        final Pattern pattern;
        final List<EventSubscriber> subscribers = new CopyOnWriteArrayList<>();
        WildcardTopicSubscribers(String topicKey) {
            this.pattern = Pattern.compile(topicKey.replace("*", ".*"));
        }
    }

    // 订阅方法，返回一个 Subscription 对象用于取消订阅
    public Subscription subscribe(String topic, EventSubscriber subscriber) {
        if (topic.indexOf('*') >= 0) {
            // 通配订阅：预编译 Pattern 的成本（冷路径，每 key 一次）换取 publish 零编译
            WildcardTopicSubscribers ws = wildcardSubscribers.computeIfAbsent(topic, k -> new WildcardTopicSubscribers(k));
            if (!ws.subscribers.contains(subscriber)) {
                ws.subscribers.add(subscriber);
            }
            return () -> unsubscribeWildcard(topic, subscriber);
        }
        List<EventSubscriber> subs = exactSubscribers.computeIfAbsent(topic, k -> new CopyOnWriteArrayList<EventSubscriber>());
        if (!subs.contains(subscriber)) {
            subs.add(subscriber);
        }
        return () -> unsubscribeExact(topic, subscriber);
    }

    /**
     * 精确订阅取消：移除该订阅者；仅当列表清空且仍是当前列表才清理映射——共享 topic
     * （device.data.update 挂 13 处生产订阅）上取消其一绝不能影响其余订阅者。
     */
    private void unsubscribeExact(String topic, EventSubscriber subscriber) {
        List<EventSubscriber> subList = exactSubscribers.get(topic);
        if (subList != null) {
            subList.remove(subscriber);
            if (subList.isEmpty()) {
                exactSubscribers.remove(topic, subList);
            }
        }
    }

    /** 通配订阅取消，规则与 {@link #unsubscribeExact} 相同。 */
    private void unsubscribeWildcard(String topic, EventSubscriber subscriber) {
        WildcardTopicSubscribers ws = wildcardSubscribers.get(topic);
        if (ws != null) {
            ws.subscribers.remove(subscriber);
            if (ws.subscribers.isEmpty()) {
                wildcardSubscribers.remove(topic, ws);
            }
        }
    }

    /**
     * 发布事件（唯一入口）：同步扇出——在当前线程内依次调用所有匹配 {@code event.getType()} 的订阅者。
     *
     * @param event 总线事件信封（携带 type/payload/firedAt/uuid/context），不能为 null
     */
    public void publish(BusEvent<?> event) {
        if (event == null) {
            throw new IllegalArgumentException("event 不能为 null");
        }
        publishedTotal.increment();
        dispatchToMatching(event);
    }

    /** 累计发布事件总数（system_health 速率差分的原始值）。 */
    public long getPublishedCount() {
        return publishedTotal.sum();
    }

    /**
     * 核心分发逻辑：按 event.getType() 查找所有匹配主题的订阅者，在当前线程同步调用；记录慢订阅者与异常。
     *
     * <p><b>热路径快路径</b>（本方法是全系统最高频路径，353 事件/s、100 设备满载估 1-2k/s）：
     * <ul>
     *   <li>精确 topic 先行 Map equals/hash 直配 O(1)——旧实现对每个事件 × 每个已注册 key 重编译正则
     *       （{@code key.replace("*",".*")} 新串 + {@code Pattern.matches} 内部 compile），实测 3,123ns/事件、
     *       ≈18KB/事件分配 churn（551GB/日年轻代垃圾的主力）；equals 直配 28ns/事件、零分配。</li>
     *   <li>通配 key（含 {@code *}，显式支持的通配语义）落到预编译 Pattern 匹配，语义与旧实现逐位一致；
     *       无通配订阅时 isEmpty() 短路，扫描整个通配表的成本都不发生。</li>
     *   <li>不含 {@code *} 的 key 语义收敛为字面 equals：旧实现把 key 当正则（点号匹配任意字符），
     *       点号在本系统 topic 命名中是层级分隔符（BusTopic 全部 9 名均为 a.b.c 形态、生产 subscribe
     *       实参 100% 枚举名），字面化无现存流量差异，见 BusRegistryTopicMatchSemanticsTest。</li>
     *   <li>同一发布同时命中精确与通配订阅时，先投递精确订阅再投递通配订阅（确定性顺序；旧实现的
     *       跨 key 顺序 = ConcurrentHashMap 迭代序，本就无契约）。</li>
     * </ul>
     */
    private void dispatchToMatching(BusEvent<?> event) {
        String topic = event.getType();
        List<EventSubscriber> exact = exactSubscribers.get(topic);
        if (exact != null) {
            dispatchToList(event, topic, exact);
        }
        if (!wildcardSubscribers.isEmpty()) {
            for (WildcardTopicSubscribers ws : wildcardSubscribers.values()) {
                if (ws.pattern.matcher(topic).matches()) {
                    dispatchToList(event, topic, ws.subscribers);
                }
            }
        }
    }

    /** 单个订阅者列表的同步扇出：慢订阅者计时与异常隔离逻辑与改造前逐行一致。 */
    private static void dispatchToList(BusEvent<?> event, String topic, List<EventSubscriber> subscribers) {
        for (EventSubscriber subscriber : subscribers) {
            long t0 = System.nanoTime();
            try {
                // 同步扇出：订阅者在发布线程内执行。重活订阅者须自行异步（AbstractBusConsumer）。
                subscriber.handleEvent(event);
            } catch (RuntimeException e) {
                // 单个订阅者抛异常不得影响其他订阅者与发布线程；记 error 暴露，严格模式不静默吞。
                log.error("总线订阅者处理事件异常: topic=" + topic
                        + ", subscriber=" + safeName(subscriber), e);
            }
            long dtMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - t0);
            if (dtMs > SLOW_SUBSCRIBER_MS) {
                // 违反非阻塞契约：该订阅者在发布线程耗时过长，会拖慢生产者。告警暴露，由开发改为异步消费。
                log.error("总线慢订阅者告警（违反非阻塞契约，拖慢发布线程）: topic=" + topic
                        + ", subscriber=" + safeName(subscriber) + ", 耗时=" + dtMs + "ms"
                        + "（重活应包进 AbstractBusConsumer 自带独占线程）");
            }
        }
    }

    /** 取订阅者类名用于日志；lambda/匿名类取其实际类名即可。 */
    private static String safeName(EventSubscriber subscriber) {
        return (subscriber == null) ? "null" : subscriber.getClass().getName();
    }

    /**
     * 关闭资源。重构后无共享线程池，此方法为空操作；消费者线程各自管理生命周期
     * （AbstractBusConsumer.shutdown 由各集成在 onPause 释放）。
     */
    public void shutdown() {
        // 重构后无共享线程池：无资源可关。
    }
}
