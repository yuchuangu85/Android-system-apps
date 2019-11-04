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

import android.content.Context;
import android.content.res.Configuration;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.documentsui.tests.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * This class test default Dark Theme (Night Mode Disable)
 * Verify ActionBar background, Window background, and GridItem background to meet Light style
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class DarkThemeUiTest extends ThemeUiTestBase {
    Context mTestContext;

    @Before
    public void setUp() throws Exception {
        super.setUp();
        mTestContext = InstrumentationRegistry.getContext();
        mTheme = getThemeByUiMode(mTargetContext, Configuration.UI_MODE_NIGHT_YES);
    }

    @Test
    public void themeNightModeEnable_actionBarColorShouldBeDark() {
        assertTheme(R.styleable.ThemeColor, R.styleable.ThemeColor_android_colorBackground,
                mTheme.getResources().getColor(com.android.documentsui.R.color.app_background_color,
                        mTheme));
    }

    @Test
    public void themeNightModeEnable_windowLightNavigationBarShouldBeFalse() {
        assertTheme(R.styleable.SystemWindow,
                R.styleable.SystemWindow_android_windowLightNavigationBar, false);
    }

    @Test
    public void themeNightModeEnable_windowLightStatusBarShouldBeFalse() {
        assertTheme(R.styleable.SystemWindow,
                R.styleable.SystemWindow_android_windowLightNavigationBar, false);
    }

    @Test
    public void themeNightModeEnable_navigationBarColorShouldBeDark() {
        assertTheme(R.styleable.SystemWindow, R.styleable.SystemWindow_android_navigationBarColor,
                mTheme.getResources().getColor(android.R.color.black, mTheme));
    }

    @Test
    public void themeNightModeEnable_windowBackgroundColorShouldBeDark() {
        assertTheme(R.styleable.SystemWindow, R.styleable.SystemWindow_android_windowBackground,
                mTheme.getResources().getColor(com.android.documentsui.R.color.app_background_color,
                        mTheme));
    }

    @Test
    public void themeNightModeEnable_statusBarColorShouldBeDark() {
        assertTheme(R.styleable.SystemWindow, R.styleable.SystemWindow_android_statusBarColor,
                mTheme.getResources().getColor(com.android.documentsui.R.color.app_background_color,
                        mTheme));
    }

    @Test
    public void appCompatThemeNightModeEnable_colorPrimaryShouldBeThemeable() {
        assertTheme(R.styleable.ThemeColor, R.styleable.ThemeColor_android_colorPrimary,
                mTheme.getResources().getColor(com.android.documentsui.R.color.primary, mTheme));
    }
}