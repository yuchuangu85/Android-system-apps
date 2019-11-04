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

package com.android.car.settings.system;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.os.SystemProperties;

import androidx.annotation.VisibleForTesting;

import com.android.car.settings.common.FragmentController;

/**
 * Factory reset specific version of {@link ResetEsimPreferenceController} that is only available if
 * the system property {@code masterclear.allow_retain_esim_profiles_after_fdr} is also true.
 */
public class MasterClearResetEsimPreferenceController extends ResetEsimPreferenceController {

    @VisibleForTesting
    static final String KEY_SHOW_ESIM_RESET_CHECKBOX =
            "masterclear.allow_retain_esim_profiles_after_fdr";

    public MasterClearResetEsimPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected int getAvailabilityStatus() {
        int status = super.getAvailabilityStatus();
        if (status == AVAILABLE) {
            return SystemProperties.get(KEY_SHOW_ESIM_RESET_CHECKBOX,
                    Boolean.FALSE.toString()).equals(Boolean.TRUE.toString()) ? AVAILABLE
                    : UNSUPPORTED_ON_DEVICE;
        }
        return status;
    }
}
