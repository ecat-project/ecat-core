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

package com.ecat.core.Task;

import java.util.Map;

import com.ecat.core.Utils.DynamicConfig.ConfigDefinition;

import lombok.Getter;

/**
 * TaskInfo class represents metadata about a task in the ecat core framework.
 * It contains the task name, description, and task execute function call.
 * 
 * @implNote
 * integration should use this subclass to implement task execution logic.
 * 
 * @Author coffee
 */
public abstract class Task {

    @Getter
    Map<String, Object> lastParameters;


    public abstract String getTaskName();

    public abstract String getDescription();

    /**
     * 执行任务
     * @param parameters 任务参数
     */
    public void execute(Map<String, Object> parameters) {
        if(getConfigDefinition() != null ){
            // 验证参数
            ConfigDefinition configDefinition = getConfigDefinition();
            if (!configDefinition.validateConfig(parameters)) {
                throw new IllegalArgumentException("Invalid parameters: " + configDefinition.getInvalidConfigItems());
            }
        }
        
        this.lastParameters = parameters;
        executeImpl(parameters);
    }

    /**
     * 获取任务配置定义
     * @return
     */
    protected abstract ConfigDefinition getConfigDefinition();

    /**
     * 执行任务的具体实现方法
     * @param parameters
     */
    protected abstract void executeImpl(Map<String, Object> parameters);

}
