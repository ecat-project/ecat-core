# 单元测试

相关文档：[开发工作流程](workflow.md) | [I18n 国际化系统](i18n.md) | [项目结构与核心文件](project-structure.md)

## 测试文件命名

测试文件必须与被测试的设备类对应：

- `CODevice.java` → `CODeviceTest.java`
- `SaimosenIntegration.java` → `SaimosenI18nTest.java`

---

## 测试覆盖要求

每个设备类至少需要一个测试用例验证国际化功能：

```java
@Test
public void testDeviceAttributeI18n() {
    device.init();

    AttributeBase<?> attr = device.getAttrs().get("co");
    assertEquals("CO浓度", attr.getDisplayName());

    StringCommandAttribute commandAttr = (StringCommandAttribute) device.getAttrs().get("gas_device_command");
    assertEquals("气体设备命令", commandAttr.getDisplayName());
}
```

---

## TestTools 使用

TestTools 是 ECAT 核心提供的反射操作工具类：

```java
import com.ecat.core.Utils.TestTools;

// 设置私有字段
TestTools.setPrivateField(device, "modbusSource", mockSource);

// 获取私有字段
Object value = TestTools.getPrivateField(device, "modbusSource");

// 调用私有方法（自动推断参数类型）
Object result = TestTools.invokePrivateMethod(device, "updateValue", 123);

// 调用私有方法（指定参数类型）
Object result2 = TestTools.invokePrivateMethodByClass(
    device, "updateValue", new Class<?>[]{int.class}, 123
);
```

**核心方法**:

| 方法 | 说明 |
|------|------|
| `setPrivateField(target, fieldName, value)` | 设置私有字段值 |
| `getPrivateField(target, fieldName)` | 获取私有字段值 |
| `findField(clazz, fieldName)` | 递归查找类及父类中的字段 |
| `invokePrivateMethod(target, methodName, args...)` | 调用私有方法（自动推断参数类型） |
| `invokePrivateMethodByClass(target, methodName, types, args...)` | 调用私有方法（指定参数类型） |
| `invokePrivateStaticMethod(Class, methodName, args...)` | 调用私有静态方法（支持基本类型：short, int, long, double, float, boolean） |
| `findMethod(clazz, methodName, types...)` | 递归查找类及父类中的方法 |
| `assertAttributeDisplayName(device, attributeId, expected)` | 断言属性显示名称 |

> **注意**：`invokePrivateMethod` 不适合基本类型参数（short, int 等），请使用 `invokePrivateMethodByClass` 或 `invokePrivateStaticMethod`。

---

## 国际化测试

### 属性国际化

```java
@Test
public void testDeviceAttributeI18n() {
    AttributeBase<?> attr = device.getAttrs().get("co");
    assertEquals("CO浓度", attr.getDisplayName());

    StringCommandAttribute commandAttr = (StringCommandAttribute) device.getAttrs().get("gas_device_command");
    assertEquals("气体设备命令", commandAttr.getDisplayName());
}
```

### 命令路径

```java
@Test
public void testCommandI18nWithDeviceSet() throws Exception {
    I18nKeyPath expectedPrefix = new I18nKeyPath("devices.saimosen_device.gas_device_command_commands", "");
    I18nKeyPath actualPrefix = (I18nKeyPath) TestTools.getPrivateField(commandAttr, "i18nCommandPathPrefix");
    assertEquals(expectedPrefix.getFullPath(), actualPrefix.getFullPath());
}
```

### 选项国际化

```java
@Test
public void testOptionI18nName() throws Exception {
    CalibratorGasSelectAttribute attr = (CalibratorGasSelectAttribute) device.getAttrs().get("calibrator_gas_select");
    String optionName = (String) TestTools.invokePrivateMethod(attr, "getOptionI18nName", "SO2");
    assertEquals("二氧化硫", optionName);
}
```

### 设备分组路径

```java
@Test
public void testI18nPathWithDeviceGrouping() throws Exception {
    I18nKeyPath devicePrefix = (I18nKeyPath) TestTools.getPrivateField(device, "i18nPrefix");
    assertEquals("devices.saimosen_device.", devicePrefix.getPathPrefix());

    AttributeBase<?> attr = device.getAttrs().get("co");
    assertNotNull(attr.getDisplayName());
    assertEquals("CO浓度", attr.getDisplayName());
}
```
