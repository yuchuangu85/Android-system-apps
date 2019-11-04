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
package android.car.test;

import android.annotation.TestApi;
import android.content.Context;
import android.location.Location;
import android.location.LocationManager;

/**
 * Provides a helper method for use in ATS tests which calls {@link LocationManager#injectLocation}
 * without reflection.
 *
 * @hide
 */
public class CarLocationTestHelper {

    /**
     * Calls the {@link LocationManager#injectLocation} API without reflection.
     *
     * @hide
     */
    @TestApi
    public static boolean injectLocation(Location location, Context context) {
        LocationManager locationManager =
                (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        return locationManager.injectLocation(location);
    }
}
