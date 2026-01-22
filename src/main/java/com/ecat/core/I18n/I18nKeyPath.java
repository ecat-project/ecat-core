package com.ecat.core.I18n;

import java.util.Objects;

/**
 * I18n 结构化地址类
 * 用于管理国际化资源的路径信息，支持路径前缀和最后段的分离
 */
public class I18nKeyPath {

    private final String pathPrefix;
    private final String lastSegment;
    private final String fullPath;

    /**
     * 构造函数
     * @param pathPrefix 路径前缀, 以点号结尾，如 "devices.qc_device."
     * @param lastSegment 最后段
     */
    public I18nKeyPath(String pathPrefix, String lastSegment) {
        if (pathPrefix == null) {
            throw new IllegalArgumentException("pathPrefix cannot be null");
        }
        if (lastSegment == null) {
            throw new IllegalArgumentException("lastSegment cannot be null");
        }

        this.pathPrefix = pathPrefix;
        this.lastSegment = lastSegment;
        this.fullPath = pathPrefix + lastSegment;
    }

    /**
     * 从完整路径创建 I18nKeyPath
     * @param fullPath 完整路径
     * @return I18nKeyPath 实例
     */
    public static I18nKeyPath fromFullPath(String fullPath) {
        if (fullPath == null || fullPath.trim().isEmpty()) {
            throw new IllegalArgumentException("fullPath cannot be null or empty");
        }

        int lastDotIndex = fullPath.lastIndexOf('.');
        if (lastDotIndex > 0) {
            String prefix = fullPath.substring(0, lastDotIndex + 1);
            String segment = fullPath.substring(lastDotIndex + 1);
            return new I18nKeyPath(prefix, segment);
        } else {
            return new I18nKeyPath("", fullPath);
        }
    }

    /**
     * 获取路径前缀
     * @return 路径前缀
     */
    public String getPathPrefix() {
        return pathPrefix;
    }

    /**
     * 获取最后段
     * @return 最后段
     */
    public String getLastSegment() {
        return lastSegment;
    }

    /**
     * 获取完整路径
     * @return 完整路径
     */
    public String getFullPath() {
        return fullPath;
    }

    /**
     * 获取 I18n 路径（等同于完整路径）
     * @return I18n 路径
     */
    public String getI18nPath() {
        return fullPath;
    }

    /**
     * 创建新的 I18nKeyPath，修改最后段
     * @param newLastSegment 新的最后段
     * @return 新的 I18nKeyPath， 如 "devices.qc_device" with "device_status" => "devices.device_status"
     */
    public I18nKeyPath withLastSegment(String newLastSegment) {
        return new I18nKeyPath(pathPrefix, newLastSegment);
    }

    /**
     * 创建新的 I18nKeyPath，添加最后段
     * @param newLastSegment 要添加的最后段, 
     * @return 新的 I18nKeyPath, 如 "devices.qc_device" + "device_status" => "devices.qc_device.device_status"
     */
    public I18nKeyPath addLastSegment(String newLastSegment) {
        return new I18nKeyPath(this.fullPath+".", newLastSegment);
    }

    /**
     * 创建新的 I18nKeyPath，修改路径前缀
     * @param newPathPrefix 新的路径前缀
     * @return 新的 I18nKeyPath
     */
    public I18nKeyPath withPathPrefix(String newPathPrefix) {
        return new I18nKeyPath(newPathPrefix, lastSegment);
    }

    /**
     * 创建新的 I18nKeyPath，为最后段添加后缀
     * @param suffix 要添加的后缀
     * @return 新的 I18nKeyPath
     * 
     * @example
     * <pre>
     * I18nKeyPath path = new I18nKeyPath("devices.qc_device.", "device_status");
     * assertEquals("devices.qc_device.device_status", path.getFullPath());
     * I18nKeyPath newPath = path.withSuffix("_active");
     * assertEquals("devices.qc_device.device_status_active", newPath.getFullPath());
     * </pre>
     */
    public I18nKeyPath withSuffix(String suffix) {
        return new I18nKeyPath(pathPrefix, lastSegment + suffix);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        I18nKeyPath that = (I18nKeyPath) o;
        return pathPrefix.equals(that.pathPrefix) && lastSegment.equals(that.lastSegment);
    }

    @Override
    public int hashCode() {
        return Objects.hash(pathPrefix, lastSegment);
    }

    @Override
    public String toString() {
        return "I18nKeyPath{" +
                "pathPrefix='" + pathPrefix + '\'' +
                ", lastSegment='" + lastSegment + '\'' +
                ", fullPath='" + fullPath + '\'' +
                '}';
    }
}
