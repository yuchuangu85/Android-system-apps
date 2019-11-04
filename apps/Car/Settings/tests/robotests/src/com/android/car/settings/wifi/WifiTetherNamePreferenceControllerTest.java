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

package com.android.car.settings.wifi;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.net.wifi.WifiConfiguration;

import androidx.lifecycle.Lifecycle;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.common.ValidatedEditTextPreference;
import com.android.car.settings.testutils.ShadowCarWifiManager;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;

@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowCarWifiManager.class})
public class WifiTetherNamePreferenceControllerTest {

    private Context mContext;
    private ValidatedEditTextPreference mPreference;
    private PreferenceControllerTestHelper<WifiTetherNamePreferenceController> mControllerHelper;
    private CarWifiManager mCarWifiManager;

    @After
    public void tearDown() {
        ShadowCarWifiManager.reset();
    }

    @Test
    public void onStart_wifiConfigHasSSID_setsSummary() {
        mContext = RuntimeEnvironment.application;
        mCarWifiManager = new CarWifiManager(mContext);
        String testSSID = "TEST_SSID";
        WifiConfiguration config = new WifiConfiguration();
        config.SSID = testSSID;
        getShadowCarWifiManager().setWifiApConfig(config);
        mPreference = new ValidatedEditTextPreference(mContext);
        mControllerHelper =
                new PreferenceControllerTestHelper<WifiTetherNamePreferenceController>(mContext,
                        WifiTetherNamePreferenceController.class, mPreference);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);
        assertThat(mPreference.getSummary()).isEqualTo(testSSID);
    }

    private ShadowCarWifiManager getShadowCarWifiManager() {
        return Shadow.extract(mCarWifiManager);
    }
}
