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
 * limitations under the License
 */

package com.android.settings.display;

import static com.android.settings.core.BasePreferenceController.AVAILABLE;
import static com.android.settings.core.BasePreferenceController.UNSUPPORTED_ON_DEVICE;

import static com.google.common.truth.Truth.assertThat;

import com.android.settings.testutils.shadow.SettingsShadowResources;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(shadows = {SettingsShadowResources.class})
public class AutoBrightnessDetailPreferenceControllerTest {

    private AutoBrightnessDetailPreferenceController mController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mController = new AutoBrightnessDetailPreferenceController(
                RuntimeEnvironment.application, "test_key");
    }

    @Test
    public void isSliceable_returnTrue() {
        mController.onPreferenceChange(null, true);

        assertThat(mController.isSliceable()).isTrue();
    }

    @Test
    public void isPublicSlice_returnTrue() {
        assertThat(mController.isPublicSlice()).isTrue();
    }

    @Test
    public void getAvailabilityStatus_configTrueSet_shouldReturnAvailable() {
        SettingsShadowResources.overrideResource(
                com.android.internal.R.bool.config_automatic_brightness_available, true);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_configFalseSet_shouldReturnUnsupportedOnDevice() {
        SettingsShadowResources.overrideResource(
                com.android.internal.R.bool.config_automatic_brightness_available, false);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(UNSUPPORTED_ON_DEVICE);
    }
}
