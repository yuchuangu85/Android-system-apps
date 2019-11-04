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
package android.car.usb.handler;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;

import java.util.ArrayList;
import java.util.List;

/**
 * Controller used to handle USB device connections.
 * TODO: Support handling multiple new USB devices at the same time.
 */
public final class UsbHostController
        implements UsbDeviceHandlerResolver.UsbDeviceHandlerResolverCallback {

    /**
     * Callbacks for controller
     */
    public interface UsbHostControllerCallbacks {
        /** Host controller ready for shutdown */
        void shutdown();
        /** Change of processing state */
        void processingStarted();
        /** Title of processing changed */
        void titleChanged(String title);
        /** Options for USB device changed */
        void optionsUpdated(List<UsbDeviceSettings> options);
    }

    private static final String TAG = UsbHostController.class.getSimpleName();
    private static final boolean LOCAL_LOGD = true;
    private static final boolean LOCAL_LOGV = true;

    private static final int DISPATCH_RETRY_DELAY_MS = 1000;
    private static final int DISPATCH_RETRY_ATTEMPTS = 5;

    private final List<UsbDeviceSettings> mEmptyList = new ArrayList<>();
    private final Context mContext;
    private final UsbHostControllerCallbacks mCallback;
    private final UsbSettingsStorage mUsbSettingsStorage;
    private final UsbManager mUsbManager;
    private final UsbDeviceHandlerResolver mUsbResolver;
    private final UsbHostControllerHandler mHandler;

    private final BroadcastReceiver mUsbBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (UsbManager.ACTION_USB_DEVICE_DETACHED.equals(intent.getAction())) {
                UsbDevice device = intent.<UsbDevice>getParcelableExtra(UsbManager.EXTRA_DEVICE);
                unsetActiveDeviceIfMatch(device);
            } else if (UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(intent.getAction())) {
                UsbDevice device = intent.<UsbDevice>getParcelableExtra(UsbManager.EXTRA_DEVICE);
                setActiveDeviceIfMatch(device);
            }
        }
    };

    @GuardedBy("this")
    private UsbDevice mActiveDevice;

    public UsbHostController(Context context, UsbHostControllerCallbacks callbacks) {
        mContext = context;
        mCallback = callbacks;
        mHandler = new UsbHostControllerHandler(Looper.myLooper());
        mUsbSettingsStorage = new UsbSettingsStorage(context);
        mUsbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        mUsbResolver = new UsbDeviceHandlerResolver(mUsbManager, mContext, this);
        IntentFilter filter = new IntentFilter();
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        context.registerReceiver(mUsbBroadcastReceiver, filter);
    }

    private synchronized void setActiveDeviceIfMatch(UsbDevice device) {
        if (mActiveDevice != null && device != null
                && UsbUtil.isDevicesMatching(device, mActiveDevice)) {
            mActiveDevice = device;
        }
    }

    private synchronized void unsetActiveDeviceIfMatch(UsbDevice device) {
        mHandler.requestDeviceRemoved();
        if (mActiveDevice != null && device != null
                && UsbUtil.isDevicesMatching(device, mActiveDevice)) {
            mActiveDevice = null;
        }
    }

    private synchronized boolean startDeviceProcessingIfNull(UsbDevice device) {
        if (mActiveDevice == null) {
            mActiveDevice = device;
            return true;
        }
        return false;
    }

    private synchronized void stopDeviceProcessing() {
        mActiveDevice = null;
    }

    private synchronized UsbDevice getActiveDevice() {
        return mActiveDevice;
    }

    private boolean deviceMatchedActiveDevice(UsbDevice device) {
        UsbDevice activeDevice = getActiveDevice();
        return activeDevice != null && UsbUtil.isDevicesMatching(activeDevice, device);
    }

    private static String generateTitle(Context context, UsbDevice usbDevice) {
        String manufacturer = usbDevice.getManufacturerName();
        String product = usbDevice.getProductName();
        if (manufacturer == null && product == null) {
            return context.getString(R.string.usb_unknown_device);
        }
        if (manufacturer != null && product != null) {
            return manufacturer + " " + product;
        }
        if (manufacturer != null) {
            return manufacturer;
        }
        return product;
    }

    /**
     * Processes device new device.
     * <p>
     * It will load existing settings or resolve supported handlers.
     */
    public void processDevice(UsbDevice device) {
        if (!startDeviceProcessingIfNull(device)) {
            Log.w(TAG, "Currently, other device is being processed");
        }
        mCallback.optionsUpdated(mEmptyList);
        mCallback.processingStarted();

        UsbDeviceSettings settings = mUsbSettingsStorage.getSettings(device);

        if (settings == null) {
            resolveDevice(device);
        } else {
            Object obj =
                    new UsbHostControllerHandlerDispatchData(
                            device, settings, DISPATCH_RETRY_ATTEMPTS, true);
            Message.obtain(mHandler, UsbHostControllerHandler.MSG_DEVICE_DISPATCH, obj)
                    .sendToTarget();
        }
    }

    /**
     * Applies device settings.
     */
    public void applyDeviceSettings(UsbDeviceSettings settings) {
        mUsbSettingsStorage.saveSettings(settings);
        Message msg = mHandler.obtainMessage();
        msg.obj =
                new UsbHostControllerHandlerDispatchData(
                        getActiveDevice(), settings, DISPATCH_RETRY_ATTEMPTS, false);
        msg.what = UsbHostControllerHandler.MSG_DEVICE_DISPATCH;
        msg.sendToTarget();
    }

    private void resolveDevice(UsbDevice device) {
        mCallback.titleChanged(generateTitle(mContext, device));
        mUsbResolver.resolve(device);
    }

    /**
     * Release object.
     */
    public void release() {
        mContext.unregisterReceiver(mUsbBroadcastReceiver);
        mUsbResolver.release();
    }

    private boolean isDeviceAoapPossible(UsbDevice device) {
        if (AoapInterface.isDeviceInAoapMode(device)) {
            return true;
        }

        UsbManager usbManager = mContext.getSystemService(UsbManager.class);
        UsbDeviceConnection connection = UsbUtil.openConnection(usbManager, device);
        boolean aoapSupported = AoapInterface.isSupported(mContext, device, connection);
        connection.close();

        return aoapSupported;
    }

    @Override
    public void onHandlersResolveCompleted(
            UsbDevice device, List<UsbDeviceSettings> handlers) {
        if (LOCAL_LOGD) {
            Log.d(TAG, "onHandlersResolveComplete: " + device);
        }
        if (deviceMatchedActiveDevice(device)) {
            if (handlers.isEmpty()) {
                onDeviceDispatched();
            } else if (handlers.size() == 1) {
                applyDeviceSettings(handlers.get(0));
            } else {
                if (isDeviceAoapPossible(device)) {
                    // Device supports AOAP mode, if we have just single AOAP handler then use it
                    // instead of showing disambiguation dialog to the user.
                    UsbDeviceSettings aoapHandler = getSingleAoapDeviceHandlerOrNull(handlers);
                    if (aoapHandler != null) {
                        applyDeviceSettings(aoapHandler);
                        return;
                    }
                }
                mCallback.optionsUpdated(handlers);
            }
        } else {
            Log.w(TAG, "Handlers ignored as they came for inactive device");
        }
    }

    private UsbDeviceSettings getSingleAoapDeviceHandlerOrNull(List<UsbDeviceSettings> handlers) {
        UsbDeviceSettings aoapHandler = null;
        for (UsbDeviceSettings handler : handlers) {
            if (handler.getAoap()) {
                if (aoapHandler != null) { // Found multiple AOAP handlers.
                    return null;
                }
                aoapHandler = handler;
            }
        }
        return aoapHandler;
    }

    @Override
    public void onDeviceDispatched() {
        stopDeviceProcessing();
        mCallback.shutdown();
    }

    void doHandleDeviceRemoved() {
        if (getActiveDevice() == null) {
            if (LOCAL_LOGD) {
                Log.d(TAG, "USB device detached");
            }
            stopDeviceProcessing();
            mCallback.shutdown();
        }
    }

    private class UsbHostControllerHandlerDispatchData {
        private final UsbDevice mUsbDevice;
        private final UsbDeviceSettings mUsbDeviceSettings;

        public int mRetries = 0;
        public boolean mCanResolve = true;

        public UsbHostControllerHandlerDispatchData(
                UsbDevice usbDevice, UsbDeviceSettings usbDeviceSettings,
                int retries, boolean canResolve) {
            mUsbDevice = usbDevice;
            mUsbDeviceSettings = usbDeviceSettings;
            mRetries = retries;
            mCanResolve = canResolve;
        }

        public UsbDevice getUsbDevice() {
            return mUsbDevice;
        }

        public UsbDeviceSettings getUsbDeviceSettings() {
            return mUsbDeviceSettings;
        }
    }

    private class UsbHostControllerHandler extends Handler {
        private static final int MSG_DEVICE_REMOVED = 1;
        private static final int MSG_DEVICE_DISPATCH = 2;

        private static final int DEVICE_REMOVE_TIMEOUT_MS = 500;

        private UsbHostControllerHandler(Looper looper) {
            super(looper);
        }

        private void requestDeviceRemoved() {
            sendEmptyMessageDelayed(MSG_DEVICE_REMOVED, DEVICE_REMOVE_TIMEOUT_MS);
        }

        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_DEVICE_REMOVED:
                    doHandleDeviceRemoved();
                    break;
                case MSG_DEVICE_DISPATCH:
                    UsbHostControllerHandlerDispatchData data =
                            (UsbHostControllerHandlerDispatchData) msg.obj;
                    UsbDevice device = data.getUsbDevice();
                    UsbDeviceSettings settings = data.getUsbDeviceSettings();
                    if (!mUsbResolver.dispatch(device, settings.getHandler(), settings.getAoap())) {
                        if (data.mRetries > 0) {
                            --data.mRetries;
                            Message nextMessage = Message.obtain(msg);
                            mHandler.sendMessageDelayed(nextMessage, DISPATCH_RETRY_DELAY_MS);
                        } else if (data.mCanResolve) {
                            resolveDevice(device);
                        }
                    } else if (LOCAL_LOGV) {
                        Log.v(TAG, "Usb Device: " + data.getUsbDevice() + " was sent to component: "
                                + settings.getHandler());
                    }
                    break;
                default:
                    Log.w(TAG, "Unhandled message: " + msg);
                    super.handleMessage(msg);
            }
        }
    }

}
