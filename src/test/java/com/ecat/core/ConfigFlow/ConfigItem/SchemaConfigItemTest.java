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
        String error = (String) item.validate(null);
        assertNotNull("required=true, null should return error", error);
        assertTrue("Error should mention field key", error.contains("nested"));
    }

    @Test
    public void testRequired_NullValue_WithDisplayName() {
        SchemaConfigItem item = new SchemaConfigItem("nested", true, new ConfigSchema());
        item.displayName("嵌套配置");
        String error = (String) item.validate(null);
        assertNotNull("required=true, null should return error", error);
        assertTrue("Error should mention display name", error.contains("嵌套配置"));
    }

    @Test
    public void testOptional_NullValue_ReturnsNoError() {
        SchemaConfigItem item = new SchemaConfigItem("nested", false, new ConfigSchema());
        assertNull("required=false, null should pass", item.validate(null));
    }

    @Test
    public void testRequired_PresentValue_ReturnsNoError() {
        SchemaConfigItem item = new SchemaConfigItem("nested", true, new ConfigSchema());
        Map<String, Object> value = new HashMap<>();
        assertNull("required=true, non-null Map should pass", item.validate(value));
    }

    // ========== 类型检查 ==========

    @Test
    public void testValidateType_NonMapValue_ReturnsError() {
        SchemaConfigItem item = new SchemaConfigItem("nested", true, new ConfigSchema());
        item.displayName("嵌套配置");
        String error = (String) item.validate("not a map");
        assertNotNull("Non-Map value should return type error", error);
        assertTrue("Error should mention display name", error.contains("嵌套配置"));
    }

    @Test
    public void testValidateType_NonMapValue_NoDisplayName() {
        SchemaConfigItem item = new SchemaConfigItem("nested", true, new ConfigSchema());
        String error = (String) item.validate("not a map");
        assertNotNull("Non-Map value should return type error", error);
        assertTrue("Error should mention field key", error.contains("nested"));
    }

    // ========== Provider 模式 ==========

    @Test
    public void testProvider_Required_NullValue_ReturnsError() {
        SchemaConfigItem item = new SchemaConfigItem("nested", true, StubSchemaProvider.class);
        assertNotNull("required=true, null should return error", item.validate(null));
    }

    @Test
    public void testProvider_Optional_NullValue_ReturnsNoError() {
        SchemaConfigItem item = new SchemaConfigItem("nested", false, StubSchemaProvider.class);
        assertNull("required=false, null should pass", item.validate(null));
    }

    // ========== 嵌套 Schema 递归验证 ==========

    @Test
    @SuppressWarnings("unchecked")
    public void testNestedSchema_RequiredChildFieldMissing_ReturnsError() {
        ConfigSchema nestedSchema = new ConfigSchema()
                .addField(new TextConfigItem("child_required", true).displayName("子字段"));

        SchemaConfigItem item = new SchemaConfigItem("nested", true, nestedSchema);
        item.displayName("嵌套配置");

        Map<String, Object> value = new HashMap<>();
        // child_required is missing
        // validate() 返回嵌套 Map 结构（包含子字段错误）
        Object result = item.validate(value);
        assertNotNull("validate() should return error for missing child", result);
        assertTrue("validate() should return Map for nested errors", result instanceof Map);
        Map<String, Object> nestedErrors = (Map<String, Object>) result;
        assertTrue("Should contain error for child_required", nestedErrors.containsKey("child_required"));
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
        assertNull("All required child fields present, should pass", item.validate(value));
    }

    @Test
    public void testNestedSchema_OptionalChildFieldMissing_ReturnsNoError() {
        ConfigSchema nestedSchema = new ConfigSchema()
                .addField(new TextConfigItem("child_optional", false).displayName("可选字段"));

        SchemaConfigItem item = new SchemaConfigItem("nested", true, nestedSchema);

        Map<String, Object> value = new HashMap<>();
        // child_optional is optional, not provided
        assertNull("Only optional child field missing, should pass", item.validate(value));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testNestedSchema_InvalidChildValue_ReturnsError() {
        ConfigSchema nestedSchema = new ConfigSchema()
                .addField(new NumericConfigItem("child_num", true).range(0, 100));

        SchemaConfigItem item = new SchemaConfigItem("nested", true, nestedSchema);

        Map<String, Object> value = new HashMap<>();
        value.put("child_num", "not_a_number");
        // validate() 返回嵌套错误 Map
        Object result = item.validate(value);
        assertNotNull("validate() should return error for invalid child", result);
        assertTrue("validate() should return Map", result instanceof Map);
    }

    // ========== 多层嵌套 ==========

    @Test
    @SuppressWarnings("unchecked")
    public void testDeepNested_RequiredMissing_ReturnsError() {
        // 3 层嵌套：outer -> middle(inner) -> deep_field
        ConfigSchema innerSchema = new ConfigSchema()
                .addField(new TextConfigItem("deep_field", true).displayName("深层字段"));

        ConfigSchema middleSchema = new ConfigSchema()
                .addField(new SchemaConfigItem("inner", true, innerSchema).displayName("内层"));

        SchemaConfigItem outerItem = new SchemaConfigItem("outer", true, middleSchema);
        outerItem.displayName("外层");

        Map<String, Object> outerValue = new HashMap<>();
        Map<String, Object> middleValue = new HashMap<>();
        outerValue.put("inner", middleValue);

        // validate() 返回多层嵌套 Map 结构
        Object result = outerItem.validate(outerValue);
        assertNotNull("validate() should return error for deep nested missing field", result);
        assertTrue("Should return Map", result instanceof Map);
        Map<String, Object> outerErrors = (Map<String, Object>) result;
        assertTrue("Should contain error for inner", outerErrors.containsKey("inner"));
        Object innerError = outerErrors.get("inner");
        assertTrue("inner error should be nested Map", innerError instanceof Map);
        Map<String, Object> innerErrors = (Map<String, Object>) innerError;
        assertTrue("Should contain error for deep_field", innerErrors.containsKey("deep_field"));
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

    // ========== ConfigSchema.validate() ==========

    @Test
    @SuppressWarnings("unchecked")
    public void testConfigSchema_validate_ReturnsNestedMap() {
        ConfigSchema innerSchema = new ConfigSchema()
                .addField(new TextConfigItem("deep_field", true).displayName("深层字段"));

        ConfigSchema schema = new ConfigSchema()
                .addField(new TextConfigItem("name", true).displayName("名称"))
                .addField(new SchemaConfigItem("comm_settings", true, innerSchema).displayName("通讯设置"));

        Map<String, Object> input = new HashMap<>();
        input.put("comm_settings", new HashMap<>()); // name missing, deep_field missing

        Map<String, Object> errors = schema.validate(input);

        // name 应该有错误（叶子 String）
        assertTrue("Should contain error for 'name'", errors.containsKey("name"));
        assertTrue("name error should be String", errors.get("name") instanceof String);

        // comm_settings 应该有嵌套错误（Map）
        assertTrue("Should contain error for 'comm_settings'", errors.containsKey("comm_settings"));
        assertTrue("comm_settings error should be Map", errors.get("comm_settings") instanceof Map);
        Map<String, Object> commErrors = (Map<String, Object>) errors.get("comm_settings");
        assertTrue("Should contain error for deep_field", commErrors.containsKey("deep_field"));
    }

    @Test
    public void testConfigSchema_validate_NoErrors_ReturnsEmptyMap() {
        ConfigSchema schema = new ConfigSchema()
                .addField(new TextConfigItem("name", true).displayName("名称"));

        Map<String, Object> input = new HashMap<>();
        input.put("name", "test");

        Map<String, Object> errors = schema.validate(input);
        assertTrue("Should return empty map when no errors", errors.isEmpty());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testConfigSchema_validate_ContainsNestedErrors() {
        ConfigSchema nestedSchema = new ConfigSchema()
                .addField(new TextConfigItem("child", true).displayName("子字段"));

        ConfigSchema schema = new ConfigSchema()
                .addField(new TextConfigItem("name", true).displayName("名称"))
                .addField(new SchemaConfigItem("nested", true, nestedSchema).displayName("嵌套"));

        Map<String, Object> input = new HashMap<>();
        input.put("nested", new HashMap<>()); // name missing, child missing

        Map<String, Object> errors = schema.validate(input);

        // name 应该有 String 错误
        assertTrue("Should contain error for 'name'", errors.containsKey("name"));
        assertTrue("name error should be String", errors.get("name") instanceof String);

        // nested 应该有嵌套 Map 错误
        assertTrue("Should contain error for 'nested'", errors.containsKey("nested"));
        assertTrue("nested error should be Map", errors.get("nested") instanceof Map);
        Map<String, Object> nestedErrors = (Map<String, Object>) errors.get("nested");
        assertTrue("Should contain error for child", nestedErrors.containsKey("child"));
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
