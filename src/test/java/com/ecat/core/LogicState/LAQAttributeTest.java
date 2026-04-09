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

import com.ecat.core.State.AQAttribute;
import com.ecat.core.State.AttributeBase;
import com.ecat.core.State.AttributeClass;
import com.ecat.core.State.AttributeStatus;
import com.ecat.core.State.AttributeType;
import com.ecat.core.State.Unit.AirMassUnit;
import com.ecat.core.State.Unit.AirVolumeUnit;
import com.ecat.core.State.UnitInfo;
import com.ecat.core.Science.AirQuality.Consts.MolecularWeights;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

import static org.junit.Assert.*;
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

        // Simulate physical attribute being updated with 50.0 mg/m3
        bindAttr.updateValue(50.0, AttributeStatus.NORMAL);

        // Trigger logic attribute update - converts 50.0 mg/m3 -> 50000.0 ug/m3
        logicAttr.updateBindAttrValue(bindAttr);

        assertNotNull(logicAttr.getValue());
        assertEquals(50000.0, logicAttr.getValue(), 0.01);
        assertEquals(AttributeStatus.NORMAL, logicAttr.getStatus());
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

        // Simulate physical attribute being updated with 15.5 PPB
        bindAttr.updateValue(15.5, AttributeStatus.NORMAL);

        // Trigger logic attribute update - converts 15.5 PPB -> ~41.97 ug/m3
        logicAttr.updateBindAttrValue(bindAttr);

        assertNotNull(logicAttr.getValue());
        // SO2: 1 ppb = MW / MolarVolume ug/m3 = 64.066 / 24.465 ug/m3
        // 15.5 ppb = 15.5 * 64.066 / 24.465 = ~40.60 ug/m3
        double expected = 15.5 * SO2_MW / 24.465;
        assertEquals(expected, logicAttr.getValue(), 0.01);
        assertEquals(AttributeStatus.NORMAL, logicAttr.getStatus());
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

        bindAttr.updateValue(15.5, AttributeStatus.NORMAL);

        // This should throw because cross-class conversion needs molecularWeight
        logicAttr.updateBindAttrValue(bindAttr);
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

        // Set display value in UGM3 - passthrough converts to MGM3 for bindAttr
        CompletableFuture<Boolean> result = logicAttr.setDisplayValue("50000.0", AirMassUnit.UGM3);

        assertTrue(result.isDone());
        // 50000.0 ug/m3 = 50.0 mg/m3
        assertEquals(50.0, changeableBindAttr.getValue(), 0.001);
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

        // Update physical attribute with null value
        bindAttr.updateValue(null, AttributeStatus.EMPTY);

        logicAttr.updateBindAttrValue(bindAttr);

        assertNull(logicAttr.getValue());
        assertEquals(AttributeStatus.EMPTY, logicAttr.getStatus());
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
