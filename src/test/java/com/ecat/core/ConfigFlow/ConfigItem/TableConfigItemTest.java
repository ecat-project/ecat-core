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
package com.ecat.core.ConfigFlow.ConfigItem;

import com.ecat.core.ConfigFlow.ConfigSchema;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * TableConfigItem 行级校验测试。
 */
public class TableConfigItemTest {

    /** 行 schema：两个必填文本列（name + tag），用于验证 TableConfigItem 的结构化行级校验。 */
    private ConfigSchema rowSchema() {
        return new ConfigSchema()
                .addField(new TextConfigItem("name", true))
                .addField(new TextConfigItem("tag", true));
    }

    private Map<String, Object> row(String name, String tag) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (name != null) {
            m.put("name", name);
        }
        if (tag != null) {
            m.put("tag", tag);
        }
        return m;
    }

    @Test
    public void validate_allRowsValid_returnsNull() {
        TableConfigItem t = ConfigItemBuilder.table("points", true, rowSchema());
        List<Map<String, Object>> rows = Arrays.asList(row("temp", "a"), row("hum", "b"));
        assertNull("所有行字段齐全应返回 null", t.validate(rows));
    }

    @Test
    public void validate_rowWithFieldErrors_returnsRowIndexedMap() {
        TableConfigItem t = ConfigItemBuilder.table("points", true, rowSchema());
        List<Map<String, Object>> rows = Arrays.asList(
                row("temp", "a"),        // 第 0 行齐全
                row(null, null));        // 第 1 行 name/tag 缺失 → 行内字段错误
        Object result = t.validate(rows);
        assertNotNull(result);
        assertTrue("行级错误应为 Map", result instanceof Map);
        Map<?, ?> m = (Map<?, ?>) result;
        assertTrue("第 1 行应有错误（行号键为字符串，避免 Integer 键 JSON 序列化畸形）", m.containsKey("1"));
        Map<?, ?> row1 = (Map<?, ?>) m.get("1");
        assertTrue("第 1 行 name 应报错", row1.containsKey("name"));
        assertTrue("第 1 行 tag 应报错", row1.containsKey("tag"));
    }

    @Test
    public void validate_notAList_returnsStringError() {
        TableConfigItem t = ConfigItemBuilder.table("points", true, rowSchema());
        Object r = t.validate("not a list");
        assertTrue("非列表应返回字符串错误", r instanceof String);
    }

    @Test
    public void getFieldType_isTable() {
        assertEquals("table", ConfigItemBuilder.table("p", true, rowSchema()).getFieldType());
    }

    @Test
    public void minRows_constraint_returnsStringError() {
        TableConfigItem t = ConfigItemBuilder.table("p", true, rowSchema()).minRows(3);
        Object r = t.validate(Arrays.asList(row("a", "x")));
        assertTrue("少于 minRows 应返回字符串错误", r instanceof String);
    }
}
