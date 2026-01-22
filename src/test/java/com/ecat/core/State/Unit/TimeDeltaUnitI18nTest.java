package com.ecat.core.State.Unit;

import com.ecat.core.I18n.ResourceLoader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * TimeDeltaUnit类的国际化单元测试
 */
public class TimeDeltaUnitI18nTest {

    @Before
    public void setUp() {
        // 确保测试只使用 strings.json，不加载 i18n 目录资源
        ResourceLoader.setLoadI18nResources(false);
    }

    @After
    public void tearDown() {
        // 恢复默认设置
        ResourceLoader.setLoadI18nResources(true);
    }

    @Test
    public void testTimeDeltaUnitDisplayName() {
        // 测试基本属性
        assertEquals("time_delta", TimeDeltaUnit.SECOND.getUnitCategory());
        assertEquals("second", TimeDeltaUnit.SECOND.getEnumName());

        // 测试国际化显示名称
        String displayName = TimeDeltaUnit.SECOND.getDisplayName();
        assertNotNull("显示名称不应为null", displayName);
        assertEquals("秒的单位符号应正确", "s", displayName);

        // 验证单位实现了 InternationalizedUnit 接口
        assertTrue("TimeDeltaUnit应实现InternationalizedUnit接口",
                   TimeDeltaUnit.SECOND instanceof InternationalizedUnit);
    }
}