package com.ecat.core.State;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
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
 * attr 写路径朴素组合语义测试（19 号 v2 S3 写闸塌缩后形态，接替原
 * AttributeWriteCommandGateTest 的仍有效断言）：写 = IO 载荷朴素 CF 组合 +
 * 确认后收尾（成功才 updateValue+publicState，失败/false 不发布不残留），
 * IO 在调用线程执行（互斥/超时由载荷自身 SDK 事务承担），accountedWrite
 * 保留 commandFailed 记账（21 号起 counter 咽喉自持于 AttributeBase，
 * 经 commandFailedCount() 读，不再依赖注册表/引擎/执行 API 装配）。
 * 确定性同步（latch/future.get，禁 sleep）。
 */
public class AttributeWritePlainCompositionTest {

    private AttributeClass attrClass;
    private DeviceBase mockDevice;
    private EcatCore mockCore;
    private BusRegistry mockBus;
    private final List<BusEvent<?>> published = new ArrayList<>();
    private AutoCloseable mocks;

    /** 与被测属性同包的最小 Integer 属性：IO callback 可注入。 */
    static class PlainAttr extends AttributeBase<Integer> {
        PlainAttr(Function<AttrChangedCallbackParams<Integer>, CompletableFuture<Boolean>> cb) {
            super("plain_attr", null, null, null, 0, false, true, cb);
        }
        @Override public String getDisplayValue(UnitInfo toUnit) { return String.valueOf(value); }
        @Override protected Integer convertFromUnitImp(Integer v, UnitInfo u) { return v; }
        @Override public ConfigDefinition getValueDefinition() { return null; }
        @Override protected I18nKeyPath getI18nPrefixPath() { return new I18nKeyPath("state.plain_attr.", ""); }
        @Override public AttributeType getAttributeType() { return AttributeType.UNKNOWN; }
        @Override public Double convertValueToUnit(Double v, UnitInfo f, UnitInfo t) { return v; }
    }

    /** 最小 CommandAttribute（包内可见抽象类，同包可继承）。 */
    static class PlainCommandAttr extends CommandAttribute<String> {
        final AtomicReference<String> ioThread = new AtomicReference<>();
        CompletableFuture<Boolean> result = CompletableFuture.completedFuture(true);
        PlainCommandAttr() {
            super("plain_cmd", null, Arrays.asList("on", "off"), null);
        }
        @Override protected CompletableFuture<Boolean> sendCommandImpl(String cmd) {
            ioThread.set(Thread.currentThread().getName());
            return result;
        }
        @Override public String getDisplayValue(UnitInfo toUnit) { return value; }
        @Override protected String convertFromUnitImp(String v, UnitInfo u) { return v; }
        @Override public ConfigDefinition getValueDefinition() { return null; }
        @Override public I18nKeyPath getI18nPrefixPath() { return new I18nKeyPath("state.plain_cmd.", ""); }
        @Override public AttributeType getAttributeType() { return AttributeType.UNKNOWN; }
        @Override public Map<String, String> getOptionDict() { return java.util.Collections.emptyMap(); }
        @Override protected String parseCommandValue(String text) { return text; }
    }

    /** 最小 SelectAttribute。 */
    static class PlainSelectAttr extends SelectAttribute<String> {
        PlainSelectAttr() {
            super("plain_sel", null, true, Arrays.asList("low", "high"));
        }
        @Override protected CompletableFuture<Boolean> selectOptionImp(String option) {
            return CompletableFuture.completedFuture(true);
        }
        @Override public String getDisplayValue(UnitInfo toUnit) { return value; }
        @Override protected String convertFromUnitImp(String v, UnitInfo u) { return v; }
        @Override public ConfigDefinition getValueDefinition() { return null; }
        @Override public I18nKeyPath getI18nPrefixPath() { return new I18nKeyPath("state.plain_sel.", ""); }
        @Override public AttributeType getAttributeType() { return AttributeType.UNKNOWN; }
        @Override public Map<String, String> getOptionDict() { return java.util.Collections.emptyMap(); }
    }

    @Before
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        mockDevice = mock(DeviceBase.class);
        mockCore = mock(EcatCore.class);
        mockBus = mock(BusRegistry.class);
        attrClass = mock(AttributeClass.class);
        when(attrClass.isValidUnit(any())).thenReturn(true);
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

    private PlainAttr newAttr(Function<AttrChangedCallbackParams<Integer>, CompletableFuture<Boolean>> cb) {
        PlainAttr attr = new PlainAttr(cb);
        attr.setDevice(mockDevice);
        return attr;
    }

    private static void await(CountDownLatch latch) throws InterruptedException {
        assertTrue("latch 未在期限内放开（5s）", latch.await(5, TimeUnit.SECONDS));
    }

    // 确认后更新核心语义①：写失败（callback false）不发布、状态不残留、future=false、记账
    @Test
    public void writeFalseDoesNotPublishOrResidue() throws Exception {
        PlainAttr attr = newAttr(p -> CompletableFuture.completedFuture(false));
        attr.updateValue(1, AttributeStatus.NORMAL);
        int publishedBefore = published.size();
        long failedBefore = commandFailed();

        Boolean ok = attr.setDisplayValue("2").get(5, TimeUnit.SECONDS);

        assertFalse(ok);
        assertEquals("值不得残留乐观更新", Integer.valueOf(1), attr.getState().getValue());
        assertEquals("写失败不得发布事件", publishedBefore, published.size());
        awaitCommandFailed(failedBefore, 1L);
    }

    // 确认后更新核心语义②：写异常路径同语义 + 异常以 future 完成（不抛穿调用线程）
    @Test
    public void writeExceptionDoesNotPublishOrResidue() throws Exception {
        PlainAttr attr = newAttr(p -> failed(new RuntimeException("io down")));
        attr.updateValue(1, AttributeStatus.NORMAL);
        int publishedBefore = published.size();
        long failedBefore = commandFailed();

        try {
            attr.setDisplayValue("2").get(5, TimeUnit.SECONDS);
            fail("异常路径 future 应以异常完成");
        } catch (ExecutionException e) {
            assertThat(e.getCause().getMessage(), is("io down"));
        }
        assertEquals(Integer.valueOf(1), attr.getState().getValue());
        assertEquals(publishedBefore, published.size());
        awaitCommandFailed(failedBefore, 1L);
    }

    // IO 在调用线程执行（朴素组合：互斥/超时由载荷自身 SDK 事务承担，无 lane worker 搬运）
    @Test
    public void ioRunsOnCallerThread() throws Exception {
        AtomicReference<String> ioThread = new AtomicReference<>();
        CountDownLatch done = new CountDownLatch(1);
        PlainAttr attr = newAttr(p -> {
            ioThread.set(Thread.currentThread().getName());
            done.countDown();
            return CompletableFuture.completedFuture(true);
        });

        attr.setDisplayValue("5");
        await(done);

        assertEquals("IO 应在调用线程上执行（朴素组合）",
            Thread.currentThread().getName(), ioThread.get());
    }

    // 记账口径：false / 异常 两类失败 → commandFailed +2（原键缺失拒绝类随键路由消亡；
    // 注册表进程级共享，断言按前后差值——不假设绝对值起点）
    @Test
    public void accountingCountsAllFailureKinds() throws Exception {
        long failedBefore = commandFailed();
        PlainAttr falseAttr = newAttr(p -> CompletableFuture.completedFuture(false));
        falseAttr.setDisplayValue("1").get(5, TimeUnit.SECONDS);

        PlainAttr exAttr = newAttr(p -> failed(new RuntimeException("x")));
        swallowException(exAttr.setDisplayValue("1"));

        awaitCommandFailed(failedBefore, 2L);
    }

    // 入口族回归：sendCommand/selectOption/asyncTurnOn 朴素组合——成功才发布 + 各发布一次
    @Test
    public void entryFamilyPublishesOnlyOnConfirmedSuccess() throws Exception {
        PlainCommandAttr cmd = new PlainCommandAttr();
        cmd.setDevice(mockDevice);
        assertTrue(cmd.sendCommand("on").get(5, TimeUnit.SECONDS));
        assertEquals("on", cmd.getValue());
        assertEquals("sendCommand IO 在调用线程执行（朴素组合）",
            Thread.currentThread().getName(), cmd.ioThread.get());

        PlainSelectAttr sel = new PlainSelectAttr();
        sel.setDevice(mockDevice);
        assertTrue(sel.selectOption("high").get(5, TimeUnit.SECONDS));
        assertEquals("high", sel.getValue());

        BinaryAttribute bin = new BinaryAttribute("plain_bin", attrClass, true, null) {
            @Override protected CompletableFuture<Boolean> asyncTurnOnImpl() {
                return CompletableFuture.completedFuture(true);
            }
        };
        bin.setDevice(mockDevice);
        assertTrue(bin.asyncTurnOn().get(5, TimeUnit.SECONDS));
        assertEquals(Boolean.TRUE, bin.getValue());
        assertEquals("三入口族各发布一次", 3, published.size());
    }

    // 入口族失败不发布（确认语义防退化）
    @Test
    public void entryFamilyFailureDoesNotPublish() throws Exception {
        PlainCommandAttr cmd = new PlainCommandAttr();
        cmd.result = CompletableFuture.completedFuture(false);
        cmd.setDevice(mockDevice);
        int before = published.size();
        assertFalse(cmd.sendCommand("on").get(5, TimeUnit.SECONDS));
        assertNull(cmd.getValue());
        assertEquals(before, published.size());
    }

    // impl 同步抛 → 异常 future 原样透传（不误标为类型转换失败；写入口族契约=不抛穿调用线程）
    @Test
    public void implSyncThrowPassesThroughAsExceptionFuture() throws Exception {
        PlainAttr attr = new PlainAttr(null) {
            @Override protected CompletableFuture<Boolean> setDisplayValueImp(Integer v, UnitInfo u) {
                throw new RuntimeException("impl boom");
            }
        };
        attr.setDevice(mockDevice);
        attr.updateValue(1, AttributeStatus.NORMAL);
        try {
            attr.setDisplayValue("2").get(5, TimeUnit.SECONDS);
            fail("impl 同步抛应以异常 future 完成");
        } catch (ExecutionException e) {
            assertThat("异常原样透传，不得包装为类型转换失败",
                e.getCause().getMessage(), is("impl boom"));
        }
        assertEquals("impl 抛出后值不得残留", Integer.valueOf(1), attr.getState().getValue());
    }

    // Binary 早期 !valueChangeable 门（与 sendCommand/selectOption 既有门对齐）：不可变更属性
    // 直接拒绝——IO（asyncTurnOnImpl/asyncTurnOffImpl）零发起、值不写入、不发布
    @Test
    public void binaryNonChangeableGateSkipsIo() throws Exception {
        AtomicBoolean ioInitiated = new AtomicBoolean();
        BinaryAttribute locked = new BinaryAttribute("locked_bin", attrClass, false, null) {
            @Override protected CompletableFuture<Boolean> asyncTurnOnImpl() {
                ioInitiated.set(true);
                return CompletableFuture.completedFuture(true);
            }
            @Override protected CompletableFuture<Boolean> asyncTurnOffImpl() {
                ioInitiated.set(true);
                return CompletableFuture.completedFuture(true);
            }
        };
        locked.setDevice(mockDevice);
        int before = published.size();
        assertFalse(locked.asyncTurnOn().get(5, TimeUnit.SECONDS));
        assertFalse(locked.asyncTurnOff().get(5, TimeUnit.SECONDS));
        assertFalse("不可变更属性不得发起 IO", ioInitiated.get());
        assertNull("值不得写入/残留", locked.getValue());
        assertEquals("不得发布事件", before, published.size());
    }

    // selectOption(option, publishState=false) 分支：IO 成功后值照常更新但不上总线
    // （publishState 布尔参语义经 confirmPublishTail 透传保留）
    @Test
    public void selectOptionWithoutPublishUpdatesValueButSkipsEvent() throws Exception {
        PlainSelectAttr sel = new PlainSelectAttr();
        sel.setDevice(mockDevice);
        int before = published.size();
        assertTrue(sel.selectOption("high", false).get(5, TimeUnit.SECONDS));
        assertEquals("值照常更新", "high", sel.getValue());
        assertEquals("publishState=false 不得发布事件", before, published.size());
    }

    // 调用方超时不取消：future.get 短超时后，IO future 完成时命令照常收尾并发布。
    // IO 用「返回在飞 CF」形态（朴素组合下发起段在调用线程执行、完成点=IO CF 完成点——
    // 若回调在调用线程同步 park 反而会死锁自己，生产 IO 载荷均为真异步形态）
    @Test
    public void callerTimeoutDoesNotCancelCommand() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        PlainAttr attr = newAttr(p -> {
            CompletableFuture<Boolean> io = new CompletableFuture<>();
            Thread ioWorker = new Thread(() -> {
                try {
                    release.await();
                    io.complete(true);
                } catch (InterruptedException e) {
                    io.completeExceptionally(e);
                }
            }, "plain-io-worker");
            ioWorker.setDaemon(true);
            ioWorker.start();
            return io;
        });
        CompletableFuture<Boolean> future = attr.setDisplayValue("7");
        try {
            future.get(200, TimeUnit.MILLISECONDS);
            fail("应超时");
        } catch (TimeoutException expected) {
            // 调用方超时≠命令取消
        }
        release.countDown();
        assertTrue(future.get(5, TimeUnit.SECONDS));
        assertEquals(Integer.valueOf(7), attr.getState().getValue());
        assertEquals(1, published.size());
    }

    // 无 core 形态：裸属性（getCore()==null，单测形态）照常工作；失败写记账不再依赖
    // core 可达性（计数咽喉自持于 AttributeBase 静态 counter——旧「裸属性跳过计数」的
    // 可达性限制随迁移消亡，失败写与生产同口径计数）
    @Test
    public void bareAttrWithoutCoreWorksAndStillAccounts() throws Exception {
        DeviceBase bareDevice = mock(DeviceBase.class);
        when(bareDevice.getId()).thenReturn("bare-1");
        when(bareDevice.isReady()).thenReturn(true);
        AtomicReference<String> ioThread = new AtomicReference<>();
        PlainAttr okAttr = new PlainAttr(p -> {
            ioThread.set(Thread.currentThread().getName());
            return CompletableFuture.completedFuture(true);
        });
        okAttr.setDevice(bareDevice); // getCore() 未 stub → null → 裸属性形态

        CompletableFuture<Boolean> future = okAttr.setDisplayValue("8");
        assertTrue("朴素组合应同步完成", future.isDone());
        assertTrue(future.get(5, TimeUnit.SECONDS));
        assertEquals("IO 在调用线程执行", Thread.currentThread().getName(), ioThread.get());
        assertEquals(Integer.valueOf(8), okAttr.getState().getValue());

        long failedBefore = commandFailed();
        PlainAttr failAttr = new PlainAttr(p -> CompletableFuture.completedFuture(false));
        failAttr.setDevice(bareDevice);
        assertFalse(failAttr.setDisplayValue("9").get(5, TimeUnit.SECONDS));
        awaitCommandFailed(failedBefore, 1L);
    }

    /** Java 8 无 CompletableFuture.failedFuture，测试内自建异常完成 future。 */
    private static CompletableFuture<Boolean> failed(Throwable t) {
        CompletableFuture<Boolean> f = new CompletableFuture<>();
        f.completeExceptionally(t);
        return f;
    }

    private static void swallowException(CompletableFuture<Boolean> future) {
        try {
            future.get(5, TimeUnit.SECONDS);
        } catch (Exception expected) {
            // 异常完成即计入
        }
    }

    /** 咽喉 counter 读数（AttributeBase 静态自持，进程级恒可读，同 JVM 测试序共享累积）。 */
    private static long commandFailed() {
        return AttributeBase.commandFailedCount();
    }

    /** commandFailed 记账在 whenComplete（postComplete 回调）里，与本线程被唤醒无
     * happens-before——用有界等待观察到计数到位（禁 sleep 猜测；差值口径防跨用例
     * 静态计数累积）。 */
    private static void awaitCommandFailed(long before, long delta) {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (commandFailed() < before + delta) {
            assertTrue("失败写必须记账 commandFailed（5s 内）", System.nanoTime() < deadline);
            Thread.yield();
        }
    }
}
