package com.ecat.core.Utils.DynamicConfig.Validator;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.ecat.core.Utils.DynamicConfig.ConstraintValidator;

public class NumericRangeValidatorTest {

    private ConstraintValidator<Double> validator;

    @Before
    public void setUp() {
        validator = new NumericRangeValidator(0.0, 100.0);
    }

    @Test
    public void testValidValues() {
        assertTrue(validator.validate(0.0));
        assertTrue(validator.validate(50.0));
        assertTrue(validator.validate(100.0));
    }

    @Test
    public void testInvalidValues() {
        assertFalse(validator.validate(-1.0));
        assertFalse(validator.validate(101.0));
    }

    @Test
    public void testNullValue() {
        assertFalse(validator.validate(null));
    }

    @Test
    public void testErrorMessage() {
        String errorMessage = validator.getErrorMessage();
        assertTrue(errorMessage.contains("0.0"));
        assertTrue(errorMessage.contains("100.0"));
    }

    @Test
    public void testGetters() {
        NumericRangeValidator rangeValidator = (NumericRangeValidator) validator;
        assertEquals(0.0, rangeValidator.getMinValue(), 0.001);
        assertEquals(100.0, rangeValidator.getMaxValue(), 0.001);
    }
}