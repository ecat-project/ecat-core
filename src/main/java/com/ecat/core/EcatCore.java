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

package com.ecat.core;

import com.ecat.core.Bus.BusRegistry;
import com.ecat.core.Device.DeviceRegistry;
import com.ecat.core.I18n.I18nProxy;
import com.ecat.core.I18n.I18nRegistry;
import com.ecat.core.Integration.IntegrationManager;
import com.ecat.core.Integration.IntegrationRegistry;
import com.ecat.core.State.StateManager;
import com.ecat.core.Task.TaskManager;

import lombok.Getter;

/**
 * Class for ecat core main 
 * 
 * @author coffee
 * 
 */
public class EcatCore {
    private static EcatCore instance;
    
    public static EcatCore getInstance() {
        return instance;
    }
    
    public static void setInstance(EcatCore core) {
        instance = core;
    }
    
    private IntegrationRegistry integrationRegistry;
    private BusRegistry busRegistry;
    private StateManager stateManager;
    private IntegrationManager integrationManager;
    private TaskManager taskManager;
    @Getter
    private DeviceRegistry deviceRegistry;
    @Getter
    private I18nRegistry i18nRegistry;

    // core i18n proxy
    @Getter
    private I18nProxy i18nProxy;

    public IntegrationRegistry getIntegrationRegistry() {
        return integrationRegistry;
    }

    // public void setIntegrationRegistry(IntegrationRegistry integrationRegistry) {
    //     this.integrationRegistry = integrationRegistry;
    // }

    public BusRegistry getBusRegistry() {
        return busRegistry;
    }

    // public void setBusRegistry(BusRegistry busRegistry) {
    //     this.busRegistry = busRegistry;
    // }

    public StateManager getStateManager() {
        return stateManager;
    }

    // public void setStateManager(StateManager stateManager) {
    //     this.stateManager = stateManager;
    // }

    public IntegrationManager getIntegrationManager() {
        return integrationManager;
    }

    // public void setIntegrationManager(IntegrationManager integrationManager) {
    //     this.integrationManager = integrationManager;
    // }

    public TaskManager getTaskManager() {
        return taskManager;
    }

    // public void setTaskManager(TaskManager taskManager) {
    //     this.taskManager = taskManager;
    // }

    public void init() {
        i18nProxy = new I18nProxy(Const.CORE_ARTIFACT_ID, EcatCore.class, EcatCore.class.getClassLoader());
        integrationRegistry = new IntegrationRegistry();
        busRegistry = new BusRegistry();
        stateManager = new StateManager();
        taskManager = new TaskManager();
        integrationManager = new IntegrationManager(this, integrationRegistry, stateManager);
        integrationManager.loadIntegrations();
        deviceRegistry = new DeviceRegistry();
        i18nRegistry = I18nRegistry.getInstance();
    }

    public static void main(String[] args) {
        // 这里可以添加一些初始化逻辑
        EcatCore core = new EcatCore();

        // core.setApplicationContext(null);
        core.init();
        EcatCore.setInstance(core);
        // 例如，加载配置文件、注册服务等
        System.out.println("EcatCore initialized successfully.");

        // 添加关闭钩子，确保优雅退出
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("EcatCore is shutting down...");
        }));

        // 保持运行，直到收到终止信号
        try {
            // 使用 CountDownLatch 保持运行
            java.util.concurrent.CountDownLatch latch = new java.util.concurrent.CountDownLatch(1);
            latch.await();
        } catch (InterruptedException e) {
            System.out.println("EcatCore interrupted, shutting down...");
            Thread.currentThread().interrupt();
        }
    }
}
