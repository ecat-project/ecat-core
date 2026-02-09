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
 * 气态污染物分子量保留3位小数以提高转换精度
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
    public static final double SO2 = 64.066;

    /**
     * 一氧化碳 (CO) 分子质量
     * 标准值：28.010 g/mol
     * 化学式：CO
     * 数据来源：NIST Chemistry WebBook
     */
    public static final double CO = 28.010;

    /**
     * 臭氧 (O3) 分子质量
     * 标准值：47.998 g/mol
     * 化学式：O3
     * 数据来源：NIST Chemistry WebBook
     */
    public static final double O3 = 47.998;

    /**
     * 一氧化氮 (NO) 分子质量
     * 标准值：30.006 g/mol
     * 化学式：NO
     * 数据来源：NIST Chemistry WebBook
     */
    public static final double NO = 30.006;

    /**
     * 二氧化氮 (NO2) 分子质量
     * 标准值：46.006 g/mol
     * 化学式：NO2
     * 数据来源：NIST Chemistry WebBook
     */
    public static final double NO2 = 46.006;

    /**
     * 标准环境温度和压力下的摩尔体积 (SATP Molar Volume)
     * 标准条件：SATP (25°C, 1 atm)
     * 数值：24.465 L/mol
     * 数据来源：理想气体状态方程 PV = nRT
     * V = RT/P = (0.082057 × 298.15) / 1 = 24.465 L/mol
     *
     * SATP (Standard Ambient Temperature and Pressure): 25°C (298.15 K), 1 atm
     * 注意：不要与 STP (0°C, 1 atm) 下的 22.4 L/mol 混淆
     * 本项目采用 SATP 作为环境监测的标准条件
     *
     * 示例计算：1 ppb SO₂ = 2.6187 μg/m³
     * formula: 1 × 1000 × 64.066 / 24.465 = 2618.67 μg/m³ = 2.6187 mg/m³
     * 
     * https://starlighttools.org/science/moles-mass-volume-calculator
     */
    public static final double MOLAR_VOLUME_25C = 24.465;

    // 私有构造函数，防止实例化
    private MolecularWeights() {
        throw new UnsupportedOperationException("Utility class cannot be instantiated");
    }
}
