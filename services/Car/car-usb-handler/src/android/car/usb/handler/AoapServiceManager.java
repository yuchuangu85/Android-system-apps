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

package android.car.usb.handler;

import static android.car.AoapService.KEY_DEVICE;
import static android.car.AoapService.KEY_RESULT;
import static android.car.AoapService.MSG_CAN_SWITCH_TO_AOAP;
import static android.car.AoapService.MSG_CAN_SWITCH_TO_AOAP_RESPONSE;
import static android.car.AoapService.MSG_NEW_DEVICE_ATTACHED;
import static android.car.AoapService.MSG_NEW_DEVICE_ATTACHED_RESPONSE;

import android.annotation.Nullable;
import android.annotation.WorkerThread;
import android.car.AoapService;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.hardware.usb.UsbDevice;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.Log;
import android.util.SparseArray;

import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Manages connections to {@link android.car.AoapService} (AOAP handler apps). */
public class AoapServiceManager {
    private static final String TAG = AoapServiceManager.class.getSimpleName();

    private static final int MSG_DISCONNECT = 1;
    private static final int DISCONNECT_DELAY_MS = 30000;

    private static final int INVOCATION_TIMEOUT_MS = 5000;


    private final HashMap<ComponentName, AoapServiceConnection> mConnections = new HashMap<>();
    private Context mContext;
    private final Object mLock = new Object();
    private final HandlerThread mHandlerThread;
    private final Handler mHandler;

    public AoapServiceManager(Context context) {
        mContext = context;

        mHandlerThread = new HandlerThread(TAG);
        mHandlerThread.start();

        mHandler = new Handler(mHandlerThread.getLooper()) {
            @Override
            public void handleMessage(Message msg) {
                if (msg.what == MSG_DISCONNECT) {
                    removeConnection((AoapServiceConnection) msg.obj);
                } else {
                    Log.e(TAG, "Unexpected message " + msg.what);
                }
            }
        };
    }

    /**
     * Calls synchronously with timeout {@link #INVOCATION_TIMEOUT_MS} to the given service to check
     * if it supports the device.
     */
    @WorkerThread
    public boolean isDeviceSupported(UsbDevice device, ComponentName serviceName) {
        final AoapServiceConnection connection = getConnectionOrNull(serviceName);
        if (connection == null) {
            return false;
        }

        try {
            return connection.isDeviceSupported(device)
                    .get(INVOCATION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            Log.w(TAG, "Failed to get response isDeviceSupported from " + serviceName, e);
            return false;
        }
    }

    /**
     * Calls synchronously with timeout {@link #INVOCATION_TIMEOUT_MS} to the given service to check
     * if the device can be switched to AOAP mode now.
     */
    @WorkerThread
    public boolean canSwitchDeviceToAoap(UsbDevice device, ComponentName serviceName) {
        final AoapServiceConnection connection = getConnectionOrNull(serviceName);
        if (connection == null) {
            return false;
        }

        try {
            return connection.canSwitchDeviceToAoap(device)
                    .get(INVOCATION_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        } catch (ExecutionException | InterruptedException | TimeoutException e) {
            Log.w(TAG, "Failed to get response of canSwitchDeviceToAoap from " + serviceName, e);
            return false;
        }
    }

    @Nullable
    private AoapServiceConnection getConnectionOrNull(ComponentName name) {
        AoapServiceConnection connection;
        synchronized (mLock) {
            connection = mConnections.get(name);
            if (connection != null) {
                postponeServiceDisconnection(connection);
                return connection;
            }

            connection = new AoapServiceConnection(name, this, mHandlerThread.getLooper());
            boolean bound = mContext.bindService(
                    createIntent(name), connection, Context.BIND_AUTO_CREATE);
            if (bound) {
                mConnections.put(name, connection);
                postponeServiceDisconnection(connection);
            } else {
                Log.w(TAG, "Failed to bind to service " + name);
                return null;
            }
        }
        return connection;
    }

    private void postponeServiceDisconnection(AoapServiceConnection connection) {
        mHandler.removeMessages(MSG_DISCONNECT, connection);
        mHandler.sendMessageDelayed(mHandler.obtainMessage(MSG_DISCONNECT, connection),
                DISCONNECT_DELAY_MS);
    }

    private static Intent createIntent(ComponentName name) {
        Intent intent = new Intent();
        intent.setComponent(name);
        return intent;
    }

    private void removeConnection(AoapServiceConnection connection) {
        Log.i(TAG, "Removing connection to " + connection);
        synchronized (mLock) {
            mConnections.remove(connection.mComponentName);
            if (connection.mBound) {
                mContext.unbindService(connection);
                connection.mBound = false;
            }
        }
    }

    private static class AoapServiceConnection implements ServiceConnection {
        private Messenger mOutgoingMessenger;
        private boolean mBound;
        private final CompletableFuture<Void> mConnected = new CompletableFuture<>();
        private final SparseArray<CompletableFuture<Bundle>> mExpectedResponses =
                new SparseArray<>();
        private final ComponentName mComponentName;
        private final WeakReference<AoapServiceManager> mManagerRef;
        private final Messenger mIncomingMessenger;
        private final Object mLock = new Object();

        private AoapServiceConnection(ComponentName name, AoapServiceManager manager,
                Looper looper) {
            mComponentName = name;
            mManagerRef = new WeakReference<>(manager);
            mIncomingMessenger = new Messenger(new Handler(looper) {
                @Override
                public void handleMessage(Message msg) {
                    onResponse(msg);
                }
            });
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (service == null) {
                Log.e(TAG, "Binder object was not provided on service connection to " + name);
                return;
            }

            synchronized (mLock) {
                mBound = true;
                mOutgoingMessenger = new Messenger(service);
            }
            mConnected.complete(null);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            synchronized (mLock) {
                mOutgoingMessenger = null;
                mBound = false;
            }

            final AoapServiceManager mgr = mManagerRef.get();
            if (mgr != null) {
                mgr.removeConnection(this);
            }
        }

        private void onResponse(Message message) {
            final CompletableFuture<Bundle> response;
            synchronized (mLock) {
                response = mExpectedResponses.removeReturnOld(message.what);
            }
            if (response == null) {
                Log.e(TAG, "Received unexpected response " + message.what + ", expected: "
                        + mExpectedResponses);
                return;
            }

            if (message.getData() == null) {
                throw new IllegalArgumentException("Received response msg " + message.what
                        + " without data");
            }
            Log.i(TAG, "onResponse msg: " + message.what + ", data: " + message.getData());
            boolean res = response.complete(message.getData());
            if (!res) {
                Log.w(TAG, "Failed to complete future " + response);
            }
        }

        CompletableFuture<Boolean> isDeviceSupported(UsbDevice device) {
            return sendMessageForResult(
                    MSG_NEW_DEVICE_ATTACHED,
                    MSG_NEW_DEVICE_ATTACHED_RESPONSE,
                    createUsbDeviceData(device))
                    .thenApply(this::isResultOk);

        }

        CompletableFuture<Boolean> canSwitchDeviceToAoap(UsbDevice device) {
            return sendMessageForResult(
                    MSG_CAN_SWITCH_TO_AOAP,
                    MSG_CAN_SWITCH_TO_AOAP_RESPONSE,
                    createUsbDeviceData(device))
                    .thenApply(this::isResultOk);
        }

        private boolean isResultOk(Bundle data) {
            int result = data.getInt(KEY_RESULT);
            Log.i(TAG, "Got result: " + data);
            return AoapService.RESULT_OK == result;
        }

        private static Bundle createUsbDeviceData(UsbDevice device) {
            Bundle data = new Bundle(1);
            data.putParcelable(KEY_DEVICE, device);
            return data;
        }

        private CompletableFuture<Bundle> sendMessageForResult(
                int msgRequest, int msgResponse, Bundle data) {
            return mConnected.thenCompose(x -> {
                CompletableFuture<Bundle> responseFuture = new CompletableFuture<>();
                Messenger messenger;
                synchronized (mLock) {
                    mExpectedResponses.put(msgResponse, responseFuture);
                    messenger = mOutgoingMessenger;
                }
                send(messenger, msgRequest, data);

                return responseFuture;
            });
        }

        private void send(Messenger messenger, int req, Bundle data) {
            Message msg = Message.obtain(null, req, null);
            msg.replyTo = mIncomingMessenger;
            msg.setData(data);
            try {
                messenger.send(msg);
            } catch (RemoteException e) {
                throw new RuntimeException("Connection broken with " + mComponentName, e);
            }
        }

        @Override
        public String toString() {
            return "AoapServiceConnection{"
                    + "mBound=" + mBound
                    + ", mConnected=" + mConnected
                    + ", mExpectedResponses=" + mExpectedResponses
                    + ", mComponentName=" + mComponentName
                    + '}';
        }
    }
}
