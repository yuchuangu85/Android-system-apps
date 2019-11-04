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

package android.car.testapi;

import android.car.Car;
import android.car.IAppFocus;
import android.car.ICar;
import android.car.ICarBluetooth;
import android.car.cluster.IInstrumentClusterManagerService;
import android.car.cluster.renderer.IInstrumentClusterNavigation;
import android.car.content.pm.ICarPackageManager;
import android.car.diagnostic.ICarDiagnostic;
import android.car.drivingstate.ICarDrivingState;
import android.car.drivingstate.ICarUxRestrictionsManager;
import android.car.hardware.power.ICarPower;
import android.car.media.ICarAudio;
import android.car.settings.ICarConfigurationManager;
import android.car.storagemonitoring.ICarStorageMonitoring;
import android.car.vms.IVmsSubscriberService;
import android.content.Context;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/*
    The idea behind this class is that we can fake-out interfaces between Car*Manager and
    Car Service.  Effectively creating a fake version of Car Service that can run under Robolectric
    environment (thus running on the desktop rather than on a real device).

    By default all interfaces are mocked out just to allow dummy implementation and avoid crashes.
    This will allow production code to call into Car*Manager w/o crashes because managers will just
    pass the call into mocked version of the interface. However, in many cases
    developers would like to have more sophisticated test cases and ability to simulate vehicle as
    they need.  In this case mocked version of particular service needs to be replaced with the fake
    one which will have fake implementation to satisfy test needs and additional interface needs
    to be exposed to the app developers such that they can simulate fake car behavior, this
    interface has -Controller suffix and defined as inner interface in this class.
 */

/**
 * Test API to get Car Managers backed by fake car service.
 *
 * <p>In order to use it in your tests you should create Car object by calling static method
 * {@link FakeCar#createFakeCar(Context)}.  It will effectively create {@link FakeCar} object and
 * you can get access to {@link Car} by calling {@link FakeCar#getCar()}.  Also, {@code FakeCar}
 * provides additional testing API that will allow you to simulate vehicle's behavior as you need.
 *
 * <p>Here's an example of usage:
 * <code>
 *     FakeCar fakeCar = FakeCar.createFakeCar(appContext);
 *     Car realCar = fakeCar.getCar();  // pass this instance to your DI framework or class to test
 *
 *     // Then you can obtain different controllers to modify behavior of your fake car.
 *     PropertyController propertyController = fakeCar.getPropertyController();
 *     propertyController.setProperties(listOfSupportedProperties)
 * </code>
 */
public class FakeCar {
    private static final String TAG = FakeCar.class.getSimpleName();

    private final Car mCar;
    private final FakeCarService mService;

    /** Creates an instance of {@link FakeCar} */
    public static FakeCar createFakeCar(Context context) {
        FakeCarService service = new FakeCarService(context);
        Car car = new Car(context, service, null);

        return new FakeCar(car, service);
    }

    private FakeCar(Car car, FakeCarService service) {
        mCar = car;
        mService = service;
    }

    /** Returns Car object which is backed by fake implementation. */
    public Car getCar() {
        return mCar;
    }

    /** Returns test controller to modify car properties */
    public CarPropertyController getCarPropertyController() {
        return mService.mCarProperty;
    }

    /** Returns test controller to change behavior of {@link android.car.CarProjectionManager} */
    public CarProjectionController getCarProjectionController() {
        return mService.mCarProjection;
    }

    private static class FakeCarService extends ICar.Stub {
        @Mock ICarAudio.Stub mCarAudio;
        @Mock IAppFocus.Stub mAppFocus;
        @Mock ICarPackageManager.Stub mCarPackageManager;
        @Mock ICarDiagnostic.Stub mCarDiagnostic;
        @Mock ICarPower.Stub mCarPower;
        @Mock IInstrumentClusterNavigation.Stub mClusterNavigation;
        @Mock IInstrumentClusterManagerService.Stub mClusterService;
        @Mock IVmsSubscriberService.Stub mVmsSubscriberService;
        @Mock ICarBluetooth.Stub mCarBluetooth;
        @Mock ICarStorageMonitoring.Stub mCarStorageMonitoring;
        @Mock ICarDrivingState.Stub mCarDrivingState;
        @Mock ICarUxRestrictionsManager.Stub mCarUxRestriction;
        @Mock ICarConfigurationManager.Stub mCarConfigurationManager;

        private final FakeCarPropertyService mCarProperty;
        private final FakeCarProjectionService mCarProjection;

        FakeCarService(Context context) {
            MockitoAnnotations.initMocks(this);
            mCarProperty = new FakeCarPropertyService();
            mCarProjection = new FakeCarProjectionService(context);
        }

        @Override
        public void setCarServiceHelper(IBinder helper) throws RemoteException {
            // Nothing to do yet.
        }

        @Override
        public void setUserLockStatus(int userHandle, int unlocked) throws RemoteException {
            // Nothing to do yet.
        }

        @Override
        public void onSwitchUser(int userHandle) throws RemoteException {
            // Nothing to do yet.
        }

        @Override
        public IBinder getCarService(String serviceName) throws RemoteException {
            switch (serviceName) {
                case Car.AUDIO_SERVICE:
                    return mCarAudio;
                case Car.APP_FOCUS_SERVICE:
                    return mAppFocus;
                case Car.PACKAGE_SERVICE:
                    return mCarPackageManager;
                case Car.DIAGNOSTIC_SERVICE:
                    return mCarDiagnostic;
                case Car.POWER_SERVICE:
                    return mCarPower;
                case Car.CABIN_SERVICE:
                case Car.HVAC_SERVICE:
                case Car.INFO_SERVICE:
                case Car.PROPERTY_SERVICE:
                case Car.SENSOR_SERVICE:
                case Car.VENDOR_EXTENSION_SERVICE:
                    return mCarProperty;
                case Car.CAR_NAVIGATION_SERVICE:
                    return mClusterNavigation;
                case Car.CAR_INSTRUMENT_CLUSTER_SERVICE:
                    return mClusterService;
                case Car.PROJECTION_SERVICE:
                    return mCarProjection;
                case Car.VMS_SUBSCRIBER_SERVICE:
                    return mVmsSubscriberService;
                case Car.BLUETOOTH_SERVICE:
                    return mCarBluetooth;
                case Car.STORAGE_MONITORING_SERVICE:
                    return mCarStorageMonitoring;
                case Car.CAR_DRIVING_STATE_SERVICE:
                    return mCarDrivingState;
                case Car.CAR_UX_RESTRICTION_SERVICE:
                    return mCarUxRestriction;
                case Car.CAR_CONFIGURATION_SERVICE:
                    return mCarConfigurationManager;
                default:
                    Log.w(TAG, "getCarService for unknown service:" + serviceName);
                    return null;
            }
        }

        @Override
        public int getCarConnectionType() throws RemoteException {
            return Car.CONNECTION_TYPE_EMBEDDED;
        }
    }

}
