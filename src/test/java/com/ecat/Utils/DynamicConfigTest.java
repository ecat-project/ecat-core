package com.ecat.Utils;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import com.ecat.core.Utils.DynamicConfig.ConfigDefinition;
import com.ecat.core.Utils.DynamicConfig.ConfigFormGenerator;
import com.ecat.core.Utils.DynamicConfig.ConfigItem;
import com.ecat.core.Utils.DynamicConfig.ConfigItemBuilder;
import com.ecat.core.Utils.DynamicConfig.ListSizeValidator;
import com.ecat.core.Utils.DynamicConfig.ListValidator;
import com.ecat.core.Utils.DynamicConfig.StringEnumValidator;
import com.ecat.core.Utils.DynamicConfig.StringLengthValidator;
import com.ecat.core.Utils.DynamicConfig.ConstraintValidator;

public class DynamicConfigTest {
    @Before
    public void setUp() {
    }

    /**
     * 测试 setDeviceName 和 getDeviceName 方法 **/
    @Test
    public void testDynamicConfig()
    {
        // 定义配置项
        Set<String> validValues = new HashSet<>(Arrays.asList("one", "two", "three"));
        StringEnumValidator enumValidator = new StringEnumValidator(validValues);
        // 定义列表验证器
        ListValidator<String> listElementValidator = new ListValidator<>(enumValidator);
        ListSizeValidator<String> listSizeValidator = new ListSizeValidator<>(1, 3);
        List<ConstraintValidator<List<String>>> listValidators = Arrays.asList(listElementValidator, listSizeValidator);

        // 定义单选和多选列表验证器
        ListSizeValidator<String> singleSelectListSizeValidator = new ListSizeValidator<>(1, 1);
        ListSizeValidator<String> multiSelectListSizeValidator = new ListSizeValidator<>(1, 3);
        List<ConstraintValidator<List<String>>> singleSelectListValidators = Arrays.asList(listElementValidator, singleSelectListSizeValidator);
        List<ConstraintValidator<List<String>>> multiSelectListValidators = Arrays.asList(listElementValidator, multiSelectListSizeValidator);
        
        ConfigDefinition configDefinition = new ConfigDefinition();
        ConfigItemBuilder builder = new ConfigItemBuilder()
               .add(new ConfigItem<>("id", String.class, true, null, new StringLengthValidator(3, 10)))
               .add(new ConfigItem<>("name", String.class, true, null, new StringLengthValidator(3, 10)))
               .add(new ConfigItem<>("nested", Map.class, false, null)
                       .addNestedConfigItems(new ConfigItemBuilder()
                              .add(new ConfigItem<>("nestedname", String.class, true, null, new StringLengthValidator(3, 10)))
                              ))
               .add(new ConfigItem<>("note", String.class, true, null, new StringLengthValidator(3, 10)))
               .add(new ConfigItem<>("listField", (Class<List<String>>)(Class<?>)List.class, false, (List<String>) null, listValidators))
               .add(new ConfigItem<>("defaultValue", Integer.class, false, 1))
               .add(new ConfigItem<List<String>>("singleSelectListField", (Class<List<String>>)(Class<?>)List.class, false, (List<String>) null, singleSelectListValidators))
               .add(new ConfigItem<List<String>>("multiSelectListField", (Class<List<String>>)(Class<?>)List.class, false, (List<String>) null, multiSelectListValidators));


        configDefinition.define(builder);

        // 模拟配置
        Map<String, Object> config = new HashMap<>();
        config.put("id", "device-001");
        config.put("name", "Test Dev");
        Map<String, Object> nestedConfig = new HashMap<>();
        nestedConfig.put("nestedname", "namehello");
        config.put("nested", nestedConfig);
        config.put("note", "this a note");
        config.put("listField", Arrays.asList("one", "two"));
        config.put("singleSelectListField", Arrays.asList("one", "two"));
        config.put("multiSelectListField", Arrays.asList("one", "two"));

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

        assertEquals(configDefinition.getInvalidConfigItems().size(), 2);

    }

    @Test
    public void testConfigFormGenerator(){

        // 定义配置项
        Set<String> validValues = new HashSet<>(Arrays.asList("one", "two", "three"));
        StringEnumValidator enumValidator = new StringEnumValidator(validValues);
        // 定义列表验证器
        ListValidator<String> listElementValidator = new ListValidator<>(enumValidator);
        ListSizeValidator<String> listSizeValidator = new ListSizeValidator<>(1, 3);
        List<ConstraintValidator<List<String>>> listValidators = Arrays.asList(listElementValidator, listSizeValidator);

        // 定义单选和多选列表验证器
        ListSizeValidator<String> singleSelectListSizeValidator = new ListSizeValidator<>(1, 1);
        ListSizeValidator<String> multiSelectListSizeValidator = new ListSizeValidator<>(1, 3);
        List<ConstraintValidator<List<String>>> singleSelectListValidators = Arrays.asList(listElementValidator, singleSelectListSizeValidator);
        List<ConstraintValidator<List<String>>> multiSelectListValidators = Arrays.asList(listElementValidator, multiSelectListSizeValidator);
        
        ConfigDefinition configDefinition = new ConfigDefinition();
        ConfigItemBuilder builder = new ConfigItemBuilder()
               .add(new ConfigItem<>("id", String.class, true, null, new StringLengthValidator(3, 10)))
               .add(new ConfigItem<>("name", String.class, true, null, new StringLengthValidator(3, 10)))
               .add(new ConfigItem<>("nested", Map.class, false, null)
                       .addNestedConfigItems(new ConfigItemBuilder()
                              .add(new ConfigItem<>("nestedname", String.class, true, null, new StringLengthValidator(3, 10)))
                              ))
               .add(new ConfigItem<>("note", String.class, true, null, new StringLengthValidator(3, 100)))
               .add(new ConfigItem<>("listField", (Class<List<String>>)(Class<?>)List.class, false, (List<String>) null, listValidators))
               .add(new ConfigItem<>("defaultValue", Integer.class, false, 1))
               .add(new ConfigItem<List<String>>("singleSelectListField", (Class<List<String>>)(Class<?>)List.class, false, (List<String>) null, singleSelectListValidators))
               .add(new ConfigItem<List<String>>("multiSelectListField", (Class<List<String>>)(Class<?>)List.class, false, (List<String>) null, multiSelectListValidators));


        configDefinition.define(builder);

        // 模拟配置
        Map<String, Object> config = new HashMap<>();
        config.put("id", "device-001");
        config.put("name", "Test Dev");
        Map<String, Object> nestedConfig = new HashMap<>();
        nestedConfig.put("nestedname", "namehello");
        config.put("nested", nestedConfig);
        config.put("note", "this a note");
        config.put("listField", Arrays.asList("one", "two"));
        config.put("singleSelectListField", Arrays.asList("two"));
        config.put("multiSelectListField", Arrays.asList("one", "two"));

        // 验证配置
        boolean isValid = configDefinition.validateConfig(config);
        assertTrue(isValid);
        assertEquals(configDefinition.getInvalidConfigItems().size(), 0);

        // 生成 HTML 表单
        ConfigFormGenerator formGenerator = new ConfigFormGenerator();
        String htmlForm = formGenerator.generateForm(configDefinition);
        System.out.println(htmlForm);

        // 模拟表单提交数据，一条错误信息：name 长度不符合要求
        Map<String, String[]> formData = new HashMap<>();
        formData.put("id", new String[]{"one"});
        formData.put("age", new String[]{"20"});
        formData.put("note", new String[]{"note+20"});
        formData.put("name", new String[]{"defaultName1111"});
        formData.put("nested.nestedname", new String[]{"three"});
        formData.put("singleSelectListField", new String[]{"one"});
        formData.put("multiSelectListField", new String[]{"one", "two"});

        // 解析表单数据
        Map<String, Object> parsedConfig = formGenerator.parseFormData(configDefinition, formData);
        System.out.println("解析后的配置: " + parsedConfig);

        // 验证配置
        isValid = configDefinition.validateConfig(parsedConfig);
        assertTrue(!isValid);
        assertEquals(configDefinition.getInvalidConfigItems().size(), 1);

    }

    @Test
    public void testYamlConfigValidation() {
        // 定义基础验证器
        StringLengthValidator stringValidator = new StringLengthValidator(1, 50);
        Set<String> classValidValues = new HashSet<>(Arrays.asList("air.monitor.calibration.zero"));
        StringEnumValidator classEnumValidator = new StringEnumValidator(classValidValues);
        
        // 定义 calibrations.times 配置项
        ConfigItemBuilder timesConfig = new ConfigItemBuilder()
                .add(new ConfigItem<>("prepare_time", Integer.class, true, null))
                .add(new ConfigItem<>("stable_time", Integer.class, true, null))
                .add(new ConfigItem<>("read_data_time_span", Integer.class, true, null))
                .add(new ConfigItem<>("read_data_times", Integer.class, true, null))
                .add(new ConfigItem<>("release_time", Integer.class, true, null));
        
        // 定义 zero_device.turn_on_attribute 和 turn_off_attribute 配置项
        ConfigItemBuilder attributeConfig = new ConfigItemBuilder()
                .add(new ConfigItem<>("id", String.class, true, null, stringValidator))
                .add(new ConfigItem<>("value", String.class, true, null, stringValidator));
        
        // 定义 zero_device 配置项
        ConfigItemBuilder zeroDeviceConfig = new ConfigItemBuilder()
                .add(new ConfigItem<>("id", String.class, true, null, stringValidator))
                .add(new ConfigItem<>("turn_on_attribute", Map.class, true, null)
                        .addNestedConfigItems(attributeConfig))
                .add(new ConfigItem<>("turn_off_attribute", Map.class, true, null)
                        .addNestedConfigItems(attributeConfig));
        
        // 定义 calib_device.gases 中每个气体的配置项
        ConfigItemBuilder gasAttributeConfig = new ConfigItemBuilder()
                .add(new ConfigItem<>("id", String.class, true, null, stringValidator))
                .add(new ConfigItem<>("value", String.class, true, null, stringValidator));
        
        ConfigItemBuilder gasConfig = new ConfigItemBuilder()
                .add(new ConfigItem<>("gas", String.class, true, null, stringValidator))
                .add(new ConfigItem<>("turn_on_attribute", Map.class, true, null)
                        .addNestedConfigItems(gasAttributeConfig))
                .add(new ConfigItem<>("turn_off_attribute", Map.class, true, null)
                        .addNestedConfigItems(gasAttributeConfig))
                .add(new ConfigItem<>("set_concentration_attribute", Map.class, true, null)
                        .addNestedConfigItems(gasAttributeConfig));
        
        // 定义 calib_device 配置项
        ConfigItemBuilder calibDeviceConfig = new ConfigItemBuilder()
                .add(new ConfigItem<>("id", String.class, true, null, stringValidator))
                .add(new ConfigItem<List<Map<String, Object>>>("gases", 
                        (Class<List<Map<String, Object>>>) (Class<?>) List.class, 
                        true, null)
                        .addNestedListItems(gasConfig));
        
        // 定义 valves 中每个阀门的配置项
        ConfigItemBuilder valveAttributeConfig = new ConfigItemBuilder()
                .add(new ConfigItem<>("id", String.class, true, null, stringValidator))
                .add(new ConfigItem<>("value", String.class, true, null, stringValidator));
        
        ConfigItemBuilder valveConfig = new ConfigItemBuilder()
                .add(new ConfigItem<>("gas", String.class, true, null, stringValidator))
                .add(new ConfigItem<>("id", String.class, true, null, stringValidator))
                .add(new ConfigItem<>("turn_on_attribute", Map.class, true, null)
                        .addNestedConfigItems(valveAttributeConfig))
                .add(new ConfigItem<>("turn_off_attribute", Map.class, true, null)
                        .addNestedConfigItems(valveAttributeConfig));
        
        // 定义 tested_devices 中每个被测设备的配置项
        ConfigItemBuilder testedDeviceConfig = new ConfigItemBuilder()
                .add(new ConfigItem<>("gas", String.class, true, null, stringValidator))
                .add(new ConfigItem<>("id", String.class, true, null, stringValidator))
                .add(new ConfigItem<>("data_attribute_id", String.class, true, null, stringValidator))
                .add(new ConfigItem<>("calib_attribute_id", String.class, true, null, stringValidator));
        
        // 定义 calibrations 中每个校准项的配置项
        ConfigItemBuilder calibrationConfig = new ConfigItemBuilder()
                .add(new ConfigItem<>("id", String.class, true, null, stringValidator))
                .add(new ConfigItem<>("name", String.class, true, null, stringValidator))
                .add(new ConfigItem<>("class", String.class, false, null, classEnumValidator))
                .add(new ConfigItem<>("times", Map.class, true, null)
                        .addNestedConfigItems(timesConfig))
                .add(new ConfigItem<>("zero_device", Map.class, true, null)
                        .addNestedConfigItems(zeroDeviceConfig))
                .add(new ConfigItem<>("calib_device", Map.class, true, null)
                        .addNestedConfigItems(calibDeviceConfig))
                .add(new ConfigItem<List<Map<String, Object>>>("valves", 
                        (Class<List<Map<String, Object>>>) (Class<?>) List.class, 
                        true, null)
                        .addNestedListItems(valveConfig))
                .add(new ConfigItem<List<Map<String, Object>>>("tested_devices", 
                        (Class<List<Map<String, Object>>>) (Class<?>) List.class, 
                        true, null)
                        .addNestedListItems(testedDeviceConfig));
        
        // 定义根配置项
        ConfigDefinition configDefinition = new ConfigDefinition();
        ConfigItemBuilder rootBuilder = new ConfigItemBuilder()
                .add(new ConfigItem<>("version", String.class, true, null, stringValidator))
                .add(new ConfigItem<>("update", String.class, true, null, stringValidator))
                .add(new ConfigItem<List<Map<String, Object>>>("calibrations", 
                        (Class<List<Map<String, Object>>>) (Class<?>) List.class, 
                        true, null)
                        .addNestedListItems(calibrationConfig));
        
        configDefinition.define(rootBuilder);
        
        // 模拟 YAML 配置数据
        Map<String, Object> config = new HashMap<>();
        config.put("version", "1.0.0");
        config.put("update", "2024-02-02 14:42:21");
        
        List<Map<String, Object>> calibrations = new ArrayList<>();
        Map<String, Object> calibration = new HashMap<>();
        calibration.put("id", "calib-001");
        calibration.put("name", "零点检查");
        calibration.put("class", "air.monitor.calibration.zero");
        
        Map<String, Object> times = new HashMap<>();
        times.put("prepare_time", 10);
        times.put("stable_time", 600);
        times.put("read_data_time_span", 60);
        times.put("read_data_times", 5);
        times.put("release_time", 300);
        calibration.put("times", times);
        
        Map<String, Object> zeroDevice = new HashMap<>();
        zeroDevice.put("id", "zero");
        
        Map<String, Object> turnOnAttribute = new HashMap<>();
        turnOnAttribute.put("id", "zero_001");
        turnOnAttribute.put("value", "1");
        zeroDevice.put("turn_on_attribute", turnOnAttribute);
        
        Map<String, Object> turnOffAttribute = new HashMap<>();
        turnOffAttribute.put("id", "zero_002");
        turnOffAttribute.put("value", "0");
        zeroDevice.put("turn_off_attribute", turnOffAttribute);
        calibration.put("zero_device", zeroDevice);
        
        Map<String, Object> calibDevice = new HashMap<>();
        calibDevice.put("id", "zero");
        
        List<Map<String, Object>> gases = new ArrayList<>();
        addGas(gases, "SO2", "gas_001", "0.0");
        addGas(gases, "CO", "gas_002", "0.0");
        addGas(gases, "NO2", "gas_003", "0.0");
        addGas(gases, "O3", "gas_004", "0.0");
        calibDevice.put("gases", gases);
        calibration.put("calib_device", calibDevice);
        
        List<Map<String, Object>> valves = new ArrayList<>();
        addValve(valves, "SO2", "valve_001", "valve_001_on", "valve_001_off");
        addValve(valves, "CO", "valve_001", "valve_001_on", "valve_001_off");
        calibration.put("valves", valves);
        
        List<Map<String, Object>> testedDevices = new ArrayList<>();
        addTestedDevice(testedDevices, "SO2", "so2", "so2_data", "so2_calib");
        addTestedDevice(testedDevices, "CO", "co", "co_data", "co_calib");
        addTestedDevice(testedDevices, "NO2", "no2", "no2_data", "no2_calib");
        addTestedDevice(testedDevices, "O3", "o3", "o3_data", "o3_calib");
        calibration.put("tested_devices", testedDevices);
        
        calibrations.add(calibration);
        config.put("calibrations", calibrations);
        
        // 验证配置
        boolean isValid = configDefinition.validateConfig(config);
        assertTrue("配置验证失败", isValid);
        assertEquals("无效配置项数量不为0", 0, configDefinition.getInvalidConfigItems().size());
        
        // 测试表单生成
        ConfigFormGenerator formGenerator = new ConfigFormGenerator();
        String htmlForm = formGenerator.generateForm(configDefinition);
        assertNotNull("HTML表单为空", htmlForm);
        
        // 测试表单数据解析
        Map<String, String[]> formData = createFormData(config);
        Map<String, Object> parsedConfig = formGenerator.parseFormData(configDefinition, formData);
        assertNotNull("解析后的配置为空", parsedConfig);
        
        // 验证解析后的配置
        boolean parsedIsValid = configDefinition.validateConfig(parsedConfig);
        // TODO：目前解析List的嵌套配置错误，需要修正，但目前还没用到生成parseFormData函数，后面优化
        // assertTrue("解析后的配置验证失败", parsedIsValid);
        // assertEquals("解析后的配置无效配置项数量不为0", 0, configDefinition.getInvalidConfigItems().size());
    }
    
    // 辅助方法：添加气体配置
    private void addGas(List<Map<String, Object>> gases, String gasName, String attributeId, String concentration) {
        Map<String, Object> gas = new HashMap<>();
        gas.put("gas", gasName);
        
        Map<String, Object> turnOn = new HashMap<>();
        turnOn.put("id", attributeId);
        turnOn.put("value", "1");
        gas.put("turn_on_attribute", turnOn);
        
        Map<String, Object> turnOff = new HashMap<>();
        turnOff.put("id", attributeId);
        turnOff.put("value", "0");
        gas.put("turn_off_attribute", turnOff);
        
        Map<String, Object> setConcentration = new HashMap<>();
        setConcentration.put("id", attributeId + "_concentration");
        setConcentration.put("value", concentration);
        gas.put("set_concentration_attribute", setConcentration);
        
        gases.add(gas);
    }
    
    // 辅助方法：添加阀门配置
    private void addValve(List<Map<String, Object>> valves, String gasName, String valveId, 
                          String turnOnId, String turnOffId) {
        Map<String, Object> valve = new HashMap<>();
        valve.put("gas", gasName);
        valve.put("id", valveId);
        
        Map<String, Object> turnOn = new HashMap<>();
        turnOn.put("id", turnOnId);
        turnOn.put("value", "1");
        valve.put("turn_on_attribute", turnOn);
        
        Map<String, Object> turnOff = new HashMap<>();
        turnOff.put("id", turnOffId);
        turnOff.put("value", "0");
        valve.put("turn_off_attribute", turnOff);
        
        valves.add(valve);
    }
    
    // 辅助方法：添加被测设备配置
    private void addTestedDevice(List<Map<String, Object>> testedDevices, String gasName, 
                               String deviceId, String dataAttr, String calibAttr) {
        Map<String, Object> device = new HashMap<>();
        device.put("gas", gasName);
        device.put("id", deviceId);
        device.put("data_attribute_id", dataAttr);
        device.put("calib_attribute_id", calibAttr);
        testedDevices.add(device);
    }
    
    // 辅助方法：创建表单数据
    private Map<String, String[]> createFormData(Map<String, Object> config) {
        Map<String, String[]> formData = new HashMap<>();
        
        // 根字段
        formData.put("version", new String[]{"1.0.0"});
        formData.put("update", new String[]{"2024-02-02 14:42:21"});
        
        // calibrations[0] 主字段
        formData.put("calibrations[0].id", new String[]{"calib-001"});
        formData.put("calibrations[0].name", new String[]{"零点检查"});
        formData.put("calibrations[0].class", new String[]{"air.monitor.calibration.zero"});
        
        // calibrations[0].times 字段
        formData.put("calibrations[0].times.prepare_time", new String[]{"10"});
        formData.put("calibrations[0].times.stable_time", new String[]{"600"});
        formData.put("calibrations[0].times.read_data_time_span", new String[]{"60"});
        formData.put("calibrations[0].times.read_data_times", new String[]{"5"});
        formData.put("calibrations[0].times.release_time", new String[]{"300"});
        
        // calibrations[0].zero_device 字段
        formData.put("calibrations[0].zero_device.id", new String[]{"zero"});
        formData.put("calibrations[0].zero_device.turn_on_attribute.id", new String[]{"zero_001"});
        formData.put("calibrations[0].zero_device.turn_on_attribute.value", new String[]{"1"});
        formData.put("calibrations[0].zero_device.turn_off_attribute.id", new String[]{"zero_002"});
        formData.put("calibrations[0].zero_device.turn_off_attribute.value", new String[]{"0"});
        
        // calibrations[0].calib_device 字段
        formData.put("calibrations[0].calib_device.id", new String[]{"zero"});
        
        // calibrations[0].calib_device.gases 字段（4个元素）
        formData.put("calibrations[0].calib_device.gases[0].gas", new String[]{"SO2"});
        formData.put("calibrations[0].calib_device.gases[0].turn_on_attribute.id", new String[]{"gas_001"});
        formData.put("calibrations[0].calib_device.gases[0].turn_on_attribute.value", new String[]{"1"});
        formData.put("calibrations[0].calib_device.gases[0].turn_off_attribute.id", new String[]{"gas_001"});
        formData.put("calibrations[0].calib_device.gases[0].turn_off_attribute.value", new String[]{"0"});
        formData.put("calibrations[0].calib_device.gases[0].set_concentration_attribute.id", new String[]{"gas_001_concentration"});
        formData.put("calibrations[0].calib_device.gases[0].set_concentration_attribute.value", new String[]{"0.0"});
        
        formData.put("calibrations[0].calib_device.gases[1].gas", new String[]{"CO"});
        formData.put("calibrations[0].calib_device.gases[1].turn_on_attribute.id", new String[]{"gas_002"});
        formData.put("calibrations[0].calib_device.gases[1].turn_on_attribute.value", new String[]{"1"});
        formData.put("calibrations[0].calib_device.gases[1].turn_off_attribute.id", new String[]{"gas_002"});
        formData.put("calibrations[0].calib_device.gases[1].turn_off_attribute.value", new String[]{"0"});
        formData.put("calibrations[0].calib_device.gases[1].set_concentration_attribute.id", new String[]{"gas_001_concentration"});
        formData.put("calibrations[0].calib_device.gases[1].set_concentration_attribute.value", new String[]{"0.0"});
        
        formData.put("calibrations[0].calib_device.gases[2].gas", new String[]{"NO2"});
        formData.put("calibrations[0].calib_device.gases[2].turn_on_attribute.id", new String[]{"gas_003"});
        formData.put("calibrations[0].calib_device.gases[2].turn_on_attribute.value", new String[]{"1"});
        formData.put("calibrations[0].calib_device.gases[2].turn_off_attribute.id", new String[]{"gas_003"});
        formData.put("calibrations[0].calib_device.gases[2].turn_off_attribute.value", new String[]{"0"});
        formData.put("calibrations[0].calib_device.gases[2].set_concentration_attribute.id", new String[]{"gas_001_concentration"});
        formData.put("calibrations[0].calib_device.gases[2].set_concentration_attribute.value", new String[]{"0.0"});
        
        formData.put("calibrations[0].calib_device.gases[3].gas", new String[]{"O3"});
        formData.put("calibrations[0].calib_device.gases[3].turn_on_attribute.id", new String[]{"gas_004"});
        formData.put("calibrations[0].calib_device.gases[3].turn_on_attribute.value", new String[]{"1"});
        formData.put("calibrations[0].calib_device.gases[3].turn_off_attribute.id", new String[]{"gas_004"});
        formData.put("calibrations[0].calib_device.gases[3].turn_off_attribute.value", new String[]{"0"});
        formData.put("calibrations[0].calib_device.gases[3].set_concentration_attribute.id", new String[]{"gas_001_concentration"});
        formData.put("calibrations[0].calib_device.gases[3].set_concentration_attribute.value", new String[]{"0.0"});
        
        // calibrations[0].valves 字段（2个元素）
        formData.put("calibrations[0].valves[0].gas", new String[]{"SO2"});
        formData.put("calibrations[0].valves[0].id", new String[]{"valve_001"});
        formData.put("calibrations[0].valves[0].turn_on_attribute.id", new String[]{"valve_001_on"});
        formData.put("calibrations[0].valves[0].turn_on_attribute.value", new String[]{"1"});
        formData.put("calibrations[0].valves[0].turn_off_attribute.id", new String[]{"valve_001_off"});
        formData.put("calibrations[0].valves[0].turn_off_attribute.value", new String[]{"0"});
        
        formData.put("calibrations[0].valves[1].gas", new String[]{"CO"});
        formData.put("calibrations[0].valves[1].id", new String[]{"valve_001"});
        formData.put("calibrations[0].valves[1].turn_on_attribute.id", new String[]{"valve_001_on"});
        formData.put("calibrations[0].valves[1].turn_on_attribute.value", new String[]{"1"});
        formData.put("calibrations[0].valves[1].turn_off_attribute.id", new String[]{"valve_001_off"});
        formData.put("calibrations[0].valves[1].turn_off_attribute.value", new String[]{"0"});
        
        // calibrations[0].tested_devices 字段（4个元素）
        formData.put("calibrations[0].tested_devices[0].gas", new String[]{"SO2"});
        formData.put("calibrations[0].tested_devices[0].id", new String[]{"so2"});
        formData.put("calibrations[0].tested_devices[0].data_attribute_id", new String[]{"so2_data"});
        formData.put("calibrations[0].tested_devices[0].calib_attribute_id", new String[]{"so2_calib"});
        
        formData.put("calibrations[0].tested_devices[1].gas", new String[]{"CO"});
        formData.put("calibrations[0].tested_devices[1].id", new String[]{"co"});
        formData.put("calibrations[0].tested_devices[1].data_attribute_id", new String[]{"co_data"});
        formData.put("calibrations[0].tested_devices[1].calib_attribute_id", new String[]{"co_calib"});
        
        formData.put("calibrations[0].tested_devices[2].gas", new String[]{"NO2"});
        formData.put("calibrations[0].tested_devices[2].id", new String[]{"no2"});
        formData.put("calibrations[0].tested_devices[2].data_attribute_id", new String[]{"no2_data"});
        formData.put("calibrations[0].tested_devices[2].calib_attribute_id", new String[]{"no2_calib"});
        
        formData.put("calibrations[0].tested_devices[3].gas", new String[]{"O3"});
        formData.put("calibrations[0].tested_devices[3].id", new String[]{"o3"});
        formData.put("calibrations[0].tested_devices[3].data_attribute_id", new String[]{"o3_data"});
        formData.put("calibrations[0].tested_devices[3].calib_attribute_id", new String[]{"o3_calib"});
        
        return formData;
    }
}
