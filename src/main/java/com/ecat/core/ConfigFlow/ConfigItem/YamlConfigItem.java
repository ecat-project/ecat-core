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

import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;

import java.util.Map;

/**
 * YAML 只读显示配置项
 * <p>
 * 将 Map 数据转换为 YAML 格式的只读文本，用于在确认步骤中展示配置详情。
 * <p>
 * 示例：
 * <pre>{@code
 * YamlConfigItem summary = new YamlConfigItem("config_summary")
 *     .displayName("配置详情")
 *     .setValue(entryData);
 * }</pre>
 *
 * @author coffee
 */
public class YamlConfigItem extends AbstractConfigItem<String> {

    /**
     * 构造函数
     *
     * @param key 配置项键
     */
    public YamlConfigItem(String key) {
        super(key, false);
        this.readOnly = true;
    }

    /**
     * 设置要显示的 Map 数据，自动转换为 YAML 字符串作为默认值
     *
     * @param data 数据 Map
     * @return this
     */
    public YamlConfigItem setValue(Map<String, Object> data) {
        this.defaultValue = toYaml(data);
        return this;
    }

    /**
     * 直接设置已序列化好的 YAML 文本作为默认值（不经 Map→dump 转换）。
     * <p>用于展示"导出 YML"等场景：内容本就是 YAML 列表（如 DescriptorYaml 顶层数组），
     * 调用方已用业务约定的 Dumper 序列化好，直接透传即可，避免再用单键 Map 包一层破坏顶层结构。
     *
     * @param rawYaml 已序列化的 YAML 文本（null/空串视为空展示）
     * @return this
     */
    public YamlConfigItem setValue(String rawYaml) {
        this.defaultValue = rawYaml == null ? "" : rawYaml;
        return this;
    }

    /**
     * 将 Map 转换为 YAML 字符串
     */
    private static String toYaml(Map<String, Object> data) {
        if (data == null || data.isEmpty()) {
            return "";
        }
        DumperOptions options = new DumperOptions();
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.BLOCK);
        options.setPrettyFlow(true);
        Yaml yaml = new Yaml(options);
        return yaml.dump(data);
    }

    @Override
    public YamlConfigItem displayName(String displayName) {
        this.displayName = displayName;
        return this;
    }

    @Override
    public YamlConfigItem description(String description) {
        this.description = description;
        return this;
    }

    @Override
    protected String validateType(Object value) {
        // 只读字段，不需要验证
        return null;
    }

    @Override
    public String getDefaultValue() {
        return defaultValue;
    }

    @Override
    public String getFieldType() {
        return "yaml";
    }
}
