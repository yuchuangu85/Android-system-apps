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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TtsEngines;
import android.speech.tts.Voice;

import androidx.lifecycle.Lifecycle;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.common.ActivityResultCallback;
import com.android.car.settings.common.LogicalPreferenceGroup;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.common.SeekBarPreference;
import com.android.car.settings.testutils.ShadowSecureSettings;
import com.android.car.settings.testutils.ShadowTextToSpeech;
import com.android.car.settings.testutils.ShadowTtsEngines;

import com.google.android.collect.Lists;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Locale;

@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowTextToSpeech.class, ShadowTtsEngines.class,
        ShadowSecureSettings.class})
public class TtsPlaybackPreferenceControllerTest {

    private static final String DEFAULT_ENGINE_NAME = "com.android.car.settings.tts.test.default";
    private static final TextToSpeech.EngineInfo ENGINE_INFO = new TextToSpeech.EngineInfo();
    private static final Voice VOICE = new Voice("Test Name", Locale.ENGLISH, /* quality= */0,
            /* latency= */ 0, /* requiresNetworkConnection= */ true, /* features= */ null);

    static {
        ENGINE_INFO.label = "Test Engine";
        ENGINE_INFO.name = "com.android.car.settings.tts.test.other";
    }

    private Context mContext;
    private PreferenceControllerTestHelper<TtsPlaybackPreferenceController>
            mControllerHelper;
    private TtsPlaybackPreferenceController mController;
    private PreferenceGroup mPreferenceGroup;
    private ListPreference mDefaultLanguagePreference;
    private SeekBarPreference mSpeechRatePreference;
    private SeekBarPreference mVoicePitchPreference;
    private Preference mResetPreference;
    @Mock
    private TextToSpeech mTextToSpeech;
    @Mock
    private TtsEngines mEnginesHelper;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ShadowTextToSpeech.setInstance(mTextToSpeech);
        ShadowTtsEngines.setInstance(mEnginesHelper);

        mContext = RuntimeEnvironment.application;
        mPreferenceGroup = new LogicalPreferenceGroup(mContext);
        mControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                TtsPlaybackPreferenceController.class, mPreferenceGroup);

        mDefaultLanguagePreference = new ListPreference(mContext);
        mDefaultLanguagePreference.setKey(mContext.getString(R.string.pk_tts_default_language));
        mPreferenceGroup.addPreference(mDefaultLanguagePreference);

        mSpeechRatePreference = new SeekBarPreference(mContext);
        mSpeechRatePreference.setKey(mContext.getString(R.string.pk_tts_speech_rate));
        mPreferenceGroup.addPreference(mSpeechRatePreference);

        mVoicePitchPreference = new SeekBarPreference(mContext);
        mVoicePitchPreference.setKey(mContext.getString(R.string.pk_tts_pitch));
        mPreferenceGroup.addPreference(mVoicePitchPreference);

        mResetPreference = new Preference(mContext);
        mResetPreference.setKey(mContext.getString(R.string.pk_tts_reset));
        mPreferenceGroup.addPreference(mResetPreference);

        mController = mControllerHelper.getController();

        when(mTextToSpeech.getCurrentEngine()).thenReturn(ENGINE_INFO.name);
        when(mTextToSpeech.getVoice()).thenReturn(VOICE);
        when(mEnginesHelper.parseLocaleString(VOICE.getLocale().toString())).thenReturn(
                VOICE.getLocale());
        when(mEnginesHelper.parseLocaleString(Locale.CANADA.toString())).thenReturn(Locale.CANADA);
        when(mEnginesHelper.parseLocaleString(Locale.KOREA.toString())).thenReturn(Locale.KOREA);

        Settings.Secure.putInt(mContext.getContentResolver(), Settings.Secure.TTS_DEFAULT_RATE,
                TextToSpeech.Engine.DEFAULT_RATE);
        Settings.Secure.putInt(mContext.getContentResolver(), Settings.Secure.TTS_DEFAULT_PITCH,
                TextToSpeech.Engine.DEFAULT_PITCH);
    }

    @After
    public void tearDown() {
        ShadowTtsEngines.reset();
        ShadowTextToSpeech.reset();
        ShadowSecureSettings.reset();
    }

    @Test
    public void onCreate_startsCheckVoiceData() {
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);

        ArgumentCaptor<Intent> intent = ArgumentCaptor.forClass(Intent.class);
        verify(mControllerHelper.getMockFragmentController()).startActivityForResult(
                intent.capture(), eq(TtsPlaybackPreferenceController.VOICE_DATA_CHECK),
                any(ActivityResultCallback.class));

        assertThat(intent.getValue().getAction()).isEqualTo(
                TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        assertThat(intent.getValue().getPackage()).isEqualTo(ENGINE_INFO.name);
    }

    @Test
    public void voiceDataCheck_processActivityResult_dataIsNull_defaultSynthRemainsUnchanged() {
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
        Settings.Secure.putString(mContext.getContentResolver(), Settings.Secure.TTS_DEFAULT_SYNTH,
                DEFAULT_ENGINE_NAME);

        mController.processActivityResult(
                TtsPlaybackPreferenceController.VOICE_DATA_CHECK,
                TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, /* data= */ null);

        assertThat(Settings.Secure.getString(mContext.getContentResolver(),
                Settings.Secure.TTS_DEFAULT_SYNTH)).isEqualTo(DEFAULT_ENGINE_NAME);
    }

    @Test
    public void voiceDataCheck_processActivityResult_dataIsNotNull_updatesDefaultSynth() {
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
        Settings.Secure.putString(mContext.getContentResolver(), Settings.Secure.TTS_DEFAULT_SYNTH,
                DEFAULT_ENGINE_NAME);

        Intent data = new Intent();
        mController.processActivityResult(
                TtsPlaybackPreferenceController.VOICE_DATA_CHECK,
                TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, data);

        assertThat(Settings.Secure.getString(mContext.getContentResolver(),
                Settings.Secure.TTS_DEFAULT_SYNTH)).isEqualTo(ENGINE_INFO.name);
    }

    @Test
    public void voiceDataCheck_processActivityResult_checkSuccess_hasVoices_populatesPreference() {
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);

        // Check that the length is 0 initially.
        assertThat(mDefaultLanguagePreference.getEntries()).isNull();

        Intent data = new Intent();
        data.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES,
                Lists.newArrayList(
                        Locale.ENGLISH.toString(),
                        Locale.CANADA.toString(),
                        Locale.KOREA.toString()
                ));

        ShadowTextToSpeech.callInitializationCallbackWithStatus(TextToSpeech.SUCCESS);
        mController.processActivityResult(TtsPlaybackPreferenceController.VOICE_DATA_CHECK,
                TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, data);

        // Length is 3 languages + default language.
        assertThat(mDefaultLanguagePreference.getEntries().length).isEqualTo(4);
    }

    @Test
    public void getSampleText_processActivityResult_dataIsNull_setsDefaultText() {
        mController.processActivityResult(TtsPlaybackPreferenceController.GET_SAMPLE_TEXT,
                TextToSpeech.LANG_AVAILABLE, /* data= */ null);

        assertThat(mController.getSampleText()).isEqualTo(
                mContext.getString(R.string.tts_default_sample_string));
    }

    @Test
    public void getSampleText_processActivityResult_emptyText_setsDefaultText() {
        String testData = "";

        Intent data = new Intent();
        data.putExtra(TextToSpeech.Engine.EXTRA_SAMPLE_TEXT, testData);

        mController.processActivityResult(TtsPlaybackPreferenceController.GET_SAMPLE_TEXT,
                TextToSpeech.LANG_AVAILABLE, data);

        assertThat(mController.getSampleText()).isEqualTo(
                mContext.getString(R.string.tts_default_sample_string));
    }

    @Test
    public void getSampleText_processActivityResult_dataIsNotNull_setsCorrectText() {
        String testData = "Test sample text";

        Intent data = new Intent();
        data.putExtra(TextToSpeech.Engine.EXTRA_SAMPLE_TEXT, testData);

        mController.processActivityResult(TtsPlaybackPreferenceController.GET_SAMPLE_TEXT,
                TextToSpeech.LANG_AVAILABLE, data);

        assertThat(mController.getSampleText()).isEqualTo(testData);
    }

    @Test
    public void defaultLanguage_handlePreferenceChanged_passEmptyValue_setsDefault() {
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);

        Intent data = new Intent();
        data.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES,
                Lists.newArrayList(
                        Locale.ENGLISH.toString(),
                        Locale.CANADA.toString(),
                        Locale.KOREA.toString()
                ));
        mController.processActivityResult(TtsPlaybackPreferenceController.VOICE_DATA_CHECK,
                TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, data);

        // Test change listener.
        mDefaultLanguagePreference.callChangeListener("");

        verify(mTextToSpeech).setLanguage(Locale.getDefault());
    }

    @Test
    public void defaultLanguage_handlePreferenceChanged_passLocale_setsLocale() {
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);

        Intent data = new Intent();
        data.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES,
                Lists.newArrayList(
                        Locale.ENGLISH.toString(),
                        Locale.CANADA.toString(),
                        Locale.KOREA.toString()
                ));
        mController.processActivityResult(TtsPlaybackPreferenceController.VOICE_DATA_CHECK,
                TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, data);

        // Test change listener.
        mDefaultLanguagePreference.callChangeListener(Locale.ENGLISH.toString());

        verify(mTextToSpeech).setLanguage(Locale.ENGLISH);
    }

    @Test
    public void defaultLanguage_handlePreferenceChanged_passLocale_setsSummary() {
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);

        Intent data = new Intent();
        data.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES,
                Lists.newArrayList(
                        Locale.ENGLISH.toString(),
                        Locale.CANADA.toString(),
                        Locale.KOREA.toString()
                ));
        mController.processActivityResult(TtsPlaybackPreferenceController.VOICE_DATA_CHECK,
                TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, data);
        mDefaultLanguagePreference.callChangeListener(Locale.ENGLISH.toString());

        assertThat(mDefaultLanguagePreference.getSummary()).isEqualTo(
                Locale.ENGLISH.getDisplayName());
    }

    @Test
    public void speechRate_handlePreferenceChanged_updatesSecureSettings() {
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);

        int newSpeechRate = TextToSpeech.Engine.DEFAULT_RATE + 40;
        mSpeechRatePreference.callChangeListener(newSpeechRate);

        assertThat(Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.TTS_DEFAULT_RATE, TextToSpeech.Engine.DEFAULT_RATE)).isEqualTo(
                newSpeechRate);
    }

    @Test
    public void speechRate_handlePreferenceChanged_updatesTts() {
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);

        int newSpeechRate = TextToSpeech.Engine.DEFAULT_RATE + 40;
        mSpeechRatePreference.callChangeListener(newSpeechRate);

        verify(mTextToSpeech).setSpeechRate(
                newSpeechRate / TtsPlaybackSettingsManager.SCALING_FACTOR);
    }

    @Test
    public void speechRate_handlePreferenceChanged_speaksSampleText() {
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);

        int newSpeechRate = TextToSpeech.Engine.DEFAULT_RATE + 40;
        mSpeechRatePreference.callChangeListener(newSpeechRate);

        verify(mTextToSpeech).speak(any(), eq(TextToSpeech.QUEUE_FLUSH), isNull(), eq("Sample"));
    }

    @Test
    public void voicePitch_handlePreferenceChanged_updatesSecureSettings() {
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);

        int newVoicePitch = TextToSpeech.Engine.DEFAULT_PITCH + 40;
        mVoicePitchPreference.callChangeListener(newVoicePitch);

        assertThat(Settings.Secure.getInt(mContext.getContentResolver(),
                Settings.Secure.TTS_DEFAULT_PITCH, TextToSpeech.Engine.DEFAULT_PITCH)).isEqualTo(
                newVoicePitch);
    }

    @Test
    public void voicePitch_handlePreferenceChanged_updatesTts() {
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);

        int newVoicePitch = TextToSpeech.Engine.DEFAULT_PITCH + 40;
        mVoicePitchPreference.callChangeListener(newVoicePitch);

        verify(mTextToSpeech).setPitch(
                newVoicePitch / TtsPlaybackSettingsManager.SCALING_FACTOR);
    }

    @Test
    public void voicePitch_handlePreferenceChanged_speaksSampleText() {
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);

        int newVoicePitch = TextToSpeech.Engine.DEFAULT_PITCH + 40;
        mVoicePitchPreference.callChangeListener(newVoicePitch);

        verify(mTextToSpeech).speak(any(), eq(TextToSpeech.QUEUE_FLUSH), isNull(), eq("Sample"));
    }

    @Test
    public void refreshUi_notInitialized_disablesPreference() {
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);

        mController.refreshUi();

        assertThat(mDefaultLanguagePreference.isEnabled()).isFalse();
        assertThat(mSpeechRatePreference.isEnabled()).isFalse();
        assertThat(mVoicePitchPreference.isEnabled()).isFalse();
        assertThat(mResetPreference.isEnabled()).isFalse();
    }

    @Test
    public void refreshUi_initialized_defaultLocaleIsNull_disablesPreference() {
        when(mEnginesHelper.parseLocaleString(any())).thenReturn(null);

        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
        ShadowTextToSpeech.callInitializationCallbackWithStatus(TextToSpeech.SUCCESS);

        mController.refreshUi();

        assertThat(mDefaultLanguagePreference.isEnabled()).isFalse();
        assertThat(mSpeechRatePreference.isEnabled()).isFalse();
        assertThat(mVoicePitchPreference.isEnabled()).isFalse();
        assertThat(mResetPreference.isEnabled()).isFalse();
    }

    @Test
    public void refreshUi_defaultLocaleNotSupported_disablesPreferencesExceptLanguage() {
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
        ShadowTextToSpeech.callInitializationCallbackWithStatus(TextToSpeech.SUCCESS);

        Intent data = new Intent();
        data.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES,
                Lists.newArrayList(
                        // English is default locale, but isn't available.
                        Locale.CANADA.toString(),
                        Locale.KOREA.toString()
                ));
        mController.processActivityResult(TtsPlaybackPreferenceController.VOICE_DATA_CHECK,
                TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, data);

        mController.refreshUi();

        assertThat(mDefaultLanguagePreference.isEnabled()).isTrue();
        assertThat(mSpeechRatePreference.isEnabled()).isFalse();
        assertThat(mVoicePitchPreference.isEnabled()).isFalse();
        assertThat(mResetPreference.isEnabled()).isFalse();
    }

    @Test
    public void refreshUi_initialized_defaultLocaleSupported_enablesPreference() {
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
        ShadowTextToSpeech.callInitializationCallbackWithStatus(TextToSpeech.SUCCESS);

        Intent data = new Intent();
        data.putStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES,
                Lists.newArrayList(
                        Locale.ENGLISH.toString(),
                        Locale.CANADA.toString(),
                        Locale.KOREA.toString()
                ));
        mController.processActivityResult(TtsPlaybackPreferenceController.VOICE_DATA_CHECK,
                TextToSpeech.Engine.CHECK_VOICE_DATA_PASS, data);

        mController.refreshUi();

        assertThat(mPreferenceGroup.isEnabled()).isTrue();
        assertThat(mDefaultLanguagePreference.isEnabled()).isTrue();
        assertThat(mSpeechRatePreference.isEnabled()).isTrue();
        assertThat(mVoicePitchPreference.isEnabled()).isTrue();
        assertThat(mResetPreference.isEnabled()).isTrue();
    }
}
