/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static android.hardware.input.InputManager.INJECT_INPUT_EVENT_MODE_ASYNC;
import static android.service.voice.VoiceInteractionSession.SHOW_SOURCE_PUSH_TO_TALK;

import android.annotation.Nullable;
import android.app.ActivityManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHeadsetClient;
import android.bluetooth.BluetoothProfile;
import android.car.CarProjectionManager;
import android.car.input.CarInputHandlingService;
import android.car.input.CarInputHandlingService.InputFilter;
import android.car.input.ICarInputListener;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.input.InputManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Parcel;
import android.os.RemoteException;
import android.os.UserHandle;
import android.provider.CallLog.Calls;
import android.provider.Settings;
import android.telecom.TelecomManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.ViewConfiguration;

import com.android.car.hal.InputHalService;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.app.AssistUtils;
import com.android.internal.app.IVoiceInteractionSessionShowCallback;

import java.io.PrintWriter;
import java.util.BitSet;
import java.util.List;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

public class CarInputService implements CarServiceBase, InputHalService.InputListener {

    /** An interface to receive {@link KeyEvent}s as they occur. */
    public interface KeyEventListener {
        /** Called when a key event occurs. */
        void onKeyEvent(KeyEvent event);
    }

    private static final class KeyPressTimer {
        private final Handler mHandler;
        private final Runnable mLongPressRunnable;
        private final Runnable mCallback = this::onTimerExpired;
        private final IntSupplier mLongPressDelaySupplier;

        @GuardedBy("this")
        private boolean mDown = false;
        @GuardedBy("this")
        private boolean mLongPress = false;

        KeyPressTimer(
                Handler handler, IntSupplier longPressDelaySupplier, Runnable longPressRunnable) {
            mHandler = handler;
            mLongPressRunnable = longPressRunnable;
            mLongPressDelaySupplier = longPressDelaySupplier;
        }

        /** Marks that a key was pressed, and starts the long-press timer. */
        synchronized void keyDown() {
            mDown = true;
            mLongPress = false;
            mHandler.removeCallbacks(mCallback);
            mHandler.postDelayed(mCallback, mLongPressDelaySupplier.getAsInt());
        }

        /**
         * Marks that a key was released, and stops the long-press timer.
         *
         * Returns true if the press was a long-press.
         */
        synchronized boolean keyUp() {
            mHandler.removeCallbacks(mCallback);
            mDown = false;
            return mLongPress;
        }

        private void onTimerExpired() {
            synchronized (this) {
                // If the timer expires after key-up, don't retroactively make the press long.
                if (!mDown) {
                    return;
                }
                mLongPress = true;
            }

            mLongPressRunnable.run();
        }
    }

    private final IVoiceInteractionSessionShowCallback mShowCallback =
            new IVoiceInteractionSessionShowCallback.Stub() {
                @Override
                public void onFailed() {
                    Log.w(CarLog.TAG_INPUT, "Failed to show VoiceInteractionSession");
                }

                @Override
                public void onShown() {
                    if (DBG) {
                        Log.d(CarLog.TAG_INPUT, "IVoiceInteractionSessionShowCallback onShown()");
                    }
                }
            };

    private static final boolean DBG = false;
    @VisibleForTesting
    static final String EXTRA_CAR_PUSH_TO_TALK =
            "com.android.car.input.EXTRA_CAR_PUSH_TO_TALK";

    private final Context mContext;
    private final InputHalService mInputHalService;
    private final TelecomManager mTelecomManager;
    private final AssistUtils mAssistUtils;
    // The ComponentName of the CarInputListener service. Can be changed via resource overlay,
    // or overridden directly for testing.
    @Nullable
    private final ComponentName mCustomInputServiceComponent;
    // The default handler for main-display input events. By default, injects the events into
    // the input queue via InputManager, but can be overridden for testing.
    private final KeyEventListener mMainDisplayHandler;
    // The supplier for the last-called number. By default, gets the number from the call log.
    // May be overridden for testing.
    private final Supplier<String> mLastCalledNumberSupplier;
    // The supplier for the system long-press delay, in milliseconds. By default, gets the value
    // from Settings.Secure for the current user, falling back to the system-wide default
    // long-press delay defined in ViewConfiguration. May be overridden for testing.
    private final IntSupplier mLongPressDelaySupplier;

    @GuardedBy("this")
    private CarProjectionManager.ProjectionKeyEventHandler mProjectionKeyEventHandler;
    @GuardedBy("this")
    private final BitSet mProjectionKeyEventsSubscribed = new BitSet();

    private final KeyPressTimer mVoiceKeyTimer;
    private final KeyPressTimer mCallKeyTimer;

    @GuardedBy("this")
    private KeyEventListener mInstrumentClusterKeyListener;

    @GuardedBy("this")
    @VisibleForTesting
    ICarInputListener mCarInputListener;

    @GuardedBy("this")
    private boolean mCarInputListenerBound = false;

    // Maps display -> keycodes handled.
    @GuardedBy("this")
    private final SetMultimap<Integer, Integer> mHandledKeys = new SetMultimap<>();

    private final Binder mCallback = new Binder() {
        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) {
            if (code == CarInputHandlingService.INPUT_CALLBACK_BINDER_CODE) {
                data.setDataPosition(0);
                InputFilter[] handledKeys = (InputFilter[]) data.createTypedArray(
                        InputFilter.CREATOR);
                if (handledKeys != null) {
                    setHandledKeys(handledKeys);
                }
                return true;
            }
            return false;
        }
    };

    private final ServiceConnection mInputServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            if (DBG) {
                Log.d(CarLog.TAG_INPUT, "onServiceConnected, name: "
                        + name + ", binder: " + binder);
            }
            synchronized (CarInputService.this) {
                mCarInputListener = ICarInputListener.Stub.asInterface(binder);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(CarLog.TAG_INPUT, "onServiceDisconnected, name: " + name);
            synchronized (CarInputService.this) {
                mCarInputListener = null;
            }
        }
    };

    private final BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

    // BluetoothHeadsetClient set through mBluetoothProfileServiceListener, and used by
    // launchBluetoothVoiceRecognition().
    @GuardedBy("mBluetoothProfileServiceListener")
    private BluetoothHeadsetClient mBluetoothHeadsetClient;

    private final BluetoothProfile.ServiceListener mBluetoothProfileServiceListener =
            new BluetoothProfile.ServiceListener() {
        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (profile == BluetoothProfile.HEADSET_CLIENT) {
                Log.d(CarLog.TAG_INPUT, "Bluetooth proxy connected for HEADSET_CLIENT profile");
                synchronized (this) {
                    mBluetoothHeadsetClient = (BluetoothHeadsetClient) proxy;
                }
            }
        }

        @Override
        public void onServiceDisconnected(int profile) {
            if (profile == BluetoothProfile.HEADSET_CLIENT) {
                Log.d(CarLog.TAG_INPUT, "Bluetooth proxy disconnected for HEADSET_CLIENT profile");
                synchronized (this) {
                    mBluetoothHeadsetClient = null;
                }
            }
        }
    };

    @Nullable
    private static ComponentName getDefaultInputComponent(Context context) {
        String carInputService = context.getString(R.string.inputService);
        if (TextUtils.isEmpty(carInputService)) {
            return null;
        }

        return ComponentName.unflattenFromString(carInputService);
    }

    private static int getViewLongPressDelay(ContentResolver cr) {
        return Settings.Secure.getIntForUser(
                cr,
                Settings.Secure.LONG_PRESS_TIMEOUT,
                ViewConfiguration.getLongPressTimeout(),
                UserHandle.USER_CURRENT);
    }

    public CarInputService(Context context, InputHalService inputHalService) {
        this(context, inputHalService, new Handler(Looper.getMainLooper()),
                context.getSystemService(TelecomManager.class), new AssistUtils(context),
                event ->
                        context.getSystemService(InputManager.class)
                                .injectInputEvent(event, INJECT_INPUT_EVENT_MODE_ASYNC),
                () -> Calls.getLastOutgoingCall(context),
                getDefaultInputComponent(context),
                () -> getViewLongPressDelay(context.getContentResolver()));
    }

    @VisibleForTesting
    CarInputService(Context context, InputHalService inputHalService, Handler handler,
            TelecomManager telecomManager, AssistUtils assistUtils,
            KeyEventListener mainDisplayHandler, Supplier<String> lastCalledNumberSupplier,
            @Nullable ComponentName customInputServiceComponent,
            IntSupplier longPressDelaySupplier) {
        mContext = context;
        mInputHalService = inputHalService;
        mTelecomManager = telecomManager;
        mAssistUtils = assistUtils;
        mMainDisplayHandler = mainDisplayHandler;
        mLastCalledNumberSupplier = lastCalledNumberSupplier;
        mCustomInputServiceComponent = customInputServiceComponent;
        mLongPressDelaySupplier = longPressDelaySupplier;

        mVoiceKeyTimer =
                new KeyPressTimer(
                        handler, longPressDelaySupplier, this::handleVoiceAssistLongPress);
        mCallKeyTimer =
                new KeyPressTimer(handler, longPressDelaySupplier, this::handleCallLongPress);
    }

    @VisibleForTesting
    synchronized void setHandledKeys(InputFilter[] handledKeys) {
        mHandledKeys.clear();
        for (InputFilter handledKey : handledKeys) {
            mHandledKeys.put(handledKey.mTargetDisplay, handledKey.mKeyCode);
        }
    }

    /**
     * Set projection key event listener. If null, unregister listener.
     */
    public void setProjectionKeyEventHandler(
            @Nullable CarProjectionManager.ProjectionKeyEventHandler listener,
            @Nullable BitSet events) {
        synchronized (this) {
            mProjectionKeyEventHandler = listener;
            mProjectionKeyEventsSubscribed.clear();
            if (events != null) {
                mProjectionKeyEventsSubscribed.or(events);
            }
        }
    }

    public void setInstrumentClusterKeyListener(KeyEventListener listener) {
        synchronized (this) {
            mInstrumentClusterKeyListener = listener;
        }
    }

    @Override
    public void init() {
        if (!mInputHalService.isKeyInputSupported()) {
            Log.w(CarLog.TAG_INPUT, "Hal does not support key input.");
            return;
        } else if (DBG) {
            Log.d(CarLog.TAG_INPUT, "Hal supports key input.");
        }


        mInputHalService.setInputListener(this);
        synchronized (this) {
            mCarInputListenerBound = bindCarInputService();
        }
        if (mBluetoothAdapter != null) {
            mBluetoothAdapter.getProfileProxy(
                    mContext, mBluetoothProfileServiceListener, BluetoothProfile.HEADSET_CLIENT);
        }
    }

    @Override
    public void release() {
        synchronized (this) {
            mProjectionKeyEventHandler = null;
            mProjectionKeyEventsSubscribed.clear();
            mInstrumentClusterKeyListener = null;
            if (mCarInputListenerBound) {
                mContext.unbindService(mInputServiceConnection);
                mCarInputListenerBound = false;
            }
        }
        synchronized (mBluetoothProfileServiceListener) {
            if (mBluetoothHeadsetClient != null) {
                mBluetoothAdapter.closeProfileProxy(
                        BluetoothProfile.HEADSET_CLIENT, mBluetoothHeadsetClient);
                mBluetoothHeadsetClient = null;
            }
        }
    }

    @Override
    public void onKeyEvent(KeyEvent event, int targetDisplay) {
        // Give a car specific input listener the opportunity to intercept any input from the car
        ICarInputListener carInputListener;
        synchronized (this) {
            carInputListener = mCarInputListener;
        }
        if (carInputListener != null && isCustomEventHandler(event, targetDisplay)) {
            try {
                carInputListener.onKeyEvent(event, targetDisplay);
            } catch (RemoteException e) {
                Log.e(CarLog.TAG_INPUT, "Error while calling car input service", e);
            }
            // Custom input service handled the event, nothing more to do here.
            return;
        }

        // Special case key code that have special "long press" handling for automotive
        switch (event.getKeyCode()) {
            case KeyEvent.KEYCODE_VOICE_ASSIST:
                handleVoiceAssistKey(event);
                return;
            case KeyEvent.KEYCODE_CALL:
                handleCallKey(event);
                return;
            default:
                break;
        }

        // Allow specifically targeted keys to be routed to the cluster
        if (targetDisplay == InputHalService.DISPLAY_INSTRUMENT_CLUSTER) {
            handleInstrumentClusterKey(event);
        } else {
            mMainDisplayHandler.onKeyEvent(event);
        }
    }

    private synchronized boolean isCustomEventHandler(KeyEvent event, int targetDisplay) {
        return mHandledKeys.containsEntry(targetDisplay, event.getKeyCode());
    }

    private void handleVoiceAssistKey(KeyEvent event) {
        int action = event.getAction();
        if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            mVoiceKeyTimer.keyDown();
            dispatchProjectionKeyEvent(CarProjectionManager.KEY_EVENT_VOICE_SEARCH_KEY_DOWN);
        } else if (action == KeyEvent.ACTION_UP) {
            if (mVoiceKeyTimer.keyUp()) {
                // Long press already handled by handleVoiceAssistLongPress(), nothing more to do.
                // Hand it off to projection, if it's interested, otherwise we're done.
                dispatchProjectionKeyEvent(
                        CarProjectionManager.KEY_EVENT_VOICE_SEARCH_LONG_PRESS_KEY_UP);
                return;
            }

            if (dispatchProjectionKeyEvent(
                    CarProjectionManager.KEY_EVENT_VOICE_SEARCH_SHORT_PRESS_KEY_UP)) {
                return;
            }

            launchDefaultVoiceAssistantHandler();
        }
    }

    private void handleVoiceAssistLongPress() {
        // If projection wants this event, let it take it.
        if (dispatchProjectionKeyEvent(
                CarProjectionManager.KEY_EVENT_VOICE_SEARCH_LONG_PRESS_KEY_DOWN)) {
            return;
        }
        // Otherwise, try to launch voice recognition on a BT device.
        if (launchBluetoothVoiceRecognition()) {
            return;
        }
        // Finally, fallback to the default voice assist handling.
        launchDefaultVoiceAssistantHandler();
    }

    private void handleCallKey(KeyEvent event) {
        int action = event.getAction();
        if (action == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
            mCallKeyTimer.keyDown();
            dispatchProjectionKeyEvent(CarProjectionManager.KEY_EVENT_CALL_KEY_DOWN);
        } else if (action == KeyEvent.ACTION_UP) {
            if (mCallKeyTimer.keyUp()) {
                // Long press already handled by handleCallLongPress(), nothing more to do.
                // Hand it off to projection, if it's interested, otherwise we're done.
                dispatchProjectionKeyEvent(CarProjectionManager.KEY_EVENT_CALL_LONG_PRESS_KEY_UP);
                return;
            }

            if (acceptCallIfRinging()) {
                // Ringing call answered, nothing more to do.
                return;
            }

            if (dispatchProjectionKeyEvent(
                    CarProjectionManager.KEY_EVENT_CALL_SHORT_PRESS_KEY_UP)) {
                return;
            }

            launchDialerHandler();
        }
    }

    private void handleCallLongPress() {
        // Long-press answers call if ringing, same as short-press.
        if (acceptCallIfRinging()) {
            return;
        }

        if (dispatchProjectionKeyEvent(CarProjectionManager.KEY_EVENT_CALL_LONG_PRESS_KEY_DOWN)) {
            return;
        }

        dialLastCallHandler();
    }

    private boolean dispatchProjectionKeyEvent(@CarProjectionManager.KeyEventNum int event) {
        CarProjectionManager.ProjectionKeyEventHandler projectionKeyEventHandler;
        synchronized (this) {
            projectionKeyEventHandler = mProjectionKeyEventHandler;
            if (projectionKeyEventHandler == null || !mProjectionKeyEventsSubscribed.get(event)) {
                // No event handler, or event handler doesn't want this event - we're done.
                return false;
            }
        }

        projectionKeyEventHandler.onKeyEvent(event);
        return true;
    }

    private void launchDialerHandler() {
        Log.i(CarLog.TAG_INPUT, "call key, launch dialer intent");
        Intent dialerIntent = new Intent(Intent.ACTION_DIAL);
        mContext.startActivityAsUser(dialerIntent, null, UserHandle.CURRENT_OR_SELF);
    }

    private void dialLastCallHandler() {
        Log.i(CarLog.TAG_INPUT, "call key, dialing last call");

        String lastNumber = mLastCalledNumberSupplier.get();
        if (!TextUtils.isEmpty(lastNumber)) {
            Intent callLastNumberIntent = new Intent(Intent.ACTION_CALL)
                    .setData(Uri.fromParts("tel", lastNumber, null))
                    .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            mContext.startActivityAsUser(callLastNumberIntent, null, UserHandle.CURRENT_OR_SELF);
        }
    }

    private boolean acceptCallIfRinging() {
        if (mTelecomManager != null && mTelecomManager.isRinging()) {
            Log.i(CarLog.TAG_INPUT, "call key while ringing. Answer the call!");
            mTelecomManager.acceptRingingCall();
            return true;
        }

        return false;
    }

    private boolean launchBluetoothVoiceRecognition() {
        synchronized (mBluetoothProfileServiceListener) {
            if (mBluetoothHeadsetClient == null) {
                return false;
            }
            // getConnectedDevices() does not make any guarantees about the order of the returned
            // list. As of 2019-02-26, this code is only triggered through a long-press of the
            // voice recognition key, so handling of multiple connected devices that support voice
            // recognition is not expected to be a primary use case.
            List<BluetoothDevice> devices = mBluetoothHeadsetClient.getConnectedDevices();
            if (devices != null) {
                for (BluetoothDevice device : devices) {
                    Bundle bundle = mBluetoothHeadsetClient.getCurrentAgFeatures(device);
                    if (bundle == null || !bundle.getBoolean(
                            BluetoothHeadsetClient.EXTRA_AG_FEATURE_VOICE_RECOGNITION)) {
                        continue;
                    }
                    if (mBluetoothHeadsetClient.startVoiceRecognition(device)) {
                        Log.d(CarLog.TAG_INPUT, "started voice recognition on BT device at "
                                + device.getAddress());
                        return true;
                    }
                }
            }
        }
        return false;
    }

    private void launchDefaultVoiceAssistantHandler() {
        Log.i(CarLog.TAG_INPUT, "voice key, invoke AssistUtils");

        if (mAssistUtils.getAssistComponentForUser(ActivityManager.getCurrentUser()) == null) {
            Log.w(CarLog.TAG_INPUT, "Unable to retrieve assist component for current user");
            return;
        }

        final Bundle args = new Bundle();
        args.putBoolean(EXTRA_CAR_PUSH_TO_TALK, true);

        mAssistUtils.showSessionForActiveService(args,
                SHOW_SOURCE_PUSH_TO_TALK, mShowCallback, null /*activityToken*/);
    }

    private void handleInstrumentClusterKey(KeyEvent event) {
        KeyEventListener listener = null;
        synchronized (this) {
            listener = mInstrumentClusterKeyListener;
        }
        if (listener == null) {
            return;
        }
        listener.onKeyEvent(event);
    }

    @Override
    public synchronized void dump(PrintWriter writer) {
        writer.println("*Input Service*");
        writer.println("mCustomInputServiceComponent: " + mCustomInputServiceComponent);
        writer.println("mCarInputListenerBound: " + mCarInputListenerBound);
        writer.println("mCarInputListener: " + mCarInputListener);
        writer.println("Long-press delay: " + mLongPressDelaySupplier.getAsInt() + "ms");
    }

    private boolean bindCarInputService() {
        if (mCustomInputServiceComponent == null) {
            Log.i(CarLog.TAG_INPUT, "Custom input service was not configured");
            return false;
        }

        Log.d(CarLog.TAG_INPUT, "bindCarInputService, component: " + mCustomInputServiceComponent);

        Intent intent = new Intent();
        Bundle extras = new Bundle();
        extras.putBinder(CarInputHandlingService.INPUT_CALLBACK_BINDER_KEY, mCallback);
        intent.putExtras(extras);
        intent.setComponent(mCustomInputServiceComponent);
        return mContext.bindService(intent, mInputServiceConnection, Context.BIND_AUTO_CREATE);
    }
}
