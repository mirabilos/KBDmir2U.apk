/*
 * Copyright (C) 2010 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package de.mirsolutions.android.mirkbd;

import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;

import java.util.Locale;

/**
 * Keeps track of list of selected input languages and the current
 * input language that the user has selected.
 */
public class LanguageSwitcher {

    private static final String TAG = "KBDmir2U/LanguageSwitcher";
    private Locale[] mLocales;
    private LatinIME mIme;
    private String[] mSelectedLanguageArray;
    private String   mSelectedLanguages;
    private int      mCurrentIndex = 0;
    private String   mDefaultInputLanguage;
    private Locale   mDefaultInputLocale;
    private Locale   mSystemLocale;

    public LanguageSwitcher(LatinIME ime) {
        mIme = ime;
        mLocales = new Locale[0];
    }

    public Locale[]  getLocales() {
        return mLocales;
    }

    public int getLocaleCount() {
        return mLocales.length;
    }

    /**
     * Loads the currently selected input languages from shared preferences.
     * @param sp
     * @return whether there was any change
     */
    public boolean loadLocales(SharedPreferences sp) {
            loadDefaults();
                return false;
    }

    private void loadDefaults() {
        mDefaultInputLanguage = "en";
        mDefaultInputLocale = new Locale(mDefaultInputLanguage);
    }

    /**
     * Returns the currently selected input language code, or the display language code if
     * no specific locale was selected for input.
     */
    public String getInputLanguage() {
        return mDefaultInputLanguage;
    }

    public boolean allowDeadKeys() {
	return false;
    }
    
    public boolean allowAutoSpace() {
	return false;
    }
    
    /**
     * Returns the list of enabled language codes.
     */
    public String[] getEnabledLanguages() {
        return mSelectedLanguageArray;
    }

    /**
     * Returns the currently selected input locale, or the display locale if no specific
     * locale was selected for input.
     * @return
     */
    public Locale getInputLocale() {
        Locale locale;
            locale = mDefaultInputLocale;
        LatinIME.sKeyboardSettings.inputLocale = (locale != null) ? locale : Locale.getDefault();
        return locale;
    }

    /**
     * Returns the next input locale in the list. Wraps around to the beginning of the
     * list if we're at the end of the list.
     * @return
     */
    public Locale getNextInputLocale() {
        return mDefaultInputLocale;
    }

    /**
     * Sets the system locale (display UI) used for comparing with the input language.
     * @param locale the locale of the system
     */
    public void setSystemLocale(Locale locale) {
        mSystemLocale = locale;
    }

    /**
     * Returns the system locale.
     * @return the system locale
     */
    public Locale getSystemLocale() {
        return mSystemLocale;
    }

    /**
     * Returns the previous input locale in the list. Wraps around to the end of the
     * list if we're at the beginning of the list.
     * @return
     */
    public Locale getPrevInputLocale() {
        return mDefaultInputLocale;
    }

    public void reset() {
    }

    public void next() {
    }

    public void prev() {
    }

    public void persist() {
    }

    static String toTitleCase(String s, Locale locale) {
        if (s.length() == 0) {
            return s;
        }

        return s.toUpperCase(locale).charAt(0) + s.substring(1);
    }
}
