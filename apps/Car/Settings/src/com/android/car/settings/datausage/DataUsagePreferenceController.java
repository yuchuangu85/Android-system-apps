/*
 * Copyright 2019 The Android Open Source Project
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

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.telephony.SubscriptionManager;

import androidx.preference.Preference;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;

/**
 * Controller to handle the business logic for AppDataUsage preference on the data usage screen
 */
public class DataUsagePreferenceController extends PreferenceController<Preference> {

    private int mSubId = Integer.MIN_VALUE;

    private SubscriptionManager mSubscriptionManager;

    public DataUsagePreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mSubscriptionManager = context.getSystemService(SubscriptionManager.class);
    }

    @Override
    protected Class<Preference> getPreferenceType() {
        return Preference.class;
    }

    @Override
    protected boolean handlePreferenceClicked(Preference preference) {
        int subId = mSubId != Integer.MIN_VALUE ? mSubId : getDefaultSubId();
        AppDataUsageFragment appDataUsageFragment = AppDataUsageFragment.newInstance(subId);
        getFragmentController().launchFragment(appDataUsageFragment);
        return true;
    }

    /**
     * Sets the subId for which data usage will be loaded. If this is not set then default subId
     * will be used to load data.
     */
    public void setSubId(int subId) {
        mSubId = subId;
    }

    private int getDefaultSubId() {
        return DataUsageUtils.getDefaultSubscriptionId(mSubscriptionManager);
    }
}
