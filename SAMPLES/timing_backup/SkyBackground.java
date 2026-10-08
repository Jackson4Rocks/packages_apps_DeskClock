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
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Paints the window background with a sky that follows the clock. The colors come from
 * {@link TimeOfDayTheme#sky(LocalTime)}, which glides from midnight through sunrise, golden hour,
 * sunset and dusk; a slow ticker keeps the background in step while the app is open, so the light
 * outside and the light on screen never drift far apart.
 */
public final class SkyBackground {

    /** How often the sky is re-sampled. The sun does not move faster than this. */
    private static final long TICK_MILLIS = 20_000L;

    /** Smallest per channel change worth redrawing for. */
    private static final int COLOR_DELTA = 3;

    /** Smallest total channel drift worth reporting a new accent for. */
    private static final int ACCENT_DELTA = 4;

    /** Notified with the live interpolated accent whenever it drifts. */
    public interface OnAccentChangedListener {
        void onAccentChanged(@ColorInt int accent);
    }

    private final Activity mActivity;
    private final GradientDrawable mDrawable;
    private final Handler mHandler = new Handler(Looper.getMainLooper());
    private final int[] mColors = new int[2];
    private View mBoundView;
    private OnAccentChangedListener mAccentListener;
    private int mLastAccent;
    private boolean mRunning;

    private final Runnable mTicker = new Runnable() {
        @Override
        public void run() {
            refresh();
            if (mRunning) {
                mHandler.postDelayed(this, TICK_MILLIS);
            }
        }
    };

    public SkyBackground(@NonNull Activity activity) {
        mActivity = activity;

        final int[] sky = TimeOfDayTheme.sky(activity);
        mColors[0] = sky[0];
        mColors[1] = sky[1];
        mDrawable = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, sky);
        mActivity.getWindow().setBackgroundDrawable(mDrawable);
    }

    /** Starts sampling the clock; safe to call from every start of the activity. */
    public void start() {
        // Install here rather than in the constructor: installing the decor view replaces the
        // window background with the one from the theme, which happens after onCreate returns.
        mActivity.getWindow().setBackgroundDrawable(mDrawable);

        if (mRunning) {
            return;
        }
        mRunning = true;
        refresh();
        mHandler.postDelayed(mTicker, TICK_MILLIS);
    }

    /**
     * Keeps a view's background on the same color as the top of the sky. The app bar resolves its
     * background from a child theme that the activity's overlay cannot reach, so it is painted
     * here instead and never drifts away from the sky behind it.
     *
     * @param view the view whose background follows the sky, or null to stop following
     */
    public void bind(@Nullable View view) {
        mBoundView = view;
        if (view != null) {
            view.setBackgroundColor(mColors[0]);
        }
    }

    /**
     * Registers for the live accent color. The listener is called back immediately with the
     * current value, then again whenever the accent drifts as the day moves on.
     */
    public void setOnAccentChangedListener(@Nullable OnAccentChangedListener listener) {
        mAccentListener = listener;
        if (listener != null) {
            mLastAccent = TimeOfDayTheme.accent(mActivity);
            listener.onAccentChanged(mLastAccent);
        }
    }

    /** Stops sampling the clock; call from {@code onStop} so a background app keeps no timers. */
    public void stop() {
        mRunning = false;
        mHandler.removeCallbacks(mTicker);
    }

    /**
     * Pins the sky to a single flat color, the way {@code adjustAppColor} used to behave. The
     * ticker is left running, so the next tick resumes the sky where the clock actually is.
     *
     * @param color the ARGB value to fill the window with
     */
    public void setColor(@ColorInt int color) {
        mColors[0] = color;
        mColors[1] = color;
        apply();
    }

    private void refresh() {
        final int[] sky = TimeOfDayTheme.sky(mActivity);
        if (changed(sky)) {
            mColors[0] = sky[0];
            mColors[1] = sky[1];
            apply();
        }

        if (mAccentListener != null) {
            final int accent = TimeOfDayTheme.accent(mActivity);
            if (distance(mLastAccent, accent) > ACCENT_DELTA) {
                mLastAccent = accent;
                mAccentListener.onAccentChanged(accent);
            }
        }
    }

    private boolean changed(int[] sky) {
        return distance(mColors[0], sky[0]) > COLOR_DELTA || distance(mColors[1], sky[1]) > COLOR_DELTA;
    }

    private void apply() {
        mDrawable.setColors(new int[] {mColors[0], mColors[1]});
        mDrawable.invalidateSelf();
        if (mBoundView != null) {
            mBoundView.setBackgroundColor(mColors[0]);
        }
    }

    private static int distance(int a, int b) {
        return Math.abs(((a >>> 16) & 0xFF) - ((b >>> 16) & 0xFF))
                + Math.abs(((a >>> 8) & 0xFF) - ((b >>> 8) & 0xFF))
                + Math.abs((a & 0xFF) - (b & 0xFF));
    }
}
