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

package com.android.car.settings.security;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;

import androidx.fragment.app.DialogFragment;

import com.android.car.settings.R;

/**
 * Dialog to confirm screen lock removal.
 */
public class ConfirmRemoveScreenLockDialog extends DialogFragment {

    /** Identifier for the dialog which confirms the removal of a screen lock. */
    public static final String TAG = "confirm_remove_lock_dialog";
    private ConfirmRemoveScreenLockListener mConfirmRemoveScreenLockListener;

    /** Sets a listener to act when a user confirms delete. */
    public void setConfirmRemoveScreenLockListener(ConfirmRemoveScreenLockListener listener) {
        mConfirmRemoveScreenLockListener = listener;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new AlertDialog.Builder(getContext())
                .setTitle(R.string.remove_screen_lock_title)
                .setMessage(R.string.remove_screen_lock_message)
                .setPositiveButton(R.string.remove_button, (dialog, which) -> {
                    if (mConfirmRemoveScreenLockListener != null) {
                        mConfirmRemoveScreenLockListener.onConfirmRemoveScreenLock();
                    }
                    dialog.dismiss();
                })
                .setNegativeButton(android.R.string.cancel, null)
                .create();
    }

    /** A listener for when user confirms lock removal for the current user. */
    public interface ConfirmRemoveScreenLockListener {
        /** Defines the actions to take when a user confirms the removal of a lock. */
        void onConfirmRemoveScreenLock();
    }
}
