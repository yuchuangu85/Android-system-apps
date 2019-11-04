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

package com.android.car.settings.users;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

import com.android.car.settings.R;

/**
 * Dialog to inform that user deletion failed and offers to retry.
 */
public class MaxUsersLimitReachedDialog extends DialogFragment {
    @VisibleForTesting
    static final String DIALOG_TAG = "MaxUsersLimitReachedDialog";
    private final int mMaxUserLimit;

    public MaxUsersLimitReachedDialog(int maxUserLimit) {
        super();
        mMaxUserLimit = maxUserLimit;
    }

    /**
     * Shows the dialog.
     *
     * @param parent Fragment associated with the dialog.
     */
    public void show(Fragment parent) {
        setTargetFragment(parent, 0);
        show(parent.getFragmentManager(), DIALOG_TAG);
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new AlertDialog.Builder(getContext())
                .setTitle(R.string.user_limit_reached_title)
                .setMessage(getResources().getQuantityString(
                        R.plurals.user_limit_reached_message, mMaxUserLimit, mMaxUserLimit))
                .setPositiveButton(android.R.string.ok, null)
                .create();
    }
}
