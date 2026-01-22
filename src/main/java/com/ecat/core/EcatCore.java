package com.ecat.core;

// import java.lang.reflect.InvocationTargetException;
// import java.lang.reflect.Method;

// import org.springframework.beans.factory.support.AbstractBeanDefinition;
// import org.springframework.beans.factory.support.BeanDefinitionBuilder;
// import org.springframework.beans.factory.support.DefaultListableBeanFactory;
// import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.context.ApplicationContext;
// import org.springframework.stereotype.Component;
// import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import com.ecat.core.Bus.BusRegistry;
import com.ecat.core.Device.DeviceRegistry;
import com.ecat.core.I18n.I18nProxy;
import com.ecat.core.I18n.I18nRegistry;
import com.ecat.core.Integration.IntegrationManager;
import com.ecat.core.Integration.IntegrationRegistry;
import com.ecat.core.State.StateManager;
import com.ecat.core.Task.TaskManager;

import lombok.Getter;

// @Component
public class EcatCore {
    private static EcatCore instance;
    
    public static EcatCore getInstance() {
        return instance;
    }
    
    public static void setInstance(EcatCore core) {
        instance = core;
    }

    // @Autowired
    // private RequestMappingHandlerMapping requestMappingHandlerMapping;
    
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

    // public ApplicationContext getApplicationContext() {
    //     return applicationContext;
    // }

    // public <T> T getSpringBean(String name, Class<T> requiredType) {
    //     return applicationContext.getBean(name, requiredType);
    // }
    // public <T> T getSpringBean(Class<T> requiredType) {
    //     return applicationContext.getBean(requiredType);
    // }

    // public boolean checkSpringBean(String beanName) {
    //     return applicationContext.containsBean(beanName);
    // }

    // public void registeRestController(String beanName, Object restController) {
    //     if(checkSpringBean(beanName)) {
    //         throw new RuntimeException("Bean name already exists: " + beanName);
    //     }
    //     DefaultListableBeanFactory beanFactory = (DefaultListableBeanFactory) applicationContext.getAutowireCapableBeanFactory();
    //     // 2.2交给spring管理
    //     BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(beanName);
    //     AbstractBeanDefinition beanDefinition = builder.getBeanDefinition();
    //     // 2.3注册到spring的beanFactory中
    //     beanFactory.registerBeanDefinition(beanName, beanDefinition);
    //     // 2.4允许注入和反向注入
    //     beanFactory.autowireBean(restController);
    //     beanFactory.initializeBean(restController, beanName);
    //     // 2.5手动构建实例，并注入base service 防止卸载之后不再生成
    //     // Object obj = clazz.newInstance();
    //     beanFactory.registerSingleton(beanName, restController);

    //     _registeRestController("Controller", beanName);

    //     // applicationContext.getAutowireCapableBeanFactory().autowireBean(restController);
    //     // applicationContext.getAutowireCapableBeanFactory().initializeBean(restController, beanName);

    //     // // 手动注册 RequestMapping
    //     // requestMappingHandlerMapping = applicationContext.getBean(RequestMappingHandlerMapping.class);
    //     // requestMappingHandlerMapping.detectHandlerMethods(restController);
    // }

    // /**
    //  * 处理类
    //  * @param type 类型标识
    //  * @param name bean名称
    //  */
    // private  void _registeRestController(String type ,String name){
    //     //这里只做了contrller类型标识的处理
    //     if(type.equals("Controller")){
    //         // RequestMappingHandlerMapping handlerMapping = getSpringBean(RequestMappingHandlerMapping.class);
    //         RequestMappingHandlerMapping handlerMapping = applicationContext.getBean(RequestMappingHandlerMapping.class);
    //         // 注册Controller
    //         Method method = null;
    //         try {
    //             method = handlerMapping.getClass().getSuperclass().getSuperclass().
    //                     getDeclaredMethod("detectHandlerMethods", Object.class);
    //         } catch (NoSuchMethodException e) {
    //             e.printStackTrace();
    //         }
    //         // 将private改为可使用
    //         assert method != null;
    //         method.setAccessible(true);
    //         try {
    //             method.invoke(handlerMapping, name);
    //         } catch (IllegalAccessException | InvocationTargetException e) {
    //             e.printStackTrace();
    //         }
    //     }

    // }

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
