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

import java.nio.charset.StandardCharsets;

/**
 * 通讯帧捕获事件（不可变）。环内只存本对象（payload 已截断的副本），hex/ascii 渲染
 * 是纯派生计算、按需在读端点调用——环内不预渲染，写热路径不为渲染付代价。
 *
 * <p>payload 截断上限 256 字节；{@link #getOriginalLength()} 保留原始全长，
 * {@link #isTruncated()} 标记是否截断。
 *
 * <p>设备上下文（deviceId/deviceName/coordinate）在捕获点从 MDC 继承（引擎已设
 * integration.coordinate）；捕获线程拿不到时为 null，如实呈现，不猜测填充。
 *
 * @author coffee
 */
public final class CommTraceEvent {
    /** payload 截断上限（字节）。 */
    public static final int MAX_PAYLOAD_BYTES = 256;

    private final long seq;
    private final long tsMicros;
    private final CommTraceTransport transport;
    private final String portId;
    private final String deviceId;
    private final String deviceName;
    private final String coordinate;
    private final CommTraceDirection direction;
    private final byte[] payload;
    private final int originalLength;
    /** 事务配对标识（请求与响应同 txnId）；无配对语义时为 null。 */
    private final String txnId;
    /** 响应耗时（毫秒，RX 填）；请求/无配对为 null。 */
    private final Double durationMillis;

    CommTraceEvent(long seq, long tsMicros, CommTraceTransport transport, String portId,
            String deviceId, String deviceName, String coordinate, CommTraceDirection direction,
            byte[] payload, int originalLength, String txnId, Double durationMillis) {
        this.seq = seq;
        this.tsMicros = tsMicros;
        this.transport = transport;
        this.portId = portId;
        this.deviceId = deviceId;
        this.deviceName = deviceName;
        this.coordinate = coordinate;
        this.direction = direction;
        this.payload = payload;
        this.originalLength = originalLength;
        this.txnId = txnId;
        this.durationMillis = durationMillis;
    }

    /** 截断 payload 副本（入环前调用，环外数组可被调用方复用，必须拷贝）。 */
    static byte[] truncate(byte[] data, int length) {
        int n = Math.min(length, MAX_PAYLOAD_BYTES);
        byte[] copy = new byte[n];
        System.arraycopy(data, 0, copy, 0, n);
        return copy;
    }

    public long getSeq() { return seq; }
    public long getTsMicros() { return tsMicros; }
    public CommTraceTransport getTransport() { return transport; }
    public String getPortId() { return portId; }
    public String getDeviceId() { return deviceId; }
    public String getDeviceName() { return deviceName; }
    public String getCoordinate() { return coordinate; }
    public CommTraceDirection getDirection() { return direction; }
    public byte[] getPayload() { return payload; }
    public int getOriginalLength() { return originalLength; }
    public String getTxnId() { return txnId; }
    public Double getDurationMillis() { return durationMillis; }

    public boolean isTruncated() { return originalLength > payload.length; }

    /** 空格分隔的十六进制渲染（如 "01 03 00 00"）。仅读端点调用。 */
    public String renderHex() {
        StringBuilder sb = new StringBuilder(payload.length * 3);
        for (int i = 0; i < payload.length; i++) {
            if (i > 0) {
                sb.append(' ');
            }
            sb.append(Character.forDigit((payload[i] >> 4) & 0xF, 16));
            sb.append(Character.forDigit(payload[i] & 0xF, 16));
        }
        return sb.toString();
    }

    /** 可打印 ASCII 渲染（不可打印字节以 '.' 替代）。仅读端点调用。 */
    public String renderAscii() {
        StringBuilder sb = new StringBuilder(payload.length);
        for (byte b : payload) {
            sb.append(b >= 0x20 && b < 0x7F ? (char) b : '.');
        }
        return sb.toString();
    }

    /** ascii 渲染的纯字节版（过滤 q 子串匹配用，避免 StringBuilder）。 */
    byte[] asciiBytes() {
        byte[] out = new byte[payload.length];
        for (int i = 0; i < payload.length; i++) {
            byte b = payload[i];
            out[i] = (b >= 0x20 && b < 0x7F) ? b : (byte) '.';
        }
        return out;
    }

    /** 派生 ISO-8601 时间戳（微秒精度折叠到毫秒 + 小数位）。 */
    public String renderIsoTime() {
        long ms = tsMicros / 1000;
        int frac = (int) (tsMicros % 1000);
        String base = new java.sql.Timestamp(ms).toInstant().toString(); // ISO-8601, 含毫秒
        if (frac == 0) {
            return base;
        }
        // 把微秒剩余位接到毫秒后（best-effort 展示精度）
        return base + String.format(java.util.Locale.ROOT, "%03d", frac).replaceAll("0+$", "");
    }

    /** q 子串匹配目标串（deviceId/deviceName/portId/coordinate + ascii payload）。 */
    boolean matchesQuery(String q) {
        if (q == null || q.isEmpty()) {
            return true;
        }
        String lower = q.toLowerCase(java.util.Locale.ROOT);
        if (containsLower(deviceId, lower) || containsLower(deviceName, lower)
                || containsLower(portId, lower) || containsLower(coordinate, lower)) {
            return true;
        }
        String ascii = new String(asciiBytes(), StandardCharsets.US_ASCII).toLowerCase(java.util.Locale.ROOT);
        return ascii.contains(lower);
    }

    private static boolean containsLower(String s, String lower) {
        return s != null && s.toLowerCase(java.util.Locale.ROOT).contains(lower);
    }
}
