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
import android.net.LinkProperties;
import android.net.Network;
import android.net.RouteInfo;

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

import java.net.InetAddress;
import java.util.Arrays;

@RunWith(CarSettingsRobolectricTestRunner.class)
public class WifiGatewayPreferenceControllerTest {

    private static final String GATE_WAY = "gateway";

    @Mock
    private AccessPoint mMockAccessPoint;
    @Mock
    private WifiInfoProvider mMockWifiInfoProvider;
    @Mock
    private Network mMockNetwork;
    @Mock
    private LinkProperties mMockLinkProperties;
    @Mock
    private RouteInfo mMockRouteInfo;
    @Mock
    private InetAddress mMockInetAddress;

    private Context mContext;
    private WifiDetailsPreference mPreference;
    private WifiGatewayPreferenceController mController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mContext = RuntimeEnvironment.application;
        shadowOf(mContext.getPackageManager()).setSystemFeature(PackageManager.FEATURE_WIFI, true);
        mPreference = new WifiDetailsPreference(mContext);
        when(mMockWifiInfoProvider.getLinkProperties()).thenReturn(mMockLinkProperties);

        PreferenceControllerTestHelper<WifiGatewayPreferenceController> controllerHelper =
                new PreferenceControllerTestHelper<>(mContext,
                        WifiGatewayPreferenceController.class, mPreference);
        mController = (WifiGatewayPreferenceController) controllerHelper.getController().init(
                mMockAccessPoint, mMockWifiInfoProvider);
        controllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
    }

    @Test
    public void onWifiChanged_shouldHaveDetailTextSet() {
        when(mMockAccessPoint.isActive()).thenReturn(true);
        when(mMockLinkProperties.getRoutes()).thenReturn(Arrays.asList(mMockRouteInfo));
        when(mMockRouteInfo.isIPv4Default()).thenReturn(true);
        when(mMockRouteInfo.hasGateway()).thenReturn(true);
        when(mMockRouteInfo.getGateway()).thenReturn(mMockInetAddress);
        when(mMockInetAddress.getHostAddress()).thenReturn(GATE_WAY);

        mController.onLinkPropertiesChanged(mMockNetwork, mMockLinkProperties);
        assertThat(mPreference.getDetailText()).isEqualTo(GATE_WAY);
    }

    @Test
    public void onWifiChanged_isNotActive_noUpdate() {
        when(mMockAccessPoint.isActive()).thenReturn(false);

        mController.onLinkPropertiesChanged(mMockNetwork, mMockLinkProperties);
        assertThat(mPreference.getDetailText()).isNull();
    }
}