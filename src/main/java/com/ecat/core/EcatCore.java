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

import java.util.concurrent.atomic.AtomicBoolean;

import com.ecat.core.Bus.BusRegistry;
import com.ecat.core.ConfigEntry.ConfigEntryRegistry;
import com.ecat.core.ConfigEntry.YmlConfigEntryPersistence;
import com.ecat.core.ConfigFlow.ConfigFlowRegistry;
import com.ecat.core.ConfigFlow.ConfigFlowService;
import com.ecat.core.Device.DeviceRegistry;
import com.ecat.core.Device.YmlDevicePersistence;
import com.ecat.core.I18n.I18nProxy;
import com.ecat.core.I18n.I18nRegistry;
import com.ecat.core.Integration.IntegrationManager;
import com.ecat.core.Integration.IntegrationRegistry;
import com.ecat.core.Log.LogManager;
import com.ecat.core.Observability.BootTraceContext;
import com.ecat.core.Observability.SystemHealthService;
import com.ecat.core.Shutdown.CoreShutdown;
import com.ecat.core.State.StateManager;
import com.ecat.core.Task.TaskManager;
import com.ecat.core.Utils.platform.PlatformInfo;

import lombok.Getter;

/**
 * Class for ecat core main 
 * 
 * @author coffee
 * 
 */
public class EcatCore {
    private static EcatCore instance;

    /** shutdown 只跑一次：hook 与手工调用双入口时幂等（第二次直接返回）。 */
    private final AtomicBoolean shutdownOnce = new AtomicBoolean(false);

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

    /**
     * ConfigEntry 注册器
     */
    @Getter
    private ConfigEntryRegistry configEntryRegistry;

    /**
     * ConfigFlow 注册器
     */
    @Getter
    private ConfigFlowRegistry configFlowRegistry;

    /**
     * 平台自观测（B2 system_health）：执行指标/总线/线程三层内存指标快照，
     * core-api 的 /core-api/system/health 端点从这里读
     */
    @Getter
    private SystemHealthService systemHealth;

    /**
     * ConfigFlow 服务（flow 推进与管理能力）
     * <p>2026-06-22 下沉自 ecat-core-api：REST（controller）与同进程第三方使用集成（import-flow /
     * mqtt / zeroconf discovery 监听器）都经 {@link #getConfigFlowService()} 调本能力，只依赖 ecat-core。
     */
    @Getter
    private ConfigFlowService configFlowService;

    /**
     * 平台信息（OS、架构、JavaCPP classifier）
     */
    @Getter
    private PlatformInfo platformInfo;

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

    /**
     * 获取 ConfigEntry 注册器（便捷方法名）
     */
    public ConfigEntryRegistry getEntryRegistry() {
        return configEntryRegistry;
    }

    /**
     * 获取 ConfigFlow 注册器（便捷方法名）
     */
    public ConfigFlowRegistry getFlowRegistry() {
        return configFlowRegistry;
    }

    // public void setTaskManager(TaskManager taskManager) {
    //     this.taskManager = taskManager;
    // }

    public void init() {
        // boot 作用域（arch-review 25 号杠杆④）：main 线程 MDC 设 boot ULID，
        // 整段启动序列（init + loadIntegrations 的 entry 恢复）共用一个 id，可按代际 grep 分段。
        BootTraceContext.beginBoot();
        platformInfo = PlatformInfo.getInstance();
        i18nProxy = new I18nProxy(Const.CORE_COORDINATE, EcatCore.class, EcatCore.class.getClassLoader());
        integrationRegistry = new IntegrationRegistry();
        busRegistry = new BusRegistry();
        taskManager = new TaskManager();
        systemHealth = new SystemHealthService(busRegistry);
        // StateManager 生产构造：自持 ecat-state-commit 单线程做每秒持久化 commit（IO 型，
        // 见 StateManager 构造注释），不再借道调度引擎
        stateManager = new StateManager(".ecat-data/core/states/");
        configFlowRegistry = new ConfigFlowRegistry();
        configEntryRegistry = new ConfigEntryRegistry(this, new YmlConfigEntryPersistence());
        // flow 推进/管理能力下沉到 core（原在 ecat-core-api）：依赖 integrationRegistry + 两个 registry，均在上方已就绪
        configFlowService = new ConfigFlowService(this);
        integrationManager = new IntegrationManager(this, integrationRegistry, stateManager);
        deviceRegistry = new DeviceRegistry();
        // 00-core：设备持久化 + 启动加载（deviceId 跨重启稳定）。必须在 integrationManager.load（createEntry）之前完成。
        deviceRegistry.setPersistence(new YmlDevicePersistence(".ecat-data/core/devices"));
        deviceRegistry.setBusRegistry(busRegistry);
        deviceRegistry.setEntryRegistry(configEntryRegistry);   // disable 级联 setEnabled(false)（三态承重墙）
        deviceRegistry.load();
        i18nRegistry = I18nRegistry.getInstance();
        
        // 注册 core 日志缓冲区
        LogManager.getInstance().registerIntegration(Const.CORE_COORDINATE, null);
    }

    public void load(){
        integrationManager.loadIntegrations();
    }

    /**
     * 优雅关闭（C2 停机编排）：阶段化收尾——调度引擎停新工作 → 设备型集成 onPause（源先停，
     * 终态事件仍被消费）→ 服务型集成 onPause（总线 drain + 尾批 flush，重启零丢尾主干）→
     * 状态持久化 → onRelease + 线程池收尾。每阶段有界，超时 WARN 不硬等；
     * 顺序依据与预算见 {@link com.ecat.core.Shutdown.CoreShutdown}。
     */
    public void shutdown() {
        if (!shutdownOnce.compareAndSet(false, true)) {
            return;
        }
        // 停机编排段用独立 shutdown ULID 标注（与 boot 段分离），见 BootTraceContext。
        BootTraceContext.runWithShutdownTrace(() ->
            CoreShutdown.forRegistries(taskManager, stateManager, integrationRegistry).run());
    }

    public static void main(String[] args) {
        // 这里可以添加一些初始化逻辑
        EcatCore core = new EcatCore();
        
        core.init();

        EcatCore.setInstance(core);
        // 例如，加载配置文件、注册服务等
        core.load();
        
        System.out.println("EcatCore initialized successfully.");

        // 添加关闭钩子，确保优雅退出（具名：ThreadNamingArchTest 规则 3 立法，
        // 无名 shutdown hook 线程在线程普查（system/health threads）中不可归属）
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("EcatCore is shutting down...");
            core.shutdown();
        }, "ecat-core-shutdown"));

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
