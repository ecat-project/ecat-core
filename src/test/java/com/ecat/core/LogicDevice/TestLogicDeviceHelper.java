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

import com.ecat.core.ConfigEntry.ConfigEntry;
import com.ecat.core.Device.DeviceBase;
import com.ecat.core.LogicState.LogicAttributeDefine;
import com.ecat.core.State.AttributeBase;
import com.ecat.core.State.AttributeClass;
import com.ecat.core.State.AttributeType;
import com.ecat.core.State.UnitInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 测试辅助类，用于创建属于 com.ecat.core.LogicDevice 包的设备和属性。
 *
 * <p>在 LogicDeviceConsumer 测试中，需要模拟来自 LogicDevice 包的属性事件
 * 来验证过滤逻辑。由于 LogicDevice 类尚未创建（Task 7），
 * 这里通过在本包下创建具体的 DeviceBase 子类来模拟。
 *
 * @author coffee
 */
public class TestLogicDeviceHelper {

    /**
     * 创建一个属于 com.ecat.core.LogicDevice 包的 DeviceBase 实例。
     * 由于此类在 com.ecat.core.LogicDevice 包中，
     * {@link #getClass()}.getPackage().getName() 返回 "com.ecat.core.LogicDevice"。
     */
    public static DeviceBase createDevice(String deviceId) {
        ConfigEntry entry = new ConfigEntry();
        entry.setEntryId(deviceId);
        entry.setUniqueId(deviceId);
        Map<String, Object> data = new HashMap<>();
        data.put("name", "test-logic-device-" + deviceId);
        entry.setData(data);
        return new TestLogicDeviceInPackage(entry);
    }

    /**
     * 属于 com.ecat.core.LogicDevice 包的 LogicDevice 子类。
     * 用于测试 LogicDeviceConsumer 的 instanceof LogicDevice 过滤逻辑。
     */
    private static class TestLogicDeviceInPackage extends LogicDevice {
        public TestLogicDeviceInPackage(ConfigEntry entry) {
            super(entry);
        }

        @Override
        public List<LogicAttributeDefine> getAttrDefs() {
            return new ArrayList<>();
        }

        @Override
        protected String getMappingType() {
            return "TEST";
        }

        @Override
        public void start() {}

        @Override
        public void stop() {}

        @Override
        public void release() {}
    }

    /**
     * 属于 com.ecat.core.LogicDevice 包的 AttributeBase 子类。
     * 用于测试 LogicDeviceConsumer 的包名过滤逻辑。
     */
    public static class TestLogicAttr extends AttributeBase<Double> {
        public TestLogicAttr(String attributeID) {
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
