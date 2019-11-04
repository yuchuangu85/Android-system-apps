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

import static android.content.Intent.ACTION_USER_UNLOCKED;

import android.annotation.Nullable;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;

import java.util.List;

/**
 * Activity to handle USB device attached.
 * <p>
 * When user plugs in USB device: a) Device was used before and user selected handler for it. In
 * this case handler will be launched. b) Device has not handler assigned. In this case supported
 * handlers will be captured, and user will be presented with choice to assign default handler.
 * After that handler will be launched.
 */
public class UsbHostManagementActivity extends Activity {
    private static final String TAG = UsbHostManagementActivity.class.getSimpleName();

    private HandlersAdapter mListAdapter;
    private ListView mHandlersList;
    private TextView mHandlerTitle;
    private LinearLayout mUsbHandlersDialog;
    private UsbHostController mController;
    private PackageManager mPackageManager;

    private final ResolveBroadcastReceiver mResolveBroadcastReceiver
            = new ResolveBroadcastReceiver();
    private boolean mReceiverRegistered = false;

    private void unregisterResolveBroadcastReceiver() {
        if (mReceiverRegistered) {
            unregisterReceiver(mResolveBroadcastReceiver);
            mReceiverRegistered = false;
        }
    }

    private void processDevice() {
        UsbDevice connectedDevice = getDevice();

        if (connectedDevice != null) {
            mController.processDevice(connectedDevice);
        } else {
            unregisterResolveBroadcastReceiver();
            finish();
        }
    }

    private class ResolveBroadcastReceiver extends BroadcastReceiver {
        public void onReceive(Context context, Intent intent) {
            // We could have been unregistered after receiving the intent but before processing it,
            // so make sure we are still registered.
            if (mReceiverRegistered) {
                processDevice();
                unregisterResolveBroadcastReceiver();
            }
        }
    }

    private final AdapterView.OnItemClickListener mHandlerClickListener =
            new AdapterView.OnItemClickListener() {
        @Override
        public void onItemClick(AdapterView<?> parent, final View view, int position, long id) {
            UsbDeviceSettings settings = (UsbDeviceSettings) parent.getItemAtPosition(position);
            settings.setDefaultHandler(true);
            mController.applyDeviceSettings(settings);
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.usb_host);
        mUsbHandlersDialog = findViewById(R.id.usb_handlers_dialog);
        mHandlersList = findViewById(R.id.usb_handlers_list);
        mHandlerTitle = findViewById(R.id.usb_handler_heading);
        mListAdapter = new HandlersAdapter(this);
        mHandlersList.setAdapter(mListAdapter);
        mHandlersList.setOnItemClickListener(mHandlerClickListener);
        mController = new UsbHostController(this, new UsbCallbacks());
        mPackageManager = getPackageManager();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mController.release();
    }

    @Override
    public void onPause() {
        super.onPause();
        unregisterResolveBroadcastReceiver();
    }

    @Override
    public void onResume() {
        super.onResume();

        UserManager userManager = getSystemService(UserManager.class);
        if (userManager.isUserUnlocked() || getUserId() == UserHandle.USER_SYSTEM) {
            processDevice();
        } else {
            mReceiverRegistered = true;
            registerReceiver(mResolveBroadcastReceiver, new IntentFilter(ACTION_USER_UNLOCKED));
            // in case the car was unlocked while the receiver was being registered
            if (userManager.isUserUnlocked()) {
                mResolveBroadcastReceiver.onReceive(this, new Intent(ACTION_USER_UNLOCKED));
            }
        }
    }

    class UsbCallbacks implements UsbHostController.UsbHostControllerCallbacks {
        private boolean mProcessing = false;

        @Override
        public void shutdown() {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    finish();
                }
            });
        }

        @Override
        public void processingStarted() {
            mProcessing = true;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    if (mProcessing && !mListAdapter.isEmpty()) {
                        mUsbHandlersDialog.setVisibility(View.VISIBLE);
                    }
                }
            });
        }

        @Override
        public void titleChanged(final String title) {
            runOnUiThread(() -> mHandlerTitle.setText(title));
        }

        @Override
        public void optionsUpdated(final List<UsbDeviceSettings> options) {
            if (options != null && !options.isEmpty()) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        if (mProcessing) {
                            mUsbHandlersDialog.setVisibility(View.VISIBLE);
                        }
                        mListAdapter.clear();
                        mListAdapter.addAll(options);
                    }
                });
            }
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }

    @Nullable
    private UsbDevice getDevice() {
        if (!UsbManager.ACTION_USB_DEVICE_ATTACHED.equals(getIntent().getAction())) {
            return null;
        }
        return (UsbDevice) getIntent().getParcelableExtra(UsbManager.EXTRA_DEVICE);
    }

    private class HandlersAdapter extends ArrayAdapter<UsbDeviceSettings> {
        class HandlerHolder {
            public TextView mAppName;
            public ImageView mAppIcon;
        }

        HandlersAdapter(Context context) {
            super(context, R.layout.usb_handler_row);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View rowView = convertView;
            if (rowView == null) {
                rowView = getLayoutInflater().inflate(R.layout.usb_handler_row, null);
                HandlerHolder holder = new HandlerHolder();
                holder.mAppName = (TextView) rowView.findViewById(R.id.usb_handler_title);
                holder.mAppIcon = (ImageView) rowView.findViewById(R.id.usb_handler_icon);
                rowView.setTag(holder);
            }

            HandlerHolder holder = (HandlerHolder) rowView.getTag();
            ComponentName handler = getItem(position).getHandler();

            try {
                ApplicationInfo appInfo =
                        mPackageManager.getApplicationInfo(handler.getPackageName(), 0);
                holder.mAppName.setText(appInfo.loadLabel(mPackageManager));
                holder.mAppIcon.setImageDrawable(appInfo.loadIcon(mPackageManager));
            } catch (NameNotFoundException e) {
                Log.e(TAG, "Handling package not found: " + handler.getPackageName());
                holder.mAppName.setText(handler.flattenToShortString());
                holder.mAppIcon.setImageResource(android.R.color.transparent);
            }
            return rowView;
        }
    }
}
