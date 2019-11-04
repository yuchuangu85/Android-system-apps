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

package android.car.hardware.power;

import android.annotation.SystemApi;
import android.car.Car;
import android.car.CarManagerBase;
import android.content.Context;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

/**
 * API for receiving power state change notifications.
 * @hide
 */
@SystemApi
public class CarPowerManager implements CarManagerBase {
    private final static boolean DBG = false;
    private final static String TAG = "CarPowerManager";

    private final Object mLock = new Object();
    private final ICarPower mService;

    private CarPowerStateListener mListener;
    private CarPowerStateListenerWithCompletion mListenerWithCompletion;
    private CompletableFuture<Void> mFuture;
    @GuardedBy("mLock")
    private ICarPowerStateListener mListenerToService;


    /**
     *  Applications set a {@link CarPowerStateListener} for power state event updates.
     */
    public interface CarPowerStateListener {
        /**
         * onStateChanged() states.  These definitions must match the ones located in the native
         * CarPowerManager:  packages/services/Car/car-lib/native/CarPowerManager/CarPowerManager.h
         */

        /**
         * Android is up, but vendor is controlling the audio / display
         * @hide
         */
        int WAIT_FOR_VHAL = 1;
        /**
         * Enter suspend state.  CPMS is switching to WAIT_FOR_FINISHED state.
         * @hide
         */
        int SUSPEND_ENTER = 2;
        /**
         * Wake up from suspend.
         * @hide
         */
        int SUSPEND_EXIT = 3;
        /**
         * Enter shutdown state.  CPMS is switching to WAIT_FOR_FINISHED state.
         * @hide
         */
        int SHUTDOWN_ENTER = 5;
        /**
         * On state
         * @hide
         */
        int ON = 6;
        /**
         * State where system is getting ready for shutdown or suspend.  Application is expected to
         * cleanup and be ready to suspend
         * @hide
         */
        int SHUTDOWN_PREPARE = 7;
        /**
         * Shutdown is cancelled, return to normal state.
         * @hide
         */
        int SHUTDOWN_CANCELLED = 8;

        /**
         * Called when power state changes. This callback is available to
         * any listener, even if it is not running in the system process.
         * @param state New power state of device.
         * @hide
         */
        void onStateChanged(int state);
    }

    /**
     * Applications set a {@link CarPowerStateListenerWithCompletion} for power state
     * event updates where a CompletableFuture is used.
     * @hide
     */
    public interface CarPowerStateListenerWithCompletion {
        /**
         * Called when power state changes. This callback is only for listeners
         * that are running in the system process.
         * @param state New power state of device.
         * @param future CompletableFuture used by Car modules to notify CPMS that they
         *               are ready to continue shutting down. CPMS will wait until this
         *               future is completed.
         * @hide
         */
        void onStateChanged(int state, CompletableFuture<Void> future);
    }

    /**
     * Get an instance of the CarPowerManager.
     *
     * Should not be obtained directly by clients, use {@link Car#getCarManager(String)} instead.
     * @param service
     * @param context
     * @param handler
     * @hide
     */
    public CarPowerManager(IBinder service, Context context, Handler handler) {
        mService = ICarPower.Stub.asInterface(service);
    }

    /**
     * Request power manager to shutdown in lieu of suspend at the next opportunity.
     * @hide
     */
    public void requestShutdownOnNextSuspend() {
        try {
            mService.requestShutdownOnNextSuspend();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Schedule next wake up time in CarPowerManagementSystem
     * @hide
     */
    public void scheduleNextWakeupTime(int seconds) {
        try {
            mService.scheduleNextWakeupTime(seconds);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Sets a listener to receive power state changes. Only one listener may be set at a
     * time for an instance of CarPowerManager.
     * The listener is assumed to completely handle the 'onStateChanged' before returning.
     *
     * @param listener
     * @throws IllegalStateException
     * @hide
     */
    public void setListener(CarPowerStateListener listener) {
        synchronized (mLock) {
            if (mListener != null || mListenerWithCompletion != null) {
                throw new IllegalStateException("Listener must be cleared first");
            }
            // Update listener
            mListener = listener;
            setServiceForListenerLocked(false);
        }
    }

    /**
     * Sets a listener to receive power state changes. Only one listener may be set at a
     * time for an instance of CarPowerManager.
     * For calls that require completion before continue, we attach a {@link CompletableFuture}
     * which is being used as a signal that caller is finished and ready to proceed.
     * Once future is completed, the {@link finished} method will automatically be called to notify
     * {@link CarPowerManagementService} that the application has handled the
     * {@link #SHUTDOWN_PREPARE} state transition.
     *
     * @param listener
     * @throws IllegalStateException
     * @hide
     */
    public void setListenerWithCompletion(CarPowerStateListenerWithCompletion listener) {
        synchronized(mLock) {
            if (mListener != null || mListenerWithCompletion != null) {
                throw new IllegalStateException("Listener must be cleared first");
            }
            // Update listener
            mListenerWithCompletion = listener;
            setServiceForListenerLocked(true);
        }
    }

    private void setServiceForListenerLocked(boolean useCompletion) {
        if (mListenerToService == null) {
            ICarPowerStateListener listenerToService = new ICarPowerStateListener.Stub() {
                @Override
                public void onStateChanged(int state) throws RemoteException {
                    if (useCompletion) {
                        // Update CompletableFuture. This will recreate it or just clean it up.
                        updateFuture(state);
                        // Notify user that the state has changed and supply a future
                        mListenerWithCompletion.onStateChanged(state, mFuture);
                    } else {
                        // Notify the user without supplying a future
                        mListener.onStateChanged(state);
                    }
                }
            };
            try {
                if (useCompletion) {
                    mService.registerListenerWithCompletion(listenerToService);
                } else {
                    mService.registerListener(listenerToService);
                }
                mListenerToService = listenerToService;
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
        }
    }

    /**
     * Removes the listener from {@link CarPowerManagementService}
     * @hide
     */
    public void clearListener() {
        ICarPowerStateListener listenerToService;
        synchronized (mLock) {
            listenerToService = mListenerToService;
            mListenerToService = null;
            mListener = null;
            mListenerWithCompletion = null;
            cleanupFuture();
        }

        if (listenerToService == null) {
            Log.w(TAG, "unregisterListener: listener was not registered");
            return;
        }

        try {
            mService.unregisterListener(listenerToService);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    private void updateFuture(int state) {
        cleanupFuture();
        if (state == CarPowerStateListener.SHUTDOWN_PREPARE) {
            // Create a CompletableFuture and pass it to the listener.
            // When the listener completes the future, tell
            // CarPowerManagementService that this action is finished.
            mFuture = new CompletableFuture<>();
            mFuture.whenComplete((result, exception) -> {
                if (exception != null && !(exception instanceof CancellationException)) {
                    Log.e(TAG, "Exception occurred while waiting for future", exception);
                }
                try {
                    mService.finished(mListenerToService);
                } catch (RemoteException e) {
                    throw e.rethrowFromSystemServer();
                }
            });
        }
    }

    private void cleanupFuture() {
        if (mFuture != null) {
            if (!mFuture.isDone()) {
                mFuture.cancel(false);
            }
            mFuture = null;
        }
    }

    /** @hide */
    @Override
    public void onCarDisconnected() {
        ICarPowerStateListener listenerToService;
        synchronized (mLock) {
            listenerToService = mListenerToService;
        }

        if (listenerToService != null) {
            clearListener();
        }
    }
}
