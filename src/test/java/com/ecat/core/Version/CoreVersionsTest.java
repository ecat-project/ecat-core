package com.ecat.core.Version;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * CoreVersions 单测:缝注值透传、无 manifest 默认态负向、缝注 null 负向、
 * -dev 开发源的 target/classes->pom.xml 定位与校验。
 *
 * <p>缝隔离=@Before 保存现值、@After 还原,用例间互不污染;全同步、无真实时间等待。
 */
public class CoreVersionsTest {

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

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

    @Test
    public void developmentVersionComesFromModulePomBesideTargetClasses() throws IOException {
        Path classes = targetClassesWithPom("<artifactId>ecat-core</artifactId>", "<version>9.9.9</version>");

        assertEquals("9.9.9", CoreVersions.developmentVersionFrom(classes));
    }

    @Test
    public void developmentVersionRejectsWrongArtifactId() throws IOException {
        Path classes = targetClassesWithPom("<artifactId>other-module</artifactId>", "<version>9.9.9</version>");

        assertDevelopmentSourceRejected(classes, "artifactId=other-module");
    }

    @Test
    public void developmentVersionRejectsMissingVersion() throws IOException {
        Path classes = targetClassesWithPom("<artifactId>ecat-core</artifactId>", "");

        assertDevelopmentSourceRejected(classes, "缺失直接 version");
    }

    @Test
    public void developmentVersionRejectsInvalidVersion() throws IOException {
        Path classes = targetClassesWithPom("<artifactId>ecat-core</artifactId>", "<version>x.y.z</version>");

        assertDevelopmentSourceRejected(classes, "版本非法: x.y.z");
    }

    @Test
    public void developmentVersionRejectsNonTargetClassesPath() throws IOException {
        Path classes = temporaryFolder.newFolder("classes").toPath().toAbsolutePath();

        assertDevelopmentSourceRejected(classes, "仅支持 ecat-core/target/classes");
    }

    @Test
    public void realDevelopmentSourceReadsCurrentEcatCorePom() {
        CoreVersions.enableDevelopmentVersionSource();

        Version.parse(CoreVersions.current());
    }

    private Path targetClassesWithPom(String artifactIdXml, String versionXml) throws IOException {
        Path module = temporaryFolder.newFolder("ecat-core").toPath().toAbsolutePath();
        Path classes = module.resolve("target").resolve("classes");
        Files.createDirectories(classes);
        Files.write(module.resolve("pom.xml"), (
                "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
                        + "<project xmlns=\"http://maven.apache.org/POM/4.0.0\">"
                        + "<modelVersion>4.0.0</modelVersion>"
                        + artifactIdXml
                        + versionXml
                        + "</project>").getBytes(StandardCharsets.UTF_8));
        return classes;
    }

    private void assertDevelopmentSourceRejected(Path classes, String expectedFragment) {
        try {
            CoreVersions.developmentVersionFrom(classes);
            fail("非法开发版本源应显式拒绝");
        } catch (IllegalStateException e) {
            assertTrue("异常应含「" + expectedFragment + "」，实际=" + e.getMessage(),
                    e.getMessage().contains(expectedFragment));
        }
    }
}
