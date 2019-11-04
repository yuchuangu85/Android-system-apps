/*
 * Copyright 2019 The Android Open Source Project
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

package com.android.car.settings.system;

import android.app.ActivityManager;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.Settings;
import android.service.oemlock.OemLockManager;
import android.service.persistentdata.PersistentDataBlockManager;
import android.widget.Button;

import androidx.annotation.LayoutRes;
import androidx.preference.PreferenceManager;

import com.android.car.settings.R;
import com.android.car.settings.common.SettingsFragment;

/**
 * Presents the user with a final warning before issuing the request to reset the head unit to its
 * default "factory" state.
 */
public class MasterClearConfirmFragment extends SettingsFragment {

    private Button.OnClickListener mFinalClickListener = v -> {
        if (ActivityManager.isUserAMonkey()) {
            return;
        }

        PersistentDataBlockManager pdbManager =
                (PersistentDataBlockManager) requireContext().getSystemService(
                        Context.PERSISTENT_DATA_BLOCK_SERVICE);
        OemLockManager oemLockManager = (OemLockManager) requireContext().getSystemService(
                Context.OEM_LOCK_SERVICE);
        if (pdbManager != null && !oemLockManager.isOemUnlockAllowed()
                && isDeviceProvisioned()) {
            // If OEM unlock is allowed, the persistent data block will be wiped during the factory
            // reset process. If disabled, it will be wiped here, unless the device is still being
            // provisioned, in which case the persistent data block will be preserved.
            new AsyncTask<Void, Void, Void>() {
                private ProgressDialog mProgressDialog;

                @Override
                protected Void doInBackground(Void... params) {
                    pdbManager.wipe();
                    return null;
                }

                @Override
                protected void onPostExecute(Void aVoid) {
                    mProgressDialog.hide();
                    if (getActivity() != null) {
                        resetEverything();
                    }
                }

                @Override
                protected void onPreExecute() {
                    mProgressDialog = getProgressDialog();
                    mProgressDialog.show();
                }
            }.execute();
        } else {
            resetEverything();
        }
    };

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.master_clear_confirm_fragment;
    }

    @Override
    @LayoutRes
    protected int getActionBarLayoutId() {
        return R.layout.action_bar_with_button;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        Button masterClearConfirmButton = requireActivity().findViewById(R.id.action_button1);
        masterClearConfirmButton.setText(
                requireContext().getString(R.string.master_clear_confirm_button_text));
        masterClearConfirmButton.setOnClickListener(mFinalClickListener);
    }

    private boolean isDeviceProvisioned() {
        return Settings.Global.getInt(requireContext().getContentResolver(),
                Settings.Global.DEVICE_PROVISIONED, 0) != 0;
    }

    private ProgressDialog getProgressDialog() {
        ProgressDialog progressDialog = new ProgressDialog(requireContext());
        progressDialog.setIndeterminate(true);
        progressDialog.setCancelable(false);
        progressDialog.setTitle(requireContext().getString(R.string.master_clear_progress_title));
        progressDialog.setMessage(requireContext().getString(R.string.master_clear_progress_text));
        return progressDialog;
    }

    private void resetEverything() {
        Intent intent = new Intent(Intent.ACTION_FACTORY_RESET);
        intent.setPackage("android");
        intent.addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        intent.putExtra(Intent.EXTRA_REASON, "MasterClearConfirm");
        intent.putExtra(Intent.EXTRA_WIPE_ESIMS, shouldResetEsim());
        requireActivity().sendBroadcast(intent);
    }

    private boolean shouldResetEsim() {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(
                requireContext());
        return sharedPreferences.getBoolean(
                requireContext().getString(R.string.pk_master_clear_reset_esim), false);
    }
}
