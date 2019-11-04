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

package com.android.car.settings.tts;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TtsEngines;

import androidx.lifecycle.Lifecycle;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.ButtonPreference;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowTtsEngines;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;

@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowTtsEngines.class})
public class PreferredEngineEntryPreferenceControllerTest {

    private static final TextToSpeech.EngineInfo ENGINE_INFO = new TextToSpeech.EngineInfo();
    private static final String INTENT_ACTION = "test_action";

    static {
        ENGINE_INFO.label = "Test Engine";
        ENGINE_INFO.name = "com.android.car.settings.tts.test.Engine";
    }

    private ButtonPreference mPreference;
    @Mock
    private TtsEngines mEnginesHelper;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ShadowTtsEngines.setInstance(mEnginesHelper);
        Context context = RuntimeEnvironment.application;

        mPreference = new ButtonPreference(context);
        PreferenceControllerTestHelper<PreferredEngineEntryPreferenceController> controllerHelper =
                new PreferenceControllerTestHelper<>(context,
                        PreferredEngineEntryPreferenceController.class, mPreference);

        Intent intent = new Intent(INTENT_ACTION);
        when(mEnginesHelper.getSettingsIntent(ENGINE_INFO.name)).thenReturn(intent);
        when(mEnginesHelper.getEngineInfo(ENGINE_INFO.name)).thenReturn(
                ENGINE_INFO);
        when(mEnginesHelper.getDefaultEngine()).thenReturn(ENGINE_INFO.name);

        controllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
    }

    @After
    public void tearDown() {
        ShadowTtsEngines.reset();
    }

    @Test
    public void performButtonClick_navigateToNextActivity() {
        mPreference.performButtonClick();

        Intent actual = ShadowApplication.getInstance().getNextStartedActivity();
        assertThat(actual.getAction()).isEqualTo(INTENT_ACTION);
    }
}
