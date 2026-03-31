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
import org.junit.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * LogicMappingManager 单元测试
 *
 * @author coffee
 */
public class LogicMappingManagerTest {

    // =========================================================
    // Test stub: minimal IDeviceMapping implementation for testing
    // =========================================================

    /**
     * 测试用 IDeviceMapping 实现，仅用于验证 LogicMappingManager 的注册和查找逻辑。
     */
    private static class TestDeviceMapping implements IDeviceMapping {
        private final String mappingType;
        private final String coordinate;
        private final String model;
        private final List<LogicAttributeDefine> attrDefs;

        TestDeviceMapping(String mappingType, String coordinate, String model) {
            this(mappingType, coordinate, model, null);
        }

        TestDeviceMapping(String mappingType, String coordinate, String model,
                          List<LogicAttributeDefine> attrDefs) {
            this.mappingType = mappingType;
            this.coordinate = coordinate;
            this.model = model;
            this.attrDefs = attrDefs;
        }

        @Override
        public String getMappingType() {
            return mappingType;
        }

        @Override
        public ILogicAttribute<?> getAttr(String logicAttrId, DeviceBase phyDevice) {
            return null; // test stub, no real mapping
        }

        @Override
        public String getDeviceCoordinate() {
            return coordinate;
        }

        @Override
        public String getDeviceModel() {
            return model;
        }

        @Override
        public List<LogicAttributeDefine> getAttrDefs() {
            return attrDefs != null ? attrDefs : new java.util.ArrayList<>();
        }
    }

    // =========================================================
    // Tests
    // =========================================================

    /**
     * 测试：注册映射后通过 type + coordinate + model 精确查找
     */
    @Test
    public void testRegisterAndGetMapping() {
        LogicMappingManager manager = new LogicMappingManager();
        TestDeviceMapping mapping = new TestDeviceMapping(
                "SO2", "com.ecat:integration-saimosen", "SMS8200");

        manager.registerMapping(mapping);

        IDeviceMapping result = manager.getMapping(
                "SO2", "com.ecat:integration-saimosen", "SMS8200");

        assertNotNull("应找到已注册的映射", result);
        assertSame("应返回同一个实例", mapping, result);
    }

    /**
     * 测试：查找不存在的映射返回 null
     */
    @Test
    public void testGetMappingNotFound() {
        LogicMappingManager manager = new LogicMappingManager();

        // 未注册任何映射
        IDeviceMapping result = manager.getMapping(
                "SO2", "com.ecat:integration-saimosen", "SMS8200");
        assertNull("未注册的映射应返回 null", result);

        // 注册一个不同类型的映射
        manager.registerMapping(new TestDeviceMapping(
                "UPS", "com.ecat:integration-saimosen", "QCDevice"));

        // 用错误的类型查找
        result = manager.getMapping(
                "SO2", "com.ecat:integration-saimosen", "QCDevice");
        assertNull("类型不匹配应返回 null", result);

        // 用错误的 coordinate 查找
        result = manager.getMapping(
                "UPS", "com.ecat:integration-other", "QCDevice");
        assertNull("coordinate 不匹配应返回 null", result);

        // 用错误的 model 查找
        result = manager.getMapping(
                "UPS", "com.ecat:integration-saimosen", "OTHER");
        assertNull("model 不匹配应返回 null", result);
    }

    /**
     * 测试：同一个物理设备（saimosen QCDevice）可以注册到多种映射类型（SO2 和 UPS）
     */
    @Test
    public void testSameDeviceMultipleTypes() {
        LogicMappingManager manager = new LogicMappingManager();

        // 同一个 coordinate + model，注册到两种不同的映射类型
        TestDeviceMapping so2Mapping = new TestDeviceMapping(
                "SO2", "com.ecat:integration-saimosen", "QCDevice");
        TestDeviceMapping upsMapping = new TestDeviceMapping(
                "UPS", "com.ecat:integration-saimosen", "QCDevice");

        manager.registerMapping(so2Mapping);
        manager.registerMapping(upsMapping);

        // 分别通过不同类型查找，应得到不同的映射实例
        IDeviceMapping so2Result = manager.getMapping(
                "SO2", "com.ecat:integration-saimosen", "QCDevice");
        IDeviceMapping upsResult = manager.getMapping(
                "UPS", "com.ecat:integration-saimosen", "QCDevice");

        assertNotNull("应找到 SO2 映射", so2Result);
        assertNotNull("应找到 UPS 映射", upsResult);
        assertSame("SO2 映射应返回注册的实例", so2Mapping, so2Result);
        assertSame("UPS 映射应返回注册的实例", upsMapping, upsResult);
        assertNotSame("两种映射类型应返回不同实例", so2Result, upsResult);
    }

    /**
     * 测试：getAllMappingTypes 返回所有已注册的映射类型集合
     */
    @Test
    public void testGetAllMappingTypes() {
        LogicMappingManager manager = new LogicMappingManager();

        // 初始为空
        Set<String> types = manager.getAllMappingTypes();
        assertTrue("未注册时应返回空集合", types.isEmpty());

        // 注册多个映射
        manager.registerMapping(new TestDeviceMapping(
                "SO2", "com.ecat:integration-saimosen", "SMS8200"));
        manager.registerMapping(new TestDeviceMapping(
                "UPS", "com.ecat:integration-saimosen", "QCDevice"));
        manager.registerMapping(new TestDeviceMapping(
                "SO2", "com.ecat:integration-other", "OTHER_MODEL"));

        types = manager.getAllMappingTypes();
        assertEquals("应包含2种映射类型", 2, types.size());
        assertTrue("应包含 SO2 类型", types.contains("SO2"));
        assertTrue("应包含 UPS 类型", types.contains("UPS"));
    }

    /**
     * 测试：getMappingsByType 返回指定类型的所有映射
     */
    @Test
    public void testGetMappingsByType() {
        LogicMappingManager manager = new LogicMappingManager();

        // 注册多个 SO2 映射（不同 coordinate-model）
        TestDeviceMapping so2Mapping1 = new TestDeviceMapping(
                "SO2", "com.ecat:integration-saimosen", "SMS8200");
        TestDeviceMapping so2Mapping2 = new TestDeviceMapping(
                "SO2", "com.ecat:integration-saimosen", "QCDevice");
        TestDeviceMapping upsMapping = new TestDeviceMapping(
                "UPS", "com.ecat:integration-saimosen", "QCDevice");

        manager.registerMapping(so2Mapping1);
        manager.registerMapping(so2Mapping2);
        manager.registerMapping(upsMapping);

        // 查询 SO2 类型的所有映射
        List<IDeviceMapping> so2Mappings = manager.getMappingsByType("SO2");
        assertEquals("SO2 类型应有2个映射", 2, so2Mappings.size());
        assertTrue("应包含 SMS8200 映射", so2Mappings.contains(so2Mapping1));
        assertTrue("应包含 QCDevice 映射", so2Mappings.contains(so2Mapping2));
    }

    /**
     * 测试：getMappingsByType 对不存在的类型返回空列表
     */
    @Test
    public void testGetMappingsByTypeNotFound() {
        LogicMappingManager manager = new LogicMappingManager();

        List<IDeviceMapping> result = manager.getMappingsByType("NON_EXISTENT");
        assertNotNull("返回值不应为 null", result);
        assertTrue("不存在的类型应返回空列表", result.isEmpty());
    }

    /**
     * 测试：getAnyMappingByType 返回指定类型的第一个映射
     */
    @Test
    public void testGetAnyMappingByType() {
        LogicMappingManager manager = new LogicMappingManager();

        // 空管理器应返回 null
        IDeviceMapping result = manager.getAnyMappingByType("SO2");
        assertNull("空管理器应返回 null", result);

        // 注册映射后应返回第一个
        TestDeviceMapping mapping = new TestDeviceMapping(
                "SO2", "com.ecat:integration-saimosen", "SMS8200");
        manager.registerMapping(mapping);

        result = manager.getAnyMappingByType("SO2");
        assertNotNull("应返回一个映射实例", result);
        // 结果应是已注册的映射之一
        assertTrue("返回的映射应是 SO2 类型",
                "SO2".equals(result.getMappingType()));
    }

    /**
     * 测试：IDeviceMapping 默认 getAttrDefs() 返回空列表
     */
    @Test
    public void testGetAttrDefs() {
        LogicMappingManager manager = new LogicMappingManager();

        // 使用默认 getAttrDefs() 的映射
        TestDeviceMapping defaultMapping = new TestDeviceMapping(
                "SO2", "com.ecat:integration-saimosen", "SMS8200");
        manager.registerMapping(defaultMapping);

        IDeviceMapping result = manager.getMapping(
                "SO2", "com.ecat:integration-saimosen", "SMS8200");
        assertNotNull(result);

        List<LogicAttributeDefine> defs = result.getAttrDefs();
        assertNotNull("默认 getAttrDefs() 不应返回 null", defs);
        assertTrue("默认 getAttrDefs() 应返回空列表", defs.isEmpty());
    }

    /**
     * 测试：覆盖 getAttrDefs() 的映射应返回自定义的属性定义列表
     */
    @Test
    public void testGetAttrDefsCustom() {
        LogicMappingManager manager = new LogicMappingManager();

        // 创建带有自定义 attrDefs 的映射
        LogicAttributeDefine def1 = new LogicAttributeDefine(
                "so2", null, null, null, 0, false, null);
        LogicAttributeDefine def2 = new LogicAttributeDefine(
                "status", null, null, null, 0, false, null);
        TestDeviceMapping customMapping = new TestDeviceMapping(
                "SO2", "com.ecat:integration-saimosen", "SMS8200",
                Arrays.asList(def1, def2));

        manager.registerMapping(customMapping);

        IDeviceMapping result = manager.getMapping(
                "SO2", "com.ecat:integration-saimosen", "SMS8200");
        assertNotNull(result);

        List<LogicAttributeDefine> defs = result.getAttrDefs();
        assertNotNull(defs);
        assertEquals("应返回2个属性定义", 2, defs.size());
        assertEquals("so2", defs.get(0).getAttrId());
        assertEquals("status", defs.get(1).getAttrId());
    }

    /**
     * 测试：getAllMappingTypes 返回的集合是不可修改的
     */
    @Test(expected = UnsupportedOperationException.class)
    public void testGetAllMappingTypesUnmodifiable() {
        LogicMappingManager manager = new LogicMappingManager();
        manager.registerMapping(new TestDeviceMapping(
                "SO2", "com.ecat:integration-saimosen", "SMS8200"));

        Set<String> types = manager.getAllMappingTypes();
        types.add("SHOULD_FAIL"); // should throw UnsupportedOperationException
    }
}
