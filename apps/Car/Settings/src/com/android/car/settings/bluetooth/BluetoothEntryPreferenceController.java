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

package com.android.car.settings.bluetooth;

import static android.content.pm.PackageManager.FEATURE_BLUETOOTH;
import static android.os.UserManager.DISALLOW_BLUETOOTH;

import android.car.drivingstate.CarUxRestrictions;
import android.car.userlib.CarUserManagerHelper;
import android.content.Context;

import androidx.preference.Preference;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;

/**
 * Controller which determines if the top level entry into Bluetooth settings should be displayed
 * based on device capabilities and user restrictions.
 */
public class BluetoothEntryPreferenceController extends PreferenceController<Preference> {

    private final CarUserManagerHelper mCarUserManagerHelper;

    public BluetoothEntryPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mCarUserManagerHelper = new CarUserManagerHelper(context);
    }

    @Override
    protected Class<Preference> getPreferenceType() {
        return Preference.class;
    }

    @Override
    public int getAvailabilityStatus() {
        if (!getContext().getPackageManager().hasSystemFeature(FEATURE_BLUETOOTH)) {
            return UNSUPPORTED_ON_DEVICE;
        }
        return mCarUserManagerHelper.isCurrentProcessUserHasRestriction(DISALLOW_BLUETOOTH)
                ? DISABLED_FOR_USER : AVAILABLE;
    }
}
