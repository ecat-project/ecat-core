# 设备开发规范

相关文档：[项目结构与核心文件](project-structure.md) | [I18n 国际化系统](i18n.md) | [开发工作流程](workflow.md)

## 命名规则

- **Java类名**: 使用PascalCase，如`CODevice`、`O3Device`
- **国际化key**: 使用snake_case，如`codevice`、`o3device`
- **转换规则**:
  - `CODevice` → `codevice`
  - `O3Device` → `o3device`
  - `SmartPowerStabilizer` → `smart_power_stabilizer`

---

## 属性创建标准

所有属性创建必须使用i18n友好的构造函数：

```java
// ✅ 正确：使用无displayName参数的构造函数
new NumericAttribute(
    "co",                     // attributeID
    AttributeClass.CO,        // AttributeClass
    AirVolumeUnit.PPM,        // Unit
    AirVolumeUnit.PPM,        // DisplayUnit
    2,                        // 精度
    true,                     // 可写
    false,                    // 历史记录
    molecularWeight           // 扩展参数
);

// ❌ 错误：使用null作为displayName参数
new NumericAttribute(
    "co",
    null,                     // 不应使用null displayName
    AttributeClass.CO,
    // ...
);

// 尽量避免硬编码，除非遇到通讯模块类属性是抽象的，需要用户定义名称才有意义的厂家
new NumericAttribute(
    "co",
    "CO浓度",                 // 这里建议使用用户定义的名称变量
    AttributeClass.CO,
    // ...
);
```

---

## 构造函数使用规范

根据属性类型选择合适的构造函数：

**NumericAttribute**:
```java
new NumericAttribute(attributeID, AttributeClass, Unit, DisplayUnit, precision, writable, historic, ...);
```

**BinaryAttribute**:
```java
new BinaryAttribute(attributeID, AttributeClass, initialValue);
```

**StringCommandAttribute**:
```java
new StringCommandAttribute(attributeID, AttributeClass);
```

**ModbusScalableFloatSRAttribute** (状态属性):
```java
// 状态属性可以使用null单位
new ModbusScalableFloatSRAttribute(
    "device_status",
    AttributeClass.STATUS,
    null,  // 设备状态属性确实不需要单位
    null,
    0, false, false, ...
);
```

---

## 最佳实践

### 代码规范

1. **导入规范**: 使用长类名导入，提高代码可读性
   ```java
   import java.util.Map;
   import java.util.HashMap;
   import java.lang.reflect.Method;
   ```

2. **构造函数选择**: 优先使用i18n友好的构造函数

3. **命名一致性**: 保持代码命名与国际化key的一致性

### 性能优化

1. **避免重复初始化**: 在`init()`方法中避免重复创建对象
2. **使用懒加载**: 对于不常用的属性，考虑使用懒加载
3. **资源管理**: 及时释放Modbus连接等资源

### 错误处理

1. **异常处理**: 在关键操作中添加异常处理
   ```java
   try {
       modbusResult = modbusSource.readHoldingRegisters(address, count);
   } catch (Exception e) {
       log.error("读取寄存器失败: {}", e.getMessage());
       attr.setStatus(AttributeStatus.MALFUNCTION);
   }
   ```

2. **状态管理**: 使用`AttributeStatus`正确标识属性状态

---

## 常见问题

**Q: 为什么不能使用带displayName参数的构造函数？**
A: 因为国际化要求显示名称从strings.json中获取，使用硬编码的displayName会破坏国际化机制。

**Q: ModbusScalableFloatSRAttribute的单位参数可以为null吗？**
A: 可以为null。对于设备状态等属性，业务上确实不需要单位参数，这是合理的设计。

**Q: 命令的key为什么要使用英文而不是中文？**
A: 使用英文key符合国际化最佳实践，便于维护和扩展，中文显示名称应该在strings.json中定义。
