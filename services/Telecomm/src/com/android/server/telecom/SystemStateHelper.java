/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.server.telecom;

import android.app.UiModeManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.telecom.Log;

import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Provides various system states to the rest of the telecom codebase.
 */
public class SystemStateHelper {
    public static interface SystemStateListener {
        public void onCarModeChanged(boolean isCarMode);
    }

    private final Context mContext;
    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            Log.startSession("SSP.oR");
            try {
                String action = intent.getAction();
                if (UiModeManager.ACTION_ENTER_CAR_MODE.equals(action)) {
                    onEnterCarMode();
                } else if (UiModeManager.ACTION_EXIT_CAR_MODE.equals(action)) {
                    onExitCarMode();
                } else {
                    Log.w(this, "Unexpected intent received: %s", intent.getAction());
                }
            } finally {
                Log.endSession();
            }
        }
    };

    private Set<SystemStateListener> mListeners = new CopyOnWriteArraySet<>();
    private boolean mIsCarMode;

    public SystemStateHelper(Context context) {
        mContext = context;

        IntentFilter intentFilter = new IntentFilter(UiModeManager.ACTION_ENTER_CAR_MODE);
        intentFilter.addAction(UiModeManager.ACTION_EXIT_CAR_MODE);
        mContext.registerReceiver(mBroadcastReceiver, intentFilter);
        Log.i(this, "Registering car mode receiver: %s", intentFilter);

        mIsCarMode = getSystemCarMode();
    }

    public void addListener(SystemStateListener listener) {
        if (listener != null) {
            mListeners.add(listener);
        }
    }

    public boolean removeListener(SystemStateListener listener) {
        return mListeners.remove(listener);
    }

    public boolean isCarMode() {
        return mIsCarMode;
    }

    public boolean isDeviceAtEar() {
        return isDeviceAtEar(mContext);
    }

    /**
     * Returns a guess whether the phone is up to the user's ear. Use the proximity sensor and
     * the gravity sensor to make a guess
     * @return true if the proximity sensor is activated, the magnitude of gravity in directions
     *         parallel to the screen is greater than some configurable threshold, and the
     *         y-component of gravity isn't less than some other configurable threshold.
     */
    public static boolean isDeviceAtEar(Context context) {
        SensorManager sm = context.getSystemService(SensorManager.class);
        if (sm == null) {
            return false;
        }
        Sensor grav = sm.getDefaultSensor(Sensor.TYPE_GRAVITY);
        Sensor proximity = sm.getDefaultSensor(Sensor.TYPE_PROXIMITY);
        if (grav == null || proximity == null) {
            return false;
        }

        AtomicBoolean result = new AtomicBoolean(true);
        CountDownLatch gravLatch = new CountDownLatch(1);
        CountDownLatch proxLatch = new CountDownLatch(1);

        final double xyGravityThreshold = context.getResources().getFloat(
                R.dimen.device_on_ear_xy_gravity_threshold);
        final double yGravityNegativeThreshold = context.getResources().getFloat(
                R.dimen.device_on_ear_y_gravity_negative_threshold);

        SensorEventListener listener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                if (event.sensor.getType() == Sensor.TYPE_GRAVITY) {
                    if (gravLatch.getCount() == 0) {
                        return;
                    }
                    double xyMag = Math.sqrt(event.values[0] * event.values[0]
                            + event.values[1] * event.values[1]);
                    if (xyMag < xyGravityThreshold
                            || event.values[1] < yGravityNegativeThreshold) {
                        result.set(false);
                    }
                    gravLatch.countDown();
                } else if (event.sensor.getType() == Sensor.TYPE_PROXIMITY) {
                    if (proxLatch.getCount() == 0) {
                        return;
                    }
                    if (event.values[0] >= proximity.getMaximumRange()) {
                        result.set(false);
                    }
                    proxLatch.countDown();
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
            }
        };

        try {
            sm.registerListener(listener, grav, SensorManager.SENSOR_DELAY_FASTEST);
            sm.registerListener(listener, proximity, SensorManager.SENSOR_DELAY_FASTEST);
            boolean accelValid = gravLatch.await(100, TimeUnit.MILLISECONDS);
            boolean proxValid = proxLatch.await(100, TimeUnit.MILLISECONDS);
            if (accelValid && proxValid) {
                return result.get();
            } else {
                Log.w(SystemStateHelper.class.getSimpleName(),
                        "Timed out waiting for sensors: %b %b", accelValid, proxValid);
                return false;
            }
        } catch (InterruptedException e) {
            return false;
        } finally {
            sm.unregisterListener(listener);
        }
    }

    private void onEnterCarMode() {
        if (!mIsCarMode) {
            Log.i(this, "Entering carmode");
            mIsCarMode = true;
            notifyCarMode();
        }
    }

    private void onExitCarMode() {
        if (mIsCarMode) {
            Log.i(this, "Exiting carmode");
            mIsCarMode = false;
            notifyCarMode();
        }
    }

    private void notifyCarMode() {
        for (SystemStateListener listener : mListeners) {
            listener.onCarModeChanged(mIsCarMode);
        }
    }

    /**
     * Checks the system for the current car mode.
     *
     * @return True if in car mode, false otherwise.
     */
    private boolean getSystemCarMode() {
        UiModeManager uiModeManager =
                (UiModeManager) mContext.getSystemService(Context.UI_MODE_SERVICE);

        if (uiModeManager != null) {
            return uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_CAR;
        }

        return false;
    }
}
