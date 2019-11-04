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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.ProgressBar;
import android.widget.Switch;

import androidx.annotation.LayoutRes;
import androidx.annotation.XmlRes;

import com.android.car.settings.R;
import com.android.car.settings.common.SettingsFragment;

/**
 * Fragment to host tethering-related preferences.
 */
public class WifiTetherFragment extends SettingsFragment implements Switch.OnCheckedChangeListener {

    private CarWifiManager mCarWifiManager;
    private ConnectivityManager mConnectivityManager;
    private ProgressBar mProgressBar;
    private Switch mTetherSwitch;

    private final ConnectivityManager.OnStartTetheringCallback mOnStartTetheringCallback =
            new ConnectivityManager.OnStartTetheringCallback() {
                @Override
                public void onTetheringFailed() {
                    super.onTetheringFailed();
                    mTetherSwitch.setChecked(false);
                    mTetherSwitch.setEnabled(true);
                }
            };

    @Override
    @LayoutRes
    protected int getActionBarLayoutId() {
        return R.layout.action_bar_with_toggle;
    }

    @Override
    @XmlRes
    protected int getPreferenceScreenResId() {
        return R.xml.wifi_tether_fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        mCarWifiManager = new CarWifiManager(context);
        mConnectivityManager = (ConnectivityManager) getContext().getSystemService(
                Context.CONNECTIVITY_SERVICE);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mProgressBar = getActivity().findViewById(R.id.progress_bar);
        mTetherSwitch = getActivity().findViewById(R.id.toggle_switch);
        setupTetherSwitch();
    }

    @Override
    public void onStart() {
        super.onStart();

        mCarWifiManager.start();
        getContext().registerReceiver(mReceiver,
                new IntentFilter(WifiManager.WIFI_AP_STATE_CHANGED_ACTION));
    }

    @Override
    public void onStop() {
        super.onStop();
        mCarWifiManager.stop();
        getContext().unregisterReceiver(mReceiver);
        mProgressBar.setVisibility(View.GONE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mCarWifiManager.destroy();
    }

    @Override
    public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
        if (!isChecked) {
            mConnectivityManager.stopTethering(ConnectivityManager.TETHERING_WIFI);
        } else {
            mConnectivityManager.startTethering(ConnectivityManager.TETHERING_WIFI,
                    /* showProvisioningUi= */ true,
                    mOnStartTetheringCallback, new Handler(Looper.getMainLooper()));
        }
    }

    protected void setupTetherSwitch() {
        mTetherSwitch.setChecked(mCarWifiManager.isWifiApEnabled());
        mTetherSwitch.setOnCheckedChangeListener(this);
    }

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final int state = intent.getIntExtra(
                    WifiManager.EXTRA_WIFI_AP_STATE, WifiManager.WIFI_AP_STATE_FAILED);
            handleWifiApStateChanged(state);
        }
    };

    private void handleWifiApStateChanged(int state) {
        switch (state) {
            case WifiManager.WIFI_AP_STATE_ENABLING:
                mTetherSwitch.setEnabled(false);
                break;
            case WifiManager.WIFI_AP_STATE_ENABLED:
                mTetherSwitch.setEnabled(true);
                if (!mTetherSwitch.isChecked()) {
                    mTetherSwitch.setChecked(true);
                }
                break;
            case WifiManager.WIFI_AP_STATE_DISABLING:
                mTetherSwitch.setEnabled(false);
                if (mTetherSwitch.isChecked()) {
                    mTetherSwitch.setChecked(false);
                }
                break;
            case WifiManager.WIFI_AP_STATE_DISABLED:
                mTetherSwitch.setChecked(false);
                mTetherSwitch.setEnabled(true);
                break;
            default:
                mTetherSwitch.setChecked(false);
                mTetherSwitch.setEnabled(true);
                break;
        }
    }
}
