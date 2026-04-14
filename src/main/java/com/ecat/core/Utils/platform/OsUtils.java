package com.ecat.core.Utils.platform;

/**
 * 跨平台操作系统工具类
 */
public final class OsUtils {

    private static final String OS_NAME = System.getProperty("os.name").toLowerCase();

    private OsUtils() {}

    /**
     * 当前系统是否为 Windows
     */
    public static boolean isWindows() {
        return OS_NAME.contains("win");
    }
}
