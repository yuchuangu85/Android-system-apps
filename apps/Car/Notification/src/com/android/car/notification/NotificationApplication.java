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

import android.app.Application;
import android.car.Car;
import android.car.CarNotConnectedException;
import android.car.drivingstate.CarUxRestrictionsManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.ServiceManager;
import android.util.Log;

import com.android.internal.statusbar.IStatusBarService;

/**
 * Application class that makes connections to the car service api so components can share these
 * objects
 */
public class NotificationApplication extends Application {
    private static final String TAG = "NotificationApplication";
    private Car mCar;
    private NotificationClickHandlerFactory mClickHandlerFactory;

    private CarUxRestrictionManagerWrapper mCarUxRestrictionManagerWrapper =
            new CarUxRestrictionManagerWrapper();

    private ServiceConnection mCarConnectionListener = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "onServiceConnected");
            try {
                CarUxRestrictionsManager carUxRestrictionsManager =
                        (CarUxRestrictionsManager)
                                mCar.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE);
                mCarUxRestrictionManagerWrapper
                        .setCarUxRestrictionsManager(carUxRestrictionsManager);
                PreprocessingManager preprocessingManager = PreprocessingManager.getInstance(
                        getApplicationContext());
                preprocessingManager
                        .setCarUxRestrictionManagerWrapper(mCarUxRestrictionManagerWrapper);
            } catch (CarNotConnectedException e) {
                Log.e(TAG, "Car not connected in CarConnectionListener", e);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }
    };

    /**
     * Returns the CarUxRestrictionManagerWrapper used to determine visual treatment of notifications.
     */
    public CarUxRestrictionManagerWrapper getCarUxRestrictionWrapper() {
        return mCarUxRestrictionManagerWrapper;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mCar = Car.createCar(this, mCarConnectionListener);
        mCar.connect();
        mClickHandlerFactory = new NotificationClickHandlerFactory(
                IStatusBarService.Stub.asInterface(
                        ServiceManager.getService(Context.STATUS_BAR_SERVICE)),
                /* callback= */null);
    }

    /**
     * Returns the NotificationClickHandlerFactory used to generate click OnClickListeners
     * for the notifications
     */
    public NotificationClickHandlerFactory getClickHandlerFactory() {
        return mClickHandlerFactory;
    }
}
