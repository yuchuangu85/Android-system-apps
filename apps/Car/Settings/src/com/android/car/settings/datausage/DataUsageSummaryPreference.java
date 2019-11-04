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

import android.content.Context;
import android.content.Intent;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.StyleRes;
import androidx.preference.PreferenceViewHolder;

import com.android.car.settings.R;
import com.android.car.settings.common.ProgressBarPreference;

/** Extends {@link ProgressBarPreference} in order to support multiple text fields. */
public class DataUsageSummaryPreference extends ProgressBarPreference {

    private CharSequence mDataLimitText;
    private CharSequence mRemainingBillingCycleText;
    private CharSequence mCarrierInfoText;
    private Intent mManageSubscriptionIntent;
    @StyleRes
    private int mCarrierInfoTextStyle = R.style.DataUsageSummaryCarrierInfoTextAppearance;

    public DataUsageSummaryPreference(Context context, AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init();
    }

    public DataUsageSummaryPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    public DataUsageSummaryPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public DataUsageSummaryPreference(Context context) {
        super(context);
        init();
    }

    private void init() {
        setLayoutResource(R.layout.data_usage_summary_preference);
    }

    /** Sets the data limit text. */
    public void setDataLimitText(CharSequence text) {
        if (!TextUtils.equals(mDataLimitText, text)) {
            mDataLimitText = text;
            notifyChanged();
        }
    }

    /** Gets the data limit text. */
    public CharSequence getDataLimitText() {
        return mDataLimitText;
    }

    /** Sets the remaining billing cycle description. */
    public void setRemainingBillingCycleText(CharSequence text) {
        if (!TextUtils.equals(mRemainingBillingCycleText, text)) {
            mRemainingBillingCycleText = text;
            notifyChanged();
        }
    }

    /** Gets the remaining billing cycle description. */
    public CharSequence getRemainingBillingCycleText() {
        return mRemainingBillingCycleText;
    }

    /** Sets the carrier info text. */
    public void setCarrierInfoText(CharSequence text) {
        if (!TextUtils.equals(mCarrierInfoText, text)) {
            mCarrierInfoText = text;
            notifyChanged();
        }
    }

    /** Gets the carrier info text. */
    public CharSequence getCarrierInfoText() {
        return mCarrierInfoText;
    }

    /** Sets the carrier info text style. */
    public void setCarrierInfoTextStyle(@StyleRes int styleId) {
        if (mCarrierInfoTextStyle != styleId) {
            mCarrierInfoTextStyle = styleId;
            notifyChanged();
        }
    }

    /** Gets the carrier info text style. */
    @StyleRes
    public int getCarrierInfoTextStyle() {
        return mCarrierInfoTextStyle;
    }

    /** Sets the manage subscription intent. */
    public void setManageSubscriptionIntent(Intent intent) {
        mManageSubscriptionIntent = intent;
        notifyChanged();
    }

    /** Gets the manage subscription intent. */
    public Intent getManageSubscriptionIntent() {
        return mManageSubscriptionIntent;
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder view) {
        super.onBindViewHolder(view);

        setTextAndVisibility((TextView) view.findViewById(R.id.data_limit_text), mDataLimitText);
        setTextAndVisibility((TextView) view.findViewById(R.id.remaining_billing_cycle_time_text),
                mRemainingBillingCycleText);
        TextView carrierInfo = (TextView) view.findViewById(R.id.carrier_info_text);
        setTextAndVisibility(carrierInfo, mCarrierInfoText);
        carrierInfo.setTextAppearance(mCarrierInfoTextStyle);

        Button button = (Button) view.findViewById(R.id.manage_subscription_button);
        if (mManageSubscriptionIntent != null) {
            button.setVisibility(View.VISIBLE);
            button.setOnClickListener(v -> getContext().startActivity(mManageSubscriptionIntent));
        } else {
            button.setVisibility(View.GONE);
        }
    }

    private void setTextAndVisibility(TextView textView, CharSequence value) {
        if (!TextUtils.isEmpty(value)) {
            textView.setText(value);
            textView.setVisibility(View.VISIBLE);
        } else {
            textView.setVisibility(View.GONE);
        }
    }
}
