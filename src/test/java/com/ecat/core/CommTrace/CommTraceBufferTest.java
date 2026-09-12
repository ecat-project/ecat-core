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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * CommTraceBuffer 环语义与过滤/增量查询测试（确定性同步，无 sleep）。
 */
public class CommTraceBufferTest {

    private CommTraceBuffer buffer;

    @Before
    public void setUp() {
        buffer = new CommTraceBuffer(8);
    }

    @After
    public void tearDown() {
        buffer.close();
    }

    private void fill(String port, int n) {
        for (int i = 0; i < n; i++) {
            buffer.tx(CommTraceTransport.SERIAL, port, new byte[]{(byte) i}, null);
        }
    }

    @Test
    public void ringEvictsOldestAndKeepsSeqOrder() {
        fill("p1", 12); // capacity 8 → 只剩 seq 5..12
        List<CommTraceEvent> all = buffer.query(CommTraceFilter.all(), 100, 0);
        assertEquals("满弃旧后只剩 8 条", 8, all.size());
        assertEquals("最旧存活 seq=5", 5, all.get(0).getSeq());
        assertEquals("最新 seq=12", 12, all.get(all.size() - 1).getSeq());
        for (int i = 1; i < all.size(); i++) {
            assertTrue("seq 升序", all.get(i).getSeq() > all.get(i - 1).getSeq());
        }
    }

    @Test
    public void filterByTransportPortDir() {
        buffer.tx(CommTraceTransport.SERIAL, "p1", new byte[]{1}, null);
        buffer.rx(CommTraceTransport.SERIAL, "p1", new byte[]{2}, null, null);
        buffer.tx(CommTraceTransport.MODBUS_TCP, "10.0.0.1:502", new byte[]{3}, null);
        buffer.rx(CommTraceTransport.TCP_CLIENT, "h:9000", new byte[]{4}, null, 1.5);

        assertEquals(1, buffer.query(new CommTraceFilter(CommTraceTransport.MODBUS_TCP, null, null, null, null), 10, 0).size());
        assertEquals(2, buffer.query(new CommTraceFilter(CommTraceTransport.SERIAL, "p1", null, null, null), 10, 0).size());
        assertEquals(1, buffer.query(new CommTraceFilter(CommTraceTransport.SERIAL, "p1", null, CommTraceDirection.RX, null), 10, 0).size());
        assertEquals("port 维过滤串台为零",
                0, buffer.query(new CommTraceFilter(CommTraceTransport.SERIAL, "pX", null, null, null), 10, 0).size());
    }

    @Test
    public void filterByQuerySubstringMatchesAsciiAndPortAndDevice() {
        buffer.tx(CommTraceTransport.TCP_CLIENT, "host-a:9000", "OK\r\n".getBytes(), null);
        buffer.tx(CommTraceTransport.TCP_CLIENT, "host-b:9000", "ERR".getBytes(), null);

        CommTraceFilter q = new CommTraceFilter(null, null, null, null, "ok");
        assertEquals("q=ok 匹配 ascii payload（大小写不敏感）", 1, buffer.query(q, 10, 0).size());

        CommTraceFilter qp = new CommTraceFilter(null, null, null, null, "host-a");
        assertEquals("q=host-a 匹配 portId", 1, buffer.query(qp, 10, 0).size());
    }

    @Test
    public void sinceCursorReturnsOnlyNewFrames() {
        fill("p1", 3);
        long cursor = buffer.query(CommTraceFilter.all(), 10, 0).get(2).getSeq(); // seq=3
        fill("p1", 2);
        List<CommTraceEvent> delta = buffer.query(CommTraceFilter.all(), 10, cursor);
        assertEquals("since=3 只返回 seq 4,5", 2, delta.size());
        assertEquals(4, delta.get(0).getSeq());
        assertEquals(5, delta.get(1).getSeq());
    }

    @Test
    public void limitTakesNewestTail() {
        fill("p1", 5);
        List<CommTraceEvent> tail = buffer.query(CommTraceFilter.all(), 2, 0);
        assertEquals(2, tail.size());
        assertEquals("取最新 2 条", 4, tail.get(0).getSeq());
    }

    @Test
    public void payloadTruncatedAt256AndOriginalLengthKept() {
        byte[] big = new byte[1000];
        for (int i = 0; i < big.length; i++) {
            big[i] = (byte) i;
        }
        buffer.tx(CommTraceTransport.SERIAL, "p1", big, null);
        CommTraceEvent e = buffer.query(CommTraceFilter.all(), 1, 0).get(0);
        assertEquals("截断到 256B", 256, e.getPayload().length);
        assertEquals("原始全长保留", 1000, e.getOriginalLength());
        assertTrue(e.isTruncated());
    }

    @Test
    public void txWithIndependentLengthTruncatesAndKeepsOriginalLength() {
        // HTTP 埋点形态：编码器为避免整包拷贝只填前缀字节（前缀覆盖 min(length,256) 契约），
        // 真实全长经 length 传入（与 rx(data, length) 同语义）——isTruncated 按 length 判定
        byte[] prefix = new byte[256];
        System.arraycopy("GET /api HTTP/1.1".getBytes(), 0, prefix, 0, 17);
        buffer.tx(CommTraceTransport.HTTP, "h:80", prefix, 5000, "http-1");
        CommTraceEvent e = buffer.query(CommTraceFilter.all(), 1, 0).get(0);
        assertEquals("payload 截断到上限 256", 256, e.getPayload().length);
        assertEquals("原始全长按 length 记账", 5000, e.getOriginalLength());
        assertTrue("全长大于前缀须标记截断", e.isTruncated());

        // 全长小于上限时前缀即全部（不截断）
        byte[] small = "POST /x HTTP/1.1".getBytes();
        buffer.tx(CommTraceTransport.HTTP, "h:80", small, small.length, "http-2");
        CommTraceEvent e2 = buffer.query(CommTraceFilter.all(), 1, 0).get(0);
        assertEquals(small.length, e2.getPayload().length);
        assertFalse(e2.isTruncated());
    }

    @Test
    public void httpTransportPairGroupedInOverview() {
        buffer.tx(CommTraceTransport.HTTP, "10.0.0.5:80", new byte[]{1}, "http-1");
        buffer.rx(CommTraceTransport.HTTP, "10.0.0.5:80", new byte[]{2}, "http-1", 12.5);
        buffer.error(CommTraceTransport.HTTP, "10.0.0.5:80");

        Map<String, Object> overview = buffer.overview();
        @SuppressWarnings("unchecked")
        Map<String, Object> transports = (Map<String, Object>) overview.get("transports");
        assertNotNull("HTTP 传输组存在（枚举值可捕获可分组）", transports.get("HTTP"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ports =
                (List<Map<String, Object>>) ((Map<String, Object>) transports.get("HTTP")).get("ports");
        assertEquals(1, ports.size());
        Map<String, Object> port = ports.get(0);
        assertEquals("10.0.0.5:80", port.get("port"));
        assertEquals(1L, port.get("txCount"));
        assertEquals(1L, port.get("rxCount"));
        assertEquals(1L, port.get("errorCount"));
    }

    @Test
    public void getFrameReturnsEventOrNull() {
        fill("p1", 3);
        assertNotNull("环内 seq 可查", buffer.getFrame(2));
        assertNull("环外 seq 返 null", buffer.getFrame(1 + 100));
    }

    @Test
    public void errorAndOverviewCounters() {
        buffer.tx(CommTraceTransport.SERIAL, "p1", new byte[]{1}, null);
        buffer.rx(CommTraceTransport.SERIAL, "p1", new byte[]{2}, null, null);
        buffer.error(CommTraceTransport.SERIAL, "p1");
        buffer.error(CommTraceTransport.SERIAL, "p1");

        Map<String, Object> overview = buffer.overview();
        @SuppressWarnings("unchecked")
        Map<String, Object> serial =
                (Map<String, Object>) overview.get("transports");
        assertNotNull("SERIAL 组存在", serial.get("SERIAL"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> ports =
                (List<Map<String, Object>>) ((Map<String, Object>) serial.get("SERIAL")).get("ports");
        assertEquals(1, ports.size());
        Map<String, Object> p1 = ports.get(0);
        assertEquals(1L, p1.get("txCount"));
        assertEquals(1L, p1.get("rxCount"));
        assertEquals(2L, p1.get("errorCount"));
    }

    @Test
    public void concurrentAppendNoLostOrDuplicateSlots() throws Exception {
        int threads = 4;
        int perThread = 500;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        AtomicInteger unexpected = new AtomicInteger();
        for (int t = 0; t < threads; t++) {
            final String port = "p" + t;
            pool.execute(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        buffer.tx(CommTraceTransport.SERIAL, port, new byte[]{(byte) i}, null);
                    }
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
        assertEquals("总写入计数精确", threads * perThread, buffer.latestSeq());
        // 槽唯一性：环内（capacity=8 已弃旧）seq 无重复
        List<CommTraceEvent> all = buffer.query(CommTraceFilter.all(), Integer.MAX_VALUE, 0);
        Set<Long> seqs = new HashSet<>();
        for (CommTraceEvent e : all) {
            assertTrue("seq 无重复（槽位原子分配）", seqs.add(e.getSeq()));
        }
    }

    @Test
    public void appendHotPathMicroBenchmark() {
        // 性能自证：200 帧/s 预算下单次 append 耗时（ns 级，输出供汇报；断言只做宽松上界防回归）
        int n = 200_000;
        CommTraceBuffer bench = new CommTraceBuffer(n);
        byte[] payload = new byte[64];
        long start = System.nanoTime();
        for (int i = 0; i < n; i++) {
            bench.tx(CommTraceTransport.SERIAL, "bench", payload, null);
        }
        long elapsed = System.nanoTime() - start;
        long nsPerAppend = elapsed / n;
        System.out.println("[CommTraceBenchmark] append avg = " + nsPerAppend + " ns/op ("
                + (nsPerAppend / 1000.0) + " us/op), throughput = "
                + (1_000_000_000L / nsPerAppend) + " frames/s");
        // 单次 append 微秒级：200 帧/s 占比 < 0.1% 单核；宽松上界 100us 防退化
        assertTrue("单次 append 须 < 100us（实测 " + nsPerAppend + "ns）", nsPerAppend < 100_000);
        bench.close();
    }
}
