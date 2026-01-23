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

package com.ecat.core.Science.Weather;

/**
 * 露点温度计算器
 *
 * 提供基于温度和相对湿度计算露点温度的功能
 * 使用 Magnus-Tetens 公式进行计算
 *
 * @author coffee
 * @version 1.0.0
 */
public class DewPointCalculator {

    /**
     * Magnus 公式常数 A (适用于温度范围 0°C 到 50°C)
     */
    private static final double A = 17.27;

    /**
     * Magnus 公式常数 B (适用于温度范围 0°C 到 50°C)
     */
    private static final double B = 237.7;

    /**
     * 计算露点温度
     *
     * 使用 Magnus-Tetens 公式计算露点温度：
     * γ = (A × T) / (B + T) + ln(RH/100)
     * Td = (B × γ) / (A - γ)
     *
     * 其中：
     * T = 温度 (°C)
     * RH = 相对湿度 (%)
     * Td = 露点温度 (°C)
     *
     * @param temperature 温度，单位：摄氏度 (°C)
     * @param relativeHumidity 相对湿度，单位：百分比 (%)
     * @return 露点温度，单位：摄氏度 (°C)
     * @throws IllegalArgumentException 当输入参数超出有效范围时
     */
    public static double calculateDewPoint(double temperature, double relativeHumidity) {
        // 输入参数验证
        if (relativeHumidity <= 0 || relativeHumidity > 100) {
            throw new IllegalArgumentException(
                "相对湿度必须在 (0, 100%] 范围内，当前值: " + relativeHumidity);
        }

        // 对于极低温度（< -50°C）或极高温度（> 50°C），发出警告但仍计算
        if (temperature < -50 || temperature > 50) {
            System.out.println("警告: 温度 " + temperature + "°C 超出推荐范围 [-50°C, 50°C]，" +
                "计算结果可能不够准确");
        }

        // Magnus-Tetens 公式
        double gamma = (A * temperature) / (B + temperature) + Math.log(relativeHumidity / 100.0);
        double dewPoint = (B * gamma) / (A - gamma);

        return dewPoint;
    }

    /**
     * 计算露点温度（带安全检查）
     *
     * 与 {@link #calculateDewPoint(double, double)} 相比，此方法在输入参数无效时
     * 返回 {@link Double#NaN} 而不是抛出异常，适用于某些不希望中断计算的场景
     *
     * @param temperature 温度，单位：摄氏度 (°C)
     * @param relativeHumidity 相对湿度，单位：百分比 (%)
     * @return 露点温度，单位：摄氏度 (°C)；如果输入无效则返回 {@link Double#NaN}
     */
    public static double calculateDewPointSafe(double temperature, double relativeHumidity) {
        try {
            return calculateDewPoint(temperature, relativeHumidity);
        } catch (IllegalArgumentException e) {
            System.out.println("露点温度计算失败: " + e.getMessage());
            return Double.NaN;
        }
    }

    /**
     * 检查给定温湿度条件下是否可能出现结露
     *
     * 当当前温度接近或低于露点温度时，可能发生结露现象
     *
     * @param currentTemperature 当前温度，单位：摄氏度 (°C)
     * @param relativeHumidity 相对湿度，单位：百分比 (%)
     * @return 如果可能结露返回 true，否则返回 false
     */
    public static boolean isCondensationPossible(double currentTemperature, double relativeHumidity) {
        double dewPoint = calculateDewPointSafe(currentTemperature, relativeHumidity);
        return !Double.isNaN(dewPoint) && currentTemperature <= dewPoint + 1.0; // 1°C 容差
    }
}
