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
import android.widget.Button;

import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.testutils.FragmentController;
import com.android.car.settings.testutils.ShadowLocalBroadcastManager;
import com.android.car.settings.testutils.ShadowWifiManager;
import com.android.settingslib.wifi.AccessPoint;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.List;

@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowLocalBroadcastManager.class, ShadowWifiManager.class})
public class AddWifiFragmentTest {

    private Context mContext;
    private LocalBroadcastManager mLocalBroadcastManager;
    private AddWifiFragment mFragment;
    private FragmentController<AddWifiFragment> mFragmentController;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.application;
        mLocalBroadcastManager = LocalBroadcastManager.getInstance(mContext);
        mFragment = new AddWifiFragment();
        mFragmentController = FragmentController.of(mFragment);
    }

    @After
    public void tearDown() {
        ShadowLocalBroadcastManager.reset();
        ShadowWifiManager.reset();
    }

    @Test
    public void onStart_registersNameChangeListener() {
        mFragmentController.create().start();

        assertThat(isReceiverRegisteredForAction(
                NetworkNamePreferenceController.ACTION_NAME_CHANGE)).isTrue();
    }

    @Test
    public void onStart_registersSecurityChangeListener() {
        mFragmentController.create().start();

        assertThat(isReceiverRegisteredForAction(
                NetworkSecurityPreferenceController.ACTION_SECURITY_CHANGE)).isTrue();
    }

    @Test
    public void onStop_unregistersNameChangeListener() {
        mFragmentController.create().start();
        mFragmentController.stop();

        assertThat(isReceiverRegisteredForAction(
                NetworkNamePreferenceController.ACTION_NAME_CHANGE)).isFalse();
    }

    @Test
    public void onStop_unregistersSecurityChangeListener() {
        mFragmentController.create().start();
        mFragmentController.stop();

        assertThat(isReceiverRegisteredForAction(
                NetworkSecurityPreferenceController.ACTION_SECURITY_CHANGE)).isFalse();
    }

    @Test
    public void initialState_buttonDisabled() {
        mFragmentController.setup();
        assertThat(getAddWifiButton().isEnabled()).isFalse();
    }

    @Test
    public void receiveNameChangeIntent_emptyName_buttonDisabled() {
        mFragmentController.setup();
        Intent intent = new Intent(NetworkNamePreferenceController.ACTION_NAME_CHANGE);
        intent.putExtra(NetworkNamePreferenceController.KEY_NETWORK_NAME, "");
        mLocalBroadcastManager.sendBroadcastSync(intent);

        assertThat(getAddWifiButton().isEnabled()).isFalse();
    }

    @Test
    public void receiveNameChangeIntent_name_buttonEnabled() {
        mFragmentController.setup();
        String networkName = "test_network_name";
        Intent intent = new Intent(NetworkNamePreferenceController.ACTION_NAME_CHANGE);
        intent.putExtra(NetworkNamePreferenceController.KEY_NETWORK_NAME, networkName);
        mLocalBroadcastManager.sendBroadcastSync(intent);

        assertThat(getAddWifiButton().isEnabled()).isTrue();
    }

    @Test
    public void receiveSecurityChangeIntent_nameSet_buttonDisabled() {
        mFragmentController.setup();
        String networkName = "test_network_name";
        Intent intent = new Intent(NetworkNamePreferenceController.ACTION_NAME_CHANGE);
        intent.putExtra(NetworkNamePreferenceController.KEY_NETWORK_NAME, networkName);
        mLocalBroadcastManager.sendBroadcastSync(intent);

        intent = new Intent(NetworkSecurityPreferenceController.ACTION_SECURITY_CHANGE);
        intent.putExtra(NetworkSecurityPreferenceController.KEY_SECURITY_TYPE,
                AccessPoint.SECURITY_PSK);
        mLocalBroadcastManager.sendBroadcastSync(intent);

        assertThat(getAddWifiButton().isEnabled()).isFalse();
    }

    private Button getAddWifiButton() {
        return mFragment.requireActivity().findViewById(R.id.action_button1);
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
