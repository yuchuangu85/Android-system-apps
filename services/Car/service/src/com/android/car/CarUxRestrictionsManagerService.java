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

import static android.car.drivingstate.CarDrivingStateEvent.DRIVING_STATE_IDLING;
import static android.car.drivingstate.CarDrivingStateEvent.DRIVING_STATE_MOVING;
import static android.car.drivingstate.CarDrivingStateEvent.DRIVING_STATE_PARKED;
import static android.car.drivingstate.CarDrivingStateEvent.DRIVING_STATE_UNKNOWN;
import static android.car.drivingstate.CarUxRestrictionsManager.UX_RESTRICTION_MODE_BASELINE;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.car.Car;
import android.car.drivingstate.CarDrivingStateEvent;
import android.car.drivingstate.CarDrivingStateEvent.CarDrivingState;
import android.car.drivingstate.CarUxRestrictions;
import android.car.drivingstate.CarUxRestrictionsConfiguration;
import android.car.drivingstate.CarUxRestrictionsManager;
import android.car.drivingstate.ICarDrivingStateChangeListener;
import android.car.drivingstate.ICarUxRestrictionsChangeListener;
import android.car.drivingstate.ICarUxRestrictionsManager;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyEvent;
import android.car.hardware.property.ICarPropertyEventListener;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.automotive.vehicle.V2_0.VehicleProperty;
import android.hardware.display.DisplayManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.ArraySet;
import android.util.AtomicFile;
import android.util.JsonReader;
import android.util.JsonWriter;
import android.util.Log;
import android.util.Slog;
import android.view.Display;
import android.view.DisplayAddress;

import com.android.car.systeminterface.SystemInterface;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A service that listens to current driving state of the vehicle and maps it to the
 * appropriate UX restrictions for that driving state.
 * <p>
 * <h1>UX Restrictions Configuration</h1>
 * When this service starts, it will first try reading the configuration set through
 * {@link #saveUxRestrictionsConfigurationForNextBoot(CarUxRestrictionsConfiguration)}.
 * If one is not available, it will try reading the configuration saved in
 * {@code R.xml.car_ux_restrictions_map}. If XML is somehow unavailable, it will
 * fall back to a hard-coded configuration.
 * <p>
 * <h1>Multi-Display</h1>
 * Only physical displays that are available at service initialization are recognized.
 * This service does not support pluggable displays.
 */
public class CarUxRestrictionsManagerService extends ICarUxRestrictionsManager.Stub implements
        CarServiceBase {
    private static final String TAG = "CarUxR";
    private static final boolean DBG = false;
    private static final int MAX_TRANSITION_LOG_SIZE = 20;
    private static final int PROPERTY_UPDATE_RATE = 5; // Update rate in Hz
    private static final float SPEED_NOT_AVAILABLE = -1.0F;
    private static final byte DEFAULT_PORT = 0;

    @VisibleForTesting
    static final String CONFIG_FILENAME_PRODUCTION = "ux_restrictions_prod_config.json";
    @VisibleForTesting
    static final String CONFIG_FILENAME_STAGED = "ux_restrictions_staged_config.json";

    private final Context mContext;
    private final DisplayManager mDisplayManager;
    private final CarDrivingStateService mDrivingStateService;
    private final CarPropertyService mCarPropertyService;
    // List of clients listening to UX restriction events.
    private final List<UxRestrictionsClient> mUxRClients = new ArrayList<>();

    private float mCurrentMovingSpeed;

    // Byte represents a physical port for display.
    private byte mDefaultDisplayPhysicalPort;
    private final List<Byte> mPhysicalPorts = new ArrayList<>();
    // This lookup caches the mapping from an int display id
    // to a byte that represents a physical port.
    private final Map<Integer, Byte> mPortLookup = new HashMap<>();
    private Map<Byte, CarUxRestrictionsConfiguration> mCarUxRestrictionsConfigurations;
    private Map<Byte, CarUxRestrictions> mCurrentUxRestrictions;

    @CarUxRestrictionsManager.UxRestrictionMode
    private int mRestrictionMode = UX_RESTRICTION_MODE_BASELINE;

    // Flag to disable broadcasting UXR changes - for development purposes
    @GuardedBy("this")
    private boolean mUxRChangeBroadcastEnabled = true;
    // For dumpsys logging
    private final LinkedList<Utils.TransitionLog> mTransitionLogs = new LinkedList<>();

    public CarUxRestrictionsManagerService(Context context, CarDrivingStateService drvService,
            CarPropertyService propertyService) {
        mContext = context;
        mDisplayManager = mContext.getSystemService(DisplayManager.class);
        mDrivingStateService = drvService;
        mCarPropertyService = propertyService;
    }

    @Override
    public synchronized void init() {
        mDefaultDisplayPhysicalPort = getDefaultDisplayPhysicalPort();

        initPhysicalPort();

        // Unrestricted until driving state information is received. During boot up, we don't want
        // everything to be blocked until data is available from CarPropertyManager.  If we start
        // driving and we don't get speed or gear information, we have bigger problems.
        mCurrentUxRestrictions = new HashMap<>();
        for (byte port : mPhysicalPorts) {
            mCurrentUxRestrictions.put(port, createUnrestrictedRestrictions());
        }

        // subscribe to driving State
        mDrivingStateService.registerDrivingStateChangeListener(
                mICarDrivingStateChangeEventListener);
        // subscribe to property service for speed
        mCarPropertyService.registerListener(VehicleProperty.PERF_VEHICLE_SPEED,
                PROPERTY_UPDATE_RATE, mICarPropertyEventListener);

        // At this point the driving state is known, which determines whether it's safe
        // to promote staged new config.
        mCarUxRestrictionsConfigurations = convertToMap(loadConfig());

        initializeUxRestrictions();
    }

    @Override
    public List<CarUxRestrictionsConfiguration> getConfigs() {
        return new ArrayList<>(mCarUxRestrictionsConfigurations.values());
    }

    /**
     * Loads UX restrictions configurations and returns them.
     *
     * <p>Reads config from the following sources in order:
     * <ol>
     * <li>saved config set by
     * {@link #saveUxRestrictionsConfigurationForNextBoot(CarUxRestrictionsConfiguration)};
     * <li>XML resource config from {@code R.xml.car_ux_restrictions_map};
     * <li>hardcoded default config.
     * </ol>
     *
     * This method attempts to promote staged config file. Doing which depends on driving state.
     */
    @VisibleForTesting
    synchronized List<CarUxRestrictionsConfiguration> loadConfig() {
        promoteStagedConfig();
        List<CarUxRestrictionsConfiguration> configs;

        // Production config, if available, is the first choice.
        File prodConfig = getFile(CONFIG_FILENAME_PRODUCTION);
        if (prodConfig.exists()) {
            logd("Attempting to read production config");
            configs = readPersistedConfig(prodConfig);
            if (configs != null) {
                return configs;
            }
        }

        // XML config is the second choice.
        logd("Attempting to read config from XML resource");
        configs = readXmlConfig();
        if (configs != null) {
            return configs;
        }

        // This should rarely happen.
        Log.w(TAG, "Creating default config");

        configs = new ArrayList<>();
        for (byte port : mPhysicalPorts) {
            configs.add(createDefaultConfig(port));
        }
        return configs;
    }

    private File getFile(String filename) {
        SystemInterface systemInterface = CarLocalServices.getService(SystemInterface.class);
        return new File(systemInterface.getSystemCarDir(), filename);
    }

    @Nullable
    private List<CarUxRestrictionsConfiguration> readXmlConfig() {
        try {
            return CarUxRestrictionsConfigurationXmlParser.parse(
                    mContext, R.xml.car_ux_restrictions_map);
        } catch (IOException | XmlPullParserException e) {
            Log.e(TAG, "Could not read config from XML resource", e);
        }
        return null;
    }

    private void promoteStagedConfig() {
        Path stagedConfig = getFile(CONFIG_FILENAME_STAGED).toPath();

        CarDrivingStateEvent currentDrivingStateEvent =
                mDrivingStateService.getCurrentDrivingState();
        // Only promote staged config when car is parked.
        if (currentDrivingStateEvent != null
                && currentDrivingStateEvent.eventValue == DRIVING_STATE_PARKED
                && Files.exists(stagedConfig)) {

            Path prod = getFile(CONFIG_FILENAME_PRODUCTION).toPath();
            try {
                logd("Attempting to promote stage config");
                Files.move(stagedConfig, prod, REPLACE_EXISTING);
            } catch (IOException e) {
                Log.e(TAG, "Could not promote state config", e);
            }
        }
    }

    // Update current restrictions by getting the current driving state and speed.
    private void initializeUxRestrictions() {
        CarDrivingStateEvent currentDrivingStateEvent =
                mDrivingStateService.getCurrentDrivingState();
        // if we don't have enough information from the CarPropertyService to compute the UX
        // restrictions, then leave the UX restrictions unchanged from what it was initialized to
        // in the constructor.
        if (currentDrivingStateEvent == null
                || currentDrivingStateEvent.eventValue == DRIVING_STATE_UNKNOWN) {
            return;
        }
        int currentDrivingState = currentDrivingStateEvent.eventValue;
        Float currentSpeed = getCurrentSpeed();
        if (currentSpeed == SPEED_NOT_AVAILABLE) {
            return;
        }
        // At this point the underlying CarPropertyService has provided us enough information to
        // compute the UX restrictions that could be potentially different from the initial UX
        // restrictions.
        handleDispatchUxRestrictions(currentDrivingState, currentSpeed);
    }

    private Float getCurrentSpeed() {
        CarPropertyValue value = mCarPropertyService.getProperty(VehicleProperty.PERF_VEHICLE_SPEED,
                0);
        if (value != null) {
            return (Float) value.getValue();
        }
        return SPEED_NOT_AVAILABLE;
    }

    @Override
    public synchronized void release() {
        for (UxRestrictionsClient client : mUxRClients) {
            client.listenerBinder.unlinkToDeath(client, 0);
        }
        mUxRClients.clear();
        mDrivingStateService.unregisterDrivingStateChangeListener(
                mICarDrivingStateChangeEventListener);
    }

    // Binder methods

    /**
     * Registers a {@link ICarUxRestrictionsChangeListener} to be notified for changes to the UX
     * restrictions.
     *
     * @param listener  Listener to register
     * @param displayId UX restrictions on this display will be notified.
     */
    @Override
    public synchronized void registerUxRestrictionsChangeListener(
            ICarUxRestrictionsChangeListener listener, int displayId) {
        if (listener == null) {
            Log.e(TAG, "registerUxRestrictionsChangeListener(): listener null");
            throw new IllegalArgumentException("Listener is null");
        }
        // If a new client is registering, create a new DrivingStateClient and add it to the list
        // of listening clients.
        UxRestrictionsClient client = findUxRestrictionsClient(listener);
        if (client == null) {
            client = new UxRestrictionsClient(listener, displayId);
            try {
                listener.asBinder().linkToDeath(client, 0);
            } catch (RemoteException e) {
                Log.e(TAG, "Cannot link death recipient to binder " + e);
            }
            mUxRClients.add(client);
        }
        return;
    }

    /**
     * Iterates through the list of registered UX Restrictions clients -
     * {@link UxRestrictionsClient} and finds if the given client is already registered.
     *
     * @param listener Listener to look for.
     * @return the {@link UxRestrictionsClient} if found, null if not
     */
    @Nullable
    private UxRestrictionsClient findUxRestrictionsClient(
            ICarUxRestrictionsChangeListener listener) {
        IBinder binder = listener.asBinder();
        for (UxRestrictionsClient client : mUxRClients) {
            if (client.isHoldingBinder(binder)) {
                return client;
            }
        }
        return null;
    }

    /**
     * Unregister the given UX Restrictions listener
     *
     * @param listener client to unregister
     */
    @Override
    public synchronized void unregisterUxRestrictionsChangeListener(
            ICarUxRestrictionsChangeListener listener) {
        if (listener == null) {
            Log.e(TAG, "unregisterUxRestrictionsChangeListener(): listener null");
            throw new IllegalArgumentException("Listener is null");
        }

        UxRestrictionsClient client = findUxRestrictionsClient(listener);
        if (client == null) {
            Log.e(TAG, "unregisterUxRestrictionsChangeListener(): listener was not previously "
                    + "registered");
            return;
        }
        listener.asBinder().unlinkToDeath(client, 0);
        mUxRClients.remove(client);
    }

    /**
     * Gets the current UX restrictions for a display.
     *
     * @param displayId UX restrictions on this display will be returned.
     */
    @Override
    public synchronized CarUxRestrictions getCurrentUxRestrictions(int displayId) {
        CarUxRestrictions restrictions = mCurrentUxRestrictions.get(getPhysicalPort(displayId));
        if (restrictions == null) {
            Log.e(TAG, String.format(
                    "Restrictions are null for displayId:%d. Returning full restrictions.",
                    displayId));
            restrictions = createFullyRestrictedRestrictions();
        }
        return restrictions;
    }

    /**
     * Convenience method to retrieve restrictions for default display.
     */
    @Nullable
    public synchronized CarUxRestrictions getCurrentUxRestrictions() {
        return getCurrentUxRestrictions(Display.DEFAULT_DISPLAY);
    }

    @Override
    public synchronized boolean saveUxRestrictionsConfigurationForNextBoot(
            List<CarUxRestrictionsConfiguration> configs) {
        ICarImpl.assertPermission(mContext, Car.PERMISSION_CAR_UX_RESTRICTIONS_CONFIGURATION);

        validateConfigs(configs);

        return persistConfig(configs, CONFIG_FILENAME_STAGED);
    }

    @Override
    @Nullable
    public List<CarUxRestrictionsConfiguration> getStagedConfigs() {
        File stagedConfig = getFile(CONFIG_FILENAME_STAGED);
        if (stagedConfig.exists()) {
            logd("Attempting to read staged config");
            return readPersistedConfig(stagedConfig);
        } else {
            return null;
        }
    }

    /**
     * Sets the restriction mode to use. Restriction mode allows a different set of restrictions to
     * be applied in the same driving state. Restrictions for each mode can be configured through
     * {@link CarUxRestrictionsConfiguration}.
     *
     * <p>Defaults to {@link CarUxRestrictionsManager#UX_RESTRICTION_MODE_BASELINE}.
     *
     * @param mode See values in {@link CarUxRestrictionsManager.UxRestrictionMode}.
     * @return {@code true} if mode was successfully changed; {@code false} otherwise.
     * @see CarUxRestrictionsConfiguration.DrivingStateRestrictions
     * @see CarUxRestrictionsConfiguration.Builder
     */
    @Override
    public synchronized boolean setRestrictionMode(
            @CarUxRestrictionsManager.UxRestrictionMode int mode) {
        if (mRestrictionMode == mode) {
            return true;
        }

        addTransitionLog(TAG, mRestrictionMode, mode, System.currentTimeMillis(),
                "Restriction mode");
        mRestrictionMode = mode;
        logd("Set restriction mode to: " + CarUxRestrictionsManager.modeToString(mode));

        handleDispatchUxRestrictions(
                mDrivingStateService.getCurrentDrivingState().eventValue, getCurrentSpeed());
        return true;
    }

    @Override
    @CarUxRestrictionsManager.UxRestrictionMode
    public synchronized int getRestrictionMode() {
        return mRestrictionMode;
    }

    /**
     * Writes configuration into the specified file.
     *
     * IO access on file is not thread safe. Caller should ensure threading protection.
     */
    private boolean persistConfig(List<CarUxRestrictionsConfiguration> configs, String filename) {
        File file = getFile(filename);
        AtomicFile stagedFile = new AtomicFile(file);
        FileOutputStream fos;
        try {
            fos = stagedFile.startWrite();
        } catch (IOException e) {
            Log.e(TAG, "Could not open file to persist config", e);
            return false;
        }
        try (JsonWriter jsonWriter = new JsonWriter(
                new OutputStreamWriter(fos, StandardCharsets.UTF_8))) {
            jsonWriter.beginArray();
            for (CarUxRestrictionsConfiguration config : configs) {
                config.writeJson(jsonWriter);
            }
            jsonWriter.endArray();
        } catch (IOException e) {
            Log.e(TAG, "Could not persist config", e);
            stagedFile.failWrite(fos);
            return false;
        }
        stagedFile.finishWrite(fos);
        return true;
    }

    @Nullable
    private List<CarUxRestrictionsConfiguration> readPersistedConfig(File file) {
        if (!file.exists()) {
            Log.e(TAG, "Could not find config file: " + file.getName());
            return null;
        }

        AtomicFile configFile = new AtomicFile(file);
        try (JsonReader reader = new JsonReader(
                new InputStreamReader(configFile.openRead(), StandardCharsets.UTF_8))) {
            List<CarUxRestrictionsConfiguration> configs = new ArrayList<>();
            reader.beginArray();
            while (reader.hasNext()) {
                configs.add(CarUxRestrictionsConfiguration.readJson(reader));
            }
            reader.endArray();
            return configs;
        } catch (IOException e) {
            Log.e(TAG, "Could not read persisted config file " + file.getName(), e);
        }
        return null;
    }

    /**
     * Enable/disable UX restrictions change broadcast blocking.
     * Setting this to true will stop broadcasts of UX restriction change to listeners.
     * This method works only on debug builds and the caller of this method needs to have the same
     * signature of the car service.
     */
    public synchronized void setUxRChangeBroadcastEnabled(boolean enable) {
        if (!isDebugBuild()) {
            Log.e(TAG, "Cannot set UX restriction change broadcast.");
            return;
        }
        // Check if the caller has the same signature as that of the car service.
        if (mContext.getPackageManager().checkSignatures(Process.myUid(), Binder.getCallingUid())
                != PackageManager.SIGNATURE_MATCH) {
            throw new SecurityException(
                    "Caller " + mContext.getPackageManager().getNameForUid(Binder.getCallingUid())
                            + " does not have the right signature");
        }
        if (enable) {
            // if enabling it back, send the current restrictions
            mUxRChangeBroadcastEnabled = enable;
            handleDispatchUxRestrictions(mDrivingStateService.getCurrentDrivingState().eventValue,
                    getCurrentSpeed());
        } else {
            // fake parked state, so if the system is currently restricted, the restrictions are
            // relaxed.
            handleDispatchUxRestrictions(DRIVING_STATE_PARKED, 0);
            mUxRChangeBroadcastEnabled = enable;
        }
    }

    private boolean isDebugBuild() {
        return Build.IS_USERDEBUG || Build.IS_ENG;
    }

    /**
     * Class that holds onto client related information - listener interface, process that hosts the
     * binder object etc.
     * It also registers for death notifications of the host.
     */
    private class UxRestrictionsClient implements IBinder.DeathRecipient {
        private final IBinder listenerBinder;
        private final ICarUxRestrictionsChangeListener listener;
        private final int mDisplayId;

        UxRestrictionsClient(ICarUxRestrictionsChangeListener l, int displayId) {
            listener = l;
            listenerBinder = l.asBinder();
            mDisplayId = displayId;
        }

        @Override
        public void binderDied() {
            logd("Binder died " + listenerBinder);
            listenerBinder.unlinkToDeath(this, 0);
            synchronized (CarUxRestrictionsManagerService.this) {
                mUxRClients.remove(this);
            }
        }

        /**
         * Returns if the given binder object matches to what this client info holds.
         * Used to check if the listener asking to be registered is already registered.
         *
         * @return true if matches, false if not
         */
        public boolean isHoldingBinder(IBinder binder) {
            return listenerBinder == binder;
        }

        /**
         * Dispatch the event to the listener
         *
         * @param event {@link CarUxRestrictions}.
         */
        public void dispatchEventToClients(CarUxRestrictions event) {
            if (event == null) {
                return;
            }
            try {
                listener.onUxRestrictionsChanged(event);
            } catch (RemoteException e) {
                Log.e(TAG, "Dispatch to listener failed", e);
            }
        }
    }

    @Override
    public void dump(PrintWriter writer) {
        writer.println("*CarUxRestrictionsManagerService*");
        for (byte port : mCurrentUxRestrictions.keySet()) {
            CarUxRestrictions restrictions = mCurrentUxRestrictions.get(port);
            writer.printf("Port: 0x%02X UXR: %s\n", port, restrictions.toString());
        }
        if (isDebugBuild()) {
            writer.println("mUxRChangeBroadcastEnabled? " + mUxRChangeBroadcastEnabled);
        }

        writer.println("UX Restriction configurations:");
        for (CarUxRestrictionsConfiguration config : mCarUxRestrictionsConfigurations.values()) {
            config.dump(writer);
        }
        writer.println("UX Restriction change log:");
        for (Utils.TransitionLog tlog : mTransitionLogs) {
            writer.println(tlog);
        }
    }

    /**
     * {@link CarDrivingStateEvent} listener registered with the {@link CarDrivingStateService}
     * for getting driving state change notifications.
     */
    private final ICarDrivingStateChangeListener mICarDrivingStateChangeEventListener =
            new ICarDrivingStateChangeListener.Stub() {
                @Override
                public void onDrivingStateChanged(CarDrivingStateEvent event) {
                    logd("Driving State Changed:" + event.eventValue);
                    handleDrivingStateEvent(event);
                }
            };

    /**
     * Handle the driving state change events coming from the {@link CarDrivingStateService}.
     * Map the driving state to the corresponding UX Restrictions and dispatch the
     * UX Restriction change to the registered clients.
     */
    private synchronized void handleDrivingStateEvent(CarDrivingStateEvent event) {
        if (event == null) {
            return;
        }
        int drivingState = event.eventValue;
        Float speed = getCurrentSpeed();

        if (speed != SPEED_NOT_AVAILABLE) {
            mCurrentMovingSpeed = speed;
        } else if (drivingState == DRIVING_STATE_PARKED
                || drivingState == DRIVING_STATE_UNKNOWN) {
            // If speed is unavailable, but the driving state is parked or unknown, it can still be
            // handled.
            logd("Speed null when driving state is: " + drivingState);
            mCurrentMovingSpeed = 0;
        } else {
            // If we get here with driving state != parked or unknown && speed == null,
            // something is wrong.  CarDrivingStateService could not have inferred idling or moving
            // when speed is not available
            Log.e(TAG, "Unexpected:  Speed null when driving state is: " + drivingState);
            return;
        }
        handleDispatchUxRestrictions(drivingState, mCurrentMovingSpeed);
    }

    /**
     * {@link CarPropertyEvent} listener registered with the {@link CarPropertyService} for getting
     * speed change notifications.
     */
    private final ICarPropertyEventListener mICarPropertyEventListener =
            new ICarPropertyEventListener.Stub() {
                @Override
                public void onEvent(List<CarPropertyEvent> events) throws RemoteException {
                    for (CarPropertyEvent event : events) {
                        if ((event.getEventType()
                                == CarPropertyEvent.PROPERTY_EVENT_PROPERTY_CHANGE)
                                && (event.getCarPropertyValue().getPropertyId()
                                == VehicleProperty.PERF_VEHICLE_SPEED)) {
                            handleSpeedChange((Float) event.getCarPropertyValue().getValue());
                        }
                    }
                }
            };

    private synchronized void handleSpeedChange(float newSpeed) {
        if (newSpeed == mCurrentMovingSpeed) {
            // Ignore if speed hasn't changed
            return;
        }
        int currentDrivingState = mDrivingStateService.getCurrentDrivingState().eventValue;
        if (currentDrivingState != DRIVING_STATE_MOVING) {
            // Ignore speed changes if the vehicle is not moving
            return;
        }
        mCurrentMovingSpeed = newSpeed;
        handleDispatchUxRestrictions(currentDrivingState, newSpeed);
    }

    /**
     * Handle dispatching UX restrictions change.
     *
     * @param currentDrivingState driving state of the vehicle
     * @param speed               speed of the vehicle
     */
    private synchronized void handleDispatchUxRestrictions(@CarDrivingState int currentDrivingState,
            float speed) {
        if (isDebugBuild() && !mUxRChangeBroadcastEnabled) {
            Log.d(TAG, "Not dispatching UX Restriction due to setting");
            return;
        }

        Map<Byte, CarUxRestrictions> newUxRestrictions = new HashMap<>();
        for (byte port : mPhysicalPorts) {
            CarUxRestrictionsConfiguration config = mCarUxRestrictionsConfigurations.get(port);
            if (config == null) {
                continue;
            }

            CarUxRestrictions uxRestrictions = config.getUxRestrictions(
                    currentDrivingState, speed, mRestrictionMode);
            logd(String.format("Display port 0x%02x\tDO old->new: %b -> %b",
                    port,
                    mCurrentUxRestrictions.get(port).isRequiresDistractionOptimization(),
                    uxRestrictions.isRequiresDistractionOptimization()));
            logd(String.format("Display port 0x%02x\tUxR old->new: 0x%x -> 0x%x",
                    port,
                    mCurrentUxRestrictions.get(port).getActiveRestrictions(),
                    uxRestrictions.getActiveRestrictions()));
            newUxRestrictions.put(port, uxRestrictions);
        }

        // Ignore dispatching if the restrictions has not changed.
        Set<Byte> displayToDispatch = new ArraySet<>();
        for (byte port : newUxRestrictions.keySet()) {
            if (!mCurrentUxRestrictions.containsKey(port)) {
                // This should never happen.
                Log.wtf(TAG, "Unrecognized port:" + port);
                continue;
            }
            CarUxRestrictions uxRestrictions = newUxRestrictions.get(port);
            if (!mCurrentUxRestrictions.get(port).isSameRestrictions(uxRestrictions)) {
                displayToDispatch.add(port);
            }
        }
        if (displayToDispatch.isEmpty()) {
            return;
        }

        for (byte port : displayToDispatch) {
            addTransitionLog(
                    mCurrentUxRestrictions.get(port), newUxRestrictions.get(port));
        }

        logd("dispatching to clients");
        for (UxRestrictionsClient client : mUxRClients) {
            Byte clientDisplayPort = getPhysicalPort(client.mDisplayId);
            if (clientDisplayPort == null) {
                clientDisplayPort = mDefaultDisplayPhysicalPort;
            }
            if (displayToDispatch.contains(clientDisplayPort)) {
                client.dispatchEventToClients(newUxRestrictions.get(clientDisplayPort));
            }
        }

        mCurrentUxRestrictions = newUxRestrictions;
    }

    private byte getDefaultDisplayPhysicalPort() {
        Display defaultDisplay = mDisplayManager.getDisplay(Display.DEFAULT_DISPLAY);
        DisplayAddress.Physical address = (DisplayAddress.Physical) defaultDisplay.getAddress();

        if (address == null) {
            Log.w(TAG, "Default display does not have physical display port.");
            return DEFAULT_PORT;
        }
        return address.getPort();
    }

    private void initPhysicalPort() {
        for (Display display : mDisplayManager.getDisplays()) {
            if (display.getType() == Display.TYPE_VIRTUAL) {
                continue;
            }

            if (display.getDisplayId() == Display.DEFAULT_DISPLAY && display.getAddress() == null) {
                // Assume default display is a physical display so assign an address if it
                // does not have one (possibly due to lower graphic driver version).
                if (Log.isLoggable(TAG, Log.INFO)) {
                    Log.i(TAG, "Default display does not have display address. Using default.");
                }
                mPhysicalPorts.add(mDefaultDisplayPhysicalPort);
            } else if (display.getAddress() instanceof DisplayAddress.Physical) {
                byte port = ((DisplayAddress.Physical) display.getAddress()).getPort();
                if (Log.isLoggable(TAG, Log.INFO)) {
                    Log.i(TAG, String.format(
                            "Display %d uses port %d", display.getDisplayId(), port));
                }
                mPhysicalPorts.add(port);
            } else {
                Log.w(TAG, "At init non-virtual display has a non-physical display address: "
                        + display);
            }
        }
    }

    private Map<Byte, CarUxRestrictionsConfiguration> convertToMap(
            List<CarUxRestrictionsConfiguration> configs) {
        validateConfigs(configs);

        Map<Byte, CarUxRestrictionsConfiguration> result = new HashMap<>();
        if (configs.size() == 1) {
            CarUxRestrictionsConfiguration config = configs.get(0);
            byte port = config.getPhysicalPort() == null
                    ? mDefaultDisplayPhysicalPort
                    : config.getPhysicalPort();
            result.put(port, config);
        } else {
            for (CarUxRestrictionsConfiguration config : configs) {
                result.put(config.getPhysicalPort(), config);
            }
        }
        return result;
    }

    /**
     * Validates configs for multi-display:
     * - share the same restrictions parameters;
     * - each sets display port;
     * - each has unique display port.
     */
    @VisibleForTesting
    void validateConfigs(List<CarUxRestrictionsConfiguration> configs) {
        if (configs.size() == 0) {
            throw new IllegalArgumentException("Empty configuration.");
        }

        if (configs.size() == 1) {
            return;
        }

        CarUxRestrictionsConfiguration first = configs.get(0);
        Set<Byte> existingPorts = new ArraySet<>();
        for (CarUxRestrictionsConfiguration config : configs) {
            if (!config.hasSameParameters(first)) {
                // Input should have the same restriction parameters because:
                // - it doesn't make sense otherwise; and
                // - in format it matches how xml can only specify one set of parameters.
                throw new IllegalArgumentException(
                        "Configurations should have the same restrictions parameters.");
            }

            Byte port = config.getPhysicalPort();
            if (port == null) {
                // Size was checked above; safe to assume there are multiple configs.
                throw new IllegalArgumentException(
                        "Input contains multiple configurations; each must set physical port.");
            }
            if (existingPorts.contains(port)) {
                throw new IllegalArgumentException("Multiple configurations for port " + port);
            }

            existingPorts.add(port);
        }
    }

    /**
     * Returns the physical port byte id for the display or {@code null} if {@link
     * DisplayManager#getDisplay(int)} is not aware of the provided id.
     */
    @Nullable
    private Byte getPhysicalPort(int displayId) {
        if (!mPortLookup.containsKey(displayId)) {
            Display display = mDisplayManager.getDisplay(displayId);
            if (display == null) {
                Log.w(TAG, "Could not retrieve display for id: " + displayId);
                return null;
            }
            byte port = getPhysicalPort(display);
            mPortLookup.put(displayId, port);
        }
        return mPortLookup.get(displayId);
    }

    private byte getPhysicalPort(@NonNull Display display) {
        if (display.getType() == Display.TYPE_VIRTUAL) {
            // We require all virtual displays to be launched on default display.
            return mDefaultDisplayPhysicalPort;
        }

        DisplayAddress address = display.getAddress();
        if (address == null) {
            Log.e(TAG, "Display " + display
                    + " is not a virtual display but has null DisplayAddress.");
            return mDefaultDisplayPhysicalPort;
        } else if (!(address instanceof DisplayAddress.Physical)) {
            Log.e(TAG, "Display " + display + " has non-physical address: " + address);
            return mDefaultDisplayPhysicalPort;
        } else {
            return ((DisplayAddress.Physical) address).getPort();
        }
    }

    private CarUxRestrictions createUnrestrictedRestrictions() {
        return new CarUxRestrictions.Builder(/* reqOpt= */ false,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, SystemClock.elapsedRealtimeNanos())
                .build();
    }

    private CarUxRestrictions createFullyRestrictedRestrictions() {
        return new CarUxRestrictions.Builder(
                /*reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_FULLY_RESTRICTED,
                SystemClock.elapsedRealtimeNanos()).build();
    }

    CarUxRestrictionsConfiguration createDefaultConfig(byte port) {
        return new CarUxRestrictionsConfiguration.Builder()
                .setPhysicalPort(port)
                .setUxRestrictions(DRIVING_STATE_PARKED,
                        false, CarUxRestrictions.UX_RESTRICTIONS_BASELINE)
                .setUxRestrictions(DRIVING_STATE_IDLING,
                        false, CarUxRestrictions.UX_RESTRICTIONS_BASELINE)
                .setUxRestrictions(DRIVING_STATE_MOVING,
                        true, CarUxRestrictions.UX_RESTRICTIONS_FULLY_RESTRICTED)
                .setUxRestrictions(DRIVING_STATE_UNKNOWN,
                        true, CarUxRestrictions.UX_RESTRICTIONS_FULLY_RESTRICTED)
                .build();
    }

    private void addTransitionLog(String name, int from, int to, long timestamp, String extra) {
        if (mTransitionLogs.size() >= MAX_TRANSITION_LOG_SIZE) {
            mTransitionLogs.remove();
        }

        Utils.TransitionLog tLog = new Utils.TransitionLog(name, from, to, timestamp, extra);
        mTransitionLogs.add(tLog);
    }

    private void addTransitionLog(
            CarUxRestrictions oldRestrictions, CarUxRestrictions newRestrictions) {
        if (mTransitionLogs.size() >= MAX_TRANSITION_LOG_SIZE) {
            mTransitionLogs.remove();
        }
        StringBuilder extra = new StringBuilder();
        extra.append(oldRestrictions.isRequiresDistractionOptimization() ? "DO -> " : "No DO -> ");
        extra.append(newRestrictions.isRequiresDistractionOptimization() ? "DO" : "No DO");

        Utils.TransitionLog tLog = new Utils.TransitionLog(TAG,
                oldRestrictions.getActiveRestrictions(), newRestrictions.getActiveRestrictions(),
                System.currentTimeMillis(), extra.toString());
        mTransitionLogs.add(tLog);
    }

    private static void logd(String msg) {
        if (DBG) {
            Slog.d(TAG, msg);
        }
    }
}
