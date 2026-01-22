package com.ecat.core.Utils.DynamicConfig.Validator;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.ecat.core.Utils.DynamicConfig.ConstraintValidator;

public class BooleanValidatorTest {

    private ConstraintValidator<Boolean> validator;

    @Before
    public void setUp() {
        validator = new BooleanValidator();
    }

    @Test
    public void testValidValues() {
        assertTrue(validator.validate(true));
        assertTrue(validator.validate(false));
    }

    @Test
    public void testNullValue() {
        assertFalse(validator.validate(null));
    }

    @Test
    public void testErrorMessage() {
        String errorMessage = validator.getErrorMessage();
        assertEquals("值必须为布尔类型", errorMessage);
    }
}