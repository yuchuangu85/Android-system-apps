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
import android.net.wifi.WifiManager;

import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.common.PreferenceControllerTestHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;

import java.util.Arrays;
import java.util.List;

@RunWith(CarSettingsRobolectricTestRunner.class)
public class WifiStatusPreferenceControllerTest {
    private static final List<Integer> VISIBLE_STATES = Arrays.asList(
            WifiManager.WIFI_STATE_DISABLED,
            WifiManager.WIFI_STATE_ENABLING);
    private static final List<Integer> INVISIBLE_STATES = Arrays.asList(
            WifiManager.WIFI_STATE_ENABLED,
            WifiManager.WIFI_STATE_DISABLING,
            WifiManager.WIFI_STATE_UNKNOWN);

    private Context mContext;
    private Preference mPreference;
    private WifiStatusPreferenceController mController;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.application;
        mPreference = new Preference(mContext);
        PreferenceControllerTestHelper<WifiStatusPreferenceController> controllerHelper =
                new PreferenceControllerTestHelper<>(mContext,
                        WifiStatusPreferenceController.class, mPreference);
        mController = controllerHelper.getController();
        controllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
    }

    @Test
    public void onWifiStateChanged_invisible() {
        for (int state : INVISIBLE_STATES) {
            mController.onWifiStateChanged(state);
            assertThat(mPreference.isVisible()).isEqualTo(false);
        }
    }

    @Test
    public void onWifiStateChanged_visible() {
        for (int state : VISIBLE_STATES) {
            mController.onWifiStateChanged(state);
            assertThat(mPreference.isVisible()).isEqualTo(true);
        }
    }

    @Test
    public void onWifiStateChanged_disabled() {
        mController.onWifiStateChanged(WifiManager.WIFI_STATE_DISABLED);
        assertThat(mPreference.getTitle())
                .isEqualTo(mContext.getResources().getString(R.string.wifi_disabled));
    }

    @Test
    public void onWifiStateChanged_enabling() {
        mController.onWifiStateChanged(WifiManager.WIFI_STATE_ENABLING);
        assertThat(mPreference.getTitle())
                .isEqualTo(mContext.getResources().getString(R.string.loading_wifi_list));
    }
}
