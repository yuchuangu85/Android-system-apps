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

package com.android.car.settings.security;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;

import androidx.fragment.app.DialogFragment;

import com.android.car.settings.R;

/**
 * Dialog to confirm removing a trusted device.
 */
public class ConfirmRemoveDeviceDialog extends DialogFragment {

    /** Identifier for the dialog which confirms removing a trusted device. */
    public static final String TAG = "confirm_remove_device_dialog";
    private static final String DEVICE_NAME_KEY = "deviceName";
    private static final String HANDLE_KEY = "handle";
    private ConfirmRemoveDeviceListener mConfirmRemoveDeviceListener;

    /**
     * Factory method for creating a {@link ConfirmRemoveDeviceDialog}
     *
     * @param deviceName the name of current clicked device
     * @param handle the handle of current clicked device and is used to identify the device
     */
    public static ConfirmRemoveDeviceDialog newInstance(String deviceName, long handle) {
        Bundle args = new Bundle();
        args.putString(DEVICE_NAME_KEY, deviceName);
        args.putLong(HANDLE_KEY, handle);

        ConfirmRemoveDeviceDialog dialog = new ConfirmRemoveDeviceDialog();
        dialog.setArguments(args);
        return dialog;
    }

    /** Sets a listener to act when a user confirms removing the trusted device. */
    public void setConfirmRemoveDeviceListener(ConfirmRemoveDeviceListener listener) {
        mConfirmRemoveDeviceListener = listener;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle args = getArguments();
        String name = args.getString(DEVICE_NAME_KEY);
        long handle = args.getLong(HANDLE_KEY);
        return new AlertDialog.Builder(getContext())
                .setTitle(name)
                .setMessage(getContext().getString(R.string.remove_device_message, name, name))
                .setPositiveButton(R.string.trusted_device_remove_button, (dialog, which) -> {
                    if (mConfirmRemoveDeviceListener != null) {
                        mConfirmRemoveDeviceListener.onConfirmRemoveDevice(handle);
                    }
                })
                .setNegativeButton(R.string.trusted_device_done_button, /* listener= */ null)
                .create();
    }

    /** A listener for when user confirms removing a trusted device. */
    public interface ConfirmRemoveDeviceListener {
        /** Defines the actions to take when a user confirms removing the trusted device. */
        void onConfirmRemoveDevice(long handle);
    }

}
