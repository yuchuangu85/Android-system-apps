/*
 * Copyright 2019 The Android Open Source Project
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

import android.app.AlertDialog;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

/**
 * Adds optional text validation logic to {@link EditTextPreferenceDialogFragment}. Disables
 * Positive Button and the ability to press Enter to submit the dialog if the input is invalid.
 * Validator must be provided by {@link ValidatedEditTextPreference} before launching the Dialog
 * Fragment for it to be attached to its View.
 */
public class ValidatedEditTextPreferenceDialogFragment extends
        EditTextPreferenceDialogFragment implements TextView.OnEditorActionListener {

    private final EditTextWatcher mTextWatcher = new EditTextWatcher();

    private ValidatedEditTextPreference.Validator mValidator;
    private EditText mEditText;

    /**
     * Returns a new instance of {@link ValidatedEditTextPreferenceDialogFragment} for the
     * {@link ValidatedEditTextPreference} with the given {@code key}.
     */
    public static ValidatedEditTextPreferenceDialogFragment newInstance(String key) {
        ValidatedEditTextPreferenceDialogFragment fragment =
                new ValidatedEditTextPreferenceDialogFragment();
        Bundle b = new Bundle(/* capacity= */ 1);
        b.putString(ARG_KEY, key);
        fragment.setArguments(b);
        return fragment;
    }

    @Override
    protected void onBindDialogView(View view) {
        super.onBindDialogView(view);
        mEditText = view.findViewById(android.R.id.edit);
        if (getPreference() instanceof ValidatedEditTextPreference) {
            ValidatedEditTextPreference.Validator validator =
                    ((ValidatedEditTextPreference) getPreference()).getValidator();
            if (validator != null) {
                attachValidatorToView(view, validator);
            }
        }

    }

    @Override
    public void onStart() {
        super.onStart();
        allowDialogSubmissionOnlyIfValidInput((AlertDialog) getDialog());
    }

    private void attachValidatorToView(View view, ValidatedEditTextPreference.Validator validator) {
        mValidator = validator;
        EditText editText = view.findViewById(android.R.id.edit);
        if (mValidator != null && editText != null) {
            editText.removeTextChangedListener(mTextWatcher);
            editText.addTextChangedListener(mTextWatcher);
        }
    }

    private class EditTextWatcher implements TextWatcher {
        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            allowDialogSubmissionOnlyIfValidInput((AlertDialog) getDialog());
        }
    }

    private void allowDialogSubmissionOnlyIfValidInput(AlertDialog dialog) {
        if (dialog != null && mValidator != null && mEditText != null) {
            boolean valid = mValidator.isTextValid(mEditText.getText().toString());
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(valid);
            setAllowEnterToSubmit(valid);
        }
    }
}
