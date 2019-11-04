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
import android.content.DialogInterface;
import android.os.Bundle;
import android.widget.Button;
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
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowWindow;

/** Unit test for {@link EditTextPreferenceDialogFragment}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
public class ValidatedEditTextPreferenceDialogFragmentTest {

    private Context mContext;
    private ActivityController<BaseTestActivity> mTestActivityController;
    private BaseTestActivity mTestActivity;
    private EditTextPreference mPreference;
    private ValidatedEditTextPreferenceDialogFragment mFragment;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.application;
        mTestActivityController = ActivityController.of(new BaseTestActivity());
        mTestActivity = mTestActivityController.get();
        mTestActivityController.setup();
        TestTargetFragment targetFragment = new TestTargetFragment();
        mTestActivity.launchFragment(targetFragment);
        mPreference = new ValidatedEditTextPreference(mContext);
        mPreference.setDialogLayoutResource(R.layout.preference_dialog_edittext);
        mPreference.setKey("key");
        targetFragment.getPreferenceScreen().addPreference(mPreference);
        mFragment = ValidatedEditTextPreferenceDialogFragment
                .newInstance(mPreference.getKey());

        mFragment.setTargetFragment(targetFragment, /* requestCode= */ 0);
    }

    @Test
    public void noValidatorSet_shouldEnablePositiveButton_and_allowEnterToSubmit() {
        mTestActivity.showDialog(mFragment, /* tag= */ null);

        Button positiveButton = ShadowAlertDialog.getLatestAlertDialog().getButton(
                DialogInterface.BUTTON_POSITIVE);
        EditText editText = ShadowAlertDialog.getLatestAlertDialog().findViewById(
                android.R.id.edit);

        assertThat(positiveButton.isEnabled()).isTrue();
        assertThat(mFragment.getAllowEnterToSubmit()).isTrue();

        editText.setText("any text");
        assertThat(positiveButton.isEnabled()).isTrue();
        assertThat(mFragment.getAllowEnterToSubmit()).isTrue();
    }

    @Test
    public void onInvalidInput_shouldDisablePositiveButton_and_disallowEnterToSubmit() {
        ((ValidatedEditTextPreference) mPreference).setValidator(
                new ValidatedEditTextPreference.Validator() {
                    @Override
                    public boolean isTextValid(String value) {
                        return value.length() > 100;
                    }
                });
        mTestActivity.showDialog(mFragment, /* tag= */ null);

        Button positiveButton = ShadowAlertDialog.getLatestAlertDialog().getButton(
                DialogInterface.BUTTON_POSITIVE);
        EditText editText = ShadowAlertDialog.getLatestAlertDialog().findViewById(
                android.R.id.edit);
        editText.setText("shorter than 100");

        assertThat(positiveButton.isEnabled()).isFalse();
        assertThat(mFragment.getAllowEnterToSubmit()).isFalse();
    }

    @Test
    public void onValidInput_shouldEnablePositiveButton_and_allowEnterToSubmit() {
        ((ValidatedEditTextPreference) mPreference).setValidator(
                new ValidatedEditTextPreference.Validator() {
                    @Override
                    public boolean isTextValid(String value) {
                        return value.length() > 1;
                    }
                });
        mTestActivity.showDialog(mFragment, /* tag= */ null);

        Button positiveButton = ShadowAlertDialog.getLatestAlertDialog().getButton(
                DialogInterface.BUTTON_POSITIVE);
        EditText editText = ShadowAlertDialog.getLatestAlertDialog().findViewById(
                android.R.id.edit);
        editText.setText("longer than 1");

        assertThat(positiveButton.isEnabled()).isTrue();
        assertThat(mFragment.getAllowEnterToSubmit()).isTrue();
    }

    private ShadowWindow getShadowWindowFromDialog(AlertDialog dialog) {
        return (ShadowWindow) Shadow.extract(dialog.getWindow());
    }

    /** Simple {@link PreferenceFragmentCompat} implementation to serve as the target fragment. */
    public static class TestTargetFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferenceScreen(getPreferenceManager().createPreferenceScreen(getContext()));
        }
    }
}
