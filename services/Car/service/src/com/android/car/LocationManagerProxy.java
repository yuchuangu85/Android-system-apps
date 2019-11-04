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

package com.android.car;

import android.annotation.NonNull;
import android.car.ILocationManagerProxy;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

/** Wraps a {@link LocationManager}. */
public class LocationManagerProxy extends ILocationManagerProxy.Stub {
    private static final String TAG = "LocationManagerProxy";
    private static final boolean DBG = Log.isLoggable(TAG, Log.DEBUG);

    private final LocationManager mLocationManager;

    /**
     * Create a LocationManagerProxy instance given a {@link Context}.
     */
    public LocationManagerProxy(Context context) {
        if (DBG) {
            Log.d(TAG, "constructed.");
        }
        mLocationManager = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
    }

    @Override
    public boolean isLocationEnabled() {
        return mLocationManager.isLocationEnabled();
    }

    @Override
    public boolean injectLocation(Location location) {
        return mLocationManager.injectLocation(location);
    }

    @Override
    public Location getLastKnownLocation(@NonNull String provider) {
        if (DBG) {
            Log.d(TAG, "Getting last known location for provider " + provider);
        }
        return mLocationManager.getLastKnownLocation(provider);
    }
}
