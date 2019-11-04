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

package com.android.car.settings.network;

import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.DISABLED_FOR_USER;
import static com.android.car.settings.common.PreferenceController.UNSUPPORTED_ON_DEVICE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.robolectric.shadow.api.Shadow.extract;

import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.content.pm.UserInfo;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.os.UserManager;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowCarUserManagerHelper;
import com.android.car.settings.testutils.ShadowConnectivityManager;
import com.android.car.settings.testutils.ShadowSubscriptionManager;
import com.android.car.settings.testutils.ShadowTelephonyManager;

import com.google.android.collect.Lists;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowNetwork;

import java.util.List;

@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowCarUserManagerHelper.class, ShadowConnectivityManager.class,
        ShadowTelephonyManager.class, ShadowSubscriptionManager.class})
public class MobileNetworkEntryPreferenceControllerTest {

    private static final String TEST_NETWORK_NAME = "test network name";
    private static final UserInfo TEST_ADMIN_USER = new UserInfo(10, "test_name",
            UserInfo.FLAG_ADMIN);
    private static final UserInfo TEST_NON_ADMIN_USER = new UserInfo(10, "test_name",
            /* flags= */ 0);

    private Context mContext;
    private Preference mPreference;
    private PreferenceControllerTestHelper<MobileNetworkEntryPreferenceController>
            mControllerHelper;
    private MobileNetworkEntryPreferenceController mController;
    @Mock
    private CarUserManagerHelper mCarUserManagerHelper;
    @Mock
    private NetworkCapabilities mNetworkCapabilities;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ShadowCarUserManagerHelper.setMockInstance(mCarUserManagerHelper);
        mContext = RuntimeEnvironment.application;
        mPreference = new Preference(mContext);
        mControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                MobileNetworkEntryPreferenceController.class, mPreference);
        mController = mControllerHelper.getController();

        // Setup to always make preference available.
        getShadowConnectivityManager().clearAllNetworks();
        getShadowConnectivityManager().addNetworkCapabilities(
                ShadowNetwork.newInstance(ConnectivityManager.TYPE_MOBILE), mNetworkCapabilities);
        when(mNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)).thenReturn(
                true);
        when(mCarUserManagerHelper.getCurrentProcessUserInfo()).thenReturn(TEST_ADMIN_USER);
        when(mCarUserManagerHelper.isCurrentProcessUserHasRestriction(
                UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS)).thenReturn(false);
    }

    @After
    public void tearDown() {
        ShadowCarUserManagerHelper.reset();
        ShadowConnectivityManager.reset();
        ShadowTelephonyManager.reset();
    }

    @Test
    public void getAvailabilityStatus_noMobileNetwork_unsupported() {
        when(mNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)).thenReturn(
                false);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_notAdmin_disabledForUser() {
        when(mNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)).thenReturn(
                true);
        when(mCarUserManagerHelper.getCurrentProcessUserInfo()).thenReturn(TEST_NON_ADMIN_USER);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(DISABLED_FOR_USER);
    }

    @Test
    public void getAvailabilityStatus_hasRestriction_disabledForUser() {
        when(mNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)).thenReturn(
                true);
        when(mCarUserManagerHelper.getCurrentProcessUserInfo()).thenReturn(TEST_ADMIN_USER);
        when(mCarUserManagerHelper.isCurrentProcessUserHasRestriction(
                UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS)).thenReturn(true);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(DISABLED_FOR_USER);
    }

    @Test
    public void getAvailabilityStatus_hasMobileNetwork_isAdmin_noRestriction_available() {
        when(mNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)).thenReturn(
                true);
        when(mCarUserManagerHelper.getCurrentProcessUserInfo()).thenReturn(TEST_ADMIN_USER);
        when(mCarUserManagerHelper.isCurrentProcessUserHasRestriction(
                UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS)).thenReturn(false);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(AVAILABLE);
    }

    @Test
    public void refreshUi_noSims_disabled() {
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
        mController.refreshUi();

        assertThat(mPreference.isEnabled()).isFalse();
    }

    @Test
    public void refreshUi_oneSim_enabled() {
        SubscriptionInfo info = createSubscriptionInfo(/* subId= */ 1,
                /* simSlotIndex= */ 1, TEST_NETWORK_NAME);
        List<SubscriptionInfo> selectable = Lists.newArrayList(info);
        getShadowSubscriptionManager().setSelectableSubscriptionInfoList(selectable);

        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
        mController.refreshUi();

        assertThat(mPreference.isEnabled()).isTrue();
    }

    @Test
    public void refreshUi_oneSim_summaryIsDisplayName() {
        SubscriptionInfo info = createSubscriptionInfo(/* subId= */ 1,
                /* simSlotIndex= */ 1, TEST_NETWORK_NAME);
        List<SubscriptionInfo> selectable = Lists.newArrayList(info);
        getShadowSubscriptionManager().setSelectableSubscriptionInfoList(selectable);

        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
        mController.refreshUi();

        assertThat(mPreference.getSummary()).isEqualTo(TEST_NETWORK_NAME);
    }

    @Test
    public void refreshUi_multiSim_enabled() {
        SubscriptionInfo info1 = createSubscriptionInfo(/* subId= */ 1,
                /* simSlotIndex= */ 1, TEST_NETWORK_NAME);
        SubscriptionInfo info2 = createSubscriptionInfo(/* subId= */ 2,
                /* simSlotIndex= */ 2, TEST_NETWORK_NAME);
        List<SubscriptionInfo> selectable = Lists.newArrayList(info1, info2);
        getShadowSubscriptionManager().setSelectableSubscriptionInfoList(selectable);

        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
        mController.refreshUi();

        assertThat(mPreference.isEnabled()).isTrue();
    }

    @Test
    public void refreshUi_multiSim_summaryShowsCount() {
        SubscriptionInfo info1 = createSubscriptionInfo(/* subId= */ 1,
                /* simSlotIndex= */ 1, TEST_NETWORK_NAME);
        SubscriptionInfo info2 = createSubscriptionInfo(/* subId= */ 2,
                /* simSlotIndex= */ 2, TEST_NETWORK_NAME);
        List<SubscriptionInfo> selectable = Lists.newArrayList(info1, info2);
        getShadowSubscriptionManager().setSelectableSubscriptionInfoList(selectable);

        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
        mController.refreshUi();

        assertThat(mPreference.getSummary()).isEqualTo(mContext.getResources().getQuantityString(
                R.plurals.mobile_network_summary_count, 2, 2));
    }

    @Test
    public void performClick_noSim_noFragmentStarted() {
        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
        mPreference.performClick();

        verify(mControllerHelper.getMockFragmentController(), never()).launchFragment(
                any(Fragment.class));
    }

    @Test
    public void performClick_oneSim_startsMobileNetworkFragment() {
        int subId = 1;
        SubscriptionInfo info = createSubscriptionInfo(subId, /* simSlotIndex= */ 1,
                TEST_NETWORK_NAME);
        List<SubscriptionInfo> selectable = Lists.newArrayList(info);
        getShadowSubscriptionManager().setSelectableSubscriptionInfoList(selectable);

        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
        mPreference.performClick();

        ArgumentCaptor<MobileNetworkFragment> captor = ArgumentCaptor.forClass(
                MobileNetworkFragment.class);
        verify(mControllerHelper.getMockFragmentController()).launchFragment(captor.capture());

        assertThat(captor.getValue().getArguments().getInt(MobileNetworkFragment.ARG_NETWORK_SUB_ID,
                -1)).isEqualTo(subId);
    }

    @Test
    public void performClick_multiSim_startsMobileNetworkListFragment() {
        SubscriptionInfo info1 = createSubscriptionInfo(/* subId= */ 1,
                /* simSlotIndex= */ 1, TEST_NETWORK_NAME);
        SubscriptionInfo info2 = createSubscriptionInfo(/* subId= */ 2,
                /* simSlotIndex= */ 2, TEST_NETWORK_NAME);
        List<SubscriptionInfo> selectable = Lists.newArrayList(info1, info2);
        getShadowSubscriptionManager().setSelectableSubscriptionInfoList(selectable);

        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
        mPreference.performClick();

        verify(mControllerHelper.getMockFragmentController()).launchFragment(
                any(MobileNetworkListFragment.class));
    }

    private ShadowTelephonyManager getShadowTelephonyManager() {
        return (ShadowTelephonyManager) extract(mContext.getSystemService(TelephonyManager.class));
    }

    private ShadowConnectivityManager getShadowConnectivityManager() {
        return (ShadowConnectivityManager) extract(
                mContext.getSystemService(ConnectivityManager.class));
    }

    private ShadowSubscriptionManager getShadowSubscriptionManager() {
        return Shadow.extract(mContext.getSystemService(SubscriptionManager.class));
    }

    private SubscriptionInfo createSubscriptionInfo(int subId, int simSlotIndex,
            String displayName) {
        SubscriptionInfo subInfo = new SubscriptionInfo(subId, /* iccId= */ "",
                simSlotIndex, displayName, /* carrierName= */ "",
                /* nameSource= */ 0, /* iconTint= */ 0, /* number= */ "",
                /* roaming= */ 0, /* icon= */ null, /* mcc= */ "", "mncString",
                /* countryIso= */ "", /* isEmbedded= */ false,
                /* accessRules= */ null, /* cardString= */ "");
        return subInfo;
    }
}
