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

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

import com.ecat.core.I18n.I18nKeyPath;

/** 
 * 适用于远程命令下发等单次事件的属性
 * 适合使用IO处理的场景
 * 前端显示样式为命令按钮+点击弹框选择命令+确认+提示+弹框收回=命令发送
 * 
 * @apiNote displayName i18n supported, path: state.string_command_attr.{attributeID}
 * @apiNote commands i18n supported, path: state.string_command_attr.{attributeID}_commands.{command}
 *
 * @implSpec 继承该类需要实现sendCommandImpl方法，完成具体的命令下发逻辑
 *
 * @author coffee
 */ 
public abstract class StringCommandAttribute extends CommandAttribute<String> {

    /**
     * 支持I18n的构造函数
     */
    public StringCommandAttribute(String attributeID, AttributeClass attrClass) {
        super(attributeID, attrClass);
    }

    /**
     * 同时支持用户设置显示名称displayName和I18n的构造函数，displayName优先级高
     */
    public StringCommandAttribute(String attributeID, String displayName, AttributeClass attrClass) {
        super(attributeID, displayName, attrClass);
    }

    /**
     * 支持I18n的构造函数
     */
    public StringCommandAttribute(String attributeID, AttributeClass attrClass, List<String> commands,
            Function<AttrChangedCallbackParams<String>, CompletableFuture<Boolean>> onChangedCallback) {
        super(attributeID, attrClass, commands, onChangedCallback);
    }

    /**
     * 同时支持用户设置显示名称displayName和I18n的构造函数，displayName优先级高
     */
    public StringCommandAttribute(String attributeID, String displayName, AttributeClass attrClass, List<String> commands,
            Function<AttrChangedCallbackParams<String>, CompletableFuture<Boolean>> onChangedCallback) {
        super(attributeID, displayName, attrClass, commands, onChangedCallback);
    }

    @Override
    public String getDisplayValue(UnitInfo toUnit) {
        if (value == null) {
            return null;
        }
        return getCommandI18nName(value);
    }

    @Override
    public String getI18nValue(UnitInfo toUnit){
        return value.toLowerCase(Locale.ENGLISH);
    }

    @Override
    public I18nKeyPath getI18nPrefixPath() {
        return new I18nKeyPath("state.string_command_attr.", "");
    }

    @Override
    public Map<String, String> getOptionDict() {
        Map<String, String> dict = new HashMap<>();
        if (commands != null) {
            for (String command : commands) {
                dict.put(command, getCommandI18nName(command));
            }
        }
        return dict;
    }

    @Override
    protected String parseCommandValue(String commandText) {
        // 查找匹配的命令
        if (commands != null) {
            for (String command : commands) {
                if (command.equalsIgnoreCase(commandText)) {
                    return command;
                }
            }
        }
        return null;
    }

    /**
     * 子类实现具体的命令下发逻辑
     * @param cmd
     * @return
     *        true: 命令下发成功
     *       false: 命令下发失败
     */
    protected abstract CompletableFuture<Boolean> sendCommandImpl(String cmd);

    @Override
    public AttributeType getAttributeType() {
        return AttributeType.STRING_COMMAND;
    }

}
