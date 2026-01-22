package com.ecat.core.Utils.DynamicConfig.Validator;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.ecat.core.Utils.DynamicConfig.ConstraintValidator;

public class FloatRangeValidatorTest {

    private ConstraintValidator<Float> validator;

    @Before
    public void setUp() {
        validator = new FloatRangeValidator(0.0f, 100.0f);
    }

    @Test
    public void testValidValues() {
        assertTrue(validator.validate(0.0f));
        assertTrue(validator.validate(50.0f));
        assertTrue(validator.validate(100.0f));
    }

    @Test
    public void testInvalidValues() {
        assertFalse(validator.validate(-1.0f));
        assertFalse(validator.validate(101.0f));
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
        FloatRangeValidator rangeValidator = (FloatRangeValidator) validator;
        assertEquals(0.0f, rangeValidator.getMinValue(), 0.001f);
        assertEquals(100.0f, rangeValidator.getMaxValue(), 0.001f);
    }
}