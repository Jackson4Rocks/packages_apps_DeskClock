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

package com.android.deskclock.settings;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;

import com.android.deskclock.BuildConfig;
import com.android.deskclock.R;
import com.android.deskclock.widget.ToolbarBaseActivity;

/**
 * The About page for MaxxOS Clock: what is installed, who it came from and where the sources
 * live. The cards are plain solid system surfaces here, a quiet look that belongs to this
 * section alone.
 */
public final class AboutActivity extends ToolbarBaseActivity {

    /** Where the sources for this app are published. */
    public static final String SOURCE_URL =
            "https://github.com/MaxxOS-AOSP/packages_apps_DeskClock";

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.about);

        // The build knows the version; the pill shows "v1.0 (1)" style text.
        ((TextView) findViewById(R.id.about_version)).setText(
                "v" + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")");
        // Build stamp so installed builds are identifiable from the About page alone.
        ((TextView) findViewById(R.id.about_copyright)).setText(
                getString(R.string.about_copyright) + " • " + BuildConfig.BUILD_STAMP);

        final View sourceCard = findViewById(R.id.about_source_card);
        sourceCard.setOnClickListener(view -> openSource());
    }

    private void openSource() {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(SOURCE_URL)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, R.string.about_open_link_failed, Toast.LENGTH_SHORT).show();
        }
    }
}
