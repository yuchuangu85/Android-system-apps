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

package com.android.car.settings.common;

import static com.google.common.truth.Truth.assertThat;

import static org.robolectric.RuntimeEnvironment.application;

import androidx.fragment.app.Fragment;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.testutils.BaseTestActivity;
import com.android.car.settings.testutils.DialogTestUtils;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;

/**
 * Tests for ErrorDialog.
 */
@RunWith(CarSettingsRobolectricTestRunner.class)
public class ErrorDialogTest {
    private static final String ERROR_DIALOG_TAG = "ErrorDialogTag";
    private BaseTestActivity mTestActivity;
    private Fragment mTestFragment;

    @Before
    public void setUpTestActivity() {
        MockitoAnnotations.initMocks(this);

        mTestActivity = Robolectric.setupActivity(BaseTestActivity.class);

        mTestFragment = new Fragment();
        mTestActivity.launchFragment(mTestFragment);
    }

    @Test
    public void testOkDismissesDialog() {
        ErrorDialog dialog = ErrorDialog.show(mTestFragment, R.string.delete_user_error_title);

        assertThat(isDialogShown()).isTrue(); // Dialog is shown.

        // Invoke cancel.
        DialogTestUtils.clickPositiveButton(dialog);

        assertThat(isDialogShown()).isFalse(); // Dialog is dismissed.
    }

    @Test
    public void testErrorDialogSetsTitle() {
        int testTitleId = R.string.add_user_error_title;
        ErrorDialog dialog = ErrorDialog.show(mTestFragment, testTitleId);

        assertThat(DialogTestUtils.getTitle(dialog)).isEqualTo(application.getString(testTitleId));
    }

    private boolean isDialogShown() {
        return mTestActivity.getSupportFragmentManager()
                .findFragmentByTag(ERROR_DIALOG_TAG) != null;
    }
}
