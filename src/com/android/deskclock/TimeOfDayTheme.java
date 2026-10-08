/*
 * Copyright (C) 2026 The Android Open Source Project
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

package com.android.deskclock;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

/**
 * Reads the theme choice: system default, dark or light. Time-of-day theming used to live
 * here too; it is parked for now and kept under SAMPLES/timing_backup for later.
 */
public final class TimeOfDayTheme {

    /**
     * Preference key for the theme choice in Settings: system default, dark or light.
     */
    public static final String KEY_THEME_MODE = "theme_mode";

    private static final String VALUE_THEME_SYSTEM = "system";
    private static final String VALUE_THEME_LIGHT = "light";

    private TimeOfDayTheme() {
        // Prevent instantiation.
    }

    /**
     * @return whether light surfaces win: an explicit Light choice, or a light OS when
     *         following the system default
     */
    public static boolean isLightMode(@NonNull Context context) {
        // Settings stores its preferences in device-protected storage, so read from the
        // same place or the choice is never seen.
        final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(
                context.createDeviceProtectedStorageContext());
        // Earlier builds had a plain Light toggle; honor it when no choice exists yet.
        final String mode = prefs.getString(KEY_THEME_MODE,
                prefs.getBoolean("light_mode", false) ? VALUE_THEME_LIGHT : VALUE_THEME_SYSTEM);
        if (VALUE_THEME_LIGHT.equals(mode)) {
            return true;
        }
        if (VALUE_THEME_SYSTEM.equals(mode)) {
            final int night = context.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK;
            return night != Configuration.UI_MODE_NIGHT_YES;
        }
        return false;
    }
}
