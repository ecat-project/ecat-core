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
