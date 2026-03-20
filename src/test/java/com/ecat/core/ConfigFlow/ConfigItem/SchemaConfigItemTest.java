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

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

/**
 * SchemaConfigItem 单元测试
 * <p>
 * 重点验证 required 检查、嵌套 schema 递归验证、null 值处理。
 */
public class SchemaConfigItemTest {

    // ========== required 检查 ==========

    @Test
    public void testRequired_NullValue_ReturnsError() {
        SchemaConfigItem item = new SchemaConfigItem("nested", true, new ConfigSchema());
        String error = item.validate(null);
        assertNotNull("required=true, null should return error", error);
        assertTrue("Error should mention field key", error.contains("nested"));
    }

    @Test
    public void testRequired_NullValue_WithDisplayName() {
        SchemaConfigItem item = new SchemaConfigItem("nested", true, new ConfigSchema());
        item.displayName("嵌套配置");
        String error = item.validate(null);
        assertNotNull("required=true, null should return error", error);
        assertTrue("Error should mention display name", error.contains("嵌套配置"));
    }

    @Test
    public void testOptional_NullValue_ReturnsNoError() {
        SchemaConfigItem item = new SchemaConfigItem("nested", false, new ConfigSchema());
        String error = item.validate(null);
        assertNull("required=false, null should pass", error);
    }

    @Test
    public void testRequired_PresentValue_ReturnsNoError() {
        SchemaConfigItem item = new SchemaConfigItem("nested", true, new ConfigSchema());
        Map<String, Object> value = new HashMap<>();
        String error = item.validate(value);
        assertNull("required=true, non-null Map should pass", error);
    }

    // ========== 类型检查 ==========

    @Test
    public void testValidateType_NonMapValue_ReturnsError() {
        SchemaConfigItem item = new SchemaConfigItem("nested", true, new ConfigSchema());
        item.displayName("嵌套配置");
        String error = item.validate("not a map");
        assertNotNull("Non-Map value should return type error", error);
        assertTrue("Error should mention display name", error.contains("嵌套配置"));
    }

    @Test
    public void testValidateType_NonMapValue_NoDisplayName() {
        SchemaConfigItem item = new SchemaConfigItem("nested", true, new ConfigSchema());
        String error = item.validate("not a map");
        assertNotNull("Non-Map value should return type error", error);
        assertTrue("Error should mention field key", error.contains("nested"));
    }

    // ========== Provider 模式 ==========

    @Test
    public void testProvider_Required_NullValue_ReturnsError() {
        SchemaConfigItem item = new SchemaConfigItem("nested", true, StubSchemaProvider.class);
        String error = item.validate(null);
        assertNotNull("required=true, null should return error", error);
    }

    @Test
    public void testProvider_Optional_NullValue_ReturnsNoError() {
        SchemaConfigItem item = new SchemaConfigItem("nested", false, StubSchemaProvider.class);
        String error = item.validate(null);
        assertNull("required=false, null should pass", error);
    }

    // ========== 嵌套 Schema 递归验证 ==========

    @Test
    public void testNestedSchema_RequiredChildFieldMissing_ReturnsError() {
        ConfigSchema nestedSchema = new ConfigSchema()
                .addField(new TextConfigItem("child_required", true).displayName("子字段"));

        SchemaConfigItem item = new SchemaConfigItem("nested", true, nestedSchema);
        item.displayName("嵌套配置");

        Map<String, Object> value = new HashMap<>();
        // child_required is missing
        String error = item.validate(value);
        assertNotNull("Missing required child field should return error", error);
    }

    @Test
    public void testNestedSchema_RequiredChildFieldPresent_ReturnsNoError() {
        ConfigSchema nestedSchema = new ConfigSchema()
                .addField(new TextConfigItem("child_required", true).displayName("子字段"))
                .addField(new TextConfigItem("child_optional", false).displayName("可选字段"));

        SchemaConfigItem item = new SchemaConfigItem("nested", true, nestedSchema);

        Map<String, Object> value = new HashMap<>();
        value.put("child_required", "value");
        // child_optional is optional, not provided
        String error = item.validate(value);
        assertNull("All required child fields present, should pass", error);
    }

    @Test
    public void testNestedSchema_OptionalChildFieldMissing_ReturnsNoError() {
        ConfigSchema nestedSchema = new ConfigSchema()
                .addField(new TextConfigItem("child_optional", false).displayName("可选字段"));

        SchemaConfigItem item = new SchemaConfigItem("nested", true, nestedSchema);

        Map<String, Object> value = new HashMap<>();
        // child_optional is optional, not provided
        String error = item.validate(value);
        assertNull("Only optional child field missing, should pass", error);
    }

    @Test
    public void testNestedSchema_InvalidChildValue_ReturnsError() {
        ConfigSchema nestedSchema = new ConfigSchema()
                .addField(new NumericConfigItem("child_num", true).range(0, 100));

        SchemaConfigItem item = new SchemaConfigItem("nested", true, nestedSchema);

        Map<String, Object> value = new HashMap<>();
        value.put("child_num", "not_a_number");
        String error = item.validate(value);
        assertNotNull("Invalid child value should return error", error);
    }

    // ========== 多层嵌套 ==========

    @Test
    public void testDeepNested_RequiredMissing_ReturnsError() {
        ConfigSchema innerSchema = new ConfigSchema()
                .addField(new TextConfigItem("deep_field", true).displayName("深层字段"));

        ConfigSchema middleSchema = new ConfigSchema()
                .addField(new SchemaConfigItem("inner", true, innerSchema).displayName("内层"));

        SchemaConfigItem outerItem = new SchemaConfigItem("outer", true, middleSchema);
        outerItem.displayName("外层");

        Map<String, Object> outerValue = new HashMap<>();
        Map<String, Object> middleValue = new HashMap<>();
        // inner is missing
        outerValue.put("middle", middleValue);

        String error = outerItem.validate(outerValue);
        assertNotNull("Missing required nested field should return error", error);
        assertTrue("Error should contain inner field info", error.contains("inner") || error.contains("内层"));
    }

    // ========== getFieldType ==========

    @Test
    public void getFieldType_ReturnsSchema() {
        SchemaConfigItem item = new SchemaConfigItem("test", false, new ConfigSchema());
        assertEquals("schema", item.getFieldType());
    }

    @Test
    public void getFieldType_WithProvider() {
        SchemaConfigItem item = new SchemaConfigItem("test", false, StubSchemaProvider.class);
        assertEquals("schema", item.getFieldType());
    }

    // ========== extend 模式 ==========

    @Test
    public void extend_DefaultIsFalse() {
        SchemaConfigItem item = new SchemaConfigItem("test", false, new ConfigSchema());
        assertFalse("Default extend should be false", item.isExtend());
    }

    @Test
    public void extend_SetToTrue() {
        SchemaConfigItem item = new SchemaConfigItem("test", false, new ConfigSchema());
        item.extend();
        assertTrue("extend() should set to true", item.isExtend());
    }

    // ========== resolveSchema ==========

    @Test
    public void resolveSchema_DirectSchema_ReturnsSchema() {
        ConfigSchema schema = new ConfigSchema();
        SchemaConfigItem item = new SchemaConfigItem("test", false, schema);
        assertSame("Should return the direct schema", schema, item.resolveSchema());
    }

    @Test
    public void resolveSchema_ProviderClass_ReturnsNewSchema() {
        SchemaConfigItem item = new SchemaConfigItem("test", false, StubSchemaProvider.class);
        ConfigSchema resolved = item.resolveSchema();
        assertNotNull("Provider should create schema", resolved);
    }

    // ========== addDefaultValue ==========

    @Test
    public void addDefaultValue_KeyMissing_WithDefault_AddsValue() {
        Map<String, Object> defaultValue = new HashMap<>();
        defaultValue.put("k", "v");

        SchemaConfigItem item = new SchemaConfigItem("nested", false, new ConfigSchema());
        item.setDefaultValue(defaultValue);

        Map<String, Object> config = new HashMap<>();
        item.addDefaultValue(config);

        assertTrue("Should add default value when key missing", config.containsKey("nested"));
    }

    @Test
    public void addDefaultValue_KeyPresent_DoesNotOverride() {
        Map<String, Object> existingValue = new HashMap<>();
        existingValue.put("k", "v");

        Map<String, Object> defaultValue = new HashMap<>();
        defaultValue.put("k", "default");

        SchemaConfigItem item = new SchemaConfigItem("nested", false, new ConfigSchema());
        item.setDefaultValue(defaultValue);

        Map<String, Object> config = new HashMap<>();
        config.put("nested", existingValue);
        item.addDefaultValue(config);

        assertSame("Should not override existing value", existingValue, config.get("nested"));
    }

    @Test
    public void addDefaultValue_NoDefault_DoesNothing() {
        SchemaConfigItem item = new SchemaConfigItem("nested", false, new ConfigSchema());
        Map<String, Object> config = new HashMap<>();
        item.addDefaultValue(config);
        assertFalse("Should not add when no default", config.containsKey("nested"));
    }

    @Test
    public void addDefaultValue_NestedDefaultFields() {
        ConfigSchema nestedSchema = new ConfigSchema()
                .addField(new TextConfigItem("port", true, "COM1").displayName("端口"))
                .addField(new NumericConfigItem("baudRate", true, 9600).displayName("波特率"));

        SchemaConfigItem item = new SchemaConfigItem("comm_settings", false, nestedSchema);

        Map<String, Object> config = new HashMap<>();
        config.put("comm_settings", new HashMap<>());
        item.addDefaultValue(config);

        @SuppressWarnings("unchecked")
        Map<String, Object> commSettings = (Map<String, Object>) config.get("comm_settings");
        assertEquals("Should add default port", "COM1", commSettings.get("port"));
        assertEquals("Should add default baudRate", 9600.0, commSettings.get("baudRate"));
    }

    // ========== Stub ==========

    /** 测试用 Schema Provider */
    public static class StubSchemaProvider implements com.ecat.core.ConfigFlow.ConfigSchemaProvider {
        @Override
        public ConfigSchema createSchema() {
            return new ConfigSchema()
                    .addField(new TextConfigItem("stub_field", true).displayName("桩字段"));
        }
    }
}
