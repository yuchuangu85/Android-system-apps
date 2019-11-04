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

package com.android.car.settings.wifi;

import android.annotation.NonNull;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Toast;

import com.android.car.settings.R;
import com.android.car.settings.common.Logger;

/**
 * Code drop from {@link com.android.settings.wifi.RequestToggleWiFiActivity}.
 *
 * This {@link Activity} handles requests to toggle WiFi by collecting user
 * consent and waiting until the state change is completed.
 */
public class WifiRequestToggleActivity extends Activity implements DialogInterface.OnClickListener,
        DialogInterface.OnCancelListener {
    private static final Logger LOG = new Logger(WifiRequestToggleActivity.class);
    private static final long TOGGLE_TIMEOUT_MILLIS = 10000; // 10 sec

    private static final int STATE_UNKNOWN = -1;
    private static final int STATE_ENABLE = 1;
    private static final int STATE_ENABLING = 2;
    private static final int STATE_DISABLE = 3;
    private static final int STATE_DISABLING = 4;

    private final StateChangeReceiver mReceiver = new StateChangeReceiver();

    private final Runnable mTimeoutCommand = () -> {
        if (!isFinishing() && !isDestroyed()) {
            finish();
        }
    };

    @NonNull
    private CarWifiManager mCarWifiManager;
    @NonNull
    private CharSequence mAppLabel;

    private AlertDialog mDialog;
    private int mAction = STATE_UNKNOWN;
    private int mState = STATE_UNKNOWN;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mCarWifiManager = new CarWifiManager(getApplicationContext());

        setResult(Activity.RESULT_CANCELED);

        String packageName = getIntent().getStringExtra(Intent.EXTRA_PACKAGE_NAME);
        if (TextUtils.isEmpty(packageName)) {
            finish();
            return;
        }

        try {
            ApplicationInfo applicationInfo = getPackageManager().getApplicationInfo(
                    packageName,  /* flags= */ 0);
            mAppLabel = applicationInfo.loadSafeLabel(getPackageManager());
        } catch (PackageManager.NameNotFoundException e) {
            LOG.e("Couldn't find app with package name " + packageName);
            finish();
            return;
        }

        mAction = extractActionState();
        if (mAction == STATE_UNKNOWN) {
            finish();
        }
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case DialogInterface.BUTTON_POSITIVE:
                switch (mState) {
                    case STATE_ENABLE:
                        mCarWifiManager.setWifiEnabled(true);
                        mState = STATE_ENABLING;
                        updateUi();
                        scheduleToggleTimeout();
                        break;

                    case STATE_DISABLE:
                        mCarWifiManager.setWifiEnabled(false);
                        mState = STATE_DISABLING;
                        updateUi();
                        scheduleToggleTimeout();
                        break;
                }
                setResult(RESULT_OK);
                break;
            case DialogInterface.BUTTON_NEGATIVE:
                finish();
                break;
        }
    }

    @Override
    public void onCancel(DialogInterface dialog) {
        finish();
    }

    @Override
    protected void onStart() {
        super.onStart();

        mReceiver.register();

        final int wifiState = mCarWifiManager.getWifiState();

        switch (mAction) {
            case STATE_ENABLE:
                switch (wifiState) {
                    case WifiManager.WIFI_STATE_ENABLED:
                        setResult(RESULT_OK);
                        finish();
                        return;

                    case WifiManager.WIFI_STATE_ENABLING:
                        mState = STATE_ENABLING;
                        scheduleToggleTimeout();
                        break;
                    default:
                        mState = mAction;
                }
                break;

            case STATE_DISABLE:
                switch (wifiState) {
                    case WifiManager.WIFI_STATE_DISABLED:
                        setResult(RESULT_OK);
                        finish();
                        return;

                    case WifiManager.WIFI_STATE_DISABLING:
                        mState = STATE_DISABLING;
                        scheduleToggleTimeout();
                        break;
                    default:
                        mState = mAction;
                }
                break;
        }

        updateUi();
    }

    @Override
    protected void onStop() {
        super.onStop();
        mReceiver.unregister();
        unscheduleToggleTimeout();
        if (mDialog != null) {
            mDialog.dismiss();
        }
    }

    private void updateUi() {
        if (mDialog != null) {
            mDialog.dismiss();
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(/* context= */
                this).setOnCancelListener(/* listener= */ this);
        switch (mState) {
            case STATE_ENABLE:
                builder.setPositiveButton(R.string.allow, /* listener= */ this);
                builder.setNegativeButton(R.string.deny, /* listener= */ this);
                builder.setMessage(getString(R.string.wifi_ask_enable, mAppLabel));
                break;

            case STATE_ENABLING:
                builder.setMessage(getString(R.string.wifi_starting, mAppLabel));
                break;

            case STATE_DISABLE:
                builder.setPositiveButton(R.string.allow, /* listener= */ this);
                builder.setNegativeButton(R.string.deny, /* listener= */ this);
                builder.setMessage(getString(R.string.wifi_ask_disable, mAppLabel));
                break;

            case STATE_DISABLING:
                builder.setMessage(getString(R.string.wifi_stopping, mAppLabel));
                break;
        }
        mDialog = builder.create();
        mDialog.show();
    }

    private void scheduleToggleTimeout() {
        getWindow().getDecorView().postDelayed(mTimeoutCommand, TOGGLE_TIMEOUT_MILLIS);
    }

    private void unscheduleToggleTimeout() {
        getWindow().getDecorView().removeCallbacks(mTimeoutCommand);
    }

    private int extractActionState() {
        String action = getIntent().getAction();
        switch (action) {
            case WifiManager.ACTION_REQUEST_ENABLE:
                return STATE_ENABLE;

            case WifiManager.ACTION_REQUEST_DISABLE:
                return STATE_DISABLE;

            default:
                return STATE_UNKNOWN;
        }
    }

    private final class StateChangeReceiver extends BroadcastReceiver {
        private final IntentFilter mFilter = new IntentFilter(
                WifiManager.WIFI_STATE_CHANGED_ACTION);

        public void register() {
            registerReceiver(/* receiver= */ this, mFilter);
        }

        public void unregister() {
            unregisterReceiver(/* receiver= */ this);
        }

        public void onReceive(Context context, Intent intent) {
            Activity activity = WifiRequestToggleActivity.this;
            if (activity.isFinishing() || activity.isDestroyed()) {
                return;
            }
            int currentState = mCarWifiManager.getWifiState();
            switch (currentState) {
                case WifiManager.WIFI_STATE_ENABLED:
                case WifiManager.WIFI_STATE_DISABLED:
                    if (mState == STATE_ENABLING || mState == STATE_DISABLING) {
                        activity.setResult(Activity.RESULT_OK);
                        finish();
                    }
                    break;

                case WifiManager.ERROR:
                    Toast.makeText(activity, R.string.wifi_error, Toast.LENGTH_SHORT).show();
                    finish();
                    break;
            }
        }
    }
}
