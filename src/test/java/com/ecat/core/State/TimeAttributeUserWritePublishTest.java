package com.ecat.core.State;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoAnnotations;

import com.ecat.core.Bus.BusRegistry;
import com.ecat.core.Bus.event.BusEvent;
import com.ecat.core.Device.DeviceBase;
import com.ecat.core.EcatCore;

/**
 * TimeAttribute 用户侧写可见性（22 号 setValue final 化 W2，D-22-7 ②）：用户经
 * setDisplayValue 改时间后，变更必须即时 publicState——不得滞留在途 midState 等下一轮询。
 *
 * <p>被测因果链一环：本地写值（updateValue）与总线可见（publicState）在用户写路径上
 * 必须成对出现。此前 TimeAttribute.setDisplayValueImp 只 updateValue 不 publicState，
 * 下游（总线消费方/绑定属性）对一个已发生的用户设置零感知。确定性同步（future.get，禁 sleep）。
 */
public class TimeAttributeUserWritePublishTest {

    private DeviceBase mockDevice;
    private EcatCore mockCore;
    private BusRegistry mockBus;
    private final List<BusEvent<?>> published = new ArrayList<>();
    private AutoCloseable mocks;

    @Before
    public void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        mockDevice = mock(DeviceBase.class);
        mockCore = mock(EcatCore.class);
        mockBus = mock(BusRegistry.class);
        when(mockDevice.getId()).thenReturn("time-dev-1");
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

    private TimeAttribute newAttr() {
        TimeAttribute attr = new TimeAttribute("filter_change_time", null, null, null, 0, false, true);
        attr.setDevice(mockDevice);
        return attr;
    }

    /** 用户写成功 → 值更新且总线发布恰一次（写后即见，不等轮询兜回）。 */
    @Test
    public void userWritePublishesImmediately() throws Exception {
        TimeAttribute attr = newAttr();
        Instant before = Instant.now().minusSeconds(3600);
        attr.updateValue(before, AttributeStatus.NORMAL);
        int publishedBefore = published.size();

        Boolean ok = attr.setDisplayValue("2026-08-29 10:00:00").get(5, TimeUnit.SECONDS);

        assertTrue("合法时间串写应成功", ok);
        ZonedDateTime expected = ZonedDateTime.of(2026, 8, 29, 10, 0, 0, 0, ZoneId.systemDefault());
        assertEquals("本地值应为用户设置的时间", expected.toInstant(), attr.getState().getValue());
        assertEquals("用户写后必须即时 publicState（总线可见）", publishedBefore + 1, published.size());
    }

    /** 非法时间串 → 异常 future、值不变、零发布（写入口族既有契约，回归护栏）。 */
    @Test
    public void illegalTimeStringFailsWithoutPublish() throws Exception {
        TimeAttribute attr = newAttr();
        Instant before = Instant.now().minusSeconds(3600);
        attr.updateValue(before, AttributeStatus.NORMAL);
        int publishedBefore = published.size();

        try {
            attr.setDisplayValue("not-a-time").get(5, TimeUnit.SECONDS);
            org.junit.Assert.fail("非法时间串应以异常完成 future");
        } catch (java.util.concurrent.ExecutionException expected) {
            // 用户输入问题：以 IllegalArgumentException 包装透传
        }
        assertEquals("非法写不得变更本地值", before, attr.getState().getValue());
        assertEquals("非法写零发布", publishedBefore, published.size());
    }
}
