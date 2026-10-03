package com.ecat.core.Version;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.function.Supplier;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * CoreVersions 单测:缝注值透传、无 manifest 默认态负向、缝注 null 负向。
 *
 * <p>缝隔离=@Before 保存现值、@After 还原,用例间互不污染;全同步、无真实时间等待。
 */
public class CoreVersionsTest {

    private Supplier<String> savedSource;

    @Before
    public void saveSeam() {
        savedSource = CoreVersions.versionSource;
    }

    @After
    public void restoreSeam() {
        CoreVersions.versionSource = savedSource;
    }

    /** 缝注值透传:current() 原样返回缝供的版本串——后续兼容门类测试依赖此缝形态。 */
    @Test
    public void testSeamInjectedValueFlowsThrough() {
        CoreVersions.versionSource = () -> "3.9.0";
        assertEquals("3.9.0", CoreVersions.current());
    }

    /** 默认态负向(单测 classes/ 目录加载,无 manifest):明确异常,禁默认值兜底。 */
    @Test
    public void testDefaultStateWithoutManifestThrows() {
        try {
            CoreVersions.current();
            fail("无 manifest 时 current() 应抛 IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue("异常消息应指明 Implementation-Version,实际=" + e.getMessage(),
                e.getMessage().contains("Implementation-Version"));
        }
    }

    /** 缝注 null 负向:缝不弱化执法,与默认态同一异常路径。 */
    @Test
    public void testSeamInjectedNullThrows() {
        CoreVersions.versionSource = () -> null;
        try {
            CoreVersions.current();
            fail("缝注 null 时 current() 应抛 IllegalStateException");
        } catch (IllegalStateException e) {
            assertTrue("异常消息应指明 Implementation-Version,实际=" + e.getMessage(),
                e.getMessage().contains("Implementation-Version"));
        }
    }
}
