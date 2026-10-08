/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.deskclock.widget;

import android.app.ActionBar;
import android.content.Context;
import android.graphics.BlendMode;
import android.graphics.BlendModeColorFilter;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toolbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.WindowInsetsControllerCompat;
import androidx.fragment.app.FragmentActivity;

import com.android.deskclock.R;
import com.android.deskclock.ThemeUtils;
import com.android.deskclock.TimeOfDayTheme;

/**
 * A base Activity that has a toolbar layout
 */
public class ToolbarBaseActivity extends FragmentActivity {

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        if (TimeOfDayTheme.isLightMode(this)) {
            setTheme(R.style.Theme_DeskClock_Light);
        }
        super.onCreate(savedInstanceState);

        // Status and navigation glyphs follow the theme: dark on light surfaces.
        final boolean light = TimeOfDayTheme.isLightMode(this);
        final WindowInsetsControllerCompat insets = new WindowInsetsControllerCompat(
                getWindow(), getWindow().getDecorView());
        insets.setAppearanceLightStatusBars(light);
        insets.setAppearanceLightNavigationBars(light);

        super.setContentView(R.layout.toolbar_base_layout);

        // Solid theme surface for the header: it must match the system theme in every
        // mode instead of floating on the sky. Title and back arrow resolve their colors
        // from the theme the same way.
        final View appBar = findViewById(R.id.app_bar);
        appBar.setBackgroundColor(ThemeUtils.resolveColor(this, R.attr.colorSurface));

        final Toolbar toolbar = findViewById(R.id.action_bar);
        setActionBar(toolbar);
        final int ink = ThemeUtils.resolveColor(this, R.attr.colorOnSurface);
        toolbar.setTitleTextColor(ink);
        toolbar.setSubtitleTextColor(ink);

        // Enable title and home button by default
        final ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayShowTitleEnabled(true);
            // We need this to have an always light back arrow
            BlendModeColorFilter filter = new BlendModeColorFilter(
                    ThemeUtils.resolveColor(
                            this,
                            R.attr.colorOnSurface
                    ),
                    BlendMode.SRC_ATOP
            );
            toolbar.getNavigationIcon().setColorFilter(filter);
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        final boolean shown = super.onCreateOptionsMenu(menu);
        tintMenuIcons(this, menu);
        return shown;
    }

    /**
     * Tints every menu icon with the theme so fixed-white glyphs survive Light mode.
     * Icons are tinted on copies, never on the shared drawables.
     */
    public static void tintMenuIcons(@NonNull Context context, @NonNull Menu menu) {
        final int tint = ThemeUtils.resolveColor(context, R.attr.colorOnSurface);
        for (int i = 0; i < menu.size(); i++) {
            final Drawable icon = menu.getItem(i).getIcon();
            if (icon != null) {
                icon.mutate().setTint(tint);
            }
        }
    }

    @Override
    public void setContentView(int layoutResID) {
        final ViewGroup parent = findViewById(R.id.content_frame);
        if (parent != null) {
            parent.removeAllViews();
        }
        LayoutInflater.from(this).inflate(layoutResID, parent);
    }

    @Override
    public void setContentView(View view) {
        final ViewGroup parent = findViewById(R.id.content_frame);
        if (parent != null) {
            parent.addView(view);
        }
    }

    @Override
    public void setContentView(View view, ViewGroup.LayoutParams params) {
        final ViewGroup parent = findViewById(R.id.content_frame);
        if (parent != null) {
            parent.addView(view, params);
        }
    }

    @Override
    public boolean onNavigateUp() {
        if (!super.onNavigateUp()) {
            finishAfterTransition();
        }
        return true;
    }
}
