package com.ecat.core.Utils;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.math.RoundingMode;

public class NumberFormatter {

    /**
     * 安全修约数值到指定小数位数（支持补零）
     * @param value 待修约的数值（支持 Integer、Double 等 Number 子类）
     * @param displayPrecision 目标小数位数（需 ≥0）
     * @return 修约后的字符串（如 0 → "0.00"，12.345 → "12.35"）
     * @throws IllegalArgumentException 若 displayPrecision <0
     */
    public static String formatValue(Number value, int displayPrecision) {
        // 参数校验：小数位数不能为负数
        if (displayPrecision < 0) {
            throw new IllegalArgumentException("小数位数不能为负数: " + displayPrecision);
        }

        // 构建小数部分的模式（例如 displayPrecision=2 → "00"）
        String decimalPart = buildRepeatedString('0', displayPrecision);
        // 完整格式模式（若小数位数为0，则模式为"0"，否则为"0.00..."）
        String pattern = (displayPrecision == 0) ? "0" : "0." + decimalPart;

        // 初始化 DecimalFormat 并设置舍入规则
        NumberFormat df = new DecimalFormat(pattern);
        df.setRoundingMode(RoundingMode.HALF_EVEN);

        return df.format(value);
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
