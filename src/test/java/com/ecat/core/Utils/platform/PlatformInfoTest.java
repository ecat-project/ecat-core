package com.ecat.core.Utils.platform;

import org.junit.Test;
import static org.junit.Assert.*;

public class PlatformInfoTest {

    @Test
    public void testSingletonInstance() {
        PlatformInfo info1 = PlatformInfo.getInstance();
        PlatformInfo info2 = PlatformInfo.getInstance();
        assertSame(info1, info2);
    }

    @Test
    public void testCurrentPlatformNotNull() {
        PlatformInfo info = PlatformInfo.getInstance();
        assertNotNull(info.getOs());
        assertNotNull(info.getArch());
        assertNotNull(info.getOsName());
        assertNotNull(info.getOsVersion());
        assertNotNull(info.getJavaVersion());
    }

    @Test
    public void testIsWindowsIsConsistent() {
        PlatformInfo info = PlatformInfo.getInstance();
        boolean osNameHasWin = info.getOsName().contains("win");
        assertEquals(osNameHasWin, info.isWindows());
        assertEquals(osNameHasWin, info.getOs() == PlatformInfo.OsType.WINDOWS);
    }

    @Test
    public void testIsLinuxIsConsistent() {
        PlatformInfo info = PlatformInfo.getInstance();
        boolean osNameHasLinux = info.getOsName().contains("linux");
        assertEquals(osNameHasLinux, info.isLinux());
        assertEquals(osNameHasLinux, info.getOs() == PlatformInfo.OsType.LINUX);
    }

    @Test
    public void testIsMacosIsConsistent() {
        PlatformInfo info = PlatformInfo.getInstance();
        boolean osNameHasMac = info.getOsName().contains("mac");
        assertEquals(osNameHasMac, info.isMacos());
        assertEquals(osNameHasMac, info.getOs() == PlatformInfo.OsType.MACOS);
    }

    @Test
    public void testGetJavacppClassifierFormat() {
        PlatformInfo info = PlatformInfo.getInstance();
        if (info.isPlatformSupported()) {
            String classifier = info.getJavacppClassifier();
            // classifier 格式应为 "平台-架构"，如 "linux-x86_64"
            assertTrue("Classifier should contain '-': " + classifier,
                       classifier.contains("-"));
            // 应该是已知的 classifier 之一
            assertTrue("Unknown classifier: " + classifier,
                       classifier.equals("linux-x86_64")
                    || classifier.equals("linux-arm64")
                    || classifier.equals("windows-x86_64")
                    || classifier.equals("windows-arm64")
                    || classifier.equals("macosx-arm64")
                    || classifier.equals("macosx-x86_64"));
        }
    }

    @Test
    public void testGetNativeLibExtension() {
        PlatformInfo info = PlatformInfo.getInstance();
        if (info.isPlatformSupported()) {
            String ext = info.getNativeLibExtension();
            assertTrue("Should be a known extension: " + ext,
                       ext.equals(".so") || ext.equals(".dll") || ext.equals(".dylib"));
        }
    }

    @Test
    public void testToStringContainsUsefulInfo() {
        PlatformInfo info = PlatformInfo.getInstance();
        String str = info.toString();
        assertFalse(str.isEmpty());
        assertTrue(str.contains("PlatformInfo"));
    }
}
