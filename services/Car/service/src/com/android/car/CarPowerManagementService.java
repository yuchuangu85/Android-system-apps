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

import android.car.Car;
import android.car.hardware.power.CarPowerManager.CarPowerStateListener;
import android.car.hardware.power.ICarPower;
import android.car.hardware.power.ICarPowerStateListener;
import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.hardware.automotive.vehicle.V2_0.VehicleApPowerStateReq;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.util.Log;

import com.android.car.hal.PowerHalService;
import com.android.car.hal.PowerHalService.PowerState;
import com.android.car.systeminterface.SystemInterface;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.io.PrintWriter;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Power Management service class for cars. Controls the power states and interacts with other
 * parts of the system to ensure its own state.
 */
public class CarPowerManagementService extends ICarPower.Stub implements
        CarServiceBase, PowerHalService.PowerEventListener {
    private final Context mContext;
    private final PowerHalService mHal;
    private final SystemInterface mSystemInterface;
    // The listeners that complete simply by returning from onStateChanged()
    private final PowerManagerCallbackList mPowerManagerListeners = new PowerManagerCallbackList();
    // The listeners that must indicate asynchronous completion by calling finished().
    private final PowerManagerCallbackList mPowerManagerListenersWithCompletion =
                          new PowerManagerCallbackList();
    private final Set<IBinder> mListenersWeAreWaitingFor = new HashSet<>();
    private final Object mSimulationSleepObject = new Object();

    @GuardedBy("this")
    private CpmsState mCurrentState;
    @GuardedBy("this")
    private Timer mTimer;
    @GuardedBy("this")
    private long mProcessingStartTime;
    @GuardedBy("this")
    private long mLastSleepEntryTime;
    @GuardedBy("this")
    private final LinkedList<CpmsState> mPendingPowerStates = new LinkedList<>();
    @GuardedBy("this")
    private HandlerThread mHandlerThread;
    @GuardedBy("this")
    private PowerHandler mHandler;
    @GuardedBy("this")
    private boolean mTimerActive;
    @GuardedBy("mSimulationSleepObject")
    private boolean mInSimulatedDeepSleepMode = false;
    @GuardedBy("mSimulationSleepObject")
    private boolean mWakeFromSimulatedSleep = false;
    private int mNextWakeupSec = 0;
    private boolean mShutdownOnFinish = false;
    private boolean mIsBooting = true;

    private final CarUserManagerHelper mCarUserManagerHelper;

    // TODO:  Make this OEM configurable.
    private static final int SHUTDOWN_POLLING_INTERVAL_MS = 2000;
    private static final int SHUTDOWN_EXTEND_MAX_MS = 5000;

    // maxGarageModeRunningDurationInSecs should be equal or greater than this. 15 min for now.
    private static final int MIN_MAX_GARAGE_MODE_DURATION_MS = 15 * 60 * 1000;

    private static int sShutdownPrepareTimeMs = MIN_MAX_GARAGE_MODE_DURATION_MS;

    // in secs
    private static final String PROP_MAX_GARAGE_MODE_DURATION_OVERRIDE =
            "android.car.garagemodeduration";

    private class PowerManagerCallbackList extends RemoteCallbackList<ICarPowerStateListener> {
        /**
         * Old version of {@link #onCallbackDied(E, Object)} that
         * does not provide a cookie.
         */
        @Override
        public void onCallbackDied(ICarPowerStateListener listener) {
            Log.i(CarLog.TAG_POWER, "binderDied " + listener.asBinder());
            CarPowerManagementService.this.doUnregisterListener(listener);
        }
    }

    public CarPowerManagementService(
            Context context, PowerHalService powerHal, SystemInterface systemInterface,
            CarUserManagerHelper carUserManagerHelper) {
        mContext = context;
        mHal = powerHal;
        mSystemInterface = systemInterface;
        mCarUserManagerHelper = carUserManagerHelper;
        sShutdownPrepareTimeMs = mContext.getResources().getInteger(
                R.integer.maxGarageModeRunningDurationInSecs) * 1000;
        if (sShutdownPrepareTimeMs < MIN_MAX_GARAGE_MODE_DURATION_MS) {
            Log.w(CarLog.TAG_POWER,
                    "maxGarageModeRunningDurationInSecs smaller than minimum required, resource:"
                    + sShutdownPrepareTimeMs + "(ms) while should exceed:"
                    +  MIN_MAX_GARAGE_MODE_DURATION_MS + "(ms), Ignore resource.");
            sShutdownPrepareTimeMs = MIN_MAX_GARAGE_MODE_DURATION_MS;
        }
    }

    /**
     * Create a dummy instance for unit testing purpose only. Instance constructed in this way
     * is not safe as members expected to be non-null are null.
     */
    @VisibleForTesting
    protected CarPowerManagementService() {
        mContext = null;
        mHal = null;
        mSystemInterface = null;
        mHandlerThread = null;
        mHandler = new PowerHandler(Looper.getMainLooper());
        mCarUserManagerHelper = null;
    }

    @VisibleForTesting
    protected static void setShutdownPrepareTimeout(int timeoutMs) {
        // Override the timeout to keep testing time short
        if (timeoutMs < SHUTDOWN_EXTEND_MAX_MS) {
            sShutdownPrepareTimeMs = SHUTDOWN_EXTEND_MAX_MS;
        } else {
            sShutdownPrepareTimeMs = timeoutMs;
        }
    }

    @Override
    public void init() {
        synchronized (CarPowerManagementService.this) {
            mHandlerThread = new HandlerThread(CarLog.TAG_POWER);
            mHandlerThread.start();
            mHandler = new PowerHandler(mHandlerThread.getLooper());
        }

        mHal.setListener(this);
        if (mHal.isPowerStateSupported()) {
            // Initialize CPMS in WAIT_FOR_VHAL state
            onApPowerStateChange(CpmsState.WAIT_FOR_VHAL, CarPowerStateListener.WAIT_FOR_VHAL);
        } else {
            Log.w(CarLog.TAG_POWER, "Vehicle hal does not support power state yet.");
            onApPowerStateChange(CpmsState.ON, CarPowerStateListener.ON);
        }
        mSystemInterface.startDisplayStateMonitoring(this);
    }

    @Override
    public void release() {
        HandlerThread handlerThread;
        synchronized (CarPowerManagementService.this) {
            releaseTimerLocked();
            mCurrentState = null;
            mHandler.cancelAll();
            handlerThread = mHandlerThread;
        }
        handlerThread.quitSafely();
        try {
            handlerThread.join(1000);
        } catch (InterruptedException e) {
            Log.e(CarLog.TAG_POWER, "Timeout while joining for handler thread to join.");
        }
        mSystemInterface.stopDisplayStateMonitoring();
        mPowerManagerListeners.kill();
        mListenersWeAreWaitingFor.clear();
        mSystemInterface.releaseAllWakeLocks();
    }

    @Override
    public void dump(PrintWriter writer) {
        writer.println("*PowerManagementService*");
        writer.print("mCurrentState:" + mCurrentState);
        writer.print(",mProcessingStartTime:" + mProcessingStartTime);
        writer.print(",mLastSleepEntryTime:" + mLastSleepEntryTime);
        writer.print(",mNextWakeupSec:" + mNextWakeupSec);
        writer.print(",mShutdownOnFinish:" + mShutdownOnFinish);
        writer.println(",sShutdownPrepareTimeMs:" + sShutdownPrepareTimeMs);
    }

    @Override
    public void onApPowerStateChange(PowerState state) {
        PowerHandler handler;
        synchronized (CarPowerManagementService.this) {
            mPendingPowerStates.addFirst(new CpmsState(state));
            handler = mHandler;
        }
        handler.handlePowerStateChange();
    }

    @VisibleForTesting
    protected void clearIsBooting() {
        mIsBooting = false;
    }

    /**
     * Initiate state change from CPMS directly.
     */
    private void onApPowerStateChange(int apState, int carPowerStateListenerState) {
        CpmsState newState = new CpmsState(apState, carPowerStateListenerState);
        PowerHandler handler;
        synchronized (CarPowerManagementService.this) {
            mPendingPowerStates.addFirst(newState);
            handler = mHandler;
        }
        handler.handlePowerStateChange();
    }

    private void doHandlePowerStateChange() {
        CpmsState state;
        PowerHandler handler;
        synchronized (CarPowerManagementService.this) {
            state = mPendingPowerStates.peekFirst();
            mPendingPowerStates.clear();
            if (state == null) {
                return;
            }
            Log.i(CarLog.TAG_POWER, "doHandlePowerStateChange: newState=" + state.name());
            if (!needPowerStateChangeLocked(state)) {
                Log.d(CarLog.TAG_POWER, "doHandlePowerStateChange no change needed");
                return;
            }
            // now real power change happens. Whatever was queued before should be all cancelled.
            releaseTimerLocked();
            handler = mHandler;
        }
        handler.cancelProcessingComplete();
        Log.i(CarLog.TAG_POWER, "setCurrentState " + state.toString());
        CarStatsLog.logPowerState(state.mState);
        mCurrentState = state;
        switch (state.mState) {
            case CpmsState.WAIT_FOR_VHAL:
                handleWaitForVhal(state);
                break;
            case CpmsState.ON:
                handleOn();
                break;
            case CpmsState.SHUTDOWN_PREPARE:
                handleShutdownPrepare(state);
                break;
            case CpmsState.SIMULATE_SLEEP:
                simulateShutdownPrepare();
                break;
            case CpmsState.WAIT_FOR_FINISH:
                handleWaitForFinish(state);
                break;
            case CpmsState.SUSPEND:
                // Received FINISH from VHAL
                handleFinish();
                break;
            default:
                // Illegal state
                // TODO:  Throw exception?
                break;
        }
    }

    private void handleWaitForVhal(CpmsState state) {
        int carPowerStateListenerState = state.mCarPowerStateListenerState;
        sendPowerManagerEvent(carPowerStateListenerState);
        // Inspect CarPowerStateListenerState to decide which message to send via VHAL
        switch (carPowerStateListenerState) {
            case CarPowerStateListener.WAIT_FOR_VHAL:
                mHal.sendWaitForVhal();
                break;
            case CarPowerStateListener.SHUTDOWN_CANCELLED:
                mHal.sendShutdownCancel();
                break;
            case CarPowerStateListener.SUSPEND_EXIT:
                mHal.sendSleepExit();
                break;
        }
    }

    private void handleOn() {
        // Do not switch user if it is booting as there can be a race with CarServiceHelperService
        if (mIsBooting) {
            mIsBooting = false;
        } else {
            int targetUserId = mCarUserManagerHelper.getInitialUser();
            if (targetUserId != UserHandle.USER_SYSTEM
                    && targetUserId != mCarUserManagerHelper.getCurrentForegroundUserId()) {
                Log.i(CarLog.TAG_POWER, "Desired user changed, switching to user:" + targetUserId);
                mCarUserManagerHelper.switchToUserId(targetUserId);
            }
        }
        mSystemInterface.setDisplayState(true);
        sendPowerManagerEvent(CarPowerStateListener.ON);
        mHal.sendOn();
    }

    private void handleShutdownPrepare(CpmsState newState) {
        mSystemInterface.setDisplayState(false);
        // Shutdown on finish if the system doesn't support deep sleep or doesn't allow it.
        mShutdownOnFinish |= !mHal.isDeepSleepAllowed()
                || !mSystemInterface.isSystemSupportingDeepSleep()
                || !newState.mCanSleep;
        if (newState.mCanPostpone) {
            Log.i(CarLog.TAG_POWER, "starting shutdown prepare");
            sendPowerManagerEvent(CarPowerStateListener.SHUTDOWN_PREPARE);
            mHal.sendShutdownPrepare();
            doHandlePreprocessing();
        } else {
            Log.i(CarLog.TAG_POWER, "starting shutdown immediately");
            synchronized (CarPowerManagementService.this) {
                releaseTimerLocked();
            }
            // Notify hal that we are shutting down and since it is immediate, don't schedule next
            // wake up
            mHal.sendShutdownStart(0);
            // shutdown HU
            mSystemInterface.shutdown();
        }
    }

    // Simulate system shutdown to Deep Sleep
    private void simulateShutdownPrepare() {
        mSystemInterface.setDisplayState(false);
        Log.i(CarLog.TAG_POWER, "starting shutdown prepare");
        sendPowerManagerEvent(CarPowerStateListener.SHUTDOWN_PREPARE);
        mHal.sendShutdownPrepare();
        doHandlePreprocessing();
    }

    private void handleWaitForFinish(CpmsState state) {
        sendPowerManagerEvent(state.mCarPowerStateListenerState);
        switch (state.mCarPowerStateListenerState) {
            case CarPowerStateListener.SUSPEND_ENTER:
                mHal.sendSleepEntry(mNextWakeupSec);
                break;
            case CarPowerStateListener.SHUTDOWN_ENTER:
                mHal.sendShutdownStart(mNextWakeupSec);
                break;
        }
    }

    private void handleFinish() {
        boolean mustShutDown;
        boolean simulatedMode;
        synchronized (mSimulationSleepObject) {
            simulatedMode = mInSimulatedDeepSleepMode;
            mustShutDown = mShutdownOnFinish && !simulatedMode;
        }
        if (mustShutDown) {
            // shutdown HU
            mSystemInterface.shutdown();
        } else {
            doHandleDeepSleep(simulatedMode);
        }
    }

    @GuardedBy("this")
    private void releaseTimerLocked() {
        synchronized (CarPowerManagementService.this) {
            if (mTimer != null) {
                mTimer.cancel();
            }
            mTimer = null;
            mTimerActive = false;
        }
    }

    private void doHandlePreprocessing() {
        int pollingCount = (sShutdownPrepareTimeMs / SHUTDOWN_POLLING_INTERVAL_MS) + 1;
        if (Build.IS_USERDEBUG || Build.IS_ENG) {
            int shutdownPrepareTimeOverrideInSecs =
                    SystemProperties.getInt(PROP_MAX_GARAGE_MODE_DURATION_OVERRIDE, -1);
            if (shutdownPrepareTimeOverrideInSecs >= 0) {
                pollingCount =
                        (shutdownPrepareTimeOverrideInSecs * 1000 / SHUTDOWN_POLLING_INTERVAL_MS)
                                + 1;
                Log.i(CarLog.TAG_POWER,
                        "Garage mode duration overridden secs:"
                                + shutdownPrepareTimeOverrideInSecs);
            }
        }
        Log.i(CarLog.TAG_POWER, "processing before shutdown expected for: "
                + sShutdownPrepareTimeMs + " ms, adding polling:" + pollingCount);
        synchronized (CarPowerManagementService.this) {
            mProcessingStartTime = SystemClock.elapsedRealtime();
            releaseTimerLocked();
            mTimer = new Timer();
            mTimerActive = true;
            mTimer.scheduleAtFixedRate(
                    new ShutdownProcessingTimerTask(pollingCount),
                    0 /*delay*/,
                    SHUTDOWN_POLLING_INTERVAL_MS);
        }
    }

    private void sendPowerManagerEvent(int newState) {
        // Broadcast to the listeners that do not signal completion
        notifyListeners(mPowerManagerListeners, newState);

        // SHUTDOWN_PREPARE is the only state where we need
        // to maintain callbacks from listener components.
        boolean allowCompletion = (newState == CarPowerStateListener.SHUTDOWN_PREPARE);

        // Fully populate mListenersWeAreWaitingFor before calling any onStateChanged()
        // for the listeners that signal completion.
        // Otherwise, if the first listener calls finish() synchronously, we will
        // see the list go empty and we will think that we are done.
        boolean haveSomeCompleters = false;
        PowerManagerCallbackList completingListeners = new PowerManagerCallbackList();
        synchronized (mListenersWeAreWaitingFor) {
            mListenersWeAreWaitingFor.clear();
            int idx = mPowerManagerListenersWithCompletion.beginBroadcast();
            while (idx-- > 0) {
                ICarPowerStateListener listener =
                        mPowerManagerListenersWithCompletion.getBroadcastItem(idx);
                completingListeners.register(listener);
                if (allowCompletion) {
                    mListenersWeAreWaitingFor.add(listener.asBinder());
                    haveSomeCompleters = true;
                }
            }
            mPowerManagerListenersWithCompletion.finishBroadcast();
        }
        // Broadcast to the listeners that DO signal completion
        notifyListeners(completingListeners, newState);

        if (allowCompletion && !haveSomeCompleters) {
            // No jobs need to signal completion. So we are now complete.
            signalComplete();
        }
    }

    private void notifyListeners(PowerManagerCallbackList listenerList, int newState) {
        int idx = listenerList.beginBroadcast();
        while (idx-- > 0) {
            ICarPowerStateListener listener = listenerList.getBroadcastItem(idx);
            try {
                listener.onStateChanged(newState);
            } catch (RemoteException e) {
                // It's likely the connection snapped. Let binder death handle the situation.
                Log.e(CarLog.TAG_POWER, "onStateChanged() call failed: " + e, e);
            }
        }
        listenerList.finishBroadcast();
    }

    private void doHandleDeepSleep(boolean simulatedMode) {
        // keep holding partial wakelock to prevent entering sleep before enterDeepSleep call
        // enterDeepSleep should force sleep entry even if wake lock is kept.
        mSystemInterface.switchToPartialWakeLock();
        PowerHandler handler;
        synchronized (CarPowerManagementService.this) {
            handler = mHandler;
        }
        handler.cancelProcessingComplete();
        synchronized (CarPowerManagementService.this) {
            mLastSleepEntryTime = SystemClock.elapsedRealtime();
        }
        int nextListenerState;
        if (simulatedMode) {
            simulateSleepByLooping();
            nextListenerState = CarPowerStateListener.SHUTDOWN_CANCELLED;
        } else {
            boolean sleepSucceeded = mSystemInterface.enterDeepSleep();
            if (!sleepSucceeded) {
                // VHAL should transition CPMS to shutdown.
                Log.e(CarLog.TAG_POWER, "Sleep did not succeed. Now attempting to shut down.");
                mSystemInterface.shutdown();
            }
            nextListenerState = CarPowerStateListener.SUSPEND_EXIT;
        }
        // On wake, reset nextWakeup time. If not set again, system will suspend/shutdown forever.
        mNextWakeupSec = 0;
        mSystemInterface.refreshDisplayBrightness();
        onApPowerStateChange(CpmsState.WAIT_FOR_VHAL, nextListenerState);
    }

    private boolean needPowerStateChangeLocked(CpmsState newState) {
        if (newState == null) {
            return false;
        } else if (mCurrentState == null) {
            return true;
        } else if (mCurrentState.equals(newState)) {
            return false;
        }

        // The following switch/case enforces the allowed state transitions.
        switch (mCurrentState.mState) {
            case CpmsState.WAIT_FOR_VHAL:
                return (newState.mState == CpmsState.ON)
                    || (newState.mState == CpmsState.SHUTDOWN_PREPARE);
            case CpmsState.SUSPEND:
                return newState.mState == CpmsState.WAIT_FOR_VHAL;
            case CpmsState.ON:
                return (newState.mState == CpmsState.SHUTDOWN_PREPARE)
                    || (newState.mState == CpmsState.SIMULATE_SLEEP);
            case CpmsState.SHUTDOWN_PREPARE:
                // If VHAL sends SHUTDOWN_IMMEDIATELY while in SHUTDOWN_PREPARE state, do it.
                return ((newState.mState == CpmsState.SHUTDOWN_PREPARE) && !newState.mCanPostpone)
                    || (newState.mState == CpmsState.WAIT_FOR_FINISH)
                    || (newState.mState == CpmsState.WAIT_FOR_VHAL);
            case CpmsState.SIMULATE_SLEEP:
                return true;
            case CpmsState.WAIT_FOR_FINISH:
                return newState.mState == CpmsState.SUSPEND;
            default:
                Log.e(CarLog.TAG_POWER, "Unhandled state transition:  currentState="
                        + mCurrentState.name() + ", newState=" + newState.name());
                return false;
        }
    }

    private void doHandleProcessingComplete() {
        synchronized (CarPowerManagementService.this) {
            releaseTimerLocked();
            if (!mShutdownOnFinish && mLastSleepEntryTime > mProcessingStartTime) {
                // entered sleep after processing start. So this could be duplicate request.
                Log.w(CarLog.TAG_POWER, "Duplicate sleep entry request, ignore");
                return;
            }
        }

        if (mShutdownOnFinish) {
            onApPowerStateChange(CpmsState.WAIT_FOR_FINISH, CarPowerStateListener.SHUTDOWN_ENTER);
        } else {
            onApPowerStateChange(CpmsState.WAIT_FOR_FINISH, CarPowerStateListener.SUSPEND_ENTER);
        }
    }

    @Override
    public void onDisplayBrightnessChange(int brightness) {
        PowerHandler handler;
        synchronized (CarPowerManagementService.this) {
            handler = mHandler;
        }
        handler.handleDisplayBrightnessChange(brightness);
    }

    private void doHandleDisplayBrightnessChange(int brightness) {
        mSystemInterface.setDisplayBrightness(brightness);
    }

    private void doHandleMainDisplayStateChange(boolean on) {
        Log.w(CarLog.TAG_POWER, "Unimplemented:  doHandleMainDisplayStateChange() - on = " + on);
    }

    public void handleMainDisplayChanged(boolean on) {
        PowerHandler handler;
        synchronized (CarPowerManagementService.this) {
            handler = mHandler;
        }
        handler.handleMainDisplayStateChange(on);
    }

    /**
     * Send display brightness to VHAL.
     * @param brightness value 0-100%
     */
    public void sendDisplayBrightness(int brightness) {
        mHal.sendDisplayBrightness(brightness);
    }

    public synchronized Handler getHandler() {
        return mHandler;
    }

    // Binder interface for general use.
    // The listener is not required (or allowed) to call finished().
    @Override
    public void registerListener(ICarPowerStateListener listener) {
        ICarImpl.assertPermission(mContext, Car.PERMISSION_CAR_POWER);
        mPowerManagerListeners.register(listener);
    }

    // Binder interface for Car services only.
    // After the listener completes its processing, it must call finished().
    @Override
    public void registerListenerWithCompletion(ICarPowerStateListener listener) {
        ICarImpl.assertPermission(mContext, Car.PERMISSION_CAR_POWER);
        ICarImpl.assertCallingFromSystemProcessOrSelf();

        mPowerManagerListenersWithCompletion.register(listener);
        // TODO: Need to send current state to newly registered listener? If so, need to handle
        //       completion for SHUTDOWN_PREPARE state
    }

    @Override
    public void unregisterListener(ICarPowerStateListener listener) {
        ICarImpl.assertPermission(mContext, Car.PERMISSION_CAR_POWER);
        doUnregisterListener(listener);
    }

    private void doUnregisterListener(ICarPowerStateListener listener) {
        mPowerManagerListeners.unregister(listener);
        boolean found = mPowerManagerListenersWithCompletion.unregister(listener);
        if (found) {
            // Remove this from the completion list (if it's there)
            finishedImpl(listener.asBinder());
        }
    }

    @Override
    public void requestShutdownOnNextSuspend() {
        ICarImpl.assertPermission(mContext, Car.PERMISSION_CAR_POWER);
        mShutdownOnFinish = true;
    }

    @Override
    public void finished(ICarPowerStateListener listener) {
        ICarImpl.assertPermission(mContext, Car.PERMISSION_CAR_POWER);
        ICarImpl.assertCallingFromSystemProcessOrSelf();
        finishedImpl(listener.asBinder());
    }

    @Override
    public synchronized void scheduleNextWakeupTime(int seconds) {
        if (seconds < 0) {
            Log.w(CarLog.TAG_POWER, "Next wake up can not be in negative time. Ignoring!");
            return;
        }
        if (!mHal.isTimedWakeupAllowed()) {
            Log.w(CarLog.TAG_POWER, "Setting timed wakeups are disabled in HAL. Skipping");
            mNextWakeupSec = 0;
            return;
        }
        if (mNextWakeupSec == 0 || mNextWakeupSec > seconds) {
            mNextWakeupSec = seconds;
        } else {
            Log.d(CarLog.TAG_POWER, "Tried to schedule next wake up, but already had shorter "
                    + "scheduled time");
        }
    }

    private void finishedImpl(IBinder binder) {
        boolean allAreComplete = false;
        synchronized (mListenersWeAreWaitingFor) {
            boolean oneWasRemoved = mListenersWeAreWaitingFor.remove(binder);
            allAreComplete = oneWasRemoved && mListenersWeAreWaitingFor.isEmpty();
        }
        if (allAreComplete) {
            signalComplete();
        }
    }

    private void signalComplete() {
        if (mCurrentState.mState == CpmsState.SHUTDOWN_PREPARE
                || mCurrentState.mState == CpmsState.SIMULATE_SLEEP) {
            PowerHandler powerHandler;
            // All apps are ready to shutdown/suspend.
            synchronized (CarPowerManagementService.this) {
                if (!mShutdownOnFinish) {
                    if (mLastSleepEntryTime > mProcessingStartTime
                            && mLastSleepEntryTime < SystemClock.elapsedRealtime()) {
                        Log.i(CarLog.TAG_POWER, "signalComplete: Already slept!");
                        return;
                    }
                }
                powerHandler = mHandler;
            }
            Log.i(CarLog.TAG_POWER, "Apps are finished, call handleProcessingComplete()");
            powerHandler.handleProcessingComplete();
        }
    }

    private class PowerHandler extends Handler {
        private final int MSG_POWER_STATE_CHANGE = 0;
        private final int MSG_DISPLAY_BRIGHTNESS_CHANGE = 1;
        private final int MSG_MAIN_DISPLAY_STATE_CHANGE = 2;
        private final int MSG_PROCESSING_COMPLETE = 3;

        // Do not handle this immediately but with some delay as there can be a race between
        // display off due to rear view camera and delivery to here.
        private final long MAIN_DISPLAY_EVENT_DELAY_MS = 500;

        private PowerHandler(Looper looper) {
            super(looper);
        }

        private void handlePowerStateChange() {
            Message msg = obtainMessage(MSG_POWER_STATE_CHANGE);
            sendMessage(msg);
        }

        private void handleDisplayBrightnessChange(int brightness) {
            Message msg = obtainMessage(MSG_DISPLAY_BRIGHTNESS_CHANGE, brightness, 0);
            sendMessage(msg);
        }

        private void handleMainDisplayStateChange(boolean on) {
            removeMessages(MSG_MAIN_DISPLAY_STATE_CHANGE);
            Message msg = obtainMessage(MSG_MAIN_DISPLAY_STATE_CHANGE, Boolean.valueOf(on));
            sendMessageDelayed(msg, MAIN_DISPLAY_EVENT_DELAY_MS);
        }

        private void handleProcessingComplete() {
            removeMessages(MSG_PROCESSING_COMPLETE);
            Message msg = obtainMessage(MSG_PROCESSING_COMPLETE);
            sendMessage(msg);
        }

        private void cancelProcessingComplete() {
            removeMessages(MSG_PROCESSING_COMPLETE);
        }

        private void cancelAll() {
            removeMessages(MSG_POWER_STATE_CHANGE);
            removeMessages(MSG_DISPLAY_BRIGHTNESS_CHANGE);
            removeMessages(MSG_MAIN_DISPLAY_STATE_CHANGE);
            removeMessages(MSG_PROCESSING_COMPLETE);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_POWER_STATE_CHANGE:
                    doHandlePowerStateChange();
                    break;
                case MSG_DISPLAY_BRIGHTNESS_CHANGE:
                    doHandleDisplayBrightnessChange(msg.arg1);
                    break;
                case MSG_MAIN_DISPLAY_STATE_CHANGE:
                    doHandleMainDisplayStateChange((Boolean) msg.obj);
                    break;
                case MSG_PROCESSING_COMPLETE:
                    doHandleProcessingComplete();
                    break;
            }
        }
    }

    private class ShutdownProcessingTimerTask extends TimerTask {
        private final int mExpirationCount;
        private int mCurrentCount;

        private ShutdownProcessingTimerTask(int expirationCount) {
            mExpirationCount = expirationCount;
            mCurrentCount = 0;
        }

        @Override
        public void run() {
            synchronized (CarPowerManagementService.this) {
                if (!mTimerActive) {
                    // Ignore timer expiration since we got cancelled
                    return;
                }
                mCurrentCount++;
                if (mCurrentCount > mExpirationCount) {
                    PowerHandler handler;
                    releaseTimerLocked();
                    handler = mHandler;
                    handler.handleProcessingComplete();
                } else {
                    mHal.sendShutdownPostpone(SHUTDOWN_EXTEND_MAX_MS);
                }
            }
        }
    }

    private static class CpmsState {
        // NOTE: When modifying states below, make sure to update CarPowerStateChanged.State in
        //   frameworks/base/cmds/statsd/src/atoms.proto also.
        public static final int WAIT_FOR_VHAL = 0;
        public static final int ON = 1;
        public static final int SHUTDOWN_PREPARE = 2;
        public static final int WAIT_FOR_FINISH = 3;
        public static final int SUSPEND = 4;
        public static final int SIMULATE_SLEEP = 5;

        /* Config values from AP_POWER_STATE_REQ */
        public final boolean mCanPostpone;
        public final boolean mCanSleep;
        /* Message sent to CarPowerStateListener in response to this state */
        public final int mCarPowerStateListenerState;
        /* One of the above state variables */
        public final int mState;

        /**
          * This constructor takes a PowerHalService.PowerState object and creates the corresponding
          * CPMS state from it.
          */
        CpmsState(PowerState halPowerState) {
            switch (halPowerState.mState) {
                case VehicleApPowerStateReq.ON:
                    this.mCanPostpone = false;
                    this.mCanSleep = false;
                    this.mCarPowerStateListenerState = cpmsStateToPowerStateListenerState(ON);
                    this.mState = ON;
                    break;
                case VehicleApPowerStateReq.SHUTDOWN_PREPARE:
                    this.mCanPostpone = halPowerState.canPostponeShutdown();
                    this.mCanSleep = halPowerState.canEnterDeepSleep();
                    this.mCarPowerStateListenerState = cpmsStateToPowerStateListenerState(
                            SHUTDOWN_PREPARE);
                    this.mState = SHUTDOWN_PREPARE;
                    break;
                case VehicleApPowerStateReq.CANCEL_SHUTDOWN:
                    this.mCanPostpone = false;
                    this.mCanSleep = false;
                    this.mCarPowerStateListenerState = CarPowerStateListener.SHUTDOWN_CANCELLED;
                    this.mState = WAIT_FOR_VHAL;
                    break;
                case VehicleApPowerStateReq.FINISHED:
                    this.mCanPostpone = false;
                    this.mCanSleep = false;
                    this.mCarPowerStateListenerState = cpmsStateToPowerStateListenerState(SUSPEND);
                    this.mState = SUSPEND;
                    break;
                default:
                    // Illegal state from PowerState.  Throw an exception?
                    this.mCanPostpone = false;
                    this.mCanSleep = false;
                    this.mCarPowerStateListenerState = 0;
                    this.mState = 0;
                    break;
            }
        }

        CpmsState(int state) {
            this(state, cpmsStateToPowerStateListenerState(state));
        }

        CpmsState(int state, int carPowerStateListenerState) {
            this.mCanPostpone = (state == SIMULATE_SLEEP);
            this.mCanSleep = (state == SIMULATE_SLEEP);
            this.mCarPowerStateListenerState = carPowerStateListenerState;
            this.mState = state;
        }

        public String name() {
            String baseName;
            switch(mState) {
                case WAIT_FOR_VHAL:     baseName = "WAIT_FOR_VHAL";    break;
                case ON:                baseName = "ON";               break;
                case SHUTDOWN_PREPARE:  baseName = "SHUTDOWN_PREPARE"; break;
                case WAIT_FOR_FINISH:   baseName = "WAIT_FOR_FINISH";  break;
                case SUSPEND:           baseName = "SUSPEND";          break;
                case SIMULATE_SLEEP:    baseName = "SIMULATE_SLEEP";   break;
                default:                baseName = "<unknown>";        break;
            }
            return baseName + "(" + mState + ")";
        }

        private static int cpmsStateToPowerStateListenerState(int state) {
            int powerStateListenerState = 0;

            // Set the CarPowerStateListenerState based on current state
            switch (state) {
                case ON:
                    powerStateListenerState = CarPowerStateListener.ON;
                    break;
                case SHUTDOWN_PREPARE:
                    powerStateListenerState = CarPowerStateListener.SHUTDOWN_PREPARE;
                    break;
                case SUSPEND:
                    powerStateListenerState = CarPowerStateListener.SUSPEND_ENTER;
                    break;
                case WAIT_FOR_VHAL:
                case WAIT_FOR_FINISH:
                default:
                    // Illegal state for this constructor.  Throw an exception?
                    break;
            }
            return powerStateListenerState;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof CpmsState)) {
                return false;
            }
            CpmsState that = (CpmsState) o;
            return this.mState == that.mState
                    && this.mCanSleep == that.mCanSleep
                    && this.mCanPostpone == that.mCanPostpone
                    && this.mCarPowerStateListenerState == that.mCarPowerStateListenerState;
        }

        @Override
        public String toString() {
            return "CpmsState canSleep:" + mCanSleep + ", canPostpone=" + mCanPostpone
                    + ", carPowerStateListenerState=" + mCarPowerStateListenerState
                    + ", CpmsState=" + this.name();
        }
    }

    /**
     * Resume after a manually-invoked suspend.
     * Invoked using "adb shell dumpsys activity service com.android.car resume".
     */
    public void forceSimulatedResume() {
        PowerHandler handler;
        synchronized (this) {
            // Cancel Garage Mode in case it's running
            mPendingPowerStates.addFirst(new CpmsState(CpmsState.WAIT_FOR_VHAL,
                                                       CarPowerStateListener.SHUTDOWN_CANCELLED));
            handler = mHandler;
        }
        handler.handlePowerStateChange();

        synchronized (mSimulationSleepObject) {
            mWakeFromSimulatedSleep = true;
            mSimulationSleepObject.notify();
        }
    }

    /**
     * Manually enter simulated suspend (Deep Sleep) mode
     * Invoked using "adb shell dumpsys activity service com.android.car suspend".
     * This is similar to 'onApPowerStateChange()' except that it needs to create a CpmsState
     * that is not directly derived from a VehicleApPowerStateReq.
     */
    public void forceSimulatedSuspend() {
        synchronized (mSimulationSleepObject) {
            mInSimulatedDeepSleepMode = true;
            mWakeFromSimulatedSleep = false;
        }
        PowerHandler handler;
        synchronized (this) {
            mPendingPowerStates.addFirst(new CpmsState(CpmsState.SIMULATE_SLEEP,
                                                       CarPowerStateListener.SHUTDOWN_PREPARE));
            handler = mHandler;
        }
        handler.handlePowerStateChange();
    }

    // In a real Deep Sleep, the hardware removes power from the CPU (but retains power
    // on the RAM). This puts the processor to sleep. Upon some external signal, power
    // is re-applied to the CPU, and processing resumes right where it left off.
    // We simulate this behavior by simply going into a loop.
    // We exit the loop when forceResume() is called.
    private void simulateSleepByLooping() {
        Log.i(CarLog.TAG_POWER, "Starting to simulate Deep Sleep by looping");
        synchronized (mSimulationSleepObject) {
            while (!mWakeFromSimulatedSleep) {
                try {
                    mSimulationSleepObject.wait();
                } catch (InterruptedException ignored) {
                }
            }
            mInSimulatedDeepSleepMode = false;
        }
        Log.i(CarLog.TAG_POWER, "Exit Deep Sleep simulation loop");
    }
}
