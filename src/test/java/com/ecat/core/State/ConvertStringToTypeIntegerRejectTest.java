package com.ecat.core.State;

import com.ecat.core.I18n.I18nKeyPath;
import com.ecat.core.Utils.DynamicConfig.ConfigDefinition;
import org.junit.Test;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * setDisplayValue 整型目标的小数拒绝契约（bug-record-20260911-103000 根治验证）。
 *
 * <p><b>背景</b>：整型属性（Integer/Short/Long/Byte）收到带非零小数的字符串 historically 被
 * 向零截断静默写入（"26.5"→26）且报成功——用户以为下发了 26.5 设备实际 26。严格模式要求
 * 显式拒绝。判定按<b>数学值</b>非字符串形态："25.0"/"2.5e1" 解析后是整数 25 → 放行；
 * 仅含非零小数部分才拒绝（经 setDisplayValue 统一包装为类型转换失败 future，不产生写入）。</p>
 *
 * <p>桩形沿用 SetValueImplIoHookTest 的最小属性模式（转换语义即被测对象，
 * setDisplayValueImp 仅记录转换产物）。</p>
 *
 * @author coffee
 */
public class ConvertStringToTypeIntegerRejectTest {

    private abstract static class StubAttr<T> extends AttributeBase<T> {
        StubAttr(String id) { super(id, null, null, null, 0, false, true, null); }
        @Override public String getDisplayValue(UnitInfo toUnit) { return ""; }
        @Override public ConfigDefinition getValueDefinition() { return null; }
        @Override protected I18nKeyPath getI18nPrefixPath() { return new I18nKeyPath("state.test_attr.", ""); }
        @Override public AttributeType getAttributeType() { return AttributeType.UNKNOWN; }
        @Override public Double convertValueToUnit(Double v, UnitInfo f, UnitInfo t) { return v; }
        @Override protected T convertFromUnitImp(T v, UnitInfo u) { return v; }
    }

    private static class IntAttr extends StubAttr<Integer> {
        Integer received;
        IntAttr() { super("test_int"); }
        @Override protected CompletableFuture<Boolean> setDisplayValueImp(Integer v, UnitInfo u) {
            received = v;
            return CompletableFuture.completedFuture(true);
        }
    }

    private static class ShortAttr extends StubAttr<Short> {
        ShortAttr() { super("test_short"); }
        @Override protected CompletableFuture<Boolean> setDisplayValueImp(Short v, UnitInfo u) {
            return CompletableFuture.completedFuture(true);
        }
    }

    private static class LongAttr extends StubAttr<Long> {
        LongAttr() { super("test_long"); }
        @Override protected CompletableFuture<Boolean> setDisplayValueImp(Long v, UnitInfo u) {
            return CompletableFuture.completedFuture(true);
        }
    }

    private static class ByteAttr extends StubAttr<Byte> {
        ByteAttr() { super("test_byte"); }
        @Override protected CompletableFuture<Boolean> setDisplayValueImp(Byte v, UnitInfo u) {
            return CompletableFuture.completedFuture(true);
        }
    }

    /** 浮点目标对照桩：本契约不涉及（浓度/流量等小数属性零波及）。 */
    private static class DoubleAttr extends StubAttr<Double> {
        DoubleAttr() { super("test_double"); }
        @Override protected CompletableFuture<Boolean> setDisplayValueImp(Double v, UnitInfo u) {
            return CompletableFuture.completedFuture(true);
        }
    }

    /** 整型目标 + 非零小数 → future 异常完成且报错含「整数」，不产生写入。 */
    private static void assertRejected(AttributeBase<?> attr, String bad) {
        CompletableFuture<Boolean> f = attr.setDisplayValue(bad);
        try {
            f.get(2, TimeUnit.SECONDS);
            fail("小数输入应异常完成: " + bad);
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            assertTrue("应包装为 IAE，实际: " + cause, cause instanceof IllegalArgumentException);
            assertTrue("报错应说明仅支持整数，实际: " + cause.getMessage(),
                    cause.getMessage().contains("整数"));
        } catch (Exception e) {
            fail("异常形态不符: " + e);
        }
    }

    @Test
    public void integerValuedStrings_pass() throws Exception {
        IntAttr a = new IntAttr();
        assertTrue(a.setDisplayValue("25").get(2, TimeUnit.SECONDS));
        assertTrue("25.0 数学值是整数，应放行", a.setDisplayValue("25.0").get(2, TimeUnit.SECONDS));
        assertTrue("科学计数法整值同放行", a.setDisplayValue("2.5e1").get(2, TimeUnit.SECONDS));
        assertTrue(a.setDisplayValue("-3.0").get(2, TimeUnit.SECONDS));
        assertEquals(Integer.valueOf(-3), a.received);
    }

    @Test
    public void nonZeroFraction_rejected() {
        assertRejected(new IntAttr(), "26.5");
        assertRejected(new IntAttr(), "26.01");
        assertRejected(new IntAttr(), "-0.5");
    }

    @Test
    public void shortLongByte_sameContract() {
        assertRejected(new ShortAttr(), "26.5");
        assertRejected(new LongAttr(), "1.5");
        assertRejected(new ByteAttr(), "3.5");
    }

    @Test
    public void doubleTarget_unaffected() throws Exception {
        assertTrue("Double 目标小数照常放行",
                new DoubleAttr().setDisplayValue("26.5").get(2, TimeUnit.SECONDS));
    }
}
