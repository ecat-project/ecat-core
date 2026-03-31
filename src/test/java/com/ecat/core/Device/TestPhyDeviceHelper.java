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

package com.ecat.core.Device;

import com.ecat.core.ConfigEntry.ConfigEntry;
import com.ecat.core.State.AttributeBase;
import com.ecat.core.State.AttributeClass;
import com.ecat.core.State.AttributeType;
import com.ecat.core.State.UnitInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * 测试辅助类，用于创建属于 com.ecat.core.Device 包的设备和属性。
 *
 * <p>在 LogicDeviceConsumer 测试中，需要模拟来自物理设备（非 LogicDevice 包）的属性事件。
 * 由于测试类本身在 com.ecat.core.LogicDevice 包中，
 * 匿名 DeviceBase 子类会继承该包名，导致 {@link com.ecat.core.LogicDevice.LogicDeviceConsumer}
 * 的包名过滤逻辑将其误判为逻辑设备属性。
 *
 * <p>通过在本包（com.ecat.core.Device）下创建具体的 DeviceBase 子类，
 * 确保设备的类名以 "com.ecat.core.Device." 开头，
 * 从而正确模拟物理设备的事件。
 *
 * @author coffee
 */
public class TestPhyDeviceHelper {

    /**
     * 创建一个属于 com.ecat.core.Device 包的 DeviceBase 实例。
     */
    public static DeviceBase createDevice(String deviceId) {
        ConfigEntry entry = new ConfigEntry();
        entry.setEntryId(deviceId);
        entry.setUniqueId(deviceId);
        Map<String, Object> data = new HashMap<>();
        data.put("name", "phy-device-" + deviceId);
        entry.setData(data);
        return new TestPhyDeviceInPackage(entry);
    }

    /**
     * 创建一个绑定到物理设备的属性。
     *
     * @param deviceId 物理设备ID
     * @param attrId   属性ID
     * @return 绑定到物理设备的属性实例
     */
    public static AttributeBase<Double> createPhyAttr(String deviceId, String attrId) {
        DeviceBase device = createDevice(deviceId);
        final AttributeBase<Double> attr = new TestPhyAttr(attrId);
        device.setAttribute(attr);
        return attr;
    }

    /**
     * 属于 com.ecat.core.Device 包的 DeviceBase 子类。
     */
    private static class TestPhyDeviceInPackage extends DeviceBase {
        public TestPhyDeviceInPackage(ConfigEntry entry) {
            super(entry);
        }

        @Override
        public void init() {}

        @Override
        public void start() {}

        @Override
        public void stop() {}

        @Override
        public void release() {}
    }

    /**
     * 属于 com.ecat.core.Device 包的 AttributeBase 子类。
     */
    public static class TestPhyAttr extends AttributeBase<Double> {
        public TestPhyAttr(String attributeID) {
            super(attributeID, AttributeClass.VALUE, null, null, 0, false, false);
        }

        @Override
        public String getDisplayValue(UnitInfo toUnit) { return null; }

        @Override
        protected Double convertFromUnitImp(Double value, UnitInfo fromUnit) { return value; }

        @Override
        public Double convertValueToUnit(Double value, UnitInfo fromUnit, UnitInfo toUnit) { return value; }

        @Override
        public com.ecat.core.Utils.DynamicConfig.ConfigDefinition getValueDefinition() { return null; }

        @Override
        protected com.ecat.core.I18n.I18nKeyPath getI18nPrefixPath() {
            return new com.ecat.core.I18n.I18nKeyPath("test.", "test");
        }

        @Override
        public AttributeType getAttributeType() { return AttributeType.NUMERIC; }
    }
}
