package com.ecat.core.I18n;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * 测试 I18nKeyPath 类的功能
 */
public class I18nKeyPathTest {

    @Test
    public void testConstructorWithPrefixAndSegment() {
        I18nKeyPath path = new I18nKeyPath("state.select_attr.", "options");
        assertEquals("state.select_attr.", path.getPathPrefix());
        assertEquals("options", path.getLastSegment());
        assertEquals("state.select_attr.options", path.getFullPath());
        assertEquals("state.select_attr.options", path.getI18nPath());
    }

    @Test
    public void testConstructorWithEmptyPrefix() {
        I18nKeyPath path = new I18nKeyPath("", "test");
        assertEquals("", path.getPathPrefix());
        assertEquals("test", path.getLastSegment());
        assertEquals("test", path.getFullPath());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithNullPrefix() {
        new I18nKeyPath(null, "test");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConstructorWithNullSegment() {
        new I18nKeyPath("prefix.", null);
    }

    @Test
    public void testFromFullPathWithDot() {
        I18nKeyPath path = I18nKeyPath.fromFullPath("state.select_attr.options");
        assertEquals("state.select_attr.", path.getPathPrefix());
        assertEquals("options", path.getLastSegment());
        assertEquals("state.select_attr.options", path.getFullPath());
    }

    @Test
    public void testFromFullPathWithoutDot() {
        I18nKeyPath path = I18nKeyPath.fromFullPath("test");
        assertEquals("", path.getPathPrefix());
        assertEquals("test", path.getLastSegment());
        assertEquals("test", path.getFullPath());
    }

    @Test
    public void testFromFullPathWithMultipleDots() {
        I18nKeyPath path = I18nKeyPath.fromFullPath("a.b.c.d");
        assertEquals("a.b.c.", path.getPathPrefix());
        assertEquals("d", path.getLastSegment());
        assertEquals("a.b.c.d", path.getFullPath());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFromFullPathWithNull() {
        I18nKeyPath.fromFullPath(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testFromFullPathWithEmptyString() {
        I18nKeyPath.fromFullPath("");
    }

    @Test
    public void testWithLastSegment() {
        I18nKeyPath original = new I18nKeyPath("state.select_attr.", "options");
        I18nKeyPath modified = original.withLastSegment("commands");

        assertEquals("state.select_attr.", original.getPathPrefix());
        assertEquals("options", original.getLastSegment());

        assertEquals("state.select_attr.", modified.getPathPrefix());
        assertEquals("commands", modified.getLastSegment());
        assertEquals("state.select_attr.commands", modified.getFullPath());
    }

    @Test
    public void testWithPathPrefix() {
        I18nKeyPath original = new I18nKeyPath("state.select_attr.", "options");
        I18nKeyPath modified = original.withPathPrefix("device.test_attr.");

        assertEquals("state.select_attr.", original.getPathPrefix());
        assertEquals("options", original.getLastSegment());

        assertEquals("device.test_attr.", modified.getPathPrefix());
        assertEquals("options", modified.getLastSegment());
        assertEquals("device.test_attr.options", modified.getFullPath());
    }

    @Test
    public void testEquals() {
        I18nKeyPath path1 = new I18nKeyPath("state.select_attr.", "options");
        I18nKeyPath path2 = new I18nKeyPath("state.select_attr.", "options");
        I18nKeyPath path3 = new I18nKeyPath("state.select_attr.", "commands");

        assertEquals(path1, path2);
        assertNotEquals(path1, path3);
        assertNotEquals(path2, path3);
    }

    @Test
    public void testHashCode() {
        I18nKeyPath path1 = new I18nKeyPath("state.select_attr.", "options");
        I18nKeyPath path2 = new I18nKeyPath("state.select_attr.", "options");

        assertEquals(path1.hashCode(), path2.hashCode());
    }

    @Test
    public void testToString() {
        I18nKeyPath path = new I18nKeyPath("state.select_attr.", "options");
        String toString = path.toString();

        assertTrue(toString.contains("pathPrefix='state.select_attr.'"));
        assertTrue(toString.contains("lastSegment='options'"));
        assertTrue(toString.contains("fullPath='state.select_attr.options'"));
    }

    @Test
    public void testImmutability() {
        I18nKeyPath original = new I18nKeyPath("state.select_attr.", "options");
        I18nKeyPath modified = original.withLastSegment("commands");

        // Original should remain unchanged
        assertEquals("state.select_attr.", original.getPathPrefix());
        assertEquals("options", original.getLastSegment());
        assertEquals("state.select_attr.options", original.getFullPath());
    }

    @Test
    public void testWithSuffix() {
        I18nKeyPath path = new I18nKeyPath("devices.qc_device.", "device_status");
        I18nKeyPath commandsPath = path.withSuffix("_commands");

        assertEquals("devices.qc_device.", commandsPath.getPathPrefix());
        assertEquals("device_status_commands", commandsPath.getLastSegment());
        assertEquals("devices.qc_device.device_status_commands", commandsPath.getFullPath());
    }

    @Test
    public void testWithSuffixWithOptions() {
        I18nKeyPath path = new I18nKeyPath("devices.qc_device.", "binary_alarm");
        I18nKeyPath optionsPath = path.withSuffix("_options");

        assertEquals("devices.qc_device.", optionsPath.getPathPrefix());
        assertEquals("binary_alarm_options", optionsPath.getLastSegment());
        assertEquals("devices.qc_device.binary_alarm_options", optionsPath.getFullPath());
    }

    @Test
    public void testWithSuffixImmutability() {
        I18nKeyPath original = new I18nKeyPath("devices.qc_device.", "device_status");
        I18nKeyPath modified = original.withSuffix("_commands");

        // Original should remain unchanged
        assertEquals("devices.qc_device.", original.getPathPrefix());
        assertEquals("device_status", original.getLastSegment());
        assertEquals("devices.qc_device.device_status", original.getFullPath());
    }

    @Test
    public void testEdgeCases() {
        // Test with dot at the end of segment
        I18nKeyPath path1 = new I18nKeyPath("prefix.", "segment.");
        assertEquals("prefix.segment.", path1.getFullPath());

        // Test with multiple consecutive dots - the last dot is the separator
        I18nKeyPath path2 = I18nKeyPath.fromFullPath("a..b");
        assertEquals("a..", path2.getPathPrefix());
        assertEquals("b", path2.getLastSegment());
    }
}