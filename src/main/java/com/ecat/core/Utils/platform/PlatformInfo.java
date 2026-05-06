/*
 * Copyright (c) 2026 ECAT Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ecat.core.Utils.platform;

/**
 * 统一平台检测服务。
 *
 * <p>为所有集成提供操作系统类型、CPU 架构、JavaCPP classifier 等信息。
 * 替代散乱的 {@code System.getProperty("os.name")} 内联代码。
 *
 * <p>Singleton 模式，JVM 启动时初始化一次，之后不可变。
 *
 * @author coffee
 */
public final class PlatformInfo {

    private static final PlatformInfo INSTANCE = new PlatformInfo();

    public enum OsType { LINUX, WINDOWS, MACOS, UNKNOWN }
    public enum ArchType { X86_64, ARM64, UNKNOWN }

    private final OsType os;
    private final ArchType arch;
    private final String osName;
    private final String osVersion;
    private final String archName;
    private final String javaVersion;

    private PlatformInfo() {
        osName = System.getProperty("os.name", "").toLowerCase();
        osVersion = System.getProperty("os.version", "");
        archName = System.getProperty("os.arch", "").toLowerCase();
        javaVersion = System.getProperty("java.version", "");

        if (osName.contains("linux")) {
            os = OsType.LINUX;
        } else if (osName.contains("win")) {
            os = OsType.WINDOWS;
        } else if (osName.contains("mac")) {
            os = OsType.MACOS;
        } else {
            os = OsType.UNKNOWN;
        }

        if (archName.equals("amd64") || archName.equals("x86_64")) {
            arch = ArchType.X86_64;
        } else if (archName.equals("aarch64") || archName.equals("arm64")) {
            arch = ArchType.ARM64;
        } else {
            arch = ArchType.UNKNOWN;
        }
    }

    public static PlatformInfo getInstance() {
        return INSTANCE;
    }

    public OsType getOs() { return os; }
    public ArchType getArch() { return arch; }
    public String getOsName() { return osName; }
    public String getOsVersion() { return osVersion; }
    public String getArchName() { return archName; }
    public String getJavaVersion() { return javaVersion; }

    public boolean isWindows() { return os == OsType.WINDOWS; }
    public boolean isLinux() { return os == OsType.LINUX; }
    public boolean isMacos() { return os == OsType.MACOS; }

    /**
     * 获取 JavaCPP classifier 后缀。
     *
     * <p>返回值示例：{@code "linux-x86_64"}, {@code "linux-arm64"},
     * {@code "windows-x86_64"}, {@code "macosx-arm64"}。
     *
     * @return classifier 字符串，如果平台无法识别则抛出异常
     */
    public String getJavacppClassifier() {
        String osPart;
        switch (os) {
            case LINUX:   osPart = "linux"; break;
            case WINDOWS: osPart = "windows"; break;
            case MACOS:   osPart = "macosx"; break;
            default:
                throw new UnsupportedOperationException(
                    "Unsupported OS for native library loading: " + osName);
        }

        String archPart;
        switch (arch) {
            case X86_64: archPart = "x86_64"; break;
            case ARM64:  archPart = "arm64"; break;
            default:
                throw new UnsupportedOperationException(
                    "Unsupported architecture for native library loading: " + archName);
        }

        return osPart + "-" + archPart;
    }

    /**
     * 获取当前平台原生库文件扩展名。
     *
     * @return ".so" (Linux), ".dll" (Windows), ".dylib" (macOS)
     */
    public String getNativeLibExtension() {
        switch (os) {
            case LINUX:   return ".so";
            case WINDOWS: return ".dll";
            case MACOS:   return ".dylib";
            default:
                throw new UnsupportedOperationException(
                    "Unsupported OS for native library: " + osName);
        }
    }

    /** 便捷方法：检查平台是否支持原生库加载（非 UNKNOWN） */
    public boolean isPlatformSupported() {
        return os != OsType.UNKNOWN && arch != ArchType.UNKNOWN;
    }

    @Override
    public String toString() {
        return "PlatformInfo{" + os + "-" + arch +
               ", osVersion=" + osVersion +
               ", java=" + javaVersion + "}";
    }
}
