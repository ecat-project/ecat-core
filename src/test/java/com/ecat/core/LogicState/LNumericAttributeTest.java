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

package com.ecat.core.LogicState;

import com.ecat.core.State.AttributeBase;
import com.ecat.core.State.AttributeClass;
import com.ecat.core.State.AttributeStatus;
import com.ecat.core.State.AttributeType;
import com.ecat.core.State.NumericAttribute;
import com.ecat.core.State.Unit.LiterFlowUnit;
import com.ecat.core.State.Unit.PressureUnit;
import com.ecat.core.State.UnitInfo;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * LNumericAttribute 单元测试 — 重点覆盖 setDisplayValue 单位转换和 updateBindAttrValue 方向。
 *
 * @author coffee
 */
public class LNumericAttributeTest {

    @Mock
    private AttributeClass mockAttrClass;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockAttrClass.getDisplayName()).thenReturn("TestAttrClass");
        when(mockAttrClass.isValidUnit(any())).thenReturn(true);
    }

    // =====================================================================
    // setDisplayValue — 不同单位（修复前 BUG 的核心场景）
    // =====================================================================

    /**
     * 核心测试：逻辑属性 KPA ↔ 物理属性 PA，setDisplayValue 应正确传递 fromUnit。
     *
     * <p>场景：物理属性 nativeUnit=PA，逻辑属性 nativeUnit=KPA。
     * 调用 logicAttr.setDisplayValue("5.0", KPA)：
     * - 修复前：传 bindAttr.getNativeUnit()=PA，物理属性认为 "5.0 是 Pa" → 存储 5.0 Pa（错误）
     * - 修复后：传 fromUnit=KPA，物理属性正确将 5.0 kPa → 5000.0 Pa（正确）
     */
    @Test
    public void setDisplayValue_differentSameClassUnits_convertsCorrectly() {
        // 物理属性：nativeUnit=PA, valueChangeable=true
        NumericAttribute phyAttr = new NumericAttribute(
                "phy_pressure", mockAttrClass,
                PressureUnit.PA, PressureUnit.PA,
                1, false, true, null);

        LNumericAttribute logicAttr = new LNumericAttribute(phyAttr);
        logicAttr.initNativeUnit(PressureUnit.KPA);
        logicAttr.initDisplayUnit(PressureUnit.KPA);

        // 5.0 kPa → 应该被转换为 5000.0 Pa 存入物理属性
        CompletableFuture<Boolean> result = logicAttr.setDisplayValue("5.0", PressureUnit.KPA);

        assertTrue(result.isDone());
        assertEquals(5000.0, phyAttr.getValue(), 0.001);
    }

    /**
     * 反向场景：逻辑属性 PA ↔ 物理属性 KPA。
     */
    @Test
    public void setDisplayValue_reverseDirection_convertsCorrectly() {
        // 物理属性：nativeUnit=KPA
        NumericAttribute phyAttr = new NumericAttribute(
                "phy_pressure", mockAttrClass,
                PressureUnit.KPA, PressureUnit.KPA,
                2, false, true, null);

        LNumericAttribute logicAttr = new LNumericAttribute(phyAttr);
        logicAttr.initNativeUnit(PressureUnit.PA);
        logicAttr.initDisplayUnit(PressureUnit.PA);

        // 3000.0 Pa → 3.0 kPa
        CompletableFuture<Boolean> result = logicAttr.setDisplayValue("3000.0", PressureUnit.PA);

        assertTrue(result.isDone());
        assertEquals(3.0, phyAttr.getValue(), 0.001);
    }

    /**
     * 流量单位：逻辑 L/min ↔ 物理 L/h。
     */
    @Test
    public void setDisplayValue_flowUnits_convertsCorrectly() {
        // 物理属性：nativeUnit=L/h
        NumericAttribute phyAttr = new NumericAttribute(
                "phy_flow", mockAttrClass,
                LiterFlowUnit.L_PER_HOUR, LiterFlowUnit.L_PER_HOUR,
                2, false, true, null);

        LNumericAttribute logicAttr = new LNumericAttribute(phyAttr);
        logicAttr.initNativeUnit(LiterFlowUnit.L_PER_MINUTE);
        logicAttr.initDisplayUnit(LiterFlowUnit.L_PER_MINUTE);

        // 0.5 L/min → 30.0 L/h
        CompletableFuture<Boolean> result = logicAttr.setDisplayValue("0.5", LiterFlowUnit.L_PER_MINUTE);

        assertTrue(result.isDone());
        assertEquals(30.0, phyAttr.getValue(), 0.01);
    }

    /**
     * 相同单位：fromUnit == bindAttr.nativeUnit，无需转换。
     */
    @Test
    public void setDisplayValue_sameUnit_noConversion() {
        NumericAttribute phyAttr = new NumericAttribute(
                "phy_pressure", mockAttrClass,
                PressureUnit.KPA, PressureUnit.KPA,
                1, false, true, null);

        LNumericAttribute logicAttr = new LNumericAttribute(phyAttr);
        // logic attr 也使用 KPA
        logicAttr.initNativeUnit(PressureUnit.KPA);

        CompletableFuture<Boolean> result = logicAttr.setDisplayValue("5.0", PressureUnit.KPA);

        assertTrue(result.isDone());
        assertEquals(5.0, phyAttr.getValue(), 0.001);
    }

    // =====================================================================
    // setDisplayValue — 不兼容单位类应抛异常
    // =====================================================================

    @Test
    public void setDisplayValue_incompatibleUnitClass_throwsException() {
        // 物理属性：PressureUnit.PA
        NumericAttribute phyAttr = new NumericAttribute(
                "phy_pressure", mockAttrClass,
                PressureUnit.PA, PressureUnit.PA,
                1, false, true, null);

        LNumericAttribute logicAttr = new LNumericAttribute(phyAttr);
        logicAttr.initNativeUnit(PressureUnit.KPA);

        // 传入 LiterFlowUnit — 与 PressureUnit 不兼容
        // AttributeBase.setDisplayValue 内部 catch 异常并返回 failed future
        CompletableFuture<Boolean> result = logicAttr.setDisplayValue("5.0", LiterFlowUnit.L_PER_MINUTE);

        assertTrue(result.isCompletedExceptionally());
    }

    // =====================================================================
    // setDisplayValue — standalone 模式（bindAttr=null）
    // =====================================================================

    @Test
    public void setDisplayValue_standaloneMode_usesSuperImplementation() {
        LNumericAttribute attr = LNumericAttribute.standalone(
                "threshold", mockAttrClass,
                PressureUnit.KPA, PressureUnit.KPA, 2);
        attr.initValueChangeable(true);

        CompletableFuture<Boolean> result = attr.setDisplayValue("5.0", PressureUnit.KPA);

        assertTrue(result.isDone());
        assertEquals(5.0, attr.getValue(), 0.001);
    }

    // =====================================================================
    // updateBindAttrValue — 不同单位（读方向）
    // =====================================================================

    /**
     * 读方向：物理属性 5000 Pa → 逻辑属性应读取为 5.0 KPA。
     */
    @Test
    public void updateBindAttrValue_differentUnits_convertsToLogicNativeUnit() {
        NumericAttribute phyAttr = new NumericAttribute(
                "phy_pressure", mockAttrClass,
                PressureUnit.PA, PressureUnit.PA,
                1, false, false, null);

        LNumericAttribute logicAttr = new LNumericAttribute(phyAttr);
        logicAttr.initNativeUnit(PressureUnit.KPA);

        phyAttr.updateValue(5000.0, AttributeStatus.NORMAL);
        logicAttr.updateBindAttrValue(phyAttr);

        assertEquals(5.0, logicAttr.getValue(), 0.001);
        assertEquals(AttributeStatus.NORMAL, logicAttr.getStatus());
    }

    /**
     * 读方向：流量单位 L/h → L/min。
     */
    @Test
    public void updateBindAttrValue_flowUnits_convertsToLogicNativeUnit() {
        NumericAttribute phyAttr = new NumericAttribute(
                "phy_flow", mockAttrClass,
                LiterFlowUnit.L_PER_HOUR, LiterFlowUnit.L_PER_HOUR,
                2, false, false, null);

        LNumericAttribute logicAttr = new LNumericAttribute(phyAttr);
        logicAttr.initNativeUnit(LiterFlowUnit.L_PER_MINUTE);

        phyAttr.updateValue(30.0, AttributeStatus.NORMAL);
        logicAttr.updateBindAttrValue(phyAttr);

        assertEquals(0.5, logicAttr.getValue(), 0.01);
    }

    /**
     * 读方向：相同单位，无转换。
     */
    @Test
    public void updateBindAttrValue_sameUnit_noConversion() {
        NumericAttribute phyAttr = new NumericAttribute(
                "phy_pressure", mockAttrClass,
                PressureUnit.KPA, PressureUnit.KPA,
                1, false, false, null);

        LNumericAttribute logicAttr = new LNumericAttribute(phyAttr);
        logicAttr.initNativeUnit(PressureUnit.KPA);

        phyAttr.updateValue(5.0, AttributeStatus.NORMAL);
        logicAttr.updateBindAttrValue(phyAttr);

        assertEquals(5.0, logicAttr.getValue(), 0.001);
    }

    /**
     * 读方向：物理值为 null。
     */
    @Test
    public void updateBindAttrValue_nullValue() {
        NumericAttribute phyAttr = new NumericAttribute(
                "phy_pressure", mockAttrClass,
                PressureUnit.PA, PressureUnit.PA,
                1, false, false, null);

        LNumericAttribute logicAttr = new LNumericAttribute(phyAttr);
        logicAttr.initNativeUnit(PressureUnit.KPA);

        phyAttr.updateValue(null, AttributeStatus.EMPTY);
        logicAttr.updateBindAttrValue(phyAttr);

        assertNull(logicAttr.getValue());
    }

    /**
     * 读方向：standalone 模式（bindAttr=null），应直接 return。
     */
    @Test
    public void updateBindAttrValue_standaloneMode_noOp() {
        LNumericAttribute attr = LNumericAttribute.standalone(
                "threshold", mockAttrClass,
                PressureUnit.KPA, PressureUnit.KPA, 2);

        // 不会抛异常
        attr.updateBindAttrValue(null);
        assertNull(attr.getValue());
    }

    // =====================================================================
    // 往返测试（round-trip）：write → read 一致性
    // =====================================================================

    /**
     * 写入 5.0 KPA → 物理属性存 5000 PA → 逻辑属性读回 5.0 KPA。
     */
    @Test
    public void roundTrip_writeThenRead_sameValue() {
        NumericAttribute phyAttr = new NumericAttribute(
                "phy_pressure", mockAttrClass,
                PressureUnit.PA, PressureUnit.PA,
                1, false, true, null);

        LNumericAttribute logicAttr = new LNumericAttribute(phyAttr);
        logicAttr.initNativeUnit(PressureUnit.KPA);
        logicAttr.initDisplayUnit(PressureUnit.KPA);

        // Write: 5.0 kPa
        logicAttr.setDisplayValue("5.0", PressureUnit.KPA);
        assertEquals(5000.0, phyAttr.getValue(), 0.001);

        // Read: 物理属性 5000 Pa → 逻辑属性读回
        logicAttr.updateBindAttrValue(phyAttr);
        assertEquals(5.0, logicAttr.getValue(), 0.001);
    }

    /**
     * 流量单位往返：0.5 L/min → 30 L/h → 读回 0.5 L/min。
     */
    @Test
    public void roundTrip_flowUnits_writeThenRead_sameValue() {
        NumericAttribute phyAttr = new NumericAttribute(
                "phy_flow", mockAttrClass,
                LiterFlowUnit.L_PER_HOUR, LiterFlowUnit.L_PER_HOUR,
                2, false, true, null);

        LNumericAttribute logicAttr = new LNumericAttribute(phyAttr);
        logicAttr.initNativeUnit(LiterFlowUnit.L_PER_MINUTE);
        logicAttr.initDisplayUnit(LiterFlowUnit.L_PER_MINUTE);

        // Write: 0.5 L/min
        logicAttr.setDisplayValue("0.5", LiterFlowUnit.L_PER_MINUTE);
        assertEquals(30.0, phyAttr.getValue(), 0.01);

        // Read: 30 L/h → 读回 L/min
        logicAttr.updateBindAttrValue(phyAttr);
        assertEquals(0.5, logicAttr.getValue(), 0.01);
    }

    // =====================================================================
    // 基本属性测试
    // =====================================================================

    @Test
    public void constructor_boundMode_basicProperties() {
        NumericAttribute phyAttr = new NumericAttribute(
                "phy_temp", mockAttrClass,
                PressureUnit.PA, PressureUnit.PA,
                1, false, false, null);

        LNumericAttribute logicAttr = new LNumericAttribute(phyAttr);

        // Constructor uses bindAttr's metadata as initial values
        assertEquals("phy_temp", logicAttr.getAttributeID());
        assertEquals(PressureUnit.PA, logicAttr.getNativeUnit());
        assertEquals(PressureUnit.PA, logicAttr.getDisplayUnit());
        assertEquals(1, logicAttr.getDisplayPrecision());
        assertFalse(logicAttr.canUnitChange());
        assertFalse(logicAttr.canValueChange());
    }

    @Test
    public void getBindedAttrs_returnsSingletonList() {
        NumericAttribute phyAttr = new NumericAttribute(
                "phy_temp", mockAttrClass,
                PressureUnit.PA, PressureUnit.PA,
                1, false, false, null);

        LNumericAttribute logicAttr = new LNumericAttribute(phyAttr);

        assertEquals(1, logicAttr.getBindedAttrs().size());
        assertSame(phyAttr, logicAttr.getBindedAttrs().get(0));
    }

    @Test
    public void getBindedAttrs_standalone_returnsEmptyList() {
        LNumericAttribute attr = LNumericAttribute.standalone(
                "threshold", mockAttrClass,
                PressureUnit.KPA, PressureUnit.KPA, 2);

        assertTrue(attr.getBindedAttrs().isEmpty());
    }

    @Test
    public void initMethods_setProperties() {
        NumericAttribute phyAttr = new NumericAttribute(
                "phy_temp", mockAttrClass,
                PressureUnit.PA, PressureUnit.PA,
                1, false, false, null);

        LNumericAttribute logicAttr = new LNumericAttribute(phyAttr);

        logicAttr.initAttributeID("logic_pressure");
        assertEquals("logic_pressure", logicAttr.getAttributeID());

        logicAttr.initNativeUnit(PressureUnit.KPA);
        assertEquals(PressureUnit.KPA, logicAttr.getNativeUnit());

        logicAttr.initDisplayUnit(PressureUnit.HPA);
        assertEquals(PressureUnit.HPA, logicAttr.getDisplayUnit());

        logicAttr.initValueChangeable(true);
        assertTrue(logicAttr.canValueChange());
    }

    @Test
    public void getAttributeType_returnsNumeric() {
        NumericAttribute phyAttr = new NumericAttribute(
                "phy_temp", mockAttrClass,
                PressureUnit.PA, PressureUnit.PA,
                1, false, false, null);

        LNumericAttribute logicAttr = new LNumericAttribute(phyAttr);
        assertEquals(AttributeType.NUMERIC, logicAttr.getAttributeType());
    }
}
