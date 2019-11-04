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
import static com.android.car.settings.common.PreferenceController.CONDITIONALLY_UNAVAILABLE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.robolectric.shadow.api.Shadow.extract;

import android.content.Context;
import android.net.NetworkTemplate;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.SubscriptionPlan;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.text.format.Formatter;

import androidx.lifecycle.Lifecycle;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowDataUsageController;
import com.android.car.settings.testutils.ShadowSubscriptionManager;
import com.android.car.settings.testutils.ShadowTelephonyManager;
import com.android.settingslib.net.DataUsageController;

import com.google.android.collect.Lists;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;

import java.time.Period;
import java.time.ZonedDateTime;
import java.util.concurrent.TimeUnit;

@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowTelephonyManager.class, ShadowSubscriptionManager.class,
        ShadowDataUsageController.class})
public class DataUsageSummaryPreferenceControllerTest {

    private static final CharSequence TEST_CARRIER_NAME = "TEST_CARRIER_NAME";

    private Context mContext;
    private DataUsageSummaryPreference mDataUsageSummaryPreference;
    private DataUsageSummaryPreferenceController mController;
    private PreferenceControllerTestHelper<DataUsageSummaryPreferenceController> mControllerHelper;
    private NetworkTemplate mNetworkTemplate;
    @Mock
    private DataUsageController mDataUsageController;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        SubscriptionInfo info = mock(SubscriptionInfo.class);
        when(info.getSubscriptionId()).thenReturn(1);
        ShadowSubscriptionManager.setDefaultDataSubscriptionInfo(info);
        ShadowDataUsageController.setInstance(mDataUsageController);

        mContext = RuntimeEnvironment.application;
        mDataUsageSummaryPreference = new DataUsageSummaryPreference(mContext);
        mControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                DataUsageSummaryPreferenceController.class, mDataUsageSummaryPreference);
        mController = mControllerHelper.getController();

        mNetworkTemplate = DataUsageUtils.getMobileNetworkTemplate(
                mContext.getSystemService(TelephonyManager.class),
                DataUsageUtils.getDefaultSubscriptionId(
                        mContext.getSystemService(SubscriptionManager.class)));
    }

    @After
    public void tearDown() {
        ShadowTelephonyManager.reset();
        ShadowSubscriptionManager.reset();
        ShadowDataUsageController.reset();
    }

    @Test
    public void getAvailabilityStatus_hasSim_isAvailable() {
        getShadowTelephonyManager().setSimState(TelephonyManager.SIM_STATE_LOADED);
        assertThat(mController.getAvailabilityStatus()).isEqualTo(AVAILABLE);
    }

    @Test
    public void getAvailabilityStatus_noSim_isConditionallyUnavailable() {
        getShadowTelephonyManager().setSimState(TelephonyManager.SIM_STATE_UNKNOWN);
        assertThat(mController.getAvailabilityStatus()).isEqualTo(CONDITIONALLY_UNAVAILABLE);
    }

    @Test
    public void refreshUi_hasUsage_titleSet() {
        DataUsageController.DataUsageInfo info = new DataUsageController.DataUsageInfo();
        info.usageLevel = 10000;
        when(mDataUsageController.getDataUsageInfo(mNetworkTemplate)).thenReturn(info);

        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
        mController.refreshUi();

        String usedValueString = Formatter.formatBytes(mContext.getResources(), info.usageLevel,
                Formatter.FLAG_CALCULATE_ROUNDED | Formatter.FLAG_IEC_UNITS).value;
        assertThat(mDataUsageSummaryPreference.getTitle().toString()).contains(usedValueString);
    }

    @Test
    public void refreshUi_noLimits_doesntSetDataLimitText() {
        DataUsageController.DataUsageInfo info = new DataUsageController.DataUsageInfo();
        info.usageLevel = 10000;
        info.limitLevel = -1;
        info.warningLevel = -1;
        when(mDataUsageController.getDataUsageInfo(mNetworkTemplate)).thenReturn(info);

        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
        mController.refreshUi();

        assertThat(mDataUsageSummaryPreference.getDataLimitText()).isNull();
    }

    @Test
    public void refreshUi_hasLimit_setsDataLimitText() {
        DataUsageController.DataUsageInfo info = new DataUsageController.DataUsageInfo();
        info.usageLevel = 10000;
        info.limitLevel = 100000;
        info.warningLevel = -1;
        when(mDataUsageController.getDataUsageInfo(mNetworkTemplate)).thenReturn(info);

        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
        mController.refreshUi();

        assertThat(mDataUsageSummaryPreference.getDataLimitText().toString()).isEqualTo(
                TextUtils.expandTemplate(mContext.getText(R.string.cell_data_limit),
                        DataUsageUtils.bytesToIecUnits(mContext, info.limitLevel)).toString());
    }

    @Test
    public void refreshUi_hasWarning_setsDataLimitText() {
        DataUsageController.DataUsageInfo info = new DataUsageController.DataUsageInfo();
        info.usageLevel = 10000;
        info.limitLevel = -1;
        info.warningLevel = 50000;
        when(mDataUsageController.getDataUsageInfo(mNetworkTemplate)).thenReturn(info);

        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
        mController.refreshUi();

        assertThat(mDataUsageSummaryPreference.getDataLimitText().toString()).isEqualTo(
                TextUtils.expandTemplate(mContext.getText(R.string.cell_data_warning),
                        DataUsageUtils.bytesToIecUnits(mContext, info.warningLevel)).toString());
    }

    @Test
    public void refreshUi_hasWarningAndLimit_setsDataLimitText() {
        DataUsageController.DataUsageInfo info = new DataUsageController.DataUsageInfo();
        info.usageLevel = 10000;
        info.limitLevel = 100000;
        info.warningLevel = 50000;
        when(mDataUsageController.getDataUsageInfo(mNetworkTemplate)).thenReturn(info);

        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
        mController.refreshUi();

        assertThat(mDataUsageSummaryPreference.getDataLimitText().toString()).isEqualTo(
                TextUtils.expandTemplate(mContext.getText(R.string.cell_data_warning_and_limit),
                        DataUsageUtils.bytesToIecUnits(mContext, info.warningLevel),
                        DataUsageUtils.bytesToIecUnits(mContext, info.limitLevel)).toString());
    }

    @Test
    public void refreshUi_endTimeIsGreaterThanOneDay_setsBillingCycleText() {
        int numDays = 20;
        DataUsageController.DataUsageInfo info = new DataUsageController.DataUsageInfo();
        // Add an extra hour to account for the difference in time when the test calls
        // System.currentTimeMillis() vs when the code calls it.
        info.cycleEnd = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(numDays)
                + TimeUnit.HOURS.toMillis(1);
        when(mDataUsageController.getDataUsageInfo(mNetworkTemplate)).thenReturn(info);

        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
        mController.refreshUi();

        assertThat(mDataUsageSummaryPreference.getRemainingBillingCycleText().toString())
                .isEqualTo(
                        mContext.getResources().getQuantityString(R.plurals.billing_cycle_days_left,
                                numDays, numDays));
    }

    @Test
    public void refreshUi_endTimeIsLessThanOneDay_setsBillingCycleText() {
        DataUsageController.DataUsageInfo info = new DataUsageController.DataUsageInfo();
        info.cycleEnd = System.currentTimeMillis() + TimeUnit.HOURS.toMillis(22);
        when(mDataUsageController.getDataUsageInfo(mNetworkTemplate)).thenReturn(info);

        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
        mController.refreshUi();

        assertThat(mDataUsageSummaryPreference.getRemainingBillingCycleText().toString())
                .isEqualTo(
                        mContext.getString(R.string.billing_cycle_less_than_one_day_left));
    }

    @Test
    public void refreshUi_endTimeIsNow_setsBillingCycleText() {
        DataUsageController.DataUsageInfo info = new DataUsageController.DataUsageInfo();
        info.cycleEnd = System.currentTimeMillis();
        when(mDataUsageController.getDataUsageInfo(mNetworkTemplate)).thenReturn(info);

        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
        mController.refreshUi();

        assertThat(mDataUsageSummaryPreference.getRemainingBillingCycleText().toString())
                .isEqualTo(
                        mContext.getString(R.string.billing_cycle_none_left));
    }

    @Test
    public void refreshUi_hasCarrierName_hasRecentUpdate_setsCarrierInfoText() {
        DataUsageController.DataUsageInfo info = new DataUsageController.DataUsageInfo();
        info.usageLevel = 10000;
        when(mDataUsageController.getDataUsageInfo(mNetworkTemplate)).thenReturn(info);

        setCarrierName(TEST_CARRIER_NAME);
        setSubscriptionPlan(/* usageBytes= */ 1000L, System.currentTimeMillis());

        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
        mController.refreshUi();

        assertThat(mDataUsageSummaryPreference.getCarrierInfoText()).isEqualTo(
                TextUtils.expandTemplate(mContext.getText(R.string.carrier_and_update_now_text),
                        TEST_CARRIER_NAME));
    }

    @Test
    public void refreshUi_hasCarrierName_hasOldUpdate_setsCarrierInfoText() {
        DataUsageController.DataUsageInfo info = new DataUsageController.DataUsageInfo();
        info.usageLevel = 10000;
        when(mDataUsageController.getDataUsageInfo(mNetworkTemplate)).thenReturn(info);

        int numDays = 15;
        setCarrierName(TEST_CARRIER_NAME);
        setSubscriptionPlan(/* usageBytes= */ 1000L,
                System.currentTimeMillis() - TimeUnit.DAYS.toMillis(numDays));

        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
        mController.refreshUi();

        assertThat(mDataUsageSummaryPreference.getCarrierInfoText()).isEqualTo(
                TextUtils.expandTemplate(mContext.getText(R.string.carrier_and_update_text),
                        TEST_CARRIER_NAME, numDays + " days"));
    }

    @Test
    public void refreshUi_noCarrierName_hasRecentUpdate_setsCarrierInfoText() {
        DataUsageController.DataUsageInfo info = new DataUsageController.DataUsageInfo();
        info.usageLevel = 10000;
        when(mDataUsageController.getDataUsageInfo(mNetworkTemplate)).thenReturn(info);

        setSubscriptionPlan(/* usageBytes= */ 1000L, System.currentTimeMillis());

        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
        mController.refreshUi();

        assertThat(mDataUsageSummaryPreference.getCarrierInfoText().toString()).isEqualTo(
                mContext.getString(R.string.no_carrier_update_now_text));
    }

    @Test
    public void refreshUi_noCarrierName_hasOldUpdate_setsCarrierInfoText() {
        DataUsageController.DataUsageInfo info = new DataUsageController.DataUsageInfo();
        info.usageLevel = 10000;
        when(mDataUsageController.getDataUsageInfo(mNetworkTemplate)).thenReturn(info);

        int numDays = 15;
        setSubscriptionPlan(/* usageBytes= */ 1000L,
                System.currentTimeMillis() - TimeUnit.DAYS.toMillis(numDays));

        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
        mController.refreshUi();

        assertThat(mDataUsageSummaryPreference.getCarrierInfoText()).isEqualTo(
                TextUtils.expandTemplate(mContext.getText(R.string.no_carrier_update_text),
                        null, numDays + " days"));
    }

    @Test
    public void refreshUi_hasUpdateTimeOlderThanWarning_setsCarrierInfoStyle() {
        DataUsageController.DataUsageInfo info = new DataUsageController.DataUsageInfo();
        info.usageLevel = 10000;
        when(mDataUsageController.getDataUsageInfo(mNetworkTemplate)).thenReturn(info);


        // Subtract an extra hour to account fo the difference in calls to
        // System.currentTimeMillis().
        setSubscriptionPlan(/* usageBytes= */ 1000L,
                System.currentTimeMillis() - DataUsageSummaryPreferenceController.WARNING_AGE
                        - TimeUnit.HOURS.toMillis(1));

        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
        mController.refreshUi();

        assertThat(mDataUsageSummaryPreference.getCarrierInfoTextStyle()).isEqualTo(
                R.style.DataUsageSummaryCarrierInfoWarningTextAppearance);
    }

    @Test
    public void refreshUi_hasUpdateTimeYoungerThanWarning_setsCarrierInfoStyle() {
        DataUsageController.DataUsageInfo info = new DataUsageController.DataUsageInfo();
        info.usageLevel = 10000;
        when(mDataUsageController.getDataUsageInfo(mNetworkTemplate)).thenReturn(info);

        // Subtract an extra hour to account fo the difference in calls to
        // System.currentTimeMillis().
        setSubscriptionPlan(/* usageBytes= */ 1000L,
                System.currentTimeMillis() - DataUsageSummaryPreferenceController.WARNING_AGE
                        + TimeUnit.HOURS.toMillis(1));

        mControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);
        mController.refreshUi();

        assertThat(mDataUsageSummaryPreference.getCarrierInfoTextStyle()).isEqualTo(
                R.style.DataUsageSummaryCarrierInfoTextAppearance);
    }

    private ShadowTelephonyManager getShadowTelephonyManager() {
        return (ShadowTelephonyManager) extract(
                mContext.getSystemService(TelephonyManager.class));
    }

    private ShadowSubscriptionManager getShadowSubscriptionManager() {
        return Shadow.extract(mContext.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE));
    }

    private void setCarrierName(CharSequence name) {
        SubscriptionInfo subInfo = new SubscriptionInfo(/* id= */ 0, /* iccId= */ "",
                /* simSlotIndex= */ 0, /* displayName= */ "", name,
                /* nameSource= */ 0, /* iconTint= */ 0, /* number= */ "",
                /* roaming= */ 0, /* icon= */ null, /* mcc= */ "", /* mnc= */ "",
                /* countryIso= */ "", /* isEmbedded= */ false,
                /* accessRules= */ null, /* cardString= */ "");
        ShadowSubscriptionManager.setDefaultDataSubscriptionInfo(subInfo);
    }

    private void setSubscriptionPlan(long usageBytes, long snapshotMillis) {
        ZonedDateTime start = ZonedDateTime.now();
        ZonedDateTime end = ZonedDateTime.now().plusDays(30);
        SubscriptionPlan plan = new SubscriptionPlan.Builder(start, end, Period.ofMonths(1))
                .setDataLimit(/* dataLimitBytes= */ 5000000000L,
                        SubscriptionPlan.LIMIT_BEHAVIOR_DISABLED)
                .setDataUsage(usageBytes, snapshotMillis)
                .build();
        getShadowSubscriptionManager().setSubscriptionPlans(Lists.newArrayList(plan));
    }
}
