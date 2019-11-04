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

package com.android.car.dialer.ui.settings.common;

import static com.google.common.truth.Truth.assertThat;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;

import androidx.preference.ListPreference;

import com.android.car.dialer.CarDialerRobolectricTestRunner;
import com.android.car.dialer.FragmentTestActivity;
import com.android.car.dialer.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowApplication;

/** Unit test for {@link SettingsListPreferenceDialogFragment}. */
@RunWith(CarDialerRobolectricTestRunner.class)
public class SettingsListPreferenceDialogFragmentTest {

    private ActivityController<FragmentTestActivity> mTestActivityController;
    private FragmentTestActivity mTestActivity;
    private ListPreference mPreference;
    private SettingsListPreferenceDialogFragment mFragment;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.application;

        mTestActivityController = ActivityController.of(new FragmentTestActivity());
        mTestActivity = mTestActivityController.get();
        mTestActivityController.setup();

        SettingsPreferenceDialogFragmentTest.TestTargetFragment targetFragment =
                new SettingsPreferenceDialogFragmentTest.TestTargetFragment();
        mTestActivity.setFragment(targetFragment);
        mPreference = new ListPreference(context);
        mPreference.setDialogLayoutResource(R.layout.preference_dialog_edittext);
        mPreference.setKey("key");
        mPreference.setEntries(R.array.entries);
        mPreference.setEntryValues(R.array.entry_values);
        targetFragment.getPreferenceScreen().addPreference(mPreference);

        mFragment = SettingsListPreferenceDialogFragment.newInstance(mPreference.getKey());
        mFragment.setTargetFragment(targetFragment, /* requestCode= */ 0);
    }

    @Test
    public void dialogPopulatedWithPreferenceEntries() {
        mTestActivity.showDialog(mFragment, /* tag= */ null);

        assertThat(getShadowAlertDialog().getItems()).isEqualTo(mPreference.getEntries());
    }

    @Test
    public void itemSelected_dismissesDialog() {
        mTestActivity.showDialog(mFragment, /* tag= */ null);

        getShadowAlertDialog().clickOnItem(1);

        assertThat(getShadowAlertDialog().hasBeenDismissed()).isTrue();
    }

    @Test
    public void itemSelected_setsPreferenceValue() {
        mPreference.setValueIndex(0);
        mTestActivity.showDialog(mFragment, /* tag= */ null);

        getShadowAlertDialog().clickOnItem(1);

        assertThat(mPreference.getValue()).isEqualTo(mPreference.getEntryValues()[1]);
    }

    @Test
    public void onDialogClosed_negativeResult_doesNothing() {
        mPreference.setValueIndex(0);
        mTestActivity.showDialog(mFragment, /* tag= */ null);
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();

        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();

        assertThat(mPreference.getValue()).isEqualTo(mPreference.getEntryValues()[0]);
    }

    @Test
    public void instanceStateRetained() {
        mPreference.setValueIndex(0);
        mTestActivity.showDialog(mFragment, /* tag= */ null);

        // Save instance state.
        Bundle outState = new Bundle();
        mTestActivityController.pause().saveInstanceState(outState).stop();

        // Recreate everything with saved state.
        mTestActivityController = ActivityController.of(new FragmentTestActivity());
        mTestActivity = mTestActivityController.get();
        mTestActivityController.setup(outState);

        // Ensure saved entries were applied.
        assertThat(getShadowAlertDialog().getItems()).isEqualTo(mPreference.getEntries());
    }

    private ShadowAlertDialog getShadowAlertDialog() {
        return ShadowApplication.getInstance().getLatestAlertDialog();
    }
}
