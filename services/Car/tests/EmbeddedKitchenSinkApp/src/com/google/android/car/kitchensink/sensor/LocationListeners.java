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

package com.google.android.car.kitchensink.sensor;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.util.Log;


public class LocationListeners {

    private static final String TAG = "CAR.SENSOR.KS.location";

    SensorsTestFragment.LocationInfoTextUpdateListener mTextUpdateHandler;

    LocationManager mLocationMgr;
    SensorManager   mSensorMgr;

    Sensor mAccelerometerSensor, mMagneticFieldSensor, mGyroscopeSensor;

    public LocationListeners(Context context,
                             SensorsTestFragment.LocationInfoTextUpdateListener listener) {
        mTextUpdateHandler = listener;

        mLocationMgr = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        mSensorMgr = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);

        mAccelerometerSensor = mSensorMgr.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        mMagneticFieldSensor = mSensorMgr.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        mGyroscopeSensor = mSensorMgr.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
    }

    public void startListening() {
        if (mLocationMgr != null) {
            if (mLocationMgr.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                mLocationMgr.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0,
                        mLocationListener);
                mTextUpdateHandler.setLocationField("waiting to hear from GPS");
            } else {
                mTextUpdateHandler.setLocationField("GPS_PROVIDER not available");
            }
        } else {
            mTextUpdateHandler.setLocationField("LocationManager not available");
        }

        if (mSensorMgr != null) {
            // Accelerometer.
            if (mAccelerometerSensor != null) {
                if (!mSensorMgr.registerListener(mSensorListener, mAccelerometerSensor,
                        SensorManager.SENSOR_DELAY_FASTEST)) {
                    mTextUpdateHandler.setAccelField(
                            "Accelerometer: failed to register listener.");
                } else {
                    mTextUpdateHandler.setAccelField(
                            "Accelerometer: waiting to hear from SensorManager");
                }
            } else {
                mTextUpdateHandler.setAccelField("Accelerometer: sensor not available.");
            }

            // Gyroscope.
            if (mGyroscopeSensor != null) {
                if (!mSensorMgr.registerListener(mSensorListener, mGyroscopeSensor,
                        SensorManager.SENSOR_DELAY_FASTEST)) {
                    mTextUpdateHandler.setGyroField(
                            "Gyroscope: failed to register listener.");
                } else {
                    mTextUpdateHandler.setGyroField(
                            "Gyroscope: waiting to hear from SensorManager");
                }
            } else {
                mTextUpdateHandler.setGyroField("Gyroscope: sensor not available.");
            }

            // Magnetometer.
            if (mMagneticFieldSensor != null) {
                if (!mSensorMgr.registerListener(mSensorListener, mMagneticFieldSensor,
                        SensorManager.SENSOR_DELAY_FASTEST)) {
                    mTextUpdateHandler.setMagField(
                            "Magnetometer: failed to register listener.");
                } else {
                    mTextUpdateHandler.setMagField(
                            "Magnetometer: waiting to hear from SensorManager");
                }
            } else {
                mTextUpdateHandler.setMagField("Magnetometer: sensor not available.");
            }
        } else {
            mTextUpdateHandler.setAccelField("SensorManager not available");
            mTextUpdateHandler.setGyroField("SensorManager not available");
            mTextUpdateHandler.setMagField("SensorManager not available");
        }
    }

    public void stopListening() {
        if (mLocationMgr != null) {
            if (mLocationMgr.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                mLocationMgr.removeUpdates(mLocationListener);
                mTextUpdateHandler.setMagField("GPS stopped");
            }
        }

        if (mSensorMgr != null) {
            mSensorMgr.unregisterListener(mSensorListener);
            mTextUpdateHandler.setAccelField("SensorManager stopped");
            mTextUpdateHandler.setGyroField("SensorManager stopped");
            mTextUpdateHandler.setMagField("SensorManager stopped");
        }
    }


    private final LocationListener mLocationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            String s = String.format("Location: lat=%10.6f, lon=%10.6f, altitude=%5.0f, "
                                   + "speed=%5.1f, bearing=%3.0f, accuracy=%5.1f",
                    location.getLatitude(),
                    location.getLongitude(),
                    location.getAltitude(),
                    location.getSpeed(),
                    location.getBearing(),
                    location.getAccuracy());

            mTextUpdateHandler.setLocationField(s);
        }

        @Override
        public void onProviderEnabled(String provider) {
        }

        @Override
        public void onProviderDisabled(String provider) {
        }

        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {
        }
    };

    private final SensorEventListener mSensorListener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent event) {
            int type = event.sensor.getType();
            switch (type) {
                case Sensor.TYPE_GYROSCOPE:
                    String gs = String.format("Gyroscope Rad/s: (%6.2f, %6.2f, %6.2f)",
                            event.values[0], event.values[1], event.values[2]);
                    mTextUpdateHandler.setGyroField(gs);
                    break;
                case Sensor.TYPE_MAGNETIC_FIELD:
                    // NOTE:  If we wanted to report yaw/pitch/roll, we would use both
                    //        accelerometer and magnetic data to compute R and I:
                    // SensorManager.getRotationMatrix(R, I,
                    //                                 mLastAccelerometerData
                    //                                 mLastMagneticFieldData);
                    // SensorManager.getOrientation(mR, orientation);
                    String ms = String.format("Magnetic uT: (%6.2f, %6.2f, %6.2f)",
                            event.values[0], event.values[1], event.values[2]);
                    mTextUpdateHandler.setMagField(ms);
                    break;
                case Sensor.TYPE_ACCELEROMETER:
                    String as = String.format("Accelerometer m/s2: (%6.2f, %6.2f, %6.2f)",
                            event.values[0], event.values[1], event.values[2]);
                    mTextUpdateHandler.setAccelField(as);
                    break;
                default:
                    Log.w(TAG, "Unexpected sensor event type: " + type);
                    // Should never happen.
                    return;
            }
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int accuracy) {
        }
    };
}
