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

package com.android.car.settings.common;

import android.content.Context;
import android.util.AttributeSet;
import android.widget.TextView;

import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceViewHolder;

/**
 * Extends {@link EditTextPreference} to add optional {@link Validator} logic. Validator is passed
 * on to {@link ValidatedEditTextPreferenceDialogFragment} to be attached to its View.
 */
public class ValidatedEditTextPreference extends EditTextPreference {

    /** Defines the validation logic used in this preference. */
    public interface Validator {
        /** Returns true only if the value provided meets validation criteria. */
        boolean isTextValid(String value);
    }

    private Validator mValidator;
    private int mInputType;

    public ValidatedEditTextPreference(Context context) {
        super(context);
    }

    public ValidatedEditTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ValidatedEditTextPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public ValidatedEditTextPreference(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    /** Sets the text input type of the summary field. */
    public void setSummaryInputType(int inputType) {
        mInputType = inputType;
        notifyChanged();
    }

    /** Gets the text input type of the summary field. */
    public int getSummaryInputType() {
        return mInputType;
    }

    /**
     * Sets the {@link Validator}. Validator has to be set before the Preference is
     * clicked for it to be attached to the Dialog's EditText.
     */
    public void setValidator(Validator validator) {
        mValidator = validator;
    }

    /**
     * Gets the {@link Validator} for {@link EditTextPreferenceDialogFragment} to attach
     * to its own View.
     */
    public Validator getValidator() {
        return mValidator;
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder holder) {
        super.onBindViewHolder(holder);

        TextView summaryView = (TextView) holder.findViewById(android.R.id.summary);
        summaryView.setInputType(mInputType);
    }
}
