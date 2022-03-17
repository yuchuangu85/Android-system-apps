/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.settings.sound;

import static com.android.settings.core.BasePreferenceController.AVAILABLE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.spy;

import android.content.ContentResolver;
import android.content.Context;
import android.provider.Settings;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class MediaControlsPreferenceControllerTest {

    private static final String KEY = "media_controls_resume_switch";

    private Context mContext;
    private int mOriginalQs;
    private int mOriginalResume;
    private ContentResolver mContentResolver;
    private MediaControlsPreferenceController mController;

    @Before
    public void setUp() {
        mContext = spy(RuntimeEnvironment.application);
        mContentResolver = mContext.getContentResolver();
        mOriginalQs = Settings.Global.getInt(mContentResolver,
                Settings.Global.SHOW_MEDIA_ON_QUICK_SETTINGS, 1);
        mOriginalResume = Settings.Secure.getInt(mContentResolver,
                Settings.Secure.MEDIA_CONTROLS_RESUME, 1);
        mController = new MediaControlsPreferenceController(mContext, KEY);
    }

    @After
    public void tearDown() {
        Settings.Global.putInt(mContentResolver, Settings.Global.SHOW_MEDIA_ON_QUICK_SETTINGS,
                mOriginalQs);
        Settings.Secure.putInt(mContentResolver, Settings.Secure.MEDIA_CONTROLS_RESUME,
                mOriginalResume);
    }

    @Test
    public void getAvailability_returnAvailable() {
        assertThat(mController.getAvailabilityStatus()).isEqualTo(AVAILABLE);
    }

    @Test
    public void setChecked_enable_shouldTurnOff() {
        Settings.Global.putInt(mContentResolver, Settings.Global.SHOW_MEDIA_ON_QUICK_SETTINGS, 1);
        Settings.Secure.putInt(mContentResolver, Settings.Secure.MEDIA_CONTROLS_RESUME, 1);

        assertThat(mController.isChecked()).isFalse();

        mController.setChecked(true);

        assertThat(Settings.Secure.getInt(mContentResolver,
                Settings.Secure.MEDIA_CONTROLS_RESUME, -1)).isEqualTo(0);
    }

    @Test
    public void setChecked_disable_shouldTurnOn() {
        Settings.Global.putInt(mContentResolver, Settings.Global.SHOW_MEDIA_ON_QUICK_SETTINGS, 1);
        Settings.Secure.putInt(mContentResolver, Settings.Secure.MEDIA_CONTROLS_RESUME, 0);

        assertThat(mController.isChecked()).isTrue();

        mController.setChecked(false);

        assertThat(Settings.Secure.getInt(mContentResolver,
                Settings.Secure.MEDIA_CONTROLS_RESUME, -1)).isEqualTo(1);
    }
}
