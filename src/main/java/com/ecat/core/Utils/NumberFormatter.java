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

/**
 * Class for number value process
 * 
 * @author coffee
 */
public class NumberFormatter {

    /**
     * 每线程按精度缓存的 DecimalFormat 上界（含）。displayPrecision 是属性级配置值，实际使用
     * 0~6；上界防病态超大精度把 per-thread 缓存数组撑爆。超出上界的精度走按次构造（行为与
     * 历史逐位一致，仅不缓存）——displayPrecision 合法域是任意 ≥0 整数，超大值是合法边界
     * 而非异常，不抛错。
     */
    private static final int MAX_CACHED_PRECISION = 16;

    /**
     * DecimalFormat 按精度做 ThreadLocal 缓存：DecimalFormat 非线程安全（这正是历史实现每次
     * new 的原因），但本系统无 Locale 运行时切换（全仓无 Locale.setDefault），同一线程内
     * pattern/舍入模式构造后不再变，逐次复用安全。
     * <p>热路径背景：formatValue 在每次 attr 状态提交（=每次 device.data.update 事件）至少调用
     * 1 次，历史每次 new DecimalFormat 实测 618ns/次、≈1.3KB/次分配；ThreadLocal 复用 59ns/次、
     * 零稳态分配。
     */
    private static final ThreadLocal<DecimalFormat[]> FORMATTER_CACHE = ThreadLocal.withInitial(
            () -> new DecimalFormat[MAX_CACHED_PRECISION + 1]);

    /**
     * 取指定精度的格式化器：缓存索引内复用（首用构造并写缓存），上界外按次构造。
     * pattern 与历史实现逐位一致（precision=0 → "0"，否则 "0." + precision 个 '0'）。
     */
    private static DecimalFormat formatterFor(int displayPrecision) {
        if (displayPrecision <= MAX_CACHED_PRECISION) {
            DecimalFormat[] cache = FORMATTER_CACHE.get();
            DecimalFormat df = cache[displayPrecision];
            if (df == null) {
                df = new DecimalFormat(patternFor(displayPrecision));
                df.setRoundingMode(RoundingMode.HALF_EVEN);
                cache[displayPrecision] = df;
            }
            return df;
        }
        DecimalFormat df = new DecimalFormat(patternFor(displayPrecision));
        df.setRoundingMode(RoundingMode.HALF_EVEN);
        return df;
    }

    /** 精度 → DecimalFormat 模式（如 0 → "0"，2 → "0.00"）。 */
    private static String patternFor(int displayPrecision) {
        if (displayPrecision == 0) {
            return "0";
        }
        return "0." + buildRepeatedString('0', displayPrecision);
    }

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
        if (!isFiniteNumber(value)) {
            return "";
        }

        // 使用 BigDecimal 确保精度，避免 double 舍入误差
        BigDecimal bd = new BigDecimal(value.toString());
        // 使用银行家算法（HALF_EVEN）进行舍入
        bd = bd.setScale(displayPrecision, RoundingMode.HALF_EVEN);

        // 复用按精度缓存的 DecimalFormat（HALF_EVEN 已在构造期设好），传入 BigDecimal 确保精确的银行家算法
        return formatterFor(displayPrecision).format(bd);
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
        if (!isFiniteNumber(value)) {
            return Double.NaN;
        }

        // 使用 BigDecimal 确保精度，避免 double 舍入误差
        BigDecimal bd = new BigDecimal(value.toString());
        // 使用银行家算法（HALF_EVEN）进行舍入
        bd = bd.setScale(displayPrecision, RoundingMode.HALF_EVEN);
        return bd.doubleValue();
    }

    /**
     * BigDecimal(String) 无法解析 NaN / Infinity / null，会抛出 message 为 null 的 NumberFormatException。
     */
    private static boolean isFiniteNumber(Number value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Double || value instanceof Float) {
            double d = value.doubleValue();
            return !Double.isNaN(d) && !Double.isInfinite(d);
        }
        return true;
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
