package com.ecat.core.Task;

import java.util.Map;

import com.ecat.core.Utils.DynamicConfig.ConfigDefinition;

import lombok.Getter;
import lombok.Setter;

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
