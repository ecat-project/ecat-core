package com.ecat.core.Utils;

/**
 * JAR 扫描异常 - 当扫描 JAR 中的集成入口类失败时抛出
 *
 * <p>此异常在以下情况抛出：</p>
 * <ul>
 *   <li>JAR 中未找到继承自 IntegrationBase 的入口类</li>
 *   <li>JAR 中找到多个继承自 IntegrationBase 的类</li>
 *   <li>扫描 JAR 时发生 I/O 错误</li>
 * </ul>
 *
 * @author coffee
 * @version 1.0.0
 */
public class JarScanException extends Exception {

    /**
     * 扫描失败类型
     */
    public enum ScanFailureType {
        NO_ENTRY_CLASS("未找到继承自 IntegrationBase 的入口类"),
        MULTIPLE_ENTRY_CLASSES("找到多个继承自 IntegrationBase 的类"),
        SCAN_ERROR("扫描 JAR 时发生错误");

        private final String description;

        ScanFailureType(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    private final ScanFailureType failureType;
    private final String jarFilePath;

    /**
     * 构造函数
     *
     * @param jarFilePath JAR 文件路径
     * @param failureType 失败类型
     * @param message 错误消息
     */
    public JarScanException(String jarFilePath, ScanFailureType failureType, String message) {
        super(message);
        this.jarFilePath = jarFilePath;
        this.failureType = failureType;
    }

    /**
     * 构造函数（带原因）
     *
     * @param jarFilePath JAR 文件路径
     * @param failureType 失败类型
     * @param message 错误消息
     * @param cause 原因
     */
    public JarScanException(String jarFilePath, ScanFailureType failureType, String message, Throwable cause) {
        super(message, cause);
        this.jarFilePath = jarFilePath;
        this.failureType = failureType;
    }

    /**
     * 获取失败类型
     */
    public ScanFailureType getFailureType() {
        return failureType;
    }

    /**
     * 获取 JAR 文件路径
     */
    public String getJarFilePath() {
        return jarFilePath;
    }
}
