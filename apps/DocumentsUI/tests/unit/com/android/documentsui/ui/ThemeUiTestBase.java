/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.documentsui.ui;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.content.res.CompatibilityInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.util.DisplayMetrics;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.documentsui.R;

import org.junit.After;
import org.junit.Before;
import org.junit.runner.RunWith;

/**
 * The TestBase class define getThemeByUiMode API and prepare setUp and tearDown for Theme test
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public abstract class ThemeUiTestBase {
    protected Context mTargetContext;
    protected Configuration mConfiguration;
    protected DisplayMetrics mDisplayMetrics;
    protected CompatibilityInfo mCompatibilityInfo;
    protected Resources.Theme mTheme;

    @Before
    public void setUp() throws Exception {
        mTargetContext = InstrumentationRegistry.getTargetContext();
        mConfiguration = mTargetContext.getResources().getConfiguration();
        mDisplayMetrics = mTargetContext.getResources().getDisplayMetrics();
        mCompatibilityInfo = mTargetContext.getResources().getCompatibilityInfo();
    }

    @After
    public void tearDown() throws Exception {
        mTargetContext.getResources().updateConfiguration(mConfiguration, mDisplayMetrics,
                mCompatibilityInfo);
    }

    /**
     * The method to return the customized theme base on UI_MODE_NIGHT_YES or UI_MODE_NIGHT_NO
     *
     * @param context       The applicationContext from Test App
     * @param nightModeFlag Only support Configuration.UI_MODE_NIGHT_YES or UI_MODE_NIGHT_NO
     * @return Resources.Theme Applied target App's style/DocumentsTheme
     */
    protected Resources.Theme getThemeByUiMode(Context context, int nightModeFlag) {
        final CompatibilityInfo ci = context.getResources().getCompatibilityInfo();
        final DisplayMetrics dm = context.getResources().getDisplayMetrics();
        final Configuration nightConfig = new Configuration(
                context.getResources().getConfiguration());
        nightConfig.uiMode &= ~Configuration.UI_MODE_NIGHT_MASK;
        nightConfig.uiMode |= nightModeFlag;
        context.getResources().updateConfiguration(nightConfig, dm, ci);
        // Resources.theme won't be updated by updateConfiguration()
        // hence, we need to create new Resources.theme to force apply again
        final Resources.Theme theme = context.getResources().newTheme();
        theme.applyStyle(R.style.DocumentsTheme, true);
        theme.applyStyle(R.style.DocumentsDefaultTheme, false);
        return theme;
    }

    /**
     * The method to assert target theme defined color match expected color
     *
     * @param styleable The scope of attrs defined in the theme
     * @param attr      The specific target color to retrieve
     * @param expected  The expected color in the theme
     */
    protected void assertTheme(int[] styleable, int attr, int expected) {
        final TypedArray ta = mTheme.obtainStyledAttributes(styleable);
        final int targetColor = ta.getColor(attr, ~expected);
        ta.recycle();
        assertThat(targetColor).isEqualTo(expected);
    }

    /**
     * The method to assert target theme defined config match expected boolean
     *
     * @param styleable The scope of attrs defined in the theme
     * @param attr      The specific target config to retrieve
     * @param expected  The expected boolean of defined attr in the theme
     */
    protected void assertTheme(int[] styleable, int attr, boolean expected) {
        final TypedArray ta = mTheme.obtainStyledAttributes(styleable);
        final boolean targetBoolean = ta.getBoolean(attr, !expected);
        ta.recycle();
        assertThat(targetBoolean).isEqualTo(expected);
    }
}
