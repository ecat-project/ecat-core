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

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

/**
 * MultiSelectConfigItem 受限多选校验测试。
 */
public class MultiSelectConfigItemTest {

    private MultiSelectConfigItem item() {
        return ConfigItemBuilder.multiSelect("granularities", true)
                .displayName("推送粒度")
                .addOption("REALTIME", "实时")
                .addOption("MINUTE", "分钟")
                .addOption("HOUR", "小时");
    }

    @Test
    public void getFieldType_isMultiSelect() {
        assertEquals("multi_select", item().getFieldType());
    }

    @Test
    public void validate_selectedSubset_returnsNull() {
        List<String> v = Arrays.asList("REALTIME", "HOUR");
        assertNull("选中项全部在选项值域内应通过", item().validate(v));
    }

    @Test
    public void validate_notAList_returnsStringError() {
        assertNotNull("非列表值应报类型错误", item().validate("REALTIME"));
    }

    @Test
    public void validate_itemNotInOptions_returnsStringError() {
        Object r = item().validate(Arrays.asList("REALTIME", "DAILY"));
        assertNotNull("含值域外选项应报错", r);
        assertEquals("错误应为字符串（叶子字段错误）", true, r instanceof String);
    }

    @Test
    public void validate_requiredEmptyList_returnsStringError() {
        Object r = item().validate(Collections.emptyList());
        assertNotNull("required 语义=至少选一项，空集应报错", r);
    }

    @Test
    public void validate_optionalEmptyList_returnsNull() {
        MultiSelectConfigItem optional = ConfigItemBuilder.multiSelect("tags", false)
                .addOption("A", "甲");
        assertNull("非 required 时空集合法（用户可全不选）", optional.validate(Collections.emptyList()));
    }

    @Test
    public void validate_requiredNull_returnsStringError() {
        assertNotNull("required 时 null 应报缺失", item().validate(null));
    }

    @Test
    public void validate_optionalNull_returnsNull() {
        MultiSelectConfigItem optional = ConfigItemBuilder.multiSelect("tags", false)
                .addOption("A", "甲");
        assertNull("非 required 时 null 合法", optional.validate(null));
    }

    @Test
    public void addOption_singleArg_labelEqualsValue() {
        MultiSelectConfigItem i = ConfigItemBuilder.multiSelect("k", false).addOption("X");
        assertEquals("只传 value 时 label 与 value 相同", "X", i.getOptionLabel("X"));
    }
}
