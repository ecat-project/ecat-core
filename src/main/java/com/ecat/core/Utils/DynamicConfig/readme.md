供AI生成规则的脚本，提供以下代码和yml作为prompt让ai生成

# yml
```
id: mockserial-001 # 设备ID，Core管理，必填，不可重复
name: 测试串口设备1 # 设备名称，Core管理，必填，不可重复
class: air.monitor.pm # 所属ecat设备类型，Core管理，选填，见DeviceClasses的className
sn: XH2000E-001 # 设备序列号，Core管理，选填
vendor: 先河环保 # 设备厂商，Core管理，选填
model: XH2000E # 设备型号，Core管理，选填
abilities: # 设备能力，Core管理，选填
    - gas.switch
    - gas.zero.generate
    - gas.span.generate
integration: MockTimerTickIntegration # integration自定义
comm_settings: # integration自定义
    protocal: serial
    port: /dev/pts/2
    baudrate: 115200
    numdatabit: 8
    numstopbit: 1
    parity: N

```

# DeviceConfigFormGenerator.java
```
import java.util.*;

// 约束验证接口
interface ConstraintValidator<T> {
    boolean validate(T value);
    String getErrorMessage();
}

// 字符串长度约束验证器
class StringLengthValidator implements ConstraintValidator<String> {
    private final int minLength;
    private final int maxLength;

    public StringLengthValidator(int minLength, int maxLength) {
        this.minLength = minLength;
        this.maxLength = maxLength;
    }

    @Override
    public boolean validate(String value) {
        return value.length() >= minLength && value.length() <= maxLength;
    }

    @Override
    public String getErrorMessage() {
        return "字符串长度必须在 " + minLength + " 到 " + maxLength + " 之间";
    }
}

// 字符串枚举值验证器
class StringEnumValidator implements ConstraintValidator<String> {
    private final Set<String> validValues;

    public StringEnumValidator(Set<String> validValues) {
        this.validValues = validValues;
    }

    @Override
    public boolean validate(String value) {
        return validValues.contains(value);
    }

    @Override
    public String getErrorMessage() {
        return "字符串值必须是 " + validValues + " 中的一个";
    }

    public Set<String> getValidValues() {
        return validValues;
    }
}

// 列表验证器
class ListValidator<T> implements ConstraintValidator<List<T>> {
    private final ConstraintValidator<T> elementValidator;

    public ListValidator(ConstraintValidator<T> elementValidator) {
        this.elementValidator = elementValidator;
    }

    @Override
    public boolean validate(List<T> value) {
        if (value == null) {
            return true;
        }
        for (T element : value) {
            if (!elementValidator.validate(element)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public String getErrorMessage() {
        return "列表中的元素不满足验证条件: " + elementValidator.getErrorMessage();
    }

    public ConstraintValidator<T> getElementValidator() {
        return elementValidator;
    }
}

// 列表大小验证器
class ListSizeValidator<T> implements ConstraintValidator<List<T>> {
    private final int minSize;
    private final int maxSize;

    public ListSizeValidator(int minSize, int maxSize) {
        this.minSize = minSize;
        this.maxSize = maxSize;
    }

    @Override
    public boolean validate(List<T> value) {
        if (value == null) {
            return true;
        }
        int size = value.size();
        return size >= minSize && size <= maxSize;
    }

    @Override
    public String getErrorMessage() {
        return "列表大小必须在 " + minSize + " 到 " + maxSize + " 之间";
    }

    public boolean isSingleSelect() {
        return minSize == 1 && maxSize == 1;
    }
}

// 配置项类
class ConfigItem<T> {
    private final String key;
    private final Class<T> type;
    private final boolean required;
    private final T defaultValue;
    private final List<ConstraintValidator<T>> validators;
    private final Map<String, ConfigItem<?>> nestedConfigItems;

    public ConfigItem(String key, Class<T> type, boolean required, T defaultValue, List<ConstraintValidator<T>> validators) {
        this.key = key;
        this.type = type;
        this.required = required;
        this.defaultValue = defaultValue;
        this.validators = validators != null ? validators : new ArrayList<>();
        this.nestedConfigItems = new HashMap<>();
    }

    public ConfigItem(String key, Class<T> type, boolean required, T defaultValue) {
        this(key, type, required, defaultValue, null);
    }

    public String getKey() {
        return key;
    }

    public Class<T> getType() {
        return type;
    }

    public boolean isRequired() {
        return required;
    }

    public T getDefaultValue() {
        return defaultValue;
    }

    public ConfigItem<T> addNestedConfigItems(ConfigItemBuilder builder) {
        for (ConfigItem<?> item : builder.build()) {
            nestedConfigItems.put(item.getKey(), item);
        }
        return this;
    }

    // 检查是否有嵌套配置项
    public boolean hasNestedConfigItems() {
        return !nestedConfigItems.isEmpty();
    }

    // 获取嵌套配置项集合
    public Collection<ConfigItem<?>> getNestedConfigItems() {
        return nestedConfigItems.values();
    }

    // 验证配置项的值
    public String validate(Object value) {
        if (value == null) {
            if (required) {
                return "配置项 " + key + " 是必需的，但值为空";
            }
            return null;
        }
        if (!type.isInstance(value)) {
            return "配置项 " + key + " 的类型不正确，期望类型为 " + type.getSimpleName();
        }
        T typedValue = type.cast(value);
        for (ConstraintValidator<T> validator : validators) {
            if (!validator.validate(typedValue)) {
                return "配置项 " + key + " 不满足验证条件: " + validator.getErrorMessage();
            }
        }
        if (hasNestedConfigItems() && value instanceof Map) {
            Map<String, Object> nestedConfig = (Map<String, Object>) value;
            for (ConfigItem<?> nestedItem : getNestedConfigItems()) {
                Object nestedValue = nestedConfig.get(nestedItem.getKey());
                String nestedError = nestedItem.validate(nestedValue);
                if (nestedError != null) {
                    return "配置项 " + key + " 的嵌套配置项 " + nestedItem.getKey() + " 验证失败: " + nestedError;
                }
            }
        }
        return null;
    }

    // 为配置添加默认值
    public void addDefaultValue(Map<String, Object> config) {
        if (!config.containsKey(key) && defaultValue != null) {
            config.put(key, defaultValue);
        }
        if (hasNestedConfigItems()) {
            Object nestedValue = config.get(key);
            if (nestedValue instanceof Map) {
                Map<String, Object> nestedConfig = (Map<String, Object>) nestedValue;
                for (ConfigItem<?> nestedItem : getNestedConfigItems()) {
                    nestedItem.addDefaultValue(nestedConfig);
                }
            }
        }
    }

    public List<ConstraintValidator<T>> getValidators() {
        return validators;
    }
}

// 配置项构建器
class ConfigItemBuilder {
    private final LinkedHashMap<String, ConfigItem<?>> items = new LinkedHashMap<>();

    public ConfigItemBuilder add(ConfigItem<?> item) {
        items.put(item.getKey(), item);
        return this;
    }

    public ConfigItem<?>[] build() {
        return items.values().toArray(new ConfigItem[0]);
    }
}

// 配置定义类
class ConfigDefinition {
    private final LinkedHashMap<String, ConfigItem<?>> configItems = new LinkedHashMap<>();
    private final Map<ConfigItem<?>, String> invalidConfigItems = new HashMap<>();

    public ConfigDefinition define(ConfigItemBuilder builder) {
        for (ConfigItem<?> item : builder.build()) {
            configItems.put(item.getKey(), item);
        }
        return this;
    }

    // 验证配置并添加默认值
    public boolean validateConfig(Map<String, Object> config) {
        invalidConfigItems.clear();
        boolean isValid = true;
        for (ConfigItem<?> item : configItems.values()) {
            item.addDefaultValue(config);
            Object value = config.get(item.getKey());
            String errorMessage = item.validate(value);
            if (errorMessage != null) {
                invalidConfigItems.put(item, errorMessage);
                isValid = false;
            }
        }
        return isValid;
    }

    // 填充默认值
    public Map<String, Object> fillDefaults(Map<String, Object> config) {
        Map<String, Object> filledConfig = new HashMap<>(config);
        for (ConfigItem<?> item : configItems.values()) {
            item.addDefaultValue(filledConfig);
        }
        return filledConfig;
    }

    // 对外开放获取未通过验证的配置项及其错误信息的方法
    public Map<ConfigItem<?>, String> getInvalidConfigItems() {
        return invalidConfigItems;
    }

    public LinkedHashMap<String, ConfigItem<?>> getConfigItems() {
        return configItems;
    }
}

// 示例使用
public class IntegrationConfigExample {
    public static void main(String[] args) {
        // 定义验证器
        StringLengthValidator lengthValidator = new StringLengthValidator(1, 50);
        Set<String> classValidValues = new HashSet<>(Arrays.asList("air.monitor.pm"));
        StringEnumValidator classEnumValidator = new StringEnumValidator(classValidValues);
        Set<String> abilitiesValidValues = new HashSet<>(Arrays.asList("gas.switch", "gas.zero.generate", "gas.span.generate"));
        StringEnumValidator abilitiesEnumValidator = new StringEnumValidator(abilitiesValidValues);

        ListValidator<String> abilitiesListValidator = new ListValidator<>(abilitiesEnumValidator);
        ListSizeValidator<String> abilitiesListSizeValidator = new ListSizeValidator<>(0, 3);
        List<ConstraintValidator<List<String>>> abilitiesListValidators = Arrays.asList(abilitiesListValidator, abilitiesListSizeValidator);

        ListValidator<String> classListValidator = new ListValidator<>(classEnumValidator);
        ListSizeValidator<String> classListSizeValidator = new ListSizeValidator<>(0, 1);
        List<ConstraintValidator<List<String>>> classListValidators = Arrays.asList(classListValidator, classListSizeValidator);

        // 定义配置项
        ConfigDefinition configDefinition = new ConfigDefinition();
        ConfigItemBuilder builder = new ConfigItemBuilder()
               .add(new ConfigItem<>("id", String.class, true, null, Collections.singletonList(lengthValidator)))
               .add(new ConfigItem<>("name", String.class, true, null, Collections.singletonList(lengthValidator)))
               .add(new ConfigItem<List<String>>("class", (Class<List<String>>)(Class<?>)List.class, false, null, classListValidators))
               .add(new ConfigItem<>("sn", String.class, false, null, Collections.singletonList(lengthValidator)))
               .add(new ConfigItem<>("vendor", String.class, false, null, Collections.singletonList(lengthValidator)))
               .add(new ConfigItem<>("model", String.class, false, null, Collections.singletonList(lengthValidator)))
               .add(new ConfigItem<List<String>>("abilities", (Class<List<String>>)(Class<?>)List.class, false, null, abilitiesListValidators))
               .add(new ConfigItem<>("integration", String.class, true, null, Collections.singletonList(lengthValidator)))
               .add(new ConfigItem<>("comm_settings", Map.class, true, null)
                       .addNestedConfigItems(new ConfigItemBuilder()
                              .add(new ConfigItem<>("protocal", String.class, true, null, Collections.singletonList(lengthValidator)))
                              .add(new ConfigItem<>("port", String.class, true, null, Collections.singletonList(lengthValidator)))
                              .add(new ConfigItem<>("baudrate", String.class, true, null, Collections.singletonList(lengthValidator)))
                              .add(new ConfigItem<>("numdatabit", String.class, true, null, Collections.singletonList(lengthValidator)))
                              .add(new ConfigItem<>("numstopbit", String.class, true, null, Collections.singletonList(lengthValidator)))
                              .add(new ConfigItem<>("parity", String.class, true, null, Collections.singletonList(lengthValidator)))
                              .build()));

        configDefinition.define(builder);

        // 模拟配置
        Map<String, Object> config = new HashMap<>();
        config.put("id", "mockserial-001");
        config.put("name", "测试串口设备1");
        config.put("class", Collections.singletonList("air.monitor.pm"));
        config.put("sn", "XH2000E-001");
        config.put("vendor", "先河环保");
        config.put("model", "XH2000E");
        config.put("abilities", Arrays.asList("gas.switch", "gas.zero.generate"));
        config.put("integration", "MockTimerTickIntegration");

        Map<String, Object> commSettings = new HashMap<>();
        commSettings.put("protocal", "serial");
        commSettings.put("port", "/dev/pts/2");
        commSettings.put("baudrate", "115200");
        commSettings.put("numdatabit", "8");
        commSettings.put("numstopbit", "1");
        commSettings.put("parity", "N");
        config.put("comm_settings", commSettings);

        // 验证配置
        boolean isValid = configDefinition.validateConfig(config);
        if (isValid) {
            System.out.println("配置验证通过");
            System.out.println("添加默认值后的配置: " + config);
        } else {
            System.out.println("配置验证失败，未通过的配置项及错误信息如下：");
            Map<ConfigItem<?>, String> invalidItems = configDefinition.getInvalidConfigItems();
            for (Map.Entry<ConfigItem<?>, String> entry : invalidItems.entrySet()) {
                System.out.println("配置项: " + entry.getKey().getKey() + ", 错误信息: " + entry.getValue());
            }
        }

        // 生成 HTML 表单
        ConfigFormGenerator formGenerator = new ConfigFormGenerator();
        String htmlForm = formGenerator.generateForm(configDefinition);
        System.out.println(htmlForm);

        // 模拟表单提交数据
        Map<String, String[]> formData = new HashMap<>();
        formData.put("id", new String[]{"mockserial-001"});
        formData.put("name", new String[]{"测试串口设备1"});
        formData.put("class", new String[]{"air.monitor.pm"});
        formData.put("sn", new String[]{"XH2000E-001"});
        formData.put("vendor", new String[]{"先河环保"});
        formData.put("model", new String[]{"XH2000E"});
        formData.put("abilities", new String[]{"gas.switch", "gas.zero.generate"});
        formData.put("integration", new String[]{"MockTimerTickIntegration"});
        formData.put("comm_settings.protocal", new String[]{"serial"});
        formData.put("comm_settings.port", new String[]{"port"});
        formData.put("comm_settings.baudrate", new String[]{"115200"});
        formData.put("comm_settings.numdatabit", new String[]{"8"});
        formData.put("comm_settings.numstopbit", new String[]{"1"});
        formData.put("comm_settings.parity", new String[]{"N"});

        // 解析表单数据
        Map<String, Object> parsedConfig = formGenerator.parseFormData(configDefinition, formData);
        System.out.println("解析后的配置: " + parsedConfig);
    }
}    

```


# ConfigFormGenerator.java

```
import java.util.*;

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
        if (formData.containsKey(fullKey)) {
            String[] values = formData.get(fullKey);
            if (item.getType() == String.class) {
                config.put(item.getKey(), values[0]);
            } else if (item.getType() == Integer.class) {
                config.put(item.getKey(), Integer.parseInt(values[0]));
            } else if (item.getType() == Map.class) {
                Map<String, Object> nestedConfig = new HashMap<>();
                for (ConfigItem<?> nestedItem : item.getNestedConfigItems()) {
                    parseFormField(nestedConfig, fullKey, nestedItem, formData);
                }
                config.put(item.getKey(), nestedConfig);
            } else if (item.getType() == List.class) {
                config.put(item.getKey(), Arrays.asList(values));
            }
        }
    }
}    

```