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

import java.util.concurrent.ScheduledExecutorService;

import com.ecat.core.EcatCore;
import com.ecat.core.Device.DeviceRegistry;
import com.ecat.core.I18n.I18nHelper;
import com.ecat.core.I18n.I18nProxy;
import com.ecat.core.Log.LogManager;
import com.ecat.core.State.StateManager;

import com.ecat.core.Utils.LogFactory;

import lombok.Getter;

import com.ecat.core.Utils.Log;

/**
 * Base class for all integrations without device management.
 *
 * @author coffee
 */
public abstract class IntegrationBase implements IntegrationLifecycle {

    protected Log log;

    protected EcatCore core;
    @Getter
    protected IntegrationLoadOption loadOption;
    protected StateManager stateManager;
    protected IntegrationManager integrationManager;
    protected IntegrationRegistry integrationRegistry;
    protected DeviceRegistry deviceRegistry;

    protected final I18nProxy i18n = I18nHelper.createProxy(this.getClass());

    public IntegrationBase() {
        // 初始化日志对象为子类的类名
        log = LogFactory.getLogger(getClass());
    }

    @Override
    public void onLoad(EcatCore core, IntegrationLoadOption loadOption) {
        this.core = core;
        this.loadOption = loadOption;
        this.stateManager = core.getStateManager();
        this.integrationManager = core.getIntegrationManager();
        this.deviceRegistry = core.getDeviceRegistry();
        this.integrationRegistry = core.getIntegrationRegistry();

        // 设置日志上下文，将日志路由到对应集成的日志文件
        String coordinate = loadOption.getIntegrationInfo().getCoordinate();
        Log.setIntegrationContext(coordinate);
        LogManager.getInstance().registerIntegration(coordinate, loadOption.getIntegrationInfo());
    }

    public String getName() {
        return this.getClass().getSimpleName();
    }

    // integration schedule task executor
    public ScheduledExecutorService getScheduledExecutor() {
        return this.core.getTaskManager().getExecutorService();
    }

    @Override
    public void onRelease() {
        // 清除日志上下文
        Log.clearIntegrationContext();

        // 从日志管理器中注销集成
        if (this.loadOption != null && this.loadOption.getIntegrationInfo() != null) {
            String coordinate = this.loadOption.getIntegrationInfo().getCoordinate();
            LogManager.getInstance().unregisterIntegration(coordinate);
        }
    }
}
