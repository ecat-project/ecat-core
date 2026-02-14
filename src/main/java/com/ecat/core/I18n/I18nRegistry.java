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
     * @param coordinate groupId:artifactId format
     */
    public void registerProxy(String coordinate, I18nProxy proxy) {
        proxyMap.put(coordinate, proxy);
    }

    /**
     * Get an I18nProxy instance by coordinate
     * @param coordinate groupId:artifactId format
     */
    public I18nProxy getProxy(String coordinate) {
        return proxyMap.get(coordinate);
    }

    /**
     * Unregister and remove an I18nProxy instance
     * @param coordinate groupId:artifactId format
     */
    public void unregisterProxy(String coordinate) {
        I18nProxy proxy = proxyMap.remove(coordinate);
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
        if (!Const.CORE_COORDINATE.equals(namespace)) {
            Map<String, Object> coreResources = allResources.get(Const.CORE_COORDINATE);
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
