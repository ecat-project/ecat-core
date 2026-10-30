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

package com.ecat.core.Integration;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

import com.ecat.core.ConfigFormatException;
import com.ecat.core.EcatCore;
import com.ecat.core.State.StateManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

/**
 * 零集成启用面（integrations.yml 空启用集）启动契约测试。
 *
 * <p>背景（bug-record-20261029-091900）：手写最小配置 {@code version: '4.0'} + 裸
 * {@code integrations:}（键在值 null——峰值压测 headless 最小面即此形态）曾被当合法
 * 输入送进 {@code cleanupPendingRemovedIntegrations}，SnakeYAML 的 null 值让
 * {@code getOrDefault} 返回存储 null（非默认空 map），2858 行 entrySet 即 NPE 杀 boot。
 *
 * <p>被测契约：空启用集=正常启动（纯 core 域最小面），日志一行说明零集成启用；
 * 3.3 无戳存量 + 空 integrations 根仍 fail-closed 拒绝（合法边界只放宽「带当前戳的
 * 空启用集」，不放宽格式门）。
 *
 * <p>红预测：修复前 null 归一缺失——空 map 断言拿到 null 红、loadIntegrations 以
 * NPE 红；无戳负向两态（修复前后）都必须绿（本用例守护门不被顺手松动）。
 *
 * @author coffee
 */
public class ZeroIntegrationsStartupTest {

    private File testDir;
    private File configFile;
    private String originalConfigPath;
    private ListAppender<ILoggingEvent> logCapture;

    @Before
    public void setUp() throws Exception {
        testDir = new File("target", ".ecat-zero-integrations");
        deleteRecursively(testDir);
        assertTrue("测试目录创建失败: " + testDir, testDir.mkdirs() || testDir.exists());
        configFile = new File(testDir, "integrations.yml");

        originalConfigPath = (String) getStaticField("INTEGRATIONS_CONFIG_PATH");

        Logger managerLogger = (Logger) LoggerFactory.getLogger(IntegrationManager.class);
        logCapture = new ListAppender<>();
        logCapture.start();
        managerLogger.addAppender(logCapture);
        managerLogger.setLevel(Level.INFO);
    }

    @After
    public void tearDown() throws Exception {
        Logger managerLogger = (Logger) LoggerFactory.getLogger(IntegrationManager.class);
        managerLogger.detachAppender(logCapture);

        if (originalConfigPath != null) {
            setStaticField("INTEGRATIONS_CONFIG_PATH", originalConfigPath);
        }
        deleteRecursively(testDir);
    }

    /** 复现形态（裸 integrations: 键）：解析收口归一为空 map，调用方拿不到 null */
    @Test
    public void bareIntegrationsKey_parsesToEmptyMap() throws Exception {
        writeConfig("version: '4.0'\nintegrations:\n");

        Map<String, Map<String, Object>> config = newManager().loadIntegrationsConfig();

        Object integrations = config.get("integrations");
        assertTrue("裸 integrations: 键应归一为空 map，实得: " + integrations,
            integrations instanceof Map && ((Map<?, ?>) integrations).isEmpty());
    }

    /** 端到端：空启用集 boot 正常完成（不再 NPE 秒退），零集成说明日志恰好一行 */
    @Test
    public void bareIntegrationsKey_bootCompletesWithZeroIntegrationLog() throws Exception {
        writeConfig("version: '4.0'\nintegrations:\n");

        newManager().loadIntegrations();   // 不抛=boot 照常完成（修复前此处 NPE 杀 boot）

        long zeroLines = logCapture.list.stream()
            .filter(event -> event.getFormattedMessage().contains("零集成"))
            .count();
        assertEquals("空启用集应有一行零集成说明日志", 1L, zeroLines);
    }

    /** 显式空 map 形态（integrations: {}）与裸键形态同语义：boot 照常 + 同一行说明 */
    @Test
    public void emptyMapForm_bootCompletesSameAsBareKey() throws Exception {
        writeConfig("version: '4.0'\nintegrations: {}\n");

        newManager().loadIntegrations();

        long zeroLines = logCapture.list.stream()
            .filter(event -> event.getFormattedMessage().contains("零集成"))
            .count();
        assertEquals("空 map 形态与裸键形态应同语义", 1L, zeroLines);
    }

    /** 负向守门：3.3 无戳存量 + 空 integrations 根仍拒绝——空启用集合法边界不带开格式门 */
    @Test
    public void legacyNoStampWithBareIntegrations_stillRejected() throws Exception {
        writeConfig("integrations:\n");

        assertThrows(ConfigFormatException.class, () -> newManager().loadIntegrationsConfig());
    }

    // ==================== 夹具 ====================

    private IntegrationManager newManager() {
        return new IntegrationManager(mock(EcatCore.class), new IntegrationRegistry(),
            mock(StateManager.class));
    }

    private void writeConfig(String content) throws Exception {
        Files.write(configFile.toPath(), content.getBytes(StandardCharsets.UTF_8));
        setStaticField("INTEGRATIONS_CONFIG_PATH", configFile.getAbsolutePath());
    }

    private static Object getStaticField(String name) throws Exception {
        Field field = IntegrationManager.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(null);
    }

    private static void setStaticField(String name, Object value) throws Exception {
        Field field = IntegrationManager.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(null, value);
    }

    private static void deleteRecursively(File file) {
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        if (file.exists() && !file.delete()) {
            file.deleteOnExit();
        }
    }
}
