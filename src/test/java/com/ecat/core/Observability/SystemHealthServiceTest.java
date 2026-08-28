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

package com.ecat.core.Observability;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.MDC;

import com.ecat.core.Bus.BusRegistry;
import com.ecat.core.Device.DeviceBase;
import com.ecat.core.EcatCore;
import com.ecat.core.I18n.I18nKeyPath;
import com.ecat.core.State.AttrChangedCallbackParams;
import com.ecat.core.State.AttributeBase;
import com.ecat.core.State.AttributeType;
import com.ecat.core.State.UnitInfo;
import com.ecat.core.Utils.DynamicConfig.ConfigDefinition;

/**
 * system_health 快照服务测试（14 号架构 §5.4-2；21 号观测面形态）：
 * 三节结构完整性（execution=写命令失败 counter / bus / threads）、速率差分（注入时钟
 * 确定性驱动，零 sleep）、组件缺失时节为 null（严格模式不编造）。旧引擎 15 键 scheduler
 * 视图按 D-19-5 接受随引擎消亡，SdkMetrics 注册柜台 8 个零读取 gauge 按 D-21-2 摘除
 * （biz/serial/modbus 键不再断言；失败自增语义由 State 包两个消费测试覆盖）。
 */
public class SystemHealthServiceTest {

    private BusRegistry registry;
    private SystemHealthService service;

    @Before
    public void setUp() {
        // 服务只依赖总线（execution 节直读 AttributeBase 静态 counter，无组件装配需求）
        registry = new BusRegistry();
        service = new SystemHealthService(registry);
    }

    @After
    public void tearDown() {
        MDC.clear();
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(Object container, String key) {
        return (Map<String, Object>) ((Map<String, Object>) container).get(key);
    }

    @Test
    @SuppressWarnings("unchecked")
    public void snapshotContainsThreeSectionsWithExecutionBusThreads() throws Exception {
        // counter 单调递增且进程级共享（并行测试可能推高）。先驱动一次确定性写失败
        // （future.get() 返回即 whenComplete 记账完成）把下界锚定在已推高的读数上——
        // 消除「counter 恒 0 时夹逼恒真」的退化窗口：快照若编造常数 0 必然低于下界而挂
        long before = AttributeBase.commandFailedCount();
        driveOneFailedWrite();
        long after = AttributeBase.commandFailedCount();
        assertThat("写失败确定性推高 counter（本测试自己驱动的因果）", after > before, is(true));
        Map<String, Object> snap = service.snapshot(1_000L);
        long afterSnapshot = AttributeBase.commandFailedCount();
        assertThat(snap.get("timestamp"), is(1_000L));

        Map<String, Object> execution = section(snap, "execution");
        assertThat("execution 节来自 AttributeBase 进程级静态 counter，恒可采集",
                execution, notNullValue());
        Long commandFailed = (Long) execution.get("commandFailed");
        assertThat("execution.commandFailed 存在且为计数读数", commandFailed, notNullValue());
        assertThat("快照值 ≥ 已推高下界且 ≤ 快照后直读（活 counter 透出）",
                commandFailed >= after && commandFailed <= afterSnapshot, is(true));

        Map<String, Object> bus = section(snap, "bus");
        assertThat(bus, notNullValue());
        assertThat("消费者表键存在", bus.containsKey("consumers"), is(true));

        Map<String, Object> threads = section(snap, "threads");
        assertThat("线程普查总数 > 0", (Integer) threads.get("total") > 0, is(true));
        List<Map<String, Object>> groups = (List<Map<String, Object>>) threads.get("groups");
        assertThat("分组非空", groups.isEmpty(), is(false));
        int groupSum = groups.stream().mapToInt(g -> (Integer) g.get("count")).sum();
        assertThat("分组计数之和 = 总线程数", groupSum, is((Integer) threads.get("total")));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void ratesAreNullOnFirstReadAndDifferencedOnSecond() {
        Map<String, Object> first = service.snapshot(1_000L);
        assertThat("首次读取无窗口，速率如实 null",
                section(first, "bus").get("publishPerSecond"), nullValue());
        assertThat(section(first, "threads").get("churnPerSecond"), nullValue());
        assertThat(section(first, "threads").get("rateWindowMillis"), nullValue());

        // 窗口内第二次读：时间推进 2s、发布 5 个事件 → publishPerSecond = 2.5
        for (int i = 0; i < 5; i++) {
            registry.publish(com.ecat.core.Bus.event.BusEvent.of("test.topic",
                    new ProbePayload(), com.ecat.core.Bus.event.EventContext.root(
                            com.ecat.core.Bus.event.EventContext.Source.SYSTEM, null)));
        }
        Map<String, Object> second = service.snapshot(3_000L);
        assertThat(section(second, "bus").get("publishPerSecond"), is(2.5));
        assertThat(section(second, "threads").get("rateWindowMillis"), is(2_000L));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void sameMillisecondReadsYieldNullRateNotDivideByZero() {
        service.snapshot(1_000L);
        Map<String, Object> second = service.snapshot(1_000L);
        assertThat("零跨度窗口无效 → null（不编 0 也不 NaN）",
                section(second, "bus").get("publishPerSecond"), nullValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void missingComponentsReportedAsNullSections() {
        SystemHealthService bare = new SystemHealthService(null);
        Map<String, Object> snap = bare.snapshot(1_000L);
        assertThat("总线未装配如实 null（不编造空结构）", snap.get("bus"), nullValue());
        assertThat("execution 节来自静态 counter，总线缺失不受影响", snap.get("execution"), notNullValue());
        assertThat("线程节恒可采集", section(snap, "threads").get("total") instanceof Integer, is(true));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void consumerTableShowsQueueDepthAndDropped() throws Exception {
        DroppingProbeConsumer consumer = new DroppingProbeConsumer();
        // 容量 1 的消费者：连发 3 条（消费线程阻塞在 latch 上），drop-oldest 丢 2
        consumer.onEvent("e1");
        consumer.onEvent("e2");
        consumer.onEvent("e3");

        Map<String, Object> snap = service.snapshot(1_000L);
        List<Map<String, Object>> consumers =
                (List<Map<String, Object>>) section(snap, "bus").get("consumers");
        Map<String, Object> row = consumers.stream()
                .filter(c -> "drop-probe".equals(c.get("name")))
                .findFirst().orElse(null);
        assertThat("探针消费者必须被枚举", row, notNullValue());
        assertThat((Integer) row.get("queueCapacity"), is(1));
        assertTrue("丢批计数 >= 0 且键存在", row.containsKey("dropped"));

        consumer.release();
        consumer.shutdown();
    }

    @Test
    @SuppressWarnings("unchecked")
    public void threadSnapshotListsEveryThreadWithFirstFrame() {
        Map<String, Object> detail = service.threadSnapshot();
        Map<String, Object> threads = section(detail, "threads");
        List<Map<String, Object>> list = (List<Map<String, Object>>) threads.get("list");
        assertThat("清单条数 = 总数", list.size(), is((Integer) threads.get("total")));
        assertThat("每条都有名字", list.get(0).containsKey("name"), is(true));
        assertThat("每条都有栈首帧键（无栈时值可为 null）", list.get(0).containsKey("firstFrame"), is(true));
    }

    /** 写失败驱动夹具：最小 Integer 属性经 IO 写模板收 false（State 包消费测试同款形态）。 */
    private static class FailingWriteAttr extends AttributeBase<Integer> {
        FailingWriteAttr() {
            super("health_fail_attr", null, null, null, 0, false, true,
                    (Function<AttrChangedCallbackParams<Integer>, CompletableFuture<Boolean>>) null);
        }
        @Override protected CompletableFuture<Boolean> setValue(Integer newValue) {
            return setValueWithIoBody(newValue, () -> Boolean.FALSE);
        }
        @Override public String getDisplayValue(UnitInfo toUnit) { return String.valueOf(value); }
        @Override protected Integer convertFromUnitImp(Integer v, UnitInfo u) { return v; }
        @Override public ConfigDefinition getValueDefinition() { return null; }
        @Override protected I18nKeyPath getI18nPrefixPath() { return new I18nKeyPath("state.health_fail_attr.", ""); }
        @Override public AttributeType getAttributeType() { return AttributeType.UNKNOWN; }
        @Override public Double convertValueToUnit(Double v, UnitInfo f, UnitInfo t) { return v; }
    }

    /**
     * 驱动一次属性写失败：最小装配 mock 设备/总线（失败路径零发布，no-op publish 兜面），
     * {@code get()} 返回即记账完成——确定性，无等待窗口。
     */
    private static void driveOneFailedWrite() throws Exception {
        DeviceBase device = mock(DeviceBase.class);
        EcatCore core = mock(EcatCore.class);
        BusRegistry bus = mock(BusRegistry.class);
        when(device.getId()).thenReturn("health-drive");
        when(device.isReady()).thenReturn(true);
        when(device.getCore()).thenReturn(core);
        when(core.getBusRegistry()).thenReturn(bus);
        doAnswer(inv -> null).when(bus).publish(any());
        FailingWriteAttr attr = new FailingWriteAttr();
        attr.setDevice(device);
        Boolean ok = attr.setDisplayValue("1").get(5, TimeUnit.SECONDS);
        assertThat("IO 载荷收 false（写失败）", ok, is(false));
    }

    /** 容量 1 + 首 consume 阻塞的探针：制造队列满触发 drop-oldest。 */
    private static final class DroppingProbeConsumer
            extends com.ecat.core.Bus.consumer.AbstractBusConsumer<String> {
        private volatile java.util.concurrent.CountDownLatch gate =
                new java.util.concurrent.CountDownLatch(1);

        DroppingProbeConsumer() {
            super("drop-probe", 1);
        }

        @Override
        protected void consume(String event) {
            try {
                gate.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        void release() {
            gate.countDown();
        }
    }

    private static final class ProbePayload implements com.ecat.core.Bus.event.BusPayload {
    }
}
