package com.ecat.core.State;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * AttributeStatus.getPriority() 测试——多异常状态并存时的聚合优先级（人干预优先方向）。
 *
 * <p>ecat-core 用 JUnit 4（pom 已声明 junit:junit:4.13.2），走 {@code org.junit.Test} /
 * {@code org.junit.Assert.*}。assertEquals 的失败消息在 JUnit 4 是首参（与 Jupiter 相反）。
 */
public class AttributeStatusPriorityTest {

    /** P1 人/仪器判定需处理 > P2 受控测试态 > P3 仪器报警 > P4 超限 > P5 恒值/突变 > P6 数据态不佳 > P7 正常。 */
    @Test
    public void priority_descendingBySeverityTier_humanInterventionFirst() {
        assertTrue(AttributeStatus.MAINTENANCE.getPriority()    > AttributeStatus.CALIBRATION.getPriority());   // P1 > P2
        assertTrue(AttributeStatus.CALIBRATION.getPriority()    > AttributeStatus.ALARM.getPriority());          // P2 > P3
        assertTrue(AttributeStatus.ALARM.getPriority()          > AttributeStatus.OVER_UPPER_LIMIT.getPriority()); // P3 > P4
        assertTrue(AttributeStatus.OVER_UPPER_LIMIT.getPriority()> AttributeStatus.NO_CHANGE.getPriority());     // P4 > P5
        assertTrue(AttributeStatus.NO_CHANGE.getPriority()      > AttributeStatus.INSUFFICIENT.getPriority());   // P5 > P6
        assertTrue(AttributeStatus.INSUFFICIENT.getPriority()   > AttributeStatus.NORMAL.getPriority());         // P6 > P7
    }

    /** 同档内代表值优先级相等（同档仲裁交 sourcePriority 二级处理）。 */
    @Test
    public void priority_sameTierEquals() {
        assertEquals(AttributeStatus.MAINTENANCE.getPriority(),    AttributeStatus.DEVICE_REPLACEMENT.getPriority()); // P1 同档
        assertEquals(AttributeStatus.MAINTENANCE.getPriority(),    AttributeStatus.MALFUNCTION.getPriority());        // P1 同档
        assertEquals(AttributeStatus.OVER_UPPER_LIMIT.getPriority(),AttributeStatus.UNDER_LOWER_LIMIT.getPriority());// P4 同档
        assertEquals(AttributeStatus.NO_CHANGE.getPriority(),      AttributeStatus.ABNORMAL_CHANGE.getPriority());   // P5 同档
    }

    /** OFFLINE/EMPTY 未列入档位 → 落 default(30)，不抢主标记（且 OFFLINE 本就不进 registry）。 */
    @Test
    public void priority_offlineAndEmptyFallToDefault_notCompetitive() {
        assertEquals(30, AttributeStatus.OFFLINE.getPriority());
        assertEquals(30, AttributeStatus.EMPTY.getPriority());
        assertTrue(AttributeStatus.OFFLINE.getPriority() < AttributeStatus.NO_CHANGE.getPriority()); // 低于真实异常档
    }

    /** P2 校准质控检定全组都在 90（防漏列：CONVERSION_CHECK/TEMP_PRESSURE_CALIBRATION 等易遗漏）。 */
    @Test
    public void priority_calibrationQualityVerificationGroup_allAt90() {
        AttributeStatus[] group = {
            AttributeStatus.CALIBRATION, AttributeStatus.ZERO_CHECK, AttributeStatus.SPAN_CHECK,
            AttributeStatus.ACCURACY_CHECK, AttributeStatus.ZERO_CALIBRATION, AttributeStatus.SPAN_CALIBRATION,
            AttributeStatus.FLOW_CHECK, AttributeStatus.QUALITY_CHECK, AttributeStatus.CONVERSION_CHECK,
            AttributeStatus.ZERO_DRIFT, AttributeStatus.SPAN_DRIFT, AttributeStatus.SPAN_REPRODUCIBILITY,
            AttributeStatus.MULTI_POINT_SPAN, AttributeStatus.PRECISION_CHECK, AttributeStatus.TEMP_PRESSURE_CALIBRATION
        };
        for (AttributeStatus s : group) {
            assertEquals(s.name() + " 应在 P2(90)", 90, s.getPriority());
        }
    }
}
