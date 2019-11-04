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

import static android.telephony.euicc.EuiccManager.ACTION_PROVISION_EMBEDDED_SUBSCRIPTION;
import static android.telephony.euicc.EuiccManager.EXTRA_FORCE_PROVISION;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.telephony.euicc.EuiccManager;

import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;

import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;

import java.util.List;

/**
 * Controls the visibility of the preference to add another mobile network based on if there is an
 * activity to handle that intent.
 */
public class AddMobileNetworkPreferenceController extends PreferenceController<Preference> {

    @VisibleForTesting
    static final Intent ADD_NETWORK_INTENT = new Intent(
            ACTION_PROVISION_EMBEDDED_SUBSCRIPTION).putExtra(EXTRA_FORCE_PROVISION, true);

    public AddMobileNetworkPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected Class<Preference> getPreferenceType() {
        return Preference.class;
    }

    @Override
    protected int getAvailabilityStatus() {
        EuiccManager euiccManager = getContext().getSystemService(EuiccManager.class);
        if (!euiccManager.isEnabled()) {
            return UNSUPPORTED_ON_DEVICE;
        }

        List<ResolveInfo> resolveInfos = getContext().getPackageManager().queryIntentActivities(
                ADD_NETWORK_INTENT, /* flags= */ 0);
        if (resolveInfos.isEmpty()) {
            return UNSUPPORTED_ON_DEVICE;
        }

        return AVAILABLE;
    }

    @Override
    protected boolean handlePreferenceClicked(Preference preference) {
        getContext().startActivity(ADD_NETWORK_INTENT);
        return true;
    }
}
