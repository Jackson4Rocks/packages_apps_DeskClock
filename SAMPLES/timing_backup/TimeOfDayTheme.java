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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;

import androidx.annotation.NonNull;
import androidx.annotation.StyleRes;
import androidx.preference.PreferenceManager;

import java.time.LocalTime;

/**
 * Chooses and applies the time of day palette. One UI retints its surfaces as the light outside
 * changes, so does this app: day, afternoon, evening and night each contribute a small overlay
 * that only rewrites a handful of color attributes on top of the regular dark theme.
 */
public final class TimeOfDayTheme {

    /**
     * Preference key for the theme choice in Settings: system default, dark or light.
     */
    public static final String KEY_THEME_MODE = "theme_mode";

    private static final String VALUE_THEME_SYSTEM = "system";
    private static final String VALUE_THEME_LIGHT = "light";

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

    /**
     * Debug hook for previewing a palette: launch the activity with
     * {@code --ei deskclock_force_period 0..3} to pin day, afternoon, evening or night.
     */
    public static final String EXTRA_FORCE_PERIOD = "deskclock_force_period";

    /**
     * Debug hook for previewing a moment of the sky: launch the activity with
     * {@code --ef deskclock_force_sky 18.5} to see the background at that hour.
     */
    public static final String EXTRA_FORCE_SKY = "deskclock_force_sky";

    /**
     * Sky keyframes across the day, in hours since midnight. The palette glides from one to the
     * next, so sunrise, golden hour and sunset arrive gradually instead of jumping between four
     * flat colors. The last mark is 24:00 and repeats the first one to close the loop.
     */
    private static final float[] SKY_HOURS = {
            0f, 4.5f, 5.5f, 6.5f, 7.5f, 9f, 12f, 15f, 17f, 18.5f, 19.5f, 21f, 24f
    };

    /** Upper half of the sky for every keyframe above. */
    private static final int[] SKY_TOP = {
            0xFF05070C, // 00:00 midnight
            0xFF06080F, // 04:30 deep night
            0xFF0A0F1E, // 05:30 pre dawn
            0xFF2A1B33, // 06:30 sunrise
            0xFF182139, // 07:30 early morning
            0xFF111C2E, // 09:00 morning
            0xFF101821, // 12:00 midday
            0xFF16151A, // 15:00 afternoon
            0xFF1E1410, // 17:00 golden hour
            0xFF33202C, // 18:30 sunset
            0xFF241A36, // 19:30 dusk
            0xFF0B0E15, // 21:00 night
            0xFF05070C  // 24:00 midnight again
    };

    /** Lower half of the sky: the horizon keeps a little of the light. */
    private static final int[] SKY_BOTTOM = {
            0xFF080B12, // 00:00 midnight
            0xFF090C14, // 04:30 deep night
            0xFF101528, // 05:30 pre dawn
            0xFF3A2340, // 06:30 sunrise
            0xFF1E2A46, // 07:30 early morning
            0xFF142235, // 09:00 morning
            0xFF131E29, // 12:00 midday
            0xFF1A181E, // 15:00 afternoon
            0xFF251813, // 17:00 golden hour
            0xFF3E2733, // 18:30 sunset
            0xFF2B2040, // 19:30 dusk
            0xFF0E1219, // 21:00 night
            0xFF080B12  // 24:00 midnight again
    };

    /** The four parts of the day, each one owning an overlay style. */
    public enum Period {
        /** Morning: 06:00 - 11:59 */
        DAY(R.style.TimeOfDay_Day),
        /** Afternoon: 12:00 - 16:59 */
        AFTERNOON(R.style.TimeOfDay_Afternoon),
        /** Evening: 17:00 - 20:59 */
        EVENING(R.style.TimeOfDay_Evening),
        /** Night: 21:00 - 05:59 */
        NIGHT(R.style.TimeOfDay_Night);

        private final @StyleRes int mOverlay;

        Period(@StyleRes int overlay) {
            mOverlay = overlay;
        }

        /** @return the overlay style that carries this palette */
        public @StyleRes int getOverlayResId() {
            return mOverlay;
        }

        /**
         * @param hour the hour of the day in 24h format
         * @return the period that owns {@code hour}
         */
        @NonNull
        static Period fromHour(int hour) {
            switch (hour) {
                case 0: case 1: case 2: case 3: case 4: case 5:
                case 21: case 22: case 23:
                    return NIGHT;
                case 6: case 7: case 8: case 9: case 10: case 11:
                    return DAY;
                case 12: case 13: case 14: case 15: case 16:
                    return AFTERNOON;
                default:
                    return EVENING;
            }
        }
    }

    private TimeOfDayTheme() {
        // Prevent instantiation.
    }

    /** Light mode keeps one soft daylight gradient instead of chasing the sun. */
    private static final int LIGHT_SKY_TOP = 0xFFD7E3F4;
    private static final int LIGHT_SKY_BOTTOM = 0xFFF1F5FA;

    /**
     * Interpolates the sky for the current moment, unless the activity was asked to preview one.
     *
     * @param context the activity to consult for a forced moment
     * @return a pair of ARGB colors, top of the screen first and horizon second
     */
    public static @NonNull int[] sky(@NonNull Context context) {
        if (isLightMode(context)) {
            return new int[] { LIGHT_SKY_TOP, LIGHT_SKY_BOTTOM };
        }
        if (context instanceof Activity) {
            final Intent intent = ((Activity) context).getIntent();
            if (intent != null && intent.hasExtra(EXTRA_FORCE_SKY)) {
                final float hour = intent.getFloatExtra(EXTRA_FORCE_SKY, -1f);
                if (hour >= 0f && hour <= 24f) {
                    return skyAt(hour);
                }
            }
        }
        return sky(LocalTime.now());
    }

    /**
     * Interpolates the sky for the given moment. Sunrise, golden hour and sunset are keyframes on
     * a continuous timeline, so the background drifts through the day rather than switching.
     *
     * @param now the moment to sample
     * @return a pair of ARGB colors, top of the screen first and horizon second
     */
    public static @NonNull int[] sky(@NonNull LocalTime now) {
        return skyAt(now.getHour() + now.getMinute() / 60f + now.getSecond() / 3600f);
    }

    /** Interpolates the sky for a given hour of the day, where 24 wraps back to midnight. */
    private static @NonNull int[] skyAt(float hour) {
        int index = 0;
        while (index < SKY_HOURS.length - 2 && hour >= SKY_HOURS[index + 1]) {
            index++;
        }

        final float span = SKY_HOURS[index + 1] - SKY_HOURS[index];
        final float t = span <= 0f ? 0f : (hour - SKY_HOURS[index]) / span;
        return new int[] {
                blend(SKY_TOP[index], SKY_TOP[index + 1], t),
                blend(SKY_BOTTOM[index], SKY_BOTTOM[index + 1], t)
        };
    }

    /** Linearly mixes two ARGB colors by {@code t}, which must be within 0 and 1. */
    private static int blend(int from, int to, float t) {
        final int a = (from >>> 24) & 0xFF;
        final int r = Math.round(((from >>> 16) & 0xFF) + (((to >>> 16) & 0xFF) - ((from >>> 16) & 0xFF)) * t);
        final int g = Math.round(((from >>> 8) & 0xFF) + (((to >>> 8) & 0xFF) - ((from >>> 8) & 0xFF)) * t);
        final int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    /**
     * Accent keyframes across the day, sampled at the same hours as {@link #SKY_HOURS} so color
     * and sky travel together: periwinkle at night, coral at sunrise, sky blue by day, amber in
     * the afternoon, coral again at sunset and lavender at dusk. Unlike the four theme overlays,
     * which can only switch at period boundaries, this interpolates continuously.
     */
    private static final int[] ACCENT_COLORS = {
            0xFFA6B3FF, // 00:00 night
            0xFFA6B3FF, // 04:30 deep night
            0xFFB9A8FF, // 05:30 pre dawn
            0xFFFF9E8A, // 06:30 sunrise
            0xFF7FC4FF, // 07:30 early morning
            0xFF79BFFF, // 09:00 morning
            0xFF79BFFF, // 12:00 midday
            0xFFFFC46B, // 15:00 afternoon
            0xFFFFB259, // 17:00 golden hour
            0xFFFF8D76, // 18:30 sunset
            0xFFC9A6FF, // 19:30 dusk
            0xFFA6B3FF, // 21:00 night
            0xFFA6B3FF  // 24:00 midnight again
    };

    /**
     * @param now the moment to sample
     * @return the accent color for that moment, gliding smoothly through the whole day
     */
    public static int accent(@NonNull LocalTime now) {
        return accentAt(now.getHour() + now.getMinute() / 60f + now.getSecond() / 3600f);
    }

    /**
     * @param context the activity to consult for a forced period
     * @return the pinned period color when previewing, otherwise the live interpolated accent
     */
    public static int accent(@NonNull Context context) {
        if (context instanceof Activity) {
            final Intent intent = ((Activity) context).getIntent();
            if (intent != null) {
                switch (intent.getIntExtra(EXTRA_FORCE_PERIOD, -1)) {
                    case 0: return 0xFF79BFFF; // day
                    case 1: return 0xFFFFC46B; // afternoon
                    case 2: return 0xFFFF8D76; // evening
                    case 3: return 0xFFA6B3FF; // night
                    default: break;
                }
            }
        }
        return accent(LocalTime.now());
    }

    /** Interpolates the accent for a given hour of the day, where 24 wraps back to midnight. */
    private static int accentAt(float hour) {
        int index = 0;
        while (index < SKY_HOURS.length - 2 && hour >= SKY_HOURS[index + 1]) {
            index++;
        }

        final float span = SKY_HOURS[index + 1] - SKY_HOURS[index];
        final float t = span <= 0f ? 0f : (hour - SKY_HOURS[index]) / span;
        return blend(ACCENT_COLORS[index], ACCENT_COLORS[index + 1], t);
    }

    /**
     * Resolves the palette for {@code context} and rewrites its theme with it. Existing values are
     * kept for every attribute the overlay does not mention, so the dark Material 3 base stays
     * intact and only the accents change.
     *
     * @param context the activity or context whose theme should be retinted
     * @return the period that was applied
     */
    @NonNull
    public static Period apply(@NonNull Context context) {
        final Period period = resolve(context);
        // Light mode brings its own complete theme; the dark time-of-day overlays stay out.
        if (!isLightMode(context)) {
            final Resources.Theme theme = context.getTheme();
            if (theme != null) {
                theme.applyStyle(period.getOverlayResId(), true /* force */);
            }
        }
        return period;
    }

    /**
     * @param context the activity to consult for a forced period
     * @return the palette for the current time, or the forced one when previewing
     */
    @NonNull
    public static Period resolve(@NonNull Context context) {        if (context instanceof Activity) {
            final Intent intent = ((Activity) context).getIntent();
            if (intent != null) {
                final int forced = intent.getIntExtra(EXTRA_FORCE_PERIOD, -1);
                final Period[] periods = Period.values();
                if (forced >= 0 && forced < periods.length) {
                    return periods[forced];
                }
            }
        }
        return Period.fromHour(LocalTime.now().getHour());
    }
}
