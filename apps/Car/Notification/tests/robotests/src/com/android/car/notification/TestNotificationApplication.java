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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

import android.car.Car;
import android.car.CarNotConnectedException;
import android.car.drivingstate.CarUxRestrictionsManager;

import com.android.car.notification.testutils.ShadowCar;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.TestLifecycleApplication;

import java.lang.reflect.Method;

/**
 * Robolectric by default loads the application mentioned in the AndroidManifest.xml <application
 * android:name=".NotificationApplication"/> tag. If there is any other class named
 * Test<name-of-application> then robolectric will search for that class and load your custom
 * application. After the application is loaded robolectric will load all the other components of
 * the application.
 *
 * Therefore, all the initialization that we want to be affective on all the components should go
 * here. First the constructor will be called and the the on create of {@link
 * NotificationApplication} will be called.
 *
 * Also, Even before calling any test for component beforeTest() of this class will be executed.
 */
public class TestNotificationApplication extends NotificationApplication implements
        TestLifecycleApplication {

    @Mock
    CarUxRestrictionsManager carUxRestrictionsManager;

    TestNotificationApplication() {
        MockitoAnnotations.initMocks(this);
        try {
            doNothing().when(carUxRestrictionsManager).registerListener(any());
        } catch (CarNotConnectedException e) {
            // do nothing, have to do this because compiler doesn't understand mock can't throw
            // exception.
        }
        ShadowCar.setCarManager(Car.CAR_UX_RESTRICTION_SERVICE, carUxRestrictionsManager);
    }


    @Override
    public void beforeTest(Method method) {
    }

    @Override
    public void prepareTest(Object o) {

    }

    @Override
    public void afterTest(Method method) {

    }
}
