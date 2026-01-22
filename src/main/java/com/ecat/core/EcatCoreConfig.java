package com.ecat.core;


// @Configuration
public class EcatCoreConfig {

    private EcatCore core;
    // @Bean(name = "ecatCore")
    public EcatCore ecatCore() {
        core = new EcatCore();
        core.init();
        return core;
    }

    // EcatCore used in Springboot likes:
    // @Autowired
    // private EcatCore core;
    // core.getDeviceRegistry().getDevice("deviceName");
}
