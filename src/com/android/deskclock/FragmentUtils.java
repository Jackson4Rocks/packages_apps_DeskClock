/*
 * Copyright (C) 2016 The Android Open Source Project
 * Copyright (C) 2020 The LineageOS Project
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

package com.android.deskclock;

import android.content.res.Resources;
import android.util.ArrayMap;
import android.view.View;
import android.view.animation.AnimationUtils;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentFactory;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.android.deskclock.uidata.UiDataModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * This class produces the DeskClockFragments that are the content of the DeskClock tabs.
 * It presents the tabs in LTR and RTL order depending on the text layout direction for the
 * current locale. To prevent issues when switching between LTR and RTL, fragments are registered
 * with the manager using position-independent tags, which is an important departure from
 * FragmentPagerAdapter.
 */
public final class FragmentUtils {

    private final DeskClock mDeskClock;

    /** The manager into which fragments are added. */
    private final FragmentManager mFragmentManager;

    /** A fragment cache that can be accessed before {@link #instantiateItem} is called. */
    private final Map<UiDataModel.Tab, DeskClockFragment> mFragmentCache;

    /** The current fragment displayed to the user. */
    private DeskClockFragment mCurrentPrimaryItem;

    /** The sections which are fading out and must be hidden once their animation completes. */
    private final List<DeskClockFragment> mFadingOut = new ArrayList<>(1);

    public FragmentUtils(DeskClock deskClock) {
        mDeskClock = deskClock;
        mFragmentCache = new ArrayMap<>(getCount());
        mFragmentManager = deskClock.getSupportFragmentManager();
    }

    private int getCount() {
        return UiDataModel.getUiDataModel().getTabCount();
    }

    public DeskClockFragment getDeskClockFragment(UiDataModel.Tab tab) {
        // First check the local cache for the fragment.
        DeskClockFragment fragment = mFragmentCache.get(tab);
        if (fragment != null) {
            return fragment;
        }

        // Next check the fragment manager; relevant when app is rebuilt after locale changes
        // because this adapter will be new and mFragmentCache will be empty, but the fragment
        // manager will retain the Fragments built on original application launch.
        fragment = (DeskClockFragment) mFragmentManager.findFragmentByTag(tab.name());
        if (fragment != null) {
            fragment.setFabContainer(mDeskClock);
            mFragmentCache.put(tab, fragment);
            return fragment;
        }

        // Otherwise, build the fragment from scratch.
        final String fragmentClassName = tab.getFragmentClassName();
        FragmentFactory fragmentFactory = mFragmentManager.getFragmentFactory();
        fragment = (DeskClockFragment) fragmentFactory.instantiate(
                mDeskClock.getClassLoader(), fragmentClassName);
        fragment.setFabContainer(mDeskClock);

        FragmentTransaction transaction = mFragmentManager.beginTransaction();
        transaction.add(R.id.fragment_container, fragment, tab.name());
        transaction.commit();

        mFragmentCache.put(tab, fragment);
        return fragment;
    }

    public void hideAllFragments() {
        // Any section still fading away must be hidden now rather than after its animation.
        for (final DeskClockFragment fading : new ArrayList<>(mFadingOut)) {
            finishFadeOut(fading);
        }

        FragmentTransaction transaction = mFragmentManager.beginTransaction();
        for (UiDataModel.Tab tab : UiDataModel.Tab.values()) {
            Fragment fragment = mFragmentManager.findFragmentByTag(tab.name());
            if (fragment != null) {
                transaction.hide(fragment);
            }
        }

        transaction.commit();
    }

    public void showFragment(UiDataModel.Tab tab) {
        // Finish any fade-out still in flight; its content is about to be replaced.
        for (final DeskClockFragment fading : new ArrayList<>(mFadingOut)) {
            finishFadeOut(fading);
        }

        final DeskClockFragment incoming = getDeskClockFragment(tab);
        if (incoming == mCurrentPrimaryItem) {
            return;
        }

        final DeskClockFragment outgoing = mCurrentPrimaryItem;
        final int outgoingIndex = indexOf(outgoing);
        final boolean animate = outgoing != null && outgoing.getView() != null;

        // Hide every section except the incoming one. The outgoing one is left visible so that it
        // can cross-fade away underneath the section replacing it.
        final FragmentTransaction transaction = mFragmentManager.beginTransaction();
        for (final UiDataModel.Tab other : UiDataModel.Tab.values()) {
            final Fragment fragment = mFragmentManager.findFragmentByTag(other.name());
            if (fragment == null || fragment == incoming || (animate && fragment == outgoing)) {
                continue;
            }
            transaction.hide(fragment);
        }
        transaction.show(incoming);
        transaction.commit();

        // Publish the selection before the transactions run: starting a section updates the shared
        // fab, which asks FragmentUtils for the currently selected fragment.
        mCurrentPrimaryItem = incoming;
        mFragmentManager.executePendingTransactions();

        if (animate) {
            animateTransition(incoming, outgoing, Integer.compare(tab.ordinal(), outgoingIndex));
        }
    }

    /** @return the position of the given fragment within the tabs, or -1 if it is unknown */
    private int indexOf(DeskClockFragment fragment) {
        if (fragment == null) {
            return -1;
        }

        final UiDataModel.Tab[] tabs = UiDataModel.Tab.values();
        for (int i = 0; i < tabs.length; i++) {
            if (fragment == mFragmentCache.get(tabs[i])) {
                return i;
            }
        }
        for (int i = 0; i < tabs.length; i++) {
            if (fragment == mFragmentManager.findFragmentByTag(tabs[i].name())) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Slides and fades the incoming section into place while the outgoing one fades away, in the
     * direction the user is travelling.
     */
    private void animateTransition(DeskClockFragment incoming, DeskClockFragment outgoing,
                                   int direction) {
        final View incomingView = incoming.getView();
        final View outgoingView = outgoing == null ? null : outgoing.getView();
        if (incomingView == null || outgoingView == null || direction == 0) {
            // Nothing sensible to animate towards; reveal the section immediately.
            incomingViewSetVisible(incomingView);
            if (outgoing != null) {
                mFadingOut.add(outgoing);
                finishFadeOut(outgoing);
            }
            return;
        }

        final Resources resources = incomingView.getResources();
        final int rtl = resources.getConfiguration().getLayoutDirection()
                == View.LAYOUT_DIRECTION_RTL ? -1 : 1;
        final int slide = Math.round(24f * resources.getDisplayMetrics().density)
                * direction * rtl;
        final long duration = UiDataModel.getUiDataModel().getShortAnimationDuration();

        incomingView.animate().cancel();
        incomingView.setAlpha(0f);
        incomingView.setTranslationX(slide);
        incomingView.animate()
                .alpha(1f)
                .translationX(0f)
                .setDuration(duration)
                .setInterpolator(AnimationUtils.loadInterpolator(incomingView.getContext(),
                        android.R.interpolator.fast_out_slow_in))
                .start();

        if (!mFadingOut.contains(outgoing)) {
            mFadingOut.add(outgoing);
        }
        outgoingView.animate().cancel();
        outgoingView.animate()
                .alpha(0f)
                .translationX(-slide / 3f)
                .setDuration(Math.round(duration * 0.7f))
                .setInterpolator(AnimationUtils.loadInterpolator(outgoingView.getContext(),
                        android.R.interpolator.fast_out_slow_in))
                .withEndAction(() -> finishFadeOut(outgoing))
                .start();
    }

    private void incomingViewSetVisible(View view) {
        if (view != null) {
            view.setAlpha(1f);
            view.setTranslationX(0f);
        }
    }

    /** Restores and hides a section whose fade-out animation has finished. */
    private void finishFadeOut(DeskClockFragment fragment) {
        if (fragment == null || !mFadingOut.remove(fragment)) {
            return;
        }

        incomingViewSetVisible(fragment.getView());
        if (fragment == mCurrentPrimaryItem) {
            return;
        }

        mFragmentManager.beginTransaction().hide(fragment).commit();
    }

    public DeskClockFragment getCurrentFragment() {
        return mCurrentPrimaryItem;
    }
}
