/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.Switch;

import androidx.annotation.LayoutRes;
import androidx.annotation.XmlRes;

import com.android.car.settings.R;
import com.android.car.settings.common.SettingsFragment;

/**
 * Main page to host Wifi related preferences.
 */
public class WifiSettingsFragment extends SettingsFragment
        implements CarWifiManager.Listener {

    private static final int SEARCHING_DELAY_MILLIS = 1700;

    private CarWifiManager mCarWifiManager;
    private ProgressBar mProgressBar;
    private Switch mWifiSwitch;

    @Override
    @LayoutRes
    protected int getActionBarLayoutId() {
        return R.layout.action_bar_with_toggle;
    }

    @Override
    @XmlRes
    protected int getPreferenceScreenResId() {
        return R.xml.wifi_list_fragment;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mCarWifiManager = new CarWifiManager(getContext());

        mProgressBar = requireActivity().findViewById(R.id.progress_bar);
        setupWifiSwitch();
    }

    @Override
    public void onStart() {
        super.onStart();
        mCarWifiManager.addListener(this);
        mCarWifiManager.start();
        onWifiStateChanged(mCarWifiManager.getWifiState());
    }

    @Override
    public void onStop() {
        super.onStop();
        mCarWifiManager.removeListener(this);
        mCarWifiManager.stop();
        mProgressBar.setVisibility(View.GONE);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mCarWifiManager.destroy();
    }

    @Override
    public void onAccessPointsChanged() {
        mProgressBar.setVisibility(View.VISIBLE);
        getView().postDelayed(() -> mProgressBar.setVisibility(View.GONE), SEARCHING_DELAY_MILLIS);
    }

    @Override
    public void onWifiStateChanged(int state) {
        mWifiSwitch.setChecked(mCarWifiManager.isWifiEnabled());
        switch (state) {
            case WifiManager.WIFI_STATE_ENABLING:
                mProgressBar.setVisibility(View.VISIBLE);
                break;
            default:
                mProgressBar.setVisibility(View.GONE);
        }
    }

    private void setupWifiSwitch() {
        mWifiSwitch = getActivity().findViewById(R.id.toggle_switch);
        mWifiSwitch.setChecked(mCarWifiManager.isWifiEnabled());
        mWifiSwitch.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (isChecked != mCarWifiManager.isWifiEnabled()) {
                mCarWifiManager.setWifiEnabled(isChecked);
            }
        });
    }
}
