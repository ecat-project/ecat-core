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
    TCP_CLIENT,
    /** HTTP 客户端（httpserver 仓 EasyHttpClient SDK 层捕获，覆盖全部已迁/将迁 HTTP 集成；portId=host:port，缺省端口补全 80/443）。 */
    HTTP,
    /** TCP 服务端入站/出站（tcp 库 server 连接捕获，portId=监听口+remote 对端；被动侧归因=server owner 直读）。 */
    TCP_SERVER,
    /** HTTP 服务端入站请求/响应（httpserver 库入站捕获；只记元数据不记 body，凭据类头禁入环，portId=ip:port）。 */
    HTTP_SERVER,
    /** MQTT 链路（mqtt 库 broker 侧消息与 client 句柄收发双面捕获，portId=clientId/topic）。 */
    MQTT
}
