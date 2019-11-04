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

import static android.content.Intent.ACTION_USER_SWITCHED;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Binder;
import android.os.UserHandle;
import android.util.Log;

import java.util.ArrayList;

/**
 * Starts the {@link UsbHostManagementActivity} for each connected usb device when {@link
 * #onStartCommand(Intent, int, int)} is called. This is meant to allow this activity to start for
 * connected devices on start up, without holding up processing of the LOCKED_BOOT_COMPLETED intent.
 */
public class BootUsbService extends Service {
    private static final String TAG = BootUsbService.class.getSimpleName();
    private static final int NOTIFICATION_ID = 1;

    static final String USB_DEVICE_LIST_KEY = "usb_device_list";

    private ArrayList<UsbDevice> mDeviceList;

    private class UserSwitchBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            processDevices();
            unregisterReceiver(this);
        }
    }

    @Override
    public Binder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        String notificationIdString =
                getResources().getString(R.string.usb_boot_service_notification);
        NotificationManager notificationManager = getSystemService(NotificationManager.class);
        NotificationChannel notificationChannel =
                new NotificationChannel(
                        notificationIdString,
                        "Car Usb Handler enumeration",
                        NotificationManager.IMPORTANCE_NONE);
        notificationManager.createNotificationChannel(notificationChannel);
        Notification notification =
                new Notification.Builder(
                        this, notificationIdString).setSmallIcon(R.drawable.ic_launcher).build();
        // CarUsbHandler runs in the background, so startForeground must be explicitly called.
        startForeground(NOTIFICATION_ID, notification);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        mDeviceList = intent.getParcelableArrayListExtra(USB_DEVICE_LIST_KEY);
        // If the current user is still the system user, wait until the non system user becomes the
        // current/foreground user. Otherwise, we can go ahead and start processing the USB devices
        // immediately.
        if (ActivityManager.getCurrentUser() == UserHandle.USER_SYSTEM) {
            Log.d(TAG, "Current user is still the system user, waiting for user switch");
            registerReceiver(
                    new UserSwitchBroadcastReceiver(), new IntentFilter(ACTION_USER_SWITCHED));
        } else {
            processDevices();
        }

        return START_NOT_STICKY;
    }

    private void processDevices() {
        Log.d(TAG, "Processing connected USB devices and starting handlers");
        for (UsbDevice device : mDeviceList) {
            handle(this, device);
        }
        stopSelf();
    }

    private void handle(Context context, UsbDevice device) {
        Intent manageDevice = new Intent(context, UsbHostManagementActivity.class);
        manageDevice.setAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        manageDevice.putExtra(UsbManager.EXTRA_DEVICE, device);
        manageDevice.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivityAsUser(manageDevice, UserHandle.CURRENT);
    }
}
