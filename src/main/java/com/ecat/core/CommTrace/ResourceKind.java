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
 * IO 资源类别（反向折叠查询参数与账本自描述用；五个 IO 传输库的资源形态封闭集）。
 *
 * @author coffee
 */
public enum ResourceKind {
    /** 串口（serial 库；key = portName）。 */
    SERIAL_PORT,
    /** Modbus 连接（modbus 库；key = ip:port 或 portName）。 */
    MODBUS_CONNECTION,
    /** TCP 客户端连接（tcp 库；key = host:port）。 */
    TCP_CLIENT,
    /** TCP 服务端监听（tcp 库；key = ip:port）。 */
    TCP_SERVER,
    /** HTTP 客户端（httpserver 库 EasyHttpClient；key = host:port）。 */
    HTTP_CLIENT,
    /** HTTP 服务端（httpserver 库共享池；key = ip:port）。 */
    HTTP_SERVER,
    /** MQTT 客户端句柄（mqtt 库；key = clientId）。 */
    MQTT_CLIENT
}
