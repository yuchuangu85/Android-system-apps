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

package com.android.car.settings.system;

import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.UNSUPPORTED_ON_DEVICE;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.os.SystemProperties;
import android.provider.Settings;

import androidx.preference.SwitchPreference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.PreferenceControllerTestHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowEuiccManager;

/** Unit test for {@link MasterClearResetEsimPreferenceController}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
public class MasterClearResetEsimPreferenceControllerTest {

    private Context mContext;
    private MasterClearResetEsimPreferenceController mController;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.application;
        ((ShadowEuiccManager) Shadow.extract(
                mContext.getSystemService(Context.EUICC_SERVICE))).setIsEnabled(true);
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.EUICC_PROVISIONED, 1);

        mController = new PreferenceControllerTestHelper<>(mContext,
                MasterClearResetEsimPreferenceController.class,
                new SwitchPreference(mContext)).getController();
    }

    @After
    public void tearDown() {
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.EUICC_PROVISIONED, 0);
    }

    @Test
    public void getAvailabilityStatus_showEsimPropertyTrue_available() {
        SystemProperties.set(MasterClearResetEsimPreferenceController.KEY_SHOW_ESIM_RESET_CHECKBOX,
                Boolean.TRUE.toString());

        assertThat(mController.getAvailabilityStatus()).isEqualTo(AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_showEsimPropertyFalse_unsupportedOnDevice() {
        SystemProperties.set(MasterClearResetEsimPreferenceController.KEY_SHOW_ESIM_RESET_CHECKBOX,
                Boolean.FALSE.toString());

        assertThat(mController.getAvailabilityStatus()).isEqualTo(UNSUPPORTED_ON_DEVICE);
    }
}
