/*
 * Copyright (C) 2017 The Android Open Source Project
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

package android.car.cluster;

import android.annotation.SystemApi;
import android.car.CarManagerBase;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;

/**
 * API to work with instrument cluster.
 *
 * @deprecated use {@link android.car.CarAppFocusManager} with focus type
 * {@link android.car.CarAppFocusManager#APP_FOCUS_TYPE_NAVIGATION} instead.
 * InstrumentClusterService will automatically launch a "android.car.cluster.NAVIGATION" activity
 * from the package holding navigation focus.
 *
 * @hide
 */
@Deprecated
@SystemApi
public class CarInstrumentClusterManager implements CarManagerBase {
    /**
     * @deprecated use {@link android.car.Car#CATEGORY_NAVIGATION} instead
     *
     * @hide
     */
    @SystemApi
    public static final String CATEGORY_NAVIGATION = "android.car.cluster.NAVIGATION";

    /**
     * When activity in the cluster is launched it will receive {@link ClusterActivityState} in the
     * intent's extra thus activity will know information about unobscured area, etc. upon activity
     * creation.
     *
     * @deprecated use {@link android.car.Car#CATEGORY_NAVIGATION} instead
     *
     * @hide
     */
    @SystemApi
    public static final String KEY_EXTRA_ACTIVITY_STATE =
            "android.car.cluster.ClusterActivityState";

    /**
     * Starts activity in the instrument cluster.
     *
     * @deprecated see {@link CarInstrumentClusterManager} deprecation message
     *
     * @hide
     */
    @SystemApi
    public void startActivity(Intent intent) {
        // No-op
    }

    /**
     * Caller of this method will receive immediate callback with the most recent state if state
     * exists for given category.
     *
     * @param category category of the activity in the cluster,
     *                         see {@link #CATEGORY_NAVIGATION}
     * @param callback instance of {@link Callback} class to receive events.
     *
     * @deprecated see {@link CarInstrumentClusterManager} deprecation message
     *
     * @hide
     */
    @SystemApi
    public void registerCallback(String category, Callback callback) {
        // No-op
    }

    /**
     * Unregisters given callback for all activity categories.
     *
     * @param callback previously registered callback
     *
     * @deprecated see {@link CarInstrumentClusterManager} deprecation message
     *
     * @hide
     */
    @SystemApi
    public void unregisterCallback(Callback callback) {
        // No-op
    }

    /** @hide */
    public CarInstrumentClusterManager(IBinder service, Handler handler) {
        // No-op
    }

    /**
     * @deprecated activity state is not longer being reported. See
     * {@link CarInstrumentClusterManager} deprecation message for more details.
     *
     * @hide
     */
    @Deprecated
    @SystemApi
    public interface Callback {
        /**
         * Notify client that activity state was changed.
         *
         * @param category cluster activity category, see {@link #CATEGORY_NAVIGATION}
         * @param clusterActivityState see {@link ClusterActivityState} how to read this bundle.
         */
        void onClusterActivityStateChanged(String category, Bundle clusterActivityState);
    }

    /** @hide */
    @Override
    public void onCarDisconnected() {
    }
}
