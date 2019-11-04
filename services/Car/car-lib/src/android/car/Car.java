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
 * limitations under the License.
 */

package android.car;

import android.annotation.IntDef;
import android.annotation.Nullable;
import android.annotation.SdkConstant;
import android.annotation.SdkConstant.SdkConstantType;
import android.annotation.SystemApi;
import android.car.cluster.CarInstrumentClusterManager;
import android.car.cluster.ClusterActivityState;
import android.car.content.pm.CarPackageManager;
import android.car.diagnostic.CarDiagnosticManager;
import android.car.drivingstate.CarDrivingStateManager;
import android.car.drivingstate.CarUxRestrictionsManager;
import android.car.hardware.CarSensorManager;
import android.car.hardware.CarVendorExtensionManager;
import android.car.hardware.cabin.CarCabinManager;
import android.car.hardware.hvac.CarHvacManager;
import android.car.hardware.power.CarPowerManager;
import android.car.hardware.property.CarPropertyManager;
import android.car.hardware.property.ICarProperty;
import android.car.media.CarAudioManager;
import android.car.media.CarMediaManager;
import android.car.navigation.CarNavigationStatusManager;
import android.car.settings.CarConfigurationManager;
import android.car.storagemonitoring.CarStorageMonitoringManager;
import android.car.test.CarTestManagerBinderWrapper;
import android.car.trust.CarTrustAgentEnrollmentManager;
import android.car.vms.VmsSubscriberManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.HashMap;

/**
 *   Top level car API for embedded Android Auto deployments.
 *   This API works only for devices with {@link PackageManager#FEATURE_AUTOMOTIVE}
 *   Calling this API on a device with no such feature will lead to an exception.
 */
public final class Car {
    /**
     * Service name for {@link CarSensorManager}, to be used in {@link #getCarManager(String)}.
     *
     * @deprecated  {@link CarSensorManager} is deprecated. Use {@link CarPropertyManager} instead.
     */
    @Deprecated
    public static final String SENSOR_SERVICE = "sensor";

    /** Service name for {@link CarInfoManager}, to be used in {@link #getCarManager(String)}. */
    public static final String INFO_SERVICE = "info";

    /** Service name for {@link CarAppFocusManager}. */
    public static final String APP_FOCUS_SERVICE = "app_focus";

    /** Service name for {@link CarPackageManager} */
    public static final String PACKAGE_SERVICE = "package";

    /** Service name for {@link CarAudioManager} */
    public static final String AUDIO_SERVICE = "audio";

    /** Service name for {@link CarNavigationStatusManager} */
    public static final String CAR_NAVIGATION_SERVICE = "car_navigation_service";

    /**
     * Service name for {@link CarInstrumentClusterManager}
     *
     * @deprecated CarInstrumentClusterManager is being deprecated
     * @hide
     */
    @Deprecated
    public static final String CAR_INSTRUMENT_CLUSTER_SERVICE = "cluster_service";

    /**
     * Service name for {@link CarCabinManager}.
     *
     * @deprecated {@link CarCabinManager} is deprecated. Use {@link CarPropertyManager} instead.
     * @hide
     */
    @Deprecated
    @SystemApi
    public static final String CABIN_SERVICE = "cabin";

    /**
     * @hide
     */
    @SystemApi
    public static final String DIAGNOSTIC_SERVICE = "diagnostic";

    /**
     * Service name for {@link CarHvacManager}
     * @deprecated {@link CarHvacManager} is deprecated. Use {@link CarPropertyManager} instead.
     * @hide
     */
    @Deprecated
    @SystemApi
    public static final String HVAC_SERVICE = "hvac";

    /**
     * @hide
     */
    @SystemApi
    public static final String POWER_SERVICE = "power";

    /**
     * @hide
     */
    @SystemApi
    public static final String PROJECTION_SERVICE = "projection";

    /**
     * Service name for {@link CarPropertyManager}
     */
    public static final String PROPERTY_SERVICE = "property";

    /**
     * Service name for {@link CarVendorExtensionManager}
     *
     * @deprecated {@link CarVendorExtensionManager} is deprecated.
     * Use {@link CarPropertyManager} instead.
     * @hide
     */
    @Deprecated
    @SystemApi
    public static final String VENDOR_EXTENSION_SERVICE = "vendor_extension";

    /**
     * @hide
     */
    public static final String BLUETOOTH_SERVICE = "car_bluetooth";

    /**
     * @hide
     */
    @SystemApi
    public static final String VMS_SUBSCRIBER_SERVICE = "vehicle_map_subscriber_service";

    /**
     * Service name for {@link CarDrivingStateManager}
     * @hide
     */
    @SystemApi
    public static final String CAR_DRIVING_STATE_SERVICE = "drivingstate";

    /**
     * Service name for {@link CarUxRestrictionsManager}
     */
    public static final String CAR_UX_RESTRICTION_SERVICE = "uxrestriction";

    /**
     * Service name for {@link android.car.settings.CarConfigurationManager}
     */
    public static final String CAR_CONFIGURATION_SERVICE = "configuration";

    /**
     * Service name for {@link android.car.media.CarMediaManager}
     * @hide
     */
    public static final String CAR_MEDIA_SERVICE = "car_media";

    /**
     *
     * Service name for {@link android.car.CarBugreportManager}
     * @hide
     */
    public static final String CAR_BUGREPORT_SERVICE = "car_bugreport";

    /**
     * @hide
     */
    @SystemApi
    public static final String STORAGE_MONITORING_SERVICE = "storage_monitoring";

    /**
     * Service name for {@link android.car.trust.CarTrustAgentEnrollmentManager}
     * @hide
     */
    @SystemApi
    public static final String CAR_TRUST_AGENT_ENROLLMENT_SERVICE = "trust_enroll";

    /**
     * Service for testing. This is system app only feature.
     * Service name for {@link CarTestManager}, to be used in {@link #getCarManager(String)}.
     * @hide
     */
    @SystemApi
    public static final String TEST_SERVICE = "car-service-test";

    /** Permission necessary to access car's mileage information.
     *  @hide
     */
    @SystemApi
    public static final String PERMISSION_MILEAGE = "android.car.permission.CAR_MILEAGE";

    /** Permission necessary to access car's energy information. */
    public static final String PERMISSION_ENERGY = "android.car.permission.CAR_ENERGY";

    /** Permission necessary to access car's VIN information */
    public static final String PERMISSION_IDENTIFICATION =
            "android.car.permission.CAR_IDENTIFICATION";

    /** Permission necessary to access car's speed. */
    public static final String PERMISSION_SPEED = "android.car.permission.CAR_SPEED";

    /** Permission necessary to access car's dynamics state.
     *  @hide
     */
    @SystemApi
    public static final String PERMISSION_CAR_DYNAMICS_STATE =
            "android.car.permission.CAR_DYNAMICS_STATE";

    /** Permission necessary to access car's fuel door and ev charge port. */
    public static final String PERMISSION_ENERGY_PORTS = "android.car.permission.CAR_ENERGY_PORTS";

    /** Permission necessary to read car's exterior lights information.
     *  @hide
     */
    @SystemApi
    public static final String PERMISSION_EXTERIOR_LIGHTS =
            "android.car.permission.CAR_EXTERIOR_LIGHTS";

    /**
     * Permission necessary to read car's interior lights information.
     */
    public static final String PERMISSION_READ_INTERIOR_LIGHTS =
            "android.car.permission.READ_CAR_INTERIOR_LIGHTS";

    /** Permission necessary to control car's exterior lights.
     *  @hide
     */
    @SystemApi
    public static final String PERMISSION_CONTROL_EXTERIOR_LIGHTS =
            "android.car.permission.CONTROL_CAR_EXTERIOR_LIGHTS";

    /**
     * Permission necessary to control car's interior lights.
     */
    public static final String PERMISSION_CONTROL_INTERIOR_LIGHTS =
            "android.car.permission.CONTROL_CAR_INTERIOR_LIGHTS";

    /** Permission necessary to access car's powertrain information.*/
    public static final String PERMISSION_POWERTRAIN = "android.car.permission.CAR_POWERTRAIN";

    /**
     * Permission necessary to change car audio volume through {@link CarAudioManager}.
     */
    public static final String PERMISSION_CAR_CONTROL_AUDIO_VOLUME =
            "android.car.permission.CAR_CONTROL_AUDIO_VOLUME";

    /**
     * Permission necessary to change car audio settings through {@link CarAudioManager}.
     */
    public static final String PERMISSION_CAR_CONTROL_AUDIO_SETTINGS =
            "android.car.permission.CAR_CONTROL_AUDIO_SETTINGS";

    /**
     * Permission necessary to receive full audio ducking events from car audio focus handler.
     *
     * @hide
     */
    @SystemApi
    public static final String PERMISSION_RECEIVE_CAR_AUDIO_DUCKING_EVENTS =
            "android.car.permission.RECEIVE_CAR_AUDIO_DUCKING_EVENTS";

    /**
     * Permission necessary to use {@link CarNavigationStatusManager}.
     */
    public static final String PERMISSION_CAR_NAVIGATION_MANAGER =
            "android.car.permission.CAR_NAVIGATION_MANAGER";

    /**
     * Permission necessary to start activities in the instrument cluster through
     * {@link CarInstrumentClusterManager}
     *
     * @hide
     */
    @SystemApi
    public static final String PERMISSION_CAR_INSTRUMENT_CLUSTER_CONTROL =
            "android.car.permission.CAR_INSTRUMENT_CLUSTER_CONTROL";

    /**
     * Application must have this permission in order to be launched in the instrument cluster
     * display.
     *
     * @hide
     */
    public static final String PERMISSION_CAR_DISPLAY_IN_CLUSTER =
            "android.car.permission.CAR_DISPLAY_IN_CLUSTER";

    /** Permission necessary to use {@link CarInfoManager}. */
    public static final String PERMISSION_CAR_INFO = "android.car.permission.CAR_INFO";

    /** Permission necessary to read temperature of car's exterior environment. */
    public static final String PERMISSION_EXTERIOR_ENVIRONMENT =
            "android.car.permission.CAR_EXTERIOR_ENVIRONMENT";

    /**
     * Permission necessary to access car specific communication channel.
     * @hide
     */
    @SystemApi
    public static final String PERMISSION_VENDOR_EXTENSION =
            "android.car.permission.CAR_VENDOR_EXTENSION";

    /**
     * @hide
     */
    @SystemApi
    public static final String PERMISSION_CONTROL_APP_BLOCKING =
            "android.car.permission.CONTROL_APP_BLOCKING";

    /**
     * Permission necessary to access car's engine information.
     * @hide
     */
    @SystemApi
    public static final String PERMISSION_CAR_ENGINE_DETAILED =
            "android.car.permission.CAR_ENGINE_DETAILED";

    /**
     * Permission necessary to access car's tire pressure information.
     * @hide
     */
    @SystemApi
    public static final String PERMISSION_TIRES = "android.car.permission.CAR_TIRES";

    /**
     * Permission necessary to access car's steering angle information.
     */
    public static final String PERMISSION_READ_STEERING_STATE =
            "android.car.permission.READ_CAR_STEERING";

    /**
     * Permission necessary to read and write display units for distance, fuel volume, tire pressure
     * and ev battery.
     */
    public static final String PERMISSION_READ_DISPLAY_UNITS =
            "android.car.permission.READ_CAR_DISPLAY_UNITS";
    /**
     * Permission necessary to control display units for distance, fuel volume, tire pressure
     * and ev battery.
     */
    public static final String PERMISSION_CONTROL_DISPLAY_UNITS =
            "android.car.permission.CONTROL_CAR_DISPLAY_UNITS";

    /**
     * Permission necessary to control car's door.
     * @hide
     */
    @SystemApi
    public static final String PERMISSION_CONTROL_CAR_DOORS =
            "android.car.permission.CONTROL_CAR_DOORS";

    /**
     * Permission necessary to control car's windows.
     * @hide
     */
    @SystemApi
    public static final String PERMISSION_CONTROL_CAR_WINDOWS =
            "android.car.permission.CONTROL_CAR_WINDOWS";

    /**
     * Permission necessary to control car's seats.
     * @hide
     */
    @SystemApi
    public static final String PERMISSION_CONTROL_CAR_SEATS =
            "android.car.permission.CONTROL_CAR_SEATS";

    /**
     * Permission necessary to control car's mirrors.
     * @hide
     */
    @SystemApi
    public static final String PERMISSION_CONTROL_CAR_MIRRORS =
            "android.car.permission.CONTROL_CAR_MIRRORS";

    /**
     * Permission necessary to access Car HVAC APIs.
     * @hide
     */
    @SystemApi
    public static final String PERMISSION_CONTROL_CAR_CLIMATE =
            "android.car.permission.CONTROL_CAR_CLIMATE";

    /**
     * Permission necessary to access Car POWER APIs.
     * @hide
     */
    @SystemApi
    public static final String PERMISSION_CAR_POWER = "android.car.permission.CAR_POWER";

    /**
     * Permission necessary to access Car PROJECTION system APIs.
     * @hide
     */
    @SystemApi
    public static final String PERMISSION_CAR_PROJECTION = "android.car.permission.CAR_PROJECTION";

    /**
     * Permission necessary to access projection status.
     * @hide
     */
    @SystemApi
    public static final String PERMISSION_CAR_PROJECTION_STATUS =
            "android.car.permission.ACCESS_CAR_PROJECTION_STATUS";

    /**
     * Permission necessary to mock vehicle hal for testing.
     * @hide
     * @deprecated mocking vehicle HAL in car service is no longer supported.
     */
    @SystemApi
    public static final String PERMISSION_MOCK_VEHICLE_HAL =
            "android.car.permission.CAR_MOCK_VEHICLE_HAL";

    /**
     * Permission necessary to access CarTestService.
     * @hide
     */
    @SystemApi
    public static final String PERMISSION_CAR_TEST_SERVICE =
            "android.car.permission.CAR_TEST_SERVICE";

    /**
     * Permission necessary to access CarDrivingStateService to get a Car's driving state.
     * @hide
     */
    @SystemApi
    public static final String PERMISSION_CAR_DRIVING_STATE =
            "android.car.permission.CAR_DRIVING_STATE";

    /**
     * Permission necessary to access VMS client service.
     *
     * @hide
     */
    public static final String PERMISSION_BIND_VMS_CLIENT =
            "android.car.permission.BIND_VMS_CLIENT";

    /**
     * Permissions necessary to access VMS publisher APIs.
     *
     * @hide
     */
    @SystemApi
    public static final String PERMISSION_VMS_PUBLISHER = "android.car.permission.VMS_PUBLISHER";

    /**
     * Permissions necessary to access VMS subscriber APIs.
     *
     * @hide
     */
    @SystemApi
    public static final String PERMISSION_VMS_SUBSCRIBER = "android.car.permission.VMS_SUBSCRIBER";

    /**
     * Permissions necessary to read diagnostic information, including vendor-specific bits.
     *
     * @hide
     */
    @SystemApi
    public static final String PERMISSION_CAR_DIAGNOSTIC_READ_ALL =
        "android.car.permission.CAR_DIAGNOSTICS";

    /**
     * Permissions necessary to clear diagnostic information.
     *
     * @hide
     */
    @SystemApi
    public static final String PERMISSION_CAR_DIAGNOSTIC_CLEAR =
            "android.car.permission.CLEAR_CAR_DIAGNOSTICS";

    /**
     * Permission necessary to configure UX restrictions through {@link CarUxRestrictionsManager}.
     *
     * @hide
     */
    public static final String PERMISSION_CAR_UX_RESTRICTIONS_CONFIGURATION =
            "android.car.permission.CAR_UX_RESTRICTIONS_CONFIGURATION";

    /**
     * Permissions necessary to clear diagnostic information.
     *
     * @hide
     */
    @SystemApi
    public static final String PERMISSION_STORAGE_MONITORING =
            "android.car.permission.STORAGE_MONITORING";

    /**
     * Permission necessary to enroll a device as a trusted authenticator device.
     *
     * @hide
     */
    @SystemApi
    public static final String PERMISSION_CAR_ENROLL_TRUST =
            "android.car.permission.CAR_ENROLL_TRUST";

    /** Type of car connection: platform runs directly in car. */
    public static final int CONNECTION_TYPE_EMBEDDED = 5;


    /** @hide */
    @IntDef({CONNECTION_TYPE_EMBEDDED})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ConnectionType {}

    /**
     * Activity Action: Provide media playing through a media template app.
     * <p>Input: String extra mapped by {@link android.app.SearchManager#QUERY} is the query
     * used to start the media. String extra mapped by {@link #CAR_EXTRA_MEDIA_COMPONENT} is the
     * component name of the media app which user wants to play media on.
     * <p>Output: nothing.
     */
    @SdkConstant(SdkConstantType.ACTIVITY_INTENT_ACTION)
    public static final String CAR_INTENT_ACTION_MEDIA_TEMPLATE =
            "android.car.intent.action.MEDIA_TEMPLATE";

    /**
     * Used as a string extra field with {@link #CAR_INTENT_ACTION_MEDIA_TEMPLATE} to specify the
     * MediaBrowserService that user wants to start the media on.
     *
     * @hide
     */
    public static final String CAR_EXTRA_MEDIA_COMPONENT =
            "android.car.intent.extra.MEDIA_COMPONENT";

    /**
     * Used as a string extra field with {@link #CAR_INTENT_ACTION_MEDIA_TEMPLATE} to specify the
     * media app that user wants to start the media on. Note: this is not the templated media app.
     *
     * This is being deprecated. Use {@link #CAR_EXTRA_MEDIA_COMPONENT} instead.
     */
    public static final String CAR_EXTRA_MEDIA_PACKAGE = "android.car.intent.extra.MEDIA_PACKAGE";

    /**
     * Used as a string extra field of media session to specify the service corresponding to the
     * session.
     *
     * @hide
     */
    public static final String CAR_EXTRA_BROWSE_SERVICE_FOR_SESSION =
            "android.media.session.BROWSE_SERVICE";

    /** @hide */
    public static final String CAR_SERVICE_INTERFACE_NAME = "android.car.ICar";

    private static final String CAR_SERVICE_PACKAGE = "com.android.car";

    private static final String CAR_SERVICE_CLASS = "com.android.car.CarService";

    /**
     * Category used by navigation applications to indicate which activity should be launched on
     * the instrument cluster when such application holds
     * {@link CarAppFocusManager#APP_FOCUS_TYPE_NAVIGATION} focus.
     *
     * @hide
     */
    public static final String CAR_CATEGORY_NAVIGATION = "android.car.cluster.NAVIGATION";

    /**
     * When an activity is launched in the cluster, it will receive {@link ClusterActivityState} in
     * the intent's extra under this key, containing instrument cluster information such as
     * unobscured area, visibility, etc.
     *
     * @hide
     */
    @SystemApi
    public static final String CAR_EXTRA_CLUSTER_ACTIVITY_STATE =
            "android.car.cluster.ClusterActivityState";

    private static final long CAR_SERVICE_BIND_RETRY_INTERVAL_MS = 500;
    private static final long CAR_SERVICE_BIND_MAX_RETRY = 20;

    private final Context mContext;
    @GuardedBy("this")
    private ICar mService;
    private final boolean mOwnsService;
    private static final int STATE_DISCONNECTED = 0;
    private static final int STATE_CONNECTING = 1;
    private static final int STATE_CONNECTED = 2;
    @GuardedBy("this")
    private int mConnectionState;
    @GuardedBy("this")
    private int mConnectionRetryCount;

    private final Runnable mConnectionRetryRunnable = new Runnable() {
        @Override
        public void run() {
            startCarService();
        }
    };

    private final Runnable mConnectionRetryFailedRunnable = new Runnable() {
        @Override
        public void run() {
            mServiceConnectionListener.onServiceDisconnected(new ComponentName(CAR_SERVICE_PACKAGE,
                    CAR_SERVICE_CLASS));
        }
    };

    private final ServiceConnection mServiceConnectionListener =
            new ServiceConnection () {
        public void onServiceConnected(ComponentName name, IBinder service) {
            synchronized (Car.this) {
                mService = ICar.Stub.asInterface(service);
                mConnectionState = STATE_CONNECTED;
            }
            mServiceConnectionListenerClient.onServiceConnected(name, service);
        }

        public void onServiceDisconnected(ComponentName name) {
            synchronized (Car.this) {
                if (mConnectionState  == STATE_DISCONNECTED) {
                    return;
                }
            }
            // unbind explicitly and set connectionState to STATE_DISCONNECTED here.
            disconnect();
            mServiceConnectionListenerClient.onServiceDisconnected(name);
        }
    };

    private final ServiceConnection mServiceConnectionListenerClient;
    private final Object mCarManagerLock = new Object();
    @GuardedBy("mCarManagerLock")
    private final HashMap<String, CarManagerBase> mServiceMap = new HashMap<>();

    /** Handler for generic event dispatching. */
    private final Handler mEventHandler;

    private final Handler mMainThreadEventHandler;

    /**
     * A factory method that creates Car instance for all Car API access.
     * @param context
     * @param serviceConnectionListener listener for monitoring service connection.
     * @param handler the handler on which the callback should execute, or null to execute on the
     * service's main thread. Note: the service connection listener will be always on the main
     * thread regardless of the handler given.
     * @return Car instance if system is in car environment and returns {@code null} otherwise.
     *
     * @deprecated use {@link #createCar(Context, Handler)} instead.
     */
    @Deprecated
    public static Car createCar(Context context, ServiceConnection serviceConnectionListener,
            @Nullable Handler handler) {
        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            Log.e(CarLibLog.TAG_CAR, "FEATURE_AUTOMOTIVE not declared while android.car is used");
            return null;
        }
        try {
          return new Car(context, serviceConnectionListener, handler);
        } catch (IllegalArgumentException e) {
          // Expected when car service loader is not available.
        }
        return null;
    }

    /**
     * A factory method that creates Car instance for all Car API access using main thread {@code
     * Looper}.
     *
     * @see #createCar(Context, ServiceConnection, Handler)
     *
     * @deprecated use {@link #createCar(Context, Handler)} instead.
     */
    @Deprecated
    public static Car createCar(Context context, ServiceConnection serviceConnectionListener) {
      return createCar(context, serviceConnectionListener, null);
    }

    /**
     * Creates new {@link Car} object which connected synchronously to Car Service and ready to use.
     *
     * @param context application's context
     *
     * @return Car object if operation succeeded, otherwise null.
     */
    @Nullable
    public static Car createCar(Context context) {
        return createCar(context, (Handler) null);
    }

    /**
     * Creates new {@link Car} object which connected synchronously to Car Service and ready to use.
     *
     * @param context application's context
     * @param handler the handler on which the manager's callbacks will be executed, or null to
     * execute on the application's main thread.
     *
     * @return Car object if operation succeeded, otherwise null.
     */
    @Nullable
    public static Car createCar(Context context, @Nullable Handler handler) {
        IBinder service = ServiceManager.getService("car_service");
        if (service == null) {
            return null;
        }
        return new Car(context, ICar.Stub.asInterface(service), handler);
    }

    private Car(Context context, ServiceConnection serviceConnectionListener,
            @Nullable Handler handler) {
        mContext = context;
        mEventHandler = determineEventHandler(handler);
        mMainThreadEventHandler = determineMainThreadEventHandler(mEventHandler);

        mService = null;
        mOwnsService = true;
        mServiceConnectionListenerClient = serviceConnectionListener;
    }


    /**
     * Car constructor when ICar binder is already available.
     * @hide
     */
    public Car(Context context, ICar service, @Nullable Handler handler) {
        mContext = context;
        mEventHandler = determineEventHandler(handler);
        mMainThreadEventHandler = determineMainThreadEventHandler(mEventHandler);

        mService = service;
        mOwnsService = false;
        mConnectionState = STATE_CONNECTED;
        mServiceConnectionListenerClient = null;
    }

    private static Handler determineMainThreadEventHandler(Handler eventHandler) {
        Looper mainLooper = Looper.getMainLooper();
        return (eventHandler.getLooper() == mainLooper) ? eventHandler : new Handler(mainLooper);
    }

    private static Handler determineEventHandler(@Nullable Handler handler) {
        if (handler == null) {
            Looper looper = Looper.getMainLooper();
            handler = new Handler(looper);
        }
        return handler;
    }

    /**
     * Connect to car service. This can be called while it is disconnected.
     * @throws IllegalStateException If connection is still on-going from previous
     *         connect call or it is already connected
     *
     * @deprecated this method is not need if this object is created via
     * {@link #createCar(Context, Handler)}.
     */
    @Deprecated
    public void connect() throws IllegalStateException {
        synchronized (this) {
            if (mConnectionState != STATE_DISCONNECTED) {
                throw new IllegalStateException("already connected or connecting");
            }
            mConnectionState = STATE_CONNECTING;
            startCarService();
        }
    }

    /**
     * Disconnect from car service. This can be called while disconnected. Once disconnect is
     * called, all Car*Managers from this instance becomes invalid, and
     * {@link Car#getCarManager(String)} will return different instance if it is connected again.
     */
    public void disconnect() {
        synchronized (this) {
            if (mConnectionState == STATE_DISCONNECTED) {
                return;
            }
            mEventHandler.removeCallbacks(mConnectionRetryRunnable);
            mMainThreadEventHandler.removeCallbacks(mConnectionRetryFailedRunnable);
            mConnectionRetryCount = 0;
            tearDownCarManagers();
            mService = null;
            mConnectionState = STATE_DISCONNECTED;

            if (mOwnsService) {
                mContext.unbindService(mServiceConnectionListener);
            }
        }
    }

    /**
     * Tells if it is connected to the service or not. This will return false if it is still
     * connecting.
     * @return
     */
    public boolean isConnected() {
        synchronized (this) {
            return mService != null;
        }
    }

    /**
     * Tells if this instance is already connecting to car service or not.
     * @return
     */
    public boolean isConnecting() {
        synchronized (this) {
            return mConnectionState == STATE_CONNECTING;
        }
    }

    /**
     * Get car specific service as in {@link Context#getSystemService(String)}. Returned
     * {@link Object} should be type-casted to the desired service.
     * For example, to get sensor service,
     * SensorManagerService sensorManagerService = car.getCarManager(Car.SENSOR_SERVICE);
     * @param serviceName Name of service that should be created like {@link #SENSOR_SERVICE}.
     * @return Matching service manager or null if there is no such service.
     */
    @Nullable
    public Object getCarManager(String serviceName) {
        CarManagerBase manager;
        ICar service = getICarOrThrow();
        synchronized (mCarManagerLock) {
            manager = mServiceMap.get(serviceName);
            if (manager == null) {
                try {
                    IBinder binder = service.getCarService(serviceName);
                    if (binder == null) {
                        Log.w(CarLibLog.TAG_CAR, "getCarManager could not get binder for service:" +
                                serviceName);
                        return null;
                    }
                    manager = createCarManager(serviceName, binder);
                    if (manager == null) {
                        Log.w(CarLibLog.TAG_CAR,
                                "getCarManager could not create manager for service:" +
                                        serviceName);
                        return null;
                    }
                    mServiceMap.put(serviceName, manager);
                } catch (RemoteException e) {
                    throw e.rethrowFromSystemServer();
                }
            }
        }
        return manager;
    }

    /**
     * Return the type of currently connected car.
     * @return
     */
    @ConnectionType
    public int getCarConnectionType() {
        return CONNECTION_TYPE_EMBEDDED;
    }

    @Nullable
    private CarManagerBase createCarManager(String serviceName, IBinder binder) {
        CarManagerBase manager = null;
        switch (serviceName) {
            case AUDIO_SERVICE:
                manager = new CarAudioManager(binder, mContext, mEventHandler);
                break;
            case SENSOR_SERVICE:
                manager = new CarSensorManager(binder, mContext, mEventHandler);
                break;
            case INFO_SERVICE:
                manager = new CarInfoManager(binder);
                break;
            case APP_FOCUS_SERVICE:
                manager = new CarAppFocusManager(binder, mEventHandler);
                break;
            case PACKAGE_SERVICE:
                manager = new CarPackageManager(binder, mContext);
                break;
            case CAR_NAVIGATION_SERVICE:
                manager = new CarNavigationStatusManager(binder);
                break;
            case CABIN_SERVICE:
                manager = new CarCabinManager(binder, mContext, mEventHandler);
                break;
            case DIAGNOSTIC_SERVICE:
                manager = new CarDiagnosticManager(binder, mContext, mEventHandler);
                break;
            case HVAC_SERVICE:
                manager = new CarHvacManager(binder, mContext, mEventHandler);
                break;
            case POWER_SERVICE:
                manager = new CarPowerManager(binder, mContext, mEventHandler);
                break;
            case PROJECTION_SERVICE:
                manager = new CarProjectionManager(binder, mEventHandler);
                break;
            case PROPERTY_SERVICE:
                manager = new CarPropertyManager(ICarProperty.Stub.asInterface(binder),
                    mEventHandler);
                break;
            case VENDOR_EXTENSION_SERVICE:
                manager = new CarVendorExtensionManager(binder, mEventHandler);
                break;
            case CAR_INSTRUMENT_CLUSTER_SERVICE:
                manager = new CarInstrumentClusterManager(binder, mEventHandler);
                break;
            case TEST_SERVICE:
                /* CarTestManager exist in static library. So instead of constructing it here,
                 * only pass binder wrapper so that CarTestManager can be constructed outside. */
                manager = new CarTestManagerBinderWrapper(binder);
                break;
            case VMS_SUBSCRIBER_SERVICE:
                manager = new VmsSubscriberManager(binder);
                break;
            case BLUETOOTH_SERVICE:
                manager = new CarBluetoothManager(binder, mContext);
                break;
            case STORAGE_MONITORING_SERVICE:
                manager = new CarStorageMonitoringManager(binder, mEventHandler);
                break;
            case CAR_DRIVING_STATE_SERVICE:
                manager = new CarDrivingStateManager(binder, mContext, mEventHandler);
                break;
            case CAR_UX_RESTRICTION_SERVICE:
                manager = new CarUxRestrictionsManager(binder, mContext, mEventHandler);
                break;
            case CAR_CONFIGURATION_SERVICE:
                manager = new CarConfigurationManager(binder);
                break;
            case CAR_TRUST_AGENT_ENROLLMENT_SERVICE:
                manager = new CarTrustAgentEnrollmentManager(binder, mContext, mEventHandler);
                break;
            case CAR_MEDIA_SERVICE:
                manager = new CarMediaManager(binder);
                break;
            case CAR_BUGREPORT_SERVICE:
                manager = new CarBugreportManager(binder, mContext);
                break;
            default:
                break;
        }
        return manager;
    }

    private void startCarService() {
        Intent intent = new Intent();
        intent.setPackage(CAR_SERVICE_PACKAGE);
        intent.setAction(Car.CAR_SERVICE_INTERFACE_NAME);
        boolean bound = mContext.bindServiceAsUser(intent, mServiceConnectionListener,
                Context.BIND_AUTO_CREATE, UserHandle.CURRENT_OR_SELF);
        if (!bound) {
            mConnectionRetryCount++;
            if (mConnectionRetryCount > CAR_SERVICE_BIND_MAX_RETRY) {
                Log.w(CarLibLog.TAG_CAR, "cannot bind to car service after max retry");
                mMainThreadEventHandler.post(mConnectionRetryFailedRunnable);
            } else {
                mEventHandler.postDelayed(mConnectionRetryRunnable,
                        CAR_SERVICE_BIND_RETRY_INTERVAL_MS);
            }
        } else {
            mConnectionRetryCount = 0;
        }
    }

    private synchronized ICar getICarOrThrow() throws IllegalStateException {
        if (mService == null) {
            throw new IllegalStateException("not connected");
        }
        return mService;
    }

    private void tearDownCarManagers() {
        synchronized (mCarManagerLock) {
            for (CarManagerBase manager: mServiceMap.values()) {
                manager.onCarDisconnected();
            }
            mServiceMap.clear();
        }
    }
}
