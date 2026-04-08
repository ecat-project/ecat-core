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

package com.ecat.core.LogicState.BindState;

import com.ecat.core.LogicState.ILogicAttribute;
import com.ecat.core.LogicState.LNumericAttribute;
import com.ecat.core.State.AttributeBase;
import com.ecat.core.State.AttributeClass;
import com.ecat.core.State.AttributeType;
import com.ecat.core.State.UnitInfo;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * LNumericBindMixAttribute 单元测试
 *
 * @author coffee
 */
public class LNumericBindMixAttributeTest {

    @Mock
    private AttributeClass mockAttrClass;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockAttrClass.getDisplayName()).thenReturn("TestAttrClass");
    }

    /**
     * 测试用的具体子类 - 求和聚合
     */
    private static class SumMixAttr extends LNumericBindMixAttribute {
        // 保存最新的源值，用于 calcRawValue
        private final java.util.Map<String, Double> sourceValues = new java.util.HashMap<>();

        SumMixAttr(String attrId, AttributeClass attrClass, UnitInfo nativeUnit,
                   UnitInfo displayUnit, int precision) {
            super(attrId, attrClass, nativeUnit, displayUnit, precision);
        }

        @Override
        public void onSourceUpdated(String sourceDeviceId, String sourceAttrId, double value) {
            sourceValues.put(sourceDeviceId + ":" + sourceAttrId, value);
            super.onSourceUpdated(sourceDeviceId, sourceAttrId, value);
        }

        @Override
        protected double calcRawValue(List<BindedLogicAttrData> sources) {
            double sum = 0;
            for (BindedLogicAttrData data : sources) {
                String key = data.getSourceDeviceId() + ":" + data.getSourceAttrId();
                Double v = sourceValues.get(key);
                if (v != null) {
                    sum += v;
                }
            }
            return sum;
        }
    }

    /**
     * 测试用的具体子类 - 求平均聚合
     */
    private static class AvgMixAttr extends LNumericBindMixAttribute {
        private final java.util.Map<String, Double> sourceValues = new java.util.HashMap<>();

        AvgMixAttr(String attrId, AttributeClass attrClass, UnitInfo nativeUnit,
                   UnitInfo displayUnit, int precision) {
            super(attrId, attrClass, nativeUnit, displayUnit, precision);
        }

        @Override
        public void onSourceUpdated(String sourceDeviceId, String sourceAttrId, double value) {
            sourceValues.put(sourceDeviceId + ":" + sourceAttrId, value);
            super.onSourceUpdated(sourceDeviceId, sourceAttrId, value);
        }

        @Override
        protected double calcRawValue(List<BindedLogicAttrData> sources) {
            if (sources.isEmpty()) return 0;
            double sum = 0;
            int count = 0;
            for (BindedLogicAttrData data : sources) {
                String key = data.getSourceDeviceId() + ":" + data.getSourceAttrId();
                Double v = sourceValues.get(key);
                if (v != null) {
                    sum += v;
                    count++;
                }
            }
            return count > 0 ? sum / count : 0;
        }
    }

    // ========== 构造函数和基本属性测试 ==========

    @Test
    public void testConstructorSetsBasicProperties() {
        SumMixAttr attr = new SumMixAttr(
                "total_power", mockAttrClass, null, null, 2);

        assertEquals("total_power", attr.getAttributeID());
        assertEquals(mockAttrClass, attr.getAttrClass());
    }

    @Test
    public void testImplementsILogicBindAttribute() {
        SumMixAttr attr = new SumMixAttr(
                "total", mockAttrClass, null, null, 2);

        assertTrue(attr instanceof ILogicBindAttribute);
        assertTrue(attr instanceof ILogicAttribute);
        assertTrue(attr instanceof LNumericAttribute);
    }

    @Test
    public void testNotStandalone() {
        SumMixAttr attr = new SumMixAttr(
                "total", mockAttrClass, null, null, 2);

        assertFalse(attr.isStandalone());
    }

    // ========== registerLogicSource 测试 ==========

    @Test
    public void testRegisterLogicSource() {
        SumMixAttr attr = new SumMixAttr(
                "total_power", mockAttrClass, null, null, 2);

        attr.registerLogicSource("device_1", "power_a");
        attr.registerLogicSource("device_2", "power_b");

        List<BindedLogicAttrData> sources = attr.getSources();
        assertNotNull(sources);
        assertEquals(2, sources.size());
        assertEquals("device_1", sources.get(0).getSourceDeviceId());
        assertEquals("power_a", sources.get(0).getSourceAttrId());
        assertEquals("device_2", sources.get(1).getSourceDeviceId());
        assertEquals("power_b", sources.get(1).getSourceAttrId());
    }

    @Test
    public void testGetSourcesImmutable() {
        SumMixAttr attr = new SumMixAttr(
                "total", mockAttrClass, null, null, 2);
        attr.registerLogicSource("dev1", "attr1");

        List<BindedLogicAttrData> sources = attr.getSources();
        try {
            sources.add(new BindedLogicAttrData("dev2", "attr2"));
            fail("Should throw UnsupportedOperationException");
        } catch (UnsupportedOperationException e) {
            // Expected
        }
    }

    // ========== onSourceUpdated 测试 ==========

    @Test
    public void testOnSourceUpdatedAllSources() {
        SumMixAttr attr = new SumMixAttr(
                "total_power", mockAttrClass, null, null, 2);
        attr.registerLogicSource("device_1", "power_a");
        attr.registerLogicSource("device_2", "power_b");

        // 更新所有源
        attr.onSourceUpdated("device_1", "power_a", 10.0);
        attr.onSourceUpdated("device_2", "power_b", 20.0);

        // 聚合结果应该是 10.0 + 20.0 = 30.0
        assertNotNull(attr.getValue());
        assertEquals(30.0, attr.getValue(), 0.01);
    }

    @Test
    public void testOnSourceUpdatedPartialSources() {
        SumMixAttr attr = new SumMixAttr(
                "total_power", mockAttrClass, null, null, 2);
        attr.registerLogicSource("device_1", "power_a");
        attr.registerLogicSource("device_2", "power_b");

        // 只更新一个源
        attr.onSourceUpdated("device_1", "power_a", 10.0);

        // 不是所有源都更新了，值应该保持 null
        assertNull(attr.getValue());
    }

    @Test
    public void testOnSourceUpdatedUnknownSource() {
        SumMixAttr attr = new SumMixAttr(
                "total_power", mockAttrClass, null, null, 2);
        attr.registerLogicSource("device_1", "power_a");

        // 更新一个未注册的源
        attr.onSourceUpdated("device_unknown", "unknown_attr", 99.0);

        assertNull(attr.getValue());
    }

    @Test
    public void testOnSourceUpdatedResetAfterCalc() {
        SumMixAttr attr = new SumMixAttr(
                "total_power", mockAttrClass, null, null, 2);
        attr.registerLogicSource("device_1", "power_a");
        attr.registerLogicSource("device_2", "power_b");

        // 第一轮：两个源都更新
        attr.onSourceUpdated("device_1", "power_a", 10.0);
        attr.onSourceUpdated("device_2", "power_b", 20.0);
        assertEquals(30.0, attr.getValue(), 0.01);

        // 第二轮：只更新一个源 - 不应重新计算（标志已重置）
        attr.onSourceUpdated("device_1", "power_a", 5.0);
        // 值应保持第一轮的结果
        assertEquals(30.0, attr.getValue(), 0.01);
    }

    // ========== 聚合公式测试 ==========

    @Test
    public void testAvgAggregation() {
        AvgMixAttr attr = new AvgMixAttr(
                "avg_temperature", mockAttrClass, null, null, 2);
        attr.registerLogicSource("device_1", "temp_a");
        attr.registerLogicSource("device_2", "temp_b");
        attr.registerLogicSource("device_3", "temp_c");

        attr.onSourceUpdated("device_1", "temp_a", 20.0);
        attr.onSourceUpdated("device_2", "temp_b", 30.0);
        attr.onSourceUpdated("device_3", "temp_c", 25.0);

        // 平均值 = (20 + 30 + 25) / 3 = 25.0
        assertNotNull(attr.getValue());
        assertEquals(25.0, attr.getValue(), 0.01);
    }

    // ========== getBindedAttrs 测试 ==========

    @Test
    public void testGetBindedAttrsEmptyBeforeResolve() {
        SumMixAttr attr = new SumMixAttr(
                "total", mockAttrClass, null, null, 2);
        attr.registerLogicSource("dev1", "attr1");

        List<AttributeBase<?>> binded = attr.getBindedAttrs();
        assertNotNull(binded);
        assertTrue(binded.isEmpty());
    }

    @Test
    public void testGetBindedAttrsAfterResolve() {
        SumMixAttr attr = new SumMixAttr(
                "total", mockAttrClass, null, null, 2);
        attr.registerLogicSource("dev1", "attr1");

        // resolvedSource 为 null，getBindedAttrs 应返回空列表
        List<AttributeBase<?>> binded = attr.getBindedAttrs();
        assertNotNull(binded);
        assertEquals(0, binded.size());

        // 使用 mock ILogicAttribute 作为源（模拟运行时解析）
        ILogicAttribute<?> mockSource = mock(ILogicAttribute.class);
        attr.getSources().get(0).setResolvedSource(mockSource);
        // mockSource 不是 AttributeBase 实例，所以 getBindedAttrs 仍返回空
        binded = attr.getBindedAttrs();
        assertEquals(0, binded.size());
    }

    // ========== setDisplayValue 测试 ==========

    @Test
    public void testSetDisplayValueReturnsFalse() {
        SumMixAttr attr = new SumMixAttr(
                "total", mockAttrClass, null, null, 2);

        Boolean result = attr.setDisplayValue("100.0", null).join();
        assertFalse(result);
    }

    // ========== init 方法测试 ==========

    @Test
    public void testInitMethods() {
        SumMixAttr attr = new SumMixAttr(
                "total", mockAttrClass, null, null, 2);

        attr.initAttributeID("new_total_id");
        assertEquals("new_total_id", attr.getAttributeID());

        attr.initValueChangeable(true);
        assertTrue(attr.canValueChange());

        attr.initNativeUnit(null);
        assertNull(attr.getNativeUnit());

        attr.initDisplayUnit(null);
        assertNull(attr.getDisplayUnit());
    }

    // ========== BindedLogicAttrData 测试 ==========

    @Test
    public void testBindedLogicAttrDataConstructAndGetters() {
        BindedLogicAttrData data = new BindedLogicAttrData("dev1", "attr1");

        assertEquals("dev1", data.getSourceDeviceId());
        assertEquals("attr1", data.getSourceAttrId());
        assertNull(data.getResolvedSource());
        assertFalse(data.isUpdated());
        assertEquals(0, data.getUpdateTime());
    }

    @Test
    public void testBindedLogicAttrDataSetters() {
        BindedLogicAttrData data = new BindedLogicAttrData("dev1", "attr1");

        data.setUpdated(true);
        assertTrue(data.isUpdated());

        data.setUpdateTime(1000L);
        assertEquals(1000L, data.getUpdateTime());

        ILogicAttribute<?> mockSource = mock(ILogicAttribute.class);
        data.setResolvedSource(mockSource);
        assertSame(mockSource, data.getResolvedSource());
    }
}
