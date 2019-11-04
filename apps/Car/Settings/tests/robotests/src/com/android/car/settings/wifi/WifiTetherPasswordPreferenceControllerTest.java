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
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.wifi.WifiConfiguration;
import android.text.InputType;
import android.text.TextUtils;

import androidx.lifecycle.Lifecycle;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.common.ValidatedEditTextPreference;
import com.android.car.settings.testutils.ShadowCarWifiManager;
import com.android.car.settings.testutils.ShadowLocalBroadcastManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowCarWifiManager.class, ShadowLocalBroadcastManager.class})
public class WifiTetherPasswordPreferenceControllerTest {

    private static final String TEST_PASSWORD = "TEST_PASSWORD";

    private Context mContext;
    private ValidatedEditTextPreference mPreference;
    private PreferenceControllerTestHelper<WifiTetherPasswordPreferenceController>
            mControllerHelper;
    private CarWifiManager mCarWifiManager;
    private LocalBroadcastManager mLocalBroadcastManager;
    private WifiTetherPasswordPreferenceController mController;

    @Before
    public void setup() {
        mContext = RuntimeEnvironment.application;
        mCarWifiManager = new CarWifiManager(mContext);
        mLocalBroadcastManager = LocalBroadcastManager.getInstance(mContext);
        mPreference = new ValidatedEditTextPreference(mContext);
        mControllerHelper =
                new PreferenceControllerTestHelper<WifiTetherPasswordPreferenceController>(mContext,
                        WifiTetherPasswordPreferenceController.class, mPreference);
        mController = mControllerHelper.getController();
    }

    @After
    public void tearDown() {
        ShadowCarWifiManager.reset();
        ShadowLocalBroadcastManager.reset();
        SharedPreferences sp = mContext.getSharedPreferences(
                WifiTetherPasswordPreferenceController.SHARED_PREFERENCE_PATH,
                Context.MODE_PRIVATE);
        sp.edit().remove(WifiTetherPasswordPreferenceController.KEY_SAVED_PASSWORD).commit();
    }

    @Test
    public void onStart_securityTypeIsNotNone_visibilityIsSetToTrue() {
        WifiConfiguration config = new WifiConfiguration();
        config.preSharedKey = null;
        config.allowedKeyManagement.clear();
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA2_PSK);
        mCarWifiManager.setWifiApConfig(config);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);

        assertThat(mPreference.isVisible()).isTrue();
    }

    @Test
    public void onStart_securityTypeIsNotNone_wifiConfigHasPassword_setsPasswordAsSummary() {
        WifiConfiguration config = new WifiConfiguration();
        config.preSharedKey = TEST_PASSWORD;
        config.allowedKeyManagement.clear();
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA2_PSK);
        mCarWifiManager.setWifiApConfig(config);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);

        assertThat(mPreference.getSummary()).isEqualTo(TEST_PASSWORD);
    }

    @Test
    public void onStart_securityTypeIsNotNone_wifiConfigHasPassword_obscuresSummary() {
        WifiConfiguration config = new WifiConfiguration();
        config.preSharedKey = TEST_PASSWORD;
        config.allowedKeyManagement.clear();
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA2_PSK);
        mCarWifiManager.setWifiApConfig(config);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);

        assertThat(mPreference.getSummaryInputType())
                .isEqualTo((InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD));
    }

    @Test
    public void onStart_securityTypeIsNotNone_wifiConfigHasNoPassword_passwordIsNotEmpty() {
        WifiConfiguration config = new WifiConfiguration();
        config.preSharedKey = "";
        config.allowedKeyManagement.clear();
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA2_PSK);
        mCarWifiManager.setWifiApConfig(config);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);

        assertThat(!TextUtils.isEmpty(mPreference.getSummary())).isTrue();
    }

    @Test
    public void onStart_securityTypeIsNotNone_wifiConfigHasNoPassword_obscuresSummary() {
        WifiConfiguration config = new WifiConfiguration();
        config.preSharedKey = "";
        config.allowedKeyManagement.clear();
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA2_PSK);
        mCarWifiManager.setWifiApConfig(config);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);

        assertThat(mPreference.getSummaryInputType())
                .isEqualTo((InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD));
    }

    @Test
    public void onStart_securityTypeIsNone_visibilityIsSetToFalse() {
        WifiConfiguration config = new WifiConfiguration();
        config.preSharedKey = null;
        config.allowedKeyManagement.clear();
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        mCarWifiManager.setWifiApConfig(config);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);

        assertThat(!mPreference.isVisible()).isTrue();
    }

    @Test
    public void onStart_receiverIsRegisteredOnLocalBroadcastManager() {
        WifiConfiguration config = new WifiConfiguration();
        mCarWifiManager.setWifiApConfig(config);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);

        assertThat(
                ShadowLocalBroadcastManager.getRegisteredBroadcastReceivers().size())
                .isEqualTo(1);
    }

    @Test
    public void onStop_receiverIsUnregisteredFromLocalBroadcastManager() {
        WifiConfiguration config = new WifiConfiguration();
        mCarWifiManager.setWifiApConfig(config);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_STOP);

        assertThat(
                ShadowLocalBroadcastManager.getRegisteredBroadcastReceivers().size())
                .isEqualTo(0);
    }

    @Test
    public void onSecurityChangedToNone_visibilityIsFalse() {
        WifiConfiguration config = new WifiConfiguration();
        config.allowedKeyManagement.clear();
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA2_PSK);
        mCarWifiManager.setWifiApConfig(config);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);

        Intent intent = new Intent(
                WifiTetherSecurityPreferenceController.ACTION_SECURITY_TYPE_CHANGED);
        intent.putExtra(WifiTetherSecurityPreferenceController.KEY_SECURITY_TYPE,
                WifiConfiguration.KeyMgmt.NONE);
        mLocalBroadcastManager.sendBroadcast(intent);

        assertThat(mPreference.isVisible()).isFalse();
    }

    @Test
    public void onSecurityChangedToWPA2PSK_visibilityIsTrue() {
        WifiConfiguration config = new WifiConfiguration();
        config.allowedKeyManagement.clear();
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        mCarWifiManager.setWifiApConfig(config);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);

        Intent intent = new Intent(
                WifiTetherSecurityPreferenceController.ACTION_SECURITY_TYPE_CHANGED);
        intent.putExtra(WifiTetherSecurityPreferenceController.KEY_SECURITY_TYPE,
                WifiConfiguration.KeyMgmt.WPA2_PSK);
        mLocalBroadcastManager.sendBroadcast(intent);

        assertThat(mPreference.isVisible()).isTrue();
    }

    @Test
    public void onChangePassword_updatesPassword() {
        String oldPassword = "OLD_PASSWORD";
        String newPassword = "NEW_PASSWORD";

        WifiConfiguration config = new WifiConfiguration();
        config.preSharedKey = oldPassword;
        config.allowedKeyManagement.clear();
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        mCarWifiManager.setWifiApConfig(config);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);
        mController.handlePreferenceChanged(mPreference, newPassword);
        String passwordReturned = mCarWifiManager.getWifiApConfig().preSharedKey;

        assertThat(passwordReturned).isEqualTo(newPassword);
    }

    @Test
    public void onChangePassword_savesNewPassword() {
        String oldPassword = "OLD_PASSWORD";
        String newPassword = "NEW_PASSWORD";

        WifiConfiguration config = new WifiConfiguration();
        config.preSharedKey = oldPassword;
        config.allowedKeyManagement.clear();
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        mCarWifiManager.setWifiApConfig(config);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);
        mController.handlePreferenceChanged(mPreference, newPassword);

        SharedPreferences sp = mContext.getSharedPreferences(
                WifiTetherPasswordPreferenceController.SHARED_PREFERENCE_PATH,
                Context.MODE_PRIVATE);

        String savedPassword = sp.getString(
                WifiTetherPasswordPreferenceController.KEY_SAVED_PASSWORD, "");

        assertThat(savedPassword).isEqualTo(newPassword);
    }

}
