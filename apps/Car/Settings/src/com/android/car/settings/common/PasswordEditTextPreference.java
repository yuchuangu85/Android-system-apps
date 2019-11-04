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

import com.android.car.settings.R;

/**
 * Extends {@link ValidatedEditTextPreference} for password input. When {@link SettingsFragment}
 * detects an instance of this class, it creates a new instance of {@link
 * PasswordEditTextPreferenceDialogFragment} so that the input is obscured on the dialog's TextEdit.
 */
public class PasswordEditTextPreference extends ValidatedEditTextPreference {

    public PasswordEditTextPreference(Context context) {
        super(context);
        init();
    }

    public PasswordEditTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public PasswordEditTextPreference(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init();
    }

    public PasswordEditTextPreference(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    private void init() {
        setDialogLayoutResource(R.layout.preference_dialog_password_edittext);
        setPersistent(false);
    }
}
