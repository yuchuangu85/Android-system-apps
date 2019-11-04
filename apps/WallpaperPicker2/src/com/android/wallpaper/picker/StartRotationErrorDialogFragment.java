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
import com.android.wallpaper.model.WallpaperRotationInitializer.NetworkPreference;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;

/**
 * Dialog fragment which communicates that starting a wallpaper rotation failed and gives the user
 * an option to retry starting the rotation.
 */
public class StartRotationErrorDialogFragment extends DialogFragment {

    private static final String ARG_NETWORK_PREFERENCE = "network_preference";

    public static StartRotationErrorDialogFragment newInstance(
            @NetworkPreference int networkPreference) {
        StartRotationErrorDialogFragment dialogFragment = new StartRotationErrorDialogFragment();
        Bundle args = new Bundle();
        args.putInt(ARG_NETWORK_PREFERENCE, networkPreference);
        dialogFragment.setArguments(args);
        return dialogFragment;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        super.onCreateDialog(savedInstanceState);

        @NetworkPreference final int networkPreference = getArguments().getInt(ARG_NETWORK_PREFERENCE);

        return new AlertDialog.Builder(getActivity(), R.style.LightDialogTheme)
                .setMessage(getResources().getString(R.string.start_rotation_error_message))
                .setPositiveButton(com.android.wallpaper.R.string.try_again,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                Listener callback = (Listener) getTargetFragment();
                                callback.retryStartRotation(networkPreference);
                            }
                        }
                )
                .setNegativeButton(android.R.string.cancel, null)
                .create();
    }

    /**
     * Interface which clients of this DialogFragment should implement in order to handle user actions
     * on the dialog's buttons.
     */
    public interface Listener {
        void retryStartRotation(@NetworkPreference int networkPreference);
    }
}
