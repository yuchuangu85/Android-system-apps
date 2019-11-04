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

import android.content.res.Configuration;
import android.graphics.Color;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.documentsui.tests.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * This class test default Light Theme (Night Mode Disable)
 * Verify ActionBar background, Window background, and GridItem background to meet Light style
 */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class ThemeUiTest extends ThemeUiTestBase {

    @Before
    public void setUp() throws Exception {
        super.setUp();
        mTheme = getThemeByUiMode(mTargetContext, Configuration.UI_MODE_NIGHT_NO);
    }

    @Test
    public void themeNightModeDisable_actionBarColorShouldBeLight() {
        assertTheme(R.styleable.ThemeColor, R.styleable.ThemeColor_android_colorBackground,
                Color.WHITE);
    }

    @Test
    public void themeNightModeDisable_windowLightNavigationBarShouldBeTrue() {
        assertTheme(R.styleable.SystemWindow,
                R.styleable.SystemWindow_android_windowLightNavigationBar, true);
    }

    @Test
    public void themeNightModeDisable_windowLightStatusBarShouldBeTrue() {
        assertTheme(R.styleable.SystemWindow, R.styleable.SystemWindow_android_windowLightStatusBar,
                true);
    }

    @Test
    public void themeNightModeDisable_navigationBarColorShouldBeLight() {
        assertTheme(R.styleable.SystemWindow, R.styleable.SystemWindow_android_navigationBarColor,
                Color.WHITE);
    }

    @Test
    public void themeNightModeDisable_windowBackgroundColorShouldBeLight() {
        assertTheme(R.styleable.SystemWindow, R.styleable.SystemWindow_android_windowBackground,
                Color.WHITE);
    }

    @Test
    public void themeNightModeDisable_statusBarColorShouldBeLight() {
        assertTheme(R.styleable.SystemWindow, R.styleable.SystemWindow_android_statusBarColor,
                Color.WHITE);
    }

    @Test
    public void appCompatThemeNightModeDisable_colorPrimaryShouldBeThemeable() {
        assertTheme(R.styleable.ThemeColor, R.styleable.ThemeColor_android_colorPrimary,
                mTheme.getResources().getColor(com.android.documentsui.R.color.primary, mTheme));
    }
}
