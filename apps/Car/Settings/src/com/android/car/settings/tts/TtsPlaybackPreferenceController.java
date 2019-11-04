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

import android.car.drivingstate.CarUxRestrictions;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TtsEngines;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import com.android.car.settings.R;
import com.android.car.settings.common.ActivityResultCallback;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;
import com.android.car.settings.common.PreferenceController;
import com.android.car.settings.common.SeekBarPreference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.Objects;

/**
 * Business logic for configuring and listening to the current TTS voice. This preference contorller
 * handles the following:
 *
 * <ol>
 * <li>Changing the TTS language
 * <li>Changing the TTS speech rate
 * <li>Changing the TTS voice pitch
 * <li>Resetting the TTS configuration
 * </ol>
 */
public class TtsPlaybackPreferenceController extends
        PreferenceController<PreferenceGroup> implements ActivityResultCallback {

    private static final Logger LOG = new Logger(TtsPlaybackPreferenceController.class);

    @VisibleForTesting
    static final int VOICE_DATA_CHECK = 1;
    @VisibleForTesting
    static final int GET_SAMPLE_TEXT = 2;

    private final TtsEngines mEnginesHelper;
    private TtsPlaybackSettingsManager mTtsPlaybackManager;
    private TextToSpeech mTts;
    private int mSelectedLocaleIndex;

    private ListPreference mDefaultLanguagePreference;
    private SeekBarPreference mSpeechRatePreference;
    private SeekBarPreference mVoicePitchPreference;
    private Preference mResetPreference;

    private String mSampleText;
    private Locale mSampleTextLocale;

    /** True if initialized with no errors. */
    private boolean mTtsInitialized = false;

    private final TextToSpeech.OnInitListener mOnInitListener = status -> {
        if (status == TextToSpeech.SUCCESS) {
            mTtsInitialized = true;
            refreshUi();
        }
    };

    public TtsPlaybackPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mEnginesHelper = new TtsEngines(context);
    }

    @Override
    protected Class<PreferenceGroup> getPreferenceType() {
        return PreferenceGroup.class;
    }

    @Override
    protected void onCreateInternal() {
        mDefaultLanguagePreference = initDefaultLanguagePreference();
        mSpeechRatePreference = initSpeechRatePreference();
        mVoicePitchPreference = initVoicePitchPreference();
        mResetPreference = initResetTtsPlaybackPreference();

        mTts = new TextToSpeech(getContext(), mOnInitListener);
        mTtsPlaybackManager = new TtsPlaybackSettingsManager(getContext(), mTts, mEnginesHelper);
        mTts.setSpeechRate(mTtsPlaybackManager.getCurrentSpeechRate()
                / TtsPlaybackSettingsManager.SCALING_FACTOR);
        mTts.setPitch(mTtsPlaybackManager.getCurrentVoicePitch()
                / TtsPlaybackSettingsManager.SCALING_FACTOR);
        startEngineVoiceDataCheck(mTts.getCurrentEngine());
    }

    @Override
    protected void onDestroyInternal() {
        if (mTts != null) {
            mTts.shutdown();
            mTts = null;
            mTtsPlaybackManager = null;
        }
    }

    @Override
    protected void updateState(PreferenceGroup preference) {
        boolean isValid = isDefaultLocaleValid();
        mDefaultLanguagePreference.setEnabled(isValid);
        mSpeechRatePreference.setEnabled(isValid);
        mVoicePitchPreference.setEnabled(isValid);
        mResetPreference.setEnabled(isValid);
        if (!isValid && mDefaultLanguagePreference.getEntries() != null) {
            mDefaultLanguagePreference.setEnabled(true);
        }

        if (mDefaultLanguagePreference.getEntries() != null) {
            mDefaultLanguagePreference.setValueIndex(mSelectedLocaleIndex);
            mDefaultLanguagePreference.setSummary(
                    mDefaultLanguagePreference.getEntries()[mSelectedLocaleIndex]);
        }

        mSpeechRatePreference.setValue(mTtsPlaybackManager.getCurrentSpeechRate());
        mVoicePitchPreference.setValue(mTtsPlaybackManager.getCurrentVoicePitch());
        checkOrUpdateSampleText();
    }

    @Override
    public void processActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        switch (requestCode) {
            case VOICE_DATA_CHECK:
                onVoiceDataIntegrityCheckDone(resultCode, data);
                break;
            case GET_SAMPLE_TEXT:
                onSampleTextReceived(resultCode, data);
                break;
            default:
                LOG.e("Got unknown activity result");
        }
    }

    private void startEngineVoiceDataCheck(String engine) {
        Intent intent = new Intent(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
        intent.setPackage(engine);
        try {
            LOG.d("Updating engine: Checking voice data: " + intent.toUri(0));
            getFragmentController().startActivityForResult(intent, VOICE_DATA_CHECK,
                    this);
        } catch (ActivityNotFoundException ex) {
            LOG.e("Failed to check TTS data, no activity found for " + intent);
        }
    }

    /**
     * Ask the current default engine to return a string of sample text to be
     * spoken to the user.
     */
    private void startGetSampleText() {
        String currentEngine = mTts.getCurrentEngine();
        if (TextUtils.isEmpty(currentEngine)) {
            currentEngine = mTts.getDefaultEngine();
        }

        Intent intent = new Intent(TextToSpeech.Engine.ACTION_GET_SAMPLE_TEXT);
        mSampleTextLocale = mTtsPlaybackManager.getEffectiveTtsLocale();
        if (mSampleTextLocale == null) {
            return;
        }
        intent.putExtra(TextToSpeech.Engine.KEY_PARAM_LANGUAGE, mSampleTextLocale.getLanguage());
        intent.putExtra(TextToSpeech.Engine.KEY_PARAM_COUNTRY, mSampleTextLocale.getCountry());
        intent.putExtra(TextToSpeech.Engine.KEY_PARAM_VARIANT, mSampleTextLocale.getVariant());
        intent.setPackage(currentEngine);

        try {
            LOG.d("Getting sample text: " + intent.toUri(0));
            getFragmentController().startActivityForResult(intent, GET_SAMPLE_TEXT, this);
        } catch (ActivityNotFoundException ex) {
            LOG.e("Failed to get sample text, no activity found for " + intent + ")");
        }
    }

    /** The voice data check is complete. */
    private void onVoiceDataIntegrityCheckDone(int resultCode, Intent data) {
        String engine = mTts.getCurrentEngine();
        if (engine == null) {
            LOG.e("Voice data check complete, but no engine bound");
            return;
        }

        if (data == null || resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_FAIL) {
            LOG.e("Engine failed voice data integrity check (null return or invalid result code)"
                    + mTts.getCurrentEngine());
            return;
        }

        Settings.Secure.putString(getContext().getContentResolver(),
                Settings.Secure.TTS_DEFAULT_SYNTH, engine);

        ArrayList<String> availableLangs =
                data.getStringArrayListExtra(TextToSpeech.Engine.EXTRA_AVAILABLE_VOICES);
        if (availableLangs == null || availableLangs.size() == 0) {
            refreshUi();
            return;
        }

        updateDefaultLanguagePreference(availableLangs);

        mSelectedLocaleIndex = findLocaleIndex(mTtsPlaybackManager.getStoredTtsLocale());
        if (mSelectedLocaleIndex < 0) {
            mSelectedLocaleIndex = 0;
        }
        startGetSampleText();
        refreshUi();
    }

    private void onSampleTextReceived(int resultCode, Intent data) {
        String sample = getContext().getString(R.string.tts_default_sample_string);

        if (resultCode == TextToSpeech.LANG_AVAILABLE && data != null) {
            String tmp = data.getStringExtra(TextToSpeech.Engine.EXTRA_SAMPLE_TEXT);
            if (!TextUtils.isEmpty(tmp)) {
                sample = tmp;
            }
            LOG.d("Got sample text: " + sample);
        } else {
            LOG.d("Using default sample text :" + sample);
        }

        mSampleText = sample;
    }

    private void updateLanguageTo(Locale locale) {
        int selectedLocaleIndex = findLocaleIndex(locale);
        if (selectedLocaleIndex == -1) {
            LOG.w("updateLanguageTo called with unknown locale argument");
            return;
        }

        if (mTtsPlaybackManager.updateTtsLocale(locale)) {
            mSelectedLocaleIndex = selectedLocaleIndex;
            refreshUi();
        } else {
            LOG.e("updateLanguageTo failed to update tts language");
        }
    }

    private int findLocaleIndex(Locale locale) {
        String localeString = (locale != null) ? locale.toString() : "";
        return mDefaultLanguagePreference.findIndexOfValue(localeString);
    }

    private boolean isDefaultLocaleValid() {
        if (!mTtsInitialized) {
            return false;
        }

        Locale defaultLocale = mTtsPlaybackManager.getEffectiveTtsLocale();
        if (defaultLocale == null) {
            LOG.e("Failed to get default language from engine " + mTts.getCurrentEngine());
            return false;
        }

        if (mDefaultLanguagePreference.getEntries() == null) {
            return false;
        }

        int index = mDefaultLanguagePreference.findIndexOfValue(defaultLocale.toString());
        if (index < 0) {
            return false;
        }

        return true;
    }

    private void checkOrUpdateSampleText() {
        if (!mTtsInitialized) {
            return;
        }
        Locale defaultLocale = mTtsPlaybackManager.getEffectiveTtsLocale();
        if (defaultLocale == null) {
            LOG.e("Failed to get default language from engine " + mTts.getCurrentEngine());
            return;
        }

        if (!Objects.equals(defaultLocale, mSampleTextLocale)) {
            mSampleText = null;
            mSampleTextLocale = null;
        }

        if (mSampleText == null) {
            startGetSampleText();
        }
    }

    @VisibleForTesting
    String getSampleText() {
        return mSampleText;
    }

    /* ***************************************************************************************** *
     * Preference initialization/update code.                                                    *
     * ***************************************************************************************** */

    private ListPreference initDefaultLanguagePreference() {
        ListPreference defaultLanguagePreference = (ListPreference) getPreference().findPreference(
                getContext().getString(R.string.pk_tts_default_language));
        defaultLanguagePreference.setOnPreferenceChangeListener((preference, newValue) -> {
            String localeString = (String) newValue;
            updateLanguageTo(!TextUtils.isEmpty(localeString) ? mEnginesHelper.parseLocaleString(
                    localeString) : null);
            checkOrUpdateSampleText();
            return true;
        });
        return defaultLanguagePreference;
    }

    private void updateDefaultLanguagePreference(@NonNull ArrayList<String> availableLangs) {
        // Sort locales by display name.
        ArrayList<Locale> locales = new ArrayList<>();
        for (int i = 0; i < availableLangs.size(); i++) {
            Locale locale = mEnginesHelper.parseLocaleString(availableLangs.get(i));
            if (locale != null) {
                locales.add(locale);
            }
        }
        Collections.sort(locales,
                (lhs, rhs) -> lhs.getDisplayName().compareToIgnoreCase(rhs.getDisplayName()));

        // Separate pairs into two separate arrays.
        CharSequence[] entries = new CharSequence[availableLangs.size() + 1];
        CharSequence[] entryValues = new CharSequence[availableLangs.size() + 1];

        entries[0] = getContext().getString(R.string.tts_lang_use_system);
        entryValues[0] = "";

        int i = 1;
        for (Locale locale : locales) {
            entries[i] = locale.getDisplayName();
            entryValues[i++] = locale.toString();
        }

        mDefaultLanguagePreference.setEntries(entries);
        mDefaultLanguagePreference.setEntryValues(entryValues);
    }

    private SeekBarPreference initSpeechRatePreference() {
        SeekBarPreference speechRatePreference = (SeekBarPreference) getPreference().findPreference(
                getContext().getString(R.string.pk_tts_speech_rate));
        speechRatePreference.setMin(TtsPlaybackSettingsManager.MIN_SPEECH_RATE);
        speechRatePreference.setMax(TtsPlaybackSettingsManager.MAX_SPEECH_RATE);
        speechRatePreference.setShowSeekBarValue(false);
        speechRatePreference.setContinuousUpdate(false);
        speechRatePreference.setOnPreferenceChangeListener((preference, newValue) -> {
            if (mTtsPlaybackManager != null) {
                mTtsPlaybackManager.updateSpeechRate((Integer) newValue);
                mTtsPlaybackManager.speakSampleText(mSampleText);
                return true;
            }
            LOG.e("speech rate preference enabled before it is allowed");
            return false;
        });

        // Initially disable.
        speechRatePreference.setEnabled(false);
        return speechRatePreference;
    }

    private SeekBarPreference initVoicePitchPreference() {
        SeekBarPreference pitchPreference = (SeekBarPreference) getPreference().findPreference(
                getContext().getString(R.string.pk_tts_pitch));
        pitchPreference.setMin(TtsPlaybackSettingsManager.MIN_VOICE_PITCH);
        pitchPreference.setMax(TtsPlaybackSettingsManager.MAX_VOICE_PITCH);
        pitchPreference.setShowSeekBarValue(false);
        pitchPreference.setContinuousUpdate(false);
        pitchPreference.setOnPreferenceChangeListener((preference, newValue) -> {
            if (mTtsPlaybackManager != null) {
                mTtsPlaybackManager.updateVoicePitch((Integer) newValue);
                mTtsPlaybackManager.speakSampleText(mSampleText);
                return true;
            }
            LOG.e("speech pitch preference enabled before it is allowed");
            return false;
        });

        // Initially disable.
        pitchPreference.setEnabled(false);
        return pitchPreference;
    }

    private Preference initResetTtsPlaybackPreference() {
        Preference resetPreference = getPreference().findPreference(
                getContext().getString(R.string.pk_tts_reset));
        resetPreference.setOnPreferenceClickListener(preference -> {
            if (mTtsPlaybackManager != null) {
                mTtsPlaybackManager.resetVoicePitch();
                mTtsPlaybackManager.resetSpeechRate();
                refreshUi();
                return true;
            }
            LOG.e("reset preference enabled before it is allowed");
            return false;
        });

        // Initially disable.
        resetPreference.setEnabled(false);
        return resetPreference;
    }
}
