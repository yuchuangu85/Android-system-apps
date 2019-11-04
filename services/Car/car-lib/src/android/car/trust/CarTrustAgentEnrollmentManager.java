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

package android.car.trust;

import static android.car.Car.PERMISSION_CAR_ENROLL_TRUST;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.annotation.SystemApi;
import android.bluetooth.BluetoothDevice;
import android.car.CarManagerBase;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;
import java.util.List;


/**
 * APIs to help enroll a remote device as a trusted device that can be used to authenticate a user
 * in the head unit.
 * <p>
 * The call sequence to add a new trusted device from the client should be as follows:
 * <ol>
 * <li> setEnrollmentCallback()
 * <li> setBleCallback(bleCallback)
 * <li> startEnrollmentAdvertising()
 * <li> wait for onEnrollmentAdvertisingStarted() or
 * <li> wait for onBleEnrollmentDeviceConnected() and check if the device connected is the right
 * one.
 * <li> initiateEnrollmentHandshake()
 * <li> wait for onAuthStringAvailable() to get the pairing code to display to the user
 * <li> enrollmentHandshakeAccepted() after user confirms the pairing code
 * <li> wait for onEscrowTokenAdded()
 * <li> Authenticate user's credentials by showing the lock screen
 * <li> activateToken()
 * <li> wait for onEscrowTokenActiveStateChanged() to add the device as a trusted device and show
 * in the list
 * </ol>
 *
 * @hide
 */
@SystemApi
public final class CarTrustAgentEnrollmentManager implements CarManagerBase {
    private static final String TAG = "CarTrustEnrollMgr";
    private static final String KEY_HANDLE = "handle";
    private static final String KEY_ACTIVE = "active";
    private static final int MSG_ENROLL_ADVERTISING_STARTED = 0;
    private static final int MSG_ENROLL_ADVERTISING_FAILED = 1;
    private static final int MSG_ENROLL_DEVICE_CONNECTED = 2;
    private static final int MSG_ENROLL_DEVICE_DISCONNECTED = 3;
    private static final int MSG_ENROLL_HANDSHAKE_FAILURE = 4;
    private static final int MSG_ENROLL_AUTH_STRING_AVAILABLE = 5;
    private static final int MSG_ENROLL_TOKEN_ADDED = 6;
    private static final int MSG_ENROLL_TOKEN_STATE_CHANGED = 7;
    private static final int MSG_ENROLL_TOKEN_REMOVED = 8;

    private final Context mContext;
    private final ICarTrustAgentEnrollment mEnrollmentService;
    private Object mListenerLock = new Object();
    @GuardedBy("mListenerLock")
    private CarTrustAgentEnrollmentCallback mEnrollmentCallback;
    @GuardedBy("mListenerLock")
    private CarTrustAgentBleCallback mBleCallback;
    @GuardedBy("mListenerLock")
    private final ListenerToEnrollmentService mListenerToEnrollmentService =
            new ListenerToEnrollmentService(this);
    private final ListenerToBleService mListenerToBleService = new ListenerToBleService(this);
    private final EventCallbackHandler mEventCallbackHandler;

    /**
     * Enrollment Handshake failed.
     */
    public static final int ENROLLMENT_HANDSHAKE_FAILURE = 1;
    /**
     * Enrollment of a new device is not allowed.  This happens when either the whole feature is
     * disabled or just the enrollment is disabled.  Useful when feature needs to be disabled
     * in a lost/stolen phone scenario.
     */
    public static final int ENROLLMENT_NOT_ALLOWED = 2;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({ENROLLMENT_HANDSHAKE_FAILURE,
            ENROLLMENT_NOT_ALLOWED})
    public @interface TrustedDeviceEnrollmentError {
    }


    /** @hide */
    public CarTrustAgentEnrollmentManager(IBinder service, Context context, Handler handler) {
        mContext = context;
        mEnrollmentService = ICarTrustAgentEnrollment.Stub.asInterface(service);
        mEventCallbackHandler = new EventCallbackHandler(this, handler.getLooper());
    }

    /** @hide */
    @Override
    public synchronized void onCarDisconnected() {
    }

    /**
     * Starts broadcasting enrollment UUID on BLE.
     * Phones can scan and connect for the enrollment process to begin.
     */
    @RequiresPermission(PERMISSION_CAR_ENROLL_TRUST)
    public void startEnrollmentAdvertising() {
        try {
            mEnrollmentService.startEnrollmentAdvertising();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Stops Enrollment advertising.
     */
    @RequiresPermission(PERMISSION_CAR_ENROLL_TRUST)
    public void stopEnrollmentAdvertising() {
        try {
            mEnrollmentService.stopEnrollmentAdvertising();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Confirms that the enrollment handshake has been accepted by the user. This should be called
     * after the user has confirmed the verification code displayed on the UI.
     *
     * @param device the remote Bluetooth device that will receive the signal.
     */
    @RequiresPermission(PERMISSION_CAR_ENROLL_TRUST)
    public void enrollmentHandshakeAccepted(BluetoothDevice device) {
        try {
            mEnrollmentService.enrollmentHandshakeAccepted(device);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Provides an option to quit enrollment if the pairing code doesn't match for example.
     */
    @RequiresPermission(PERMISSION_CAR_ENROLL_TRUST)
    public void terminateEnrollmentHandshake() {
        try {
            mEnrollmentService.terminateEnrollmentHandshake();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Returns {@code true} if the escrow token associated with the given handle is active.
     * <p>
     * When a new escrow token has been added as part of the Trusted device enrollment, the client
     * will receive {@link CarTrustAgentEnrollmentCallback#onEscrowTokenAdded(long)} and
     * {@link CarTrustAgentEnrollmentCallback#onEscrowTokenActiveStateChanged(long, boolean)}
     * callbacks.  This method provides a way to query for the token state at a later point of time.
     *
     * @param handle the handle corresponding to the escrow token
     * @param uid    user id associated with the token
     * @return true if the token is active, false if not
     */
    @RequiresPermission(PERMISSION_CAR_ENROLL_TRUST)
    public boolean isEscrowTokenActive(long handle, int uid) {
        try {
            return mEnrollmentService.isEscrowTokenActive(handle, uid);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Remove the escrow token that is associated with the given handle and uid.
     *
     * @param handle the handle associated with the escrow token
     * @param uid    user id associated with the token
     */
    @RequiresPermission(PERMISSION_CAR_ENROLL_TRUST)
    public void removeEscrowToken(long handle, int uid) {
        try {
            mEnrollmentService.removeEscrowToken(handle, uid);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Remove all of the trusted devices associated with the given user.
     *
     * @param uid User id to remove the devices for
     */
    @RequiresPermission(PERMISSION_CAR_ENROLL_TRUST)
    public void removeAllTrustedDevices(int uid) {
        try {
            mEnrollmentService.removeAllTrustedDevices(uid);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Enable or Disable Trusted device enrollment.  Once disabled, head unit will not broadcast
     * for enrollment until enabled back.
     *
     * @param isEnabled {@code true} enables enrollment.
     */
    @RequiresPermission(PERMISSION_CAR_ENROLL_TRUST)
    public void setTrustedDeviceEnrollmentEnabled(boolean isEnabled) {
        try {
            mEnrollmentService.setTrustedDeviceEnrollmentEnabled(isEnabled);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Enable or disable Unlocking with a trusted device. Once disabled, head unit will not
     * broadcast until enabled back.
     *
     * @param isEnabled {@code true} enables unlock.
     */
    @RequiresPermission(PERMISSION_CAR_ENROLL_TRUST)
    public void setTrustedDeviceUnlockEnabled(boolean isEnabled) {
        try {
            mEnrollmentService.setTrustedDeviceUnlockEnabled(isEnabled);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Register for enrollment event callbacks.
     *
     * @param callback The callback methods to call, null to unregister
     */
    @RequiresPermission(PERMISSION_CAR_ENROLL_TRUST)
    public void setEnrollmentCallback(@Nullable CarTrustAgentEnrollmentCallback callback) {
        if (callback == null) {
            unregisterEnrollmentCallback();
        } else {
            registerEnrollmentCallback(callback);
        }
    }

    private void registerEnrollmentCallback(CarTrustAgentEnrollmentCallback callback) {
        synchronized (mListenerLock) {
            if (callback != null && mEnrollmentCallback == null) {
                try {
                    mEnrollmentService.registerEnrollmentCallback(mListenerToEnrollmentService);
                    mEnrollmentCallback = callback;
                } catch (RemoteException e) {
                    throw e.rethrowFromSystemServer();
                }
            }
        }
    }

    private void unregisterEnrollmentCallback() {
        synchronized (mListenerLock) {
            if (mEnrollmentCallback != null) {
                try {
                    mEnrollmentService.unregisterEnrollmentCallback(mListenerToEnrollmentService);
                } catch (RemoteException e) {
                    throw e.rethrowFromSystemServer();
                }
                mEnrollmentCallback = null;
            }
        }
    }

    /**
     * Register for general BLE callbacks
     *
     * @param callback The callback methods to call, null to unregister
     */
    @RequiresPermission(PERMISSION_CAR_ENROLL_TRUST)
    public void setBleCallback(@Nullable CarTrustAgentBleCallback callback) {
        if (callback == null) {
            unregisterBleCallback();
        } else {
            registerBleCallback(callback);
        }
    }

    private void registerBleCallback(CarTrustAgentBleCallback callback) {
        synchronized (mListenerLock) {
            if (callback != null && mBleCallback == null) {
                try {
                    mEnrollmentService.registerBleCallback(mListenerToBleService);
                    mBleCallback = callback;
                } catch (RemoteException e) {
                    throw e.rethrowFromSystemServer();
                }
            }
        }
    }

    private void unregisterBleCallback() {
        synchronized (mListenerLock) {
            if (mBleCallback != null) {
                try {
                    mEnrollmentService.unregisterBleCallback(mListenerToBleService);
                } catch (RemoteException e) {
                    throw e.rethrowFromSystemServer();
                }
                mBleCallback = null;
            }
        }
    }

    /**
     * Provides a list that contains information about the enrolled devices for the given user id.
     * <p>
     * Each enrollment handle corresponds to a trusted device for the given user.
     *
     * @param uid user id.
     * @return list of the Enrollment handles and user names for the user id.
     */
    @RequiresPermission(PERMISSION_CAR_ENROLL_TRUST)
    @NonNull
    public List<TrustedDeviceInfo> getEnrolledDeviceInfoForUser(int uid) {
        try {
            return mEnrollmentService.getEnrolledDeviceInfosForUser(uid);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    private Handler getEventCallbackHandler() {
        return mEventCallbackHandler;
    }

    /**
     * Callback interface for Trusted device enrollment applications to implement.  The applications
     * get notified on various enrollment state change events.
     */
    public interface CarTrustAgentEnrollmentCallback {
        /**
         * Communicate about failure/timeouts in the handshake process.  BluetoothDevice will be
         * null when the returned error code is {@link #ENROLLMENT_NOT_ALLOWED}.
         *
         * @param device    the remote device trying to enroll
         * @param errorCode information on what failed.
         */
        void onEnrollmentHandshakeFailure(@Nullable BluetoothDevice device,
                @TrustedDeviceEnrollmentError int errorCode);

        /**
         * Present the pairing/authentication string to the user.
         *
         * @param device     the remote device trying to enroll
         * @param authString the authentication string to show to the user to confirm across
         *                   both devices
         */
        void onAuthStringAvailable(BluetoothDevice device, String authString);

        /**
         * Escrow token was received and the Trust Agent framework has generated a corresponding
         * handle.
         *
         * @param handle the handle associated with the escrow token.
         */
        void onEscrowTokenAdded(long handle);

        /**
         * Escrow token was removed as a result of a call to {@link #removeEscrowToken(long handle,
         * int uid)}. The peer device associated with this token is not trusted for authentication
         * anymore.
         *
         * @param handle the handle associated with the escrow token.
         */
        void onEscrowTokenRemoved(long handle);


        /**
         * Escrow token's active state changed.
         *
         * @param handle the handle associated with the escrow token
         * @param active True if token has been activated, false if not.
         */
        void onEscrowTokenActiveStateChanged(long handle, boolean active);
    }

    /**
     * Callback interface for Trusted device enrollment applications to implement.  The applications
     * get notified on various BLE state change events that happen during trusted device enrollment.
     */
    public interface CarTrustAgentBleCallback {
        /**
         * Indicates a remote device connected on BLE.
         */
        void onBleEnrollmentDeviceConnected(BluetoothDevice device);

        /**
         * Indicates a remote device disconnected on BLE.
         */
        void onBleEnrollmentDeviceDisconnected(BluetoothDevice device);

        /**
         * Indicates that the device is broadcasting for trusted device enrollment on BLE.
         */
        void onEnrollmentAdvertisingStarted();

        /**
         * Indicates a failure in BLE broadcasting for enrollment.
         */
        void onEnrollmentAdvertisingFailed();
    }

    private static final class ListenerToEnrollmentService extends
            ICarTrustAgentEnrollmentCallback.Stub {
        private final WeakReference<CarTrustAgentEnrollmentManager> mMgr;

        ListenerToEnrollmentService(CarTrustAgentEnrollmentManager mgr) {
            mMgr = new WeakReference<>(mgr);
        }

        /**
         * Communicate about failure/timeouts in the handshake process.
         */
        @Override
        public void onEnrollmentHandshakeFailure(BluetoothDevice device,
                @TrustedDeviceEnrollmentError int errorCode) {
            CarTrustAgentEnrollmentManager enrollmentManager = mMgr.get();
            if (enrollmentManager == null) {
                return;
            }
            enrollmentManager.getEventCallbackHandler().sendMessage(
                    enrollmentManager.getEventCallbackHandler().obtainMessage(
                            MSG_ENROLL_HANDSHAKE_FAILURE, new AuthInfo(device, null, errorCode)));
        }

        /**
         * Present the pairing/authentication string to the user.
         */
        @Override
        public void onAuthStringAvailable(BluetoothDevice device, String authString) {
            CarTrustAgentEnrollmentManager enrollmentManager = mMgr.get();
            if (enrollmentManager == null) {
                return;
            }
            enrollmentManager.getEventCallbackHandler().sendMessage(
                    enrollmentManager.getEventCallbackHandler().obtainMessage(
                            MSG_ENROLL_AUTH_STRING_AVAILABLE, new AuthInfo(device, authString, 0)));
        }

        /**
         * Escrow token was received and the Trust Agent framework has generated a corresponding
         * handle.
         */
        @Override
        public void onEscrowTokenAdded(long handle) {
            CarTrustAgentEnrollmentManager enrollmentManager = mMgr.get();
            if (enrollmentManager == null) {
                return;
            }
            Message message = enrollmentManager.getEventCallbackHandler().obtainMessage(
                    MSG_ENROLL_TOKEN_ADDED);
            Bundle data = new Bundle();
            data.putLong(KEY_HANDLE, handle);
            message.setData(data);
            enrollmentManager.getEventCallbackHandler().sendMessage(message);
        }

        /**
         * Escrow token was removed.
         */
        @Override
        public void onEscrowTokenRemoved(long handle) {
            CarTrustAgentEnrollmentManager enrollmentManager = mMgr.get();
            if (enrollmentManager == null) {
                return;
            }
            Message message = enrollmentManager.getEventCallbackHandler().obtainMessage(
                    MSG_ENROLL_TOKEN_REMOVED);
            Bundle data = new Bundle();
            data.putLong(KEY_HANDLE, handle);
            message.setData(data);
            enrollmentManager.getEventCallbackHandler().sendMessage(message);
        }

        /**
         * Escrow token's active state changed.
         */
        @Override
        public void onEscrowTokenActiveStateChanged(long handle, boolean active) {
            CarTrustAgentEnrollmentManager enrollmentManager = mMgr.get();
            if (enrollmentManager == null) {
                return;
            }
            Message message = enrollmentManager.getEventCallbackHandler().obtainMessage(
                    MSG_ENROLL_TOKEN_STATE_CHANGED);
            Bundle data = new Bundle();
            data.putLong(KEY_HANDLE, handle);
            data.putBoolean(KEY_ACTIVE, active);
            message.setData(data);
            enrollmentManager.getEventCallbackHandler().sendMessage(message);
        }
    }

    private static final class ListenerToBleService extends ICarTrustAgentBleCallback.Stub {
        private final WeakReference<CarTrustAgentEnrollmentManager> mMgr;

        ListenerToBleService(CarTrustAgentEnrollmentManager mgr) {
            mMgr = new WeakReference<>(mgr);
        }

        /**
         * Called when the GATT server is started and BLE is successfully advertising for
         * enrollment.
         */
        public void onEnrollmentAdvertisingStarted() {
            CarTrustAgentEnrollmentManager enrollmentManager = mMgr.get();
            if (enrollmentManager == null) {
                return;
            }
            enrollmentManager.getEventCallbackHandler().sendMessage(
                    enrollmentManager.getEventCallbackHandler().obtainMessage(
                            MSG_ENROLL_ADVERTISING_STARTED));
        }

        /**
         * Called when the BLE enrollment advertisement fails to start.
         * see AdvertiseCallback#ADVERTISE_FAILED_* for possible error codes.
         */
        public void onEnrollmentAdvertisingFailed() {
            CarTrustAgentEnrollmentManager enrollmentManager = mMgr.get();
            if (enrollmentManager == null) {
                return;
            }
            enrollmentManager.getEventCallbackHandler().sendMessage(
                    enrollmentManager.getEventCallbackHandler().obtainMessage(
                            MSG_ENROLL_ADVERTISING_FAILED));
        }

        /**
         * Called when a remote device is connected on BLE.
         */
        public void onBleEnrollmentDeviceConnected(BluetoothDevice device) {
            CarTrustAgentEnrollmentManager enrollmentManager = mMgr.get();
            if (enrollmentManager == null) {
                return;
            }
            enrollmentManager.getEventCallbackHandler().sendMessage(
                    enrollmentManager.getEventCallbackHandler().obtainMessage(
                            MSG_ENROLL_DEVICE_CONNECTED, device));
        }

        /**
         * Called when a remote device is disconnected on BLE.
         */
        public void onBleEnrollmentDeviceDisconnected(BluetoothDevice device) {
            CarTrustAgentEnrollmentManager enrollmentManager = mMgr.get();
            if (enrollmentManager == null) {
                return;
            }
            enrollmentManager.getEventCallbackHandler().sendMessage(
                    enrollmentManager.getEventCallbackHandler().obtainMessage(
                            MSG_ENROLL_DEVICE_DISCONNECTED, device));
        }
    }

    /**
     * Callback Handler to handle dispatching the enrollment state changes to the corresponding
     * listeners
     */
    private static final class EventCallbackHandler extends Handler {
        private final WeakReference<CarTrustAgentEnrollmentManager> mEnrollmentManager;

        EventCallbackHandler(CarTrustAgentEnrollmentManager manager, Looper looper) {
            super(looper);
            mEnrollmentManager = new WeakReference<>(manager);
        }

        @Override
        public void handleMessage(Message message) {
            CarTrustAgentEnrollmentManager enrollmentManager = mEnrollmentManager.get();
            if (enrollmentManager == null) {
                return;
            }
            switch (message.what) {
                case MSG_ENROLL_ADVERTISING_STARTED:
                case MSG_ENROLL_ADVERTISING_FAILED:
                case MSG_ENROLL_DEVICE_CONNECTED:
                case MSG_ENROLL_DEVICE_DISCONNECTED:
                    enrollmentManager.dispatchBleCallback(message);
                    break;
                case MSG_ENROLL_HANDSHAKE_FAILURE:
                case MSG_ENROLL_AUTH_STRING_AVAILABLE:
                case MSG_ENROLL_TOKEN_ADDED:
                case MSG_ENROLL_TOKEN_STATE_CHANGED:
                case MSG_ENROLL_TOKEN_REMOVED:
                    enrollmentManager.dispatchEnrollmentCallback(message);
                    break;
                default:
                    Log.e(TAG, "Unknown message:" + message.what);
                    break;
            }
        }
    }

    /**
     * Dispatch BLE related state change callbacks
     *
     * @param message Message to handle and dispatch
     */
    private void dispatchBleCallback(Message message) {
        CarTrustAgentBleCallback bleCallback;
        synchronized (mListenerLock) {
            bleCallback = mBleCallback;
        }
        if (bleCallback == null) {
            return;
        }
        switch (message.what) {
            case MSG_ENROLL_ADVERTISING_STARTED:
                bleCallback.onEnrollmentAdvertisingStarted();
                break;
            case MSG_ENROLL_ADVERTISING_FAILED:
                bleCallback.onEnrollmentAdvertisingFailed();
                break;
            case MSG_ENROLL_DEVICE_CONNECTED:
                bleCallback.onBleEnrollmentDeviceConnected((BluetoothDevice) message.obj);
                break;
            case MSG_ENROLL_DEVICE_DISCONNECTED:
                bleCallback.onBleEnrollmentDeviceDisconnected((BluetoothDevice) message.obj);
                break;
            default:
                break;
        }
    }

    /**
     * Dispatch Enrollment related state changes to the listener.
     *
     * @param message Message to handle and dispatch
     */
    private void dispatchEnrollmentCallback(Message message) {
        CarTrustAgentEnrollmentCallback enrollmentCallback;
        synchronized (mListenerLock) {
            enrollmentCallback = mEnrollmentCallback;
        }
        if (enrollmentCallback == null) {
            return;
        }
        AuthInfo auth;
        Bundle data;
        switch (message.what) {
            case MSG_ENROLL_HANDSHAKE_FAILURE:
                auth = (AuthInfo) message.obj;
                enrollmentCallback.onEnrollmentHandshakeFailure(auth.mDevice, auth.mErrorCode);
                break;
            case MSG_ENROLL_AUTH_STRING_AVAILABLE:
                auth = (AuthInfo) message.obj;
                if (auth.mDevice != null && auth.mAuthString != null) {
                    enrollmentCallback.onAuthStringAvailable(auth.mDevice, auth.mAuthString);
                }
                break;
            case MSG_ENROLL_TOKEN_ADDED:
                data = message.getData();
                if (data == null) {
                    break;
                }
                enrollmentCallback.onEscrowTokenAdded(data.getLong(KEY_HANDLE));
                break;
            case MSG_ENROLL_TOKEN_STATE_CHANGED:
                data = message.getData();
                if (data == null) {
                    break;
                }
                enrollmentCallback.onEscrowTokenActiveStateChanged(data.getLong(KEY_HANDLE),
                        data.getBoolean(KEY_ACTIVE));
                break;
            case MSG_ENROLL_TOKEN_REMOVED:
                data = message.getData();
                if (data == null) {
                    break;
                }
                enrollmentCallback.onEscrowTokenRemoved(data.getLong(KEY_HANDLE));
                break;
            default:
                break;
        }
    }

    /**
     * Container class to pass information through a Message to the handler.
     */
    private static class AuthInfo {
        final BluetoothDevice mDevice;
        @Nullable
        final String mAuthString;
        final int mErrorCode;

        AuthInfo(BluetoothDevice device, @Nullable String authString,
                @TrustedDeviceEnrollmentError int errorCode) {
            mDevice = device;
            mAuthString = authString;
            mErrorCode = errorCode;
        }
    }
}
