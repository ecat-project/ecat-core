package com.ecat.core.I18n;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

import com.ecat.core.Const;

/**
 * I18n registry for managing I18nProxy instances and global locale
 *
 * @author coffee
 */
public class I18nRegistry {

    private static I18nRegistry instance;
    private final Map<String, I18nProxy> proxyMap = new HashMap<>();
    private final Map<String, Map<String, Object>> allResources = new HashMap<>();

    private I18nRegistry() {
    }

    public static synchronized I18nRegistry getInstance() {
        if (instance == null) {
            instance = new I18nRegistry();
        }
        return instance;
    }

    /**
     * Register an I18nProxy instance
     */
    public void registerProxy(String artifactId, I18nProxy proxy) {
        proxyMap.put(artifactId, proxy);
    }

    /**
     * Get an I18nProxy instance by artifactId
     */
    public I18nProxy getProxy(String artifactId) {
        return proxyMap.get(artifactId);
    }

    /**
     * Unregister and remove an I18nProxy instance
     */
    public void unregisterProxy(String artifactId) {
        I18nProxy proxy = proxyMap.remove(artifactId);
        if (proxy != null) {
            allResources.remove(proxy.getNamespace());
        }
    }

    /**
     * Add or update resources for a specific namespace
     */
    public void updateResources(String namespace, Map<String, Object> resources) {
        allResources.put(namespace, resources);
    }

    /**
     * Get resources for a specific namespace
     */
    public Map<String, Object> getResources(String namespace) {
        return allResources.get(namespace);
    }

    /**
     * Get all resources
     */
    public Map<String, Map<String, Object>> getAllResources() {
        return new HashMap<>(allResources);
    }

    /**
     * Set global locale and notify all proxies to reload resources
     */
    public void setGlobalLocale(Locale locale) {
        I18nConfig.getInstance().setCurrentLocale(locale);
        // Notify all proxies to reload their resources
        for (I18nProxy proxy : proxyMap.values()) {
            proxy.reloadResources();
        }
    }

    /**
     * Get current global locale
     */
    public Locale getCurrentLocale() {
        return I18nConfig.getInstance().getCurrentLocale();
    }

    /**
     * Get translation by key with fallback mechanism
     */
    public Object getTranslation(String namespace, String key) {
        Map<String, Object> namespaceResources = allResources.get(namespace);
        if (namespaceResources != null) {
            Object value = getNestedValue(namespaceResources, key);
            if (value != null) {
                return value;
            }
        }

        // Fallback to core namespace
        if (!Const.CORE_ARTIFACT_ID.equals(namespace)) {
            Map<String, Object> coreResources = allResources.get(Const.CORE_ARTIFACT_ID);
            if (coreResources != null) {
                return getNestedValue(coreResources, key);
            }
        }

        return null;
    }

    /**
     * Get nested value from map using dot notation
     */
    @SuppressWarnings("unchecked")
    private Object getNestedValue(Map<String, Object> map, String key) {
        String[] parts = key.split("\\.");
        Object current = map;

        for (String part : parts) {
            if (current instanceof Map) {
                current = ((Map<String, Object>) current).get(part);
                if (current == null) {
                    return null;
                }
            } else {
                return null;
            }
        }

        return current;
    }

    /**
     * Clear all registered proxies and resources
     */
    public void clear() {
        proxyMap.clear();
        allResources.clear();
    }
}
