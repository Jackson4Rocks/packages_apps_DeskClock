/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.deskclock;

import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import androidx.annotation.ColorInt;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.WindowInsetsControllerCompat;

/**
 * Base activity class. The theme (dark or Light) is picked before anything inflates, and
 * the window background is the theme surface, so the whole app stays on one theme with
 * the system instead of drifting with the clock. Time-of-day theming is parked for now;
 * its implementation is kept under SAMPLES/timing_backup for later.
 */
public abstract class BaseActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (useLightTheme()) {
            // Must precede everything: the theme decides every color inflated afterwards.
            setTheme(R.style.Theme_DeskClock_Light);
        }
        super.onCreate(savedInstanceState);

        // Flat theme surface behind everything; the floating glass blurs the content.
        getWindow().setBackgroundDrawable(new ColorDrawable(
                ThemeUtils.resolveColor(this, android.R.attr.windowBackground)));

        // Allow the content to layout behind the status and navigation bars.
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);

        // Status and navigation glyphs follow the theme: dark on light surfaces.
        final WindowInsetsControllerCompat insets = new WindowInsetsControllerCompat(
                getWindow(), getWindow().getDecorView());
        final boolean light = useLightTheme();
        insets.setAppearanceLightStatusBars(light);
        insets.setAppearanceLightNavigationBars(light);

        // Never let the system paint its own contrast scrim behind the icons; the glyph
        // colors above already guarantee contrast in both themes.
        if (Build.VERSION.SDK_INT >= 29) {
            getWindow().setStatusBarContrastEnforced(false);
        }
    }

    /**
     * @return whether this activity follows the Light mode toggle; alert screens that must
     *         stay dark opt out
     */
    protected boolean useLightTheme() {
        return TimeOfDayTheme.isLightMode(this);
    }

    /**
     * @return whether this activity may rebuild itself when the time of day rolls over; screens
     *         that must not be interrupted while they are up opt out and keep their palette
     */
    protected boolean shouldRecreateOnPeriodChange() {
        return true;
    }
}
