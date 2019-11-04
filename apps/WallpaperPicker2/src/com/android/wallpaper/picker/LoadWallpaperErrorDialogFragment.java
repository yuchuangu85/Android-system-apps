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
package com.android.wallpaper.picker;

import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;

import com.android.wallpaper.R;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

/**
 * Dialog fragment which communicates a message that loading the wallpaper failed with an OK button,
 * when clicked will navigate the user back to the previous activity.
 */
public class LoadWallpaperErrorDialogFragment extends DialogFragment {

    public static LoadWallpaperErrorDialogFragment newInstance() {
        return new LoadWallpaperErrorDialogFragment();
    }

    @Override
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);

        return new AlertDialog.Builder(getActivity(), R.style.LightDialogTheme)
                .setMessage(R.string.load_wallpaper_error_message)
                .setPositiveButton(android.R.string.ok,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                Listener callback = (Listener) getTargetFragment();
                                callback.onClickOk();
                            }
                        }
                )
                .create();
    }

    @Override
    public void onDismiss(DialogInterface dialogInterface) {
        super.onDismiss(dialogInterface);

        // Treat a dismissal by user click outside the dialog foreground the same as the user clicking
        // "OK" to dismiss the dialog.
        Listener callback = (Listener) getTargetFragment();
        callback.onClickOk();
    }

    /**
     * Interface which clients of this DialogFragment should implement in order to handle user actions
     * on the dialog's buttons.
     */
    public interface Listener {
        void onClickOk();
    }
}
