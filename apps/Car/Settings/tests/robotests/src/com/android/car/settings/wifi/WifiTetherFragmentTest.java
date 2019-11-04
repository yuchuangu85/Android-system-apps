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

import static org.mockito.Mockito.when;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.widget.Switch;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.testutils.FragmentController;
import com.android.car.settings.testutils.ShadowCarWifiManager;
import com.android.car.settings.testutils.ShadowConnectivityManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;

@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowCarWifiManager.class, ShadowConnectivityManager.class})
public class WifiTetherFragmentTest {

    private Context mContext;
    private WifiTetherFragment mFragment;
    private FragmentController<WifiTetherFragment> mFragmentController;
    @Mock
    private CarWifiManager mCarWifiManager;
    @Mock
    private ConnectivityManager mConnectivityManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = RuntimeEnvironment.application;
        mFragment = new WifiTetherFragment();
        mFragmentController = FragmentController.of(mFragment);
    }

    @After
    public void tearDown() {
        ShadowConnectivityManager.reset();
        ShadowCarWifiManager.reset();
    }

    @Test
    public void onStart_tetherStateOn_shouldReturnSwitchStateOn() {
        when(mCarWifiManager.isWifiApEnabled()).thenReturn(true);
        ShadowCarWifiManager.setInstance(mCarWifiManager);

        mFragmentController.setup();

        assertThat(findSwitch(mFragment.requireActivity()).isChecked()).isTrue();
    }

    @Test
    public void onStart_tetherStateOff_shouldReturnSwitchStateOff() {
        when(mCarWifiManager.isWifiApEnabled()).thenReturn(false);
        ShadowCarWifiManager.setInstance(mCarWifiManager);

        mFragmentController.setup();

        assertThat(findSwitch(mFragment.requireActivity()).isChecked()).isFalse();
    }

    @Test
    public void onSwitchOn_shouldAttemptTetherOn() {
        when(mCarWifiManager.isWifiApEnabled()).thenReturn(false);
        ShadowCarWifiManager.setInstance(mCarWifiManager);

        mFragmentController.setup();
        findSwitch(mFragment.requireActivity()).performClick();

        assertThat(getShadowConnectivityManager().verifyStartTetheringCalled(1)).isTrue();
        assertThat(getShadowConnectivityManager().getTetheringType()
                == ConnectivityManager.TETHERING_WIFI).isTrue();
    }

    @Test
    public void onSwitchOff_shouldAttemptTetherOff() {
        when(mCarWifiManager.isWifiApEnabled()).thenReturn(true);
        ShadowCarWifiManager.setInstance(mCarWifiManager);

        mFragmentController.setup();
        findSwitch(mFragment.requireActivity()).performClick();

        assertThat(getShadowConnectivityManager().verifyStopTetheringCalled(1)).isTrue();
        assertThat(getShadowConnectivityManager().getTetheringType()
                == ConnectivityManager.TETHERING_WIFI).isTrue();
    }

    @Test
    public void onTetherEnabling_shouldReturnSwitchStateDisabled() {
        when(mCarWifiManager.isWifiApEnabled()).thenReturn(false);
        ShadowCarWifiManager.setInstance(mCarWifiManager);
        mFragmentController.setup();

        Intent intent = new Intent(WifiManager.WIFI_AP_STATE_CHANGED_ACTION);
        intent.putExtra(WifiManager.EXTRA_WIFI_AP_STATE, WifiManager.WIFI_AP_STATE_ENABLING);
        mContext.sendBroadcast(intent);

        assertThat(findSwitch(mFragment.requireActivity()).isEnabled()).isFalse();
    }

    @Test
    public void onTetherEnabled_shouldReturnSwitchStateEnabledAndOn() {
        when(mCarWifiManager.isWifiApEnabled()).thenReturn(false);
        ShadowCarWifiManager.setInstance(mCarWifiManager);
        mFragmentController.setup();

        Intent intent = new Intent(WifiManager.WIFI_AP_STATE_CHANGED_ACTION);
        intent.putExtra(WifiManager.EXTRA_WIFI_AP_STATE, WifiManager.WIFI_AP_STATE_ENABLED);
        mContext.sendBroadcast(intent);

        assertThat(findSwitch(mFragment.requireActivity()).isEnabled()).isTrue();
        assertThat(findSwitch(mFragment.requireActivity()).isChecked()).isTrue();
    }

    @Test
    public void onTetherDisabled_shouldReturnSwitchStateEnabledAndOff() {
        when(mCarWifiManager.isWifiApEnabled()).thenReturn(false);
        ShadowCarWifiManager.setInstance(mCarWifiManager);
        mFragmentController.setup();

        Intent intent = new Intent(WifiManager.WIFI_AP_STATE_CHANGED_ACTION);
        intent.putExtra(WifiManager.EXTRA_WIFI_AP_STATE, WifiManager.WIFI_AP_STATE_DISABLED);
        mContext.sendBroadcast(intent);

        assertThat(findSwitch(mFragment.requireActivity()).isEnabled()).isTrue();
        assertThat(findSwitch(mFragment.requireActivity()).isChecked()).isFalse();
    }

    @Test
    public void onEnableTetherFailed_shouldReturnSwitchStateEnabledAndOff() {
        when(mCarWifiManager.isWifiApEnabled()).thenReturn(false);
        ShadowCarWifiManager.setInstance(mCarWifiManager);
        mFragmentController.setup();

        Intent intent = new Intent(WifiManager.WIFI_AP_STATE_CHANGED_ACTION);
        intent.putExtra(WifiManager.EXTRA_WIFI_AP_STATE, WifiManager.WIFI_AP_STATE_ENABLING);
        mContext.sendBroadcast(intent);

        Intent intent2 = new Intent(WifiManager.WIFI_AP_STATE_CHANGED_ACTION);
        intent.putExtra(WifiManager.EXTRA_WIFI_AP_STATE, WifiManager.WIFI_AP_STATE_FAILED);
        mContext.sendBroadcast(intent2);

        assertThat(findSwitch(mFragment.requireActivity()).isEnabled()).isTrue();
        assertThat(findSwitch(mFragment.requireActivity()).isChecked()).isFalse();
    }

    private Switch findSwitch(Activity activity) {
        return activity.findViewById(R.id.toggle_switch);
    }

    private ShadowConnectivityManager getShadowConnectivityManager() {
        return Shadow.extract(mContext.getSystemService(Context.CONNECTIVITY_SERVICE));
    }
}
