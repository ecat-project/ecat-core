package com.ecat.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.function.Supplier;

import org.junit.Test;

import com.ecat.core.Version.CoreVersions;
import com.ecat.core.Version.CoreVersionsTestAccess;
import com.ecat.core.Version.Version;

/**
 * EcatCore 启动参数契约测试：-dev 是显式开发版本源开关，未知/重复参数禁静默忽略。
 */
public class EcatCoreStartupArgumentsTest {

    @Test
    public void noArgumentKeepsProductionVersionSource() {
        assertFalse(EcatCore.parseDevelopmentMode(new String[0]));
    }

    @Test
    public void devArgumentEnablesDevelopmentVersionSource() {
        assertTrue(EcatCore.parseDevelopmentMode(new String[] {"-dev"}));
    }

    @Test
    public void mainWiringSwitchesCoreVersionsToCurrentEcatCorePom() {
        Supplier<String> savedSource = CoreVersionsTestAccess.currentSource();
        try {
            EcatCore.configureDevelopmentVersionSource(new String[] {"-dev"});

            Version.parse(CoreVersions.current());
        } finally {
            CoreVersionsTestAccess.injectSource(savedSource);
        }
    }

    @Test
    public void mainWiringWithoutDevKeepsManifestSource() {
        Supplier<String> savedSource = CoreVersionsTestAccess.currentSource();

        EcatCore.configureDevelopmentVersionSource(new String[0]);

        assertSame(savedSource, CoreVersionsTestAccess.currentSource());
    }

    @Test
    public void unknownArgumentRejectedLoudly() {
        try {
            EcatCore.parseDevelopmentMode(new String[] {"--unknown"});
            fail("未知启动参数应显式拒绝");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("--unknown"));
            assertTrue(e.getMessage().contains("-dev"));
        }
    }

    @Test
    public void duplicateDevArgumentRejectedLoudly() {
        try {
            EcatCore.parseDevelopmentMode(new String[] {"-dev", "-dev"});
            fail("重复 -dev 应显式拒绝");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("-dev 只能出现一次"));
        }
    }
}
