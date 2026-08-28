package com.ecat.core.State;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import com.ecat.core.Bus.BusRegistry;
import com.ecat.core.Bus.event.BusEvent;
import com.ecat.core.Device.DeviceBase;
import com.ecat.core.EcatCore;
import com.ecat.core.Utils.DynamicConfig.ConfigDefinition;
import com.ecat.core.I18n.I18nKeyPath;

/**
 * setValueWithIoBody（IO 写模板，与 setValue 并列的唯二主入口，19 号 v2 S3 写闸塌缩后
 * 形态）语义测试：ioBody = SDK 事务体（在调用线程同步执行，互斥/超时由事务自身承担），
 * 成功才收尾发布；失败/异常不发布不残留（commandFailed 记账 21 号起 counter 咽喉
 * 自持于 AttributeBase，经 commandFailedCount() 读，不依赖注册表/引擎/执行 API 装配）。
 * 确定性同步（future.get，禁 sleep）。
 */
public class SetValueWithIoBodyTest {

    private DeviceBase mockDevice;
    private EcatCore mockCore;
    private BusRegistry mockBus;
    private final List<BusEvent<?>> published = new ArrayList<>();
    private AutoCloseable mocks;

    /** 最小 Integer 属性：IO 载荷可注入（模拟集成覆写 setValue 收敛进 IO 写模板）。 */
    static class IoBodyAttr extends AttributeBase<Integer> {
        final AtomicInteger ioRuns = new AtomicInteger();
        volatile Callable<Boolean> ioBody = () -> Boolean.TRUE;
        IoBodyAttr() {
            super("io_body_attr", null, null, null, 0, false, true, (Function<AttrChangedCallbackParams<Integer>, CompletableFuture<Boolean>>) null);
        }
        @Override protected CompletableFuture<Boolean> setValue(Integer newValue) {
            return setValueWithIoBody(newValue, () -> {
                ioRuns.incrementAndGet();
                return ioBody.call();
            });
        }
        @Override public String getDisplayValue(UnitInfo toUnit) { return String.valueOf(value); }
        @Override protected Integer convertFromUnitImp(Integer v, UnitInfo u) { return v; }
        @Override public ConfigDefinition getValueDefinition() { return null; }
        @Override protected I18nKeyPath getI18nPrefixPath() { return new I18nKeyPath("state.io_body_attr.", ""); }
        @Override public AttributeType getAttributeType() { return AttributeType.UNKNOWN; }
        @Override public Double convertValueToUnit(Double v, UnitInfo f, UnitInfo t) { return v; }
    }

    @Before
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        mockDevice = mock(DeviceBase.class);
        mockCore = mock(EcatCore.class);
        mockBus = mock(BusRegistry.class);
        when(mockDevice.getId()).thenReturn("dev-1");
        when(mockDevice.isReady()).thenReturn(true);
        when(mockDevice.getCore()).thenReturn(mockCore);
        when(mockCore.getBusRegistry()).thenReturn(mockBus);
        doAnswer(inv -> {
            published.add(inv.getArgument(0));
            return null;
        }).when(mockBus).publish(any());
    }

    @After
    public void tearDown() throws Exception {
        mocks.close();
    }

    private IoBodyAttr newAttr() {
        IoBodyAttr attr = new IoBodyAttr();
        attr.setDevice(mockDevice);
        return attr;
    }

    // 设备离线（IO 载荷发帧失败返回 false）→ future=false、值不变、零发布、记账
    @Test
    public void ioFalseDoesNotPublishOrResidue() throws Exception {
        IoBodyAttr attr = newAttr();
        attr.ioBody = () -> Boolean.FALSE;
        attr.updateValue(80, AttributeStatus.NORMAL);
        int publishedBefore = published.size();
        long failedBefore = commandFailed();

        Boolean ok = attr.setDisplayValue("85").get(5, TimeUnit.SECONDS);

        assertFalse(ok);
        assertEquals("离线写不得残留新值", Integer.valueOf(80), attr.getState().getValue());
        assertEquals("离线写零发布", publishedBefore, published.size());
        awaitCommandFailed(failedBefore);
    }

    // 场景②：正常写成功 → 值更新并发布一次；IO 在调用线程执行（朴素组合）
    @Test
    public void ioTruePublishesNewValueOnCallerThread() throws Exception {
        IoBodyAttr attr = newAttr();
        attr.updateValue(80, AttributeStatus.NORMAL);
        int publishedBefore = published.size();

        Boolean ok = attr.setDisplayValue("85").get(5, TimeUnit.SECONDS);

        assertTrue(ok);
        assertEquals(Integer.valueOf(85), attr.getState().getValue());
        assertEquals(publishedBefore + 1, published.size());
        assertEquals("IO 事务体应在调用线程同步执行", 1, attr.ioRuns.get());
    }

    // 场景③：IO 事务体抛异常（SDK 事务失败/事务硬超时以异常浮出）→ future 异常完成、
    // 值不发布不残留、记账。挂死 IO 的有界性由事务自身硬超时承担（预算守卫已随闸退役）。
    @Test
    public void ioExceptionFailsFutureWithoutResidue() throws Exception {
        IoBodyAttr attr = newAttr();
        attr.ioBody = () -> { throw new IllegalStateException("transaction timeout"); };
        attr.updateValue(80, AttributeStatus.NORMAL);
        int publishedBefore = published.size();
        long failedBefore = commandFailed();

        try {
            attr.setDisplayValue("90").get(5, TimeUnit.SECONDS);
            fail("IO 异常应以异常完成 future");
        } catch (ExecutionException e) {
            assertEquals("transaction timeout", e.getCause().getMessage());
        }
        assertEquals("异常写不得发布新值", Integer.valueOf(80), attr.getState().getValue());
        assertEquals(publishedBefore, published.size());
        awaitCommandFailed(failedBefore);
    }

    /** 咽喉 counter 读数（AttributeBase 静态自持，进程级恒可读，同 JVM 测试序共享累积）。 */
    private static long commandFailed() {
        return AttributeBase.commandFailedCount();
    }

    /** commandFailed 在 accountedWrite 的 whenComplete（postComplete 回调）里记账，与
     * 本线程被唤醒的顺序无 happens-before——用有界等待观察到计数到位（禁 sleep；
     * 差值口径防跨用例静态计数累积）。 */
    private static void awaitCommandFailed(long before) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (commandFailed() < before + 1) {
            assertTrue("失败写必须记账 commandFailed（5s 内）", System.nanoTime() < deadline);
            Thread.yield();
        }
    }
}
