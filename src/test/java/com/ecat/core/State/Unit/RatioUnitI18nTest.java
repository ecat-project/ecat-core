package com.ecat.core.State.Unit;

import com.ecat.core.I18n.ResourceLoader;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * RatioUnit类的国际化单元测试
 */
public class RatioUnitI18nTest {

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
    public void testRatioUnitDisplayName() {
        // 测试基本属性
        assertEquals("ratio", RatioUnit.PERCENT.getUnitCategory());
        assertEquals("percent", RatioUnit.PERCENT.getEnumName());

        // 测试国际化显示名称
        String displayName = RatioUnit.PERCENT.getDisplayName();
        assertNotNull("显示名称不应为null", displayName);
        assertEquals("百分比的单位符号应正确", "%", displayName);

        // 验证单位实现了 InternationalizedUnit 接口
        assertTrue("RatioUnit应实现InternationalizedUnit接口",
                   RatioUnit.PERCENT instanceof InternationalizedUnit);
    }
}