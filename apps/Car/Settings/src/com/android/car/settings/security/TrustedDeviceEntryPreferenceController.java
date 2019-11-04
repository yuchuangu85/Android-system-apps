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

package com.android.car.settings.security;


import android.annotation.Nullable;
import android.car.Car;
import android.car.CarNotConnectedException;
import android.car.drivingstate.CarUxRestrictions;
import android.car.trust.CarTrustAgentEnrollmentManager;
import android.car.userlib.CarUserManagerHelper;
import android.content.Context;

import androidx.preference.Preference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;
import com.android.car.settings.common.PreferenceController;

/**
 * Business logic for trusted device preference.
 */
public class TrustedDeviceEntryPreferenceController extends PreferenceController<Preference> {

    private static final Logger LOG = new Logger(TrustedDeviceEntryPreferenceController.class);
    private final Car mCar;
    private final CarUserManagerHelper mCarUserManagerHelper;
    @Nullable
    private CarTrustAgentEnrollmentManager mCarTrustAgentEnrollmentManager;

    public TrustedDeviceEntryPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mCar = Car.createCar(context);
        mCarUserManagerHelper = new CarUserManagerHelper(context);
        try {
            mCarTrustAgentEnrollmentManager = (CarTrustAgentEnrollmentManager) mCar.getCarManager(
                    Car.CAR_TRUST_AGENT_ENROLLMENT_SERVICE);
        } catch (CarNotConnectedException e) {
            LOG.e(e.getMessage(), e);
        }
    }

    @Override
    protected Class<Preference> getPreferenceType() {
        return Preference.class;
    }

    @Override
    protected void updateState(Preference preference) {
        int listSize = 0;
        try {
            listSize = mCarTrustAgentEnrollmentManager.getEnrolledDeviceInfoForUser(
                    mCarUserManagerHelper.getCurrentProcessUserId()).size();
        } catch (CarNotConnectedException e) {
            LOG.e(e.getMessage(), e);
        }
        preference.setSummary(
                getContext().getResources().getQuantityString(R.plurals.trusted_device_subtitle,
                        listSize, listSize));
    }

    @Override
    protected boolean handlePreferenceClicked(Preference preference) {
        getFragmentController().launchFragment(new ChooseTrustedDeviceFragment());
        return true;
    }
}
