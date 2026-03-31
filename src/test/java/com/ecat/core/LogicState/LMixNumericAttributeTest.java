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
import com.ecat.core.State.UnitInfo;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

/**
 * Tests for LMixNumericAttribute - multi physical to single logic aggregation.
 *
 * <p>Uses a concrete test subclass {@link SumMixNumericAttribute} that sums
 * all bound physical attribute values.
 *
 * @author coffee
 */
public class LMixNumericAttributeTest {

    @Mock
    private AttributeClass mockAttrClass;

    private NumericAttribute phyAttr1;
    private NumericAttribute phyAttr2;

    @Before
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        when(mockAttrClass.getDisplayName()).thenReturn("TestAttrClass");
        when(mockAttrClass.isValidUnit(any())).thenReturn(true);

        // Create two real NumericAttribute instances as physical attributes
        phyAttr1 = new NumericAttribute(
                "temperature_1", mockAttrClass, null, null, 2, false, false);
        phyAttr2 = new NumericAttribute(
                "temperature_2", mockAttrClass, null, null, 2, false, false);
    }

    /**
     * Concrete test subclass of LMixNumericAttribute that sums all bound physical attribute values.
     */
    private static class SumMixNumericAttribute extends LMixNumericAttribute {
        public SumMixNumericAttribute(String attributeID, AttributeClass attrClass,
                UnitInfo nativeUnit, UnitInfo displayUnit, int displayPrecision, long windowSize) {
            super(attributeID, attrClass, nativeUnit, displayUnit, displayPrecision, windowSize);
        }

        @Override
        protected Double calcRawValue() {
            double sum = 0.0;
            for (BindedPhyAttrData bad : bindAttrs.values()) {
                Object val = bad.getBindPhyAttr().getValue();
                if (val != null) {
                    sum += ((Number) val).doubleValue();
                }
            }
            return sum;
        }
    }

    // ========== testRegisterBindAttrValue ==========

    @Test
    public void testRegisterBindAttrValue() {
        SumMixNumericAttribute mixAttr = new SumMixNumericAttribute(
                "avg_temperature", mockAttrClass, null, null, 2, 5000);

        // Register two physical attributes
        mixAttr.registerBindAttrValue(phyAttr1);
        mixAttr.registerBindAttrValue(phyAttr2);

        List<AttributeBase<?>> binded = mixAttr.getBindedAttrs();

        assertNotNull(binded);
        assertEquals(2, binded.size());
        assertSame(phyAttr1, binded.get(0));
        assertSame(phyAttr2, binded.get(1));
    }

    // ========== testSetDisplayValueThrows ==========

    @Test(expected = UnsupportedOperationException.class)
    public void testSetDisplayValueThrows() {
        SumMixNumericAttribute mixAttr = new SumMixNumericAttribute(
                "avg_temperature", mockAttrClass, null, null, 2, 5000);
        mixAttr.registerBindAttrValue(phyAttr1);
        mixAttr.registerBindAttrValue(phyAttr2);

        // LMixNumericAttribute is read-only - should throw
        mixAttr.setDisplayValue("100.0", null);
    }

    // ========== testSynchronizedUpdateBindAttrValue ==========

    @Test
    public void testSynchronizedUpdateBindAttrValue() {
        SumMixNumericAttribute mixAttr = new SumMixNumericAttribute(
                "avg_temperature", mockAttrClass, null, null, 2, 5000);
        mixAttr.registerBindAttrValue(phyAttr1);
        mixAttr.registerBindAttrValue(phyAttr2);

        // Update both physical attributes within the time window
        phyAttr1.updateValue(10.0, AttributeStatus.NORMAL);
        phyAttr2.updateValue(20.0, AttributeStatus.NORMAL);

        mixAttr.updateBindAttrValue(phyAttr1);
        mixAttr.updateBindAttrValue(phyAttr2);

        // calcRawValue should be called and sum = 10.0 + 20.0 = 30.0
        assertNotNull(mixAttr.getValue());
        assertEquals(30.0, mixAttr.getValue(), 0.01);
        assertEquals(AttributeStatus.NORMAL, mixAttr.getStatus());
    }

    // ========== testTimeWindowExpired ==========

    @Test
    public void testTimeWindowExpired() throws InterruptedException {
        // Use a very short window (50ms) so it expires quickly
        SumMixNumericAttribute mixAttr = new SumMixNumericAttribute(
                "avg_temperature", mockAttrClass, null, null, 2, 50);
        mixAttr.registerBindAttrValue(phyAttr1);
        mixAttr.registerBindAttrValue(phyAttr2);

        // Update first physical attribute
        phyAttr1.updateValue(10.0, AttributeStatus.NORMAL);
        mixAttr.updateBindAttrValue(phyAttr1);

        // Wait for the time window to expire
        Thread.sleep(100);

        // Update second physical attribute - first one should now be expired
        phyAttr2.updateValue(20.0, AttributeStatus.NORMAL);
        mixAttr.updateBindAttrValue(phyAttr2);

        // Not all attrs updated within window, value should NOT be updated
        assertNull(mixAttr.getValue());
    }

    // ========== testCalcRawValue ==========

    @Test
    public void testCalcRawValue() {
        SumMixNumericAttribute mixAttr = new SumMixNumericAttribute(
                "avg_temperature", mockAttrClass, null, null, 2, 5000);
        mixAttr.registerBindAttrValue(phyAttr1);
        mixAttr.registerBindAttrValue(phyAttr2);

        // Set physical values
        phyAttr1.updateValue(15.5, AttributeStatus.NORMAL);
        phyAttr2.updateValue(24.5, AttributeStatus.NORMAL);

        // Trigger both updates within window
        mixAttr.updateBindAttrValue(phyAttr1);
        mixAttr.updateBindAttrValue(phyAttr2);

        // Sum should be 15.5 + 24.5 = 40.0
        assertNotNull(mixAttr.getValue());
        assertEquals(40.0, mixAttr.getValue(), 0.01);
    }

    // ========== testUpdateBindAttrValueUnknownAttr ==========

    @Test
    public void testUpdateBindAttrValueUnknownAttr() {
        SumMixNumericAttribute mixAttr = new SumMixNumericAttribute(
                "avg_temperature", mockAttrClass, null, null, 2, 5000);
        mixAttr.registerBindAttrValue(phyAttr1);

        // Update an attribute that is NOT registered
        NumericAttribute unknownAttr = new NumericAttribute(
                "unknown_attr", mockAttrClass, null, null, 2, false, false);
        unknownAttr.updateValue(99.0, AttributeStatus.NORMAL);

        // Should silently ignore the unknown attribute
        mixAttr.updateBindAttrValue(unknownAttr);

        assertNull(mixAttr.getValue());
    }

    // ========== testGetAttributeType ==========

    @Test
    public void testGetAttributeType() {
        SumMixNumericAttribute mixAttr = new SumMixNumericAttribute(
                "avg_temperature", mockAttrClass, null, null, 2, 5000);

        assertEquals(AttributeType.NUMERIC, mixAttr.getAttributeType());
    }

    // ========== testConstructorSetsBasicProperties ==========

    @Test
    public void testConstructorSetsBasicProperties() {
        SumMixNumericAttribute mixAttr = new SumMixNumericAttribute(
                "avg_temperature", mockAttrClass, null, null, 2, 5000);

        assertEquals("avg_temperature", mixAttr.getAttributeID());
        assertFalse(mixAttr.canUnitChange());
        assertFalse(mixAttr.canValueChange());
    }

    // ========== testResetAfterAllUpdated ==========

    @Test
    public void testResetAfterAllUpdated() {
        SumMixNumericAttribute mixAttr = new SumMixNumericAttribute(
                "avg_temperature", mockAttrClass, null, null, 2, 5000);
        mixAttr.registerBindAttrValue(phyAttr1);
        mixAttr.registerBindAttrValue(phyAttr2);

        // First round: both updated
        phyAttr1.updateValue(10.0, AttributeStatus.NORMAL);
        phyAttr2.updateValue(20.0, AttributeStatus.NORMAL);
        mixAttr.updateBindAttrValue(phyAttr1);
        mixAttr.updateBindAttrValue(phyAttr2);
        assertEquals(30.0, mixAttr.getValue(), 0.01);

        // Second round: update only one - should NOT recalculate
        // because the isUpdated flags were reset after first round
        phyAttr1.updateValue(5.0, AttributeStatus.NORMAL);
        mixAttr.updateBindAttrValue(phyAttr1);

        // Value should remain from first round (30.0), not recalculated
        // because phyAttr2 was not updated this round
        assertEquals(30.0, mixAttr.getValue(), 0.01);
    }
}
