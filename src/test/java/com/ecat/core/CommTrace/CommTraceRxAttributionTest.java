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

package com.ecat.core.CommTrace;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.MDC;

import com.ecat.core.Utils.Mdc.MdcContext;

/**
 * RX 设备归属回填测试（工单 G）：serial/tcp 的 RX 帧在读线程（sweeper/selector）捕获，
 * 线程 MDC 无设备键——按「本口最近一次带设备键 TX + 新鲜度窗口」回填。
 *
 * <p>风险锁点：串口源锁纪律下同口至多一笔在飞事务，回填=归属成立；但无 TX/窗口过期/
 * 无键 TX 不得污染（如实为 null）。时钟经测试缝注入（递增序列），无真实时间等待。
 */
public class CommTraceRxAttributionTest {

    /** 测试缝时钟：可控递增微秒时间（默认步进 0，TX/RX 同刻）。 */
    private final AtomicLong clockMicros = new AtomicLong(1_000_000L);

    private CommTraceBuffer buffer;

    @Before
    public void setUp() {
        buffer = new CommTraceBuffer(64, 30_000L, clockMicros::get);
    }

    @After
    public void tearDown() {
        buffer.close();
        MDC.clear();
    }

    private void withDeviceMdc(String deviceId, String deviceName, String coordinate) {
        MDC.put(CommTraceBuffer.MDC_DEVICE_ID_KEY, deviceId);
        MDC.put(CommTraceBuffer.MDC_DEVICE_NAME_KEY, deviceName);
        MDC.put(MdcContext.INTEGRATION_COORDINATE_KEY, coordinate);
    }

    private CommTraceEvent lastFrame() {
        List<CommTraceEvent> all = buffer.query(CommTraceFilter.all(), 1, 0);
        assertEquals("环内应有帧", 1, all.size());
        return all.get(0);
    }

    @Test
    public void rxBackfillsDeviceFieldsFromLastAttributedTxOnSamePort() {
        withDeviceMdc("dev-1", "一号设备", "com.ecat:integration-a");
        buffer.tx(CommTraceTransport.SERIAL, "/dev/ttyUSB10", new byte[]{1}, null);
        MDC.clear(); // RX 在 sweeper 线程：无任何设备键

        buffer.rx(CommTraceTransport.SERIAL, "/dev/ttyUSB10", new byte[]{2}, null, null);

        CommTraceEvent rx = lastFrame();
        assertEquals("RX 回填 deviceId", "dev-1", rx.getDeviceId());
        assertEquals("RX 回填 deviceName", "一号设备", rx.getDeviceName());
        assertEquals("RX 回填 coordinate", "com.ecat:integration-a", rx.getCoordinate());
    }

    @Test
    public void rxWithoutAnyTxStaysNull() {
        buffer.rx(CommTraceTransport.SERIAL, "/dev/ttyUSB11", new byte[]{1}, null, null);
        CommTraceEvent rx = lastFrame();
        assertNull("无 TX 可配对，设备字段如实为 null", rx.getDeviceId());
        assertNull(rx.getDeviceName());
        assertNull(rx.getCoordinate());
    }

    @Test
    public void rxAfterWindowExpiryStaysNull() {
        withDeviceMdc("dev-1", "一号设备", "com.ecat:integration-a");
        buffer.tx(CommTraceTransport.SERIAL, "/dev/ttyUSB12", new byte[]{1}, null);
        MDC.clear();

        clockMicros.addAndGet(30_001L * 1000L); // 窗口 30s + 1ms：TX 上下文已陈旧
        buffer.rx(CommTraceTransport.SERIAL, "/dev/ttyUSB12", new byte[]{2}, null, null);

        CommTraceEvent rx = lastFrame();
        assertNull("窗口过期不回填（陈旧 TX 归属不可靠）", rx.getDeviceId());
        assertNull(rx.getDeviceName());
    }

    @Test
    public void unattributedTxDoesNotOverwriteLastDeviceContext() {
        withDeviceMdc("dev-1", "一号设备", "com.ecat:integration-a");
        buffer.tx(CommTraceTransport.SERIAL, "/dev/ttyUSB13", new byte[]{1}, null);
        MDC.clear();
        buffer.tx(CommTraceTransport.SERIAL, "/dev/ttyUSB13", new byte[]{3}, null); // 无键 TX（如手动命令路径）
        buffer.rx(CommTraceTransport.SERIAL, "/dev/ttyUSB13", new byte[]{2}, null, null);

        CommTraceEvent rx = lastFrame();
        assertEquals("无键 TX 不覆盖本口已记录的设备上下文", "dev-1", rx.getDeviceId());
        assertEquals("一号设备", rx.getDeviceName());
    }

    @Test
    public void rxDirectMdcWinsOverBackfill() {
        withDeviceMdc("dev-1", "一号设备", "com.ecat:integration-a");
        buffer.tx(CommTraceTransport.SERIAL, "/dev/ttyUSB14", new byte[]{1}, null);

        // 本线程自带另一设备的键（modbus 事务层形态）：直接继承优先，不被回填覆盖
        withDeviceMdc("dev-2", "二号设备", "com.ecat:integration-a");
        buffer.rx(CommTraceTransport.SERIAL, "/dev/ttyUSB14", new byte[]{2}, null, null);

        CommTraceEvent rx = lastFrame();
        assertEquals("线程自有键优先于回填", "dev-2", rx.getDeviceId());
        assertEquals("二号设备", rx.getDeviceName());
    }

    @Test
    public void backfillDoesNotCrossPorts() {
        withDeviceMdc("dev-1", "一号设备", "com.ecat:integration-a");
        buffer.tx(CommTraceTransport.SERIAL, "/dev/ttyUSB15", new byte[]{1}, null);
        MDC.clear();

        buffer.rx(CommTraceTransport.SERIAL, "/dev/ttyUSB16", new byte[]{2}, null, null);

        CommTraceEvent rx = lastFrame();
        assertNull("异口 RX 不串台回填", rx.getDeviceId());
    }

    @Test
    public void concurrentMultiPortAppendEachPortAttributionCorrect() throws Exception {
        int ports = 4;
        ExecutorService pool = Executors.newFixedThreadPool(ports);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(ports);
        AtomicInteger unexpected = new AtomicInteger();
        for (int t = 0; t < ports; t++) {
            final int idx = t;
            final String port = "/dev/ttyUSB2" + idx;
            final String deviceId = "dev-" + idx;
            pool.execute(() -> {
                try {
                    start.await();
                    withDeviceMdc(deviceId, "设备" + idx, "com.ecat:integration-a");
                    buffer.tx(CommTraceTransport.SERIAL, port, new byte[]{1}, null);
                    MDC.clear();
                    buffer.rx(CommTraceTransport.SERIAL, port, new byte[]{2}, null, null);
                } catch (Throwable e) {
                    unexpected.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(30, TimeUnit.SECONDS));
        pool.shutdownNow();

        assertEquals("无未预期异常", 0, unexpected.get());
        List<CommTraceEvent> all = buffer.query(CommTraceFilter.all(), Integer.MAX_VALUE, 0);
        assertEquals(ports * 2, all.size());
        for (CommTraceEvent e : all) {
            String expectedDevice = "dev-" + e.getPortId().substring(e.getPortId().length() - 1);
            assertEquals("口内 TX/RX 归属本口设备（并发下不串台）", expectedDevice, e.getDeviceId());
        }
    }
}
