package com.ecat.core.State.Unit;

import com.ecat.core.State.UnitInfo;
import org.junit.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * {@link UnitRegistry} 单元测试:验证登记的 17 个单位类均为 enum 且实现
 * {@link com.ecat.core.State.Unit.InternationalizedUnit}、扁平化常量正确、
 * 排除 NoConversionUnit、返回不可变。
 */
public class UnitRegistryTest {

    @Test
    public void allUnitClasses_containsKnownClassesAndIsImmutable() {
        List<Class<? extends UnitInfo>> classes = UnitRegistry.allUnitClasses();

        // 已核实的 17 个 InternationalizedUnit enum 类
        assertEquals(17, classes.size());
        assertTrue(classes.contains(VoltageUnit.class));
        assertTrue(classes.contains(AirMassUnit.class));
        assertTrue(classes.contains(AirVolumeUnit.class));
        assertTrue(classes.contains(WeightUnit.class));

        // 每个登记类都是 enum 且实现 InternationalizedUnit
        for (Class<?> cls : classes) {
            assertTrue(cls.getSimpleName() + " 应为 enum", cls.isEnum());
            assertTrue(cls.getSimpleName() + " 应实现 InternationalizedUnit",
                    InternationalizedUnit.class.isAssignableFrom(cls));
        }

        // NoConversionUnit(非 enum、不实现 InternationalizedUnit)必须不在列
        assertFalse(classes.contains(NoConversionUnit.class));

        // 不可变
        try {
            classes.add(VoltageUnit.class);
            fail("allUnitClasses() 应不可变");
        } catch (UnsupportedOperationException expected) {
            // 期望
        }
    }

    @Test
    public void allUnits_flattensConstantsAndExcludesNoConversionUnit() {
        List<UnitInfo> units = UnitRegistry.allUnits();

        assertNotNull(units);
        assertFalse("allUnits() 不应为空", units.isEmpty());

        // 已知关键常量存在(zhiqwl output_unit 使用)
        Set<String> fullNames = new HashSet<>();
        for (UnitInfo u : units) {
            fullNames.add(u.getFullUnitString());
        }
        assertTrue("应含 VoltageUnit.VOLT", fullNames.contains("VoltageUnit.VOLT"));
        assertTrue("应含 AirMassUnit.UGM3", fullNames.contains("AirMassUnit.UGM3"));

        // NoConversionUnit 是非 enum 包装类,绝不应出现
        for (UnitInfo u : units) {
            assertFalse("不应含 NoConversionUnit 实例",
                    u instanceof NoConversionUnit);
        }

        // 计数一致:allUnits 总数 == 各登记类 getEnumConstants 之和
        int expected = 0;
        for (Class<? extends UnitInfo> cls : UnitRegistry.allUnitClasses()) {
            expected += cls.getEnumConstants().length;
        }
        assertEquals(expected, units.size());

        // 不可变
        try {
            units.add(VoltageUnit.VOLT);
            fail("allUnits() 应不可变");
        } catch (UnsupportedOperationException expected2) {
            // 期望
        }
    }

    @Test
    public void getFullUnitString_formatIsClassNameDotConstant() {
        // 验证 key 格式可被 UnitInfoFactory.getEnum 反向解析(双向闭环)
        assertEquals("VoltageUnit.VOLT", VoltageUnit.VOLT.getFullUnitString());
        assertEquals("AirMassUnit.UGM3", AirMassUnit.UGM3.getFullUnitString());
    }
}
