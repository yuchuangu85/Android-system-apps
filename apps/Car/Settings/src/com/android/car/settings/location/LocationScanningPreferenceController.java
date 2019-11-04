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

package com.android.car.settings.location;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.PackageManager;

import androidx.preference.Preference;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;

/**
 * Hides the Scanning entry if neither Wi-Fi nor Bluetooth are supported.
 */
public class LocationScanningPreferenceController extends PreferenceController<Preference> {

    public LocationScanningPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected Class<Preference> getPreferenceType() {
        return Preference.class;
    }

    @Override
    public int getAvailabilityStatus() {
        PackageManager packageManager = getContext().getPackageManager();
        return (packageManager.hasSystemFeature(PackageManager.FEATURE_WIFI)
                || packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE))
                ? AVAILABLE : UNSUPPORTED_ON_DEVICE;
    }
}
