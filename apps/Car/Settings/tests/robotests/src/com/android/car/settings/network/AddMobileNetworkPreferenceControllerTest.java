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

package com.android.car.settings.network;

import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.UNSUPPORTED_ON_DEVICE;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.content.pm.ResolveInfo;

import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.PreferenceControllerTestHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowEuiccManager;
import org.robolectric.shadows.ShadowPackageManager;

@RunWith(CarSettingsRobolectricTestRunner.class)
public class AddMobileNetworkPreferenceControllerTest {

    private Context mContext;
    private AddMobileNetworkPreferenceController mController;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.application;
        PreferenceControllerTestHelper<AddMobileNetworkPreferenceController> controllerHelper =
                new PreferenceControllerTestHelper<>(mContext,
                        AddMobileNetworkPreferenceController.class, new Preference(mContext));
        mController = controllerHelper.getController();
        controllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
    }

    @After
    public void tearDown() {
        ShadowPackageManager.reset();
    }

    @Test
    public void getAvailabilityStatus_euiccDisabled_isUnsupported() {
        getShadowEuiccManager().setIsEnabled(false);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_euiccEnabled_intentResolves_isAvailable() {
        getShadowEuiccManager().setIsEnabled(true);
        getShadowPackageManager().addResolveInfoForIntent(
                AddMobileNetworkPreferenceController.ADD_NETWORK_INTENT, new ResolveInfo());

        assertThat(mController.getAvailabilityStatus()).isEqualTo(AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_euiccEnabled_intentFailsToResolve_isUnsupported() {
        getShadowEuiccManager().setIsEnabled(true);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(UNSUPPORTED_ON_DEVICE);
    }

    private ShadowPackageManager getShadowPackageManager() {
        return Shadow.extract(mContext.getPackageManager());
    }

    private ShadowEuiccManager getShadowEuiccManager() {
        return Shadow.extract(mContext.getSystemService(Context.EUICC_SERVICE));
    }
}
