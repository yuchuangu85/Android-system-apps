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
import android.view.WindowManager;
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
public class EditTextPreferenceDialogFragmentTest {

    private Context mContext;
    private ActivityController<BaseTestActivity> mTestActivityController;
    private BaseTestActivity mTestActivity;
    private EditTextPreference mPreference;
    private EditTextPreferenceDialogFragment mFragment;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.application;
        mTestActivityController = ActivityController.of(new BaseTestActivity());
        mTestActivity = mTestActivityController.get();
        mTestActivityController.setup();
        TestTargetFragment targetFragment = new TestTargetFragment();
        mTestActivity.launchFragment(targetFragment);
        mPreference = new EditTextPreference(mContext);
        mPreference.setDialogLayoutResource(R.layout.preference_dialog_edittext);
        mPreference.setKey("key");
        targetFragment.getPreferenceScreen().addPreference(mPreference);
        mFragment = EditTextPreferenceDialogFragment
                .newInstance(mPreference.getKey());

        mFragment.setTargetFragment(targetFragment, /* requestCode= */ 0);
    }

    @Test
    public void dialogPopulatedWithPreferenceText() {
        mPreference.setText("text");

        mTestActivity.showDialog(mFragment, /* tag= */ null);
        EditText editTextView = ShadowAlertDialog.getLatestAlertDialog().findViewById(
                android.R.id.edit);

        assertThat(editTextView.getText().toString()).isEqualTo(mPreference.getText());
    }

    @Test
    public void softInputMethodSetOnWindow() {
        mTestActivity.showDialog(mFragment, /* tag= */ null);

        assertThat(getShadowWindowFromDialog(
                ShadowAlertDialog.getLatestAlertDialog()).getSoftInputMode()).isEqualTo(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }

    @Test
    public void editTextHasFocus() {
        mTestActivity.showDialog(mFragment, /* tag= */ null);
        EditText editTextView = ShadowAlertDialog.getLatestAlertDialog().findViewById(
                android.R.id.edit);

        assertThat(editTextView.hasFocus()).isTrue();
    }

    @Test
    public void onDialogClosed_positiveResult_updatesPreference() {
        String text = "text";
        mTestActivity.showDialog(mFragment, /* tag= */ null);
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        EditText editTextView = dialog.findViewById(android.R.id.edit);
        editTextView.setText(text);

        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();

        assertThat(mPreference.getText()).isEqualTo(text);
    }

    @Test
    public void onDialogClosed_negativeResult_doesNothing() {
        mTestActivity.showDialog(mFragment, /* tag= */ null);
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        EditText editTextView = dialog.findViewById(android.R.id.edit);
        editTextView.setText("text");

        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();

        assertThat(mPreference.getText()).isNull();
    }

    @Test
    public void instanceStateRetained() {
        String text = "text";
        mPreference.setText(text);
        mTestActivity.showDialog(mFragment, /* tag= */ null);

        // Save instance state.
        Bundle outState = new Bundle();
        mTestActivityController.pause().saveInstanceState(outState).stop();

        // Recreate everything with saved state.
        mTestActivityController = ActivityController.of(new BaseTestActivity());
        mTestActivity = mTestActivityController.get();
        mTestActivityController.setup(outState);

        // Ensure saved text was applied.
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();
        EditText editTextView = dialog.findViewById(android.R.id.edit);
        assertThat(editTextView.getText().toString()).isEqualTo(text);
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
