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

package com.android.car.settings.wifi.preferences;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.LocationManager;
import android.os.Process;
import android.provider.Settings;

import androidx.lifecycle.Lifecycle;
import androidx.preference.SwitchPreference;
import androidx.preference.TwoStatePreference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowLocationManager;
import com.android.car.settings.testutils.ShadowSecureSettings;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowToast;

@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowSecureSettings.class, ShadowLocationManager.class})
public class WifiWakeupTogglePreferenceControllerTest {

    private Context mContext;
    private PreferenceControllerTestHelper<WifiWakeupTogglePreferenceController> mControllerHelper;
    private WifiWakeupTogglePreferenceController mController;
    private TwoStatePreference mTwoStatePreference;
    private LocationManager mLocationManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = RuntimeEnvironment.application;
        mLocationManager = (LocationManager) mContext.getSystemService(Service.LOCATION_SERVICE);
        mTwoStatePreference = new SwitchPreference(mContext);
        mControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                WifiWakeupTogglePreferenceController.class, mTwoStatePreference);
        mController = mControllerHelper.getController();

        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
    }

    @After
    public void tearDown() {
        ShadowSecureSettings.reset();
    }

    @Test
    public void handlePreferenceClicked_locationDisabled_startNewActivity() {
        setLocationEnabled(false);

        mTwoStatePreference.performClick();

        Intent actual = ShadowApplication.getInstance().getNextStartedActivity();
        assertThat(actual.getAction()).isEqualTo(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
    }

    @Test
    public void handlePreferenceClicked_wifiWakeupEnabled_disablesWifiWakeup() {
        setLocationEnabled(true);
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.WIFI_WAKEUP_ENABLED,
                1);

        mTwoStatePreference.performClick();

        assertThat(Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.WIFI_WAKEUP_ENABLED, 1))
                .isEqualTo(0);
    }

    @Test
    public void handlePreferenceClicked_wifiScanningDisabled_showsDialog() {
        setLocationEnabled(true);
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.WIFI_SCAN_ALWAYS_AVAILABLE, 0);
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.WIFI_WAKEUP_ENABLED,
                0);

        mTwoStatePreference.performClick();

        verify(mControllerHelper.getMockFragmentController()).showDialog(
                any(ConfirmEnableWifiScanningDialogFragment.class),
                eq(ConfirmEnableWifiScanningDialogFragment.TAG));
    }

    @Test
    public void handlePreferenceClicked_wifiScanningEnabled_wifiWakeupDisabled_enablesWifiWakeup() {
        setLocationEnabled(true);
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.WIFI_SCAN_ALWAYS_AVAILABLE, 1);
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.WIFI_WAKEUP_ENABLED,
                0);

        mTwoStatePreference.performClick();

        assertThat(Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.WIFI_WAKEUP_ENABLED, 0))
                .isEqualTo(1);
    }

    @Test
    public void refreshUi_wifiWakeupEnabled_wifiScanningEnabled_locationEnabled_isChecked() {
        setLocationEnabled(true);
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.WIFI_SCAN_ALWAYS_AVAILABLE, 1);
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.WIFI_WAKEUP_ENABLED,
                1);
        mTwoStatePreference.setChecked(false);

        mController.refreshUi();

        assertThat(mTwoStatePreference.isChecked()).isTrue();
    }

    @Test
    public void refreshUi_wifiWakeupDisabled_wifiScanningEnabled_locationEnabled_isNotChecked() {
        setLocationEnabled(true);
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.WIFI_SCAN_ALWAYS_AVAILABLE, 1);
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.WIFI_WAKEUP_ENABLED,
                0);
        mTwoStatePreference.setChecked(true);

        mController.refreshUi();

        assertThat(mTwoStatePreference.isChecked()).isFalse();
    }

    @Test
    public void refreshUi_wifiWakeupEnabled_wifiScanningDisabled_locationEnabled_isNotChecked() {
        setLocationEnabled(true);
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.WIFI_SCAN_ALWAYS_AVAILABLE, 0);
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.WIFI_WAKEUP_ENABLED,
                1);
        mTwoStatePreference.setChecked(true);

        mController.refreshUi();

        assertThat(mTwoStatePreference.isChecked()).isFalse();
    }

    @Test
    public void refreshUi_wifiWakeupEnabled_wifiScanningEnabled_locationDisabled_isNotChecked() {
        setLocationEnabled(false);
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.WIFI_SCAN_ALWAYS_AVAILABLE, 1);
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.WIFI_WAKEUP_ENABLED,
                1);
        mTwoStatePreference.setChecked(true);

        mController.refreshUi();

        assertThat(mTwoStatePreference.isChecked()).isFalse();
    }

    @Test
    public void onWifiScanningEnabled_setsWifiScanningOn() {
        setLocationEnabled(true);
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.WIFI_SCAN_ALWAYS_AVAILABLE, 0);
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.WIFI_WAKEUP_ENABLED,
                0);

        mController.onWifiScanningEnabled();

        assertThat(Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.WIFI_SCAN_ALWAYS_AVAILABLE, 0)).isEqualTo(1);
    }

    @Test
    public void onWifiScanningEnabled_showsToast() {
        setLocationEnabled(true);
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.WIFI_SCAN_ALWAYS_AVAILABLE, 0);
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.WIFI_WAKEUP_ENABLED,
                0);

        mController.onWifiScanningEnabled();

        assertThat(ShadowToast.showedToast(
                mContext.getString(R.string.wifi_settings_scanning_required_enabled))).isTrue();
    }

    @Test
    public void onWifiScanningEnabled_enablesWifiWakeup() {
        setLocationEnabled(true);
        Settings.Global.putInt(mContext.getContentResolver(),
                Settings.Global.WIFI_SCAN_ALWAYS_AVAILABLE, 0);
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.WIFI_WAKEUP_ENABLED,
                0);

        mController.onWifiScanningEnabled();

        assertThat(Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.WIFI_WAKEUP_ENABLED, 0)).isEqualTo(1);
    }

    private void setLocationEnabled(boolean enabled) {
        mLocationManager.setLocationEnabledForUser(enabled, Process.myUserHandle());
    }
}
