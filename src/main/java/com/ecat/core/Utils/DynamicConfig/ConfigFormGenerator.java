package com.ecat.core.Utils.DynamicConfig;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * ConfigFormGenerator 类用于生成动态配置表单的 HTML 代码。
 * 它根据 ConfigDefinition 中定义的配置项生成相应的输入字段，并支持嵌套结构和列表。
 * 
 * @author coffee
 */
public class ConfigFormGenerator {

    public String generateForm(ConfigDefinition configDefinition) {
        StringBuilder form = new StringBuilder();
        form.append("<form method=\"post\">");

        for (Map.Entry<String, ConfigItem<?>> entry : configDefinition.getConfigItems().entrySet()) {
            ConfigItem<?> item = entry.getValue();
            form.append("<div>");
            form.append("<label for=\"").append(item.getKey()).append("\">").append(item.getKey()).append(":</label><br>");
            generateFormField(form, "", item);
            form.append("</div>");
        }
        form.append("<input type=\"submit\" value=\"Submit\">");
        form.append("</form>");
        return form.toString();
    }

    private void generateFormField(StringBuilder form, String parentKey, ConfigItem<?> item) {
        String fullKey = parentKey.isEmpty() ? item.getKey() : parentKey + "." + item.getKey();
        if (item.getType() == String.class) {
            form.append("<input type=\"text\" id=\"").append(fullKey).append("\" name=\"").append(fullKey).append("\"");
            if (item.getDefaultValue() != null) {
                form.append(" value=\"").append(item.getDefaultValue()).append("\"");
            }
            if (item.isRequired()) {
                form.append(" required");
            }
            form.append("><br>");
        } else if (item.getType() == Integer.class) {
            form.append("<input type=\"number\" id=\"").append(fullKey).append("\" name=\"").append(fullKey).append("\"");
            if (item.getDefaultValue() != null) {
                form.append(" value=\"").append(item.getDefaultValue()).append("\"");
            }
            if (item.isRequired()) {
                form.append(" required");
            }
            form.append("><br>");
        } else if (item.getType() == Map.class) {
            form.append("<div>");
            for (ConfigItem<?> nestedItem : item.getNestedConfigItems()) {
                generateFormField(form, fullKey, nestedItem);
            }
            form.append("</div>");
        } else if (item.getType() == List.class) {
             // 新增：处理嵌套列表（列表中的每个元素是结构化对象）
            if (item.isNestedList()) {
                form.append("<div class=\"nested-list\" data-list-key=\"").append(fullKey).append("\">");
                form.append("<h4>").append(item.getKey()).append("（列表）</h4>");
                
                // 生成列表元素的模板（用于前端动态添加）
                ConfigItemBuilder listElementConfig = item.getNestedListConfig();
                form.append("<div class=\"list-element-template\" style=\"display:none;\">");
                for (ConfigItem<?> elementField : listElementConfig.build()) {
                    generateFormField(form, fullKey + "[*]", elementField); // 使用 [*] 作为动态索引占位符
                }
                form.append("</div>");
    
                // 初始添加一个空元素（可通过前端JS复制模板动态添加）
                form.append("<div class=\"list-element\">");
                for (ConfigItem<?> elementField : listElementConfig.build()) {
                    generateFormField(form, fullKey + "[0]", elementField); // 初始索引为0
                }
                form.append("</div>");
    
                form.append("<button type=\"button\" class=\"add-element-btn\">添加新元素</button>");
                form.append("</div>");
            }
            else{
                boolean isSingleSelect = false;
                Set<String> validValues = null;
                for (ConstraintValidator<?> validator : item.getValidators()) {
                    if (validator instanceof ListSizeValidator) {
                        ListSizeValidator<?> listSizeValidator = (ListSizeValidator<?>) validator;
                        isSingleSelect = listSizeValidator.isSingleSelect();
                    }
                    if (validator instanceof ListValidator) {
                        ListValidator<?> listValidator = (ListValidator<?>) validator;
                        ConstraintValidator<?> elementValidator = listValidator.getElementValidator();
                        if (elementValidator instanceof StringEnumValidator) {
                            validValues = ((StringEnumValidator) elementValidator).getValidValues();
                        }
                    }
                }
                if (validValues == null) {
                    throw new IllegalArgumentException("列表配置项 " + fullKey + " 缺少 StringEnumValidator，需要提供选项值");
                }
                form.append("<select id=\"").append(fullKey).append("\" name=\"").append(fullKey);
                if (!isSingleSelect) {
                    form.append("\" multiple");
                }
                form.append("\">");
                for (String value : validValues) {
                    form.append("<option value=\"").append(value).append("\">").append(value).append("</option>");
                }
                form.append("</select><br>");
            }
        }
        
    }

    public Map<String, Object> parseFormData(ConfigDefinition configDefinition, Map<String, String[]> formData) {
        Map<String, Object> config = new HashMap<>();
        for (Map.Entry<String, ConfigItem<?>> entry : configDefinition.getConfigItems().entrySet()) {
            ConfigItem<?> item = entry.getValue();
            parseFormField(config, "", item, formData);
        }
        return config;
    }

    private void parseFormField(Map<String, Object> config, String parentKey, ConfigItem<?> item, Map<String, String[]> formData) {
        String fullKey = parentKey.isEmpty() ? item.getKey() : parentKey + "." + item.getKey();
    
        if (item.getType() == Map.class && item.hasNestedConfigItems()) {
            // 处理嵌套Map（显式遍历所有子配置项）
            Map<String, Object> nestedConfig = new HashMap<>();
            for (ConfigItem<?> nestedItem : item.getNestedConfigItems()) {
                parseFormField(nestedConfig, fullKey, nestedItem, formData); // 递归解析子字段
            }
            config.put(item.getKey(), nestedConfig); // 将解析后的Map放入父级
        } 
        else if (item.isNestedList()) {
            // 处理嵌套列表（保持原有逻辑，但确保列表元素的配置构建器正确传递）
            List<Map<String, Object>> list = new ArrayList<>();
            ConfigItemBuilder listElementConfig = item.getNestedListConfig();
            Pattern indexPattern = Pattern.compile("\\[(\\d+)\\]"); 
            
            for (String key : formData.keySet()) {
                if (key.startsWith(fullKey)) {
                    Matcher matcher = indexPattern.matcher(key);
                    List<Integer> indices = new ArrayList<>();
                    while (matcher.find()) {
                        indices.add(Integer.parseInt(matcher.group(1)));
                    }
                    if (indices.isEmpty()) continue;
                    
                    int listIndex = indices.get(0);
                    String elementBasePath = fullKey + "[" + listIndex + "]";
                    
                    while (list.size() <= listIndex) {
                        list.add(new HashMap<>());
                    }
                    Map<String, Object> currentElement = list.get(listIndex);
                    
                    String subFieldPath = key.substring(elementBasePath.length() + 1);
                    parseSubFieldInlined(currentElement, elementBasePath, subFieldPath, formData, listElementConfig); // 传递列表元素的配置构建器
                }
            }
            config.put(item.getKey(), list);
        } 
        else if (formData.containsKey(fullKey)) {
            // 处理普通字段（保持原有逻辑）
            String[] values = formData.get(fullKey);
            if (item.getType() == String.class) {
                config.put(item.getKey(), values[0]);
            } else if (item.getType() == Integer.class) {
                config.put(item.getKey(), Integer.parseInt(values[0]));
            } else if (item.getType() == List.class && !item.isNestedList()) {
                config.put(item.getKey(), Arrays.asList(values));
            }
        }
    }
    
    private void parseSubFieldInlined(Map<String, Object> currentElement, String elementBasePath, 
                                     String subFieldPath, Map<String, String[]> formData,
                                     ConfigItemBuilder currentConfigBuilder) {
        if (subFieldPath.isEmpty()) return;
    
        String[] parts = subFieldPath.split("\\.");
        String firstPart = parts[0];
        String remainingPath = parts.length > 1 ? subFieldPath.substring(firstPart.length() + 1) : "";
    
        // 处理 Map 字段的子字段（关键改进：显式匹配 currentConfigBuilder 中的子配置项）
        if (currentConfigBuilder != null) {
            for (ConfigItem<?> field : currentConfigBuilder.build()) {
                if (field.getKey().equals(firstPart)) {
                    if (field.getType() == Map.class && field.hasNestedConfigItems()) {
                        // 递归解析 Map 字段的子字段
                        Map<String, Object> nestedMap = new HashMap<>();
                        currentElement.put(firstPart, nestedMap);
                        parseSubFieldInlined(nestedMap, elementBasePath + "." + firstPart, remainingPath, formData, 
                                            new ConfigItemBuilder().add(field)); // 传递当前 Map 字段的配置构建器
                    } else if (formData.containsKey(elementBasePath + "." + subFieldPath)) {
                        // 直接设置普通字段的值
                        String[] values = formData.get(elementBasePath + "." + subFieldPath);
                        currentElement.put(firstPart, values[0]); // 假设是 String 类型，可根据实际类型扩展
                    }
                    return; // 找到匹配的字段后退出循环
                }
            }
        }
    
        // 处理列表字段（如 gases[3]）
        Pattern indexPattern = Pattern.compile("(\\w+)\\[(\\d+)\\]");
        Matcher matcher = indexPattern.matcher(firstPart);
        if (matcher.matches()) {
            String nestedListKey = matcher.group(1);
            int nestedIndex = Integer.parseInt(matcher.group(2));
            
            List<Map<String, Object>> nestedList = (List<Map<String, Object>>) currentElement.getOrDefault(nestedListKey, new ArrayList<>());
            while (nestedList.size() <= nestedIndex) {
                nestedList.add(new HashMap<>());
            }
            Map<String, Object> nestedElement = nestedList.get(nestedIndex);
            currentElement.put(nestedListKey, nestedList);
    
            // 查找列表字段的配置构建器
            ConfigItemBuilder nestedListConfig = null;
            if (currentConfigBuilder != null) {
                for (ConfigItem<?> field : currentConfigBuilder.build()) {
                    if (field.getKey().equals(nestedListKey) && field.isNestedList()) {
                        nestedListConfig = field.getNestedListConfig();
                        break;
                    }
                }
            }
            parseSubFieldInlined(nestedElement, elementBasePath + "." + firstPart, remainingPath, formData, nestedListConfig);
        }
    }

    
}    
