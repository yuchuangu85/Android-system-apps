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
 * limitations under the License
 */

package com.android.car.notification;

import android.car.CarNotConnectedException;
import android.car.drivingstate.CarUxRestrictions;
import android.car.drivingstate.CarUxRestrictionsManager;
import android.car.drivingstate.CarUxRestrictionsManager.OnUxRestrictionsChangedListener;
import android.util.Log;

/**
 * Used as a proxy and delegator of Ux Restriction state changes to the
 * {@link CarUxRestrictionsManager} due to the fact that {@link CarUxRestrictionsManager} can only
 * have one registered listener.
 * <p>
 * This was written not to be a general solution as that responsibility should be in an future api
 * change of the {@link CarUxRestrictionsManager}.
 * This class uses setter inject due to the current nature of the asynchronous depenancy creation
 * when using the Car api
 */
public class CarUxRestrictionManagerWrapper implements OnUxRestrictionsChangedListener {
    private static final String TAG = "CarUxRestrictionManager";

    private CarNotificationView mCarNotificationView;
    private CarHeadsUpNotificationManager mCarHeadsUpNotificationManager;
    private CarUxRestrictionsManager mCarUxRestrictionsManager;

    /**
     * Forwards state change to {@link CarHeadsUpNotificationManager} and
     * {@link CarNotificationView} if they've been set.
     *
     * @param restrictionInfo The latest restiction state
     */
    @Override
    public void onUxRestrictionsChanged(CarUxRestrictions restrictionInfo) {
        // This is done in this manner (and not a list) to give the next person pause
        // and check if the implementation of CarUxRestrictionsManager has been updated to handle
        // multiple listeners and delete this class if so.
        if (mCarHeadsUpNotificationManager != null) {
            mCarHeadsUpNotificationManager.onUxRestrictionsChanged(restrictionInfo);
        }
        if (mCarNotificationView != null) {
            mCarNotificationView.onUxRestrictionsChanged(restrictionInfo);
        }
    }

    /**
     * Set the {@link CarNotificationView} that should be notified on ux restriction state changes
     *
     * @param carNotificationView {@code null} to turn off notifications
     */
    public void setCarNotificationView(CarNotificationView carNotificationView) {
        mCarNotificationView = carNotificationView;
    }

    /**
     * set the {@link CarHeadsUpNotificationManager} that should be notified on ux restriction
     * state changes
     *
     * @param carHeadsUpNotificationManager {@code null} to turn off notifications
     */
    public void setCarHeadsUpNotificationManager(
            CarHeadsUpNotificationManager carHeadsUpNotificationManager) {
        mCarHeadsUpNotificationManager = carHeadsUpNotificationManager;
    }

    /**
     * Set the {@link CarUxRestrictionsManager} that will be used as the source of data. The
     * setter self registers as a listener as it's expected to be called from a connection listener
     * when a connection to the car api is established.
     *
     * @param carUxRestrictionsManager The CarUxRestrictionsManager to proxy to
     */
    public void setCarUxRestrictionsManager(
            CarUxRestrictionsManager carUxRestrictionsManager) {
        mCarUxRestrictionsManager = carUxRestrictionsManager;
        try {
            mCarUxRestrictionsManager.registerListener(this);
        } catch (CarNotConnectedException e) {
            Log.w(TAG, "Failed to register for ux restiction changes ",e);
        }
    }

    /**
     * Proxy to the same call on CarUxRestrictionsManager
     *
     * @return CarUxRestrictions The current restictions
     * @throws CarNotConnectedException Thrown if the Car service is unavailable
     */
    public CarUxRestrictions getCurrentCarUxRestrictions() throws CarNotConnectedException {
        if (mCarUxRestrictionsManager == null) {
            throw new CarNotConnectedException();
        }
        return mCarUxRestrictionsManager.getCurrentCarUxRestrictions();
    }
}
