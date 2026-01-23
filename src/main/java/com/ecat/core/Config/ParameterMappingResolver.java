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

package com.ecat.core.Config;

import com.ecat.core.Device.DeviceBase;
import com.ecat.core.State.AttributeBase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.yaml.snakeyaml.Yaml;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * ParameterMappingResolver
 * <p>
 * 统一解析设备参数概念与真实设备属性之间的映射关系。
 * 配置来源：.ecat-data/integrations/AParameterMappingConfig.yml
 * </p>
 *
 * <pre>
 * 参数映射示例：
 * parameter_mappings:
 *   std_gas_concentration:
 *     entries:
 *       - device_id: sms-calib
 *         attribute_id: so2_std_gas_concentration
 *         gases: [SO2]
 *       - attribute_id: std_concentration
 *         gases: [SO2, NO2, O3, CO]
 *     fallback:
 *       attribute_id: so2_std_gas_concentration
 * </pre>
 *
 * <p>所有集成可通过 {@link #resolve(String, String)} 获取映射信息。</p>
 */
public final class ParameterMappingResolver {

    private static final Logger logger = LoggerFactory.getLogger(ParameterMappingResolver.class);
    private static final String CONFIG_PATH = ".ecat-data/integrations/AParameterMappingConfig.yml";
    private static final ParameterMappingResolver INSTANCE = new ParameterMappingResolver();

    private final Map<String, ConceptMapping> conceptMappings = new ConcurrentHashMap<>();
    private volatile boolean loaded = false;

    private ParameterMappingResolver() {
    }

    public static ParameterMappingResolver getInstance() {
        if (!INSTANCE.loaded) {
            synchronized (INSTANCE) {
                if (!INSTANCE.loaded) {
                    INSTANCE.load();
                }
            }
        }
        return INSTANCE;
    }

    @SuppressWarnings("unchecked")
    private void load() {
        conceptMappings.clear();

        Optional<Path> configPathOpt = locateConfigPath();
        if (!configPathOpt.isPresent()) {
            logger.warn("参数映射配置文件 {} 未找到", CONFIG_PATH);
            return;
        }

        Path configPath = configPathOpt.get();
        try (InputStream inputStream = new FileInputStream(configPath.toFile())) {
            Yaml yaml = new Yaml();
            Map<String, Object> root = yaml.load(inputStream);

            if (root == null || root.isEmpty()) {
                logger.warn("参数映射配置文件 {} 内容为空", configPath);
                return;
            }

            Object mappingsNode = root.get("parameter_mappings");
            if (!(mappingsNode instanceof Map)) {
                logger.warn("参数映射配置文件 {} 缺少 parameter_mappings 节点", configPath);
                return;
            }

            Map<String, Object> mappings = (Map<String, Object>) mappingsNode;
            for (Map.Entry<String, Object> entry : mappings.entrySet()) {
                if (!(entry.getValue() instanceof Map)) {
                    continue;
                }
                ConceptMapping conceptMapping = parseConceptMapping(entry.getKey(), (Map<String, Object>) entry.getValue());
                conceptMappings.put(entry.getKey(), conceptMapping);
            }

            loaded = true;
            logger.info("参数映射配置加载完成: {}，共解析 {} 个概念参数", configPath, conceptMappings.size());
        } catch (Exception e) {
            logger.error("加载参数映射配置文件失败: {}", e.getMessage(), e);
        }
    }

    private Optional<Path> locateConfigPath() {
        Path current = Paths.get("").toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(CONFIG_PATH);
            if (Files.exists(candidate)) {
                return Optional.of(candidate);
            }
            current = current.getParent();
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private ConceptMapping parseConceptMapping(String conceptKey, Map<String, Object> node) {
        ConceptMapping mapping = new ConceptMapping();

        Object entriesNode = node.get("entries");
        if (entriesNode instanceof List) {
            List<?> entries = (List<?>) entriesNode;
            for (Object entryObj : entries) {
                if (!(entryObj instanceof Map)) {
                    continue;
                }
                DeviceAttributeMapping deviceMapping = parseDeviceAttributeMapping((Map<String, Object>) entryObj);
                if (deviceMapping != null) {
                    mapping.addEntry(deviceMapping);
                }
            }
        }

        Object fallbackNode = node.get("fallback");
        if (fallbackNode instanceof Map) {
            DeviceAttributeMapping fallback = parseDeviceAttributeMapping((Map<String, Object>) fallbackNode);
            mapping.setFallback(fallback);
        }

        return mapping;
    }

    private DeviceAttributeMapping parseDeviceAttributeMapping(Map<String, Object> node) {
        String attributeId = stringValue(node.get("attribute_id"));
        if (attributeId == null) {
            return null;
        }
        String deviceId = stringValue(node.get("device_id"));

        Set<String> gases = new HashSet<>();
        Object gasNode = node.get("gas");
        if (gasNode instanceof String) {
            gases.add(normalizeGas((String) gasNode));
        }

        Object gasesNode = node.get("gases");
        if (gasesNode instanceof List<?>) {
            for (Object gas : (List<?>) gasesNode) {
                if (gas instanceof String) {
                    gases.add(normalizeGas((String) gas));
                }
            }
        }

        return new DeviceAttributeMapping(deviceId, attributeId, gases);
    }

    private String stringValue(Object value) {
        if (value instanceof String) {
            String str = ((String) value).trim();
            return str.isEmpty() ? null : str;
        }
        return null;
    }

    private String normalizeGas(String gas) {
        return gas == null ? null : gas.trim().toUpperCase(Locale.ENGLISH);
    }

    /**
     * 根据概念参数与气体类型解析设备属性映射。
     *
     * @param conceptKey 概念参数名称，例如 std_gas_concentration
     * @param gasType    气体类型，可为 null
     * @return 设备属性映射
     */
    public Optional<DeviceAttributeMapping> resolve(String conceptKey, String gasType) {
        ConceptMapping mapping = conceptMappings.get(conceptKey);
        if (mapping == null) {
            return Optional.empty();
        }
        return mapping.resolve(normalizeGas(gasType));
    }

    /**
     * 获取指定概念的所有映射条目。
     *
     * @param conceptKey 概念参数名称
     * @return 映射列表
     */
    public List<DeviceAttributeMapping> getMappings(String conceptKey) {
        ConceptMapping mapping = conceptMappings.get(conceptKey);
        if (mapping == null) {
            return Collections.emptyList();
        }
        return mapping.getEntries();
    }

    /**
     * 获取当前环境可用的映射（设备存在时才返回）。
     *
     * @param conceptKey    概念参数名称
     * @param deviceFetcher 根据设备ID获取设备的函数
     * @return 已加载设备对应的映射
     */
    public List<DeviceAttributeMapping> getParameterMappings(
            String conceptKey,
            Function<String, DeviceBase> deviceFetcher) {

        List<DeviceAttributeMapping> mappings = getMappings(conceptKey);
        List<DeviceAttributeMapping> active = new ArrayList<>();

        for (DeviceAttributeMapping mapping : mappings) {
            if (mapping.getDeviceId() == null) {
                active.add(mapping);
                continue;
            }
            DeviceBase device = deviceFetcher.apply(mapping.getDeviceId());
            if (device != null) {
                active.add(mapping);
            }
        }

        return active;
    }

    /**
     * 获取概念参数对应设备属性的显示值。
     *
     * @param conceptKey       概念参数名称
     * @param gasType          气体类型，可为空
     * @param fallbackDevice   默认设备（当映射未指定设备ID时使用）
     * @param deviceFetcher    根据设备ID获取设备对象的函数
     * @return 属性显示值
     */
    public Optional<String> getAttributeValue(
            String conceptKey,
            String gasType,
            DeviceBase fallbackDevice,
            Function<String, DeviceBase> deviceFetcher) {

        Optional<DeviceAttributeMapping> mappingOpt = resolve(conceptKey, gasType);
        if (!mappingOpt.isPresent()) {
            return Optional.empty();
        }

        DeviceAttributeMapping mapping = mappingOpt.get();
        DeviceBase targetDevice;
        if (mapping.getDeviceId() != null) {
            targetDevice = deviceFetcher.apply(mapping.getDeviceId());
        } else {
            targetDevice = fallbackDevice;
        }

        if (targetDevice == null || targetDevice.getAttrs() == null) {
            return Optional.empty();
        }

        AttributeBase<?> attribute = targetDevice.getAttrs().get(mapping.getAttributeId());
        if (attribute == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(attribute.getDisplayValue());
    }

    private static class ConceptMapping {
        private final List<DeviceAttributeMapping> entries = new ArrayList<>();
        private DeviceAttributeMapping fallback;

        void addEntry(DeviceAttributeMapping entry) {
            entries.add(entry);
        }

        void setFallback(DeviceAttributeMapping fallback) {
            this.fallback = fallback;
        }

        Optional<DeviceAttributeMapping> resolve(String gas) {
            for (DeviceAttributeMapping entry : entries) {
                if (entry.matches(gas)) {
                    return Optional.of(entry);
                }
            }
            return Optional.ofNullable(fallback);
        }

        List<DeviceAttributeMapping> getEntries() {
            return Collections.unmodifiableList(entries);
        }
    }

    public static class DeviceAttributeMapping {
        private final String deviceId;
        private final String attributeId;
        private final Set<String> gases;

        DeviceAttributeMapping(String deviceId, String attributeId, Set<String> gases) {
            this.deviceId = deviceId;
            this.attributeId = attributeId;
            this.gases = gases != null ? gases : Collections.emptySet();
        }

        public String getDeviceId() {
            return deviceId;
        }

        public String getAttributeId() {
            return attributeId;
        }

        public boolean matches(String gas) {
            if (gases.isEmpty()) {
                return true;
            }
            return gas != null && gases.contains(gas);
        }

        public Set<String> getGases() {
            return Collections.unmodifiableSet(gases);
        }
    }
}


