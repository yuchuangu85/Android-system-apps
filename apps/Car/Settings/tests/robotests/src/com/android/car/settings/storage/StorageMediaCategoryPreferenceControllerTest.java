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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import android.content.Context;

import androidx.lifecycle.Lifecycle;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.common.ProgressBarPreference;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;

/** Unit test for {@link StorageMediaCategoryPreferenceController}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
public class StorageMediaCategoryPreferenceControllerTest {

    @Test
    public void handlePreferenceClicked_shouldLaunchAccountSyncDetailsFragment() {
        Context context = RuntimeEnvironment.application;
        ProgressBarPreference progressBarPreference = new ProgressBarPreference(context);
        PreferenceControllerTestHelper<StorageMediaCategoryPreferenceController> helper =
                new PreferenceControllerTestHelper<>(context,
                        StorageMediaCategoryPreferenceController.class, progressBarPreference);
        FragmentController mMockFragmentController = helper.getMockFragmentController();
        helper.markState(Lifecycle.State.CREATED);

        progressBarPreference.performClick();

        verify(mMockFragmentController).launchFragment(
                any(StorageMediaCategoryDetailFragment.class));
    }
}
