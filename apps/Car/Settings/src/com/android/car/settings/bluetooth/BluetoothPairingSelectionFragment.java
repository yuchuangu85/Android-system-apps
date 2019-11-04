/*
 * Copyright 2018 The Android Open Source Project
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

package com.android.car.settings.bluetooth;

import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;

import com.android.car.settings.R;
import com.android.car.settings.common.SettingsFragment;
import com.android.settingslib.bluetooth.BluetoothCallback;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.bluetooth.LocalBluetoothManager;

/**
 * Page which scans for Bluetooth devices so that a user can select a new device to pair. When a
 * device pairs, this page will finish.
 */
public class BluetoothPairingSelectionFragment extends SettingsFragment {

    private final BluetoothCallback mCallback = new BluetoothCallback() {
        @Override
        public void onScanningStateChanged(boolean started) {
        }

        @Override
        public void onDeviceBondStateChanged(CachedBluetoothDevice cachedDevice, int bondState) {
            if (bondState == BluetoothDevice.BOND_BONDED) {
                // We are in a dispatch loop from event manager to all listeners. goBack will pop
                // immediately, stopping this fragment causing an unregister from the event manager
                // and a ConcurrentModificationException. Wait until the dispatch is done to go
                // back.
                requireActivity().getMainThreadHandler().post(
                        BluetoothPairingSelectionFragment.this::goBack);
            }
        }
    };

    private LocalBluetoothManager mManager;
    private ProgressBar mProgressBar;

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.bluetooth_pairing_selection_fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mManager = BluetoothUtils.getLocalBtManager(context);
        if (mManager == null) {
            goBack();
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        mProgressBar = requireActivity().findViewById(R.id.progress_bar);
    }

    @Override
    public void onStart() {
        super.onStart();
        mManager.setForegroundActivity(requireActivity());
        mManager.getEventManager().registerCallback(mCallback);
        mProgressBar.setVisibility(View.VISIBLE);
    }

    @Override
    public void onStop() {
        super.onStop();
        mManager.setForegroundActivity(null);
        mManager.getEventManager().unregisterCallback(mCallback);
        mProgressBar.setVisibility(View.GONE);
    }
}
