package com.ecat.core.Utils.DynamicConfig.Validator;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.ecat.core.Utils.DynamicConfig.ConstraintValidator;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class DynamicStringDictValidatorTest {

    private ConstraintValidator<String> validator;
    private Supplier<Map<String, String>> dictSupplier;

    @Before
    public void setUp() {
        dictSupplier = () -> {
            Map<String, String> dict = new HashMap<>();
            dict.put("option1", "选项1");
            dict.put("option2", "选项2");
            dict.put("option3", "选项3");
            return dict;
        };
        validator = new DynamicStringDictValidator(dictSupplier);
    }

    @Test
    public void testValidValues() {
        assertTrue(validator.validate("option1"));
        assertTrue(validator.validate("option2"));
        assertTrue(validator.validate("option3"));
    }

    @Test
    public void testInvalidValues() {
        assertFalse(validator.validate("option4"));
        assertFalse(validator.validate("invalid"));
        assertFalse(validator.validate(""));
    }

    @Test
    public void testNullValue() {
        assertFalse(validator.validate(null));
    }

    @Test
    public void testCaseSensitive() {
        DynamicStringDictValidator caseSensitiveValidator = new DynamicStringDictValidator(dictSupplier, true);
        assertTrue(caseSensitiveValidator.validate("option1"));
        assertFalse(caseSensitiveValidator.validate("OPTION1"));
    }

    @Test
    public void testCaseInsensitive() {
        DynamicStringDictValidator caseInsensitiveValidator = new DynamicStringDictValidator(dictSupplier, false);
        assertTrue(caseInsensitiveValidator.validate("option1"));
        assertTrue(caseInsensitiveValidator.validate("OPTION1"));
        assertTrue(caseInsensitiveValidator.validate("Option1"));
    }

    @Test
    public void testEmptyDictionary() {
        Supplier<Map<String, String>> emptySupplier = HashMap::new;
        ConstraintValidator<String> emptyValidator = new DynamicStringDictValidator(emptySupplier);
        assertFalse(emptyValidator.validate("option1"));
    }

    @Test
    public void testErrorMessage() {
        String errorMessage = validator.getErrorMessage();
        assertTrue(errorMessage.contains("option1"));
        assertTrue(errorMessage.contains("option2"));
        assertTrue(errorMessage.contains("option3"));
    }

    @Test
    public void testAdditionalMethods() {
        DynamicStringDictValidator dynamicValidator = (DynamicStringDictValidator) validator;

        // Test getValidDict
        Map<String, String> validDict = dynamicValidator.getValidDict();
        assertEquals(3, validDict.size());
        assertTrue(validDict.containsKey("option1"));

        // Test findMatchingKey
        assertEquals("option1", dynamicValidator.findMatchingKey("option1"));
        assertNull(dynamicValidator.findMatchingKey("invalid"));

        // Test isCaseSensitive
        assertTrue(dynamicValidator.isCaseSensitive());

        // Test getDictSize
        assertEquals(3, dynamicValidator.getDictSize());

        // Test clearCache
        dynamicValidator.clearCache();
        assertEquals(3, dynamicValidator.getDictSize()); // Should work after cache clear
    }
}