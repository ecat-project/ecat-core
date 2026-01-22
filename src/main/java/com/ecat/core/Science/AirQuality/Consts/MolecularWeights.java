package com.ecat.core.Science.AirQuality.Consts;

/**
 * 空气质量相关分子质量常量类
 *
 * 包含常见空气污染物的分子质量，单位：g/mol
 * 所有数据基于权威科学数据库（NIST/CRC）
 *
 * @author coffee
 * @version 1.0.0
 */
public final class MolecularWeights {

    /**
     * 二氧化硫 (SO2) 分子质量
     * 标准值：64.066 g/mol
     * 化学式：SO2
     * 数据来源：NIST Chemistry WebBook
     */
    public static final double SO2 = 64.0;

    /**
     * 一氧化碳 (CO) 分子质量
     * 标准值：28.010 g/mol
     * 化学式：CO
     * 数据来源：NIST Chemistry WebBook
     */
    public static final double CO = 28.0;

    /**
     * 臭氧 (O3) 分子质量
     * 标准值：47.998 g/mol
     * 化学式：O3
     * 数据来源：NIST Chemistry WebBook
     */
    public static final double O3 = 48.0;

    /**
     * 一氧化氮 (NO) 分子质量
     * 标准值：30.006 g/mol
     * 化学式：NO
     * 数据来源：NIST Chemistry WebBook
     */
    public static final double NO = 30.0;

    /**
     * 二氧化氮 (NO2) 分子质量
     * 标准值：46.006 g/mol
     * 化学式：NO2
     * 数据来源：NIST Chemistry WebBook
     */
    public static final double NO2 = 46.0;

    // 私有构造函数，防止实例化
    private MolecularWeights() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
