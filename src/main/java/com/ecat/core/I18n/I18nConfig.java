package com.ecat.core.I18n;

import java.util.Locale;

/**
 * I18n configuration class for managing locale settings
 *
 * @author coffee
 */
public class I18nConfig {

    private static I18nConfig instance;
    private Locale currentLocale;

    private I18nConfig() {
        this.currentLocale = Locale.getDefault();
    }

    public static synchronized I18nConfig getInstance() {
        if (instance == null) {
            instance = new I18nConfig();
        }
        return instance;
    }

    public Locale getCurrentLocale() {
        return currentLocale;
    }

    public void setCurrentLocale(Locale locale) {
        this.currentLocale = locale;
    }

    public void setCurrentLocale(String languageTag) {
        this.currentLocale = Locale.forLanguageTag(languageTag);
    }

    public String getCurrentLanguageTag() {
        return currentLocale.toLanguageTag();
    }
}