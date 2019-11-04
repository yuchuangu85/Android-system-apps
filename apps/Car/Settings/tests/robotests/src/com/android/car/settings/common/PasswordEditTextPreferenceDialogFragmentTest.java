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

import static com.google.common.truth.Truth.assertThat;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;

import androidx.preference.EditTextPreference;
import androidx.preference.PreferenceFragmentCompat;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.testutils.BaseTestActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowAlertDialog;

/** Unit test for {@link EditTextPreferenceDialogFragment}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
public class PasswordEditTextPreferenceDialogFragmentTest {

    private Context mContext;
    private ActivityController<BaseTestActivity> mTestActivityController;
    private BaseTestActivity mTestActivity;
    private EditTextPreference mPreference;
    private PasswordEditTextPreferenceDialogFragment mFragment;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.application;
        mTestActivityController = ActivityController.of(new BaseTestActivity());
        mTestActivity = mTestActivityController.get();
        mTestActivityController.setup();
        TestTargetFragment targetFragment = new TestTargetFragment();
        mTestActivity.launchFragment(targetFragment);
        mPreference = new PasswordEditTextPreference(mContext);
        mPreference.setDialogLayoutResource(R.layout.preference_dialog_password_edittext);
        mPreference.setKey("key");
        targetFragment.getPreferenceScreen().addPreference(mPreference);
        mFragment = PasswordEditTextPreferenceDialogFragment.newInstance(mPreference.getKey());
        mFragment.setTargetFragment(targetFragment, /* requestCode= */ 0);
    }

    @Test
    public void onStart_inputTypeSetToPassword_shouldRevealShowPasswordCheckBoxUnchecked() {
        mTestActivity.showDialog(mFragment, /* tag= */ null);
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        CheckBox checkBox = dialog.findViewById(R.id.checkbox);

        assertThat(checkBox.getVisibility()).isEqualTo(View.VISIBLE);
        assertThat(!checkBox.isChecked()).isTrue();
    }

    @Test
    public void onCheckBoxChecked_shouldRevealRawPassword() {
        String testPassword = "TEST_PASSWORD";
        mTestActivity.showDialog(mFragment, /* tag= */ null);
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        CheckBox checkBox = dialog.findViewById(R.id.checkbox);
        EditText editText = dialog.findViewById(android.R.id.edit);
        editText.setText(testPassword);
        checkBox.performClick();

        assertThat(editText.getInputType()).isEqualTo(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD);
        assertThat(editText.getText().toString()).isEqualTo(testPassword);
    }

    @Test
    public void onCheckBoxUnchecked_shouldObscureRawPassword() {
        String testPassword = "TEST_PASSWORD";
        mTestActivity.showDialog(mFragment, /* tag= */ null);
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        CheckBox checkBox = dialog.findViewById(R.id.checkbox);
        EditText editText = dialog.findViewById(android.R.id.edit);
        editText.setText(testPassword);
        // Performing click twice to simulate uncheck
        checkBox.performClick();
        checkBox.performClick();

        assertThat(editText.getInputType()).isEqualTo((InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_VARIATION_PASSWORD));
        assertThat(editText.getText().toString()).isEqualTo(testPassword);
    }

    /** Simple {@link PreferenceFragmentCompat} implementation to serve as the target fragment. */
    public static class TestTargetFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferenceScreen(getPreferenceManager().createPreferenceScreen(getContext()));
        }
    }
}
