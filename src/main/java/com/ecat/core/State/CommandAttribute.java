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

package com.ecat.core.State;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import lombok.Getter;
import com.ecat.core.I18n.I18nKeyPath;
import com.ecat.core.Utils.DynamicConfig.ConfigDefinition;
import com.ecat.core.Utils.DynamicConfig.ConfigItem;
import com.ecat.core.Utils.DynamicConfig.ConfigItemBuilder;
import com.ecat.core.Utils.DynamicConfig.Validator.DynamicStringDictValidator;

/** 
 * Command pattern attribute base class
 * 
 * @apiNote displayName i18n supported, path: state.command_attr.{attributeID}
 * @apiNote commands i18n supported, path: state.command_attr.commands.{command}
 * 
 * @implNote this class just provide readonly access to outside, outside can't inherit this class directly.
 * @implNote only the derived class in this package can inherit this class.
 * 
 * @see StringCommandAttribute
 * 
 * @author coffee
 */ 
abstract class CommandAttribute<T> extends AttributeBase<T> {

    /**
     * 命令列表
     * 内容为非空且不重复，需要使用能够标识性的值，比如英文字符串
     * 作为sendCommand的输入参数
     */
    @Getter
    protected List<T> commands;

    // 依赖属性列表，用于命令下发时要用到哪些属性的值
    @Getter
    protected List<AttributeBase<?>> dependencyAttributes = new ArrayList<>();

    protected ConfigDefinition valueDef;        // 验证定义
    protected Map<T, String> commandCache = new HashMap<>();      // 命令缓存 

    /**
     * 支持I18n的构造函数
     */
    public CommandAttribute(String attributeID, AttributeClass attrClass) {
        this(attributeID, attrClass, null, null);
    }

    /**
     * 同时支持用户设置显示名称displayName和I18n的构造函数，displayName优先级高
     */
    public CommandAttribute(String attributeID, String displayName, AttributeClass attrClass) {
        this(attributeID, displayName, attrClass, null, null);
    }

    /**
     * 支持I18n的构造函数
     */
    public CommandAttribute(String attributeID, AttributeClass attrClass, List<T> commands,
            Function<AttrChangedCallbackParams<T>, CompletableFuture<Boolean>> onChangedCallback) {
        super(attributeID, attrClass, null, null, 0, false, true, onChangedCallback);
        setCommands(commands);
        this.valueDef = getValueDefinition(); // 初始化验证定义
    }

    /**
     * 同时支持用户设置显示名称displayName和I18n的构造函数，displayName优先级高
     */
    public CommandAttribute(String attributeID, String displayName, AttributeClass attrClass, List<T> commands,
            Function<AttrChangedCallbackParams<T>, CompletableFuture<Boolean>> onChangedCallback) {
        super(attributeID, displayName, attrClass, null, null, 0, false, true, onChangedCallback);
        setCommands(commands);
        this.valueDef = getValueDefinition(); // 初始化验证定义
    }

    /**
     * 设置命令列表
     * @param commands 命令列表，需要使用能够标识性的值，比如英文字符串
     * @return 设置是否成功
     */
    protected Boolean setCommands(List<T> commands) {
        if (commands == null || commands.isEmpty()) {
            return false;
        }
        this.commands = commands;
        clearCommandCache(); // 清除命令缓存
        return true;
    }

    /**
     * Get last command
     * @return
     *       value: last command
     *
     */
    public T getLastCommand() {
        return value;
    }

    /**
     * just for automation using, not for user uesd to select option
     */
    @Override
    protected CompletableFuture<Boolean> setDisplayValueImp(T cmd, UnitInfo fromUnit){
        return sendCommand(cmd);
    }

    @Override
    protected T convertFromUnitImp(T value, UnitInfo fromUnit) {
        // CommandAttribute does not require unit conversion
        return value;
    }

    /**
     * 子类实现具体的命令下发逻辑
     * @param cmd
     * @return
     *        true: 命令下发成功
     *       false: 命令下发失败
     */
    protected abstract CompletableFuture<Boolean> sendCommandImpl(T cmd);

    /**
     * Send command to device, and update the value(last command) selfly
     * @param cmd command to send， must in commands list
     * @return
     *        true: 命令下发成功
     *       false: 命令下发失败
     */
    public CompletableFuture<Boolean> sendCommand(T cmd){
        if(!valueChangeable){
            return CompletableFuture.completedFuture(false);
        }

        // 验证命令是否在有效列表中
        if(!commands.contains(cmd)){
            log.error("命令 {} 不在有效命令列表中: {}", cmd, commands);
            return CompletableFuture.completedFuture(false);
        }

        return sendCommandImpl(cmd).thenApply(result -> {
            if(result){
                // 写入成功，更新属性状态
                setValue(cmd);
                publicState();  // 发布状态变更
                log.info("Device " + getDevice().getId() + " - Send Command Successed: " + getValue() +
                        " (" + getCurrentCommandI18nName() + ")");
            }
            return result;
        }).exceptionally(e -> {
            log.error("Device " + getDevice().getId() + " - Send Command Failed: " + e.getMessage());
            return false;
        });
    }

    /**
     * 添加依赖属性
     * @param dependencyAttribute
     *        依赖属性
     */
    public void addDependencyAttribute(AttributeBase<?> dependencyAttribute) {
        if (dependencyAttribute != null && !dependencyAttributes.contains(dependencyAttribute)) {
            dependencyAttributes.add(dependencyAttribute);
        }
    }

    @Override
    public ConfigDefinition getValueDefinition() {
        if (valueDef == null) {
            valueDef = new ConfigDefinition();

            // 创建动态字典验证器
            // 创建动态字符串字典验证器（参考 00-validator-classes-reference.md）
            DynamicStringDictValidator valueValidator =
                new DynamicStringDictValidator(() -> {
                    Map<T, String> originalDict = getOptionDict();
                    Map<String, String> stringDict = new HashMap<>();
                    if (originalDict != null) {
                        for (Map.Entry<T, String> entry : originalDict.entrySet()) {
                            stringDict.put(entry.getKey().toString(), entry.getValue());
                        }
                    }
                    return stringDict;
                });

            ConfigItemBuilder builder =
                new ConfigItemBuilder()
                    .add(new ConfigItem<>("value", String.class, true, null, valueValidator));

            valueDef.define(builder);
        }
        return valueDef;
    }

    /**
     * 获取命令的国际化路径前缀
     * 约定使用后缀"_commands"，例如：devices.qc_device.device_command_commands
     *
     * @return I18nKeyPath 命令的国际化路径前缀
     */
    public I18nKeyPath getI18nCommandPathPrefix() {
        return getI18nDispNamePath().withSuffix("_commands");
    }

    @Override
    public I18nKeyPath getI18nPrefixPath() {
        return new I18nKeyPath("state.command_attr.", "");
    }

    /**
     * 获取命令字典（k-v 结构）
     * k 为命令值，v 为国际化显示名称
     * @return 命令字典
     */
    public abstract Map<T, String> getOptionDict();

    /**
     * 获取命令的国际化显示名称
     * @param command 命令值
     * @return 国际化显示名称
     * @apiNote path: getI18nCommandPathPrefix().getFullPath().{commandLowerCase}
     */
    public String getCommandI18nName(T command) {
        return i18n.t(getI18nCommandPathPrefix().getFullPath() + "." + command.toString().toLowerCase(Locale.ENGLISH));
    }

    /**
     * 获取当前命令的国际化显示名称
     * @return 当前命令的国际化显示名称
     */
    public String getCurrentCommandI18nName() {
        T currentCommand = getValue();
        return currentCommand != null ? getCommandI18nName(currentCommand) : "";
    }

    /**
     * 默认的命令字典实现
     * 子类可以重写此方法提供自定义实现
     */
    protected Map<T, String> getDefaultCommandDict() {
        Map<T, String> dict = new HashMap<>();
        if (commands != null) {
            for (T command : commands) {
                dict.put(command, getCommandI18nName(command));
            }
        }
        return dict;
    }

    /**
     * 获取所有命令的显示名称列表
     * @return 显示名称列表
     */
    public List<String> getCommandDisplayNames() {
        List<String> displayNames = new ArrayList<>();
        if (commands != null) {
            for (T command : commands) {
                displayNames.add(getCommandI18nName(command));
            }
        }
        return displayNames;
    }

    /**
     * 根据显示名称查找命令值
     * @param displayName 显示名称
     * @return 命令值，如果找不到则返回 null
     */
    public T getCommandByDisplayName(String displayName) {
        Map<T, String> commandDict = getOptionDict();
        if (commandDict != null) {
            for (Map.Entry<T, String> entry : commandDict.entrySet()) {
                if (entry.getValue().equals(displayName)) {
                    return entry.getKey();
                }
            }
        }
        return null;
    }

    /**
     * 检查命令是否有效
     * @param command 命令值
     * @return 是否有效
     */
    public boolean isValidCommand(T command) {
        return commands != null && commands.contains(command);
    }

    /**
     * 清除命令缓存
     */
    private void clearCommandCache() {
        commandCache.clear();
    }

    /**
     * 异步执行命令（支持字符串输入）
     * @param commandText 命令文本
     * @return 执行结果
     */
    public CompletableFuture<Boolean> sendCommandByText(String commandText) {
        T command = parseCommandText(commandText);
        if (command == null) {
            return CompletableFuture.completedFuture(false);
        }
        return sendCommand(command);
    }

    /**
     * 解析命令文本
     * @param commandText 命令文本
     * @return 命令值，如果无法解析则返回 null
     */
    public T parseCommandText(String commandText) {
        if (commandText == null || commandText.trim().isEmpty()) {
            return null;
        }

        // 尝试根据显示名称查找命令
        T command = getCommandByDisplayName(commandText.trim());
        if (command != null) {
            return command;
        }

        // 尝试直接解析命令值（子类需要实现具体的解析逻辑）
        return parseCommandValue(commandText.trim());
    }

    /**
     * 解析命令值（子类需要实现）
     * @param commandText 命令文本
     * @return 命令值，如果无法解析则返回 null
     */
    protected abstract T parseCommandValue(String commandText);

}
