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

package android.car.drivingstate;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.car.Car;
import android.car.CarManagerBase;
import android.content.Context;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;
import android.view.Display;

import com.android.internal.annotations.GuardedBy;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.List;

/**
 * API to register and get the User Experience restrictions imposed based on the car's driving
 * state.
 */
public final class CarUxRestrictionsManager implements CarManagerBase {
    private static final String TAG = "CarUxRManager";
    private static final boolean DBG = false;
    private static final boolean VDBG = false;
    private static final int MSG_HANDLE_UX_RESTRICTIONS_CHANGE = 0;

    /**
     * Baseline restriction mode is the default UX restrictions used for driving state.
     *
     * @hide
     */
    public static final int UX_RESTRICTION_MODE_BASELINE = 0;
    /**
     * Passenger restriction mode uses UX restrictions for {@link #UX_RESTRICTION_MODE_PASSENGER},
     * set through {@link CarUxRestrictionsConfiguration.Builder.UxRestrictions#setMode(int)}.
     *
     * <p>If a new {@link CarUxRestrictions} is available upon mode transition, it'll be immediately
     * dispatched to listeners.
     *
     * <p>If passenger mode restrictions is not configured for current driving state, it will fall
     * back to {@link #UX_RESTRICTION_MODE_BASELINE}.
     *
     * <p>Caller are responsible for determining and executing the criteria for entering and exiting
     * this mode. Exiting by setting mode to {@link #UX_RESTRICTION_MODE_BASELINE}.
     *
     * @hide
     */
    public static final int UX_RESTRICTION_MODE_PASSENGER = 1;

    /** @hide */
    @IntDef(prefix = { "UX_RESTRICTION_MODE_" }, value = {
            UX_RESTRICTION_MODE_BASELINE,
            UX_RESTRICTION_MODE_PASSENGER
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface UxRestrictionMode {}

    private final Context mContext;
    private int mDisplayId = Display.INVALID_DISPLAY;
    private final ICarUxRestrictionsManager mUxRService;
    private final EventCallbackHandler mEventCallbackHandler;
    @GuardedBy("this")
    private OnUxRestrictionsChangedListener mUxRListener;
    private CarUxRestrictionsChangeListenerToService mListenerToService;

    /** @hide */
    public CarUxRestrictionsManager(IBinder service, Context context, Handler handler) {
        mContext = context;
        mUxRService = ICarUxRestrictionsManager.Stub.asInterface(service);
        mEventCallbackHandler = new EventCallbackHandler(this, handler.getLooper());
    }

    /** @hide */
    @Override
    public void onCarDisconnected() {
        mListenerToService = null;
        synchronized (this) {
            mUxRListener = null;
        }
    }

    /**
     * Listener Interface for clients to implement to get updated on driving state related
     * changes.
     */
    public interface OnUxRestrictionsChangedListener {
        /**
         * Called when the UX restrictions due to a car's driving state changes.
         *
         * @param restrictionInfo The new UX restriction information
         */
        void onUxRestrictionsChanged(CarUxRestrictions restrictionInfo);
    }

    /**
     * Registers a {@link OnUxRestrictionsChangedListener} for listening to changes in the
     * UX Restrictions to adhere to.
     * <p>
     * If a listener has already been registered, it has to be unregistered before registering
     * the new one.
     *
     * @param listener {@link OnUxRestrictionsChangedListener}
     */
    public void registerListener(@NonNull OnUxRestrictionsChangedListener listener) {
        registerListener(listener, getDisplayId());
    }

    /**
     * @hide
     */
    public void registerListener(@NonNull OnUxRestrictionsChangedListener listener, int displayId) {
        synchronized (this) {
            // Check if the listener has been already registered.
            if (mUxRListener != null) {
                if (DBG) {
                    Log.d(TAG, "Listener already registered listener");
                }
                return;
            }
            mUxRListener = listener;
        }

        try {
            if (mListenerToService == null) {
                mListenerToService = new CarUxRestrictionsChangeListenerToService(this);
            }
            // register to the Service to listen for changes.
            mUxRService.registerUxRestrictionsChangeListener(mListenerToService, displayId);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Unregisters the registered {@link OnUxRestrictionsChangedListener}
     */
    public void unregisterListener() {
        synchronized (this) {
            if (mUxRListener == null) {
                if (DBG) {
                    Log.d(TAG, "Listener was not previously registered");
                }
                return;
            }
            mUxRListener = null;
        }
        try {
            mUxRService.unregisterUxRestrictionsChangeListener(mListenerToService);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Sets new {@link CarUxRestrictionsConfiguration}s for next trip.
     * <p>
     * Saving new configurations does not affect current configuration. The new configuration will
     * only be used after UX Restrictions service restarts when the vehicle is parked.
     * <p>
     * Input configurations must be one-to-one mapped to displays, namely each display must have
     * exactly one configuration.
     * See {@link CarUxRestrictionsConfiguration.Builder#setDisplayAddress(DisplayAddress)}.
     *
     * @param configs Map of display Id to UX restrictions configurations to be persisted.
     * @return {@code true} if input config was successfully saved; {@code false} otherwise.
     *
     * @hide
     */
    @RequiresPermission(value = Car.PERMISSION_CAR_UX_RESTRICTIONS_CONFIGURATION)
    public boolean saveUxRestrictionsConfigurationForNextBoot(
            List<CarUxRestrictionsConfiguration> configs) {
        try {
            return mUxRService.saveUxRestrictionsConfigurationForNextBoot(configs);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Gets the current UX restrictions ({@link CarUxRestrictions}) in place.
     *
     * @return current UX restrictions that is in effect.
     */
    @Nullable
    public CarUxRestrictions getCurrentCarUxRestrictions() {
        return getCurrentCarUxRestrictions(getDisplayId());
    }

    /**
     * @hide
     */
    @Nullable
    public CarUxRestrictions getCurrentCarUxRestrictions(int displayId) {
        try {
            return mUxRService.getCurrentUxRestrictions(displayId);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Sets restriction mode. Returns {@code true} if the operation succeeds.
     *
     * @hide
     */
    @RequiresPermission(value = Car.PERMISSION_CAR_UX_RESTRICTIONS_CONFIGURATION)
    public boolean setRestrictionMode(@UxRestrictionMode int mode) {
        try {
            return mUxRService.setRestrictionMode(mode);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Returns the current restriction mode.
     *
     * @hide
     */
    @RequiresPermission(value = Car.PERMISSION_CAR_UX_RESTRICTIONS_CONFIGURATION)
    @UxRestrictionMode
    public int getRestrictionMode() {
        try {
            return mUxRService.getRestrictionMode();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Sets a new {@link CarUxRestrictionsConfiguration} for next trip.
     * <p>
     * Saving a new configuration does not affect current configuration. The new configuration will
     * only be used after UX Restrictions service restarts when the vehicle is parked.
     *
     * @param config UX restrictions configuration to be persisted.
     * @return {@code true} if input config was successfully saved; {@code false} otherwise.
     *
     * @hide
     */
    @RequiresPermission(value = Car.PERMISSION_CAR_UX_RESTRICTIONS_CONFIGURATION)
    public boolean saveUxRestrictionsConfigurationForNextBoot(
            CarUxRestrictionsConfiguration config) {
        return saveUxRestrictionsConfigurationForNextBoot(Arrays.asList(config));
    }

    /**
     * Gets the staged configurations.
     * <p>
     * Configurations set by {@link #saveUxRestrictionsConfigurationForNextBoot(List)} do not
     * immediately affect current drive. Instead, they are staged to take effect when car service
     * boots up the next time.
     * <p>
     * This methods is only for test purpose, please do not use in production.
     *
     * @return current staged configuration, {@code null} if it's not available
     *
     * @hide
     */
    @Nullable
    @RequiresPermission(value = Car.PERMISSION_CAR_UX_RESTRICTIONS_CONFIGURATION)
    public List<CarUxRestrictionsConfiguration> getStagedConfigs() {
        try {
            return mUxRService.getStagedConfigs();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Gets the current configurations.
     *
     * @return current configurations that is in effect.
     *
     * @hide
     */
    @RequiresPermission(value = Car.PERMISSION_CAR_UX_RESTRICTIONS_CONFIGURATION)
    public List<CarUxRestrictionsConfiguration> getConfigs() {
        try {
            return mUxRService.getConfigs();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * @hide
     */
    public static String modeToString(@UxRestrictionMode int mode) {
        switch (mode) {
            case UX_RESTRICTION_MODE_BASELINE:
                return "baseline";
            case UX_RESTRICTION_MODE_PASSENGER:
                return "passenger";
            default:
                throw new IllegalArgumentException("Unrecognized restriction mode " + mode);
        }
    }

    /**
     * Class that implements the listener interface and gets called back from the
     * {@link com.android.car.CarDrivingStateService} across the binder interface.
     */
    private static class CarUxRestrictionsChangeListenerToService extends
            ICarUxRestrictionsChangeListener.Stub {
        private final WeakReference<CarUxRestrictionsManager> mUxRestrictionsManager;

        public CarUxRestrictionsChangeListenerToService(CarUxRestrictionsManager manager) {
            mUxRestrictionsManager = new WeakReference<>(manager);
        }

        @Override
        public void onUxRestrictionsChanged(CarUxRestrictions restrictionInfo) {
            CarUxRestrictionsManager manager = mUxRestrictionsManager.get();
            if (manager != null) {
                manager.handleUxRestrictionsChanged(restrictionInfo);
            }
        }
    }

    /**
     * Gets the {@link CarUxRestrictions} from the service listener
     * {@link CarUxRestrictionsChangeListenerToService} and dispatches it to a handler provided
     * to the manager.
     *
     * @param restrictionInfo {@link CarUxRestrictions} that has been registered to listen on
     */
    private void handleUxRestrictionsChanged(CarUxRestrictions restrictionInfo) {
        // send a message to the handler
        mEventCallbackHandler.sendMessage(mEventCallbackHandler.obtainMessage(
                MSG_HANDLE_UX_RESTRICTIONS_CHANGE, restrictionInfo));
    }

    /**
     * Callback Handler to handle dispatching the UX restriction changes to the corresponding
     * listeners.
     */
    private static final class EventCallbackHandler extends Handler {
        private final WeakReference<CarUxRestrictionsManager> mUxRestrictionsManager;

        public EventCallbackHandler(CarUxRestrictionsManager manager, Looper looper) {
            super(looper);
            mUxRestrictionsManager = new WeakReference<>(manager);
        }

        @Override
        public void handleMessage(Message msg) {
            CarUxRestrictionsManager mgr = mUxRestrictionsManager.get();
            if (mgr != null) {
                mgr.dispatchUxRChangeToClient((CarUxRestrictions) msg.obj);
            }
        }
    }

    /**
     * Checks for the listeners to list of {@link CarUxRestrictions} and calls them back
     * in the callback handler thread.
     *
     * @param restrictionInfo {@link CarUxRestrictions}
     */
    private void dispatchUxRChangeToClient(CarUxRestrictions restrictionInfo) {
        if (restrictionInfo == null) {
            return;
        }
        synchronized (this) {
            if (mUxRListener != null) {
                mUxRListener.onUxRestrictionsChanged(restrictionInfo);
            }
        }
    }

    private int getDisplayId() {
        if (mDisplayId != Display.INVALID_DISPLAY) {
            return mDisplayId;
        }

        mDisplayId = mContext.getDisplayId();
        Log.i(TAG, "Context returns display ID " + mDisplayId);

        if (mDisplayId == Display.INVALID_DISPLAY) {
            mDisplayId = Display.DEFAULT_DISPLAY;
            Log.e(TAG, "Could not retrieve display id. Using default: " + mDisplayId);
        }

        return mDisplayId;
    }
}
