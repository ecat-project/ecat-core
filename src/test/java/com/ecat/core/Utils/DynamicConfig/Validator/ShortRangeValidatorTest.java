package com.ecat.core.Utils.DynamicConfig.Validator;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.ecat.core.Utils.DynamicConfig.ConstraintValidator;

public class ShortRangeValidatorTest {

    private ConstraintValidator<Short> validator;

    @Before
    public void setUp() {
        validator = new ShortRangeValidator((short) 0, (short) 100);
    }

    @Test
    public void testValidValues() {
        assertTrue(validator.validate((short) 0));
        assertTrue(validator.validate((short) 50));
        assertTrue(validator.validate((short) 100));
    }

    @Test
    public void testInvalidValues() {
        assertFalse(validator.validate((short) -1));
        assertFalse(validator.validate((short) 101));
    }

    @Test
    public void testNullValue() {
        assertFalse(validator.validate(null));
    }

    @Test
    public void testErrorMessage() {
        String errorMessage = validator.getErrorMessage();
        assertTrue(errorMessage.contains("0"));
        assertTrue(errorMessage.contains("100"));
    }

    @Test
    public void testGetters() {
        ShortRangeValidator rangeValidator = (ShortRangeValidator) validator;
        assertEquals(0, rangeValidator.getMinValue());
        assertEquals(100, rangeValidator.getMaxValue());
    }
}