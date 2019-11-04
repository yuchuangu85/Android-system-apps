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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertThrows;

import android.app.PendingIntent;
import android.content.Context;
import android.service.settings.suggestions.Suggestion;

import androidx.lifecycle.Lifecycle;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.Loader;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceGroup;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.settingslib.suggestions.SuggestionController;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.util.ReflectionHelpers;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Unit test for {@link SuggestionsPreferenceController}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
public class SuggestionsPreferenceControllerTest {

    private static final Suggestion SUGGESTION_1 = new Suggestion.Builder("1").build();
    private static final Suggestion SUGGESTION_2 = new Suggestion.Builder("2").build();

    @Mock
    private LoaderManager mLoaderManager;
    @Mock
    private Loader<List<Suggestion>> mLoader;
    @Mock
    private SuggestionController mSuggestionController;
    private Context mContext;
    private PreferenceGroup mGroup;
    private PreferenceControllerTestHelper<SuggestionsPreferenceController> mControllerHelper;
    private SuggestionsPreferenceController mController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mContext = RuntimeEnvironment.application;
        mGroup = new PreferenceCategory(mContext);

        mControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                SuggestionsPreferenceController.class);
        mController = mControllerHelper.getController();
        mController.setLoaderManager(mLoaderManager);
        ReflectionHelpers.setField(SuggestionsPreferenceController.class, mController,
                "mSuggestionController", mSuggestionController);
        mControllerHelper.setPreference(mGroup);
        mControllerHelper.markState(Lifecycle.State.STARTED);
    }

    @Test
    public void setPreference_loaderManagerSet_doesNothing() {
        PreferenceControllerTestHelper<SuggestionsPreferenceController> helper =
                new PreferenceControllerTestHelper<>(mContext,
                        SuggestionsPreferenceController.class);
        mController = helper.getController();
        mController.setLoaderManager(mLoaderManager);

        helper.setPreference(new PreferenceCategory(mContext));
    }

    @Test
    public void checkInitialized_nullLoaderManager_throwsIllegalStateException() {
        PreferenceControllerTestHelper<SuggestionsPreferenceController> helper =
                new PreferenceControllerTestHelper<>(mContext,
                        SuggestionsPreferenceController.class);

        assertThrows(IllegalStateException.class,
                () -> helper.setPreference(new PreferenceCategory(mContext)));
    }

    @Test
    public void onStart_noSuggestions_hidesGroup() {
        assertThat(mGroup.isVisible()).isFalse();
    }

    @Test
    public void onStart_startsSuggestionController() {
        verify(mSuggestionController).start();
    }

    @Test
    public void onStop_stopsSuggestionController() {
        mControllerHelper.markState(Lifecycle.State.CREATED);

        verify(mSuggestionController).stop();
    }

    @Test
    public void onStop_destroysLoader() {
        mControllerHelper.markState(Lifecycle.State.CREATED);

        verify(mLoaderManager).destroyLoader(SettingsSuggestionsLoader.LOADER_ID_SUGGESTIONS);
    }

    @Test
    public void onServiceConnected_restartsLoader() {
        mController.onServiceConnected();

        verify(mLoaderManager).restartLoader(
                SettingsSuggestionsLoader.LOADER_ID_SUGGESTIONS, /* args= */ null, mController);
    }

    @Test
    public void onServiceDisconnected_destroysLoader() {
        mController.onServiceDisconnected();

        verify(mLoaderManager).destroyLoader(SettingsSuggestionsLoader.LOADER_ID_SUGGESTIONS);
    }

    @Test
    public void onCreateLoader_returnsSettingsSuggestionsLoader() {
        assertThat(mController.onCreateLoader(
                SettingsSuggestionsLoader.LOADER_ID_SUGGESTIONS, /* args= */ null)).isInstanceOf(
                SettingsSuggestionsLoader.class);
    }

    @Test
    public void onCreateLoader_unsupportedId_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () -> mController.onCreateLoader(
                SettingsSuggestionsLoader.LOADER_ID_SUGGESTIONS + 1000, /* args= */ null));
    }

    @Test
    public void onLoadFinished_groupContainsSuggestionPreference() {
        mController.onLoadFinished(mLoader, Collections.singletonList(SUGGESTION_1));

        assertThat(mGroup.getPreferenceCount()).isEqualTo(1);
        Preference addedPref = mGroup.getPreference(0);
        assertThat(addedPref).isInstanceOf(SuggestionPreference.class);
    }

    @Test
    public void onLoadFinished_newSuggestion_addsToGroup() {
        mController.onLoadFinished(mLoader, Collections.singletonList(SUGGESTION_1));
        assertThat(mGroup.getPreferenceCount()).isEqualTo(1);

        mController.onLoadFinished(mLoader, Arrays.asList(SUGGESTION_1, SUGGESTION_2));

        assertThat(mGroup.getPreferenceCount()).isEqualTo(2);
    }

    @Test
    public void onLoadFinished_removedSuggestion_removesFromGroup() {
        mController.onLoadFinished(mLoader, Arrays.asList(SUGGESTION_1, SUGGESTION_2));
        assertThat(mGroup.getPreferenceCount()).isEqualTo(2);

        mController.onLoadFinished(mLoader, Collections.singletonList(SUGGESTION_2));

        assertThat(mGroup.getPreferenceCount()).isEqualTo(1);
        assertThat(((SuggestionPreference) mGroup.getPreference(0)).getSuggestion()).isEqualTo(
                SUGGESTION_2);
    }

    @Test
    public void onLoadFinished_noSuggestions_hidesGroup() {
        mController.onLoadFinished(mLoader, Collections.singletonList(SUGGESTION_1));
        assertThat(mGroup.isVisible()).isTrue();

        mController.onLoadFinished(mLoader, Collections.emptyList());

        assertThat(mGroup.isVisible()).isFalse();
    }

    @Test
    public void launchSuggestion_sendsPendingIntent() throws PendingIntent.CanceledException {
        PendingIntent pendingIntent = mock(PendingIntent.class);
        Suggestion suggestion = new Suggestion.Builder("1").setPendingIntent(pendingIntent).build();
        SuggestionPreference preference = new SuggestionPreference(mContext,
                suggestion, /* callback= */ null);

        mController.launchSuggestion(preference);

        verify(pendingIntent).send();
    }

    @Test
    public void launchSuggestion_callsSuggestionControllerLaunch() {
        PendingIntent pendingIntent = mock(PendingIntent.class);
        Suggestion suggestion = new Suggestion.Builder("1").setPendingIntent(pendingIntent).build();
        SuggestionPreference preference = new SuggestionPreference(mContext,
                suggestion, /* callback= */ null);

        mController.launchSuggestion(preference);

        verify(mSuggestionController).launchSuggestion(suggestion);
    }

    @Test
    public void dismissSuggestion_removesSuggestion() {
        mController.onLoadFinished(mLoader, Arrays.asList(SUGGESTION_1, SUGGESTION_2));
        assertThat(mGroup.getPreferenceCount()).isEqualTo(2);
        SuggestionPreference pref = (SuggestionPreference) mGroup.getPreference(0);

        mController.dismissSuggestion(pref);

        assertThat(mGroup.getPreferenceCount()).isEqualTo(1);
        assertThat(((SuggestionPreference) mGroup.getPreference(0)).getSuggestion()).isEqualTo(
                SUGGESTION_2);
    }

    @Test
    public void dismissSuggestion_lastSuggestion_hidesGroup() {
        mController.onLoadFinished(mLoader, Collections.singletonList(SUGGESTION_1));
        SuggestionPreference pref = (SuggestionPreference) mGroup.getPreference(0);

        mController.dismissSuggestion(pref);

        assertThat(mGroup.isVisible()).isFalse();
    }

    @Test
    public void dismissSuggestion_callsSuggestionControllerDismiss() {
        mController.onLoadFinished(mLoader, Collections.singletonList(SUGGESTION_1));
        SuggestionPreference pref = (SuggestionPreference) mGroup.getPreference(0);

        mController.dismissSuggestion(pref);

        verify(mSuggestionController).dismissSuggestions(pref.getSuggestion());
    }
}
