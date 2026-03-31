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

package com.ecat.core.LogicMapping;

import com.ecat.core.Device.DeviceBase;
import com.ecat.core.LogicState.ILogicAttribute;
import com.ecat.core.LogicState.LogicAttributeDefine;

import java.util.ArrayList;
import java.util.List;

/**
 * 设备映射接口，定义物理设备属性到逻辑属性的映射规则。
 *
 * <p>每种设备映射对应一种逻辑设备类型（如 SO2、UPS 等），
 * 由集成模块实现并注册到 {@link LogicMappingManager}。
 * 通过此接口，ecat 可以将不同厂商、不同型号的物理设备
 * 统一映射为标准化的逻辑设备。
 *
 * <p>映射关系示例：
 * <ul>
 *   <li>saimosen SMS8200 (物理设备) -> SO2 逻辑设备</li>
 *   <li>saimosen QCDevice (物理设备) -> SO2 逻辑设备 或 UPS 逻辑设备</li>
 * </ul>
 *
 * @see LogicMappingManager
 * @see com.ecat.core.LogicState.ILogicAttribute
 * @author coffee
 */
public interface IDeviceMapping {

    /**
     * 获取映射类型，即逻辑设备类型标识。
     * 例如 "SO2"、"UPS"、"PM2.5" 等。
     *
     * @return 映射类型标识符
     */
    String getMappingType();

    /**
     * 根据逻辑属性ID和物理设备，获取对应的逻辑属性。
     *
     * <p>此方法是映射的核心：给定一个物理设备和逻辑属性ID，
     * 返回该逻辑属性的实例。映射实现类负责：
     * <ol>
     *   <li>找到物理设备上对应的物理属性</li>
     *   <li>创建或返回已绑定的逻辑属性</li>
     *   <li>建立物理属性到逻辑属性的绑定关系</li>
     * </ol>
     *
     * @param logicAttrId 逻辑属性ID
     * @param phyDevice 物理设备实例
     * @return 逻辑属性实例，如果映射关系不存在则返回null
     */
    ILogicAttribute<?> getAttr(String logicAttrId, DeviceBase phyDevice);

    /**
     * 获取设备所属集成的坐标。
     * 例如 "com.ecat:integration-saimosen"
     *
     * @return 集成坐标
     */
    String getDeviceCoordinate();

    /**
     * 获取设备型号。
     * 例如 "SMS8200"、"QCDevice"
     *
     * @return 设备型号
     */
    String getDeviceModel();

    /**
     * 获取此映射定义的所有逻辑属性定义列表。
     * 默认返回空列表，抽象基类可以覆盖此方法提供具体的属性定义。
     *
     * <p>LogicDevice 创建逻辑属性时调用此方法获取初始化元数据。
     *
     * @return 逻辑属性定义列表，不会为null
     */
    default List<LogicAttributeDefine> getAttrDefs() {
        return new ArrayList<>();
    }
}
