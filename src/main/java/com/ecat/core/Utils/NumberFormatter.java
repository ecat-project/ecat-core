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

package com.ecat.core.Utils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;

/**
 * Class for number value process
 * 
 * @author coffee
 */
public class NumberFormatter {

    /**
     * 安全修约数值到指定小数位数（支持补零，使用银行家算法）
     * @param value 待修约的数值（支持 Integer、Double 等 Number 子类）
     * @param displayPrecision 目标小数位数（需 ≥0）
     * @return 修约后的字符串（如 0 → "0.00"，2.345 → "2.34"）
     * @throws IllegalArgumentException 若 displayPrecision <0
     * @see RoundingMode#HALF_EVEN 银行家算法：舍弃位=5时，前位偶数则舍，奇数则入
     */
    public static String formatValue(Number value, int displayPrecision) {
        // 参数校验：小数位数不能为负数
        if (displayPrecision < 0) {
            throw new IllegalArgumentException("小数位数不能为负数: " + displayPrecision);
        }

        // 使用 BigDecimal 确保精度，避免 double 舍入误差
        BigDecimal bd = new BigDecimal(value.toString());
        // 使用银行家算法（HALF_EVEN）进行舍入
        bd = bd.setScale(displayPrecision, RoundingMode.HALF_EVEN);

        // 构建小数部分的模式（例如 displayPrecision=2 → "00"）
        String decimalPart = buildRepeatedString('0', displayPrecision);
        // 完整格式模式（若小数位数为0，则模式为"0"，否则为"0.00..."）
        String pattern = (displayPrecision == 0) ? "0" : "0." + decimalPart;

        // 初始化 DecimalFormat 并设置舍入规则
        NumberFormat df = new DecimalFormat(pattern);
        df.setRoundingMode(RoundingMode.HALF_EVEN);

        // 传入 BigDecimal 确保精确的银行家算法
        return df.format(bd);
    }

    /**
     * 安全修约数值到指定小数位数，返回 double 类型（银行家算法）
     * @param value 待修约的数值（支持 Integer、Double 等 Number 子类）
     * @param displayPrecision 目标小数位数（需 ≥0）
     * @return 修约后的 double 值（如 1.2345 → 1.234，1.2335 → 1.234，银行家算法）
     * @throws IllegalArgumentException 若 displayPrecision <0
     * @see RoundingMode#HALF_EVEN 银行家算法：舍弃位=5时，前位偶数则舍，奇数则入
     */
    public static double roundToDouble(Number value, int displayPrecision) {
        if (displayPrecision < 0) {
            throw new IllegalArgumentException("小数位数不能为负数: " + displayPrecision);
        }

        // 使用 BigDecimal 确保精度，避免 double 舍入误差
        BigDecimal bd = new BigDecimal(value.toString());
        // 使用银行家算法（HALF_EVEN）进行舍入
        bd = bd.setScale(displayPrecision, RoundingMode.HALF_EVEN);
        return bd.doubleValue();
    }

    /**
     * 生成重复指定次数的字符字符串（Java 8 兼容）
     * @param c 要重复的字符（如 '0'）
     * @param count 重复次数（需 ≥0）
     * @return 重复后的字符串（如 c='0', count=2 → "00"）
     */
    private static String buildRepeatedString(char c, int count) {
        if (count <= 0) {
            return "";
        }
        // 使用 StringBuilder 拼接字符（Java 8 无 String.repeat）
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append(c);
        }
        return sb.toString();
    }
}
