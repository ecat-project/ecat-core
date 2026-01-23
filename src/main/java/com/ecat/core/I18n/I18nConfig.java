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