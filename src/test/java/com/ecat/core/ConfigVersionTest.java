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

package com.ecat.core;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * ConfigVersion.requireSupported 四态判定表测试（root version 戳 fail-closed 校验）。
 *
 * <p>锁定判定序：缺失/空/非字符串/格式坏/旧戳/未来戳全拒，当前戳放行；异常消息
 * expected/actual 与分支化修法指引各断言一例，防渲染面退化成无定位价值的笼统报错。
 *
 * @author coffee
 */
public class ConfigVersionTest {

    /** 全部必须被拒绝的输入：缺失、空、未加引号的数值形态、格式坏、旧戳、未来戳。 */
    private static final List<Object> REJECTED_INPUTS = Arrays.<Object>asList(
        null, "",
        Double.valueOf(4.0), Integer.valueOf(40),
        "4", "04.0", "4.0.0", "v4.0", "3.9", "0.9", "5.0", "10.0");

    @Test
    public void currentStampPasses() {
        ConfigVersion.requireSupported("4.0");
        ConfigVersion.requireSupported(ConfigVersion.CURRENT_VERSION);
    }

    @Test
    public void everyOtherShapeIsRejected() {
        for (Object input : REJECTED_INPUTS) {
            try {
                ConfigVersion.requireSupported(input);
                fail("应拒: " + (input == null ? "null" : input.getClass().getSimpleName() + ":" + input));
            } catch (ConfigFormatException expected) {
                // 各分支的 expected/actual 细节由下方分支用例逐态断言，表内只锁「全拒」
            }
        }
    }

    @Test
    public void missingStampNamesAbsenceAndBansLegacyLenientGate() {
        try {
            ConfigVersion.requireSupported(null);
            fail("无戳必须拒（旧宽门已废止）");
        } catch (ConfigFormatException e) {
            assertEquals("缺失", e.getActual());
            assertTrue("应明示旧宽门废止", e.getMessage().contains("无戳不视为兼容"));
        }
    }

    @Test
    public void nonStringStampNamesTypeAndExplainsUnquotedYaml() {
        try {
            ConfigVersion.requireSupported(Double.valueOf(4.0));
            fail("未加引号的数值形态必须拒");
        } catch (ConfigFormatException e) {
            assertEquals("actual 应为类型:值形态", "Double:4.0", e.getActual());
            assertTrue("应解释未加引号的解析后果", e.getMessage().contains("带引号"));
        }
    }

    @Test
    public void malformedStampDelegatesToFormatVocabulary() {
        try {
            ConfigVersion.requireSupported("4.0.0");
            fail("非 major.minor 格式必须拒");
        } catch (ConfigFormatException e) {
            assertTrue("格式判定应复用 FormatVersion 词汇（expected=两段字符串）",
                e.getExpected().contains("major.minor"));
        }
    }

    @Test
    public void olderStampExplainsResidueAndNormalizationWindow() {
        try {
            ConfigVersion.requireSupported("3.9");
            fail("旧戳残留必须拒");
        } catch (ConfigFormatException e) {
            assertEquals("3.9", e.getActual());
            assertTrue("应指明旧戳归停机窗盖戳", e.getMessage().contains("旧戳残留"));
        }
    }

    @Test
    public void futureStampExplainsForwardIncompatibility() {
        try {
            ConfigVersion.requireSupported("5.0");
            fail("未来戳必须拒（数据比代码新）");
        } catch (ConfigFormatException e) {
            assertEquals("5.0", e.getActual());
            assertTrue("应指明前向不兼容", e.getMessage().contains("前向不兼容"));
        }
    }
}
