/*
 * Copyright (C) 2018 The Android Open Source Project
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
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.util.Pair;

import androidx.lifecycle.Lifecycle;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowLocalBroadcastManager;
import com.android.car.settings.testutils.ShadowWifiManager;
import com.android.settingslib.wifi.AccessPoint;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;

import java.util.List;

@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowLocalBroadcastManager.class, ShadowWifiManager.class})
public class NetworkPasswordPreferenceControllerTest {

    private Context mContext;
    private LocalBroadcastManager mLocalBroadcastManager;
    private NetworkNameRestrictedPasswordEditTextPreference mPasswordEditTextPreference;
    private PreferenceControllerTestHelper<NetworkPasswordPreferenceController>
            mPreferenceControllerHelper;
    private NetworkPasswordPreferenceController mController;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.application;
        mLocalBroadcastManager = LocalBroadcastManager.getInstance(mContext);
        mPasswordEditTextPreference = new NetworkNameRestrictedPasswordEditTextPreference(mContext);
        mPreferenceControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                NetworkPasswordPreferenceController.class, mPasswordEditTextPreference);
        mController = mPreferenceControllerHelper.getController();
    }

    @After
    public void tearDown() {
        ShadowLocalBroadcastManager.reset();
        ShadowWifiManager.reset();
    }

    @Test
    public void onStart_registersNameChangeListener() {
        mPreferenceControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);

        assertThat(isReceiverRegisteredForAction(
                NetworkNamePreferenceController.ACTION_NAME_CHANGE)).isTrue();
    }

    @Test
    public void onStart_registersSecurityChangeListener() {
        mPreferenceControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);

        assertThat(isReceiverRegisteredForAction(
                NetworkSecurityPreferenceController.ACTION_SECURITY_CHANGE)).isTrue();
    }

    @Test
    public void onStop_unregistersNameChangeListener() {
        mPreferenceControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);
        mPreferenceControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_STOP);

        assertThat(isReceiverRegisteredForAction(
                NetworkNamePreferenceController.ACTION_NAME_CHANGE)).isFalse();
    }

    @Test
    public void onStop_unregistersSecurityChangeListener() {
        mPreferenceControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);
        mPreferenceControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_STOP);

        assertThat(isReceiverRegisteredForAction(
                NetworkSecurityPreferenceController.ACTION_SECURITY_CHANGE)).isFalse();
    }

    @Test
    public void receiveNameChangeIntent_emptyName_dialogNameRemoved() {
        mPreferenceControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);
        Intent intent = new Intent(NetworkNamePreferenceController.ACTION_NAME_CHANGE);
        intent.putExtra(NetworkNamePreferenceController.KEY_NETWORK_NAME, "");
        mLocalBroadcastManager.sendBroadcastSync(intent);

        assertThat(mPasswordEditTextPreference.getDialogTitle()).isEqualTo(
                mContext.getString(R.string.wifi_password));
    }

    @Test
    public void receiveNameChangeIntent_name_dialogNameSet() {
        mPreferenceControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);
        String networkName = "test_network_name";
        Intent intent = new Intent(NetworkNamePreferenceController.ACTION_NAME_CHANGE);
        intent.putExtra(NetworkNamePreferenceController.KEY_NETWORK_NAME, networkName);
        mLocalBroadcastManager.sendBroadcastSync(intent);

        assertThat(mPasswordEditTextPreference.getDialogTitle()).isEqualTo(networkName);
    }

    @Test
    public void receiveSecurityChangeIntent_setUnsecureType_preferenceHidden() {
        mPreferenceControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);
        Intent intent = new Intent(NetworkSecurityPreferenceController.ACTION_SECURITY_CHANGE);
        intent.putExtra(NetworkSecurityPreferenceController.KEY_SECURITY_TYPE,
                AccessPoint.SECURITY_NONE);
        mLocalBroadcastManager.sendBroadcastSync(intent);

        assertThat(mPasswordEditTextPreference.isVisible()).isFalse();
    }

    @Test
    public void receiveSecurityChangeIntent_setSecureType_preferenceVisible() {
        mPreferenceControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);
        Intent intent = new Intent(NetworkSecurityPreferenceController.ACTION_SECURITY_CHANGE);
        intent.putExtra(NetworkSecurityPreferenceController.KEY_SECURITY_TYPE,
                AccessPoint.SECURITY_PSK);
        mLocalBroadcastManager.sendBroadcastSync(intent);

        assertThat(mPasswordEditTextPreference.isVisible()).isTrue();
    }

    @Test
    public void handlePreferenceChanged_hasSecurity_networkNameSet_wifiAdded() {
        mPreferenceControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);
        String networkName = "network_name";
        String password = "password";
        Intent intent = new Intent(NetworkSecurityPreferenceController.ACTION_SECURITY_CHANGE);
        intent.putExtra(NetworkSecurityPreferenceController.KEY_SECURITY_TYPE,
                AccessPoint.SECURITY_PSK);
        mLocalBroadcastManager.sendBroadcastSync(intent);

        intent = new Intent(NetworkNamePreferenceController.ACTION_NAME_CHANGE);
        intent.putExtra(NetworkNamePreferenceController.KEY_NETWORK_NAME, networkName);
        mLocalBroadcastManager.sendBroadcastSync(intent);
        mPasswordEditTextPreference.callChangeListener(password);

        WifiConfiguration lastAdded = getShadowWifiManager().getLastAddedNetworkConfiguration();
        assertThat(lastAdded.SSID).contains(networkName);
        assertThat(lastAdded.getAuthType()).isEqualTo(WifiConfiguration.KeyMgmt.WPA_PSK);
        assertThat(lastAdded.preSharedKey).contains(password);
    }

    @Test
    public void handlePreferenceChanged_hasSecurity_networkNameSet_wifiEnabled() {
        mPreferenceControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_START);
        String networkName = "network_name";
        String password = "password";
        Intent intent = new Intent(NetworkSecurityPreferenceController.ACTION_SECURITY_CHANGE);
        intent.putExtra(NetworkSecurityPreferenceController.KEY_SECURITY_TYPE,
                AccessPoint.SECURITY_PSK);
        mLocalBroadcastManager.sendBroadcastSync(intent);

        intent = new Intent(NetworkNamePreferenceController.ACTION_NAME_CHANGE);
        intent.putExtra(NetworkNamePreferenceController.KEY_NETWORK_NAME, networkName);
        mLocalBroadcastManager.sendBroadcastSync(intent);
        mPasswordEditTextPreference.callChangeListener(password);

        Pair<Integer, Boolean> lastEnabled = getShadowWifiManager().getLastEnabledNetwork();
        // Enable should be called on the most recently added network id.
        assertThat(lastEnabled.first).isEqualTo(getShadowWifiManager().getLastAddedNetworkId());
        // WifiUtil will try to enable the network right away.
        assertThat(lastEnabled.second).isTrue();
    }

    private ShadowWifiManager getShadowWifiManager() {
        return Shadow.extract(mContext.getSystemService(WifiManager.class));
    }

    private boolean isReceiverRegisteredForAction(String action) {
        List<ShadowLocalBroadcastManager.Wrapper> receivers =
                ShadowLocalBroadcastManager.getRegisteredBroadcastReceivers();

        boolean found = false;
        for (ShadowLocalBroadcastManager.Wrapper receiver : receivers) {
            if (receiver.getIntentFilter().hasAction(action)) {
                found = true;
            }
        }

        return found;
    }
}
