package com.ecat.core.Integration;

import com.ecat.core.Task.TaskExecutor;

/**
 * 任务类集成接口定义, IIntegrationTaskManagement接口定义了任务执行器的获取方法。
 * 
 * @author coffee
 * 
 * @example
 * <pre>
 *  // 继承
 *  public class MyIntegration extends IntegrationBase implements IIntegrationTaskManagement
 * 
 *  // 获取集成任务管理实例
 *  IIntegrationTaskManagement taskManagement = (IIntegrationTaskManagement)XXXXXIntegation; 
 *  TaskExecutor executor = taskManagement.getTaskExecutor(); // 获取任务执行器
 * 
 *  // 使用任务执行器获取任务
 *  executor.getTask("taskName").execute(parameters); // 执行任务
 * </pre>
 * 
 * @see TaskExecutor
 */
public interface IIntegrationTaskManagement {
    
    TaskExecutor getTaskExecutor();

}
