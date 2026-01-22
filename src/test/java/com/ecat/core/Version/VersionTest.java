package com.ecat.core.Version;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

/**
 * Version和VersionRange单元测试
 */
public class VersionTest {

    // ========== Version.parse() 测试 ==========

    @Test
    public void testParseStandardVersion() {
        Version v = Version.parse("1.2.0");
        assertEquals(1, v.getMajor());
        assertEquals(2, v.getMinor());
        assertEquals(0, v.getPatch());
        assertNull(v.getPreRelease());
        assertNull(v.getBuildMetadata());
        assertEquals("1.2.0", v.toString());
    }

    @Test
    public void testParseVersionWithPreRelease() {
        Version v = Version.parse("2.0.0-beta.1");
        assertEquals(2, v.getMajor());
        assertEquals(0, v.getMinor());
        assertEquals(0, v.getPatch());
        assertEquals("beta.1", v.getPreRelease());
        assertTrue(v.isPreRelease());
        assertFalse(v.isStable());
    }

    @Test
    public void testParseVersionWithBuildMetadata() {
        Version v = Version.parse("1.2.0+20240101");
        assertEquals(1, v.getMajor());
        assertEquals(2, v.getMinor());
        assertEquals(0, v.getPatch());
        assertNull(v.getPreRelease());
        assertEquals("20240101", v.getBuildMetadata());
        assertTrue(v.isStable());
    }

    @Test
    public void testParseVersionWithBothPreReleaseAndBuild() {
        Version v = Version.parse("1.0.0-alpha.1+001");
        assertEquals(1, v.getMajor());
        assertEquals(0, v.getMinor());
        assertEquals(0, v.getPatch());
        assertEquals("alpha.1", v.getPreRelease());
        assertEquals("001", v.getBuildMetadata());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseInvalidVersion_Null() {
        Version.parse(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseInvalidVersion_Empty() {
        Version.parse("");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseInvalidVersion_MissingPart() {
        Version.parse("1.2");  // 缺少PATCH
    }

    @Test(expected = IllegalArgumentException.class)
    public void testParseInvalidVersion_NonNumeric() {
        Version.parse("a.b.c");
    }

    // ========== Version.of() 测试 ==========

    @Test
    public void testOf() {
        Version v = Version.of(1, 2, 3);
        assertEquals(1, v.getMajor());
        assertEquals(2, v.getMinor());
        assertEquals(3, v.getPatch());
        assertEquals("1.2.3", v.toString());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testOfNegativeMajor() {
        Version.of(-1, 0, 0);
    }

    // ========== Version.compareTo() 测试 ==========

    @Test
    public void testCompareTo_Major() {
        Version v1 = Version.parse("2.0.0");
        Version v2 = Version.parse("1.9.9");
        assertTrue(v1.compareTo(v2) > 0);
        assertTrue(v2.compareTo(v1) < 0);
    }

    @Test
    public void testCompareTo_Minor() {
        Version v1 = Version.parse("1.2.0");
        Version v2 = Version.parse("1.1.9");
        assertTrue(v1.compareTo(v2) > 0);
        assertTrue(v2.compareTo(v1) < 0);
    }

    @Test
    public void testCompareTo_Patch() {
        Version v1 = Version.parse("1.2.1");
        Version v2 = Version.parse("1.2.0");
        assertTrue(v1.compareTo(v2) > 0);
        assertTrue(v2.compareTo(v1) < 0);
    }

    @Test
    public void testCompareTo_Equal() {
        Version v1 = Version.parse("1.2.0");
        Version v2 = Version.parse("1.2.0");
        assertEquals(0, v1.compareTo(v2));
    }

    @Test
    public void testCompareTo_StableVsPreRelease() {
        Version stable = Version.parse("1.0.0");
        Version pre = Version.parse("1.0.0-alpha");
        assertTrue(stable.compareTo(pre) > 0);
        assertTrue(pre.compareTo(stable) < 0);
    }

    @Test
    public void testCompareTo_PreRelease() {
        Version v1 = Version.parse("1.0.0-alpha.1");
        Version v2 = Version.parse("1.0.0-alpha");
        assertTrue(v1.compareTo(v2) > 0);
    }

    @Test
    public void testCompareTo_PreReleaseNumericVsAlpha() {
        Version v1 = Version.parse("1.0.0-alpha");
        Version v2 = Version.parse("1.0.0-1");
        assertTrue(v1.compareTo(v2) > 0);  // alpha > 1 (数字 < 字母)
    }

    // ========== Version.isCompatibleWith() 测试 ==========

    @Test
    public void testIsCompatibleWith_SameMajor() {
        Version v1 = Version.parse("1.2.0");
        Version v2 = Version.parse("1.9.9");
        assertTrue(v1.isCompatibleWith(v2));
    }

    @Test
    public void testIsCompatibleWith_DifferentMajor() {
        Version v1 = Version.parse("1.2.0");
        Version v2 = Version.parse("2.0.0");
        assertFalse(v1.isCompatibleWith(v2));
    }

    // ========== Version.isInRange() 测试 ==========

    @Test
    public void testIsInRange_InRange() {
        Version v = Version.parse("1.5.0");
        Version min = Version.parse("1.0.0");
        Version max = Version.parse("2.0.0");
        assertTrue(v.isInRange(min, max));
    }

    @Test
    public void testIsOutOfRange_BelowMin() {
        Version v = Version.parse("0.9.0");
        Version min = Version.parse("1.0.0");
        Version max = Version.parse("2.0.0");
        assertFalse(v.isInRange(min, max));
    }

    @Test
    public void testIsOutOfRange_AboveMax() {
        Version v = Version.parse("2.0.0");
        Version min = Version.parse("1.0.0");
        Version max = Version.parse("2.0.0");
        assertFalse(v.isInRange(min, max));  // max是开区间
    }

    // ========== Version.equals() 测试 ==========

    @Test
    public void testEquals_SameVersion() {
        Version v1 = Version.parse("1.2.0");
        Version v2 = Version.parse("1.2.0");
        assertEquals(v1, v2);
        assertEquals(v1.hashCode(), v2.hashCode());
    }

    @Test
    public void testEquals_DifferentVersion() {
        Version v1 = Version.parse("1.2.0");
        Version v2 = Version.parse("1.2.1");
        assertNotEquals(v1, v2);
    }

    @Test
    public void testEquals_BuildMetadataIgnored() {
        // 构建元数据不影响相等性
        Version v1 = Version.parse("1.2.0+build1");
        Version v2 = Version.parse("1.2.0+build2");
        assertEquals(v1, v2);
    }

    // ========== VersionRange.parse() 测试 ==========

    @Test
    public void testVersionRange_ExactVersion() {
        VersionRange range = VersionRange.parse("1.2.0");
        assertTrue(range.satisfies(Version.parse("1.2.0")));
        assertFalse(range.satisfies(Version.parse("1.2.1")));
        assertFalse(range.satisfies(Version.parse("1.1.9")));
    }

    @Test
    public void testVersionRange_CaretRange() {
        VersionRange range = VersionRange.parse("^1.2.0");
        // ^1.2.0 表示 >=1.2.0,<2.0.0
        assertTrue(range.satisfies(Version.parse("1.2.0")));
        assertTrue(range.satisfies(Version.parse("1.9.9")));
        assertFalse(range.satisfies(Version.parse("2.0.0")));
        assertFalse(range.satisfies(Version.parse("0.9.9")));
    }

    @Test
    public void testVersionRange_TildeRange() {
        VersionRange range = VersionRange.parse("~1.2.0");
        // ~1.2.0 表示 >=1.2.0,<1.3.0
        assertTrue(range.satisfies(Version.parse("1.2.0")));
        assertTrue(range.satisfies(Version.parse("1.2.9")));
        assertFalse(range.satisfies(Version.parse("1.3.0")));
        assertFalse(range.satisfies(Version.parse("2.0.0")));
    }

    @Test
    public void testVersionRange_TildeRangeMajorOnly() {
        VersionRange range = VersionRange.parse("~1");
        // ~1 表示 >=1.0.0,<2.0.0
        assertTrue(range.satisfies(Version.parse("1.0.0")));
        assertTrue(range.satisfies(Version.parse("1.9.9")));
        assertFalse(range.satisfies(Version.parse("2.0.0")));
    }

    @Test
    public void testVersionRange_GreaterThanOrEqual() {
        VersionRange range = VersionRange.parse(">=1.2.0");
        assertTrue(range.satisfies(Version.parse("1.2.0")));
        assertTrue(range.satisfies(Version.parse("2.0.0")));
        assertFalse(range.satisfies(Version.parse("1.1.9")));
    }

    @Test
    public void testVersionRange_LessThan() {
        VersionRange range = VersionRange.parse("<2.0.0");
        assertTrue(range.satisfies(Version.parse("1.9.9")));
        assertFalse(range.satisfies(Version.parse("2.0.0")));
    }

    @Test
    public void testVersionRange_RangeConstraint() {
        VersionRange range = VersionRange.parse(">=1.0.0,<2.0.0");
        assertTrue(range.satisfies(Version.parse("1.0.0")));
        assertTrue(range.satisfies(Version.parse("1.5.0")));
        assertFalse(range.satisfies(Version.parse("2.0.0")));
        assertFalse(range.satisfies(Version.parse("0.9.9")));
    }

    // ========== VersionRange.getMaxSatisfiedVersion() 测试 ==========

    @Test
    public void testGetMaxSatisfiedVersion() {
        VersionRange range = VersionRange.parse("^1.2.0");
        List<Version> versions = new ArrayList<>();
        versions.add(Version.parse("1.2.0"));
        versions.add(Version.parse("1.5.0"));
        versions.add(Version.parse("1.9.0"));
        versions.add(Version.parse("2.0.0"));  // 不在范围内

        Version max = range.getMaxSatisfiedVersion(versions);
        assertEquals(Version.parse("1.9.0"), max);
    }

    @Test
    public void testGetMaxSatisfiedVersion_NoMatch() {
        VersionRange range = VersionRange.parse("^2.0.0");
        List<Version> versions = new ArrayList<>();
        versions.add(Version.parse("1.0.0"));
        versions.add(Version.parse("1.5.0"));

        Version max = range.getMaxSatisfiedVersion(versions);
        assertNull(max);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testVersionRange_NullInput() {
        VersionRange.parse(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testVersionRange_EmptyInput() {
        VersionRange.parse("");
    }
}
