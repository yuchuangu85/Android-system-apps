/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.car.apps.common;

import static android.car.drivingstate.CarUxRestrictions.UX_RESTRICTIONS_LIMIT_STRING_LENGTH;

import android.car.Car;
import android.car.CarNotConnectedException;
import android.car.drivingstate.CarUxRestrictions;
import android.car.drivingstate.CarUxRestrictions.CarUxRestrictionsInfo;
import android.car.drivingstate.CarUxRestrictionsManager;
import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;

/**
 * Utility class to access Car Restriction Manager.
 *
 * This class must be a singleton because only one listener can be registered with
 * {@link CarUxRestrictionsManager} at a time, as documented in
 * {@link CarUxRestrictionsManager#registerListener}.
 */
public class CarUxRestrictionsUtil {
    private static final String TAG = "CarUxRestrictionsUtil";

    private final Car mCarApi;
    private CarUxRestrictionsManager mCarUxRestrictionsManager;
    @NonNull
    private CarUxRestrictions mCarUxRestrictions = getDefaultRestrictions();

    private Set<OnUxRestrictionsChangedListener> mObservers;
    private static CarUxRestrictionsUtil sInstance = null;

    private CarUxRestrictionsUtil(Context context) {
        CarUxRestrictionsManager.OnUxRestrictionsChangedListener listener = (carUxRestrictions) -> {
            if (carUxRestrictions == null) {
                mCarUxRestrictions = getDefaultRestrictions();
            } else {
                mCarUxRestrictions = carUxRestrictions;
            }

            for (OnUxRestrictionsChangedListener observer : mObservers) {
                observer.onRestrictionsChanged(mCarUxRestrictions);
            }
        };

        mCarApi = Car.createCar(context);
        mObservers = Collections.newSetFromMap(new WeakHashMap<>());

        try {
            mCarUxRestrictionsManager = (CarUxRestrictionsManager) mCarApi
                    .getCarManager(Car.CAR_UX_RESTRICTION_SERVICE);
            mCarUxRestrictionsManager.registerListener(listener);
            listener.onUxRestrictionsChanged(
                    mCarUxRestrictionsManager.getCurrentCarUxRestrictions());
        } catch (CarNotConnectedException e) {
            Log.e(TAG, "Car not connected", e);
            // mCarUxRestrictions will be the default
        } catch (NullPointerException e) {
            Log.e(TAG, "Car not connected", e);
            // mCarUxRestrictions will be the default
        }
    }

    @NonNull
    private CarUxRestrictions getDefaultRestrictions() {
        return new CarUxRestrictions.Builder(true,
                CarUxRestrictions.UX_RESTRICTIONS_FULLY_RESTRICTED, 0).build();
    }

    /**
     * Listener interface used to update clients on UxRestrictions changes
     */
    public interface OnUxRestrictionsChangedListener {
        /**
         * Called when CarUxRestrictions changes
         */
        void onRestrictionsChanged(@NonNull CarUxRestrictions carUxRestrictions);
    }

    /**
     * Returns the singleton instance of this class
     */
    @NonNull
    public static CarUxRestrictionsUtil getInstance(Context context) {
        if (sInstance == null) {
            sInstance = new CarUxRestrictionsUtil(context);
        }

        return sInstance;
    }

    /**
     * Registers a listener on this class for updates to CarUxRestrictions.
     * Multiple listeners may be registered.
     */
    public void register(OnUxRestrictionsChangedListener listener) {
        mObservers.add(listener);
        listener.onRestrictionsChanged(mCarUxRestrictions);
    }

    /**
     * Unregisters a registered listener
     */
    public void unregister(OnUxRestrictionsChangedListener listener) {
        mObservers.remove(listener);
    }

    /**
     * Returns whether any of the given flags is blocked by the current restrictions. If null is
     * given, the method returns true for safety.
     */
    public static boolean isRestricted(@CarUxRestrictionsInfo int restrictionFlags,
            @Nullable CarUxRestrictions uxr) {
        return (uxr == null) || ((uxr.getActiveRestrictions() & restrictionFlags) != 0);
    }

    /**
     * Complies the input string with the given UX restrictions.
     * Returns the original string if already compliant, otherwise a shortened ellipsized string.
     */
    public static String complyString(Context context, String str, CarUxRestrictions uxr) {

        if (isRestricted(UX_RESTRICTIONS_LIMIT_STRING_LENGTH, uxr)) {
            int maxLength = uxr == null
                    ? context.getResources().getInteger(R.integer.default_max_string_length)
                    : uxr.getMaxRestrictedStringLength();

            if (str.length() > maxLength) {
                return str.substring(0, maxLength) + context.getString(R.string.ellipsis);
            }
        }

        return str;
    }
}
