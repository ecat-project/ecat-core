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

/**
 * 通讯帧查询过滤（不可变，全维 AND 组合；null 维度 = 不过滤）。
 * 与 REST 查询参数一一对应（transport/port/device/dir/q/since）。
 *
 * @author coffee
 */
public final class CommTraceFilter {

    private final CommTraceTransport transport;
    private final String port;
    private final String device;
    private final CommTraceDirection dir;
    private final String q;

    public CommTraceFilter(CommTraceTransport transport, String port, String device,
            CommTraceDirection dir, String q) {
        this.transport = transport;
        this.port = port;
        this.device = device;
        this.dir = dir;
        this.q = q;
    }

    /** 不过滤任何维度的通配过滤。 */
    public static CommTraceFilter all() {
        return new CommTraceFilter(null, null, null, null, null);
    }

    boolean matches(CommTraceEvent e) {
        if (transport != null && e.getTransport() != transport) {
            return false;
        }
        if (port != null && !port.equals(e.getPortId())) {
            return false;
        }
        if (device != null && !device.equals(e.getDeviceId()) && !device.equals(e.getDeviceName())) {
            return false;
        }
        if (dir != null && e.getDirection() != dir) {
            return false;
        }
        return e.matchesQuery(q);
    }

    public CommTraceTransport getTransport() { return transport; }
    public String getPort() { return port; }
    public String getDevice() { return device; }
    public CommTraceDirection getDir() { return dir; }
    public String getQ() { return q; }
}
