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
 * Dialog to confirm pairing code.
 */
public class ConfirmPairingCodeDialog extends DialogFragment {
    /** Identifier for the dialog which confirms the pairing code. */
    public static final String TAG = "confirm_pairing_code_dialog";
    private static final String PAIRING_CODE_KEY = "pairingCode";
    private ConfirmPairingCodeListener mConfirmPairingCodeListener;

    /**
     * Factory method for creating a ConfirmPairingCodeFragment
     *
     * @param pairingCode the pairing code sent by the connected device
     */
    public static ConfirmPairingCodeDialog newInstance(String pairingCode) {
        Bundle args = new Bundle();
        args.putString(PAIRING_CODE_KEY, pairingCode);

        ConfirmPairingCodeDialog dialog = new ConfirmPairingCodeDialog();
        dialog.setArguments(args);
        return dialog;
    }

    /** Sets a listener to act when a user confirms pairing code. */
    public void setConfirmPairingCodeListener(ConfirmPairingCodeListener listener) {
        mConfirmPairingCodeListener = listener;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Bundle args = getArguments();
        String pairingCode = args.getString(PAIRING_CODE_KEY);
        return new AlertDialog.Builder(getContext())
                .setTitle(getContext().getString(R.string.trusted_device_pairing_code_dialog_title))
                .setMessage(pairingCode)
                .setPositiveButton(R.string.trusted_device_confirm_button, (dialog, which) -> {
                    if (mConfirmPairingCodeListener != null) {
                        mConfirmPairingCodeListener.onConfirmPairingCode();
                    }
                })
                .setNegativeButton(android.R.string.cancel, (dialog, which) -> {
                    if (mConfirmPairingCodeListener != null) {
                        mConfirmPairingCodeListener.onDialogCancelled();
                    }
                })
                .create();
    }

    /** A listener for when user interacts with this dialog. */
    public interface ConfirmPairingCodeListener {
        /** Defines the actions to take when a user confirms the pairing code. */
        void onConfirmPairingCode();

        /** Defines the actions to take when a user cancel confirming the pairing code. */
        void onDialogCancelled();
    }

}
