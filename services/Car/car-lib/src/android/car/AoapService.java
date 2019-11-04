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

package android.car;

import static com.android.internal.util.Preconditions.checkNotNull;

import android.annotation.IntDef;
import android.annotation.MainThread;
import android.annotation.NonNull;
import android.annotation.SystemApi;
import android.app.Service;
import android.content.Intent;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.ref.WeakReference;

/**
 * The service that must be implemented by USB AOAP handler system apps. The app must hold the
 * following permission: {@code android.car.permission.CAR_HANDLE_USB_AOAP_DEVICE}.
 *
 * <p>This service gets bound by the framework and the service needs to be protected by
 * {@code android.permission.MANAGE_USB} permission to ensure nobody else can
 * bind to the service. At most only one client should be bound at a time.
 *
 * @hide
 */
@SystemApi
public abstract class AoapService extends Service {
    private static final String TAG = AoapService.class.getSimpleName();

    /** Indicates success or confirmation. */
    public static final int RESULT_OK = 0;

    /**
     * Indicates that the device is not supported by this service and system shouldn't associate
     * given device with this service.
     */
    public static final int RESULT_DEVICE_NOT_SUPPORTED = 1;

    /**
     * Indicates that device shouldn't be switch to AOAP mode at this time.
     */
    public static final int RESULT_DO_NOT_SWITCH_TO_AOAP = 2;

    /** @hide */
    @IntDef(value = {
            RESULT_OK, RESULT_DEVICE_NOT_SUPPORTED, RESULT_DO_NOT_SWITCH_TO_AOAP
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Result {}


    /**
     * A message sent from the system USB handler service to AOAP handler service to check if the
     * device is supported. The message must have {@link #KEY_DEVICE} with {@link UsbDevice} object.
     *
     * @hide
     */
    public static final int MSG_NEW_DEVICE_ATTACHED = 1;

    /**
     * A response message for {@link #MSG_NEW_DEVICE_ATTACHED}. Must contain {@link #KEY_RESULT}
     * with one of the {@code RESULT_*} constant.
     *
     * @hide */
    public static final int MSG_NEW_DEVICE_ATTACHED_RESPONSE = 2;

    /**
     * A message sent from the system USB handler service to AOAP handler service to check if the
     * device can be switched to AOAP mode. The message must have {@link #KEY_DEVICE} with
     * {@link UsbDevice} object.
     *
     * @hide
     */
    public static final int MSG_CAN_SWITCH_TO_AOAP = 3;

    /**
     * A response message for {@link #MSG_CAN_SWITCH_TO_AOAP}. Must contain {@link #KEY_RESULT}
     * with one of the {@code RESULT_*} constant.
     *
     * @hide */
    public static final int MSG_CAN_SWITCH_TO_AOAP_RESPONSE = 4;

    /** @hide */
    public static final String KEY_DEVICE = "usb-device";

    /** @hide */
    public static final String KEY_RESULT = "result";


    /**
     * Returns {@code true} if the given USB device is supported by this service.
     *
     * <p>The device is not expected to be in AOAP mode when this method is called. The purpose of
     * this method is just to give the service a chance to tell whether based on the information
     * provided in {@link UsbDevice} class (e.g. PID/VID) this service supports or doesn't support
     * given device.
     *
     * <p>The method must return one of the following status: {@link #RESULT_OK} or
     * {@link #RESULT_DEVICE_NOT_SUPPORTED}
     */
    @MainThread
    public abstract @Result int isDeviceSupported(@NonNull UsbDevice device);

    /**
     * This method will be called at least once per connection session before switching device into
     * AOAP mode.
     *
     * <p>The device is connected, but not in AOAP mode yet. Implementors of this method may ask
     * the framework to ignore this device for now and do not switch to AOAP. This may make sense if
     * a connection to the device has been established through other means, and switching the device
     * to AOAP would break that connection.
     *
     * <p>Note: the method may be called only if this device was claimed to be supported in
     * {@link #isDeviceSupported(UsbDevice)} method, and this app has been chosen to handle the
     * device.
     *
     * <p>The method must return one of the following status: {@link #RESULT_OK},
     * {@link #RESULT_DEVICE_NOT_SUPPORTED} or {@link #RESULT_DO_NOT_SWITCH_TO_AOAP}
     */
    @MainThread
    public @Result int canSwitchToAoap(@NonNull UsbDevice device) {
        return RESULT_OK;
    }

    private Messenger mMessenger;
    private boolean mBound;

    @Override
    public void onCreate() {
        super.onCreate();
        mMessenger = new Messenger(new IncomingHandler(this));
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (mBound) {
            Log.w(TAG, "Received onBind event when the service was already bound");
        }
        mBound = true;
        return mMessenger.getBinder();
    }

    @Override
    public boolean onUnbind(Intent intent) {
        mBound = false;
        return super.onUnbind(intent);
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        writer.write("Bound: " + mBound);
    }

    private static class IncomingHandler extends Handler {
        private final WeakReference<AoapService> mServiceRef;

        IncomingHandler(AoapService service) {
            super(Looper.getMainLooper());
            mServiceRef = new WeakReference<>(service);
        }

        @Override
        public void handleMessage(Message msg) {
            AoapService service = mServiceRef.get();
            if (service == null) {
                return;
            }
            Bundle data = msg.getData();
            if (data == null) {
                Log.e(TAG, "Ignoring message " + msg.what + " without data");
                return;
            }

            Log.i(TAG, "Message received: " + msg.what);

            switch (msg.what) {
                case MSG_NEW_DEVICE_ATTACHED: {
                    int res = service.isDeviceSupported(
                            checkNotNull(data.getParcelable(KEY_DEVICE)));
                    if (res != RESULT_OK && res != RESULT_DEVICE_NOT_SUPPORTED) {
                        throw new IllegalArgumentException("Result can not be " + res);
                    }
                    sendResponse(msg.replyTo, MSG_NEW_DEVICE_ATTACHED_RESPONSE, res);
                    break;
                }

                case MSG_CAN_SWITCH_TO_AOAP: {
                    int res = service.canSwitchToAoap(
                            checkNotNull(data.getParcelable(KEY_DEVICE)));
                    if (res != RESULT_OK && res != RESULT_DEVICE_NOT_SUPPORTED
                            && res != RESULT_DO_NOT_SWITCH_TO_AOAP) {
                        throw new IllegalArgumentException("Result can not be " + res);
                    }
                    sendResponse(msg.replyTo, MSG_CAN_SWITCH_TO_AOAP_RESPONSE, res);
                    break;
                }

                default:
                    Log.e(TAG, "Unknown message received: " + msg.what);
                    break;
            }
        }

        private void sendResponse(Messenger messenger, int msg, int result) {
            try {
                messenger.send(createResponseMessage(msg, result));
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to send message", e);
            }
        }

        private Message createResponseMessage(int msg, int result) {
            Message response = Message.obtain(null, msg);
            Bundle data = new Bundle();
            data.putInt(KEY_RESULT, result);
            response.setData(data);
            return response;
        }
    }
}
