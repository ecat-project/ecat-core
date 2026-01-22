package com.ecat.core.State.Unit;

import com.ecat.core.State.UnitInfo;
import org.junit.Assert;
import org.junit.Test;

/**
 * UnitInfoFactoryTest for testing UnitInfoFactory functionality.
 * 
 * @author coffee
 */
public class UnitInfoFactoryTest {

    public enum NotUnit { AAA }

    @Test
    public void testGetEnumByShortName_success() {
        String fullName = "AirMassUnit.UGM3";
        UnitInfo info = UnitInfoFactory.getEnum(fullName);
        Assert.assertEquals(AirMassUnit.UGM3, info);
        // 缓存命中
        UnitInfo info2 = UnitInfoFactory.getEnum(fullName);
        Assert.assertSame(info, info2);
    }

    @Test
    public void testGetEnum_success() {
        String fullName = AirMassUnit.class.getName() + ".UGM3";
        UnitInfo info = UnitInfoFactory.getEnum(fullName);
        Assert.assertEquals(AirMassUnit.UGM3, info);
        // 缓存命中
        UnitInfo info2 = UnitInfoFactory.getEnum(fullName);
        Assert.assertSame(info, info2);
    }

    @Test
    public void testGetEnum_nullOrEmpty() {
        try {
            UnitInfoFactory.getEnum(null);
            Assert.fail();
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("must not be empty"));
        }
        try {
            UnitInfoFactory.getEnum("");
            Assert.fail();
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("must not be empty"));
        }
        try {
            UnitInfoFactory.getEnum("   ");
            Assert.fail();
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("must not be empty"));
        }
    }

    @Test
    public void testGetEnum_formatError() {
        try {
            UnitInfoFactory.getEnum("AirMassUnitUGM3");
            Assert.fail();
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("must be in format"));
        }
        try {
            UnitInfoFactory.getEnum(".UGM3");
            Assert.fail();
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("must be in format"));
        }
    }

    @Test
    public void testGetEnum_classNotFound() {
        String fullName = "com.ecat.core.State.Unit.NotExistUnit.UGM3";
        try {
            UnitInfoFactory.getEnum(fullName);
            Assert.fail();
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("Enum class not found"));
        }
    }

    @Test
    public void testGetEnum_notEnumClass() {
        String fullName = UnitInfoFactory.class.getName() + ".UGM3";
        try {
            UnitInfoFactory.getEnum(fullName);
            Assert.fail();
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("No enum constant com.ecat.core.State.Unit.UnitInfoFactory.UGM3"));
        }
    }

    @Test
    public void testGetEnum_notImplementUnitInfo() {
        // 定义一个不实现UnitInfo的枚举
        String fullName = NotUnit.class.getName() + ".AAA";
        try {
            UnitInfoFactory.getEnum(fullName);
            Assert.fail();
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("No enum constant com.ecat.core.State.Unit.UnitInfoFactoryTest$NotUnit.AAA"));
        }
    }

    @Test
    public void testGetEnum_enumConstantNotFound() {
        String fullName = AirMassUnit.class.getName() + ".NOT_EXIST";
        try {
            UnitInfoFactory.getEnum(fullName);
            Assert.fail();
        } catch (IllegalArgumentException e) {
            Assert.assertTrue(e.getMessage().contains("No enum constant"));
        }
    }
}
