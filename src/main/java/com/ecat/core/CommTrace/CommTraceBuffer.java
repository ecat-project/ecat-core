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

import com.ecat.core.Utils.Mdc.MdcContext;
import org.slf4j.MDC;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 通讯帧全局有界环形缓冲（独立数据面，不经 logback）。
 *
 * <p>并发模型（单写多读、append 无锁）：
 * <ul>
 *   <li>环是预分配定长数组，槽位由 {@code AtomicLong getAndIncrement} 分配——多 IO 线程并发
 *       append 各占唯一槽，引用写入原子，无锁无 CAS 重试；满时新帧直接覆盖最旧槽（满弃旧）。</li>
 *   <li>seq = 槽序号 + 1，全局单调唯一，作为 SSE 重连游标（since_seq）。</li>
 *   <li>append 热路径除事件本身（含 payload 截断副本）外零分配；per-port 统计只在端口首次
 *       出现时分配一次（ConcurrentHashMap.computeIfAbsent）。</li>
 * </ul>
 *
 * <p>投递（SSE）：仿 {@link com.ecat.core.Log.LogBuffer} 的信号式单写者——append 只发信号，
 * 懒启动的投递线程读环增量、按订阅者过滤后 send；投递时不持任何 ecat 锁，慢订阅者由环容量
 * 兜底（丢最旧），不阻塞捕获热路径。
 *
 * <p>设备上下文：捕获线程的 MDC（引擎/组件层已设 {@code integration.coordinate}）；
 * MDC 无设备维度键，deviceId/deviceName 仅当上游显式设置 {@code device.id}/{@code device.name}
 * 时继承，拿不到为 null（如实）。
 *
 * <p>容量经 {@code ecat.commtrace.capacity} 系统属性配置，默认 50_000 帧（约
 * 50k × ~300B ≈ 15MB 稳态上限）。
 *
 * @author coffee
 */
public final class CommTraceBuffer implements AutoCloseable {

    /** MDC 设备维度键（上游按需设置；不设则事件设备字段为 null）。 */
    public static final String MDC_DEVICE_ID_KEY = "device.id";
    public static final String MDC_DEVICE_NAME_KEY = "device.name";

    private static final int DEFAULT_CAPACITY = 50_000;

    private static final class Holder {
        static final CommTraceBuffer INSTANCE = new CommTraceBuffer(
                Integer.getInteger("ecat.commtrace.capacity", DEFAULT_CAPACITY));
    }

    /** 全局单例（组件埋点静态直调入口）。 */
    public static CommTraceBuffer instance() {
        return Holder.INSTANCE;
    }

    /** 单端口统计（overview 数据源；字段全 AtomicLong/volatile，append 热路径只做递增）。 */
    public static final class PortStats {
        final AtomicLong txCount = new AtomicLong();
        final AtomicLong rxCount = new AtomicLong();
        final AtomicLong errorCount = new AtomicLong();
        volatile long lastFrameMicros = 0L;

        void record(CommTraceDirection dir, long tsMicros) {
            (dir == CommTraceDirection.TX ? txCount : rxCount).incrementAndGet();
            lastFrameMicros = tsMicros;
        }
    }

    private final CommTraceEvent[] ring;
    private final int capacity;
    /** 已占槽计数（含被覆盖的）：槽 = idx % capacity，seq = idx + 1。 */
    private final AtomicLong writeCount = new AtomicLong(0);
    private final ConcurrentHashMap<String, PortStats> portStats = new ConcurrentHashMap<>();

    // ===== SSE 投递（信号式单写者，仿 LogBuffer；append 只 release 信号） =====
    private final CopyOnWriteArraySet<CommTraceSubscriber> subscribers = new CopyOnWriteArraySet<>();
    private final Semaphore deliverySignal = new Semaphore(0);
    private volatile Thread deliveryThread;
    private final Object deliveryStartLock = new Object();
    private volatile long lastDeliveredSeq = 0L;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    /** 测试/独立运行可自建实例；生产用 {@link #instance()}。 */
    public CommTraceBuffer(int capacity) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("commtrace capacity must be > 0, got " + capacity);
        }
        this.capacity = capacity;
        this.ring = new CommTraceEvent[capacity];
    }

    // ========== 捕获热路径 ==========

    /** 捕获一帧 TX。payload 截断为事件内副本，调用方数组可复用。 */
    public void tx(CommTraceTransport transport, String portId, byte[] payload, String txnId) {
        append(transport, portId, CommTraceDirection.TX, payload,
                payload != null ? payload.length : 0, txnId, null);
    }

    /** 捕获一帧 RX（带与 TX 配对的 txnId 和响应耗时）。 */
    public void rx(CommTraceTransport transport, String portId, byte[] payload, String txnId,
            Double durationMillis) {
        append(transport, portId, CommTraceDirection.RX, payload,
                payload != null ? payload.length : 0, txnId, durationMillis);
    }

    /** 捕获一帧 RX（length 独立于 payload 长度，读缓冲按有效长度截断）。 */
    public void rx(CommTraceTransport transport, String portId, byte[] data, int length,
            String txnId, Double durationMillis) {
        append(transport, portId, CommTraceDirection.RX, data, length, txnId, durationMillis);
    }

    /** 记一次通道错误（写失败/传输异常；不产生帧，只累计 per-port 错误计数）。 */
    public void error(CommTraceTransport transport, String portId) {
        PortStats stats = portStats.computeIfAbsent(portKey(transport, portId), k -> new PortStats());
        stats.errorCount.incrementAndGet();
    }

    private void append(CommTraceTransport transport, String portId, CommTraceDirection dir,
            byte[] payload, int length, String txnId, Double durationMillis) {
        if (closed.get() || payload == null) {
            return;
        }
        long tsMicros = System.currentTimeMillis() * 1000 + (System.nanoTime() / 1000) % 1000;
        // MDC 继承：coordinate 由引擎/组件层设置；设备维度键上游不设则 null（如实）
        String coordinate = MDC.get(MdcContext.INTEGRATION_COORDINATE_KEY);
        String deviceId = MDC.get(MDC_DEVICE_ID_KEY);
        String deviceName = MDC.get(MDC_DEVICE_NAME_KEY);

        long idx = writeCount.getAndIncrement();
        int slot = (int) (idx % capacity);
        ring[slot] = new CommTraceEvent(idx + 1, tsMicros, transport, portId, deviceId, deviceName,
                coordinate, dir, CommTraceEvent.truncate(payload, length), length, txnId,
                durationMillis);
        PortStats stats = portStats.computeIfAbsent(portKey(transport, portId), k -> new PortStats());
        stats.record(dir, tsMicros);
        if (!subscribers.isEmpty()) {
            deliverySignal.release();
        }
    }

    static String portKey(CommTraceTransport transport, String portId) {
        return transport.name() + ":" + portId;
    }

    // ========== 查询 ==========

    /**
     * 过滤查询：返回 seq 升序的匹配子序列（环内最新 limit 条匹配）。
     *
     * @param filter 过滤（各维 AND）
     * @param limit  最大条数
     * @param since  只返回 seq &gt; since 的帧（增量游标）
     */
    public List<CommTraceEvent> query(CommTraceFilter filter, int limit, long since) {
        List<CommTraceEvent> matched = new ArrayList<>();
        long total = writeCount.get();
        long start = Math.max(0, total - capacity);
        for (long idx = start; idx < total; idx++) {
            CommTraceEvent e = ring[(int) (idx % capacity)];
            if (e == null) {
                continue; // 理论不可达（构造后顺序写）；弱一致快照下的防御跳过
            }
            if (e.getSeq() <= since) {
                continue;
            }
            if (filter.matches(e)) {
                matched.add(e);
            }
        }
        if (matched.size() <= limit) {
            return matched;
        }
        return matched.subList(matched.size() - limit, matched.size());
    }

    /** 单帧（seq 全局单调，环外/被弃旧的 seq 返回 null）。 */
    public CommTraceEvent getFrame(long seq) {
        long total = writeCount.get();
        long start = Math.max(0, total - capacity);
        for (long idx = start; idx < total; idx++) {
            if (idx + 1 == seq) {
                return ring[(int) (idx % capacity)];
            }
        }
        return null;
    }

    /** 当前最新 seq（= 已写入帧数；客户端增量拉取的基线）。 */
    public long latestSeq() {
        return writeCount.get();
    }

    /**
     * overview 通道分组树：transport → port 列表（含计数/最后帧 ts/近 10s 速率/关联设备）。
     * 速率与设备从环扫描派生（有界 O(capacity)，读端点专用）；计数从 PortStats 直读。
     */
    public Map<String, Object> overview() {
        long nowMicros = System.currentTimeMillis() * 1000;
        long windowStart = nowMicros - 10_000_000L; // 近 10s
        // 环扫描派生：per-port 近窗计数 + 全环关联设备
        Map<String, long[]> windowCounts = new LinkedHashMap<>();
        Map<String, Map<String, String>> portDevices = new LinkedHashMap<>();
        long total = writeCount.get();
        long start = Math.max(0, total - capacity);
        for (long idx = start; idx < total; idx++) {
            CommTraceEvent e = ring[(int) (idx % capacity)];
            if (e == null) {
                continue;
            }
            String key = portKey(e.getTransport(), e.getPortId());
            if (e.getTsMicros() >= windowStart) {
                long[] c = windowCounts.computeIfAbsent(key, k -> new long[1]);
                c[0]++;
            }
            Map<String, String> devices = portDevices.computeIfAbsent(key, k -> new LinkedHashMap<>());
            String deviceLabel = e.getDeviceId() != null ? e.getDeviceId()
                    : (e.getDeviceName() != null ? e.getDeviceName() : e.getCoordinate());
            if (deviceLabel != null) {
                devices.put(deviceLabel, e.getCoordinate());
            }
        }
        // 分组树组装
        Map<String, Object> transports = new LinkedHashMap<>();
        for (CommTraceTransport t : CommTraceTransport.values()) {
            List<Map<String, Object>> ports = new ArrayList<>();
            for (Map.Entry<String, PortStats> entry : portStats.entrySet()) {
                if (!entry.getKey().startsWith(t.name() + ":")) {
                    continue;
                }
                PortStats s = entry.getValue();
                String portId = entry.getKey().substring(t.name().length() + 1);
                long[] window = windowCounts.get(entry.getKey());
                double fps = window != null ? window[0] / 10.0 : 0.0;
                Map<String, Object> port = new LinkedHashMap<>();
                port.put("transport", t.name());
                port.put("port", portId);
                port.put("txCount", s.txCount.get());
                port.put("rxCount", s.rxCount.get());
                port.put("errorCount", s.errorCount.get());
                port.put("lastFrameTs", s.lastFrameMicros > 0 ? s.lastFrameMicros : null);
                port.put("framesPerSecond10s", Math.round(fps * 10) / 10.0);
                Map<String, String> devices = portDevices.get(entry.getKey());
                List<Map<String, String>> deviceList = new ArrayList<>();
                if (devices != null) {
                    for (Map.Entry<String, String> d : devices.entrySet()) {
                        Map<String, String> dd = new LinkedHashMap<>();
                        dd.put("device", d.getKey());
                        dd.put("coordinate", d.getValue());
                        deviceList.add(dd);
                    }
                }
                port.put("devices", deviceList);
                ports.add(port);
            }
            if (!ports.isEmpty()) {
                Map<String, Object> node = new LinkedHashMap<>();
                node.put("transport", t.name());
                node.put("ports", ports);
                transports.put(t.name(), node);
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("transports", transports);
        result.put("capacity", capacity);
        result.put("bufferedFrames", Math.min(total, capacity));
        result.put("latestSeq", total);
        return result;
    }

    // ========== SSE 订阅 ==========

    public void subscribe(CommTraceSubscriber subscriber) {
        if (!closed.get() && subscriber != null) {
            subscribers.add(subscriber);
            ensureDeliveryStarted();
        }
    }

    public void unsubscribe(CommTraceSubscriber subscriber) {
        subscribers.remove(subscriber);
    }

    private void ensureDeliveryStarted() {
        if (deliveryThread != null && deliveryThread.isAlive()) {
            return;
        }
        synchronized (deliveryStartLock) {
            if (deliveryThread != null && deliveryThread.isAlive()) {
                return;
            }
            Thread t = new Thread(this::runDelivery, "commtrace-delivery");
            t.setDaemon(true);
            t.start();
            deliveryThread = t;
        }
    }

    private void runDelivery() {
        while (!closed.get()) {
            deliverySignal.acquireUninterruptibly();
            if (closed.get()) {
                return;
            }
            deliverySignal.drainPermits();
            try {
                deliverDelta();
            } catch (Throwable t) {
                // 单订阅者异常已在 deliverDelta 内兜底；此处防御性保命——投递线程绝不能退出
            }
        }
    }

    private void deliverDelta() {
        if (subscribers.isEmpty() || closed.get()) {
            return;
        }
        long fromSeq = lastDeliveredSeq;
        List<CommTraceEvent> toDeliver = new ArrayList<>();
        long maxSeq = fromSeq;
        long total = writeCount.get();
        long start = Math.max(fromSeq, total - capacity); // fromSeq 即已投递 seq 基线
        for (long idx = start; idx < total; idx++) {
            CommTraceEvent e = ring[(int) (idx % capacity)];
            if (e == null) {
                continue;
            }
            if (e.getSeq() > fromSeq) {
                toDeliver.add(e);
                if (e.getSeq() > maxSeq) {
                    maxSeq = e.getSeq();
                }
            }
        }
        if (toDeliver.isEmpty()) {
            return;
        }
        CommTraceSubscriber[] snapshot = subscribers.toArray(new CommTraceSubscriber[0]);
        List<CommTraceSubscriber> dead = null;
        for (CommTraceSubscriber sub : snapshot) {
            CommTraceFilter filter = sub.getFilter();
            for (CommTraceEvent e : toDeliver) {
                if (closed.get()) {
                    return;
                }
                if (!filter.matches(e)) {
                    continue;
                }
                try {
                    sub.send(e);
                } catch (Throwable t) {
                    if (dead == null) {
                        dead = new ArrayList<>();
                    }
                    dead.add(sub);
                    break;
                }
            }
        }
        lastDeliveredSeq = maxSeq;
        if (dead != null) {
            for (CommTraceSubscriber s : dead) {
                subscribers.remove(s);
            }
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            deliverySignal.release();
            Thread t = deliveryThread;
            if (t != null) {
                t.interrupt();
                try {
                    t.join(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            for (CommTraceSubscriber subscriber : subscribers) {
                try {
                    subscriber.close();
                } catch (Exception ignored) {
                    // 关闭路径不抛
                }
            }
            subscribers.clear();
            Arrays.fill(ring, null);
        }
    }
}
