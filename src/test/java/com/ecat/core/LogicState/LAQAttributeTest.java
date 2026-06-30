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

import com.ecat.core.Device.DeviceBase;
import com.ecat.core.State.AQAttribute;
import com.ecat.core.State.AttributeBase;
import com.ecat.core.State.AttributeClass;
import com.ecat.core.State.AttributeStatus;
import com.ecat.core.State.AttributeType;
import com.ecat.core.State.AttrState;
import com.ecat.core.State.Unit.AirMassUnit;
import com.ecat.core.State.Unit.AirVolumeUnit;
import com.ecat.core.Science.AirQuality.Consts.MolecularWeights;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Tests for LAQAttribute - single physical attribute to single logic attribute delegation.
 *
 * @author coffee
 */
public class LAQAttributeTest {

    @Mock
    private AttributeClass mockAttrClass;

    private AQAttribute bindAttr;
    private static final Double SO2_MW = MolecularWeights.SO2;

    /**
     * 绑定一个 stub getId() 非 null 的 mock 设备，使 AttributeBase.getState() 在 updateValue 后返回非 null 的不可变 AttrState。
     * <p>这些 AQ 参数构造时未显式设 persistable（默认 false），updateValue 不会走持久化分支，
     * 故无需 stub getCore()/getStateManager()。若未来参数改为 persistable=true，则需补 stub core。
     */
    private static void bindDevice(AttributeBase<?> attr) {
        DeviceBase mockDevice = mock(DeviceBase.class);
        when(mockDevice.getId()).thenReturn("testDevice");
        attr.setDevice(mockDevice);
    }

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockAttrClass.getDisplayName()).thenReturn("TestAttrClass");
        when(mockAttrClass.isValidUnit(any())).thenReturn(true);
    }

    /**
     * Creates a physical AQAttribute with AirMassUnit (mg/m3) and molecularWeight.
     * Used for same-class conversion tests (mg/m3 -> ug/m3).
     */
    private AQAttribute createMassBindAttr() {
        return new AQAttribute(
                "physical_so2",
                mockAttrClass,
                AirMassUnit.MGM3,   // physical native unit
                AirMassUnit.MGM3,   // physical display unit
                2,
                false,
                false,
                SO2_MW
        );
    }

    /**
     * Creates a physical AQAttribute with AirVolumeUnit (PPB) and molecularWeight.
     * Used for cross-class conversion tests (PPB -> ug/m3).
     */
    private AQAttribute createVolumeBindAttr() {
        return new AQAttribute(
                "physical_so2",
                mockAttrClass,
                AirVolumeUnit.PPB,   // physical native unit
                AirVolumeUnit.PPB,   // physical display unit
                2,
                false,
                false,
                SO2_MW
        );
    }

    /**
     * Creates a physical AQAttribute with AirVolumeUnit (PPB) but NO molecularWeight.
     * Used to verify that cross-class conversion throws when molecularWeight is missing.
     */
    private AQAttribute createVolumeBindAttrNoMW() {
        return new AQAttribute(
                "physical_so2",
                mockAttrClass,
                AirVolumeUnit.PPB,
                AirVolumeUnit.PPB,
                2,
                false,
                false,
                null   // no molecularWeight
        );
    }

    // ========== testUpdateBindAttrValuePassthrough (same-class) ==========

    @Test
    public void testUpdateBindAttrValuePassthroughSameClass() {
        // Physical attr uses MGM3, logic attr will use UGM3 after initFromDefinition
        // Constructor uses bindAttr's native unit (MGM3) as initial value
        bindAttr = createMassBindAttr();
        LAQAttribute logicAttr = new LAQAttribute(bindAttr, SO2_MW);

        // Set logic attr native unit to UGM3 (simulating initFromDefinition)
        logicAttr.initNativeUnit(AirMassUnit.UGM3);

        // 绑定设备：getState() 在 updateValue 前为 null，须先绑定设备（getId 非 null）才返回不可变状态
        bindDevice(bindAttr);
        bindDevice(logicAttr);

        // Simulate physical attribute being updated with 50.0 mg/m3
        bindAttr.updateValue(50.0, AttributeStatus.NORMAL);

        // Trigger logic attribute update - converts 50.0 mg/m3 -> 50000.0 ug/m3
        // 传不可变 AttrState（取代旧 AttributeBase 入参，getValue/getStatus 已降 protected）
        logicAttr.updateBindAttrValue(bindAttr.getState());

        AttrState<?> state = logicAttr.getState();
        assertNotNull(state.getValue());
        assertEquals(50000.0, (Double) state.getValue(), 0.01);
        assertEquals(AttributeStatus.NORMAL, state.getStatus());
    }

    // ========== testUpdateBindAttrValuePassthroughCrossClass ==========

    @Test
    public void testUpdateBindAttrValuePassthroughCrossClass() {
        // Physical attr uses PPB, logic attr uses UGM3
        // This tests the core fix: cross-class conversion with molecularWeight
        bindAttr = createVolumeBindAttr();
        LAQAttribute logicAttr = new LAQAttribute(bindAttr, SO2_MW);

        // Set logic attr native unit to UGM3 (simulating initFromDefinition)
        logicAttr.initNativeUnit(AirMassUnit.UGM3);

        // 绑定设备：getState() 在 updateValue 前为 null，须先绑定设备才返回不可变状态
        bindDevice(bindAttr);
        bindDevice(logicAttr);

        // Simulate physical attribute being updated with 15.5 PPB
        bindAttr.updateValue(15.5, AttributeStatus.NORMAL);

        // Trigger logic attribute update - converts 15.5 PPB -> ~41.97 ug/m3
        // 传不可变 AttrState（取代旧 AttributeBase 入参，getValue/getStatus 已降 protected）
        logicAttr.updateBindAttrValue(bindAttr.getState());

        AttrState<?> state = logicAttr.getState();
        assertNotNull(state.getValue());
        // SO2: 1 ppb = MW / MolarVolume ug/m3 = 64.066 / 24.465 ug/m3
        // 15.5 ppb = 15.5 * 64.066 / 24.465 = ~40.60 ug/m3
        double expected = 15.5 * SO2_MW / 24.465;
        assertEquals(expected, (Double) state.getValue(), 0.01);
        assertEquals(AttributeStatus.NORMAL, state.getStatus());
    }

    // ========== testUpdateBindAttrValueCrossClassNoMWThrows ==========

    @Test(expected = IllegalStateException.class)
    public void testUpdateBindAttrValueCrossClassNoMWThrows() {
        // Physical attr uses PPB, but NO molecularWeight
        // Cross-class conversion should throw IllegalStateException
        bindAttr = createVolumeBindAttrNoMW();
        // LAQAttribute with null molecularWeight
        LAQAttribute logicAttr = new LAQAttribute(bindAttr, null);

        // Set logic attr native unit to UGM3 (cross-class from PPB)
        logicAttr.initNativeUnit(AirMassUnit.UGM3);

        bindDevice(bindAttr);
        bindAttr.updateValue(15.5, AttributeStatus.NORMAL);

        // This should throw because cross-class conversion needs molecularWeight
        // 传不可变 AttrState（取代旧 AttributeBase 入参）
        logicAttr.updateBindAttrValue(bindAttr.getState());
    }

    // ========== testInitFromDefinition ==========

    @Test
    public void testInitFromDefinition() {
        bindAttr = createMassBindAttr();
        LAQAttribute logicAttr = new LAQAttribute(bindAttr, SO2_MW);

        LogicAttributeDefine def = new LogicAttributeDefine(
                "logic_so2",
                AttributeClass.SO2,
                AirMassUnit.UGM3,
                AirMassUnit.MGM3,
                3,
                true,
                AQAttribute.class
        );

        logicAttr.initFromDefinition(def);

        // Verify attributeID, nativeUnit, valueChangeable were set correctly
        assertEquals("logic_so2", logicAttr.getAttributeID());
        assertEquals(AirMassUnit.UGM3, logicAttr.getNativeUnit());
        assertTrue(logicAttr.canValueChange());
        assertEquals(3, logicAttr.getDisplayPrecision());
        // unitChangeable=false, so changeDisplayUnit(MGM3) has no effect;
        // displayUnit stays at constructor value (bindAttr's nativeUnit = MGM3)
        assertEquals(AirMassUnit.MGM3, logicAttr.getDisplayUnit());
    }

    // ========== testGetBindedAttrs ==========

    @Test
    public void testGetBindedAttrs() {
        bindAttr = createMassBindAttr();
        LAQAttribute logicAttr = new LAQAttribute(bindAttr, SO2_MW);

        List<AttributeBase<?>> binded = logicAttr.getBindedAttrs();

        assertNotNull(binded);
        assertEquals(1, binded.size());
        assertSame(bindAttr, binded.get(0));
    }

    // ========== testSetDisplayValuePassthrough ==========

    @Test
    public void testSetDisplayValuePassthroughSameClass() {
        // Make bindAttr valueChangeable so it accepts setDisplayValue
        AQAttribute changeableBindAttr = new AQAttribute(
                "physical_so2",
                mockAttrClass,
                AirMassUnit.MGM3,
                AirMassUnit.MGM3,
                2,
                false,
                true,  // valueChangeable = true
                SO2_MW
        );
        LAQAttribute logicAttr = new LAQAttribute(changeableBindAttr, SO2_MW);
        logicAttr.initNativeUnit(AirMassUnit.UGM3);

        // 绑定设备：setDisplayValue 最终调用 changeableBindAttr.updateValue，须先绑定设备才让 getState() 可读
        bindDevice(changeableBindAttr);

        // Set display value in UGM3 - passthrough converts to MGM3 for bindAttr
        CompletableFuture<Boolean> result = logicAttr.setDisplayValue("50000.0", AirMassUnit.UGM3);

        assertTrue(result.isDone());
        // 50000.0 ug/m3 = 50.0 mg/m3（读不可变 AttrState，getValue 已降 protected）
        assertEquals(50.0, (Double) changeableBindAttr.getState().getValue(), 0.001);
    }

    // ========== Additional tests ==========

    @Test
    public void testConstructorSetsBasicProperties() {
        bindAttr = createMassBindAttr();
        LAQAttribute logicAttr = new LAQAttribute(bindAttr, SO2_MW);

        // Verify constructor passes bindAttr info to super
        assertEquals("physical_so2", logicAttr.getAttributeID());
        assertEquals(AirMassUnit.MGM3, logicAttr.getNativeUnit());
        assertEquals(AirMassUnit.MGM3, logicAttr.getDisplayUnit());
        assertFalse(logicAttr.canUnitChange());
        assertFalse(logicAttr.canValueChange());
        assertEquals(AttributeType.NUMERIC, logicAttr.getAttributeType());
        assertEquals(SO2_MW, logicAttr.molecularWeight, 0.001);
    }

    @Test
    public void testUpdateBindAttrValueNullValue() {
        bindAttr = createMassBindAttr();
        LAQAttribute logicAttr = new LAQAttribute(bindAttr, SO2_MW);

        // 绑定设备：getState() 在 updateValue 前为 null，须先绑定设备才返回不可变状态
        bindDevice(bindAttr);
        bindDevice(logicAttr);

        // Update physical attribute with null value
        bindAttr.updateValue(null, AttributeStatus.EMPTY);

        // 传不可变 AttrState（取代旧 AttributeBase 入参，getValue/getStatus 已降 protected）
        logicAttr.updateBindAttrValue(bindAttr.getState());

        AttrState<?> state = logicAttr.getState();
        assertNull(state.getValue());
        assertEquals(AttributeStatus.EMPTY, state.getStatus());
    }

    @Test
    public void testInitAttributeID() {
        bindAttr = createMassBindAttr();
        LAQAttribute logicAttr = new LAQAttribute(bindAttr, SO2_MW);

        logicAttr.initAttributeID("new_logic_id");

        assertEquals("new_logic_id", logicAttr.getAttributeID());
    }

    @Test
    public void testInitNativeUnit() {
        bindAttr = createMassBindAttr();
        LAQAttribute logicAttr = new LAQAttribute(bindAttr, SO2_MW);

        logicAttr.initNativeUnit(AirMassUnit.MGM3);

        assertEquals(AirMassUnit.MGM3, logicAttr.getNativeUnit());
    }

    @Test
    public void testInitValueChangeable() {
        bindAttr = createMassBindAttr();
        LAQAttribute logicAttr = new LAQAttribute(bindAttr, SO2_MW);

        logicAttr.initValueChangeable(true);

        assertTrue(logicAttr.canValueChange());
    }

    @Test
    public void testConstructorWithNullMolecularWeight() {
        bindAttr = createMassBindAttr();
        // null molecularWeight is valid for same-class conversion only
        LAQAttribute logicAttr = new LAQAttribute(bindAttr, null);
        assertNull(logicAttr.molecularWeight);
    }
}
