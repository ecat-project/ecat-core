package com.ecat.core.State;

import com.ecat.core.Bus.BusRegistry;
import com.ecat.core.Bus.event.BusEvent;
import com.ecat.core.Bus.event.DeviceDataChangedEvent;
import com.ecat.core.ConfigEntry.ConfigEntry;
import com.ecat.core.Device.DeviceBase;
import com.ecat.core.EcatCore;
import com.ecat.core.I18n.I18nKeyPath;
import com.ecat.core.Utils.DynamicConfig.ConfigDefinition;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AttributeBase 帧快照多状态（模型 B'）契约测试。
 * 锁定：旧路径零影响、三参整组替换、setStatus 撤/声明语义与发布链一次全量。
 */
public class AttributeBaseFrameStatusesTest {

    /** 参考 AttributeBaseComputeStatusesTest 的最小 AttributeBase stub。 */
    static class StubAttr extends AttributeBase<Double> {
        public StubAttr(String id) {
            super(id, AttributeClass.VALUE, null, null, 0, false, false);
        }

        @Override public String getDisplayValue(UnitInfo toUnit) { return null; }
        @Override protected Double convertFromUnitImp(Double v, UnitInfo u) { return v; }
        @Override public Double convertValueToUnit(Double v, UnitInfo f, UnitInfo t) { return v; }
        @Override public ConfigDefinition getValueDefinition() { return null; }
        @Override protected I18nKeyPath getI18nPrefixPath() { return new I18nKeyPath("test.", "test"); }
        @Override public AttributeType getAttributeType() { return AttributeType.NUMERIC; }
    }

    /** 满足 buildState 的最小设备 stub。 */
    private DeviceBase newStubDevice() {
        ConfigEntry entry = new ConfigEntry.Builder().build();
        return new DeviceBase(entry) {
            @Override public void load(com.ecat.core.EcatCore core) { }
            @Override public void init() { }
            @Override public void start() { }
            @Override public void stop() { }
            @Override public void release() { }
        };
    }

    /** 可真实走 publicState 提交链的 mock 设备；总线只承接事件，不引入外部基建。 */
    private StubAttr newPublishableAttr(BusRegistry bus) {
        DeviceBase device = mock(DeviceBase.class);
        when(device.getId()).thenReturn("frame-device");
        when(device.isReady()).thenReturn(true);
        EcatCore core = mock(EcatCore.class);
        when(core.getBusRegistry()).thenReturn(bus);
        when(device.getCore()).thenReturn(core);
        doNothing().when(bus).publish(any(BusEvent.class));

        StubAttr attr = new StubAttr("frame-attr");
        attr.setDevice(device);
        return attr;
    }

    @Test
    public void a1_legacyLifecycleStillPublishesSingletonStatuses() {
        BusRegistry bus = mock(BusRegistry.class);
        StubAttr attr = newPublishableAttr(bus);

        attr.updateValue(1.0, AttributeStatus.NORMAL);
        assertEquals(Collections.singleton(AttributeStatus.NORMAL), attr.getState().getStatuses());
        assertTrue(attr.publicState());

        attr.setStatus(AttributeStatus.ALARM);
        assertEquals(Collections.singleton(AttributeStatus.ALARM), attr.getState().getStatuses());
        assertTrue(attr.publicState());

        attr.updateValue(2.0, AttributeStatus.CALIBRATION);
        assertEquals(Collections.singleton(AttributeStatus.CALIBRATION), attr.getState().getStatuses());
        assertTrue(attr.publicState());

        attr.setStatus(AttributeStatus.MAINTENANCE);
        assertEquals(Collections.singleton(AttributeStatus.MAINTENANCE), attr.getState().getStatuses());
        assertTrue(attr.publicState());

        ArgumentCaptor<BusEvent<?>> captor = ArgumentCaptor.forClass(BusEvent.class);
        verify(bus, times(4)).publish(captor.capture());
        List<AttributeStatus> expected = Arrays.asList(
                AttributeStatus.NORMAL,
                AttributeStatus.ALARM,
                AttributeStatus.CALIBRATION,
                AttributeStatus.MAINTENANCE);
        for (int i = 0; i < expected.size(); i++) {
            DeviceDataChangedEvent event = (DeviceDataChangedEvent) captor.getAllValues().get(i).getPayload();
            assertEquals(Collections.singleton(expected.get(i)), event.getNewState().getStatuses());
        }
    }

    @Test
    public void b1_threeArgWritesValueMainAndOrderedFrameSnapshot() {
        StubAttr attr = new StubAttr("b1");
        attr.setDevice(newStubDevice());
        Set<AttributeStatus> frame = new LinkedHashSet<>(
                Arrays.asList(AttributeStatus.CALIBRATION, AttributeStatus.ALARM));

        assertTrue(attr.updateValue(10.0, AttributeStatus.ALARM, frame));

        assertEquals(AttributeStatus.ALARM, attr.getState().getStatus());
        assertEquals(Double.valueOf(10.0), attr.getState().getValue());
        assertEquals(frame, attr.getState().getStatuses());
        assertEquals(Arrays.asList(AttributeStatus.CALIBRATION, AttributeStatus.ALARM),
                new ArrayList<>(attr.getState().getStatuses()));
    }

    @Test
    public void b2_frameWithoutMainIsRejected() {
        StubAttr attr = new StubAttr("b2");
        attr.setDevice(newStubDevice());
        Set<AttributeStatus> frame = new LinkedHashSet<>(
                Arrays.asList(AttributeStatus.CALIBRATION, AttributeStatus.ALARM));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> attr.updateValue(10.0, AttributeStatus.NORMAL, frame));
        assertTrue(ex.getMessage().contains("frameStatuses 必须包含 mainStatus"));
        assertNull(attr.getState());
    }

    @Test
    public void b3_nullEmptyMainAndInvalidElementsAreRejected() {
        StubAttr attr = new StubAttr("b3");
        attr.setDevice(newStubDevice());
        Set<AttributeStatus> frame = Collections.singleton(AttributeStatus.NORMAL);
        Set<AttributeStatus> withNull = new LinkedHashSet<>(Arrays.asList(AttributeStatus.NORMAL, null));
        Set<AttributeStatus> withEmpty = new LinkedHashSet<>(
                Arrays.asList(AttributeStatus.NORMAL, AttributeStatus.EMPTY));

        assertThrows(IllegalArgumentException.class,
                () -> attr.updateValue(1.0, null, frame));
        assertThrows(IllegalArgumentException.class,
                () -> attr.updateValue(1.0, AttributeStatus.EMPTY, frame));
        assertThrows(IllegalArgumentException.class,
                () -> attr.updateValue(1.0, AttributeStatus.NORMAL, null));
        assertThrows(IllegalArgumentException.class,
                () -> attr.updateValue(1.0, AttributeStatus.NORMAL, withNull));
        assertThrows(IllegalArgumentException.class,
                () -> attr.updateValue(1.0, AttributeStatus.NORMAL, withEmpty));
        assertNull(attr.getState());
    }

    @Test
    public void b4_nextFrameReplacesWholeSnapshot() {
        StubAttr attr = new StubAttr("b4");
        attr.setDevice(newStubDevice());

        attr.updateValue(1.0, AttributeStatus.CALIBRATION, new LinkedHashSet<>(
                Arrays.asList(AttributeStatus.CALIBRATION, AttributeStatus.ALARM)));
        assertEquals(new LinkedHashSet<>(Arrays.asList(
                AttributeStatus.CALIBRATION, AttributeStatus.ALARM)), attr.getState().getStatuses());

        attr.updateValue(2.0, AttributeStatus.NORMAL, Collections.singleton(AttributeStatus.NORMAL));

        assertEquals(AttributeStatus.NORMAL, attr.getState().getStatus());
        assertEquals(Double.valueOf(2.0), attr.getState().getValue());
        assertEquals(Collections.singleton(AttributeStatus.NORMAL), attr.getState().getStatuses());
        assertFalse(attr.getState().getStatuses().contains(AttributeStatus.CALIBRATION));
        assertFalse(attr.getState().getStatuses().contains(AttributeStatus.ALARM));
    }

    @Test
    public void b5_repeatingSameFrameIsSemanticallyIdempotent() {
        StubAttr attr = new StubAttr("b5");
        attr.setDevice(newStubDevice());
        Set<AttributeStatus> frame = new LinkedHashSet<>(
                Arrays.asList(AttributeStatus.CALIBRATION, AttributeStatus.ALARM));
        attr.updateValue(3.0, AttributeStatus.ALARM, frame);
        AttrState<Double> first = attr.getState();
        Instant firstChanged = first.getLastChanged();

        attr.updateValue(3.0, AttributeStatus.ALARM, new LinkedHashSet<>(frame));
        AttrState<Double> second = attr.getState();

        assertEquals(first.getValue(), second.getValue());
        assertEquals(first.getStatus(), second.getStatus());
        assertEquals(first.getStatuses(), second.getStatuses());
        assertEquals(firstChanged, second.getLastChanged());
    }

    @Test
    public void b6_externalMutationCannotChangeStoredSnapshot() {
        StubAttr attr = new StubAttr("b6");
        attr.setDevice(newStubDevice());
        Set<AttributeStatus> frame = EnumSet.of(AttributeStatus.CALIBRATION, AttributeStatus.ALARM);

        assertTrue(attr.updateValue(4.0, AttributeStatus.ALARM, frame));
        frame.add(AttributeStatus.MALFUNCTION);

        assertEquals(new LinkedHashSet<>(Arrays.asList(
                AttributeStatus.CALIBRATION, AttributeStatus.ALARM)), attr.getState().getStatuses());
        assertFalse(attr.getState().getStatuses().contains(AttributeStatus.MALFUNCTION));
        assertThrows(UnsupportedOperationException.class,
                () -> attr.getState().getStatuses().add(AttributeStatus.MALFUNCTION));
    }

    @Test
    public void c1_singleArgSetStatusClearsSnapshotEvenWhenMainIsUnchanged() {
        StubAttr attr = new StubAttr("c1");
        attr.setDevice(newStubDevice());
        attr.updateValue(5.0, AttributeStatus.MAINTENANCE, new LinkedHashSet<>(
                Arrays.asList(AttributeStatus.CALIBRATION, AttributeStatus.MAINTENANCE)));
        assertEquals(new LinkedHashSet<>(Arrays.asList(
                AttributeStatus.CALIBRATION, AttributeStatus.MAINTENANCE)), attr.getState().getStatuses());

        assertTrue(attr.setStatus(AttributeStatus.MAINTENANCE));

        assertEquals(AttributeStatus.MAINTENANCE, attr.getState().getStatus());
        assertEquals(Collections.singleton(AttributeStatus.MAINTENANCE), attr.getState().getStatuses());
        assertEquals(Double.valueOf(5.0), attr.getState().getValue());
    }

    @Test
    public void c2_twoArgSetStatusDeclaresFrameWithoutChangingValue() {
        StubAttr attr = new StubAttr("c2");
        attr.setDevice(newStubDevice());
        attr.updateValue(6.0, AttributeStatus.NORMAL, Collections.singleton(AttributeStatus.NORMAL));

        Set<AttributeStatus> frame = new LinkedHashSet<>(
                Arrays.asList(AttributeStatus.CALIBRATION, AttributeStatus.ALARM));
        assertTrue(attr.setStatus(AttributeStatus.ALARM, frame));

        assertEquals(Double.valueOf(6.0), attr.getState().getValue());
        assertEquals(AttributeStatus.ALARM, attr.getState().getStatus());
        assertEquals(frame, attr.getState().getStatuses());

        Set<AttributeStatus> missingMain = new LinkedHashSet<>(
                Arrays.asList(AttributeStatus.CALIBRATION, AttributeStatus.ALARM));
        assertThrows(IllegalArgumentException.class,
                () -> attr.setStatus(AttributeStatus.NORMAL, missingMain));
    }

    @Test
    public void d1_publicStatePublishesOnlyLastCompleteFrameAndCommitsIt() {
        BusRegistry bus = mock(BusRegistry.class);
        StubAttr attr = newPublishableAttr(bus);

        attr.updateValue(1.0, AttributeStatus.CALIBRATION, new LinkedHashSet<>(
                Arrays.asList(AttributeStatus.CALIBRATION, AttributeStatus.ALARM)));
        attr.updateValue(3.0, AttributeStatus.NORMAL, Collections.singleton(AttributeStatus.NORMAL));
        assertTrue(attr.publicState());

        ArgumentCaptor<BusEvent<?>> captor = ArgumentCaptor.forClass(BusEvent.class);
        verify(bus, times(1)).publish(captor.capture());
        DeviceDataChangedEvent event = (DeviceDataChangedEvent) captor.getValue().getPayload();
        assertEquals(Collections.singleton(AttributeStatus.NORMAL), event.getNewState().getStatuses());
        assertEquals(Double.valueOf(3.0), event.getNewState().getValue());

        assertFalse(attr.isValueUpdated());
        // publicState 成功后 midState 已清空，可见态即已提交 lastState。
        assertEquals(Collections.singleton(AttributeStatus.NORMAL), attr.getState().getStatuses());
    }

    @Test
    public void d2_inFlightStateIsVisibleBeforePublish() {
        StubAttr attr = new StubAttr("d2");
        attr.setDevice(newStubDevice());

        attr.updateValue(2.0, AttributeStatus.ALARM, new LinkedHashSet<>(
                Arrays.asList(AttributeStatus.CALIBRATION, AttributeStatus.ALARM)));

        assertEquals(new LinkedHashSet<>(Arrays.asList(
                AttributeStatus.CALIBRATION, AttributeStatus.ALARM)), attr.getState().getStatuses());
        assertEquals(AttributeStatus.ALARM, attr.getState().getStatus());
        assertEquals(Double.valueOf(2.0), attr.getState().getValue());
    }

    @Test
    public void d3_valueOnlyUpdateLeavesFrameSnapshotOrthogonal() {
        StubAttr attr = new StubAttr("d3");
        attr.setDevice(newStubDevice());
        Set<AttributeStatus> frame = new LinkedHashSet<>(
                Arrays.asList(AttributeStatus.CALIBRATION, AttributeStatus.ALARM));
        attr.updateValue(7.0, AttributeStatus.ALARM, frame);

        attr.updateValue(8.0);

        assertEquals(Double.valueOf(8.0), attr.getState().getValue());
        assertEquals(AttributeStatus.ALARM, attr.getState().getStatus());
        assertEquals(frame, attr.getState().getStatuses());
    }

    /**
     * E1 并发对撞（设计矩阵）：轮询线程交替写两种完整帧（A={CALIBRATION,ALARM}/main=ALARM、
     * B={NORMAL}/main=NORMAL），校验线程并发读在途快照。撕裂不变量：任一时刻可见的
     * statuses 必须是 A 或 B 的完整声明之一——绝不出现混合帧/缺失 main 的撕裂集
     * （midState 引用原子替换 + 临界区写入保证）。
     */
    @Test(timeout = 60_000)
    public void e1_concurrentFrameWritesNeverExposeTornSnapshot() throws Exception {
        StubAttr attr = new StubAttr("e1");
        attr.setDevice(newStubDevice());
        Set<AttributeStatus> frameA = new LinkedHashSet<>(
                Arrays.asList(AttributeStatus.CALIBRATION, AttributeStatus.ALARM));
        Set<AttributeStatus> frameB = Collections.singleton(AttributeStatus.NORMAL);

        final int rounds = 3000;
        Thread writer = new Thread(() -> {
            for (int i = 0; i < rounds; i++) {
                if ((i & 1) == 0) {
                    attr.updateValue((double) i, AttributeStatus.ALARM, frameA);
                } else {
                    attr.updateValue((double) i, AttributeStatus.NORMAL, frameB);
                }
            }
        }, "e1-frame-writer");
        AtomicBoolean stop = new AtomicBoolean(false);
        AtomicInteger tornFrames = new AtomicInteger();
        Thread checker = new Thread(() -> {
            while (!stop.get()) {
                AttrState<Double> state = attr.getState();
                if (state != null) {
                    Set<AttributeStatus> seen = state.getStatuses();
                    boolean isA = seen.equals(frameA);
                    boolean isB = seen.equals(frameB);
                    if (!isA && !isB) {
                        tornFrames.incrementAndGet();
                    }
                    // 自洽契约：status 必属于当前可见集合（A 帧 main=ALARM、B 帧 main=NORMAL）
                    if (!seen.contains(state.getStatus())) {
                        tornFrames.incrementAndGet();
                    }
                }
            }
        }, "e1-torn-checker");
        writer.start();
        checker.start();
        writer.join(30_000);
        stop.set(true);
        checker.join(10_000);

        assertEquals("并发写入不得暴露撕裂帧（混合集或 main 缺失）", 0, tornFrames.get());
        // 终态收敛：最后一轮（rounds-1 为奇数 → B 帧）
        assertEquals(frameB, attr.getState().getStatuses());
    }
}
