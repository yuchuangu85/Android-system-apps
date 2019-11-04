/*
 * Copyright (C) 2018 The Android Open Source Project
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
import android.os.Bundle;
import android.text.TextUtils;
import android.widget.Button;

import androidx.annotation.LayoutRes;
import androidx.annotation.XmlRes;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.android.car.settings.R;
import com.android.car.settings.common.Logger;
import com.android.car.settings.common.SettingsFragment;
import com.android.settingslib.wifi.AccessPoint;

/**
 * Adds a hidden wifi network. The connect button on the fragment is only used for unsecure hidden
 * networks. The remaining security types can be connected via pressing connect on the password
 * dialog.
 */
public class AddWifiFragment extends SettingsFragment {
    private static final Logger LOG = new Logger(AddWifiFragment.class);
    private static final String KEY_NETWORK_NAME = "network_name";
    private static final String KEY_SECURITY_TYPE = "security_type";

    private final BroadcastReceiver mNameChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mNetworkName = intent.getStringExtra(
                    NetworkNamePreferenceController.KEY_NETWORK_NAME);
            setButtonEnabledState();
        }
    };

    private final BroadcastReceiver mSecurityChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mSecurityType = intent.getIntExtra(
                    NetworkSecurityPreferenceController.KEY_SECURITY_TYPE,
                    AccessPoint.SECURITY_NONE);
            setButtonEnabledState();
        }
    };

    private Button mAddWifiButton;
    private String mNetworkName;
    private int mSecurityType = AccessPoint.SECURITY_NONE;

    @Override
    @XmlRes
    protected int getPreferenceScreenResId() {
        return R.xml.add_wifi_fragment;
    }

    @Override
    @LayoutRes
    protected int getActionBarLayoutId() {
        return R.layout.action_bar_with_button;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mNetworkName = savedInstanceState.getString(KEY_NETWORK_NAME);
            mSecurityType = savedInstanceState.getInt(KEY_SECURITY_TYPE, AccessPoint.SECURITY_NONE);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putString(KEY_NETWORK_NAME, mNetworkName);
        outState.putInt(KEY_SECURITY_TYPE, mSecurityType);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mAddWifiButton = getActivity().findViewById(R.id.action_button1);
        mAddWifiButton.setText(R.string.wifi_setup_connect);
        setButtonEnabledState();

        // This only needs to handle hidden/unsecure networks.
        mAddWifiButton.setOnClickListener(v -> {
            int netId = WifiUtil.connectToAccessPoint(getContext(), mNetworkName,
                    AccessPoint.SECURITY_NONE, /* password= */ null, /* hidden= */ true);

            LOG.d("connected to netId: " + netId);
            if (netId != WifiUtil.INVALID_NET_ID) {
                goBack();
            }
        });
    }

    @Override
    public void onStart() {
        super.onStart();
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(mNameChangeReceiver,
                new IntentFilter(NetworkNamePreferenceController.ACTION_NAME_CHANGE));
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(mSecurityChangeReceiver,
                new IntentFilter(NetworkSecurityPreferenceController.ACTION_SECURITY_CHANGE));
    }

    @Override
    public void onStop() {
        super.onStop();
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(mNameChangeReceiver);
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(mSecurityChangeReceiver);
    }

    private void setButtonEnabledState() {
        if (mAddWifiButton != null) {
            mAddWifiButton.setEnabled(
                    !TextUtils.isEmpty(mNetworkName) && mSecurityType == AccessPoint.SECURITY_NONE);
        }
    }
}
