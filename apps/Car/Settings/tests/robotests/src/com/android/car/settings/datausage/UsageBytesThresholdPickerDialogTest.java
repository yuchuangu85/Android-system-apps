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

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.app.AlertDialog;
import android.content.DialogInterface;

import androidx.fragment.app.Fragment;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.testutils.FragmentController;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.shadows.ShadowDialog;

@RunWith(CarSettingsRobolectricTestRunner.class)
public class UsageBytesThresholdPickerDialogTest {

    private Fragment mFragment;
    @Mock
    private UsageBytesThresholdPickerDialog.BytesThresholdPickedListener
            mBytesThresholdPickedListener;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mFragment = FragmentController.of(new Fragment()).setup();
    }

    @Test
    public void dialogInit_validValue_showsCurrentValue() {
        long twoGB = 2 * UsageBytesThresholdPickerDialog.GIB_IN_BYTES;
        UsageBytesThresholdPickerDialog dialog = UsageBytesThresholdPickerDialog.newInstance(
                R.string.data_usage_limit_editor_title, twoGB);
        dialog.show(mFragment.getFragmentManager(), /* tag= */ null);
        assertThat(dialog.getCurrentThreshold()).isEqualTo(twoGB);
    }

    @Test
    public void dialogInit_lowInvalidValue_showsLowestPossibleValue() {
        UsageBytesThresholdPickerDialog dialog = UsageBytesThresholdPickerDialog.newInstance(
                R.string.data_usage_limit_editor_title, -1);
        dialog.show(mFragment.getFragmentManager(), /* tag= */ null);
        assertThat(dialog.getCurrentThreshold()).isEqualTo(0);
    }

    @Test
    public void positiveButtonClick_noChangeInValue_dialogListenerNotCalled() {
        long twoGB = 2 * UsageBytesThresholdPickerDialog.GIB_IN_BYTES;
        UsageBytesThresholdPickerDialog dialog = UsageBytesThresholdPickerDialog.newInstance(
                R.string.data_usage_limit_editor_title, twoGB);
        dialog.setBytesThresholdPickedListener(mBytesThresholdPickedListener);
        dialog.show(mFragment.getFragmentManager(), /* tag= */ null);

        AlertDialog alertDialog = (AlertDialog) ShadowDialog.getLatestDialog();
        alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();

        verify(mBytesThresholdPickedListener, never()).onThresholdPicked(anyLong());
    }

    @Test
    public void positiveButtonClick_changeInValue_dialogListenerCalled() {
        long twoGB = 2 * UsageBytesThresholdPickerDialog.GIB_IN_BYTES;
        UsageBytesThresholdPickerDialog dialog = UsageBytesThresholdPickerDialog.newInstance(
                R.string.data_usage_limit_editor_title, twoGB);
        dialog.setBytesThresholdPickedListener(mBytesThresholdPickedListener);
        dialog.show(mFragment.getFragmentManager(), /* tag= */ null);

        long threeGB = 3 * UsageBytesThresholdPickerDialog.GIB_IN_BYTES;
        dialog.setThresholdEditor(threeGB);

        AlertDialog alertDialog = (AlertDialog) ShadowDialog.getLatestDialog();
        alertDialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();

        verify(mBytesThresholdPickedListener).onThresholdPicked(threeGB);
    }

    @Test
    public void getCurrentThreshold_aboveLimit_returnLimit() {
        long limitGBTimesTwo = 2 * UsageBytesThresholdPickerDialog.MAX_DATA_LIMIT_BYTES;
        UsageBytesThresholdPickerDialog dialog = UsageBytesThresholdPickerDialog.newInstance(
                R.string.data_usage_limit_editor_title, limitGBTimesTwo);
        dialog.show(mFragment.getFragmentManager(), /* tag= */ null);

        assertThat(dialog.getCurrentThreshold()).isEqualTo(
                UsageBytesThresholdPickerDialog.MAX_DATA_LIMIT_BYTES);
    }
}
