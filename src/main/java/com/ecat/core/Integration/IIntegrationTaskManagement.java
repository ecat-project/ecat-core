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
