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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertThrows;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.graphics.drawable.ShapeDrawable;
import android.service.settings.suggestions.Suggestion;
import android.view.View;

import androidx.appcompat.view.ContextThemeWrapper;
import androidx.preference.PreferenceViewHolder;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;

/** Unit test for {@link SuggestionPreference}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
public class SuggestionPreferenceTest {

    private static final String SUGGESTION_ID = "id";

    @Mock
    private SuggestionPreference.Callback mCallback;
    private Suggestion mSuggestion;
    private PreferenceViewHolder mHolder;
    private SuggestionPreference mPref;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        Context context = RuntimeEnvironment.application;

        mSuggestion = new Suggestion.Builder(SUGGESTION_ID).build();
        Context themedContext = new ContextThemeWrapper(context, R.style.CarSettingTheme);
        View rootView = View.inflate(themedContext, R.layout.suggestion_preference, null);
        mHolder = PreferenceViewHolder.createInstanceForTests(rootView);
        mPref = new SuggestionPreference(context, mSuggestion, mCallback);
    }

    @Test
    public void getSuggestion_returnsSuggestion() {
        assertThat(mPref.getSuggestion()).isEqualTo(mSuggestion);
    }

    @Test
    public void updateSuggestion_updatesPreference() {
        Icon icon = mock(Icon.class);
        Drawable iconDrawable = new ShapeDrawable();
        when(icon.loadDrawable(any(Context.class))).thenReturn(iconDrawable);
        String title = "title";
        String summary = "summary";
        Suggestion updatedSuggestion = new Suggestion.Builder(SUGGESTION_ID).setIcon(icon).setTitle(
                title).setSummary(summary).build();

        mPref.updateSuggestion(updatedSuggestion);

        assertThat(mPref.getSuggestion()).isEqualTo(updatedSuggestion);
        assertThat(mPref.getIcon()).isEqualTo(iconDrawable);
        assertThat(mPref.getTitle()).isEqualTo(title);
        assertThat(mPref.getSummary()).isEqualTo(summary);
    }

    @Test
    public void updateSuggestion_idMismatch_throwsIllegalArgumentException() {
        Suggestion updatedSuggestion = new Suggestion.Builder(SUGGESTION_ID + "wrong id").build();

        assertThrows(IllegalArgumentException.class,
                () -> mPref.updateSuggestion(updatedSuggestion));
    }

    @Test
    public void getKey_includesSuggestionId() {
        assertThat(mPref.getKey()).isEqualTo(
                SuggestionPreference.SUGGESTION_PREFERENCE_KEY + SUGGESTION_ID);
    }

    @Test
    public void onClick_callsLaunchSuggestion() {
        mPref.onBindViewHolder(mHolder);

        mHolder.itemView.performClick();

        verify(mCallback).launchSuggestion(mPref);
    }

    @Test
    public void dismissButtonClick_callsDismissSuggestion() {
        mPref.onBindViewHolder(mHolder);

        mHolder.findViewById(R.id.dismiss_button).performClick();

        verify(mCallback).dismissSuggestion(mPref);
    }
}
