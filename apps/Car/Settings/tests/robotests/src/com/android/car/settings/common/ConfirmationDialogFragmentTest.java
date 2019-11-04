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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.verify;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;

import androidx.fragment.app.Fragment;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.testutils.FragmentController;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowDialog;

@RunWith(CarSettingsRobolectricTestRunner.class)
public class ConfirmationDialogFragmentTest {

    private static final String TEST_ARG_KEY = "arg_key";
    private static final String TEST_ARG_VALUE = "arg_value";
    private static final String TEST_TITLE = "Test Title";
    private static final String TEST_MESSAGE = "Test Message";

    private ConfirmationDialogFragment.Builder mDialogFragmentBuilder;
    private Fragment mFragment;
    @Mock
    private ConfirmationDialogFragment.ConfirmListener mConfirmListener;
    @Mock
    private ConfirmationDialogFragment.RejectListener mRejectListener;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mFragment = FragmentController.of(new Fragment()).setup();
        mDialogFragmentBuilder = new ConfirmationDialogFragment.Builder(
                RuntimeEnvironment.application);
        mDialogFragmentBuilder.setTitle(TEST_TITLE);
        mDialogFragmentBuilder.setMessage(TEST_MESSAGE);
        mDialogFragmentBuilder.addArgumentString(TEST_ARG_KEY, TEST_ARG_VALUE);
    }

    @After
    public void tearDown() {
        ShadowDialog.reset();
    }

    @Test
    public void buildDialogFragment_hasTitleAndMessage() {
        ConfirmationDialogFragment dialogFragment = mDialogFragmentBuilder.build();
        dialogFragment.show(mFragment.getFragmentManager(), ConfirmationDialogFragment.TAG);

        assertThat(getShadowAlertDialog().getTitle()).isEqualTo(TEST_TITLE);
        assertThat(getShadowAlertDialog().getMessage()).isEqualTo(TEST_MESSAGE);
    }

    @Test
    public void buildDialogFragment_negativeButtonNotSet_negativeButtonNotVisible() {
        mDialogFragmentBuilder.setPositiveButton(R.string.test_positive_button_label, null);
        ConfirmationDialogFragment dialogFragment = mDialogFragmentBuilder.build();
        dialogFragment.show(mFragment.getFragmentManager(), ConfirmationDialogFragment.TAG);

        AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        assertThat(dialog.getButton(DialogInterface.BUTTON_POSITIVE).getVisibility()).isEqualTo(
                View.VISIBLE);
        assertThat(dialog.getButton(DialogInterface.BUTTON_NEGATIVE).getVisibility()).isEqualTo(
                View.GONE);
    }

    @Test
    public void buildDialogFragment_positiveButtonNotSet_positiveButtonNotVisible() {
        mDialogFragmentBuilder.setNegativeButton(R.string.test_negative_button_label, null);
        ConfirmationDialogFragment dialogFragment = mDialogFragmentBuilder.build();
        dialogFragment.show(mFragment.getFragmentManager(), ConfirmationDialogFragment.TAG);

        AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        assertThat(dialog.getButton(DialogInterface.BUTTON_POSITIVE).getVisibility()).isEqualTo(
                View.GONE);
        assertThat(dialog.getButton(DialogInterface.BUTTON_NEGATIVE).getVisibility()).isEqualTo(
                View.VISIBLE);
    }

    @Test
    public void clickPositiveButton_callsCallbackWithArgs() {
        mDialogFragmentBuilder.setPositiveButton(R.string.test_positive_button_label,
                mConfirmListener);
        ConfirmationDialogFragment dialogFragment = mDialogFragmentBuilder.build();
        dialogFragment.show(mFragment.getFragmentManager(), ConfirmationDialogFragment.TAG);

        AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();
        ArgumentCaptor<Bundle> bundle = ArgumentCaptor.forClass(Bundle.class);
        verify(mConfirmListener).onConfirm(bundle.capture());
        assertThat(bundle.getValue().getString(TEST_ARG_KEY)).isEqualTo(TEST_ARG_VALUE);
    }

    @Test
    public void clickNegativeButton_callsCallbackWithArgs() {
        mDialogFragmentBuilder.setNegativeButton(R.string.test_negative_button_label,
                mRejectListener);
        ConfirmationDialogFragment dialogFragment = mDialogFragmentBuilder.build();
        dialogFragment.show(mFragment.getFragmentManager(), ConfirmationDialogFragment.TAG);

        AlertDialog dialog = (AlertDialog) ShadowDialog.getLatestDialog();
        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();
        ArgumentCaptor<Bundle> bundle = ArgumentCaptor.forClass(Bundle.class);
        verify(mRejectListener).onReject(bundle.capture());
        assertThat(bundle.getValue().getString(TEST_ARG_KEY)).isEqualTo(TEST_ARG_VALUE);
    }

    private ShadowAlertDialog getShadowAlertDialog() {
        return ShadowApplication.getInstance().getLatestAlertDialog();
    }
}
