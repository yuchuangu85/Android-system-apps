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

import android.app.AlertDialog;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.DialogFragment;

import com.android.car.settings.R;
import com.android.settingslib.bluetooth.CachedBluetoothDevice;
import com.android.settingslib.bluetooth.LocalBluetoothManager;

/**
 * Displays a dialog which prompts the user to confirm disconnecting from a remote Bluetooth device.
 */
public class BluetoothDisconnectConfirmDialogFragment extends DialogFragment {

    private static final String KEY_DEVICE_ADDRESS = "device_address";

    private final CachedBluetoothDevice.Callback mDeviceCallback = this::dismissIfNotConnected;
    private CachedBluetoothDevice mCachedDevice;

    /**
     * Returns a new {@link BluetoothDisconnectConfirmDialogFragment} for the given {@code device}.
     */
    public static BluetoothDisconnectConfirmDialogFragment newInstance(
            CachedBluetoothDevice device) {
        Bundle args = new Bundle();
        args.putString(KEY_DEVICE_ADDRESS, device.getAddress());
        BluetoothDisconnectConfirmDialogFragment fragment =
                new BluetoothDisconnectConfirmDialogFragment();
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        String deviceAddress = getArguments().getString(KEY_DEVICE_ADDRESS);
        LocalBluetoothManager manager = BluetoothUtils.getLocalBtManager(context);
        BluetoothDevice device = BluetoothAdapter.getDefaultAdapter().getRemoteDevice(
                deviceAddress);
        mCachedDevice = manager.getCachedDeviceManager().findDevice(device);
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        Context context = requireContext();
        String name = mCachedDevice.getName();
        if (TextUtils.isEmpty(name)) {
            name = context.getString(R.string.bluetooth_device);
        }
        String title = context.getString(R.string.bluetooth_disconnect_title);
        String message = context.getString(R.string.bluetooth_disconnect_all_profiles, name);

        return new AlertDialog.Builder(context)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(android.R.string.ok,
                        (dialog, which) -> mCachedDevice.disconnect())
                .setNegativeButton(android.R.string.cancel, /* listener= */ null)
                .create();
    }

    @Override
    public void onStart() {
        super.onStart();
        mCachedDevice.registerCallback(mDeviceCallback);
    }

    @Override
    public void onStop() {
        super.onStop();
        mCachedDevice.unregisterCallback(mDeviceCallback);
    }

    private void dismissIfNotConnected() {
        // This handles the case where the dialog is showing and the connection is broken via UI
        // on the remote device. It does not cover the case of the device disconnecting while the
        // fragment is starting because we cannot begin another transaction for dismiss while in
        // a transaction to show. That case, however, should be extremely rare, and the action
        // taken on the dialog will have no effect.
        if (!mCachedDevice.isConnected() && getDialog().isShowing()) {
            dismiss();
        }
    }
}
