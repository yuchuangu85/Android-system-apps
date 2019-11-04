/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.documentsui.overlay;

import static com.google.common.truth.Truth.assertThat;

import android.graphics.drawable.Drawable;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.documentsui.R;
import com.android.documentsui.ui.ThemeUiTestBase;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * This class verify overlayable resource defined in RRO package
 * Verify Drawable, Dimen, Config to guarantee run time get resource without NotFound exception
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class OverlayableTest extends ThemeUiTestBase {

    @Before
    public void setUp() throws Exception {
        super.setUp();
    }

    @Test
    public void testConfig_isLauncherEnable_isNotNull() {
        assertThat(
                mTargetContext.getResources().getBoolean(R.bool.is_launcher_enabled)).isNotNull();
    }

    @Test
    public void testConfig_defaultRootUri_isNotEmpty() {
        assertThat(
                mTargetContext.getResources().getString(R.string.default_root_uri)).isNotEmpty();
    }

    @Test
    public void testConfig_preferredRootPackage_isNotNull() {
        assertThat(
                mTargetContext.getResources().getString(
                        R.string.preferred_root_package)).isNotNull();
    }

    @Test
    public void testConfig_trustedQuickViewerPackage_isNotNull() {
        assertThat(
                mTargetContext.getResources().getString(
                        R.string.trusted_quick_viewer_package)).isNotNull();
    }

    @Test
    public void testDrawable_icEject_isVectorDrawable() {
        assertThat(
                mTargetContext.getResources().getDrawable(
                        R.drawable.ic_eject)).isInstanceOf(Drawable.class);
    }

    @Test
    public void testDrawable_icRootDownload_isVectorDrawable() {
        assertThat(
                mTargetContext.getResources().getDrawable(
                        R.drawable.ic_root_download)).isInstanceOf(Drawable.class);
    }

    @Test
    public void testDrawable_icSdStorage_isVectorDrawable() {
        assertThat(
                mTargetContext.getResources().getDrawable(
                        R.drawable.ic_sd_storage)).isInstanceOf(Drawable.class);
    }

    @Test
    public void testDrawable_icRootListSelector_isDrawable() {
        assertThat(
                mTargetContext.getResources().getDrawable(
                        R.drawable.root_list_selector)).isInstanceOf(Drawable.class);
    }

    @Test
    public void testDimen_gridItemRadius_isReasonable() {
        int MAX_RADIUS = 160;
        assertThat(
                mTargetContext.getResources().getDimensionPixelSize(
                        R.dimen.grid_item_radius)).isLessThan(MAX_RADIUS);
        assertThat(
                mTargetContext.getResources().getDimensionPixelSize(
                        R.dimen.grid_item_radius)).isAtLeast(0);
    }
}
