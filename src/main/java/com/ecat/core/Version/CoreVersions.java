/*
 * Copyright (c) 2026 ECAT Team
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ecat.core.Version;

import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.CodeSource;
import java.util.Objects;
import java.util.function.Supplier;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import com.ecat.core.EcatCore;
import com.ecat.core.Utils.Log;
import com.ecat.core.Utils.LogFactory;

/**
 * core 自身版本的唯一权威出口。
 *
 * <p>生产值来自 fat jar manifest 的 Implementation-Version 条目——打包期由 pom
 * {@code project.version} 写入,与 pom 单源零漂移:升版本只改 pom,本类零改动。
 * 以 {@link EcatCore} 为 manifest 载体类取包元数据:它是 Main-Class、只在 core
 * 主 jar 出现一次,取包无重复类加载歧义。
 * IDE 从 {@code target/classes} 显式带 {@code -dev} 调试时,开发版本源读取同模块
 * pom.xml;这是受控开发形态,不是生产兜底。</p>
 *
 * <p>返回值契约:要么是合法版本串,要么抛 {@link IllegalStateException},永不为
 * null、也不落 "0.0.0" 类默认值——版本不明时静默放行比明确失败更危险。
 *
 * <p>值形态 MAJOR.MINOR.PATCH 三段,满足 {@link Version#parse} 的输入契约,消费方
 * (兼容门/版本比对)可直接解析;本类不做格式执法,格式执法归消费方既有解析逻辑。
 */
public final class CoreVersions {

    /**
     * 版本值源缝:包级可见 static volatile,本类唯一可变状态。
     *
     * <p>仅供同包单测注值(单测/IDE 从 classes/ 目录加载,无 manifest 可读,用例须经此缝
     * 供值);生产外部代码禁写,唯一例外是本类 {@link #enableDevelopmentVersionSource()}
     * 在显式 {@code -dev} 启动时切换受控开发源。缝形态先例=Utils.YamlAtomicFileWriter#moveAtomicallyWithRetry
     * (刻意测试缝,javadoc 同款声明风格)。勿改 private(同包测试不可达)、勿加 public
     * setter(字段已包级可见,setter 无增益反扩暴露面)。
     */
    static volatile Supplier<String> versionSource = () -> {
        Package pkg = EcatCore.class.getPackage();
        return pkg != null ? pkg.getImplementationVersion() : null;
    };

    private static final Log LOG = LogFactory.getLogger(CoreVersions.class);

    private CoreVersions() {
    }

    /**
     * 获取当前 core 版本(唯一权威出口)。
     *
     * @return 版本串(MAJOR.MINOR.PATCH,来自 fat jar manifest 的 Implementation-Version,
     *         构建期由 pom project.version 写入,单源零漂移)
     * @throws IllegalStateException manifest 缺失/不可读(单测从 classes/ 加载、异常装配形态)。
     *         版本不明=兼容门失明,失明的门比没有更危险——明确失败,禁返 "0.0.0" 类兜底
     */
    public static String current() {
        String v = versionSource.get();
        if (v == null || v.isEmpty()) {
            throw new IllegalStateException("core 版本不可得:manifest 无 Implementation-Version"
                + "(生产=fat jar 打包异常;IDE target/classes 调试=启动参数加 -dev;"
                + "单测=请经 CoreVersions 包级测试缝注值)");
        }
        return v;
    }

    /**
     * 显式启用开发版本源：仅服务 IDE 从 {@code ecat-core/target/classes} 直启的调试形态。
     *
     * <p>该形态没有 jar manifest，Java 的目录 classloader 也不会读取 classes 目录下的
     * MANIFEST 元数据；因此版本改为读取 {@code target/classes} 所属模块根下的 pom.xml。
     * 只认 {@code <artifactId>ecat-core</artifactId>} 的直接 {@code <version>}，不做
     * workspace 向上扫描，不做 parent 版本推断，防止拿错模块或把开发源泛化成生产兜底。</p>
     *
     * <p>调用后 {@link #current()} 返回 pom 正式版本串；dev 标识只进日志，不拼进版本值，
     * 避免 {@code 4.0.0-dev < 4.0.0} 破坏 requires_core 语义。</p>
     */
    public static void enableDevelopmentVersionSource() {
        Path classes = currentCodeSourcePath();
        String version = developmentVersionFrom(classes);
        versionSource = () -> version;
        Path pom = classes.getParent().getParent().resolve("pom.xml");
        LOG.warn("core 开发版本源启用：版本 {} 读取自 {}；仅支持 target/classes IDE 直启，"
                + "生产版本权威来源仍是 jar manifest", version, pom);
    }

    private static Path currentCodeSourcePath() {
        CodeSource codeSource = EcatCore.class.getProtectionDomain().getCodeSource();
        if (codeSource == null || codeSource.getLocation() == null) {
            throw new IllegalStateException("core 开发版本源不可用:EcatCore 无 CodeSource");
        }
        URL location = codeSource.getLocation();
        if (!"file".equalsIgnoreCase(location.getProtocol())) {
            throw new IllegalStateException("core 开发版本源仅支持 file target/classes，实际: " + location);
        }
        try {
            return Paths.get(location.toURI());
        } catch (URISyntaxException e) {
            throw new IllegalStateException("core 开发版本源 CodeSource URL 无法转路径: " + location, e);
        }
    }

    /** 供单测锁定 target/classes -> 模块根 pom.xml 的受控定位与 XML 读取契约。 */
    static String developmentVersionFrom(Path classesDir) {
        Objects.requireNonNull(classesDir, "classesDir");
        if (!classesDir.isAbsolute() || !hasFileName(classesDir, "classes")
                || classesDir.getParent() == null || !hasFileName(classesDir.getParent(), "target")
                || !Files.isDirectory(classesDir)) {
            throw new IllegalStateException("core 开发版本源仅支持 ecat-core/target/classes，实际: " + classesDir);
        }
        Path targetDir = classesDir.getParent();
        Path moduleDir = targetDir.getParent();
        if (moduleDir == null) {
            throw new IllegalStateException("core 开发版本源缺少模块根目录: " + classesDir);
        }
        Path pom = moduleDir.resolve("pom.xml");
        if (!Files.isRegularFile(pom)) {
            throw new IllegalStateException("core 开发版本源未找到 ecat-core pom: " + pom);
        }

        Document document;
        try (InputStream in = Files.newInputStream(pom)) {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setExpandEntityReferences(false);
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            document = builder.parse(in);
        } catch (IOException | ParserConfigurationException | SAXException e) {
            throw new IllegalStateException("core 开发版本源读取 ecat-core pom 失败: " + pom, e);
        }

        String artifactId = directChildText(document.getDocumentElement(), "artifactId");
        if (!"ecat-core".equals(artifactId)) {
            throw new IllegalStateException("core 开发版本源 pom 校验失败: artifactId=" + artifactId);
        }
        String version = directChildText(document.getDocumentElement(), "version");
        if (version == null || version.isEmpty()) {
            throw new IllegalStateException("core 开发版本源 pom 缺失直接 version: " + pom);
        }
        try {
            Version.parse(version);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("core 开发版本源 pom 版本非法: " + version
                    + " (" + pom + ")", e);
        }
        return version;
    }

    private static boolean hasFileName(Path path, String expected) {
        Path fileName = path.getFileName();
        return fileName != null && expected.equals(fileName.toString());
    }

    private static String directChildText(Element root, String childName) {
        NodeList children = root.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element
                    && childName.equals(children.item(i).getLocalName())) {
                String value = children.item(i).getTextContent().trim();
                return value.isEmpty() ? null : value;
            }
        }
        return null;
    }
}
