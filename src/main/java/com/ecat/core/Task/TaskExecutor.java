package com.ecat.core.Task;

import java.util.ArrayList;
import java.util.List;

import com.ecat.core.I18n.I18nHelper;
import com.ecat.core.Integration.IntegrationBase;

import lombok.Getter;

/**
 * TaskExecutor interface defines the methods for executing tasks within the ecat core integration.
 * * It provides methods to retrieve a task and retrieve a list of available task list.
 * 
 * @Author coffee
 * 
 * @implNote
 * integration should use this subclass to implement task execution logic.
 * 
 * 
 * @example
 * <pre>
 * //获取任务执行器实例
 * TaskExecutor taskExecutor = XXXXTaskIntegration.getTaskExecutor();
 * 
 * // 获取特定任务的配置定义
 * ConfigDefinition config = taskExecutor.getConfigDefinition("taskName");
 * 
 * 
 * // 执行任务
 * taskExecutor.execute("taskName", parameters);
 * // 获取所有任务名称
 * List<TaskInfo> tasks = taskExecutor.getTasks();
 * </pre>
 */
public class TaskExecutor {

    @Getter
    private List<Task> tasks = new ArrayList<>();

    @Getter
    private IntegrationBase integration;

    public TaskExecutor(IntegrationBase integration) {
        if (integration == null) {
            throw new IllegalArgumentException(I18nHelper.t("error.integration_cannot_be_null"));
        }
        this.integration = integration;
    }

    public void addTask(Task task) {
        if (task == null) {
            throw new IllegalArgumentException(I18nHelper.t("error.task_cannot_be_null"));
        }
        tasks.add(task);
    }

    /**
     * 获取任务对象
     * @param taskName
     * @return
     */
    public Task getTask(String taskName){
        for (Task task : tasks) {
            if (task.getTaskName().equals(taskName)) {
                return task;
            }
        }
        return null;
    }

}
