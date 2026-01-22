package com.ecat.core.Integration;

import java.util.concurrent.ScheduledExecutorService;

import com.ecat.core.EcatCore;
import com.ecat.core.Device.DeviceRegistry;
import com.ecat.core.I18n.I18nHelper;
import com.ecat.core.I18n.I18nProxy;
import com.ecat.core.State.StateManager;

import com.ecat.core.Utils.LogFactory;

import lombok.Getter;

import com.ecat.core.Utils.Log;

/** 
 * Base class for all integrations without device management.
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
    }
    
    public String getName() {
        return this.getClass().getSimpleName();
    }

    // integration schedule task executor
    public ScheduledExecutorService getScheduledExecutor() {
        return this.core.getTaskManager().getExecutorService();
    }
}
