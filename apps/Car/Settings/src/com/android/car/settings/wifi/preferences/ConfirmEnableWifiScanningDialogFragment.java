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

package com.android.car.settings.wifi.preferences;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;

import androidx.fragment.app.DialogFragment;

import com.android.car.settings.R;
import com.android.car.settings.common.Logger;
import com.android.settingslib.HelpUtils;

/** Dialog to request enabling of wifi scanning when user tries to enable auto wifi wakeup. */
public class ConfirmEnableWifiScanningDialogFragment extends DialogFragment implements
        DialogInterface.OnClickListener {

    public static final String TAG = "ConfirmEnableWifiScanningDialogFragment";
    private static final Logger LOG = new Logger(ConfirmEnableWifiScanningDialogFragment.class);

    private WifiScanningEnabledListener mListener;

    /** Sets the wifi scanning enabled listener. */
    public void setWifiScanningEnabledListener(WifiScanningEnabledListener listener) {
        mListener = listener;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext())
                .setTitle(R.string.wifi_settings_scanning_required_title)
                .setPositiveButton(R.string.wifi_settings_scanning_required_turn_on, this)
                .setNegativeButton(R.string.cancel, null);

        // Only show "learn more" if there is a help page to show
        if (!TextUtils.isEmpty(getContext().getString(R.string.help_uri_wifi_scanning_required))) {
            builder.setNeutralButton(R.string.learn_more, this);
        }

        return builder.create();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case DialogInterface.BUTTON_POSITIVE:
                if (mListener != null) {
                    mListener.onWifiScanningEnabled();
                }
                break;
            case DialogInterface.BUTTON_NEUTRAL:
                openHelpPage();
                break;
            case DialogInterface.BUTTON_NEGATIVE:
            default:
                // do nothing
        }
    }

    private void openHelpPage() {
        Intent intent = HelpUtils.getHelpIntent(getContext(),
                getContext().getString(R.string.help_uri_wifi_scanning_required),
                getContext().getClass().getName());
        if (intent != null) {
            try {
                startActivity(intent);
            } catch (ActivityNotFoundException e) {
                LOG.e("Activity was not found for intent, " + intent.toString());
            }
        }
    }

    /** Listener for when the dialog is confirmed and the wifi scanning is enabled. */
    public interface WifiScanningEnabledListener {
        /** Actions to take when wifi scanning is enabled. */
        void onWifiScanningEnabled();
    }
}
