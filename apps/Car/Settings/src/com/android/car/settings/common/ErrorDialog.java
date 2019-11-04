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

package com.android.car.settings.common;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Bundle;

import androidx.annotation.StringRes;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;

/**
 * Dialog to inform that an action failed.
 */
public class ErrorDialog extends DialogFragment {
    private static final String ERROR_DIALOG_TITLE_KEY = "error_dialog_title";
    private static final String DIALOG_TAG = "ErrorDialogTag";

    /**
     * Shows the error dialog.
     *
     * @param parent Fragment associated with the dialog.
     * @param title  Title for the error dialog.
     */
    public static ErrorDialog show(Fragment parent, @StringRes int title) {
        ErrorDialog dialog = newInstance(title);
        dialog.setTargetFragment(parent, 0);
        dialog.show(parent.getFragmentManager(), DIALOG_TAG);
        return dialog;
    }

    /**
     * Creates an instance of error dialog with the appropriate title. This constructor should be
     * used when starting an error dialog when we don't have a reference to the parent fragment.
     */
    public static ErrorDialog newInstance(@StringRes int title) {
        ErrorDialog dialog = new ErrorDialog();
        Bundle bundle = new Bundle();
        bundle.putInt(ERROR_DIALOG_TITLE_KEY, title);
        dialog.setArguments(bundle);
        return dialog;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        return new AlertDialog.Builder(getContext())
                .setTitle(getArguments().getInt(ERROR_DIALOG_TITLE_KEY))
                .setPositiveButton(android.R.string.ok, /* listener =*/ null)
                .create();
    }
}
