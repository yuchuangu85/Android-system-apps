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

package com.android.car.settings.testutils;

import android.content.Intent;
import android.location.LocationManager;
import android.os.UserHandle;
import android.provider.Settings;

import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

@Implements(value = LocationManager.class)
public class ShadowLocationManager {

    @Implementation
    protected void setLocationEnabledForUser(boolean enabled, UserHandle userHandle) {
        int newMode = enabled
                ? Settings.Secure.LOCATION_MODE_HIGH_ACCURACY
                : Settings.Secure.LOCATION_MODE_OFF;

        Settings.Secure.putIntForUser(RuntimeEnvironment.application.getContentResolver(),
                Settings.Secure.LOCATION_MODE, newMode, userHandle.getIdentifier());
        RuntimeEnvironment.application.sendBroadcast(new Intent(
                LocationManager.MODE_CHANGED_ACTION));
    }

    @Implementation
    protected boolean isLocationEnabled() {
        return Settings.Secure.getInt(RuntimeEnvironment.application.getContentResolver(),
                Settings.Secure.LOCATION_MODE, Settings.Secure.LOCATION_MODE_OFF)
                != Settings.Secure.LOCATION_MODE_OFF;
    }
}
