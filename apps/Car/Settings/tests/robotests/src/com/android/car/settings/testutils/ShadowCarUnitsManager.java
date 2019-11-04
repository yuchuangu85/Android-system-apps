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

package com.android.car.settings.testutils;

import com.android.car.settings.units.CarUnitsManager;
import com.android.car.settings.units.Unit;
import com.android.car.settings.units.UnitsMap;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Resetter;

import java.util.HashMap;

/**
 * Shadow class for {@link CarUnitsManager}.
 */
@Implements(CarUnitsManager.class)
public class ShadowCarUnitsManager {
    private static boolean sConnected = false;
    private static CarUnitsManager.OnCarServiceListener sListener;
    private static HashMap<Integer, Unit[]> sSupportedUnits = new HashMap<>();
    private static HashMap<Integer, Unit> sUnitsBeingUsed = new HashMap<>();

    @Implementation
    protected void connect() {
        sConnected = true;
    }

    @Implementation
    protected void disconnect() {
        sConnected = false;
    }

    @Implementation
    protected static Unit[] getUnitsSupportedByProperty(int propertyId) {
        return sSupportedUnits.get(propertyId);
    }

    @Implementation
    protected static Unit getUnitUsedByProperty(int propertyId) {
        return sUnitsBeingUsed.get(propertyId);
    }

    @Implementation
    protected static void setUnitUsedByProperty(int propertyId, int unitId) {
        sUnitsBeingUsed.put(propertyId, UnitsMap.MAP.get(unitId));
    }

    @Implementation
    protected static void registerCarServiceListener(
            CarUnitsManager.OnCarServiceListener listener) {
        sListener = listener;
    }

    @Implementation
    protected static void unregisterCarServiceListener() {
        sListener = null;
    }

    @Resetter
    public static void reset() {
        sConnected = false;
        sListener = null;
        sSupportedUnits = new HashMap<>();
        sUnitsBeingUsed = new HashMap<>();
    }

    public static void setUnitsSupportedByProperty(int propertyId, Unit[] units) {
        sSupportedUnits.put(propertyId, units);
    }

    public static boolean isConnected() {
        return sConnected;
    }

    public static CarUnitsManager.OnCarServiceListener getListener() {
        return sListener;
    }
}
