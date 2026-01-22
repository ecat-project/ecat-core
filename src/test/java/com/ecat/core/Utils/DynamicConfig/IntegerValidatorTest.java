package com.ecat.core.Utils.DynamicConfig;

import org.junit.*;
import static org.junit.Assert.*;

/**
 * IntegerValidator 单元测试
 * 
 * @author coffee
 */
public class IntegerValidatorTest {

    /**
     * 测试构造方法和边界参数
     */
    @Test
    public void testConstructorAndErrorMessage() {
        IntegerValidator validator = new IntegerValidator(1, 10);
        assertEquals("整数值必须在 1 到 10 之间", validator.getErrorMessage());
    }

    /**
     * 测试 validate 方法：范围内
     */
    @Test
    public void testValidateInRange() {
        IntegerValidator validator = new IntegerValidator(1, 10);
        assertTrue(validator.validate(1));
        assertTrue(validator.validate(5));
        assertTrue(validator.validate(10));
    }

    /**
     * 测试 validate 方法：范围外
     */
    @Test
    public void testValidateOutOfRange() {
        IntegerValidator validator = new IntegerValidator(1, 10);
        assertFalse(validator.validate(0));
        assertFalse(validator.validate(11));
        assertFalse(validator.validate(-5));
    }

    /**
     * 测试 validate 方法：null 输入
     */
    @Test
    public void testValidateNull() {
        IntegerValidator validator = new IntegerValidator(1, 10);
        assertFalse(validator.validate(null));
    }
}
