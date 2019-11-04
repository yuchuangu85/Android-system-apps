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
import androidx.preference.ListPreference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowCarWifiManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowCarWifiManager.class})
public class WifiTetherApBandPreferenceControllerTest {

    private Context mContext;
    private ListPreference mPreference;
    private PreferenceControllerTestHelper<WifiTetherApBandPreferenceController>
            mControllerHelper;
    private CarWifiManager mCarWifiManager;
    private WifiTetherApBandPreferenceController mController;

    @Before
    public void setup() {
        mContext = RuntimeEnvironment.application;
        mCarWifiManager = new CarWifiManager(mContext);
        mPreference = new ListPreference(mContext);
        mControllerHelper =
                new PreferenceControllerTestHelper<WifiTetherApBandPreferenceController>(mContext,
                        WifiTetherApBandPreferenceController.class, mPreference);
        mController = mControllerHelper.getController();
    }

    @After
    public void tearDown() {
        ShadowCarWifiManager.reset();
    }

    @Test
    public void onStart_5GhzBandNotSupported_preferenceIsNotEnabled() {
        ShadowCarWifiManager.setIsDualBandSupported(false);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);

        assertThat(!mPreference.isEnabled()).isTrue();
    }

    @Test
    public void onStart_5GhzBandNotSupported_summarySetToChoose2Ghz() {
        ShadowCarWifiManager.setIsDualBandSupported(false);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);

        assertThat(mPreference.getSummary())
                .isEqualTo(mContext.getString(R.string.wifi_ap_choose_2G));
    }

    @Test
    public void onStart_5GhzBandIsSupported_preferenceIsEnabled() {
        ShadowCarWifiManager.setIsDualBandSupported(true);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);

        assertThat(mPreference.isEnabled()).isTrue();
    }

    @Test
    public void onStart_wifiConfigApBandSetTo2Ghz_valueIsSetTo2Ghz() {
        ShadowCarWifiManager.setIsDualBandSupported(true);
        WifiConfiguration config = new WifiConfiguration();
        config.apBand = WifiConfiguration.AP_BAND_2GHZ;
        mCarWifiManager.setWifiApConfig(config);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);

        assertThat(mPreference.getValue())
                .isEqualTo(Integer.toString(WifiConfiguration.AP_BAND_2GHZ));
    }

    @Test
    public void onStart_wifiConfigApBandSetTo5Ghz_valueIsSetTo5Ghz() {
        ShadowCarWifiManager.setIsDualBandSupported(true);
        WifiConfiguration config = new WifiConfiguration();
        config.apBand = WifiConfiguration.AP_BAND_5GHZ;
        mCarWifiManager.setWifiApConfig(config);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);

        assertThat(mPreference.getValue())
                .isEqualTo(Integer.toString(WifiConfiguration.AP_BAND_5GHZ));
    }

    @Test
    public void onPreferenceChangedTo5Ghz_updatesApBandConfigTo5Ghz() {
        ShadowCarWifiManager.setIsDualBandSupported(true);
        ShadowCarWifiManager.setIsDualModeSupported(false);
        WifiConfiguration config = new WifiConfiguration();
        config.apBand = WifiConfiguration.AP_BAND_2GHZ;
        mCarWifiManager.setWifiApConfig(config);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);
        mController.handlePreferenceChanged(mPreference,
                Integer.toString(WifiConfiguration.AP_BAND_5GHZ));

        assertThat(mCarWifiManager.getWifiApConfig().apBand)
                .isEqualTo(WifiConfiguration.AP_BAND_5GHZ);
    }

    @Test
    public void onPreferenceChangedTo2Ghz_updatesApBandConfigTo2Ghz() {
        ShadowCarWifiManager.setIsDualBandSupported(true);
        ShadowCarWifiManager.setIsDualModeSupported(false);
        WifiConfiguration config = new WifiConfiguration();
        config.apBand = WifiConfiguration.AP_BAND_5GHZ;
        mCarWifiManager.setWifiApConfig(config);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);
        mController.handlePreferenceChanged(mPreference,
                Integer.toString(WifiConfiguration.AP_BAND_2GHZ));

        assertThat(mCarWifiManager.getWifiApConfig().apBand)
                .isEqualTo(WifiConfiguration.AP_BAND_2GHZ);
    }

    @Test
    public void onStart_dualModeIsSupported_summarySetToPrefer5Ghz() {
        ShadowCarWifiManager.setIsDualBandSupported(true);
        ShadowCarWifiManager.setIsDualModeSupported(true);
        WifiConfiguration config = new WifiConfiguration();
        config.apBand = WifiConfiguration.AP_BAND_5GHZ;
        mCarWifiManager.setWifiApConfig(config);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);
        assertThat(mPreference.getSummary()).isEqualTo(
                mContext.getString(R.string.wifi_ap_prefer_5G));
    }

    @Test
    public void onPreferenceChangedTo5Ghz_dualModeIsSupported_defaultToApBandAny() {
        ShadowCarWifiManager.setIsDualBandSupported(true);
        ShadowCarWifiManager.setIsDualModeSupported(true);
        WifiConfiguration config = new WifiConfiguration();
        config.apBand = WifiConfiguration.AP_BAND_2GHZ;
        mCarWifiManager.setWifiApConfig(config);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);
        mController.handlePreferenceChanged(mPreference,
                Integer.toString(WifiConfiguration.AP_BAND_5GHZ));

        assertThat(mCarWifiManager.getWifiApConfig().apBand)
                .isEqualTo(WifiConfiguration.AP_BAND_ANY);
    }

}
