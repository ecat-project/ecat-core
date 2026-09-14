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
 * 资源使用方式（二值，与 {@link OwnerLevel} 正交）。
 *
 * <p>借用带主（身份穿透）：经他库转手落到资源上的注册，登记的是<b>最终使用者的完整身份</b>，
 * ADAPTER 只标注"路径经过转手"，不把中间库登记为使用者。例：设备经 Modbus RTU 用串口，
 * serial 账本记 (DEVICE, …, ADAPTER)——追踪归因一跳到位，账本直接列设备清单。
 *
 * @author coffee
 */
public enum Usage {
    /** 直接使用本库资源。 */
    DIRECT,
    /** 经他库转手落到本资源（如 Modbus RTU 设备经 serial 底层串口，身份是设备、路径经 modbus）。 */
    ADAPTER
}
