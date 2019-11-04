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

package com.android.car.settings.wifi.details;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;
import static org.robolectric.Shadows.shadowOf;

import android.content.Context;
import android.content.pm.PackageManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiInfo;

import androidx.lifecycle.Lifecycle;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.settingslib.wifi.AccessPoint;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;

@RunWith(CarSettingsRobolectricTestRunner.class)
public class WifiMacAddressPreferenceControllerTest {

    private static final String MAC_ADDRESS = "mac address";

    @Mock
    private AccessPoint mMockAccessPoint;
    @Mock
    private WifiInfoProvider mMockWifiInfoProvider;
    @Mock
    private NetworkInfo mMockNetworkInfo;
    @Mock
    private WifiInfo mMockWifiInfo;

    private Context mContext;
    private WifiDetailsPreference mPreference;
    private WifiMacAddressPreferenceController mController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mContext = RuntimeEnvironment.application;
        shadowOf(mContext.getPackageManager()).setSystemFeature(PackageManager.FEATURE_WIFI, true);
        mPreference = new WifiDetailsPreference(mContext);
        when(mMockWifiInfoProvider.getWifiInfo()).thenReturn(mMockWifiInfo);

        PreferenceControllerTestHelper<WifiMacAddressPreferenceController> controllerHelper =
                new PreferenceControllerTestHelper<>(mContext,
                        WifiMacAddressPreferenceController.class, mPreference);
        mController = (WifiMacAddressPreferenceController) controllerHelper.getController().init(
                mMockAccessPoint, mMockWifiInfoProvider);
        controllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
    }

    @Test
    public void onWifiChanged_shouldHaveDetailTextSet() {
        when(mMockAccessPoint.isActive()).thenReturn(true);
        when(mMockWifiInfo.getMacAddress()).thenReturn(MAC_ADDRESS);

        mController.onWifiChanged(mMockNetworkInfo, mMockWifiInfo);
        assertThat(mPreference.getDetailText()).isEqualTo(MAC_ADDRESS);
    }

    @Test
    public void onWifiChanged_isNotActive_noUpdate() {
        when(mMockAccessPoint.isActive()).thenReturn(false);

        mController.onWifiChanged(mMockNetworkInfo, mMockWifiInfo);
        assertThat(mPreference.getDetailText()).isNull();
    }
}