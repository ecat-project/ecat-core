# I18nTextAttribute 设计方案

## 需求概述

基于现有的 `TextAttribute` 类，创建一个新的 `I18nTextAttribute` 类，该类增加了对有限值集合的国际化支持。功能类似于 `StringSelectAttribute`，但主要用于文本属性的场景。

## 核心设计思路

`I18nTextAttribute` 将继承自 `TextAttribute`，并集成 `SelectAttribute` 的选项管理和国际化特性，实现：

1. **有限值集合管理**：维护一个有效的选项列表（options）
2. **国际化支持**：选项值支持国际化显示
3. **值验证**：确保设置的值必须在有效选项列表中
4. **灵活的构造函数**：支持多种初始化方式

## 类结构设计

### 继承关系

```
AttributeBase<String> (抽象基类)
    ↓
TextAttribute (文本属性基类)
    ↓
I18nTextAttribute (国际化文本属性)
```

### 核心字段

```java
@Getter
protected List<String> options;              // 选项列表（构造时确定，不可修改）
@Getter
protected boolean caseSensitive = false;      // 是否区分大小写
protected Map<String, String> optionCache;    // 选项缓存
protected ConfigDefinition valueDef;          // 验证定义
```

### 构造函数设计

参考 `StringSelectAttribute`，提供多种构造函数：

1. **基础构造函数**：
   ```java
   public I18nTextAttribute(String attributeID, AttributeClass attrClass, boolean valueChangeable, List<String> options)
   ```

2. **带显示名称的构造函数**：
   ```java
   public I18nTextAttribute(String attributeID, String displayName, AttributeClass attrClass, boolean valueChangeable, List<String> options)
   ```

3. **完整构造函数（支持单位和回调）**：
   ```java
   public I18nTextAttribute(String attributeID, AttributeClass attrClass, UnitInfo nativeUnit, UnitInfo displayUnit, boolean valueChangeable, List<String> options, Function<AttrChangedCallbackParams<String>, CompletableFuture<Boolean>> onChangedCallback)
   ```

4. **带大小写敏感参数的构造函数**：
   ```java
   public I18nTextAttribute(String attributeID, AttributeClass attrClass, UnitInfo nativeUnit, UnitInfo displayUnit, boolean valueChangeable, List<String> options, Function<AttrChangedCallbackParams<String>, CompletableFuture<Boolean>> onChangedCallback, boolean caseSensitive)
   ```

## 核心方法实现

### 1. 值显示方法

```java
@Override
public String getDisplayValue(UnitInfo toUnit) {
    if (value == null) {
        return null;
    }
    return getOptionI18nName(value);
}

@Override
public String getI18nValue(UnitInfo toUnit) {
    return value != null ? value.toLowerCase(Locale.ENGLISH) : "";
}
```

### 2. 选项国际化支持

```java
/**
 * 获取选项的国际化显示名称
 * @param option 选项值
 * @return 国际化显示名称
 */
public String getOptionI18nName(String option) {
    return i18n.t(getI18nOptionPathPrefix().getFullPath() + "." + option.toLowerCase(Locale.ENGLISH));
}

/**
 * 获取选项字典（k-v 结构）
 * k 为选项值，v 为国际化显示名称
 * @return 选项字典
 */
public Map<String, String> getOptionDict() {
    if (optionCache.isEmpty() && options != null) {
        synchronized (optionCache) {
            if (optionCache.isEmpty()) {
                for (String option : options) {
                    String i18nName = getOptionI18nName(option);
                    optionCache.put(option, i18nName);
                }
            }
        }
    }
    return new HashMap<>(optionCache);
}
```

### 3. 国际化路径支持

```java
@Override
public I18nKeyPath getI18nPrefixPath() {
    return new I18nKeyPath("state.i18n_text_attr.", "");
}

/**
 * 获取选项的国际化路径前缀
 * 约定使用后缀"_options"
 */
public I18nKeyPath getI18nOptionPathPrefix() {
    return getI18nDispNamePath().withSuffix("_options");
}
```

### 4. 值验证和设置

```java
@Override
public boolean updateValue(String newValue) {
    if (newValue != null && !isValidOption(newValue)) {
        log.warn("值 {} 不在有效选项列表中: {}", newValue, options);
        return false;
    }
    return super.updateValue(newValue);
}

@Override
public ConfigDefinition getValueDefinition() {
    if (valueDef == null) {
        valueDef = new ConfigDefinition();

        DynamicStringDictValidator valueValidator =
            new DynamicStringDictValidator(this::getOptionDict, caseSensitive);

        ConfigItemBuilder builder =
            new ConfigItemBuilder()
                .add(new ConfigItem<>("value", String.class, true, null, valueValidator));

        valueDef.define(builder);
    }
    return valueDef;
}
```

### 5. 选项管理方法

```java
/**
 * 查找选项（支持大小写不敏感）
 */
public String findOption(String option) {
    if (option == null) return null;

    if (caseSensitive) {
        return options.contains(option) ? option : null;
    } else {
        for (String validOption : options) {
            if (validOption.equalsIgnoreCase(option)) {
                return validOption;
            }
        }
        return null;
    }
}

/**
 * 验证选项是否有效
 */
public boolean isValidOption(String option) {
    if (option == null) {
        return false;
    }

    return findOption(option) != null;
}
```

### 6. 显示名称查找

```java
/**
 * 根据显示名称查找选项值
 */
public String getOptionByDisplayName(String displayName) {
    if (displayName == null) {
        return null;
    }

    Map<String, String> optionDict = getOptionDict();

    if (caseSensitive) {
        for (Map.Entry<String, String> entry : optionDict.entrySet()) {
            if (entry.getValue().equals(displayName)) {
                return entry.getKey();
            }
        }
    } else {
        for (Map.Entry<String, String> entry : optionDict.entrySet()) {
            if (entry.getValue().equalsIgnoreCase(displayName)) {
                return entry.getKey();
            }
        }
    }

    return null;
}
```

## 国际化资源配置

### strings.json 结构示例

```json
{
  "state": {
    "i18n_text_attr": {
      "status": "设备状态",
      "status_options": {
        "normal": "正常",
        "warning": "警告",
        "error": "错误"
      },
      "mode": "工作模式",
      "mode_options": {
        "auto": "自动",
        "manual": "手动",
        "maintenance": "维护"
      }
    }
  }
}
```

### 国际化路径规则

1. **默认-属性显示名称路径**：`state.i18n_text_attr.{attributeIDLowerCase}`
2. **默认-选项显示名称路径**：`state.i18n_text_attr.{attributeIDLowerCase}_options.{optionLowerCase}`
3. **绑定设备后-属性显示名称路径**: `devices.xxxdevice.{attributeIDLowerCase}`
3. **绑定设备后-选项显示名称路径**: `devices.xxxdevice.{attributeIDLowerCase}_options.{optionLowerCase}`

## 使用示例

### 基本使用

```java
// 创建选项列表
List<String> statusOptions = Arrays.asList("normal", "warning", "error");

// 创建 I18nTextAttribute
I18nTextAttribute statusAttr = new I18nTextAttribute(
    "status",
    AttributeClass.STATUS,
    true,
    statusOptions
);

// 设置值（会自动验证）
statusAttr.updateValue("normal");  // 有效
statusAttr.updateValue("invalid"); // 无效，会被拒绝

// 获取显示值（国际化）
String displayValue = statusAttr.getDisplayValue(null); // 返回 "正常"
```

### 高级使用

```java
// 带完整参数的构造
I18nTextAttribute modeAttr = new I18nTextAttribute(
    "mode",
    "工作模式",  // 自定义显示名称
    AttributeClass.TEXT,
    null,       // nativeUnit
    null,       // displayUnit
    true,       // valueChangeable
    Arrays.asList("auto", "manual", "maintenance"),
    (params) -> {
        // 自定义变更回调
        System.out.println("模式变更为: " + params.getNewValue());
        return CompletableFuture.completedFuture(true);
    },
    false       // 不区分大小写
);
```

## 与现有类的对比

### 与 TextAttribute 的区别

| 特性 | TextAttribute | I18nTextAttribute |
|------|-------------|-------------------|
| 值范围 | 任意文本 | 限定在 options 列表中 |
| 国际化 | 仅属性名称 | 属性名称 + 选项值 |
| 值验证 | 无默认验证 | 强制验证值是否在选项中 |
| 显示值 | 直接返回原值 | 返回国际化显示名称 |

### 与 StringSelectAttribute 的区别

| 特性 | StringSelectAttribute | I18nTextAttribute |
|------|----------------------|-------------------|
| 继承关系 | 继承自 SelectAttribute | 继承自 TextAttribute |
| 使用场景 | 模式选择、用户交互 | 设备状态文本显示 |
| 选项切换 | 提供 selectOption 方法 | 无专门的切换方法 |
| 主要用途 | 用户主动选择选项 | 设备数据自动更新 |

## 实现注意事项

1. **线程安全**：optionCache 需要使用 synchronized 保证线程安全
2. **性能优化**：使用缓存机制避免频繁的国际化查询
3. **固定选项**：选项列表在构造时确定，不支持运行时修改，确保翻译资源的稳定性
4. **错误处理**：对无效选项的处理需要记录日志
5. **向后兼容**：保持与现有 TextAttribute 的接口兼容性
6. **国际化资源**：需要确保 strings.json 中有对应的翻译资源

这个设计方案结合了 TextAttribute 的简洁性和 SelectAttribute 的国际化特性，为设备状态文本等场景提供了一个既灵活又规范的解决方案。

## 文档维护人员列表
coffee
