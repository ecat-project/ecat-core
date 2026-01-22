package com.ecat.core.I18n;

import com.ecat.core.Const;
import com.ibm.icu.text.MessageFormat;
import com.ibm.icu.text.PluralFormat;
import com.ibm.icu.text.PluralRules;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * I18n proxy class for integration-specific resource isolation and caching
 *
 * @author coffee
 */
public class I18nProxy {

    private static final I18nRegistry REGISTRY = I18nRegistry.getInstance();

    private final String artifactId;
    private final String namespace;
    private final ClassLoader classLoader;
    private final ResourceLoader resourceLoader;
    private final Class<?> clazz;
    private final Map<Locale, Map<String, Object>> cache = new HashMap<>();

    /**
     * Create I18nProxy for a specific integration
     */
    public I18nProxy(String artifactId, Class<?> clazz, ClassLoader classLoader) {
        this.artifactId = artifactId;
        this.clazz = clazz;
        this.namespace = Const.CORE_ARTIFACT_ID.equals(artifactId) ? Const.CORE_ARTIFACT_ID : "integration." + artifactId;
        this.classLoader = classLoader;
        this.resourceLoader = new ResourceLoader(this.clazz, classLoader);

        // Register this proxy with the registry
        REGISTRY.registerProxy(artifactId, this);

        // Load initial resources
        reloadResources();
    }

    /**
     * Get the artifactId
     */
    public String getArtifactId() {
        return artifactId;
    }

    /**
     * Get the namespace
     */
    public String getNamespace() {
        return namespace;
    }

    /**
     * Get the class loader
     */
    public ClassLoader getClassLoader() {
        return classLoader;
    }

    /**
     * Reload resources for current locale
     */
    public void reloadResources() {
        Locale currentLocale = I18nConfig.getInstance().getCurrentLocale();
        Map<String, Object> resources = resourceLoader.loadResources(currentLocale);
        cache.put(currentLocale, resources);
        REGISTRY.updateResources(namespace, resources);
    }

    /**
     * Get translation by key
     */
    public String t(String key) {
        return t(key, (Object[]) null);
    }

    /**
     * Get translation by key with parameters
     */
    public String t(String key, Object... params) {
        Locale currentLocale = I18nConfig.getInstance().getCurrentLocale();
        Map<String, Object> resources = cache.get(currentLocale);

        if (resources == null) {
            resources = resourceLoader.loadResources(currentLocale);
            cache.put(currentLocale, resources);
            I18nRegistry.getInstance().updateResources(namespace, resources);
        }

        Object value = getNestedValue(resources, key);
        if (value == null) {
            // Fallback to registry
            value = REGISTRY.getTranslation(namespace, key);
            if (value == null) {
                return key; // Return key if not found
            }
        }

        String message = value.toString();

        if (params != null && params.length > 0) {
            return formatMessage(message, params, currentLocale);
        }

        return message;
    }

    /**
     * Get translation by key with named parameters (ICU4J Map support)
     */
    public String t(String key, Map<String, Object> params) {
        Locale currentLocale = I18nConfig.getInstance().getCurrentLocale();
        Map<String, Object> resources = cache.get(currentLocale);

        if (resources == null) {
            resources = resourceLoader.loadResources(currentLocale);
            cache.put(currentLocale, resources);
            I18nRegistry.getInstance().updateResources(namespace, resources);
        }

        Object value = getNestedValue(resources, key);
        if (value == null) {
            // Fallback to registry
            value = REGISTRY.getTranslation(namespace, key);
            if (value == null) {
                return key; // Return key if not found
            }
        }

        String message = value.toString();

        if (params != null && !params.isEmpty()) {
            return formatMessage(message, params, currentLocale);
        }

        return message;
    }

    /**
     * Format message with ICU Message Format (Object[] overload)
     */
    private String formatMessage(String message, Object[] params, Locale locale) {
        try {
            MessageFormat messageFormat = new MessageFormat(message, locale);
            return messageFormat.format(params);
        } catch (Exception e) {
            // Fallback to simple string replacement
            String result = message;
            for (int i = 0; i < params.length; i++) {
                result = result.replace("{" + i + "}", String.valueOf(params[i]));
            }
            return result;
        }
    }

    /**
     * Format message with ICU Message Format (Map overload)
     */
    private String formatMessage(String message, Map<String, Object> params, Locale locale) {
        try {
            MessageFormat messageFormat = new MessageFormat(message, locale);
            return messageFormat.format(params);
        } catch (Exception e) {
            // Fallback to simple string replacement
            String result = message;
            for (Map.Entry<String, Object> entry : params.entrySet()) {
                result = result.replace("{" + entry.getKey() + "}", String.valueOf(entry.getValue()));
            }
            return result;
        }
    }

    /**
     * Pluralize message based on count
     */
    public String pluralize(String key, Number count) {
        Locale currentLocale = I18nConfig.getInstance().getCurrentLocale();
        Map<String, Object> resources = cache.get(currentLocale);

        if (resources == null) {
            resources = resourceLoader.loadResources(currentLocale);
            cache.put(currentLocale, resources);
        }

        Object value = getNestedValue(resources, key);
        if (value == null) {
            return key;
        }

        try {
            PluralRules pluralRules = PluralRules.forLocale(currentLocale);
            PluralFormat pluralFormat = new PluralFormat(currentLocale, pluralRules);
            pluralFormat.applyPattern(value.toString());
            return pluralFormat.format(count);
        } catch (Exception e) {
            return value.toString();
        }
    }

    /**
     * Format number
     */
    public String formatNumber(String key, Number number) {
        String pattern = t(key);
        try {
            MessageFormat messageFormat = new MessageFormat(pattern, I18nConfig.getInstance().getCurrentLocale());
            return messageFormat.format(new Object[]{number});
        } catch (Exception e) {
            return String.valueOf(number);
        }
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
     * Clear cache for this proxy
     */
    public void clearCache() {
        cache.clear();
    }

    /**
     * Check if key exists
     */
    public boolean hasKey(String key) {
        Locale currentLocale = I18nConfig.getInstance().getCurrentLocale();
        Map<String, Object> resources = cache.get(currentLocale);

        if (resources == null) {
            resources = resourceLoader.loadResources(currentLocale);
            cache.put(currentLocale, resources);
        }

        return getNestedValue(resources, key) != null;
    }
}
