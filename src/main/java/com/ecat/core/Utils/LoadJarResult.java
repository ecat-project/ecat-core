package com.ecat.core.Utils;

import java.net.URLClassLoader;

import com.ecat.core.Integration.IntegrationBase;

import lombok.Getter;

/**
 * LoadJarResult is a class that encapsulates the result of loading a jar file.
 * It contains the loaded integration instance and the custom class loader used for loading.
 */
public class LoadJarResult {
    @Getter
    private IntegrationBase integration;
    @Getter
    private URLClassLoader classLoader;

    public LoadJarResult(IntegrationBase integration, URLClassLoader classLoader) {
        this.integration = integration;
        this.classLoader = classLoader;
    }
}
