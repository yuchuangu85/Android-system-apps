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

package com.android.car.settings.datausage;

import static com.android.car.settings.common.PreferenceController.AVAILABLE;
import static com.android.car.settings.common.PreferenceController.UNSUPPORTED_ON_DEVICE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;
import static org.robolectric.shadow.api.Shadow.extract;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionPlan;
import android.util.RecurrenceRule;

import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowConnectivityManager;
import com.android.car.settings.testutils.ShadowSubscriptionManager;

import com.google.android.collect.Lists;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowNetwork;

@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowConnectivityManager.class, ShadowSubscriptionManager.class})
public class DataUsageEntryPreferenceControllerTest {

    private static final int SUBSCRIPTION_ID = 1;

    private Context mContext;
    private Preference mPreference;
    private DataUsageEntryPreferenceController mController;
    @Mock
    private NetworkCapabilities mNetworkCapabilities;
    @Mock
    private SubscriptionPlan mSubscriptionPlan;
    @Mock
    private RecurrenceRule mRecurrenceRule;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = RuntimeEnvironment.application;
        mPreference = new Preference(mContext);
        PreferenceControllerTestHelper<DataUsageEntryPreferenceController> controllerHelper =
                new PreferenceControllerTestHelper<>(mContext,
                        DataUsageEntryPreferenceController.class, mPreference);
        mController = controllerHelper.getController();

        // Setup to always make preference available.
        getShadowConnectivityManager().clearAllNetworks();
        getShadowConnectivityManager().addNetworkCapabilities(
                ShadowNetwork.newInstance(ConnectivityManager.TYPE_MOBILE), mNetworkCapabilities);
        when(mNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)).thenReturn(
                true);

        controllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
    }

    @After
    public void tearDown() {
        ShadowConnectivityManager.reset();
        ShadowSubscriptionManager.reset();
    }

    @Test
    public void getAvailabilityStatus_noMobileNetwork_isUnsupported() {
        when(mNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)).thenReturn(
                false);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(UNSUPPORTED_ON_DEVICE);
    }

    @Test
    public void getAvailabilityStatus_hasMobileNetwork_isAvailable() {
        when(mNetworkCapabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)).thenReturn(
                true);

        assertThat(mController.getAvailabilityStatus()).isEqualTo(AVAILABLE);
    }

    @Test
    public void refreshUi_noDefaultSubscriptionId_noSummarySet() {
        ShadowSubscriptionManager.setDefaultSubscriptionId(
                SubscriptionManager.INVALID_SUBSCRIPTION_ID);

        mController.refreshUi();
        assertThat(mPreference.getSummary()).isNull();
    }

    @Test
    public void refreshUi_noPrimaryPlan_noSummarySet() {
        ShadowSubscriptionManager.setDefaultSubscriptionId(SUBSCRIPTION_ID);
        getShadowSubscriptionManager().setSubscriptionPlans(Lists.newArrayList());

        mController.refreshUi();
        assertThat(mPreference.getSummary()).isNull();
    }

    @Test
    public void refreshUi_hasPrimaryPlan_setsSummary() {
        ShadowSubscriptionManager.setDefaultSubscriptionId(SUBSCRIPTION_ID);
        getShadowSubscriptionManager().setSubscriptionPlans(Lists.newArrayList(mSubscriptionPlan));
        when(mSubscriptionPlan.getDataLimitBytes()).thenReturn(100L);
        when(mSubscriptionPlan.getDataUsageBytes()).thenReturn(10L);
        when(mSubscriptionPlan.getCycleRule()).thenReturn(mRecurrenceRule);

        mController.refreshUi();
        assertThat(mPreference.getSummary().length()).isGreaterThan(0);
    }

    private ShadowConnectivityManager getShadowConnectivityManager() {
        return (ShadowConnectivityManager) extract(
                mContext.getSystemService(ConnectivityManager.class));
    }

    private ShadowSubscriptionManager getShadowSubscriptionManager() {
        return (ShadowSubscriptionManager) extract(
                mContext.getSystemService(SubscriptionManager.class));
    }
}
