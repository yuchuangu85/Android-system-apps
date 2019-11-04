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

package com.android.car.settings.datausage;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.verify;

import android.app.AlertDialog;
import android.content.DialogInterface;

import androidx.fragment.app.Fragment;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.testutils.FragmentController;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.shadows.ShadowDialog;

@RunWith(CarSettingsRobolectricTestRunner.class)
public class UsageCycleResetDayOfMonthPickerDialogTest {

    private Fragment mFragment;
    @Mock
    private UsageCycleResetDayOfMonthPickerDialog.ResetDayOfMonthPickedListener
            mResetDayOfMonthPickedListener;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mFragment = FragmentController.of(new Fragment()).setup();
    }

    @Test
    public void dialogInit_validValue_showsCurrentValue() {
        int setDate = 15;
        UsageCycleResetDayOfMonthPickerDialog dialog =
                UsageCycleResetDayOfMonthPickerDialog.newInstance(setDate);
        dialog.show(mFragment.getFragmentManager(), /* tag= */ null);
        assertThat(dialog.getSelectedDayOfMonth()).isEqualTo(setDate);
    }

    @Test
    public void dialogInit_lowInvalidValue_showsLowestPossibleValue() {
        UsageCycleResetDayOfMonthPickerDialog dialog =
                UsageCycleResetDayOfMonthPickerDialog.newInstance(0);
        dialog.show(mFragment.getFragmentManager(), /* tag= */ null);
        assertThat(dialog.getSelectedDayOfMonth()).isEqualTo(1);
    }

    @Test
    public void dialogInit_highInvalidValue_showsHighestPossibleValue() {
        UsageCycleResetDayOfMonthPickerDialog dialog =
                UsageCycleResetDayOfMonthPickerDialog.newInstance(32);
        dialog.show(mFragment.getFragmentManager(), /* tag= */ null);
        assertThat(dialog.getSelectedDayOfMonth()).isEqualTo(31);
    }

    @Test
    public void dialogListenerCalled() {
        int setDate = 15;
        UsageCycleResetDayOfMonthPickerDialog dialog =
                UsageCycleResetDayOfMonthPickerDialog.newInstance(setDate);
        dialog.setResetDayOfMonthPickedListener(mResetDayOfMonthPickedListener);
        dialog.show(mFragment.getFragmentManager(), /* tag= */ null);

        AlertDialog alertDialog = (AlertDialog) ShadowDialog.getLatestDialog();
        alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();

        verify(mResetDayOfMonthPickedListener).onDayOfMonthPicked(setDate);
    }
}
