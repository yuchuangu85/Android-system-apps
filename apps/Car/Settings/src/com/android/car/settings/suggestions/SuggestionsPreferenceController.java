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

package com.android.car.settings.suggestions;

import android.app.PendingIntent;
import android.car.drivingstate.CarUxRestrictions;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;
import android.service.settings.suggestions.Suggestion;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;
import com.android.car.settings.common.PreferenceController;
import com.android.settingslib.suggestions.SuggestionController;

import java.util.ArrayList;
import java.util.List;

/**
 * Injects {@link SuggestionPreference} instances loaded from the SuggestionService at the
 * location in the hierarchy of the controller's placeholder preference. The placeholder should
 * be a {@link PreferenceGroup} which sets the controller attribute to the fully qualified name
 * of this class.
 *
 * <p>For example:
 * <pre>{@code
 * <PreferenceCategory
 *     android:key="@string/pk_suggestions"
 *     android:title="@string/suggestions_title"
 *     settings:controller="com.android.settings.suggestions.SuggestionsPreferenceController"/>
 * }</pre>
 */
public class SuggestionsPreferenceController extends
        PreferenceController<PreferenceGroup> implements
        SuggestionController.ServiceConnectionListener,
        LoaderManager.LoaderCallbacks<List<Suggestion>>, SuggestionPreference.Callback {

    private static final Logger LOG = new Logger(SuggestionsPreferenceController.class);

    // These values are hard coded until we receive the OK to plumb them through
    // SettingsIntelligence. This is ok as right now only SUW uses this framework.
    private static final ComponentName COMPONENT_NAME = new ComponentName(
            "com.android.settings.intelligence",
            "com.android.settings.intelligence.suggestions.SuggestionService");

    private final SuggestionController mSuggestionController;
    private List<Suggestion> mSuggestionsList = new ArrayList<>();
    private LoaderManager mLoaderManager;

    public SuggestionsPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mSuggestionController = new SuggestionController(context,
                COMPONENT_NAME, /* serviceConnectionListener= */ this);
    }

    @Override
    protected Class<PreferenceGroup> getPreferenceType() {
        return PreferenceGroup.class;
    }

    /**
     * Sets the {@link LoaderManager} used to load suggestions.
     */
    public void setLoaderManager(LoaderManager loaderManager) {
        mLoaderManager = loaderManager;
    }

    /**
     * Verifies that the controller was properly initialized with
     * {@link #setLoaderManager(LoaderManager)}.
     *
     * @throws IllegalStateException if the loader manager is {@code null}
     */
    @Override
    protected void checkInitialized() {
        LOG.v("checkInitialized");
        if (mLoaderManager == null) {
            throw new IllegalStateException(
                    "SuggestionPreferenceController must be initialized by calling "
                            + "setLoaderManager(LoaderManager)");
        }
    }

    /** Starts the suggestions controller. */
    @Override
    protected void onStartInternal() {
        LOG.v("onStartInternal");
        mSuggestionController.start();
    }

    /** Stops the suggestions controller. */
    @Override
    protected void onStopInternal() {
        LOG.v("onStopInternal");
        mSuggestionController.stop();
        cleanupLoader();
    }

    @Override
    public void onServiceConnected() {
        LOG.v("onServiceConnected");
        mLoaderManager.restartLoader(SettingsSuggestionsLoader.LOADER_ID_SUGGESTIONS, /* args= */
                null, /* callback= */ this);
    }

    @Override
    public void onServiceDisconnected() {
        LOG.v("onServiceDisconnected");
        cleanupLoader();
    }

    @NonNull
    @Override
    public Loader<List<Suggestion>> onCreateLoader(int id, @Nullable Bundle args) {
        LOG.v("onCreateLoader: " + id);
        if (id == SettingsSuggestionsLoader.LOADER_ID_SUGGESTIONS) {
            return new SettingsSuggestionsLoader(getContext(), mSuggestionController);
        }
        throw new IllegalArgumentException("This loader id is not supported " + id);
    }


    @Override
    public void onLoadFinished(@NonNull Loader<List<Suggestion>> loader,
            List<Suggestion> suggestions) {
        LOG.v("onLoadFinished");
        if (suggestions == null) {
            // Load started before the service was ready.
            return;
        }

        updateSuggestionPreferences(suggestions);
        mSuggestionsList = new ArrayList<>(suggestions);
    }

    private void updateSuggestionPreferences(List<Suggestion> suggestions) {
        // Remove suggestions that are not in the new list.
        for (Suggestion oldSuggestion : mSuggestionsList) {
            boolean isInNewSuggestionList = false;
            for (Suggestion suggestion : suggestions) {
                if (oldSuggestion.getId().equals(suggestion.getId())) {
                    isInNewSuggestionList = true;
                    break;
                }
            }
            if (!isInNewSuggestionList) {
                getPreference().removePreference(
                        getPreference().findPreference(getSuggestionPreferenceKey(oldSuggestion)));
            }
        }

        // Add suggestions that are not in the old list and update the existing suggestions.
        for (Suggestion suggestion : suggestions) {
            Preference curPref = getPreference().findPreference(
                    getSuggestionPreferenceKey(suggestion));
            if (curPref == null) {
                SuggestionPreference newSuggPref = new SuggestionPreference(getContext(),
                        suggestion, /* callback= */ this);
                getPreference().addPreference(newSuggPref);
            } else {
                ((SuggestionPreference) curPref).updateSuggestion(suggestion);
            }
        }

        refreshUi();
    }

    @Override
    public void onLoaderReset(@NonNull Loader<List<Suggestion>> loader) {
        LOG.v("onLoaderReset");
    }

    @Override
    public void launchSuggestion(SuggestionPreference preference) {
        LOG.v("launchSuggestion");
        Suggestion suggestion = preference.getSuggestion();
        try {
            if (suggestion.getPendingIntent() != null) {
                suggestion.getPendingIntent().send();
                mSuggestionController.launchSuggestion(suggestion);
            } else {
                LOG.w("Suggestion with null pending intent " + suggestion.getId());
            }
        } catch (PendingIntent.CanceledException e) {
            LOG.w("Failed to start suggestion " + suggestion.getId());
        }
    }

    @Override
    public void dismissSuggestion(SuggestionPreference preference) {
        LOG.v("dismissSuggestion");
        Suggestion suggestion = preference.getSuggestion();
        mSuggestionController.dismissSuggestions(suggestion);
        mSuggestionsList.remove(suggestion);
        getPreference().removePreference(preference);
        refreshUi();
    }

    @Override
    protected void updateState(PreferenceGroup preference) {
        preference.setVisible(preference.getPreferenceCount() > 0);
    }

    private void cleanupLoader() {
        LOG.v("cleanupLoader");
        mLoaderManager.destroyLoader(SettingsSuggestionsLoader.LOADER_ID_SUGGESTIONS);
    }

    private String getSuggestionPreferenceKey(Suggestion suggestion) {
        return SuggestionPreference.SUGGESTION_PREFERENCE_KEY + suggestion.getId();
    }
}
