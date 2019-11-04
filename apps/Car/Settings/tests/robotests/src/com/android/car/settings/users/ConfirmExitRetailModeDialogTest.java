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

package com.android.car.settings.users;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.verify;

import androidx.fragment.app.Fragment;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.testutils.BaseTestActivity;
import com.android.car.settings.testutils.DialogTestUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;

/**
 * Tests for ConfirmExitRetailModeDialog.
 */
@RunWith(CarSettingsRobolectricTestRunner.class)
public class ConfirmExitRetailModeDialogTest {
    private BaseTestActivity mTestActivity;
    private Fragment mTestFragment;
    private ConfirmExitRetailModeDialog mDialog;

    @Before
    public void setUpTestActivity() {
        MockitoAnnotations.initMocks(this);

        mTestActivity = Robolectric.setupActivity(BaseTestActivity.class);

        mTestFragment = new Fragment();
        mTestActivity.launchFragment(mTestFragment);

        mDialog = new ConfirmExitRetailModeDialog();
    }

    @Test
    public void testConfirmExitRetailModeInvokesOnExitRetailModeConfirmed() {
        ConfirmExitRetailModeDialog.ConfirmExitRetailModeListener listener = Mockito.mock(
                ConfirmExitRetailModeDialog.ConfirmExitRetailModeListener.class);
        mDialog.setConfirmExitRetailModeListener(listener);
        showDialog();

        // Invoke exit retail mode.
        DialogTestUtils.clickPositiveButton(mDialog);

        verify(listener).onExitRetailModeConfirmed();
        assertThat(isDialogShown()).isFalse(); // Dialog is dismissed.
    }

    @Test
    public void testCancelDismissesDialog() {
        showDialog();

        // Invoke cancel.
        DialogTestUtils.clickNegativeButton(mDialog);

        assertThat(isDialogShown()).isFalse(); // Dialog is dismissed.
    }

    @Test
    public void testNoConfirmClickListenerDismissesDialog() {
        showDialog();

        // Invoke confirm add user.
        DialogTestUtils.clickPositiveButton(mDialog);

        assertThat(isDialogShown()).isFalse(); // Dialog is dismissed.
    }

    private void showDialog() {
        mDialog.show(mTestFragment);
        assertThat(isDialogShown()).isTrue();
    }

    private boolean isDialogShown() {
        return mTestActivity.getSupportFragmentManager()
                .findFragmentByTag(ConfirmExitRetailModeDialog.DIALOG_TAG) != null;
    }
}
