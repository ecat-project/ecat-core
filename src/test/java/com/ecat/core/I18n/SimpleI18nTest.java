package com.ecat.core.I18n;

import org.junit.Test;

import com.ecat.core.Const;

import static org.junit.Assert.*;

/**
 * Simple I18n test to verify basic functionality
 *
 * @author coffee
 */
public class SimpleI18nTest {

    @Test
    public void testBasicI18nFunctionality() {
        // Test that we can create the basic I18n components
        I18nConfig config = I18nConfig.getInstance();
        assertNotNull(config);

        I18nRegistry registry = I18nRegistry.getInstance();
        assertNotNull(registry);

        I18nProxy proxy = I18nHelper.createCoreProxy();
        assertNotNull(proxy);

        // Test namespace
        assertEquals(Const.CORE_ARTIFACT_ID, proxy.getNamespace());

        // Test basic translation (should return key if not found)
        String result = proxy.t("test.key");
        assertEquals("test.key", result);
    }
}
