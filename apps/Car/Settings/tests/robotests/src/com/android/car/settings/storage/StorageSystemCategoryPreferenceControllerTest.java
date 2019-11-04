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

package com.android.car.settings.storage;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.os.Build;

import androidx.lifecycle.Lifecycle;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.common.ProgressBarPreference;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowApplication;

/** Unit test for {@link StorageSystemCategoryPreferenceController}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
public class StorageSystemCategoryPreferenceControllerTest {

    @Test
    public void handlePreferenceClicked_openAlertDialog() {
        Context context = RuntimeEnvironment.application;
        ProgressBarPreference progressBarPreference = new ProgressBarPreference(context);
        PreferenceControllerTestHelper<StorageSystemCategoryPreferenceController>
                preferenceControllerHelper = new PreferenceControllerTestHelper<>(context,
                StorageSystemCategoryPreferenceController.class, progressBarPreference);
        preferenceControllerHelper.markState(Lifecycle.State.CREATED);
        progressBarPreference.performClick();

        ShadowAlertDialog dialog = ShadowApplication.getInstance().getLatestAlertDialog();

        assertThat(dialog.getMessage().toString()).contains(
                context.getResources().getString(R.string.storage_detail_dialog_system,
                        Build.VERSION.RELEASE));
    }
}
