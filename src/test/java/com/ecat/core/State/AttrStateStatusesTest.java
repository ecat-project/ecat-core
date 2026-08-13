package com.ecat.core.State;

import com.ecat.core.Bus.event.EventContext;
import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.Assert.*;

/**
 * AttrState.statuses 字段测试。
 *
 * <p>ecat-core 用 JUnit 4（junit:junit:4.13.2，pom 已声明），故测试走 {@code org.junit.Test} /
 * {@code org.junit.Assert.*}。{@code assertThrows} 是 JUnit 4.13 才加的，但本测多断言混在一个方法里，
 * {@code @Test(expected=...)} 会一刀切，故对不可变 add 走 try/catch/fail 模式精确捕获。
 */
public class AttrStateStatusesTest {

    private AttrState<Double> buildWithStatus(AttributeStatus single) {
        return AttrState.<Double>builder()
                .deviceId("dev-1").attrId("so2").value(15.0).valueType(Double.class)
                .status(single).context(EventContext.root(EventContext.Source.DEVICE_POLL, null))
                .build();
    }

    /** 物理 attr 路径：builder 不设 statuses → 兜底 {status}，永不 null、永不空。 */
    @Test
    public void statuses_defaultsToSingletonOfStatus_whenBuilderOmitsIt() {
        AttrState<Double> state = buildWithStatus(AttributeStatus.NORMAL);
        assertEquals(AttributeStatus.NORMAL, state.getStatus());              // 老 getStatus 向后兼容
        assertEquals(Collections.singleton(AttributeStatus.NORMAL), state.getStatuses());    // 新 getStatuses 兜底
    }

    /** logic attr 路径：builder 塞全量集合 → 不可变 Set（保留 LinkedHashSet 保序）。 */
    @Test
    public void statuses_holdsFullSetFromBuilder_whenLogicLayerProvidesIt() {
        Set<AttributeStatus> live = new LinkedHashSet<>();
        live.add(AttributeStatus.OVER_UPPER_LIMIT);
        live.add(AttributeStatus.CALIBRATION);

        AttrState<Double> state = AttrState.<Double>builder()
                .deviceId("dev-1").attrId("so2").value(200.0).valueType(Double.class)
                .status(AttributeStatus.CALIBRATION)
                .statuses(live)
                .context(EventContext.root(EventContext.Source.DEVICE_POLL, null))
                .build();

        // 是 Set（不是被 defensiveCopy 转成的 List）
        assertTrue("getStatuses 应返回 Set 类型", state.getStatuses() instanceof Set);
        assertEquals(live, state.getStatuses());
    }

    /** 不可变：构造后再改源集合 / 直接 add 都不影响 / 抛异常。 */
    @Test
    public void statuses_isImmutable_defensiveCopy() {
        Set<AttributeStatus> live = new LinkedHashSet<>();
        live.add(AttributeStatus.CALIBRATION);
        AttrState<Double> state = AttrState.<Double>builder()
                .deviceId("dev-1").attrId("so2").value(1.0).valueType(Double.class)
                .status(AttributeStatus.CALIBRATION).statuses(live)
                .context(EventContext.root(EventContext.Source.DEVICE_POLL, null))
                .build();

        live.add(AttributeStatus.MALFUNCTION);                       // 改源集合
        assertFalse(state.getStatuses().contains(AttributeStatus.MALFUNCTION)); // 防御拷贝生效

        // 返回不可变 Set：add 必抛 UnsupportedOperationException
        try {
            state.getStatuses().add(AttributeStatus.MALFUNCTION);
            fail("getStatuses() 应返回不可变 Set，add 必须抛 UnsupportedOperationException");
        } catch (UnsupportedOperationException expected) {
            // 预期路径——不可变 Set 拒写
        }
    }
}
