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

package com.android.car.settings.applications.assist;

import static com.google.common.truth.Truth.assertThat;

import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.testutils.ShadowSecureSettings;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowSecureSettings.class})
public class VoiceInputUtilsTest {

    private static final String TEST_PACKAGE = "test.package";
    private static final String TEST_CLASS_1 = "Class1";
    private static final String TEST_CLASS_2 = "Class2";

    private Context mContext;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.application;
    }

    @After
    public void tearDown() {
        ShadowSecureSettings.reset();
    }

    @Test
    public void getCurrentService_nullInteraction_nullRecognition_returnsNull() {
        assertThat(VoiceInputUtils.getCurrentService(mContext)).isNull();
    }

    @Test
    public void getCurrentService_emptyInteraction_emptyRecognition_returnsNull() {
        Settings.Secure.putString(mContext.getContentResolver(),
                Settings.Secure.VOICE_INTERACTION_SERVICE, "");
        Settings.Secure.putString(mContext.getContentResolver(),
                Settings.Secure.VOICE_RECOGNITION_SERVICE, "");
        assertThat(VoiceInputUtils.getCurrentService(mContext)).isNull();
    }

    @Test
    public void getCurrentService_hasInteraction_returnsInteraction() {
        ComponentName interaction = new ComponentName(TEST_PACKAGE, TEST_CLASS_1);
        ComponentName recognition = new ComponentName(TEST_PACKAGE, TEST_CLASS_2);
        Settings.Secure.putString(mContext.getContentResolver(),
                Settings.Secure.VOICE_INTERACTION_SERVICE, interaction.flattenToString());
        Settings.Secure.putString(mContext.getContentResolver(),
                Settings.Secure.VOICE_RECOGNITION_SERVICE, recognition.flattenToString());
        assertThat(VoiceInputUtils.getCurrentService(mContext)).isEqualTo(interaction);
    }

    @Test
    public void getCurrentService_noInteraction_hasRecognition_returnsRecogntion() {
        ComponentName recognition = new ComponentName(TEST_PACKAGE, TEST_CLASS_2);
        Settings.Secure.putString(mContext.getContentResolver(),
                Settings.Secure.VOICE_INTERACTION_SERVICE, "");
        Settings.Secure.putString(mContext.getContentResolver(),
                Settings.Secure.VOICE_RECOGNITION_SERVICE, recognition.flattenToString());
        assertThat(VoiceInputUtils.getCurrentService(mContext)).isEqualTo(recognition);
    }
}
