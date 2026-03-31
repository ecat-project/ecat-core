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

package com.ecat.core.LogicDevice;

import com.ecat.core.Device.DeviceBase;
import com.ecat.core.LogicState.ILogicAttribute;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 逻辑设备属性引用，用于反向索引中关联逻辑设备与逻辑属性。
 *
 * <p>当物理属性更新时，通过反向索引（reverseIndex）查找引用了该物理属性的
 * 逻辑设备属性，从而触发逻辑属性的 {@link ILogicAttribute#updateBindAttrValue} 更新。
 *
 * <p>此对象是 LogicDeviceRegistry 反向索引中的值元素，
 * 每个引用包含一个逻辑设备实例和该设备上的一个逻辑属性。
 *
 * @see LogicDeviceRegistry
 * @see ILogicAttribute
 * @author coffee
 */
@Getter
@AllArgsConstructor
@NoArgsConstructor
public class LogicDeviceAttrRef {

    /**
     * 逻辑设备实例（DeviceBase 类型，运行时为 LogicDevice 子类）
     */
    private DeviceBase logicDevice;

    /**
     * 该逻辑设备上的逻辑属性
     */
    private ILogicAttribute<?> logicAttr;
}
