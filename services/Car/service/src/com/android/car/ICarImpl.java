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

package com.android.car;

import android.annotation.MainThread;
import android.app.ActivityManager;
import android.app.UiModeManager;
import android.car.Car;
import android.car.ICar;
import android.car.cluster.renderer.IInstrumentClusterNavigation;
import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.hardware.automotive.vehicle.V2_0.IVehicle;
import android.hardware.automotive.vehicle.V2_0.VehicleArea;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Process;
import android.os.Trace;
import android.util.Log;
import android.util.Slog;
import android.util.TimingsTraceLog;

import com.android.car.audio.CarAudioService;
import com.android.car.cluster.InstrumentClusterService;
import com.android.car.garagemode.GarageModeService;
import com.android.car.hal.VehicleHal;
import com.android.car.internal.FeatureConfiguration;
import com.android.car.pm.CarPackageManagerService;
import com.android.car.systeminterface.SystemInterface;
import com.android.car.trust.CarTrustedDeviceService;
import com.android.car.user.CarUserService;
import com.android.car.vms.VmsBrokerService;
import com.android.car.vms.VmsClientManager;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.car.ICarServiceHelper;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ICarImpl extends ICar.Stub {

    public static final String INTERNAL_INPUT_SERVICE = "internal_input";
    public static final String INTERNAL_SYSTEM_ACTIVITY_MONITORING_SERVICE =
            "system_activity_monitoring";

    private final Context mContext;
    private final VehicleHal mHal;

    private final SystemInterface mSystemInterface;

    private final SystemActivityMonitoringService mSystemActivityMonitoringService;
    private final CarPowerManagementService mCarPowerManagementService;
    private final CarPackageManagerService mCarPackageManagerService;
    private final CarInputService mCarInputService;
    private final CarDrivingStateService mCarDrivingStateService;
    private final CarUxRestrictionsManagerService mCarUXRestrictionsService;
    private final CarAudioService mCarAudioService;
    private final CarProjectionService mCarProjectionService;
    private final CarPropertyService mCarPropertyService;
    private final CarNightService mCarNightService;
    private final AppFocusService mAppFocusService;
    private final GarageModeService mGarageModeService;
    private final InstrumentClusterService mInstrumentClusterService;
    private final CarLocationService mCarLocationService;
    private final SystemStateControllerService mSystemStateControllerService;
    private final CarBluetoothService mCarBluetoothService;
    private final PerUserCarServiceHelper mPerUserCarServiceHelper;
    private final CarDiagnosticService mCarDiagnosticService;
    private final CarStorageMonitoringService mCarStorageMonitoringService;
    private final CarConfigurationService mCarConfigurationService;
    private final CarTrustedDeviceService mCarTrustedDeviceService;
    private final CarMediaService mCarMediaService;
    private final CarUserManagerHelper mUserManagerHelper;
    private final CarUserService mCarUserService;
    private final VmsClientManager mVmsClientManager;
    private final VmsBrokerService mVmsBrokerService;
    private final VmsSubscriberService mVmsSubscriberService;
    private final VmsPublisherService mVmsPublisherService;
    private final CarBugreportManagerService mCarBugreportManagerService;

    private final CarServiceBase[] mAllServices;

    private static final String TAG = "ICarImpl";
    private static final String VHAL_TIMING_TAG = "VehicleHalTiming";

    private TimingsTraceLog mBootTiming;

    /** Test only service. Populate it only when necessary. */
    @GuardedBy("this")
    private CarTestService mCarTestService;

    @GuardedBy("this")
    private ICarServiceHelper mICarServiceHelper;

    private final String mVehicleInterfaceName;

    public ICarImpl(Context serviceContext, IVehicle vehicle, SystemInterface systemInterface,
            CanBusErrorNotifier errorNotifier, String vehicleInterfaceName) {
        mContext = serviceContext;
        mSystemInterface = systemInterface;
        mHal = new VehicleHal(vehicle);
        mVehicleInterfaceName = vehicleInterfaceName;
        mUserManagerHelper = new CarUserManagerHelper(serviceContext);
        final Resources res = mContext.getResources();
        final int maxRunningUsers = res.getInteger(
                com.android.internal.R.integer.config_multiuserMaxRunningUsers);
        mCarUserService = new CarUserService(serviceContext, mUserManagerHelper,
                ActivityManager.getService(), maxRunningUsers);
        mSystemActivityMonitoringService = new SystemActivityMonitoringService(serviceContext);
        mCarPowerManagementService = new CarPowerManagementService(mContext, mHal.getPowerHal(),
                systemInterface, mUserManagerHelper);
        mCarPropertyService = new CarPropertyService(serviceContext, mHal.getPropertyHal());
        mCarDrivingStateService = new CarDrivingStateService(serviceContext, mCarPropertyService);
        mCarUXRestrictionsService = new CarUxRestrictionsManagerService(serviceContext,
                mCarDrivingStateService, mCarPropertyService);
        mCarPackageManagerService = new CarPackageManagerService(serviceContext,
                mCarUXRestrictionsService,
                mSystemActivityMonitoringService,
                mUserManagerHelper);
        mPerUserCarServiceHelper = new PerUserCarServiceHelper(serviceContext);
        mCarBluetoothService = new CarBluetoothService(serviceContext, mPerUserCarServiceHelper);
        mCarInputService = new CarInputService(serviceContext, mHal.getInputHal());
        mCarProjectionService = new CarProjectionService(
                serviceContext, null /* handler */, mCarInputService, mCarBluetoothService);
        mGarageModeService = new GarageModeService(mContext);
        mAppFocusService = new AppFocusService(serviceContext, mSystemActivityMonitoringService);
        mCarAudioService = new CarAudioService(serviceContext);
        mCarNightService = new CarNightService(serviceContext, mCarPropertyService);
        mInstrumentClusterService = new InstrumentClusterService(serviceContext,
                mAppFocusService, mCarInputService);
        mSystemStateControllerService = new SystemStateControllerService(
                serviceContext, mCarAudioService, this);
        mVmsBrokerService = new VmsBrokerService(mContext.getPackageManager());
        mVmsClientManager = new VmsClientManager(
                serviceContext, mCarUserService, mUserManagerHelper, mHal.getVmsHal());
        mVmsSubscriberService = new VmsSubscriberService(
                serviceContext, mVmsBrokerService, mHal.getVmsHal());
        mVmsPublisherService = new VmsPublisherService(
                serviceContext, mVmsBrokerService, mVmsClientManager);
        mCarDiagnosticService = new CarDiagnosticService(serviceContext, mHal.getDiagnosticHal());
        mCarStorageMonitoringService = new CarStorageMonitoringService(serviceContext,
                systemInterface);
        mCarConfigurationService =
                new CarConfigurationService(serviceContext, new JsonReaderImpl());
        mCarLocationService = new CarLocationService(mContext, mUserManagerHelper);
        mCarTrustedDeviceService = new CarTrustedDeviceService(serviceContext);
        mCarMediaService = new CarMediaService(serviceContext);
        mCarBugreportManagerService = new CarBugreportManagerService(serviceContext);

        CarLocalServices.addService(CarPowerManagementService.class, mCarPowerManagementService);
        CarLocalServices.addService(CarUserService.class, mCarUserService);
        CarLocalServices.addService(CarTrustedDeviceService.class, mCarTrustedDeviceService);
        CarLocalServices.addService(SystemInterface.class, mSystemInterface);
        CarLocalServices.addService(CarDrivingStateService.class, mCarDrivingStateService);
        CarLocalServices.addService(PerUserCarServiceHelper.class, mPerUserCarServiceHelper);

        // Be careful with order. Service depending on other service should be inited later.
        List<CarServiceBase> allServices = new ArrayList<>();
        allServices.add(mCarUserService);
        allServices.add(mSystemActivityMonitoringService);
        allServices.add(mCarPowerManagementService);
        allServices.add(mCarPropertyService);
        allServices.add(mCarDrivingStateService);
        allServices.add(mCarUXRestrictionsService);
        allServices.add(mCarPackageManagerService);
        allServices.add(mCarInputService);
        allServices.add(mGarageModeService);
        allServices.add(mAppFocusService);
        allServices.add(mCarAudioService);
        allServices.add(mCarNightService);
        allServices.add(mInstrumentClusterService);
        allServices.add(mSystemStateControllerService);
        allServices.add(mPerUserCarServiceHelper);
        allServices.add(mCarBluetoothService);
        allServices.add(mCarProjectionService);
        allServices.add(mCarDiagnosticService);
        allServices.add(mCarStorageMonitoringService);
        allServices.add(mCarConfigurationService);
        allServices.add(mVmsClientManager);
        allServices.add(mVmsSubscriberService);
        allServices.add(mVmsPublisherService);
        allServices.add(mCarTrustedDeviceService);
        allServices.add(mCarMediaService);
        allServices.add(mCarLocationService);
        allServices.add(mCarBugreportManagerService);
        mAllServices = allServices.toArray(new CarServiceBase[allServices.size()]);
    }

    @MainThread
    void init() {
        mBootTiming = new TimingsTraceLog(VHAL_TIMING_TAG, Trace.TRACE_TAG_HAL);
        traceBegin("VehicleHal.init");
        mHal.init();
        traceEnd();
        traceBegin("CarService.initAllServices");
        for (CarServiceBase service : mAllServices) {
            service.init();
        }
        traceEnd();
        mSystemInterface.reconfigureSecondaryDisplays();
    }

    void release() {
        // release done in opposite order from init
        for (int i = mAllServices.length - 1; i >= 0; i--) {
            mAllServices[i].release();
        }
        mHal.release();
        CarLocalServices.removeAllServices();
    }

    void vehicleHalReconnected(IVehicle vehicle) {
        mHal.vehicleHalReconnected(vehicle);
        for (CarServiceBase service : mAllServices) {
            service.vehicleHalReconnected();
        }
    }

    @Override
    public void setCarServiceHelper(IBinder helper) {
        assertCallingFromSystemProcess();
        synchronized (this) {
            mICarServiceHelper = ICarServiceHelper.Stub.asInterface(helper);
            mSystemInterface.setCarServiceHelper(mICarServiceHelper);
        }
    }

    @Override
    public void setUserLockStatus(int userHandle, int unlocked) {
        assertCallingFromSystemProcess();
        mCarUserService.setUserLockStatus(userHandle, unlocked == 1);
        mCarMediaService.setUserLockStatus(userHandle, unlocked == 1);
    }

    @Override
    public void onSwitchUser(int userHandle) {
        assertCallingFromSystemProcess();

        Log.i(TAG, "Foreground user switched to " + userHandle);
        mCarUserService.onSwitchUser(userHandle);
    }

    static void assertCallingFromSystemProcess() {
        int uid = Binder.getCallingUid();
        if (uid != Process.SYSTEM_UID) {
            throw new SecurityException("Only allowed from system");
        }
    }

    /**
     * Assert if binder call is coming from system process like system server or if it is called
     * from its own process even if it is not system. The latter can happen in test environment.
     * Note that car service runs as system user but test like car service test will not.
     */
    static void assertCallingFromSystemProcessOrSelf() {
        int uid = Binder.getCallingUid();
        int pid = Binder.getCallingPid();
        if (uid != Process.SYSTEM_UID && pid != Process.myPid()) {
            throw new SecurityException("Only allowed from system or self");
        }
    }

    @Override
    public IBinder getCarService(String serviceName) {
        switch (serviceName) {
            case Car.AUDIO_SERVICE:
                return mCarAudioService;
            case Car.APP_FOCUS_SERVICE:
                return mAppFocusService;
            case Car.PACKAGE_SERVICE:
                return mCarPackageManagerService;
            case Car.DIAGNOSTIC_SERVICE:
                assertAnyDiagnosticPermission(mContext);
                return mCarDiagnosticService;
            case Car.POWER_SERVICE:
                assertPowerPermission(mContext);
                return mCarPowerManagementService;
            case Car.CABIN_SERVICE:
            case Car.HVAC_SERVICE:
            case Car.INFO_SERVICE:
            case Car.PROPERTY_SERVICE:
            case Car.SENSOR_SERVICE:
            case Car.VENDOR_EXTENSION_SERVICE:
                return mCarPropertyService;
            case Car.CAR_NAVIGATION_SERVICE:
                assertNavigationManagerPermission(mContext);
                IInstrumentClusterNavigation navService =
                        mInstrumentClusterService.getNavigationService();
                return navService == null ? null : navService.asBinder();
            case Car.CAR_INSTRUMENT_CLUSTER_SERVICE:
                assertClusterManagerPermission(mContext);
                return mInstrumentClusterService.getManagerService();
            case Car.PROJECTION_SERVICE:
                return mCarProjectionService;
            case Car.VMS_SUBSCRIBER_SERVICE:
                assertVmsSubscriberPermission(mContext);
                return mVmsSubscriberService;
            case Car.TEST_SERVICE: {
                assertPermission(mContext, Car.PERMISSION_CAR_TEST_SERVICE);
                synchronized (this) {
                    if (mCarTestService == null) {
                        mCarTestService = new CarTestService(mContext, this);
                    }
                    return mCarTestService;
                }
            }
            case Car.BLUETOOTH_SERVICE:
                return mCarBluetoothService;
            case Car.STORAGE_MONITORING_SERVICE:
                assertPermission(mContext, Car.PERMISSION_STORAGE_MONITORING);
                return mCarStorageMonitoringService;
            case Car.CAR_DRIVING_STATE_SERVICE:
                assertDrivingStatePermission(mContext);
                return mCarDrivingStateService;
            case Car.CAR_UX_RESTRICTION_SERVICE:
                return mCarUXRestrictionsService;
            case Car.CAR_CONFIGURATION_SERVICE:
                return mCarConfigurationService;
            case Car.CAR_TRUST_AGENT_ENROLLMENT_SERVICE:
                assertTrustAgentEnrollmentPermission(mContext);
                return mCarTrustedDeviceService.getCarTrustAgentEnrollmentService();
            case Car.CAR_MEDIA_SERVICE:
                return mCarMediaService;
            case Car.CAR_BUGREPORT_SERVICE:
                return mCarBugreportManagerService;
            default:
                Log.w(CarLog.TAG_SERVICE, "getCarService for unknown service:" + serviceName);
                return null;
        }
    }

    @Override
    public int getCarConnectionType() {
        return Car.CONNECTION_TYPE_EMBEDDED;
    }

    public CarServiceBase getCarInternalService(String serviceName) {
        switch (serviceName) {
            case INTERNAL_INPUT_SERVICE:
                return mCarInputService;
            case INTERNAL_SYSTEM_ACTIVITY_MONITORING_SERVICE:
                return mSystemActivityMonitoringService;
            default:
                Log.w(CarLog.TAG_SERVICE, "getCarInternalService for unknown service:" +
                        serviceName);
                return null;
        }
    }

    public static void assertVehicleHalMockPermission(Context context) {
        assertPermission(context, Car.PERMISSION_MOCK_VEHICLE_HAL);
    }

    public static void assertNavigationManagerPermission(Context context) {
        assertPermission(context, Car.PERMISSION_CAR_NAVIGATION_MANAGER);
    }

    public static void assertClusterManagerPermission(Context context) {
        assertPermission(context, Car.PERMISSION_CAR_INSTRUMENT_CLUSTER_CONTROL);
    }

    public static void assertPowerPermission(Context context) {
        assertPermission(context, Car.PERMISSION_CAR_POWER);
    }

    public static void assertProjectionPermission(Context context) {
        assertPermission(context, Car.PERMISSION_CAR_PROJECTION);
    }

    /** Verify the calling context has the {@link Car#PERMISSION_CAR_PROJECTION_STATUS} */
    public static void assertProjectionStatusPermission(Context context) {
        assertPermission(context, Car.PERMISSION_CAR_PROJECTION_STATUS);
    }

    public static void assertAnyDiagnosticPermission(Context context) {
        assertAnyPermission(context,
                Car.PERMISSION_CAR_DIAGNOSTIC_READ_ALL,
                Car.PERMISSION_CAR_DIAGNOSTIC_CLEAR);
    }

    public static void assertDrivingStatePermission(Context context) {
        assertPermission(context, Car.PERMISSION_CAR_DRIVING_STATE);
    }

    public static void assertVmsPublisherPermission(Context context) {
        assertPermission(context, Car.PERMISSION_VMS_PUBLISHER);
    }

    public static void assertVmsSubscriberPermission(Context context) {
        assertPermission(context, Car.PERMISSION_VMS_SUBSCRIBER);
    }

    /**
     * Ensures the caller has the permission to enroll a Trust Agent.
     */
    public static void assertTrustAgentEnrollmentPermission(Context context) {
        assertPermission(context, Car.PERMISSION_CAR_ENROLL_TRUST);
    }

    public static void assertPermission(Context context, String permission) {
        if (context.checkCallingOrSelfPermission(permission) != PackageManager.PERMISSION_GRANTED) {
            throw new SecurityException("requires " + permission);
        }
    }

    /**
     * Checks to see if the caller has a permission.
     *
     * @return boolean TRUE if caller has the permission.
     */
    public static boolean hasPermission(Context context, String permission) {
        return context.checkCallingOrSelfPermission(permission)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static void assertAnyPermission(Context context, String... permissions) {
        for (String permission : permissions) {
            if (context.checkCallingOrSelfPermission(permission) ==
                    PackageManager.PERMISSION_GRANTED) {
                return;
            }
        }
        throw new SecurityException("requires any of " + Arrays.toString(permissions));
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        if (mContext.checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                != PackageManager.PERMISSION_GRANTED) {
            writer.println("Permission Denial: can't dump CarService from from pid="
                    + Binder.getCallingPid() + ", uid=" + Binder.getCallingUid()
                    + " without permission " + android.Manifest.permission.DUMP);
            return;
        }

        if (args == null || args.length == 0 || (args.length > 0 && "-a".equals(args[0]))) {
            writer.println("*Dump car service*");
            writer.println("*FutureConfig, DEFAULT:" + FeatureConfiguration.DEFAULT);
            writer.println("*Dump all services*");

            dumpAllServices(writer, false);

            writer.println("*Dump Vehicle HAL*");
            writer.println("Vehicle HAL Interface: " + mVehicleInterfaceName);
            try {
                // TODO dump all feature flags by creating a dumpable interface
                mHal.dump(writer);
            } catch (Exception e) {
                writer.println("Failed dumping: " + mHal.getClass().getName());
                e.printStackTrace(writer);
            }
        } else if ("--metrics".equals(args[0])) {
            writer.println("*Dump car service metrics*");
            dumpAllServices(writer, true);
        } else if (Build.IS_USERDEBUG || Build.IS_ENG) {
            execShellCmd(args, writer);
        } else {
            writer.println("Commands not supported in " + Build.TYPE);
        }
    }

    private void dumpAllServices(PrintWriter writer, boolean dumpMetricsOnly) {
        for (CarServiceBase service : mAllServices) {
            dumpService(service, writer, dumpMetricsOnly);
        }
        if (mCarTestService != null) {
            dumpService(mCarTestService, writer, dumpMetricsOnly);
        }

    }

    private void dumpService(CarServiceBase service, PrintWriter writer, boolean dumpMetricsOnly) {
        try {
            if (dumpMetricsOnly) {
                service.dumpMetrics(writer);
            } else {
                service.dump(writer);
            }
        } catch (Exception e) {
            writer.println("Failed dumping: " + service.getClass().getName());
            e.printStackTrace(writer);
        }
    }

    void execShellCmd(String[] args, PrintWriter writer) {
        new CarShellCommand().exec(args, writer);
    }

    @MainThread
    private void traceBegin(String name) {
        Slog.i(TAG, name);
        mBootTiming.traceBegin(name);
    }

    @MainThread
    private void traceEnd() {
        mBootTiming.traceEnd();
    }

    private class CarShellCommand {
        private static final String COMMAND_HELP = "-h";
        private static final String COMMAND_DAY_NIGHT_MODE = "day-night-mode";
        private static final String COMMAND_INJECT_VHAL_EVENT = "inject-vhal-event";
        private static final String COMMAND_INJECT_ERROR_EVENT = "inject-error-event";
        private static final String COMMAND_ENABLE_UXR = "enable-uxr";
        private static final String COMMAND_GARAGE_MODE = "garage-mode";
        private static final String COMMAND_GET_DO_ACTIVITIES = "get-do-activities";
        private static final String COMMAND_GET_CARPROPERTYCONFIG = "get-carpropertyconfig";
        private static final String COMMAND_GET_PROPERTY_VALUE = "get-property-value";
        private static final String COMMAND_PROJECTION_AP_TETHERING = "projection-tethering";
        private static final String COMMAND_PROJECTION_UI_MODE = "projection-ui-mode";
        private static final String COMMAND_RESUME = "resume";
        private static final String COMMAND_SUSPEND = "suspend";
        private static final String COMMAND_ENABLE_TRUSTED_DEVICE = "enable-trusted-device";
        private static final String COMMAND_REMOVE_TRUSTED_DEVICES = "remove-trusted-devices";

        private static final String PARAM_DAY_MODE = "day";
        private static final String PARAM_NIGHT_MODE = "night";
        private static final String PARAM_SENSOR_MODE = "sensor";
        private static final String PARAM_VEHICLE_PROPERTY_AREA_GLOBAL = "0";
        private static final String PARAM_ON_MODE = "on";
        private static final String PARAM_OFF_MODE = "off";
        private static final String PARAM_QUERY_MODE = "query";


        private void dumpHelp(PrintWriter pw) {
            pw.println("Car service commands:");
            pw.println("\t-h");
            pw.println("\t  Print this help text.");
            pw.println("\tday-night-mode [day|night|sensor]");
            pw.println("\t  Force into day/night mode or restore to auto.");
            pw.println("\tinject-vhal-event property [zone] data(can be comma separated list)");
            pw.println("\t  Inject a vehicle property for testing.");
            pw.println("\tinject-error-event property zone errorCode");
            pw.println("\t  Inject an error event from VHAL for testing.");
            pw.println("\tenable-uxr true|false");
            pw.println("\t  Enable/Disable UX restrictions and App blocking.");
            pw.println("\tgarage-mode [on|off|query]");
            pw.println("\t  Force into garage mode or check status.");
            pw.println("\tget-do-activities pkgname");
            pw.println("\t  Get Distraction Optimized activities in given package.");
            pw.println("\tget-carpropertyconfig [propertyId]");
            pw.println("\t  Get a CarPropertyConfig by Id in Hex or list all CarPropertyConfigs");
            pw.println("\tget-property-value [propertyId] [areaId]");
            pw.println("\t  Get a vehicle property value by property id in Hex and areaId");
            pw.println("\t  or list all property values for all areaId");
            pw.println("\tsuspend");
            pw.println("\t  Suspend the system to Deep Sleep.");
            pw.println("\tresume");
            pw.println("\t  Wake the system up after a 'suspend.'");
            pw.println("\tenable-trusted-device true|false");
            pw.println("\t  Enable/Disable Trusted device feature.");
            pw.println("\tremove-trusted-devices");
            pw.println("\t  Remove all trusted devices for the current foreground user.");
            pw.println("\tprojection-tethering [true|false]");
            pw.println("\t  Whether tethering should be used when creating access point for"
                    + " wireless projection");
            pw.println("\t--metrics");
            pw.println("\t  When used with dumpsys, only metrics will be in the dumpsys output.");
        }

        public void exec(String[] args, PrintWriter writer) {
            String arg = args[0];
            switch (arg) {
                case COMMAND_HELP:
                    dumpHelp(writer);
                    break;
                case COMMAND_DAY_NIGHT_MODE: {
                    String value = args.length < 2 ? "" : args[1];
                    forceDayNightMode(value, writer);
                    break;
                }
                case COMMAND_GARAGE_MODE: {
                    String value = args.length < 2 ? "" : args[1];
                    forceGarageMode(value, writer);
                    break;
                }
                case COMMAND_INJECT_VHAL_EVENT:
                    String zone = PARAM_VEHICLE_PROPERTY_AREA_GLOBAL;
                    String data;
                    if (args.length != 3 && args.length != 4) {
                        writer.println("Incorrect number of arguments.");
                        dumpHelp(writer);
                        break;
                    } else if (args.length == 4) {
                        // Zoned
                        zone = args[2];
                        data = args[3];
                    } else {
                        // Global
                        data = args[2];
                    }
                    injectVhalEvent(args[1], zone, data, false, writer);
                    break;
                case COMMAND_INJECT_ERROR_EVENT:
                    if (args.length != 4) {
                        writer.println("Incorrect number of arguments");
                        dumpHelp(writer);
                        break;
                    }
                    String errorAreaId = args[2];
                    String errorCode = args[3];
                    injectVhalEvent(args[1], errorAreaId, errorCode, true, writer);
                    break;
                case COMMAND_ENABLE_UXR:
                    if (args.length != 2) {
                        writer.println("Incorrect number of arguments");
                        dumpHelp(writer);
                        break;
                    }
                    boolean enableBlocking = Boolean.valueOf(args[1]);
                    if (mCarPackageManagerService != null) {
                        mCarPackageManagerService.setEnableActivityBlocking(enableBlocking);
                    }
                    break;
                case COMMAND_GET_DO_ACTIVITIES:
                    if (args.length != 2) {
                        writer.println("Incorrect number of arguments");
                        dumpHelp(writer);
                        break;
                    }
                    String pkgName = args[1].toLowerCase();
                    if (mCarPackageManagerService != null) {
                        String[] doActivities =
                                mCarPackageManagerService.getDistractionOptimizedActivities(
                                        pkgName);
                        if (doActivities != null) {
                            writer.println("DO Activities for " + pkgName);
                            for (String a : doActivities) {
                                writer.println(a);
                            }
                        } else {
                            writer.println("No DO Activities for " + pkgName);
                        }
                    }
                    break;
                case COMMAND_GET_CARPROPERTYCONFIG:
                    String propertyId = args.length < 2 ? "" : args[1];
                    mHal.dumpPropertyConfigs(writer, propertyId);
                    break;
                case COMMAND_GET_PROPERTY_VALUE:
                    String propId = args.length < 2 ? "" : args[1];
                    String areaId = args.length < 3 ? "" : args[2];
                    mHal.dumpPropertyValueByCommend(writer, propId, areaId);
                    break;
                case COMMAND_PROJECTION_UI_MODE:
                    if (args.length != 2) {
                        writer.println("Incorrect number of arguments");
                        dumpHelp(writer);
                        break;
                    }
                    mCarProjectionService.setUiMode(Integer.valueOf(args[1]));
                    break;
                case COMMAND_PROJECTION_AP_TETHERING:
                    if (args.length != 2) {
                        writer.println("Incorrect number of arguments");
                        dumpHelp(writer);
                        break;
                    }
                    mCarProjectionService.setAccessPointTethering(Boolean.valueOf(args[1]));
                    break;
                case COMMAND_RESUME:
                    mCarPowerManagementService.forceSimulatedResume();
                    writer.println("Resume: Simulating resuming from Deep Sleep");
                    break;
                case COMMAND_SUSPEND:
                    mCarPowerManagementService.forceSimulatedSuspend();
                    writer.println("Resume: Simulating powering down to Deep Sleep");
                    break;
                case COMMAND_ENABLE_TRUSTED_DEVICE:
                    if (args.length != 2) {
                        writer.println("Incorrect number of arguments");
                        dumpHelp(writer);
                        break;
                    }
                    mCarTrustedDeviceService.getCarTrustAgentEnrollmentService()
                            .setTrustedDeviceEnrollmentEnabled(Boolean.valueOf(args[1]));
                    mCarTrustedDeviceService.getCarTrustAgentUnlockService()
                            .setTrustedDeviceUnlockEnabled(Boolean.valueOf(args[1]));
                    break;
                case COMMAND_REMOVE_TRUSTED_DEVICES:
                    mCarTrustedDeviceService.getCarTrustAgentEnrollmentService()
                            .removeAllTrustedDevices(
                                    mUserManagerHelper.getCurrentForegroundUserId());
                    break;
                default:
                    writer.println("Unknown command: \"" + arg + "\"");
                    dumpHelp(writer);
            }
        }

        private void forceDayNightMode(String arg, PrintWriter writer) {
            int mode;
            switch (arg) {
                case PARAM_DAY_MODE:
                    mode = CarNightService.FORCED_DAY_MODE;
                    break;
                case PARAM_NIGHT_MODE:
                    mode = CarNightService.FORCED_NIGHT_MODE;
                    break;
                case PARAM_SENSOR_MODE:
                    mode = CarNightService.FORCED_SENSOR_MODE;
                    break;
                default:
                    writer.println("Unknown value. Valid argument: " + PARAM_DAY_MODE + "|"
                            + PARAM_NIGHT_MODE + "|" + PARAM_SENSOR_MODE);
                    return;
            }
            int current = mCarNightService.forceDayNightMode(mode);
            String currentMode = null;
            switch (current) {
                case UiModeManager.MODE_NIGHT_AUTO:
                    currentMode = PARAM_SENSOR_MODE;
                    break;
                case UiModeManager.MODE_NIGHT_YES:
                    currentMode = PARAM_NIGHT_MODE;
                    break;
                case UiModeManager.MODE_NIGHT_NO:
                    currentMode = PARAM_DAY_MODE;
                    break;
            }
            writer.println("DayNightMode changed to: " + currentMode);
        }

        private void forceGarageMode(String arg, PrintWriter writer) {
            switch (arg) {
                case PARAM_ON_MODE:
                    mGarageModeService.forceStartGarageMode();
                    writer.println("Garage mode: " + mGarageModeService.isGarageModeActive());
                    break;
                case PARAM_OFF_MODE:
                    mGarageModeService.stopAndResetGarageMode();
                    writer.println("Garage mode: " + mGarageModeService.isGarageModeActive());
                    break;
                case PARAM_QUERY_MODE:
                    mGarageModeService.dump(writer);
                    break;
                default:
                    writer.println("Unknown value. Valid argument: " + PARAM_ON_MODE + "|"
                            + PARAM_OFF_MODE + "|" + PARAM_QUERY_MODE);
            }
        }

        /**
         * Inject a fake  VHAL event
         *
         * @param property the Vehicle property Id as defined in the HAL
         * @param zone     Zone that this event services
         * @param isErrorEvent indicates the type of event
         * @param value    Data value of the event
         * @param writer   PrintWriter
         */
        private void injectVhalEvent(String property, String zone, String value,
                boolean isErrorEvent, PrintWriter writer) {
            if (zone != null && (zone.equalsIgnoreCase(PARAM_VEHICLE_PROPERTY_AREA_GLOBAL))) {
                if (!isPropertyAreaTypeGlobal(property)) {
                    writer.println("Property area type inconsistent with given zone");
                    return;
                }
            }
            try {
                if (isErrorEvent) {
                    mHal.injectOnPropertySetError(property, zone, value);
                } else {
                    mHal.injectVhalEvent(property, zone, value);
                }
            } catch (NumberFormatException e) {
                writer.println("Invalid property Id zone Id or value" + e);
                dumpHelp(writer);
            }
        }

        // Check if the given property is global
        private boolean isPropertyAreaTypeGlobal(String property) {
            if (property == null) {
                return false;
            }
            return (Integer.decode(property) & VehicleArea.MASK) == VehicleArea.GLOBAL;
        }
    }
}
