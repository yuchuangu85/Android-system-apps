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

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.preference.PreferenceViewHolder;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowApplication;

@RunWith(CarSettingsRobolectricTestRunner.class)
public class DataUsageSummaryPreferenceTest {
    private static final String TEST_LABEL = "TEST_LABEL";
    private static final Intent TEST_INTENT = new Intent("test_action");

    private Context mContext;
    private PreferenceViewHolder mViewHolder;
    private DataUsageSummaryPreference mDataUsageSummaryPreference;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.application;
        View rootView = View.inflate(mContext, R.layout.data_usage_summary_preference,
                /* root= */ null);
        mViewHolder = PreferenceViewHolder.createInstanceForTests(rootView);
        mDataUsageSummaryPreference = new DataUsageSummaryPreference(mContext);
    }

    @Test
    public void onBindViewHolder_noDataUsageText_isGone() {
        mDataUsageSummaryPreference.onBindViewHolder(mViewHolder);

        assertThat(getDataUsageText().getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void onBindViewHolder_hasDataUsageText_isVisible() {
        mDataUsageSummaryPreference.setDataLimitText(TEST_LABEL);
        mDataUsageSummaryPreference.onBindViewHolder(mViewHolder);

        assertThat(getDataUsageText().getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void onBindViewHolder_hasDataUsageText_setsText() {
        mDataUsageSummaryPreference.setDataLimitText(TEST_LABEL);
        mDataUsageSummaryPreference.onBindViewHolder(mViewHolder);

        assertThat(mDataUsageSummaryPreference.getDataLimitText().toString()).isEqualTo(TEST_LABEL);
    }

    @Test
    public void onBindViewHolder_noRemainingBillingCycleText_isGone() {
        mDataUsageSummaryPreference.onBindViewHolder(mViewHolder);

        assertThat(getRemainingBillingCycleText().getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void onBindViewHolder_hasRemainingBillingCycleText_isVisible() {
        mDataUsageSummaryPreference.setRemainingBillingCycleText(TEST_LABEL);
        mDataUsageSummaryPreference.onBindViewHolder(mViewHolder);

        assertThat(getRemainingBillingCycleText().getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void onBindViewHolder_hasRemainingBillingCycleText_setsText() {
        mDataUsageSummaryPreference.setRemainingBillingCycleText(TEST_LABEL);
        mDataUsageSummaryPreference.onBindViewHolder(mViewHolder);

        assertThat(mDataUsageSummaryPreference.getRemainingBillingCycleText().toString()).isEqualTo(
                TEST_LABEL);
    }

    @Test
    public void onBindViewHolder_noCarrierInfoText_isGone() {
        mDataUsageSummaryPreference.onBindViewHolder(mViewHolder);

        assertThat(getCarrierInfoText().getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void onBindViewHolder_hasCarrierInfoText_isVisible() {
        mDataUsageSummaryPreference.setCarrierInfoText(TEST_LABEL);
        mDataUsageSummaryPreference.onBindViewHolder(mViewHolder);

        assertThat(getCarrierInfoText().getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void onBindViewHolder_hasCarrierInfoText_setsText() {
        mDataUsageSummaryPreference.setCarrierInfoText(TEST_LABEL);
        mDataUsageSummaryPreference.onBindViewHolder(mViewHolder);

        assertThat(mDataUsageSummaryPreference.getCarrierInfoText().toString()).isEqualTo(
                TEST_LABEL);
    }

    @Test
    public void onBindViewHolder_noManagePlanIntent_isGone() {
        mDataUsageSummaryPreference.onBindViewHolder(mViewHolder);

        assertThat(getManageSubscriptionButton().getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void onBindViewHolder_hasManagePlanIntent_isVisible() {
        mDataUsageSummaryPreference.setManageSubscriptionIntent(TEST_INTENT);
        mDataUsageSummaryPreference.onBindViewHolder(mViewHolder);

        assertThat(getManageSubscriptionButton().getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void onButtonClick_hasManagePlanIntent_startsActivity() {
        mDataUsageSummaryPreference.setManageSubscriptionIntent(TEST_INTENT);
        mDataUsageSummaryPreference.onBindViewHolder(mViewHolder);
        getManageSubscriptionButton().performClick();

        Intent actual = ShadowApplication.getInstance().getNextStartedActivity();
        assertThat(actual.getAction()).isEqualTo(TEST_INTENT.getAction());
    }

    private TextView getDataUsageText() {
        return (TextView) mViewHolder.findViewById(R.id.data_limit_text);
    }

    private TextView getRemainingBillingCycleText() {
        return (TextView) mViewHolder.findViewById(R.id.remaining_billing_cycle_time_text);
    }

    private TextView getCarrierInfoText() {
        return (TextView) mViewHolder.findViewById(R.id.carrier_info_text);
    }

    private Button getManageSubscriptionButton() {
        return (Button) mViewHolder.findViewById(R.id.manage_subscription_button);
    }
}
