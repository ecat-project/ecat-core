package com.ecat.core.Integration;

import java.net.URLClassLoader;

import lombok.Getter;
import lombok.Setter;


/**
 * IntegrationLoadOption encapsulates the options for loading an integration.
 * It includes the class loader used for loading the integration and a child class loader.
 * 
 * @author coffee
 */
public class IntegrationLoadOption {
    @Getter
    /**
     * The class loader used for loading the integration.
     */
    private URLClassLoader classLoader;

    @Getter
    @Setter
    /**
     * The child class loader for this integration.
     */
    private URLClassLoader childClassLoader;

    public IntegrationLoadOption(URLClassLoader classLoader) {
        this.classLoader = classLoader;
    }
    
}
