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

package com.ecat.core.ConfigFlow;

import com.ecat.core.I18n.I18nHelper;
import com.ecat.core.I18n.I18nProxy;
import com.ecat.core.Utils.DynamicConfig.ConfigDefinition;
import com.ecat.core.Utils.Log;
import com.ecat.core.Utils.LogFactory;
import lombok.Getter;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 抽象配置流程基类
 *
 * <p>提供步骤隔离、反射发现步骤、模板方法模式。
 *
 * <p>使用方法：
 * <ol>
 *   <li>继承此类</li>
 *   <li>实现 {@code step_xxx(Map<String, Object> userInput)} 方法</li>
 *   <li>使用 {@link #show_form} 显示表单</li>
 *   <li>使用 {@link #create_entry} 完成流程</li>
 * </ol>
 *
 * <p>步骤方法命名约定：方法名必须以 {@code step_} 开头，
 * 接收一个 {@code Map<String, Object>} 参数，返回 {@link ConfigFlowResult}。
 *
 * <p>示例：
 * <pre>{@code
 * class DemoConfigFlow extends AbstractConfigFlow {
 *
 *     public DemoConfigFlow(String flowId) {
 *         super(flowId);
 *     }
 *
 *     @Override
 *     protected ConfigFlowResult step_user(Map<String, Object> userInput) {
 *         if (userInput == null || userInput.isEmpty()) {
 *             return show_form("user", this::generateUserSchema, new HashMap<>());
 *         }
 *         // 验证并进入下一步
 *         return show_form("device_config", this::generateDeviceSchema, new HashMap<>());
 *     }
 *
 *     private ConfigDefinition generateUserSchema(FormContext context) {
 *         ConfigDefinition configDef = new ConfigDefinition();
 *         configDef.define(new ConfigItemBuilder()
 *             .add(new ConfigItem<>("username", String.class, true, null)));
 *         return configDef;
 *     }
 * }
 * }</pre>
 *
 * @author coffee
 */
public abstract class AbstractConfigFlow {

    /**
     * i18n Key 前缀
     */
    private static final String PREFIX = "config_flow";

    /**
     * 日志实例
     */
    protected final Log log = LogFactory.getLogger(getClass());

    /**
     * 流程实例 ID
     */
    @Getter
    protected final String flowId;

    /**
     * 当前步骤 ID
     */
    @Getter
    protected String currentStep = "user";

    /**
     * 流程数据存储
     */
    protected final Map<String, Object> flowData = new ConcurrentHashMap<>();

    /**
     * 步骤处理器方法缓存
     */
    private final Map<String, Method> stepHandlers = new ConcurrentHashMap<>();

    /**
     * 步骤历史记录
     */
    private List<String> stepHistory = new ArrayList<>();

    /**
     * I18n 代理实例
     * 用于获取翻译资源
     */
    protected final I18nProxy i18n;

    /**
     * 构造函数
     *
     * @param flowId 流程实例 ID
     */
    protected AbstractConfigFlow(String flowId) {
        this.flowId = flowId;
        this.i18n = I18nHelper.createProxy(this.getClass());
        cacheStepHandlers();
    }

    /**
     * 缓存步骤处理器方法
     *
     * <p>基于命名约定自动发现 {@code step_*} 方法。
     */
    private void cacheStepHandlers() {
        for (Method method : this.getClass().getDeclaredMethods()) {
            if (method.getName().startsWith("step_") &&
                method.getParameterCount() == 1 &&
                method.getParameterTypes()[0] == Map.class) {

                String stepId = method.getName().substring("step_".length());
                stepHandlers.put(stepId, method);
            }
        }
    }

    /**
     * 统一的步骤处理入口
     *
     * <p>通过反射动态调用对应的步骤处理方法。
     *
     * @param stepId 步骤 ID
     * @param userInput 用户输入数据（首次进入时为 null）
     * @return 配置流程结果
     */
    public final ConfigFlowResult handleStep(String stepId, Map<String, Object> userInput) {
        try {
            // 记录步骤历史（记录处理的步骤）
            if (!stepHistory.contains(stepId)) {
                stepHistory.add(stepId);
            }
            // 暂时设置为处理的步骤，后续会根据结果更新
            this.currentStep = stepId;

            // 保存用户输入到步骤级数据存储
            if (userInput != null) {
                saveStepData(stepId, userInput);
            }

            // 动态调用步骤处理方法
            Method handler = stepHandlers.get(stepId);
            if (handler == null) {
                return ConfigFlowResult.abort("未知步骤: " + stepId);
            }

            handler.setAccessible(true);
            ConfigFlowResult result = (ConfigFlowResult) handler.invoke(this, userInput);

            // 如果返回的是 SHOW_FORM，更新当前步骤为显示的步骤（而非处理的步骤）
            // 这样导航信息才能正确反映用户看到的界面
            if (result.getType() == ConfigFlowResult.ResultType.SHOW_FORM) {
                String displayStepId = result.getStepId();
                if (displayStepId != null && !displayStepId.equals(this.currentStep)) {
                    this.currentStep = displayStepId;
                    // 如果是新步骤，添加到历史记录
                    if (!stepHistory.contains(displayStepId)) {
                        stepHistory.add(displayStepId);
                    }
                }
            }

            return result;

        } catch (Exception e) {
            log.error("步骤处理异常", e);
            return ConfigFlowResult.abort("步骤处理异常: " + e.getMessage());
        }
    }

    /**
     * 显示表单
     *
     * @param stepId 当前步骤 ID
     * @param schema Schema 生成函数
     * @param errors 错误信息
     * @return SHOW_FORM 类型结果
     */
    protected final ConfigFlowResult show_form(String stepId,
                                               DynamicFormSchema schema,
                                               Map<String, Object> errors) {
        Map<String, Object> tempData = new HashMap<>();

        // 从 step_inputs 复制当前步骤数据
        @SuppressWarnings("unchecked")
        Map<String, Object> stepInputs = (Map<String, Object>) flowData.get("step_inputs");
        if (stepInputs != null) {
            Map<String, Object> currentStepData = (Map<String, Object>) stepInputs.get(stepId);
            if (currentStepData != null) {
                tempData.putAll(currentStepData);
            }
        }

        // 生成表单 Schema
        FormContext context = new FormContext(stepId, tempData, errors);
        ConfigDefinition configDef = schema.generateSchema(context);
        configDef.fillDefaults(tempData);

        // 验证现有数据
        if (!configDef.validateConfig(tempData)) {
            errors = new HashMap<>();
            // 处理旧版 ConfigItem 错误
            for (Map.Entry<com.ecat.core.Utils.DynamicConfig.ConfigItem<?>, String> entry :
                    configDef.getInvalidConfigItems().entrySet()) {
                errors.put(entry.getKey().getKey(), entry.getValue());
            }
            // 处理新版 AbstractConfigItem 错误
            for (Map.Entry<com.ecat.core.ConfigFlow.ConfigItem.AbstractConfigItem<?>, String> entry :
                    configDef.getInvalidFlowConfigItems().entrySet()) {
                errors.put(entry.getKey().getKey(), entry.getValue());
            }
        }

        return ConfigFlowResult.showForm(stepId, configDef, errors, flowData);
    }

    /**
     * 验证步骤用户输入
     *
     * <p>使用 ConfigDefinition 中的 ConfigItem 验证器自动验证用户输入。
     * 如果验证失败，返回包含错误信息的 Map；如果验证通过，返回空 Map。
     *
     * @param stepId 步骤 ID
     * @param userInput 用户输入
     * @param schema Schema 生成函数
     * @return 错误信息 Map（空表示验证通过）
     */
    protected final Map<String, Object> validateStepInput(String stepId,
                                                          Map<String, Object> userInput,
                                                          DynamicFormSchema schema) {
        Map<String, Object> errors = new HashMap<>();

        if (userInput == null || userInput.isEmpty()) {
            return errors;
        }

        // 生成 ConfigDefinition 进行验证
        FormContext context = new FormContext(stepId, userInput, errors);
        ConfigDefinition configDef = schema.generateSchema(context);

        // 执行验证
        if (!configDef.validateConfig(userInput)) {
            // 处理新版 AbstractConfigItem 错误
            for (Map.Entry<com.ecat.core.ConfigFlow.ConfigItem.AbstractConfigItem<?>, String> entry :
                    configDef.getInvalidFlowConfigItems().entrySet()) {
                errors.put(entry.getKey().getKey(), entry.getValue());
            }
        }

        return errors;
    }

    /**
     * 创建配置条目
     *
     * <p>此方法表示流程完成，可以创建最终的配置条目。
     *
     * @param data 最终配置数据
     * @return CREATE_ENTRY 类型结果
     */
    protected final ConfigFlowResult create_entry(Map<String, Object> data) {
        Map<String, Object> resultData = new HashMap<>();

        @SuppressWarnings("unchecked")
        Map<String, Object> stepInputs = (Map<String, Object>) flowData.get("step_inputs");
        if (stepInputs != null) {
            resultData.put("step_inputs", new HashMap<>(stepInputs));
        }

        if (data != null) {
            resultData.putAll(data);
        }

        return ConfigFlowResult.createEntry(resultData);
    }

    /**
     * 中止流程
     *
     * @param reason 中止原因
     * @return ABORT 类型结果
     */
    protected final ConfigFlowResult abort(String reason) {
        return ConfigFlowResult.abort(reason);
    }

    /**
     * 保存步骤数据到独立命名空间
     *
     * @param stepId 步骤 ID
     * @param userInput 用户输入数据
     */
    protected void saveStepData(String stepId, Map<String, Object> userInput) {
        synchronized (flowData) {
            @SuppressWarnings("unchecked")
            Map<String, Object> stepInputs = (Map<String, Object>)
                flowData.computeIfAbsent("step_inputs", k -> new HashMap<>());

            stepInputs.put(stepId, new HashMap<>(userInput));
        }
    }

    /**
     * 获取特定步骤的用户输入
     *
     * @param stepId 步骤 ID
     * @return 该步骤的用户输入数据副本
     */
    @SuppressWarnings("unchecked")
    protected Map<String, Object> getStepData(String stepId) {
        synchronized (flowData) {
            Map<String, Object> stepInputs = (Map<String, Object>) flowData.get("step_inputs");
            if (stepInputs != null && stepInputs.containsKey(stepId)) {
                return new HashMap<>((Map<String, Object>) stepInputs.get(stepId));
            }
            return new HashMap<>();
        }
    }

    /**
     * 获取当前步骤的用户输入
     *
     * @return 当前步骤的用户输入数据
     */
    protected Map<String, Object> getCurrentStepData() {
        return getStepData(currentStep);
    }

    /**
     * 获取流程数据
     *
     * @return 流程数据的副本
     */
    public Map<String, Object> getData() {
        return new HashMap<>(flowData);
    }

    /**
     * 获取上一步的 ID
     *
     * @return 上一步的 ID，如果是第一步则返回 null
     */
    public String getPreviousStep() {
        int currentIndex = stepHistory.indexOf(currentStep);
        if (currentIndex > 0) {
            return stepHistory.get(currentIndex - 1);
        }
        return null;
    }

    /**
     * 回退到上一步
     *
     * @throws IllegalStateException 如果已经是第一步
     */
    public void goToPreviousStep() {
        String previousStepId = getPreviousStep();
        if (previousStepId == null) {
            throw new IllegalStateException("已经是第一步，无法回退");
        }

        this.currentStep = previousStepId;

        int currentIndex = stepHistory.indexOf(previousStepId);
        if (currentIndex >= 0 && currentIndex < stepHistory.size() - 1) {
            stepHistory = new ArrayList<>(stepHistory.subList(0, currentIndex + 1));
        }
    }

    /**
     * 获取步骤历史
     *
     * @return 步骤历史列表的副本
     */
    public List<String> getStepHistory() {
        return new ArrayList<>(stepHistory);
    }

    /**
     * 获取流程数据中的值
     *
     * @param key 键
     * @return 值，不存在时返回 null
     */
    protected Object getFlowData(String key) {
        return flowData.get(key);
    }

    /**
     * 设置流程数据
     *
     * @param key 键
     * @param value 值
     */
    protected void setFlowData(String key, Object value) {
        flowData.put(key, value);
    }

    /**
     * 获取流程数据中的值（带类型和默认值）
     *
     * @param key 键
     * @param type 期望的类型
     * @param defaultValue 默认值
     * @param <T> 值的类型
     * @return 值，不存在或类型不匹配时返回默认值
     */
    protected <T> T getFlowData(String key, Class<T> type, T defaultValue) {
        Object value = flowData.get(key);
        return value != null && type.isInstance(value) ? type.cast(value) : defaultValue;
    }

    // ========== 步骤显示信息 ==========

    /**
     * 获取步骤信息映射
     * <p>
     * 子类可覆盖此方法提供自定义的步骤信息，包括显示名称、描述等。
     *
     * @return 步骤 ID -> StepInfo 的映射，默认返回 null
     */
    public Map<String, StepInfo> getStepInfos() {
        return null;
    }

    // ========== I18n 约定方法 ==========

    /**
     * 获取步骤显示名称
     * <p>
     * 查找顺序：i18n 资源 -> getStepInfos() -> stepId
     * <p>
     * Key 格式: config_flow.step_{stepId}.display_name
     *
     * @param stepId 步骤 ID
     * @return 步骤显示名称
     */
    public String getStepDisplayName(String stepId) {
        // 1. 优先从 i18n 资源获取
        if (i18n != null) {
            String key = PREFIX + ".step_" + stepId + ".display_name";
            String translated = i18n.t(key);
            if (!translated.equals(key)) {
                return translated;
            }
        }

        // 2. 从 getStepInfos() 获取
        Map<String, StepInfo> infos = getStepInfos();
        if (infos != null && infos.containsKey(stepId)) {
            StepInfo info = infos.get(stepId);
            if (info != null && info.getDisplayName() != null) {
                return info.getDisplayName();
            }
        }

        // 3. 兜底：返回 stepId
        return stepId;
    }

    /**
     * 获取字段显示名称
     * <p>
     * Key 格式: config_flow.step_{stepId}.items.{fieldKey}.display_name
     *
     * @param stepId 步骤 ID
     * @param fieldKey 字段键
     * @return 字段显示名称
     */
    public String getFieldDisplayName(String stepId, String fieldKey) {
        if (i18n != null) {
            String key = PREFIX + ".step_" + stepId + ".items." + fieldKey + ".display_name";
            String translated = i18n.t(key);
            if (!translated.equals(key)) {
                return translated;
            }
        }
        return fieldKey;
    }

    /**
     * 获取字段占位符
     * <p>
     * Key 格式: config_flow.step_{stepId}.items.{fieldKey}.placeholder
     *
     * @param stepId 步骤 ID
     * @param fieldKey 字段键
     * @return 字段占位符，找不到时返回 null
     */
    public String getFieldPlaceholder(String stepId, String fieldKey) {
        if (i18n != null) {
            String key = PREFIX + ".step_" + stepId + ".items." + fieldKey + ".placeholder";
            String translated = i18n.t(key);
            if (!translated.equals(key)) {
                return translated;
            }
        }
        return null;
    }

    /**
     * 获取字段描述
     * <p>
     * Key 格式: config_flow.step_{stepId}.items.{fieldKey}.description
     *
     * @param stepId 步骤 ID
     * @param fieldKey 字段键
     * @return 字段描述，找不到时返回 null
     */
    public String getFieldDescription(String stepId, String fieldKey) {
        if (i18n != null) {
            String key = PREFIX + ".step_" + stepId + ".items." + fieldKey + ".description";
            String translated = i18n.t(key);
            if (!translated.equals(key)) {
                return translated;
            }
        }
        return null;
    }

    /**
     * 获取选项显示名称
     * <p>
     * Key 格式: config_flow.step_{stepId}.items.{fieldKey}.options.{optionValue}
     *
     * @param stepId 步骤 ID
     * @param fieldKey 字段键
     * @param optionValue 选项值
     * @return 选项显示名称
     */
    public String getOptionDisplayName(String stepId, String fieldKey, String optionValue) {
        if (i18n != null) {
            String key = PREFIX + ".step_" + stepId + ".items." + fieldKey + ".options." + optionValue;
            String translated = i18n.t(key);
            if (!translated.equals(key)) {
                return translated;
            }
        }
        return optionValue;
    }

    /**
     * 获取带翻译的选项 Map
     * <p>
     * 遍历所有选项，使用 getOptionDisplayName 获取翻译后的显示名称。
     *
     * @param stepId 步骤 ID
     * @param fieldKey 字段键
     * @param options 原始选项 Map (value -> label)
     * @return 翻译后的选项 Map (value -> translated label)
     */
    public Map<String, String> getTranslatedOptions(String stepId, String fieldKey, Map<String, String> options) {
        Map<String, String> translated = new LinkedHashMap<>();
        if (options == null) {
            return translated;
        }
        for (Map.Entry<String, String> entry : options.entrySet()) {
            translated.put(entry.getKey(), getOptionDisplayName(stepId, fieldKey, entry.getKey()));
        }
        return translated;
    }

    // ========== I18n 特殊消息方法 ==========

    /**
     * 获取自定义消息
     * <p>
     * 用于获取无固定模式的特殊消息，需要使用常量定义 key。
     *
     * @param i18nKey 国际化 key (如 ConfigFlowI18n.ERROR_REQUIRED)
     * @return 翻译后的消息
     */
    protected String t(String i18nKey) {
        if (i18n != null) {
            return i18n.t(i18nKey);
        }
        return i18nKey;
    }

    /**
     * 获取自定义消息（带参数）
     * <p>
     * 用于获取无固定模式的特殊消息，支持 ICU MessageFormat 参数替换。
     *
     * @param i18nKey 国际化 key
     * @param args 参数列表
     * @return 翻译后的消息
     */
    protected String t(String i18nKey, Object... args) {
        if (i18n != null) {
            return i18n.t(i18nKey, args);
        }
        return i18nKey;
    }

    /**
     * 第一步处理方法
     *
     * <p>子类必须实现此方法作为流程的入口。
     *
     * @param userInput 用户输入数据（首次进入时为 null）
     * @return 配置流程结果
     */
    protected abstract ConfigFlowResult step_user(Map<String, Object> userInput);
}
