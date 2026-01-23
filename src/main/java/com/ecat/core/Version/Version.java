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

package com.ecat.core.Version;

import java.util.Objects;

/**
 * 语义化版本实现（Semantic Versioning 2.0.0）
 *
 * 格式：MAJOR.MINOR.PATCH[-PRERELEASE][+BUILD]
 * 示例：
 * - 1.2.0
 * - 2.0.0-beta.1
 * - 1.2.0+20240101
 * - 1.0.0-alpha.1+001
 *
 * 兼容性规则：
 * - 相同主版本：向后兼容
 * - 主版本升级：不兼容变更
 *
 * @see <a href="https://semver.org/spec/v2.0.0.html">Semantic Versioning 2.0.0</a>
 * 
 * @author coffee
 */
public class Version implements Comparable<Version> {

    private final int major;
    private final int minor;
    private final int patch;
    private final String preRelease;
    private final String buildMetadata;

    // 缓存toString结果
    private transient String toStringCache;

    /**
     * 私有构造函数，使用parse()或of()创建实例
     */
    private Version(int major, int minor, int patch, String preRelease, String buildMetadata) {
        if (major < 0 || minor < 0 || patch < 0) {
            throw new IllegalArgumentException("版本号不能为负数");
        }
        this.major = major;
        this.minor = minor;
        this.patch = patch;
        this.preRelease = preRelease;
        this.buildMetadata = buildMetadata;
    }

    /**
     * 创建版本实例（不含预发布版本和构建元数据）
     */
    public static Version of(int major, int minor, int patch) {
        return new Version(major, minor, patch, null, null);
    }

    /**
     * 解析版本字符串
     *
     * @param versionString 版本字符串，如 "1.2.0"、"2.0.0-beta.1"
     * @return Version实例
     * @throws IllegalArgumentException 如果版本字符串格式无效
     */
    public static Version parse(String versionString) {
        if (versionString == null || versionString.isEmpty()) {
            throw new IllegalArgumentException("版本字符串不能为空");
        }

        String preRelease = null;
        String buildMetadata = null;

        // 解析构建元数据 (+build)
        int buildIndex = versionString.indexOf('+');
        if (buildIndex > 0) {
            buildMetadata = versionString.substring(buildIndex + 1);
            versionString = versionString.substring(0, buildIndex);
        }

        // 解析预发布版本 (-pre)
        int preIndex = versionString.indexOf('-');
        if (preIndex > 0) {
            preRelease = versionString.substring(preIndex + 1);
            versionString = versionString.substring(0, preIndex);
        }

        // 解析主版本号
        String[] parts = versionString.split("\\.");
        if (parts.length < 3) {
            throw new IllegalArgumentException("无效的版本格式: " + versionString +
                ", 必须包含MAJOR.MINOR.PATCH三部分");
        }

        try {
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            int patch = Integer.parseInt(parts[2]);

            return new Version(major, minor, patch, preRelease, buildMetadata);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("版本号必须为数字: " + versionString, e);
        }
    }

    // ========== Getters ==========

    public int getMajor() {
        return major;
    }

    public int getMinor() {
        return minor;
    }

    public int getPatch() {
        return patch;
    }

    public String getPreRelease() {
        return preRelease;
    }

    public String getBuildMetadata() {
        return buildMetadata;
    }

    /**
     * 判断是否为预发布版本
     */
    public boolean isPreRelease() {
        return preRelease != null && !preRelease.isEmpty();
    }

    /**
     * 判断是否为稳定版本（非预发布）
     */
    public boolean isStable() {
        return !isPreRelease();
    }

    // ========== 版本比较 ==========

    @Override
    public int compareTo(Version other) {
        // 1. 比较主版本号
        if (this.major != other.major) {
            return Integer.compare(this.major, other.major);
        }

        // 2. 比较次版本号
        if (this.minor != other.minor) {
            return Integer.compare(this.minor, other.minor);
        }

        // 3. 比较补丁版本号
        if (this.patch != other.patch) {
            return Integer.compare(this.patch, other.patch);
        }

        // 4. 比较预发布版本
        // 稳定版本 > 预发布版本
        if (this.isStable() && other.isPreRelease()) {
            return 1;
        }
        if (this.isPreRelease() && other.isStable()) {
            return -1;
        }

        // 都是预发布版本，比较预发布标识符
        if (this.isPreRelease() && other.isPreRelease()) {
            return comparePreRelease(this.preRelease, other.preRelease);
        }

        // 完全相同
        return 0;
    }

    /**
     * 比较预发布版本标识符
     *
     * 规则：
     * - 数字标识符 < 非数字标识符
     * - 数字标识符按数值比较
     * - 非数字标识符按字典序比较
     * - 标识符多的版本大（如 1.0.0-alpha.1 > 1.0.0-alpha）
     */
    private int comparePreRelease(String pre1, String pre2) {
        String[] parts1 = pre1.split("\\.");
        String[] parts2 = pre2.split("\\.");

        int maxLength = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < maxLength; i++) {
            // 如果一个版本没有更多标识符，它更小
            if (i >= parts1.length) {
                return -1;
            }
            if (i >= parts2.length) {
                return 1;
            }

            String p1 = parts1[i];
            String p2 = parts2[i];

            // 判断是否为数字
            boolean p1Numeric = p1.matches("\\d+");
            boolean p2Numeric = p2.matches("\\d+");

            if (p1Numeric && p2Numeric) {
                // 都是数字，按数值比较
                int n1 = Integer.parseInt(p1);
                int n2 = Integer.parseInt(p2);
                if (n1 != n2) {
                    return Integer.compare(n1, n2);
                }
            } else if (p1Numeric) {
                // 数字 < 非数字
                return -1;
            } else if (p2Numeric) {
                // 非数字 > 数字
                return 1;
            } else {
                // 都是非数字，按字典序比较
                int cmp = p1.compareTo(p2);
                if (cmp != 0) {
                    return cmp;
                }
            }
        }

        return 0;
    }

    /**
     * 兼容性检查
     *
     * 规则：
     * - 相同主版本号：兼容
     * - 主版本号不同：不兼容
     *
     * @param other 要比较的版本
     * @return 如果兼容返回true
     */
    public boolean isCompatibleWith(Version other) {
        return this.major == other.major;
    }

    /**
     * 检查是否在指定版本范围内
     *
     * @param minVersion 最小版本（包含）
     * @param maxVersion 最大版本（不包含）
     * @return 如果在范围内返回true
     */
    public boolean isInRange(Version minVersion, Version maxVersion) {
        return this.compareTo(minVersion) >= 0 && this.compareTo(maxVersion) < 0;
    }

    // ========== Object方法 ==========

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Version version = (Version) o;
        return major == version.major &&
               minor == version.minor &&
               patch == version.patch &&
               Objects.equals(preRelease, version.preRelease);
    }

    @Override
    public int hashCode() {
        return Objects.hash(major, minor, patch, preRelease);
    }

    @Override
    public String toString() {
        if (toStringCache == null) {
            StringBuilder sb = new StringBuilder();
            sb.append(major).append('.')
              .append(minor).append('.')
              .append(patch);

            if (preRelease != null) {
                sb.append('-').append(preRelease);
            }

            if (buildMetadata != null) {
                sb.append('+').append(buildMetadata);
            }

            toStringCache = sb.toString();
        }
        return toStringCache;
    }
}
