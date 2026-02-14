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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.ecat.core.Const;
import com.ecat.core.Utils.IntegrationCoordinateHelper;

/**
 * I18n helper utility class for creating I18nProxy instances with caching
 *
 * @author coffee
 */
public class I18nHelper {

    private static final Map<String, I18nProxy> proxyCache = new ConcurrentHashMap<>();

    /**
     * Create I18nProxy for a given class
     * Automatically detects coordinate from ClassLoader using IntegrationCoordinateHelper
     *
     * If coordinate cannot be determined, returns null
     */
    public static I18nProxy createProxy(Class<?> clazz) {
        ClassLoader classLoader = clazz.getClassLoader();
        String coordinate = IntegrationCoordinateHelper.getCoordinate(clazz, classLoader);
        if (coordinate == null) {
            return null;
        }

        return createProxy(coordinate, clazz);
    }

    /**
     * Create I18nProxy for core module
     */
    public static I18nProxy createCoreProxy() {
        return createProxy(Const.CORE_COORDINATE, I18nHelper.class);
    }

    /**
     * Create I18nProxy with explicit coordinate
     * @param coordinate groupId:artifactId format (e.g., "com.ecat:ecat-core" or "com.ecat:integration-xxx")
     */
    public static I18nProxy createProxy(String coordinate, Class<?> clazz) {
        // Use cached proxy if available
        I18nProxy cachedProxy = proxyCache.get(coordinate);
        if (cachedProxy != null) {
            return cachedProxy;
        }
        ClassLoader classLoader = clazz.getClassLoader();

        // Create new proxy and cache it
        I18nProxy proxy = new I18nProxy(coordinate, clazz, classLoader);
        proxyCache.put(coordinate, proxy);

        return proxy;
    }

    /**
     * Get global translation (for Ecat Core class use only)
     * @apiNote This method is only for Ecat Core classes to avoid circular dependency.
     */
    public static String t(String key) {
        return createCoreProxy().t(key);
    }

    /**
     * Get global translation with parameters (for Ecat Core class use only)
     * @apiNote This method is only for Ecat Core classes to avoid circular dependency.
     */
    public static String t(String key, Object... params) {
        return createCoreProxy().t(key, params);
    }

    /**
     * Get translation by key with named parameters (ICU4J Map support)
     * @apiNote This method is only for Ecat Core classes to avoid circular dependency.
     */
    public String t(String key, Map<String, Object> params) {
        return createCoreProxy().t(key, params);
    }

    /**
     * Set global locale
     */
    public static void setLocale(String languageTag) {
        I18nRegistry.getInstance().setGlobalLocale(java.util.Locale.forLanguageTag(languageTag));
    }

    /**
     * Set global locale
     */
    public static void setLocale(java.util.Locale locale) {
        I18nRegistry.getInstance().setGlobalLocale(locale);
    }

    /**
     * Get current locale
     */
    public static java.util.Locale getCurrentLocale() {
        return I18nRegistry.getInstance().getCurrentLocale();
    }

    /**
     * Clear all cached proxies
     */
    public static void clearCache() {
        proxyCache.clear();
    }

    /**
     * Get cached proxy count
     */
    public static int getCachedProxyCount() {
        return proxyCache.size();
    }

    /**
     * Check if proxy is cached for coordinate
     * @param coordinate groupId:artifactId format
     */
    public static boolean isProxyCached(String coordinate) {
        return proxyCache.containsKey(coordinate);
    }

    /**
     * Remove cached proxy for coordinate
     * @param coordinate groupId:artifactId format
     */
    public static void removeCachedProxy(String coordinate) {
        I18nProxy proxy = proxyCache.remove(coordinate);
        if (proxy != null) {
            I18nRegistry.getInstance().unregisterProxy(coordinate);
        }
    }

    /**
     * Get all cached coordinates
     */
    public static Map<String, I18nProxy> getCachedProxies() {
        return new HashMap<>(proxyCache);
    }

    /**
     * Get cached proxy for coordinate
     * @param coordinate groupId:artifactId format
     * @return I18nProxy or null if not cached
     */
    public static I18nProxy getCachedProxy(String coordinate) {
        return proxyCache.getOrDefault(coordinate, null);
    }
}
