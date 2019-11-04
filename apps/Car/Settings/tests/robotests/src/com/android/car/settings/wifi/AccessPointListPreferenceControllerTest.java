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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.PackageManager;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.util.Pair;

import androidx.lifecycle.Lifecycle;
import androidx.preference.PreferenceGroup;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.LogicalPreferenceGroup;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowCarWifiManager;
import com.android.car.settings.testutils.ShadowWifiManager;
import com.android.car.settings.wifi.details.WifiDetailsFragment;
import com.android.settingslib.wifi.AccessPoint;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowCarWifiManager.class, ShadowWifiManager.class})
public class AccessPointListPreferenceControllerTest {
    private static final int SIGNAL_LEVEL = 1;
    @Mock
    private AccessPoint mMockAccessPoint1;
    @Mock
    private AccessPoint mMockAccessPoint2;
    @Mock
    private CarWifiManager mMockCarWifiManager;

    private Context mContext;
    private PreferenceGroup mPreferenceGroup;
    private AccessPointListPreferenceController mController;
    private FragmentController mFragmentController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ShadowCarWifiManager.setInstance(mMockCarWifiManager);
        mContext = RuntimeEnvironment.application;
        Shadows.shadowOf(mContext.getPackageManager()).setSystemFeature(PackageManager.FEATURE_WIFI,
                true);
        mPreferenceGroup = new LogicalPreferenceGroup(mContext);
        PreferenceControllerTestHelper<AccessPointListPreferenceController> controllerHelper =
                new PreferenceControllerTestHelper<>(mContext,
                        AccessPointListPreferenceController.class, mPreferenceGroup);
        mController = controllerHelper.getController();
        mFragmentController = controllerHelper.getMockFragmentController();

        when(mMockAccessPoint1.getSecurity()).thenReturn(AccessPoint.SECURITY_NONE);
        when(mMockAccessPoint1.getLevel()).thenReturn(SIGNAL_LEVEL);
        when(mMockAccessPoint2.getSecurity()).thenReturn(AccessPoint.SECURITY_NONE);
        when(mMockAccessPoint2.getLevel()).thenReturn(SIGNAL_LEVEL);

        controllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
    }

    @After
    public void tearDown() {
        ShadowCarWifiManager.reset();
    }

    @Test
    public void refreshUi_emptyList_notVisible() {
        when(mMockCarWifiManager.getAllAccessPoints()).thenReturn(new ArrayList<>());
        mController.refreshUi();

        assertThat(mPreferenceGroup.isVisible()).isEqualTo(false);
    }

    @Test
    public void refreshUi_notEmpty_visible() {
        List<AccessPoint> accessPointList = Arrays.asList(mMockAccessPoint1);
        when(mMockCarWifiManager.getAllAccessPoints()).thenReturn(accessPointList);
        mController.refreshUi();

        assertThat(mPreferenceGroup.isVisible()).isEqualTo(true);
    }

    @Test
    public void refreshUi_notEmpty_listCount() {
        List<AccessPoint> accessPointList = Arrays.asList(mMockAccessPoint1);
        when(mMockCarWifiManager.getAllAccessPoints()).thenReturn(accessPointList);
        mController.refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(accessPointList.size());
    }

    @Test
    public void onUxRestrictionsChanged_switchToSavedApOnly() {
        List<AccessPoint> allAccessPointList = Arrays.asList(mMockAccessPoint1, mMockAccessPoint2);
        when(mMockCarWifiManager.getAllAccessPoints()).thenReturn(allAccessPointList);
        List<AccessPoint> savedAccessPointList = Arrays.asList(mMockAccessPoint1);
        when(mMockCarWifiManager.getSavedAccessPoints()).thenReturn(savedAccessPointList);
        mController.refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(allAccessPointList.size());

        CarUxRestrictions noSetupRestrictions = new CarUxRestrictions.Builder(
                true, CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP, 0).build();
        mController.onUxRestrictionsChanged(noSetupRestrictions);
        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(savedAccessPointList.size());
    }

    @Test
    public void performClick_noSecurityNotConnectedAccessPoint_connect() {
        when(mMockAccessPoint1.getSecurity()).thenReturn(AccessPoint.SECURITY_NONE);
        when(mMockAccessPoint1.isSaved()).thenReturn(false);
        when(mMockAccessPoint1.isActive()).thenReturn(false);
        List<AccessPoint> accessPointList = Arrays.asList(mMockAccessPoint1);
        when(mMockCarWifiManager.getAllAccessPoints()).thenReturn(accessPointList);
        mController.refreshUi();

        mPreferenceGroup.getPreference(0).performClick();
        verify(mMockCarWifiManager).connectToPublicWifi(eq(mMockAccessPoint1), any());
    }

    @Test
    public void performClick_activeAccessPoint_showDetailsFragment() {
        when(mMockAccessPoint1.isActive()).thenReturn(true);
        List<AccessPoint> accessPointList = Arrays.asList(mMockAccessPoint1);
        when(mMockCarWifiManager.getAllAccessPoints()).thenReturn(accessPointList);
        mController.refreshUi();

        mPreferenceGroup.getPreference(0).performClick();
        verify(mFragmentController).launchFragment(any(WifiDetailsFragment.class));
    }

    @Test
    public void performClick_savedAccessPoint_connect() {
        when(mMockAccessPoint1.isSaved()).thenReturn(true);
        when(mMockAccessPoint1.isActive()).thenReturn(false);
        List<AccessPoint> accessPointList = Arrays.asList(mMockAccessPoint1);
        when(mMockCarWifiManager.getAllAccessPoints()).thenReturn(accessPointList);
        mController.refreshUi();

        mPreferenceGroup.getPreference(0).performClick();
        verify(mMockCarWifiManager).connectToSavedWifi(eq(mMockAccessPoint1), any());
    }

    @Test
    public void performButtonClick_savedAccessPoint_wrongPassword_forgetsNetwork() {
        int netId = 1;

        WifiConfiguration config = mock(WifiConfiguration.class);
        WifiConfiguration.NetworkSelectionStatus status = mock(
                WifiConfiguration.NetworkSelectionStatus.class);
        config.networkId = netId;
        when(mMockAccessPoint1.getSecurity()).thenReturn(AccessPoint.SECURITY_PSK);
        when(mMockAccessPoint1.isSaved()).thenReturn(true);
        when(mMockAccessPoint1.getConfig()).thenReturn(config);
        when(config.getNetworkSelectionStatus()).thenReturn(status);
        when(status.isNetworkEnabled()).thenReturn(false);
        when(status.getNetworkSelectionDisableReason()).thenReturn(
                WifiConfiguration.NetworkSelectionStatus.DISABLED_BY_WRONG_PASSWORD);

        List<AccessPoint> accessPointList = Arrays.asList(mMockAccessPoint1);
        when(mMockCarWifiManager.getAllAccessPoints()).thenReturn(accessPointList);
        mController.refreshUi();

        ButtonPasswordEditTextPreference preference =
                (ButtonPasswordEditTextPreference) mPreferenceGroup.getPreference(0);
        preference.performButtonClick();

        assertThat(getShadowWifiManager().getLastForgottenNetwork()).isEqualTo(netId);
    }

    @Test
    public void callChangeListener_newSecureAccessPoint_wifiAdded() {
        String ssid = "test_ssid";
        String password = "test_password";
        when(mMockAccessPoint1.getSsid()).thenReturn(ssid);
        when(mMockAccessPoint1.getSecurity()).thenReturn(AccessPoint.SECURITY_PSK);
        when(mMockAccessPoint1.isSaved()).thenReturn(false);
        when(mMockAccessPoint1.isActive()).thenReturn(false);
        List<AccessPoint> accessPointList = Arrays.asList(mMockAccessPoint1);
        when(mMockCarWifiManager.getAllAccessPoints()).thenReturn(accessPointList);
        mController.refreshUi();

        mPreferenceGroup.getPreference(0).callChangeListener(password);
        WifiConfiguration lastAdded = getShadowWifiManager().getLastAddedNetworkConfiguration();

        assertThat(lastAdded.SSID).contains(ssid);
        assertThat(lastAdded.getAuthType()).isEqualTo(WifiConfiguration.KeyMgmt.WPA_PSK);
        assertThat(lastAdded.preSharedKey).contains(password);
    }

    @Test
    public void callChangeListener_newSecureAccessPoint_wifiEnabled() {
        String ssid = "test_ssid";
        String password = "test_password";
        when(mMockAccessPoint1.getSsid()).thenReturn(ssid);
        when(mMockAccessPoint1.getSecurity()).thenReturn(AccessPoint.SECURITY_PSK);
        when(mMockAccessPoint1.isSaved()).thenReturn(false);
        when(mMockAccessPoint1.isActive()).thenReturn(false);
        List<AccessPoint> accessPointList = Arrays.asList(mMockAccessPoint1);
        when(mMockCarWifiManager.getAllAccessPoints()).thenReturn(accessPointList);
        mController.refreshUi();

        mPreferenceGroup.getPreference(0).callChangeListener(password);
        Pair<Integer, Boolean> lastEnabled = getShadowWifiManager().getLastEnabledNetwork();

        // Enable should be called on the most recently added network id.
        assertThat(lastEnabled.first).isEqualTo(getShadowWifiManager().getLastAddedNetworkId());
        // WifiUtil will try to enable the network right away.
        assertThat(lastEnabled.second).isTrue();
    }

    private ShadowWifiManager getShadowWifiManager() {
        return Shadow.extract(mContext.getSystemService(WifiManager.class));
    }
}
