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
 * 通讯帧捕获的传输层类别。按捕获组件划分（同一物理串口可能同时出现 SERIAL 的
 * 透明串口帧与 RTU 记录：modbus RTU 经 wrapper 直持流，不经 SerialSourcePort 的
 * 读写路径，两层互不重复）。
 *
 * @author coffee
 */
public enum CommTraceTransport {
    /** 透明串口（SerialSourcePort 写/读路径）与 Modbus RTU（ModbusSource 事务层）。 */
    SERIAL,
    /** Modbus TCP / RTU over TCP（ModbusSource 事务层，portId=ip:port）。 */
    MODBUS_TCP,
    /** 原生 TCP 客户端（ReconnectTcpClient，portId=host:port）。 */
    TCP_CLIENT
}
