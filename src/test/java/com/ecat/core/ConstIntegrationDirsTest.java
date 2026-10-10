package com.ecat.core;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.Test;

/**
 * 集成目录双面分配器契约（统一执法面）：
 * <ul>
 *   <li>{@code integrationAppDir} → {@code .ecat-data/integrations/{g}/{a}}——应用安装面：
 *       二进制/原生库/生成配置（可再生、不增长）；</li>
 *   <li>{@code integrationStorageDir} → {@code .ecat-data/storage/{g}/{a}}——数据面：
 *       日志/数据库/容器卷（可增长）。</li>
 * </ul>
 * groupId 作为一个目录段保留点号（与 config_entries 布局对齐）；段值经安全校验防路径穿越。
 * 治理规则：integrations 只放应用，数据/日志一律入 storage（历史 violator：
 * mediagateway logs、llm-agent hermes-home）。
 */
public class ConstIntegrationDirsTest {

    /** 应用面布局契约：.ecat-data/integrations/{groupId}/{artifactId}。 */
    @Test
    public void appDir_coordinateLayout() {
        Path dir = Const.integrationAppDir("com.ecat", "integration-mediagateway");
        assertEquals(Paths.get(".ecat-data", "integrations", "com.ecat", "integration-mediagateway"), dir);
    }

    /** 数据面布局契约：.ecat-data/storage/{groupId}/{artifactId}（与 media/vision-analysis 既有实况一致）。 */
    @Test
    public void storageDir_coordinateLayout() {
        Path dir = Const.integrationStorageDir("com.ecat", "integration-media");
        assertEquals(Paths.get(".ecat-data", "storage", "com.ecat", "integration-media"), dir);
    }

    /** groupId 点号是合法目录段字符，保留为单段（禁 m2 式拆成 com/ecat 两级），双面同规。 */
    @Test
    public void groupIdDots_staySingleSegment_bothFaces() {
        Path app = Const.integrationAppDir("com.ecat", "integration-media");
        assertEquals("groupId 点号不得拆段", "com.ecat",
                app.getParent().getFileName().toString());
        Path storage = Const.integrationStorageDir("com.ecat", "integration-media");
        assertEquals("groupId 点号不得拆段", "com.ecat",
                storage.getParent().getFileName().toString());
    }

    /** 段安全校验：null/空/路径分隔符/当前与父目录段拒绝（防路径穿越，严格模式），双面同规。 */
    @Test
    public void unsafeSegments_rejected_bothFaces() {
        assertThrows("app: groupId null", IllegalArgumentException.class,
                () -> Const.integrationAppDir(null, "x"));
        assertThrows("app: artifactId 空", IllegalArgumentException.class,
                () -> Const.integrationAppDir("com.ecat", ""));
        assertThrows("app: groupId 含斜杠", IllegalArgumentException.class,
                () -> Const.integrationAppDir("../evil", "x"));
        assertThrows("storage: groupId null", IllegalArgumentException.class,
                () -> Const.integrationStorageDir(null, "x"));
        assertThrows("storage: artifactId 含反斜杠", IllegalArgumentException.class,
                () -> Const.integrationStorageDir("com.ecat", "a\\b"));
        assertThrows("storage: artifactId 为父目录段", IllegalArgumentException.class,
                () -> Const.integrationStorageDir("com.ecat", ".."));
        assertThrows("app: artifactId 为当前目录段", IllegalArgumentException.class,
                () -> Const.integrationAppDir("com.ecat", "."));
    }
}
