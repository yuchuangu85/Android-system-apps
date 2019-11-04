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

import androidx.lifecycle.Lifecycle;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.ListPreference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.PreferenceControllerTestHelper;
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
public class WifiTetherSecurityPreferenceControllerTest {

    private Context mContext;
    private ListPreference mPreference;
    private PreferenceControllerTestHelper<WifiTetherSecurityPreferenceController>
            mControllerHelper;
    private CarWifiManager mCarWifiManager;
    private LocalBroadcastManager mLocalBroadcastManager;
    private WifiTetherSecurityPreferenceController mController;

    @Before
    public void setup() {
        mContext = RuntimeEnvironment.application;
        mCarWifiManager = new CarWifiManager(mContext);
        mLocalBroadcastManager = LocalBroadcastManager.getInstance(mContext);
        mPreference = new ListPreference(mContext);
        mControllerHelper =
                new PreferenceControllerTestHelper<WifiTetherSecurityPreferenceController>(mContext,
                        WifiTetherSecurityPreferenceController.class, mPreference);
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
    public void onStart_securityTypeSetToNone_setsValueToNone() {
        WifiConfiguration config = new WifiConfiguration();
        config.preSharedKey = null;
        config.allowedKeyManagement.clear();
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        mCarWifiManager.setWifiApConfig(config);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);

        assertThat(Integer.parseInt(mPreference.getValue()))
                .isEqualTo(WifiConfiguration.KeyMgmt.NONE);
    }

    @Test
    public void onStart_securityTypeSetToWPA2PSK_setsValueToWPA2PSK() {
        String testPassword = "TEST_PASSWORD";
        WifiConfiguration config = new WifiConfiguration();
        config.preSharedKey = testPassword;
        config.allowedKeyManagement.clear();
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA2_PSK);
        mCarWifiManager.setWifiApConfig(config);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);

        assertThat(Integer.parseInt(mPreference.getValue()))
                .isEqualTo(WifiConfiguration.KeyMgmt.WPA2_PSK);
    }

    @Test
    public void onPreferenceChangedToNone_updatesSecurityTypeToNone() {
        String testPassword = "TEST_PASSWORD";
        WifiConfiguration config = new WifiConfiguration();
        config.preSharedKey = testPassword;
        config.allowedKeyManagement.clear();
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA2_PSK);
        mCarWifiManager.setWifiApConfig(config);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);

        mController.handlePreferenceChanged(mPreference,
                Integer.toString(WifiConfiguration.KeyMgmt.NONE));

        assertThat(mCarWifiManager.getWifiApConfig().getAuthType())
                .isEqualTo(WifiConfiguration.KeyMgmt.NONE);

    }

    @Test
    public void onPreferenceChangedToWPA2PSK_updatesSecurityTypeToWPA2PSK() {
        WifiConfiguration config = new WifiConfiguration();
        config.preSharedKey = null;
        config.allowedKeyManagement.clear();
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        mCarWifiManager.setWifiApConfig(config);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);

        mController.handlePreferenceChanged(mPreference,
                Integer.toString(WifiConfiguration.KeyMgmt.WPA2_PSK));

        assertThat(mCarWifiManager.getWifiApConfig().getAuthType())
                .isEqualTo(WifiConfiguration.KeyMgmt.WPA2_PSK);
    }

    @Test
    public void onPreferenceSwitchFromNoneToWPA2PSK_retrievesSavedPassword() {
        String savedPassword = "SAVED_PASSWORD";
        SharedPreferences sp = mContext.getSharedPreferences(
                WifiTetherPasswordPreferenceController.SHARED_PREFERENCE_PATH,
                Context.MODE_PRIVATE);
        sp.edit().putString(WifiTetherPasswordPreferenceController.KEY_SAVED_PASSWORD,
                savedPassword).commit();

        WifiConfiguration config = new WifiConfiguration();
        config.preSharedKey = null;
        config.allowedKeyManagement.clear();
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        mCarWifiManager.setWifiApConfig(config);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);

        mController.handlePreferenceChanged(mPreference,
                Integer.toString(WifiConfiguration.KeyMgmt.WPA2_PSK));

        assertThat(mCarWifiManager.getWifiApConfig().preSharedKey).isEqualTo(savedPassword);
    }

    @Test
    public void onPreferenceChanged_broadcastsExactlyOneIntent() {
        WifiConfiguration config = new WifiConfiguration();
        config.allowedKeyManagement.clear();
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        mCarWifiManager.setWifiApConfig(config);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);

        int newSecurityType = WifiConfiguration.KeyMgmt.WPA2_PSK;
        mController.handlePreferenceChanged(mPreference, newSecurityType);

        assertThat(ShadowLocalBroadcastManager.getSentBroadcastIntents().size()).isEqualTo(1);
    }

    @Test
    public void onPreferenceChangedToWPA2PSK_broadcastsSecurityTypeWPA2PSK() {
        WifiConfiguration config = new WifiConfiguration();
        config.allowedKeyManagement.clear();
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.NONE);
        mCarWifiManager.setWifiApConfig(config);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);

        int newSecurityType = WifiConfiguration.KeyMgmt.WPA2_PSK;

        mController.handlePreferenceChanged(mPreference, newSecurityType);

        Intent expectedIntent = new Intent(
                WifiTetherSecurityPreferenceController.ACTION_SECURITY_TYPE_CHANGED);
        expectedIntent.putExtra(WifiTetherSecurityPreferenceController.KEY_SECURITY_TYPE,
                newSecurityType);

        assertThat(
                ShadowLocalBroadcastManager.getSentBroadcastIntents().get(0).toString())
                .isEqualTo(expectedIntent.toString());
    }

    @Test
    public void onPreferenceChangedToNone_broadcastsSecurityTypeNone() {
        WifiConfiguration config = new WifiConfiguration();
        config.allowedKeyManagement.clear();
        config.allowedKeyManagement.set(WifiConfiguration.KeyMgmt.WPA2_PSK);
        mCarWifiManager.setWifiApConfig(config);
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);

        int newSecurityType = WifiConfiguration.KeyMgmt.NONE;

        mController.handlePreferenceChanged(mPreference, newSecurityType);

        Intent expectedIntent = new Intent(
                WifiTetherSecurityPreferenceController.ACTION_SECURITY_TYPE_CHANGED);
        expectedIntent.putExtra(WifiTetherSecurityPreferenceController.KEY_SECURITY_TYPE,
                newSecurityType);

        assertThat(
                ShadowLocalBroadcastManager.getSentBroadcastIntents().get(0).toString())
                .isEqualTo(expectedIntent.toString());
    }
}
