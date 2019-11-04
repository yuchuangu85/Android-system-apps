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
import android.speech.tts.TextToSpeech;
import android.speech.tts.TtsEngines;

import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.common.LogicalPreferenceGroup;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowTextToSpeech;
import com.android.car.settings.testutils.ShadowTtsEngines;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Arrays;

@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowTtsEngines.class, ShadowTextToSpeech.class})
public class PreferredEngineOptionsPreferenceControllerTest {
    private static final TextToSpeech.EngineInfo OTHER_ENGINE_INFO = new TextToSpeech.EngineInfo();
    private static final TextToSpeech.EngineInfo CURRENT_ENGINE_INFO =
            new TextToSpeech.EngineInfo();

    static {
        OTHER_ENGINE_INFO.label = "Test Engine 1";
        OTHER_ENGINE_INFO.name = "com.android.car.settings.tts.test.Engine1";
        CURRENT_ENGINE_INFO.label = "Test Engine 2";
        CURRENT_ENGINE_INFO.name = "com.android.car.settings.tts.test.Engine2";
    }

    private Context mContext;
    private PreferenceControllerTestHelper<PreferredEngineOptionsPreferenceController>
            mControllerHelper;
    private PreferredEngineOptionsPreferenceController mController;
    private PreferenceGroup mPreferenceGroup;
    @Mock
    private TtsEngines mEnginesHelper;
    @Mock
    private TextToSpeech mTextToSpeech;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ShadowTtsEngines.setInstance(mEnginesHelper);
        ShadowTextToSpeech.setInstance(mTextToSpeech);

        mContext = RuntimeEnvironment.application;
        mPreferenceGroup = new LogicalPreferenceGroup(mContext);
        mControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                PreferredEngineOptionsPreferenceController.class, mPreferenceGroup);
        mController = mControllerHelper.getController();

        when(mEnginesHelper.getEngines()).thenReturn(
                Arrays.asList(OTHER_ENGINE_INFO, CURRENT_ENGINE_INFO));
        when(mEnginesHelper.getEngineInfo(OTHER_ENGINE_INFO.name)).thenReturn(OTHER_ENGINE_INFO);
        when(mEnginesHelper.getEngineInfo(CURRENT_ENGINE_INFO.name)).thenReturn(
                CURRENT_ENGINE_INFO);
    }

    @After
    public void tearDown() {
        ShadowTtsEngines.reset();
        ShadowTextToSpeech.reset();
    }

    @Test
    public void onCreate_populatesGroup() {
        when(mTextToSpeech.getCurrentEngine()).thenReturn(CURRENT_ENGINE_INFO.name);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(2);
    }

    @Test
    public void refreshUi_currentEngineInfoSummarySet() {
        when(mTextToSpeech.getCurrentEngine()).thenReturn(CURRENT_ENGINE_INFO.name);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
        mController.refreshUi();

        assertThat(
                mPreferenceGroup.findPreference(CURRENT_ENGINE_INFO.name).getSummary()).isEqualTo(
                mContext.getString(R.string.text_to_speech_current_engine));
    }

    @Test
    public void refreshUi_otherEngineInfoSummaryEmpty() {
        when(mTextToSpeech.getCurrentEngine()).thenReturn(CURRENT_ENGINE_INFO.name);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
        mController.refreshUi();

        assertThat(mPreferenceGroup.findPreference(OTHER_ENGINE_INFO.name).getSummary()).isEqualTo(
                "");
    }

    @Test
    public void performClick_currentEngine_returnFalse() {
        when(mTextToSpeech.getCurrentEngine()).thenReturn(CURRENT_ENGINE_INFO.name);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);

        Preference currentEngine = mPreferenceGroup.findPreference(CURRENT_ENGINE_INFO.name);
        assertThat(currentEngine.getOnPreferenceClickListener().onPreferenceClick(
                currentEngine)).isFalse();
    }

    @Test
    public void performClick_otherEngine_returnTrue() {
        when(mTextToSpeech.getCurrentEngine()).thenReturn(CURRENT_ENGINE_INFO.name);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);

        Preference otherEngine = mPreferenceGroup.findPreference(OTHER_ENGINE_INFO.name);
        assertThat(otherEngine.getOnPreferenceClickListener().onPreferenceClick(
                otherEngine)).isTrue();
    }

    @Test
    public void performClick_otherEngine_initSuccess_changeCurrentEngine() {
        when(mTextToSpeech.getCurrentEngine()).thenReturn(CURRENT_ENGINE_INFO.name);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);
        Preference otherEngine = mPreferenceGroup.findPreference(OTHER_ENGINE_INFO.name);
        otherEngine.performClick();

        ShadowTextToSpeech.callInitializationCallbackWithStatus(TextToSpeech.SUCCESS);
        assertThat(ShadowTextToSpeech.getLastConstructedEngine()).isEqualTo(OTHER_ENGINE_INFO.name);
    }

    @Test
    public void performClick_otherEngine_initFail_keepCurrentEngine() {
        when(mTextToSpeech.getCurrentEngine()).thenReturn(CURRENT_ENGINE_INFO.name);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);
        Preference otherEngine = mPreferenceGroup.findPreference(OTHER_ENGINE_INFO.name);
        otherEngine.performClick();

        ShadowTextToSpeech.callInitializationCallbackWithStatus(TextToSpeech.ERROR);
        assertThat(ShadowTextToSpeech.getLastConstructedEngine()).isEqualTo(
                CURRENT_ENGINE_INFO.name);
    }
}
