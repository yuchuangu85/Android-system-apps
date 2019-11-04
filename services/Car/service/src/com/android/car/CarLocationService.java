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

package com.android.car;

import android.app.ActivityManager;
import android.car.ICarUserService;
import android.car.ILocationManagerProxy;
import android.car.drivingstate.CarDrivingStateEvent;
import android.car.drivingstate.ICarDrivingStateChangeListener;
import android.car.hardware.power.CarPowerManager;
import android.car.hardware.power.CarPowerManager.CarPowerStateListener;
import android.car.hardware.power.CarPowerManager.CarPowerStateListenerWithCompletion;
import android.car.userlib.CarUserManagerHelper;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.location.Location;
import android.location.LocationManager;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.UserHandle;
import android.util.AtomicFile;
import android.util.JsonReader;
import android.util.JsonWriter;
import android.util.Log;

import com.android.car.systeminterface.SystemInterface;
import com.android.internal.annotations.VisibleForTesting;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.concurrent.CompletableFuture;

/**
 * This service stores the last known location from {@link LocationManager} when a car is parked
 * and restores the location when the car is powered on.
 */
public class CarLocationService extends BroadcastReceiver implements CarServiceBase,
        CarPowerStateListenerWithCompletion {
    private static final String TAG = "CarLocationService";
    private static final String FILENAME = "location_cache.json";
    private static final boolean DBG = true;
    // The accuracy for the stored timestamp
    private static final long GRANULARITY_ONE_DAY_MS = 24 * 60 * 60 * 1000L;
    // The time-to-live for the cached location
    private static final long TTL_THIRTY_DAYS_MS = 30 * GRANULARITY_ONE_DAY_MS;
    // The maximum number of times to try injecting a location
    private static final int MAX_LOCATION_INJECTION_ATTEMPTS = 10;

    // Used internally for mHandlerThread synchronization
    private final Object mLock = new Object();

    // Used internally for mILocationManagerProxy synchronization
    private final Object mLocationManagerProxyLock = new Object();

    private final Context mContext;
    private final CarUserManagerHelper mCarUserManagerHelper;
    private int mTaskCount = 0;
    private HandlerThread mHandlerThread;
    private Handler mHandler;
    private CarPowerManager mCarPowerManager;
    private CarDrivingStateService mCarDrivingStateService;
    private PerUserCarServiceHelper mPerUserCarServiceHelper;

    // Allows us to interact with the {@link LocationManager} as the foreground user.
    private ILocationManagerProxy mILocationManagerProxy;

    // Maintains mILocationManagerProxy for the current foreground user.
    private final PerUserCarServiceHelper.ServiceCallback mUserServiceCallback =
            new PerUserCarServiceHelper.ServiceCallback() {
                @Override
                public void onServiceConnected(ICarUserService carUserService) {
                    logd("Connected to PerUserCarService");
                    if (carUserService == null) {
                        logd("ICarUserService is null. Cannot get location manager proxy");
                        return;
                    }
                    synchronized (mLocationManagerProxyLock) {
                        try {
                            mILocationManagerProxy = carUserService.getLocationManagerProxy();
                        } catch (RemoteException e) {
                            Log.e(TAG, "RemoteException from ICarUserService", e);
                            return;
                        }
                    }
                    int currentUser = ActivityManager.getCurrentUser();
                    logd("Current user: " + currentUser);
                    if (mCarUserManagerHelper.isHeadlessSystemUser()
                            && currentUser > UserHandle.USER_SYSTEM) {
                        asyncOperation(() -> loadLocation());
                    }
                }

                @Override
                public void onPreUnbind() {
                    logd("Before Unbinding from PerCarUserService");
                    synchronized (mLocationManagerProxyLock) {
                        mILocationManagerProxy = null;
                    }
                }

                @Override
                public void onServiceDisconnected() {
                    logd("Disconnected from PerUserCarService");
                    synchronized (mLocationManagerProxyLock) {
                        mILocationManagerProxy = null;
                    }
                }
            };

    private final ICarDrivingStateChangeListener mICarDrivingStateChangeEventListener =
            new ICarDrivingStateChangeListener.Stub() {
                @Override
                public void onDrivingStateChanged(CarDrivingStateEvent event) {
                    logd("onDrivingStateChanged " + event);
                    if (event != null
                            && event.eventValue == CarDrivingStateEvent.DRIVING_STATE_MOVING) {
                        deleteCacheFile();
                        if (mCarDrivingStateService != null) {
                            mCarDrivingStateService.unregisterDrivingStateChangeListener(
                                    mICarDrivingStateChangeEventListener);
                        }
                    }
                }
            };

    public CarLocationService(Context context, CarUserManagerHelper carUserManagerHelper) {
        logd("constructed");
        mContext = context;
        mCarUserManagerHelper = carUserManagerHelper;
    }

    @Override
    public void init() {
        logd("init");
        IntentFilter filter = new IntentFilter();
        filter.addAction(LocationManager.MODE_CHANGED_ACTION);
        mContext.registerReceiver(this, filter);
        mCarDrivingStateService = CarLocalServices.getService(CarDrivingStateService.class);
        if (mCarDrivingStateService != null) {
            CarDrivingStateEvent event = mCarDrivingStateService.getCurrentDrivingState();
            if (event != null && event.eventValue == CarDrivingStateEvent.DRIVING_STATE_MOVING) {
                deleteCacheFile();
            } else {
                mCarDrivingStateService.registerDrivingStateChangeListener(
                        mICarDrivingStateChangeEventListener);
            }
        }
        mCarPowerManager = CarLocalServices.createCarPowerManager(mContext);
        if (mCarPowerManager != null) { // null case happens for testing.
            mCarPowerManager.setListenerWithCompletion(CarLocationService.this);
        }
        mPerUserCarServiceHelper = CarLocalServices.getService(PerUserCarServiceHelper.class);
        if (mPerUserCarServiceHelper != null) {
            mPerUserCarServiceHelper.registerServiceCallback(mUserServiceCallback);
        }
    }

    @Override
    public void release() {
        logd("release");
        if (mCarPowerManager != null) {
            mCarPowerManager.clearListener();
        }
        if (mCarDrivingStateService != null) {
            mCarDrivingStateService.unregisterDrivingStateChangeListener(
                    mICarDrivingStateChangeEventListener);
        }
        if (mPerUserCarServiceHelper != null) {
            mPerUserCarServiceHelper.unregisterServiceCallback(mUserServiceCallback);
        }
        mContext.unregisterReceiver(this);
    }

    @Override
    public void dump(PrintWriter writer) {
        writer.println(TAG);
        writer.println("Context: " + mContext);
        writer.println("MAX_LOCATION_INJECTION_ATTEMPTS: " + MAX_LOCATION_INJECTION_ATTEMPTS);
    }

    @Override
    public void onStateChanged(int state, CompletableFuture<Void> future) {
        logd("onStateChanged: " + state);
        switch (state) {
            case CarPowerStateListener.SHUTDOWN_PREPARE:
                asyncOperation(() -> {
                    storeLocation();
                    // Notify the CarPowerManager that it may proceed to shutdown or suspend.
                    if (future != null) {
                        future.complete(null);
                    }
                });
                break;
            case CarPowerStateListener.SUSPEND_EXIT:
                deleteCacheFile();
                if (future != null) {
                    future.complete(null);
                }
            default:
                // This service does not need to do any work for these events but should still
                // notify the CarPowerManager that it may proceed.
                if (future != null) {
                    future.complete(null);
                }
                break;
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        logd("onReceive " + intent);
        // If the system user is headless but the current user is still the system user, then we
        // should not delete the location cache file due to missing location permissions.
        if (isCurrentUserHeadlessSystemUser()) {
            logd("Current user is headless system user.");
            return;
        }
        synchronized (mLocationManagerProxyLock) {
            if (mILocationManagerProxy == null) {
                logd("Null location manager.");
                return;
            }
            String action = intent.getAction();
            try {
                if (action == LocationManager.MODE_CHANGED_ACTION) {
                    boolean locationEnabled = mILocationManagerProxy.isLocationEnabled();
                    logd("isLocationEnabled(): " + locationEnabled);
                    if (!locationEnabled) {
                        deleteCacheFile();
                    }
                } else {
                    logd("Unexpected intent.");
                }
            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException from ILocationManagerProxy", e);
            }
        }
    }

    /** Tells whether the current foreground user is the headless system user. */
    private boolean isCurrentUserHeadlessSystemUser() {
        int currentUserId = ActivityManager.getCurrentUser();
        return mCarUserManagerHelper.isHeadlessSystemUser() && currentUserId == 0;
    }

    /**
     * Gets the last known location from the location manager proxy and store it in a file.
     */
    private void storeLocation() {
        Location location = null;
        synchronized (mLocationManagerProxyLock) {
            if (mILocationManagerProxy == null) {
                logd("Null location manager proxy.");
                return;
            }
            try {
                location = mILocationManagerProxy.getLastKnownLocation(
                        LocationManager.GPS_PROVIDER);
            } catch (RemoteException e) {
                Log.e(TAG, "RemoteException from ILocationManagerProxy", e);
            }
        }
        if (location == null) {
            logd("Not storing null location");
        } else {
            logd("Storing location: " + location);
            AtomicFile atomicFile = new AtomicFile(getLocationCacheFile());
            FileOutputStream fos = null;
            try {
                fos = atomicFile.startWrite();
                try (JsonWriter jsonWriter = new JsonWriter(new OutputStreamWriter(fos, "UTF-8"))) {
                    jsonWriter.beginObject();
                    jsonWriter.name("provider").value(location.getProvider());
                    jsonWriter.name("latitude").value(location.getLatitude());
                    jsonWriter.name("longitude").value(location.getLongitude());
                    if (location.hasAltitude()) {
                        jsonWriter.name("altitude").value(location.getAltitude());
                    }
                    if (location.hasSpeed()) {
                        jsonWriter.name("speed").value(location.getSpeed());
                    }
                    if (location.hasBearing()) {
                        jsonWriter.name("bearing").value(location.getBearing());
                    }
                    if (location.hasAccuracy()) {
                        jsonWriter.name("accuracy").value(location.getAccuracy());
                    }
                    if (location.hasVerticalAccuracy()) {
                        jsonWriter.name("verticalAccuracy").value(
                                location.getVerticalAccuracyMeters());
                    }
                    if (location.hasSpeedAccuracy()) {
                        jsonWriter.name("speedAccuracy").value(
                                location.getSpeedAccuracyMetersPerSecond());
                    }
                    if (location.hasBearingAccuracy()) {
                        jsonWriter.name("bearingAccuracy").value(
                                location.getBearingAccuracyDegrees());
                    }
                    if (location.isFromMockProvider()) {
                        jsonWriter.name("isFromMockProvider").value(true);
                    }
                    long currentTime = location.getTime();
                    // Round the time down to only be accurate within one day.
                    jsonWriter.name("captureTime").value(
                            currentTime - currentTime % GRANULARITY_ONE_DAY_MS);
                    jsonWriter.endObject();
                }
                atomicFile.finishWrite(fos);
            } catch (IOException e) {
                Log.e(TAG, "Unable to write to disk", e);
                atomicFile.failWrite(fos);
            }
        }
    }

    /**
     * Reads a previously stored location and attempts to inject it into the location manager proxy.
     */
    private void loadLocation() {
        Location location = readLocationFromCacheFile();
        logd("Read location from timestamp " + location.getTime());
        long currentTime = System.currentTimeMillis();
        if (location.getTime() + TTL_THIRTY_DAYS_MS < currentTime) {
            logd("Location expired.");
            deleteCacheFile();
        } else {
            location.setTime(currentTime);
            long elapsedTime = SystemClock.elapsedRealtimeNanos();
            location.setElapsedRealtimeNanos(elapsedTime);
            if (location.isComplete()) {
                injectLocation(location, 1);
            }
        }
    }

    private Location readLocationFromCacheFile() {
        Location location = new Location((String) null);
        AtomicFile atomicFile = new AtomicFile(getLocationCacheFile());
        try (FileInputStream fis = atomicFile.openRead()) {
            JsonReader reader = new JsonReader(new InputStreamReader(fis, "UTF-8"));
            reader.beginObject();
            while (reader.hasNext()) {
                String name = reader.nextName();
                if (name.equals("provider")) {
                    location.setProvider(reader.nextString());
                } else if (name.equals("latitude")) {
                    location.setLatitude(reader.nextDouble());
                } else if (name.equals("longitude")) {
                    location.setLongitude(reader.nextDouble());
                } else if (name.equals("altitude")) {
                    location.setAltitude(reader.nextDouble());
                } else if (name.equals("speed")) {
                    location.setSpeed((float) reader.nextDouble());
                } else if (name.equals("bearing")) {
                    location.setBearing((float) reader.nextDouble());
                } else if (name.equals("accuracy")) {
                    location.setAccuracy((float) reader.nextDouble());
                } else if (name.equals("verticalAccuracy")) {
                    location.setVerticalAccuracyMeters((float) reader.nextDouble());
                } else if (name.equals("speedAccuracy")) {
                    location.setSpeedAccuracyMetersPerSecond((float) reader.nextDouble());
                } else if (name.equals("bearingAccuracy")) {
                    location.setBearingAccuracyDegrees((float) reader.nextDouble());
                } else if (name.equals("isFromMockProvider")) {
                    location.setIsFromMockProvider(reader.nextBoolean());
                } else if (name.equals("captureTime")) {
                    location.setTime(reader.nextLong());
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
        } catch (FileNotFoundException e) {
            Log.d(TAG, "Location cache file not found.");
        } catch (IOException e) {
            Log.e(TAG, "Unable to read from disk", e);
        } catch (NumberFormatException | IllegalStateException e) {
            Log.e(TAG, "Unexpected format", e);
        }
        return location;
    }

    private void deleteCacheFile() {
        boolean deleted = getLocationCacheFile().delete();
        logd("Deleted cache file: " + deleted);
    }

    /**
     * Attempts to inject the location multiple times in case the LocationManager was not fully
     * initialized or has not updated its handle to the current user yet.
     */
    private void injectLocation(Location location, int attemptCount) {
        boolean success = false;
        synchronized (mLocationManagerProxyLock) {
            if (mILocationManagerProxy == null) {
                logd("Null location manager proxy.");
            } else {
                try {
                    success = mILocationManagerProxy.injectLocation(location);
                } catch (RemoteException e) {
                    Log.e(TAG, "RemoteException from ILocationManagerProxy", e);
                }
            }
        }
        logd("Injected location " + location + " with result " + success + " on attempt "
                + attemptCount);
        if (success) {
            return;
        } else if (attemptCount <= MAX_LOCATION_INJECTION_ATTEMPTS) {
            asyncOperation(() -> {
                injectLocation(location, attemptCount + 1);
            }, 200 * attemptCount);
        } else {
            logd("No location injected.");
        }
    }

    private File getLocationCacheFile() {
        SystemInterface systemInterface = CarLocalServices.getService(SystemInterface.class);
        File file = new File(systemInterface.getSystemCarDir(), FILENAME);
        logd("File: " + file);
        return file;
    }

    @VisibleForTesting
    void asyncOperation(Runnable operation) {
        asyncOperation(operation, 0);
    }

    private void asyncOperation(Runnable operation, long delayMillis) {
        synchronized (mLock) {
            // Create a new HandlerThread if this is the first task to queue.
            if (++mTaskCount == 1) {
                mHandlerThread = new HandlerThread("CarLocationServiceThread");
                mHandlerThread.start();
                mHandler = new Handler(mHandlerThread.getLooper());
            }
        }
        mHandler.postDelayed(() -> {
            try {
                operation.run();
            } finally {
                synchronized (mLock) {
                    // Quit the thread when the task queue is empty.
                    if (--mTaskCount == 0) {
                        mHandler.getLooper().quit();
                        mHandler = null;
                        mHandlerThread = null;
                    }
                }
            }
        }, delayMillis);
    }

    private static void logd(String msg) {
        if (DBG) {
            Log.d(TAG, msg);
        }
    }
}
