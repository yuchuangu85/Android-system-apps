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

import android.content.Context;
import android.service.settings.suggestions.Suggestion;
import android.view.View;

import androidx.preference.Preference;
import androidx.preference.PreferenceViewHolder;

import com.android.car.settings.R;

import java.util.Objects;

/**
 * Custom preference for Suggestions.
 */
public class SuggestionPreference extends Preference {

    public static final String SUGGESTION_PREFERENCE_KEY = "suggestion_pref_key";

    /**
     * Callback for actions performed on a suggestion preference.
     */
    public interface Callback {
        /** Called when the suggestion should be launched. */
        void launchSuggestion(SuggestionPreference preference);

        /** Called when the suggestion should be dismissed. */
        void dismissSuggestion(SuggestionPreference preference);
    }

    private final Callback mCallback;
    private Suggestion mSuggestion;

    public SuggestionPreference(Context context, Suggestion suggestion, Callback callback) {
        super(context);
        setLayoutResource(R.layout.suggestion_preference);
        mCallback = callback;
        setKey(SUGGESTION_PREFERENCE_KEY + suggestion.getId());
        updateSuggestion(suggestion);
    }

    /**
     * Returns the {@link Suggestion} represented by this preference.
     */
    public Suggestion getSuggestion() {
        return mSuggestion;
    }

    /**
     * Updates the icon, title, and summary of the preference with those of the given
     * {@link Suggestion}.
     *
     * @param suggestion the updated suggestion to represent
     * @throws IllegalArgumentException if the given suggestion has a different id than the
     *         suggestion passed to the constructor
     */
    public void updateSuggestion(Suggestion suggestion) {
        if (mSuggestion != null && !Objects.equals(mSuggestion.getId(), suggestion.getId())) {
            throw new IllegalArgumentException(
                    "Suggestion preference update must have a matching id");
        }
        mSuggestion = suggestion;
        setIcon((suggestion.getIcon() != null) ? suggestion.getIcon().loadDrawable(getContext())
                : null);
        setTitle(suggestion.getTitle());
        setSummary(suggestion.getSummary());
    }

    @Override
    public void onBindViewHolder(final PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);
        holder.setDividerAllowedAbove(false);
        holder.setDividerAllowedBelow(false);

        holder.itemView.setOnClickListener(v -> mCallback.launchSuggestion(this));

        View dismissButton = holder.findViewById(R.id.dismiss_button);
        dismissButton.setOnClickListener(v -> mCallback.dismissSuggestion(this));
    }
}
