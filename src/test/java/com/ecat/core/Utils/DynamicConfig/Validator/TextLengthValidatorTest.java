package com.ecat.core.Utils.DynamicConfig.Validator;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.ecat.core.Utils.DynamicConfig.ConstraintValidator;

public class TextLengthValidatorTest {

    private ConstraintValidator<String> validator;

    @Before
    public void setUp() {
        validator = new TextLengthValidator(1, 10);
    }

    @Test
    public void testValidLength() {
        assertTrue(validator.validate("a"));
        assertTrue(validator.validate("abc"));
        assertTrue(validator.validate("abcdefghij"));
    }

    @Test
    public void testInvalidLength() {
        assertFalse(validator.validate(""));
        assertFalse(validator.validate("abcdefghijk"));
    }

    @Test
    public void testNullValue() {
        assertFalse(validator.validate(null));
    }

    @Test
    public void testAllowEmptyString() {
        ConstraintValidator<String> emptyValidator = new TextLengthValidator(0, 10);
        assertTrue(emptyValidator.validate(""));
    }

    @Test
    public void testErrorMessage() {
        String errorMessage = validator.getErrorMessage();
        assertTrue(errorMessage.contains("1"));
        assertTrue(errorMessage.contains("10"));
    }

    @Test
    public void testGetters() {
        TextLengthValidator lengthValidator = (TextLengthValidator) validator;
        assertEquals(1, lengthValidator.getMinLength());
        assertEquals(10, lengthValidator.getMaxLength());
    }
}